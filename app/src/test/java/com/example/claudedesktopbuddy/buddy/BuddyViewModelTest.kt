package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.log.LogDirection
import com.example.claudedesktopbuddy.transport.DesktopTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuddyViewModelTest {

    /** In-memory transport: incoming lines are pushed via [emit]; outgoing lines collect in [sent]. */
    private class FakeDesktopTransport : DesktopTransport {
        private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
        override val incoming: SharedFlow<String> = _incoming.asSharedFlow()
        val sent = mutableListOf<String>()

        override suspend fun send(line: String) {
            sent += line
        }

        suspend fun emit(line: String) = _incoming.emit(line)
    }

    private val snapshotBusy = """{"running":1,"total":2,"msg":"thinking"}"""
    private val snapshotWithPrompt =
        """{"running":1,"prompt":{"id":"req_abc","tool":"Bash","hint":"rm -rf /tmp"}}"""

    /** Builds a view model on an eager test scope so launched work runs synchronously. */
    private fun TestScope.newViewModel(transport: FakeDesktopTransport): BuddyViewModel {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return BuddyViewModel(transport, scope)
    }

    @Test
    fun `default state is idle and log is disabled and empty`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        assertEquals(BuddyActivity.IDLE, vm.state.value.activity)
        assertTrue(vm.log.value.entries.isEmpty())
        assertEquals(false, vm.log.value.enabled)
    }

    @Test
    fun `an incoming snapshot updates the buddy state`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        transport.emit(snapshotBusy)

        assertEquals(BuddyActivity.BUSY, vm.state.value.activity)
        assertEquals("thinking", vm.state.value.statusMessage)
    }

    @Test
    fun `an incoming prompt awaits approval`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        transport.emit(snapshotWithPrompt)

        assertEquals(BuddyActivity.AWAITING_APPROVAL, vm.state.value.activity)
        assertEquals("req_abc", vm.state.value.pendingPrompt?.id)
    }

    @Test
    fun `a malformed incoming line leaves the state unchanged and does not crash`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        transport.emit(snapshotBusy)
        transport.emit("not json")

        assertEquals(BuddyActivity.BUSY, vm.state.value.activity)
    }

    @Test
    fun `approve sends a once decision echoing the prompt id and clears the prompt`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)
        transport.emit(snapshotWithPrompt)

        vm.approve()

        assertEquals(
            listOf("""{"cmd":"permission","id":"req_abc","decision":"once"}"""),
            transport.sent,
        )
        assertNull(vm.state.value.pendingPrompt)
        assertEquals(BuddyActivity.BUSY, vm.state.value.activity)
    }

    @Test
    fun `deny sends a deny decision`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)
        transport.emit(snapshotWithPrompt)

        vm.deny()

        assertEquals(
            listOf("""{"cmd":"permission","id":"req_abc","decision":"deny"}"""),
            transport.sent,
        )
    }

    @Test
    fun `approve with no pending prompt sends nothing`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)
        transport.emit(snapshotBusy)

        vm.approve()

        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun `incoming traffic is not logged while logging is disabled`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        transport.emit(snapshotBusy)

        assertTrue(vm.log.value.entries.isEmpty())
    }

    @Test
    fun `enabling logging records incoming lines as incoming`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        vm.setLoggingEnabled(true)
        transport.emit(snapshotBusy)

        assertEquals(1, vm.log.value.entries.size)
        assertEquals(LogDirection.INCOMING, vm.log.value.entries.single().direction)
        assertEquals(snapshotBusy, vm.log.value.entries.single().line)
    }

    @Test
    fun `outgoing decisions are logged as outgoing when logging is enabled`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)
        transport.emit(snapshotWithPrompt)
        vm.setLoggingEnabled(true)

        vm.approve()

        val outgoing = vm.log.value.entries.filter { it.direction == LogDirection.OUTGOING }
        assertEquals(listOf("""{"cmd":"permission","id":"req_abc","decision":"once"}"""), outgoing.map { it.line })
    }

    @Test
    fun `clearLog drops recorded entries`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)
        vm.setLoggingEnabled(true)
        transport.emit(snapshotBusy)

        vm.clearLog()

        assertTrue(vm.log.value.entries.isEmpty())
    }
}
