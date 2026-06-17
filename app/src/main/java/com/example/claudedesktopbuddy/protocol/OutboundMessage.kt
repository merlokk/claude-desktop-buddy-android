package com.example.claudedesktopbuddy.protocol

/**
 * A single message sent from the phone to the Claude desktop app (phone -> desktop).
 *
 * Serialization to the wire format (one compact JSON object, no trailing newline) is the job of
 * [ProtocolSerializer]; the transport adds the `\n` line terminator.
 */
sealed interface OutboundMessage {

    /**
     * The user's answer to a [PermissionPrompt]. [promptId] must echo the prompt's `id` so the
     * desktop can match the decision to the request it sent.
     */
    data class PermissionDecision(
        val promptId: String,
        val choice: PermissionChoice,
    ) : OutboundMessage

    /**
     * Acknowledges a [InboundMessage.Command] received from the desktop. On failure, [error]
     * carries a short human-readable reason.
     */
    data class CommandAck(
        val command: String,
        val ok: Boolean,
        val error: String? = null,
    ) : OutboundMessage

    /**
     * Response to a `{"cmd":"status"}` poll, carrying the current [DeviceStatus]. The desktop uses
     * it to populate the Hardware Buddy stats panel.
     */
    data class StatusAck(val status: DeviceStatus) : OutboundMessage
}

/** The decision carried by an [OutboundMessage.PermissionDecision]. */
enum class PermissionChoice(val wireValue: String) {
    /** Approve the request this once. Sent as `"once"`. */
    APPROVE("once"),

    /** Reject the request. Sent as `"deny"`. */
    DENY("deny"),
}