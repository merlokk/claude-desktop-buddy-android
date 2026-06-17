package com.example.claudedesktopbuddy.protocol

/**
 * A single message received from the Claude desktop app (desktop -> phone).
 *
 * The wire format has no common discriminator field, so the concrete type is decided by which
 * keys a line carries (see [ProtocolParser]). Anything that is valid JSON but not modelled here
 * is surfaced as [Unknown] rather than dropped, so the logs screen can still show it.
 */
sealed interface InboundMessage {

    /**
     * The ~10s keepalive snapshot that drives the buddy screen: what Claude is doing now and,
     * when present, the [prompt] awaiting an approve/deny decision.
     */
    data class Snapshot(
        val total: Int,
        val running: Int,
        val waiting: Int,
        val message: String?,
        val entries: List<String>,
        val tokens: Long,
        val tokensToday: Long,
        val prompt: PermissionPrompt?,
    ) : InboundMessage

    /** Wall-clock sync sent once on connect: epoch seconds plus the UTC offset in seconds. */
    data class TimeSync(
        val epochSeconds: Long,
        val utcOffsetSeconds: Int,
    ) : InboundMessage

    /**
     * A `{"cmd":...}` control message. [verb] is the command name ("status", "name", "owner",
     * "unpair", ...) and [argument] is the optional `name` field carried by name/owner commands.
     */
    data class Command(
        val verb: String,
        val argument: String?,
    ) : InboundMessage

    /** A `{"evt":"turn",...}` event describing assistant/user activity. */
    data class TurnEvent(
        val role: String,
        val text: String?,
    ) : InboundMessage

    /** A valid JSON object we recognise but do not model; [raw] is the original line. */
    data class Unknown(val raw: String) : InboundMessage
}

/** A pending permission request: echo [id] back in the decision (see PermissionDecision). */
data class PermissionPrompt(
    val id: String,
    val tool: String,
    val hint: String?,
)
