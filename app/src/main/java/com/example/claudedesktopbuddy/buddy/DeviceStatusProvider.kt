package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.protocol.DeviceStatus

/**
 * Supplies the hardware/identity parts of the [DeviceStatus] reported in a status ack (battery,
 * uptime, device name, link security). Implemented in the Android layer, where the platform APIs
 * live; the orchestrator stays framework-free and merges in its own app-level stats.
 */
fun interface DeviceStatusProvider {

    fun status(): DeviceStatus

    companion object {
        /** A provider that reports nothing — used by default and in tests. */
        val Empty = DeviceStatusProvider { DeviceStatus() }
    }
}
