package com.example.claudedesktopbuddy.buddy

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.claudedesktopbuddy.ble.BleDesktopTransport
import com.example.claudedesktopbuddy.transport.DesktopTransport

/**
 * Android lifecycle wrapper around the framework-free [BuddyViewModel].
 *
 * It owns the coroutine scope — `viewModelScope`, cancelled automatically in `onCleared` — and
 * forwards everything to the orchestrator, which holds the actual logic. Keeping the logic in
 * [BuddyViewModel] is what lets it be unit-tested on the JVM; this class is the thin Android seam.
 *
 * Transport lifecycle ([startTransport]/[stopTransport]) is driven by the UI, which gates it on the
 * BLE runtime permissions; the transport is also released in [onCleared].
 */
class BuddyAndroidViewModel(private val transport: DesktopTransport) : ViewModel() {

    private val delegate = BuddyViewModel(transport, viewModelScope)

    val state = delegate.state
    val log = delegate.log

    fun approve() = delegate.approve()
    fun deny() = delegate.deny()
    fun setLoggingEnabled(enabled: Boolean) = delegate.setLoggingEnabled(enabled)
    fun clearLog() = delegate.clearLog()

    /** Start the transport (begin BLE advertising). Call once the BLE permissions are granted. */
    fun startTransport() = transport.start()

    /** Stop the transport, releasing the radio. */
    fun stopTransport() = transport.stop()

    override fun onCleared() {
        transport.stop()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as Application
                BuddyAndroidViewModel(BleDesktopTransport(application))
            }
        }
    }
}
