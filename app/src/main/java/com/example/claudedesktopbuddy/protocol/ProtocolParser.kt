package com.example.claudedesktopbuddy.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Thrown when a line is not a parseable JSON object. */
class ProtocolParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses a single line of the Nordic UART JSON protocol (one object per line) into an
 * [InboundMessage]. Framing (splitting on `\n`) is the transport's responsibility; this parser
 * only sees one complete line at a time.
 *
 * The concrete type is chosen by which keys are present, in priority order: `cmd` -> [Command],
 * `evt` -> [TurnEvent], `time` -> [TimeSync], otherwise a snapshot. A valid JSON object that fits
 * none of these becomes [Unknown] so the logs screen can still display it.
 */
object ProtocolParser {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * @throws ProtocolParseException if [line] is not a valid JSON object.
     */
    fun parse(line: String): InboundMessage {
        val root = parseObject(line)
        return when {
            "cmd" in root -> parseCommand(root)
            "evt" in root -> parseTurnEvent(root)
            "time" in root -> parseTimeSync(root)
            SNAPSHOT_KEYS.any { it in root } -> parseSnapshot(root)
            else -> InboundMessage.Unknown(line)
        }
    }

    private fun parseObject(line: String): JsonObject {
        val element = try {
            json.parseToJsonElement(line)
        } catch (e: Exception) {
            throw ProtocolParseException("Line is not valid JSON: $line", e)
        }
        return element as? JsonObject
            ?: throw ProtocolParseException("Expected a JSON object but got: $line")
    }

    private fun parseSnapshot(root: JsonObject): InboundMessage.Snapshot =
        InboundMessage.Snapshot(
            total = root.int("total"),
            running = root.int("running"),
            waiting = root.int("waiting"),
            message = root.string("msg"),
            entries = root.stringList("entries"),
            tokens = root.long("tokens"),
            tokensToday = root.long("tokens_today"),
            prompt = (root["prompt"] as? JsonObject)?.let(::parsePrompt),
        )

    private fun parsePrompt(prompt: JsonObject): PermissionPrompt =
        PermissionPrompt(
            id = prompt.string("id").orEmpty(),
            tool = prompt.string("tool").orEmpty(),
            hint = prompt.string("hint"),
        )

    private fun parseCommand(root: JsonObject): InboundMessage.Command =
        InboundMessage.Command(
            verb = root.string("cmd").orEmpty(),
            argument = root.string("name"),
        )

    private fun parseTimeSync(root: JsonObject): InboundMessage.TimeSync {
        val time = root["time"] as? JsonArray
        return InboundMessage.TimeSync(
            epochSeconds = (time?.getOrNull(0) as? JsonPrimitive)?.longOrNull ?: 0L,
            utcOffsetSeconds = (time?.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: 0,
        )
    }

    private fun parseTurnEvent(root: JsonObject): InboundMessage.TurnEvent {
        val text = (root["content"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.filter { it.string("type") == "text" }
            ?.mapNotNull { it.string("text") }
            ?.joinToString(" ")
            ?.ifEmpty { null }
        return InboundMessage.TurnEvent(role = root.string("role").orEmpty(), text = text)
    }
}

/** Keys that mark a line as a heartbeat snapshot. */
private val SNAPSHOT_KEYS = listOf(
    "total", "running", "waiting", "msg", "entries", "tokens", "tokens_today", "prompt",
)

private fun JsonObject.primitive(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

private fun JsonObject.string(key: String): String? = primitive(key)?.contentOrNull

private fun JsonObject.int(key: String): Int = primitive(key)?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long = primitive(key)?.longOrNull ?: 0L

private fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
