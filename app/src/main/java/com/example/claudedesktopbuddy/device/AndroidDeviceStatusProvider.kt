package com.example.claudedesktopbuddy.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import com.example.claudedesktopbuddy.buddy.DeviceStatusProvider
import com.example.claudedesktopbuddy.protocol.BatteryStatus
import com.example.claudedesktopbuddy.protocol.DeviceStatus
import com.example.claudedesktopbuddy.protocol.SystemStatus

/**
 * Reads the phone's hardware telemetry for a status ack: battery level/voltage/charging from the
 * sticky `ACTION_BATTERY_CHANGED` intent, uptime since boot, and free JVM heap.
 *
 * Link security (`sec`) and the approval/denial stats are not set here — the orchestrator fills
 * those from the transport and its own state.
 */
class AndroidDeviceStatusProvider(context: Context) : DeviceStatusProvider {

    private val appContext = context.applicationContext

    override fun status(): DeviceStatus = DeviceStatus(
        name = Build.MODEL,
        battery = readBattery(),
        system = readSystem(),
    )

    private fun readBattery(): BatteryStatus {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return BatteryStatus()

        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)

        return BatteryStatus(
            percent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            milliVolts = voltage.takeIf { it > 0 },
            onUsb = plugged == BatteryManager.BATTERY_PLUGGED_USB,
        )
    }

    private fun readSystem(): SystemStatus {
        val runtime = Runtime.getRuntime()
        return SystemStatus(
            uptimeSeconds = SystemClock.elapsedRealtime() / 1000,
            freeHeapBytes = runtime.freeMemory(),
        )
    }
}
