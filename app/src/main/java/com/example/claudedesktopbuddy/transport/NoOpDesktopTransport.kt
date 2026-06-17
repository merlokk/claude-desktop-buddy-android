package com.example.claudedesktopbuddy.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Placeholder [DesktopTransport] used until the real BLE peripheral lands: it never delivers an
 * incoming line and silently drops anything sent. It lets the UI run end-to-end against an
 * otherwise idle buddy.
 */
class NoOpDesktopTransport : DesktopTransport {
    override val incoming: Flow<String> = emptyFlow()
    override suspend fun send(line: String) = Unit
}
