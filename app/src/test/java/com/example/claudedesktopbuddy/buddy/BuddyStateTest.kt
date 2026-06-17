package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.protocol.InboundMessage
import com.example.claudedesktopbuddy.protocol.PermissionChoice
import com.example.claudedesktopbuddy.protocol.PermissionPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuddyStateTest {

    private fun snapshot(
        running: Int = 0,
        total: Int = 0,
        waiting: Int = 0,
        message: String? = null,
        entries: List<String> = emptyList(),
        tokens: Long = 0,
        tokensToday: Long = 0,
        prompt: PermissionPrompt? = null,
    ) = InboundMessage.Snapshot(running = running, total = total, waiting = waiting,
        message = message, entries = entries, tokens = tokens, tokensToday = tokensToday,
        prompt = prompt)

    @Test
    fun `default state is idle and empty`() {
        val state = BuddyState()

        assertEquals(BuddyActivity.IDLE, state.activity)
        assertNull(state.pendingPrompt)
        assertEquals(0L, state.tokens)
    }

    @Test
    fun `a running snapshot makes the buddy busy`() {
        val state = BuddyState().reduce(snapshot(running = 1, total = 2, message = "thinking"))

        assertEquals(BuddyActivity.BUSY, state.activity)
        assertEquals("thinking", state.statusMessage)
        assertEquals(2, state.total)
    }

    @Test
    fun `a snapshot with nothing running is idle`() {
        val state = BuddyState().reduce(snapshot(running = 0))

        assertEquals(BuddyActivity.IDLE, state.activity)
    }

    @Test
    fun `a snapshot carries token counts and recent entries`() {
        val state = BuddyState().reduce(
            snapshot(running = 1, tokens = 184502, tokensToday = 31200, entries = listOf("10:42 git push")),
        )

        assertEquals(184502L, state.tokens)
        assertEquals(31200L, state.tokensToday)
        assertEquals(listOf("10:42 git push"), state.recentEntries)
    }

    @Test
    fun `a prompt in a snapshot awaits approval`() {
        val prompt = PermissionPrompt(id = "req_1", tool = "Bash", hint = "rm -rf /tmp")
        val state = BuddyState().reduce(snapshot(running = 1, prompt = prompt))

        assertEquals(BuddyActivity.AWAITING_APPROVAL, state.activity)
        assertEquals(prompt, state.pendingPrompt)
    }

    @Test
    fun `each snapshot replaces previous fields rather than accumulating`() {
        val state = BuddyState()
            .reduce(snapshot(running = 1, entries = listOf("old"), message = "first"))
            .reduce(snapshot(running = 1, entries = listOf("new"), message = "second"))

        assertEquals(listOf("new"), state.recentEntries)
        assertEquals("second", state.statusMessage)
    }

    @Test
    fun `a later snapshot without a prompt clears a pending prompt`() {
        val prompt = PermissionPrompt(id = "req_1", tool = "Bash", hint = null)
        val state = BuddyState()
            .reduce(snapshot(running = 1, prompt = prompt))
            .reduce(snapshot(running = 1, prompt = null))

        assertNull(state.pendingPrompt)
        assertEquals(BuddyActivity.BUSY, state.activity)
    }

    @Test
    fun `non-snapshot messages leave the state unchanged`() {
        val base = BuddyState().reduce(snapshot(running = 1, message = "busy"))

        assertEquals(base, base.reduce(InboundMessage.TimeSync(epochSeconds = 1, utcOffsetSeconds = 0)))
        assertEquals(base, base.reduce(InboundMessage.Command(verb = "status", argument = null)))
        assertEquals(base, base.reduce(InboundMessage.Command(verb = "owner", argument = "Felix")))
        assertEquals(base, base.reduce(InboundMessage.Command(verb = "unpair", argument = null)))
        assertEquals(base, base.reduce(InboundMessage.Unknown(raw = "{}")))
    }

    @Test
    fun `a turn event with text becomes the last turn`() {
        val state = BuddyState().reduce(InboundMessage.TurnEvent(role = "assistant", text = "Done"))

        assertEquals(Turn(role = "assistant", text = "Done"), state.lastTurn)
    }

    @Test
    fun `a turn event without text leaves the last turn unchanged`() {
        val withTurn = BuddyState().reduce(InboundMessage.TurnEvent(role = "assistant", text = "Done"))

        val after = withTurn.reduce(InboundMessage.TurnEvent(role = "user", text = null))

        assertEquals(Turn(role = "assistant", text = "Done"), after.lastTurn)
    }

    @Test
    fun `the last turn survives a later snapshot`() {
        val state = BuddyState()
            .reduce(InboundMessage.TurnEvent(role = "assistant", text = "Hello"))
            .reduce(snapshot(running = 1, message = "busy"))

        assertEquals(Turn(role = "assistant", text = "Hello"), state.lastTurn)
    }

    @Test
    fun `a name command sets the device name`() {
        val state = BuddyState().reduce(InboundMessage.Command(verb = "name", argument = "Clawd"))

        assertEquals("Clawd", state.deviceName)
    }

    @Test
    fun `answering approve produces an approve decision echoing the prompt id`() {
        val prompt = PermissionPrompt(id = "req_abc", tool = "Bash", hint = null)
        val state = BuddyState().reduce(snapshot(running = 1, prompt = prompt))

        val answer = state.answer(PermissionChoice.APPROVE)!!

        assertEquals("req_abc", answer.decision.promptId)
        assertEquals(PermissionChoice.APPROVE, answer.decision.choice)
    }

    @Test
    fun `answering deny produces a deny decision`() {
        val prompt = PermissionPrompt(id = "req_abc", tool = "Bash", hint = null)
        val state = BuddyState().reduce(snapshot(running = 1, prompt = prompt))

        val answer = state.answer(PermissionChoice.DENY)!!

        assertEquals(PermissionChoice.DENY, answer.decision.choice)
    }

    @Test
    fun `answering clears the pending prompt and recomputes activity from running`() {
        val prompt = PermissionPrompt(id = "req_abc", tool = "Bash", hint = null)
        val state = BuddyState().reduce(snapshot(running = 2, prompt = prompt))

        val next = state.answer(PermissionChoice.APPROVE)!!.state

        assertNull(next.pendingPrompt)
        assertEquals(BuddyActivity.BUSY, next.activity)
    }

    @Test
    fun `approving increments the approval count`() {
        val prompt = PermissionPrompt(id = "req_1", tool = "Bash", hint = null)
        val state = BuddyState().reduce(snapshot(running = 1, prompt = prompt))

        val next = state.answer(PermissionChoice.APPROVE)!!.state

        assertEquals(1, next.approvals)
        assertEquals(0, next.denials)
    }

    @Test
    fun `denying increments the denial count`() {
        val prompt = PermissionPrompt(id = "req_1", tool = "Bash", hint = null)
        val state = BuddyState().reduce(snapshot(running = 1, prompt = prompt))

        val next = state.answer(PermissionChoice.DENY)!!.state

        assertEquals(0, next.approvals)
        assertEquals(1, next.denials)
    }

    @Test
    fun `decision counts survive later snapshots`() {
        val prompt = PermissionPrompt(id = "req_1", tool = "Bash", hint = null)
        val answered = BuddyState().reduce(snapshot(running = 1, prompt = prompt))
            .answer(PermissionChoice.APPROVE)!!.state

        val next = answered.reduce(snapshot(running = 1, message = "still working"))

        assertEquals(1, next.approvals)
    }

    @Test
    fun `answering with no pending prompt returns null`() {
        val state = BuddyState().reduce(snapshot(running = 1))

        assertNull(state.answer(PermissionChoice.APPROVE))
    }
}
