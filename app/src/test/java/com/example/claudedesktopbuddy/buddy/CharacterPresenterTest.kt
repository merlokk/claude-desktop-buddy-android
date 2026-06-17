package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.protocol.CharacterManifest
import com.example.claudedesktopbuddy.protocol.PermissionPrompt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CharacterPresenterTest {

    @Test
    fun `a disconnected buddy sleeps`() {
        assertEquals(CharacterState.SLEEP, CharacterPresenter.stateFor(BuddyState(isConnected = false)))
    }

    @Test
    fun `a disconnected buddy sleeps even with a pending prompt`() {
        val state = BuddyState(
            isConnected = false,
            pendingPrompt = PermissionPrompt(id = "r", tool = "Bash", hint = null),
        )

        assertEquals(CharacterState.SLEEP, CharacterPresenter.stateFor(state))
    }

    @Test
    fun `a pending prompt asks for attention`() {
        val state = BuddyState(
            isConnected = true,
            running = 1,
            pendingPrompt = PermissionPrompt(id = "r", tool = "Bash", hint = null),
        )

        assertEquals(CharacterState.ATTENTION, CharacterPresenter.stateFor(state))
    }

    @Test
    fun `running sessions are busy`() {
        assertEquals(
            CharacterState.BUSY,
            CharacterPresenter.stateFor(BuddyState(isConnected = true, running = 2)),
        )
    }

    @Test
    fun `connected with nothing running is idle`() {
        assertEquals(
            CharacterState.IDLE,
            CharacterPresenter.stateFor(BuddyState(isConnected = true, running = 0)),
        )
    }

    @Test
    fun `celebrating overrides the state with heart`() {
        val busy = BuddyState(isConnected = true, running = 1)

        assertEquals(CharacterState.HEART, CharacterPresenter.stateFor(busy, celebrating = true))
        assertEquals(CharacterState.BUSY, CharacterPresenter.stateFor(busy, celebrating = false))
    }

    private val manifest = CharacterManifest(
        name = "bufo",
        colors = null,
        states = mapOf(
            "idle" to listOf("idle_0.gif", "idle_1.gif"),
            "busy" to listOf("busy.gif"),
        ),
    )

    @Test
    fun `frames come from the requested state when present`() {
        assertEquals(listOf("busy.gif"), CharacterPresenter.framesFor(manifest, CharacterState.BUSY))
    }

    @Test
    fun `a missing state falls back to idle frames`() {
        assertEquals(
            listOf("idle_0.gif", "idle_1.gif"),
            CharacterPresenter.framesFor(manifest, CharacterState.ATTENTION),
        )
    }

    @Test
    fun `frames are empty when neither the state nor idle is defined`() {
        val empty = CharacterManifest(name = null, colors = null, states = emptyMap())

        assertEquals(emptyList<String>(), CharacterPresenter.framesFor(empty, CharacterState.BUSY))
    }

    @Test
    fun `frameAt rotates through a carousel and wraps`() {
        val frames = listOf("a.gif", "b.gif", "c.gif")

        assertEquals("a.gif", CharacterPresenter.frameAt(frames, 0))
        assertEquals("b.gif", CharacterPresenter.frameAt(frames, 1))
        assertEquals("c.gif", CharacterPresenter.frameAt(frames, 2))
        assertEquals("a.gif", CharacterPresenter.frameAt(frames, 3))
        assertEquals("c.gif", CharacterPresenter.frameAt(frames, -1)) // mod handles negatives
    }

    @Test
    fun `frameAt returns null for no frames`() {
        assertNull(CharacterPresenter.frameAt(emptyList(), 0))
    }
}
