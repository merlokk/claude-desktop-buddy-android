package com.example.claudedesktopbuddy.transport

import kotlinx.coroutines.flow.Flow

/**
 * A bidirectional, line-oriented link to the Claude desktop app.
 *
 * Implementations own the actual Bluetooth (BLE) plumbing — advertising the Nordic UART service
 * and moving bytes — while the domain depends only on this interface. The dependency points one
 * way: domain -> [DesktopTransport], never the reverse, so no Android Bluetooth type leaks past
 * this boundary.
 *
 * Every value exchanged here is one complete protocol line **without** the trailing `\n`. The
 * line terminator and the reassembly of BLE chunks are handled internally via the framing helpers
 * ([LineAssembler] for inbound, [encodeLine] for outbound).
 */
interface DesktopTransport {

    /** Complete inbound lines from the desktop, in the order they arrived. */
    val incoming: Flow<String>

    /** Sends one complete line to the desktop; the `\n` terminator is appended by the transport. */
    suspend fun send(line: String)

    /**
     * Begins accepting connections (e.g. starts BLE advertising and the GATT server). Safe to call
     * repeatedly — a transport that is already running ignores the call. Default: no-op.
     */
    fun start() {}

    /** Releases the link and any radio resources. Default: no-op. */
    fun stop() {}
}
