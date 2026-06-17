package com.example.claudedesktopbuddy.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CharacterManifestParserTest {

    @Test
    fun `parses name colors and states`() {
        val manifest = CharacterManifestParser.parse(
            """
            {
              "name": "bufo",
              "colors": {"body":"#1a1a1a","bg":"#ffffff","text":"#000","textDim":"#888","ink":"#222"},
              "states": {"sleep":"sleep.gif","busy":"busy.gif"}
            }
            """.trimIndent(),
        )

        assertEquals("bufo", manifest.name)
        assertEquals(
            CharacterColors(body = "#1a1a1a", bg = "#ffffff", text = "#000", textDim = "#888", ink = "#222"),
            manifest.colors,
        )
        assertEquals(listOf("sleep.gif"), manifest.framesFor("sleep"))
        assertEquals(listOf("busy.gif"), manifest.framesFor("busy"))
    }

    @Test
    fun `normalises a single filename and an array to a list`() {
        val manifest = CharacterManifestParser.parse(
            """{"states":{"busy":"busy.gif","idle":["idle_0.gif","idle_1.gif","idle_2.gif"]}}""",
        )

        assertEquals(listOf("busy.gif"), manifest.framesFor("busy"))
        assertEquals(listOf("idle_0.gif", "idle_1.gif", "idle_2.gif"), manifest.framesFor("idle"))
    }

    @Test
    fun `a missing state resolves to an empty list`() {
        val manifest = CharacterManifestParser.parse("""{"states":{"idle":"idle.gif"}}""")

        assertEquals(emptyList<String>(), manifest.framesFor("attention"))
    }

    @Test
    fun `tolerates a manifest without colors or name`() {
        val manifest = CharacterManifestParser.parse("""{"states":{"idle":"idle.gif"}}""")

        assertNull(manifest.name)
        assertNull(manifest.colors)
        assertEquals(listOf("idle.gif"), manifest.framesFor("idle"))
    }

    @Test
    fun `tolerates a manifest with no states`() {
        val manifest = CharacterManifestParser.parse("""{"name":"empty"}""")

        assertEquals("empty", manifest.name)
        assertEquals(emptyMap<String, List<String>>(), manifest.states)
    }

    @Test
    fun `ignores unknown fields`() {
        val manifest = CharacterManifestParser.parse(
            """{"name":"x","version":3,"author":"me","states":{"idle":"idle.gif"}}""",
        )

        assertEquals("x", manifest.name)
        assertEquals(listOf("idle.gif"), manifest.framesFor("idle"))
    }

    @Test
    fun `throws on non-object json`() {
        assertThrows(CharacterManifestException::class.java) {
            CharacterManifestParser.parse("""["not","an","object"]""")
        }
        assertThrows(CharacterManifestException::class.java) {
            CharacterManifestParser.parse("not json")
        }
    }
}
