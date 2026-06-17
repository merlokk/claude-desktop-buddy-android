package com.example.claudedesktopbuddy.protocol

/**
 * The device snapshot carried by a status ack (`{"cmd":"status"}` response). Every part is
 * optional — the protocol allows omitting fields the device does not have — so absent values are
 * left out of the wire JSON. See `docs/REFERENCE.md` for the field meanings.
 */
data class DeviceStatus(
    val name: String? = null,
    val secure: Boolean? = null,
    val battery: BatteryStatus? = null,
    val system: SystemStatus? = null,
    val stats: BuddyStats? = null,
)

/** Battery telemetry. `milliAmps` negative means charging (per the protocol convention). */
data class BatteryStatus(
    val percent: Int? = null,
    val milliVolts: Int? = null,
    val milliAmps: Int? = null,
    val onUsb: Boolean? = null,
)

/** Runtime telemetry: uptime in seconds and free heap in bytes. */
data class SystemStatus(
    val uptimeSeconds: Long? = null,
    val freeHeapBytes: Long? = null,
)

/**
 * The status `stats` object. [approvals] / [denials] are our app-level counters; [velocity],
 * [naps], and [level] are pet-specific fields the desktop expects — we have no pet, so they are 0.
 */
data class BuddyStats(
    val approvals: Int,
    val denials: Int,
    val velocity: Int = 0,
    val naps: Int = 0,
    val level: Int = 0,
)
