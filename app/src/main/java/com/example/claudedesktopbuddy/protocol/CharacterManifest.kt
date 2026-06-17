package com.example.claudedesktopbuddy.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * The `manifest.json` of a pushed character pack: the pack [name], its [colors], and the mapping
 * from animation state to the GIF file(s) that play in that state.
 *
 * A state's value in the JSON may be a single filename or an array of filenames; both are normalised
 * to a list here (a single name becomes a one-element list). An empty list means the pack has no
 * animation for that state.
 */
data class CharacterManifest(
    val name: String?,
    val colors: CharacterColors?,
    val states: Map<String, List<String>>,
) {
    /** The GIF filenames for [state] (the pack-relative names), or empty if the pack omits it. */
    fun framesFor(state: String): List<String> = states[state].orEmpty()
}

/** The pack's palette (hex strings); any field may be absent. */
data class CharacterColors(
    val body: String? = null,
    val bg: String? = null,
    val text: String? = null,
    val textDim: String? = null,
    val ink: String? = null,
)

/** Thrown when a manifest is not a parseable JSON object. */
class CharacterManifestException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Parses a character pack's `manifest.json`. Tolerant of missing fields — only a non-object root is
 * an error — so a sparse manifest still yields a usable [CharacterManifest].
 */
object CharacterManifestParser {

    private val json = Json { ignoreUnknownKeys = true }

    /** @throws CharacterManifestException if [text] is not a JSON object. */
    fun parse(text: String): CharacterManifest {
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            throw CharacterManifestException("Manifest is not valid JSON", e)
        } ?: throw CharacterManifestException("Manifest is not a JSON object")

        return CharacterManifest(
            name = (root["name"] as? JsonPrimitive)?.contentOrNull,
            colors = (root["colors"] as? JsonObject)?.let(::parseColors),
            states = (root["states"] as? JsonObject)?.let(::parseStates).orEmpty(),
        )
    }

    private fun parseColors(colors: JsonObject) = CharacterColors(
        body = colors.string("body"),
        bg = colors.string("bg"),
        text = colors.string("text"),
        textDim = colors.string("textDim"),
        ink = colors.string("ink"),
    )

    private fun parseStates(states: JsonObject): Map<String, List<String>> =
        states.mapValues { (_, value) -> value.toFilenameList() }

    /** A state value is a single filename string or an array of them; normalise both to a list. */
    private fun kotlinx.serialization.json.JsonElement.toFilenameList(): List<String> = when (this) {
        is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        is JsonPrimitive -> listOfNotNull(contentOrNull)
        else -> emptyList()
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
}
