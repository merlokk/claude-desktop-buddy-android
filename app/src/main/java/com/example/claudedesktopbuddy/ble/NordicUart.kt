package com.example.claudedesktopbuddy.ble

import java.util.UUID

/**
 * Identifiers of the Nordic UART Service the phone exposes as a BLE peripheral. The desktop
 * connects as central, writes lines to [RX], and receives lines as notifications on [TX].
 */
object NordicUart {

    /** The Nordic UART primary service. */
    val SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")

    /** desktop -> phone: the central writes incoming lines here. */
    val RX: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    /** phone -> desktop: outgoing lines are sent as notifications on this characteristic. */
    val TX: UUID = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")

    /** Client Characteristic Configuration Descriptor — the central writes here to subscribe to [TX]. */
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
