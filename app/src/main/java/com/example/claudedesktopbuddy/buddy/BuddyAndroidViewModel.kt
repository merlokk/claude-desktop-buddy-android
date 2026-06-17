package com.example.claudedesktopbuddy.buddy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.claudedesktopbuddy.transport.DesktopTransport
import com.example.claudedesktopbuddy.transport.NoOpDesktopTransport

/**
 * Android lifecycle wrapper around the framework-free [BuddyViewModel].
 *
 * It owns the coroutine scope — `viewModelScope`, cancelled automatically in `onCleared` — and
 * forwards everything to the orchestrator, which holds the actual logic. Keeping the logic in
 * [BuddyViewModel] is what lets it be unit-tested on the JVM; this class is the thin Android seam.
 */
class BuddyAndroidViewModel(transport: DesktopTransport) : ViewModel() {

    private val delegate = BuddyViewModel(transport, viewModelScope)

    val state = delegate.state
    val log = delegate.log

    fun approve() = delegate.approve()
    fun deny() = delegate.deny()
    fun setLoggingEnabled(enabled: Boolean) = delegate.setLoggingEnabled(enabled)
    fun clearLog() = delegate.clearLog()

    companion object {
        val Factory = viewModelFactory {
            // TODO: swap in the real BLE DesktopTransport once the peripheral is implemented.
            initializer { BuddyAndroidViewModel(NoOpDesktopTransport()) }
        }
    }
}
