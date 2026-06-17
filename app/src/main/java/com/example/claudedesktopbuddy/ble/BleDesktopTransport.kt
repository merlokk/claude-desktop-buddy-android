package com.example.claudedesktopbuddy.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import com.example.claudedesktopbuddy.transport.DesktopTransport
import com.example.claudedesktopbuddy.transport.encodeLine
import com.example.claudedesktopbuddy.transport.LineAssembler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [DesktopTransport] backed by a real BLE peripheral: the phone hosts the Nordic UART GATT service
 * and the Claude desktop app connects as the central.
 *
 * Incoming writes on [NordicUart.RX] are reassembled by the tested [LineAssembler] and published on
 * [incoming]; outgoing lines are framed by [encodeLine] and pushed as notifications on
 * [NordicUart.TX], split to fit the negotiated MTU.
 *
 * This is the deliberately thin Android edge — the protocol logic lives in the unit-tested pure
 * layers. Callers must ensure [blePermissions] are granted before [start]; the UI gates this, so
 * the BLE calls are annotated [SuppressLint] for "MissingPermission".
 */
@SuppressLint("MissingPermission")
class BleDesktopTransport(context: Context) : DesktopTransport {

    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)

    private val incomingLines = Channel<String>(Channel.UNLIMITED)
    override val incoming: Flow<String> = incomingLines.receiveAsFlow()

    private val assembler = LineAssembler()

    private var gattServer: BluetoothGattServer? = null
    private var txCharacteristic: BluetoothGattCharacteristic? = null
    private var advertiser: BluetoothLeAdvertiser? = null

    /** The connected/subscribed central, i.e. the desktop. Single central is enough for a buddy. */
    @Volatile
    private var central: BluetoothDevice? = null

    @Volatile
    private var mtu = DEFAULT_MTU

    private val mainHandler = Handler(Looper.getMainLooper())
    private var advertising = false

    /** The phone's Bluetooth name before we prefixed it with "Claude"; restored on [stop]. */
    private var originalAdapterName: String? = null
    private var nameChangeReceiver: BroadcastReceiver? = null

    // Notifications are dispatched one at a time, awaiting onNotificationSent between chunks.
    private val sendMutex = Mutex()

    @Volatile
    private var pendingNotification: CompletableDeferred<Boolean>? = null

    override fun start() {
        if (gattServer != null) return // already running
        val manager = bluetoothManager ?: run { Log.w(TAG, "No BluetoothManager"); return }
        val adapter = manager.adapter ?: run { Log.w(TAG, "No Bluetooth adapter"); return }
        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled")
            return
        }

        val server = manager.openGattServer(appContext, serverCallback)
            ?: run { Log.w(TAG, "Failed to open GATT server"); return }
        gattServer = server
        server.addService(buildService())
        advertiseWithBuddyName(adapter)
        Log.i(TAG, "GATT server opened; advertising requested")
    }

    override fun stop() {
        unregisterNameReceiver()
        mainHandler.removeCallbacksAndMessages(null)
        advertising = false
        advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
        gattServer?.close()
        gattServer = null
        txCharacteristic = null
        central = null
        restoreAdapterName()
    }

    /**
     * The desktop's device picker filters to advertised names starting with "Claude", so we prefix
     * the phone's Bluetooth name. Android exposes no per-advertisement name, only the global adapter
     * name, and setting it is asynchronous — so we wait for [BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED]
     * (with a timeout fallback) before advertising, otherwise the packet would carry the old name.
     */
    private fun advertiseWithBuddyName(adapter: BluetoothAdapter) {
        val current = adapter.name
        when {
            current == null -> advertiseOnce() // can't read the name; advertise as-is
            current.startsWith("$BUDDY_NAME_PREFIX ") -> {
                // A previous run left the prefixed name; remember the underlying one to restore later.
                originalAdapterName = current.removePrefix("$BUDDY_NAME_PREFIX ")
                advertiseOnce()
            }
            else -> {
                originalAdapterName = current
                waitForNameChangeThenAdvertise(adapter, target = "$BUDDY_NAME_PREFIX $current")
                adapter.name = "$BUDDY_NAME_PREFIX $current"
            }
        }
    }

    private fun waitForNameChangeThenAdvertise(adapter: BluetoothAdapter, target: String) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (adapter.name == target) advertiseOnce()
            }
        }
        nameChangeReceiver = receiver
        appContext.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_LOCAL_NAME_CHANGED))
        mainHandler.postDelayed({ advertiseOnce() }, NAME_CHANGE_TIMEOUT_MS)
    }

    /** Starts advertising at most once per [start], whether triggered by the name change or timeout. */
    private fun advertiseOnce() {
        if (advertising || gattServer == null) return
        advertising = true
        unregisterNameReceiver()
        mainHandler.removeCallbacksAndMessages(null)
        startAdvertising()
    }

    private fun unregisterNameReceiver() {
        nameChangeReceiver?.let { runCatching { appContext.unregisterReceiver(it) } }
        nameChangeReceiver = null
    }

    private fun restoreAdapterName() {
        val adapter = bluetoothManager?.adapter ?: return
        originalAdapterName?.let {
            adapter.name = it
            Log.i(TAG, "Restored Bluetooth name")
        }
        originalAdapterName = null
    }

    override suspend fun send(line: String) {
        val server = gattServer ?: return
        val characteristic = txCharacteristic ?: return
        val device = central ?: return
        val payload = encodeLine(line)
        val chunkSize = (mtu - ATT_HEADER_SIZE).coerceAtLeast(MIN_CHUNK_SIZE)

        sendMutex.withLock {
            var offset = 0
            while (offset < payload.size) {
                val end = minOf(offset + chunkSize, payload.size)
                val chunk = payload.copyOfRange(offset, end)

                val ack = CompletableDeferred<Boolean>()
                pendingNotification = ack
                val dispatched = notify(server, device, characteristic, chunk)
                if (!dispatched) {
                    pendingNotification = null
                    return
                }
                val delivered = withTimeoutOrNull(NOTIFY_TIMEOUT_MS) { ack.await() } ?: false
                pendingNotification = null
                if (!delivered) return

                offset = end
            }
        }
    }

    private fun buildService(): BluetoothGattService {
        val service = BluetoothGattService(NordicUart.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val rx = BluetoothGattCharacteristic(
            NordicUart.RX,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        val tx = BluetoothGattCharacteristic(
            NordicUart.TX,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        tx.addDescriptor(
            BluetoothGattDescriptor(
                NordicUart.CCCD,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )

        service.addCharacteristic(rx)
        service.addCharacteristic(tx)
        txCharacteristic = tx
        return service
    }

    private fun startAdvertising() {
        val adapter = bluetoothManager?.adapter ?: return
        val leAdvertiser = adapter.bluetoothLeAdvertiser ?: return
        advertiser = leAdvertiser

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .build()
        // The 128-bit service UUID and the device name don't both fit in one 31-byte packet, so the
        // name goes in the scan response.
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(NordicUart.SERVICE))
            .build()
        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        leAdvertiser.startAdvertising(settings, advertiseData, scanResponse, advertiseCallback)
    }

    private fun notify(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        server.notifyCharacteristicChanged(device, characteristic, false, value) ==
            BluetoothStatusCodes.SUCCESS
    } else {
        @Suppress("DEPRECATION")
        characteristic.value = value
        @Suppress("DEPRECATION")
        server.notifyCharacteristicChanged(device, characteristic, false)
    }

    private val serverCallback = object : BluetoothGattServerCallback() {

        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    central = device
                    Log.i(TAG, "Central connected: ${device.address}")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (device == central) central = null
                    Log.i(TAG, "Central disconnected: ${device.address}")
                }
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic.uuid == NordicUart.RX && value != null) {
                assembler.append(value).forEach(incomingLines::trySend)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            // The central subscribing to TX notifications identifies the desktop we notify.
            if (descriptor.uuid == NordicUart.CCCD) {
                central = device
                Log.i(TAG, "Central subscribed to TX notifications: ${device.address}")
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            pendingNotification?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
            this@BleDesktopTransport.mtu = mtu
            Log.i(TAG, "MTU changed: $mtu")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i(TAG, "Advertising started")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.w(TAG, "Advertising failed to start: error code $errorCode")
        }
    }

    private companion object {
        const val TAG = "BleDesktopTransport"
        const val BUDDY_NAME_PREFIX = "Claude"
        const val NAME_CHANGE_TIMEOUT_MS = 1_500L
        const val DEFAULT_MTU = 23
        const val ATT_HEADER_SIZE = 3 // ATT notification header (opcode + handle) consumes 3 bytes.
        const val MIN_CHUNK_SIZE = 20 // MTU 23 - 3.
        const val NOTIFY_TIMEOUT_MS = 2_000L
    }
}
