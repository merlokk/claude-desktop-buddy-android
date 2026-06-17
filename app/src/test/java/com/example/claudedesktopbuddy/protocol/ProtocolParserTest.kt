package com.example.claudedesktopbuddy.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolParserTest {

    @Test
    fun `parses full heartbeat snapshot with pending prompt`() {
        val line = """
            {"total":3,"running":1,"waiting":1,"msg":"approve: Bash",
             "entries":["10:42 git push"],"tokens":184502,"tokens_today":31200,
             "prompt":{"id":"req_abc123","tool":"Bash","hint":"rm -rf /tmp/foo"}}
        """.trimIndent().replace("\n", "")

        val message = ProtocolParser.parse(line)

        val expected = InboundMessage.Snapshot(
            total = 3,
            running = 1,
            waiting = 1,
            message = "approve: Bash",
            entries = listOf("10:42 git push"),
            tokens = 184502,
            tokensToday = 31200,
            prompt = PermissionPrompt(id = "req_abc123", tool = "Bash", hint = "rm -rf /tmp/foo"),
        )
        assertEquals(expected, message)
    }

    @Test
    fun `parses snapshot without a prompt`() {
        val line =
            """{"total":1,"running":1,"waiting":0,"msg":"thinking","entries":[],"tokens":10,"tokens_today":5}"""

        val message = ProtocolParser.parse(line) as InboundMessage.Snapshot

        assertNull(message.prompt)
        assertEquals("thinking", message.message)
        assertEquals(emptyList<String>(), message.entries)
    }

    @Test
    fun `tolerates a snapshot with missing optional fields`() {
        val message = ProtocolParser.parse("""{"running":2}""") as InboundMessage.Snapshot

        assertEquals(2, message.running)
        assertEquals(0, message.total)
        assertEquals(0, message.waiting)
        assertEquals(0L, message.tokens)
        assertEquals(0L, message.tokensToday)
        assertNull(message.message)
        assertEquals(emptyList<String>(), message.entries)
        assertNull(message.prompt)
    }

    @Test
    fun `prompt hint is optional`() {
        val line = """{"running":1,"prompt":{"id":"req_1","tool":"Read"}}"""

        val message = ProtocolParser.parse(line) as InboundMessage.Snapshot

        assertEquals(PermissionPrompt(id = "req_1", tool = "Read", hint = null), message.prompt)
    }

    @Test
    fun `parses time sync`() {
        val message = ProtocolParser.parse("""{"time":[1775731234,-25200]}""")

        assertEquals(InboundMessage.TimeSync(epochSeconds = 1775731234, utcOffsetSeconds = -25200), message)
    }

    @Test
    fun `parses owner command with name argument`() {
        val message = ProtocolParser.parse("""{"cmd":"owner","name":"Felix"}""")

        assertEquals(InboundMessage.Command(verb = "owner", argument = "Felix"), message)
    }

    @Test
    fun `parses argument-less command`() {
        val message = ProtocolParser.parse("""{"cmd":"status"}""")

        assertEquals(InboundMessage.Command(verb = "status", argument = null), message)
    }

    @Test
    fun `parses turn event and extracts text content`() {
        val line =
            """{"evt":"turn","role":"assistant","content":[{"type":"text","text":"Hello"}]}"""

        val message = ProtocolParser.parse(line)

        assertEquals(InboundMessage.TurnEvent(role = "assistant", text = "Hello"), message)
    }

    @Test
    fun `joins multiple text parts of a turn event`() {
        val line = """
            {"evt":"turn","role":"assistant","content":[
             {"type":"text","text":"Hello"},{"type":"text","text":"world"}]}
        """.trimIndent().replace("\n", "")

        val message = ProtocolParser.parse(line) as InboundMessage.TurnEvent

        assertEquals("Hello world", message.text)
    }

    @Test
    fun `parses char_begin into a folder-push CharBegin`() {
        val message = ProtocolParser.parse("""{"cmd":"char_begin","name":"bufo","total":184320}""")

        assertEquals(InboundMessage.FolderPush.CharBegin(name = "bufo", totalBytes = 184320), message)
    }

    @Test
    fun `parses file into a folder-push FileBegin`() {
        val message = ProtocolParser.parse("""{"cmd":"file","path":"manifest.json","size":412}""")

        assertEquals(InboundMessage.FolderPush.FileBegin(path = "manifest.json", sizeBytes = 412), message)
    }

    @Test
    fun `parses chunk into a folder-push Chunk`() {
        val message = ProtocolParser.parse("""{"cmd":"chunk","d":"aGVsbG8="}""")

        assertEquals(InboundMessage.FolderPush.Chunk(dataBase64 = "aGVsbG8="), message)
    }

    @Test
    fun `parses file_end and char_end into their folder-push steps`() {
        assertEquals(InboundMessage.FolderPush.FileEnd, ProtocolParser.parse("""{"cmd":"file_end"}"""))
        assertEquals(InboundMessage.FolderPush.CharEnd, ProtocolParser.parse("""{"cmd":"char_end"}"""))
    }

    @Test
    fun `valid json that is not modelled becomes Unknown`() {
        val line = """{"foo":"bar"}"""

        val message = ProtocolParser.parse(line)

        assertTrue(message is InboundMessage.Unknown)
        assertEquals(line, (message as InboundMessage.Unknown).raw)
    }

    @Test
    fun `cmd takes precedence over snapshot-like keys`() {
        // A line carrying both "cmd" and snapshot fields is a command, not a snapshot.
        val message = ProtocolParser.parse("""{"cmd":"name","name":"Clawd","running":1}""")

        assertEquals(InboundMessage.Command(verb = "name", argument = "Clawd"), message)
    }

    @Test
    fun `malformed json throws ProtocolParseException`() {
        assertThrows(ProtocolParseException::class.java) {
            ProtocolParser.parse("not json at all")
        }
    }

    @Test
    fun `a json array is not a valid message`() {
        assertThrows(ProtocolParseException::class.java) {
            ProtocolParser.parse("""[1,2,3]""")
        }
    }

    @Test
    fun `blank line throws ProtocolParseException`() {
        assertThrows(ProtocolParseException::class.java) {
            ProtocolParser.parse("   ")
        }
    }
}