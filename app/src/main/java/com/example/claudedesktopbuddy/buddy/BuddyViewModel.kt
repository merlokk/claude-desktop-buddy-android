package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.log.ExchangeLog
import com.example.claudedesktopbuddy.log.LogDirection
import com.example.claudedesktopbuddy.protocol.BuddyStats
import com.example.claudedesktopbuddy.protocol.CommandVerbs
import com.example.claudedesktopbuddy.protocol.InboundMessage
import com.example.claudedesktopbuddy.protocol.OutboundMessage
import com.example.claudedesktopbuddy.protocol.PermissionChoice
import com.example.claudedesktopbuddy.protocol.ProtocolParseException
import com.example.claudedesktopbuddy.protocol.ProtocolParser
import com.example.claudedesktopbuddy.protocol.ProtocolSerializer
import com.example.claudedesktopbuddy.transport.DesktopTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates the two screens: it folds inbound transport lines into [state] and turns the user's
 * approve/deny actions into outbound protocol lines, mirroring all raw traffic into [log].
 *
 * Deliberately free of Android framework types so it can be unit-tested on the JVM with a fake
 * [DesktopTransport] and a test [CoroutineScope]. An Android `ViewModel` wraps an instance of this
 * and feeds it `viewModelScope`.
 *
 * State mutations are confined to [scope], so the simple read-modify-write on the state flows is
 * race-free in practice.
 */
class BuddyViewModel(
    private val transport: DesktopTransport,
    private val scope: CoroutineScope,
    private val statusProvider: DeviceStatusProvider = DeviceStatusProvider.Empty,
) {

    private val _state = MutableStateFlow(BuddyState())

    /** Current buddy state for the home screen. */
    val state: StateFlow<BuddyState> = _state.asStateFlow()

    private val _log = MutableStateFlow(ExchangeLog())

    /** Raw exchange log for the logs screen (disabled by default). */
    val log: StateFlow<ExchangeLog> = _log.asStateFlow()

    init {
        scope.launch {
            transport.incoming.collect(::onLineReceived)
        }
    }

    private fun onLineReceived(line: String) {
        _log.update { it.record(LogDirection.INCOMING, line) }
        val message = try {
            ProtocolParser.parse(line)
        } catch (e: ProtocolParseException) {
            return // Malformed line: already captured raw in the log, nothing to fold in.
        }
        _state.update { it.reduce(message) }
        if (message is InboundMessage.Command) {
            handleCommand(message)
        }
    }

    /** Acknowledges the commands the desktop expects a reply to. Unknown commands are ignored. */
    private fun handleCommand(command: InboundMessage.Command) {
        when (command.verb) {
            CommandVerbs.STATUS -> sendStatusAck()
            CommandVerbs.UNPAIR -> {
                transport.unpair()
                sendAck(command.verb)
            }
            CommandVerbs.NAME, CommandVerbs.OWNER -> sendAck(command.verb)
        }
    }

    private fun sendAck(command: String) {
        send(ProtocolSerializer.encode(OutboundMessage.CommandAck(command = command, ok = true)))
    }

    private fun sendStatusAck() {
        val current = _state.value
        val base = statusProvider.status()
        val status = base.copy(
            name = current.deviceName ?: base.name,
            secure = transport.isLinkSecure,
            stats = BuddyStats(approvals = current.approvals, denials = current.denials),
        )
        send(ProtocolSerializer.encode(OutboundMessage.StatusAck(status)))
    }

    /** Approve the pending permission prompt, if any. */
    fun approve() = answer(PermissionChoice.APPROVE)

    /** Deny the pending permission prompt, if any. */
    fun deny() = answer(PermissionChoice.DENY)

    private fun answer(choice: PermissionChoice) {
        val answer = _state.value.answer(choice) ?: return
        _state.value = answer.state
        send(ProtocolSerializer.encode(answer.decision))
    }

    private fun send(line: String) {
        _log.update { it.record(LogDirection.OUTGOING, line) }
        scope.launch { transport.send(line) }
    }

    /** Turn raw-traffic logging on or off (off by default). */
    fun setLoggingEnabled(enabled: Boolean) {
        _log.update { it.copy(enabled = enabled) }
    }

    /** Drop all recorded log entries. */
    fun clearLog() {
        _log.update { it.cleared() }
    }
}
