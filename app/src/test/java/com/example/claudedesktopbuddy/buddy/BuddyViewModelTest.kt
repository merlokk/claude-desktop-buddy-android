package com.example.claudedesktopbuddy.buddy

import com.example.claudedesktopbuddy.log.LogDirection
import com.example.claudedesktopbuddy.protocol.BatteryStatus
import com.example.claudedesktopbuddy.protocol.CharacterManifest
import com.example.claudedesktopbuddy.protocol.DeviceStatus
import com.example.claudedesktopbuddy.transport.DesktopTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BuddyViewModelTest {

    /** In-memory transport: incoming lines are pushed via [emit]; outgoing lines collect in [sent]. */
    private class FakeDesktopTransport(
        override val isLinkSecure: Boolean = false,
    ) : DesktopTransport {
        private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 64)
        override val incoming: SharedFlow<String> = _incoming.asSharedFlow()
        val sent = mutableListOf<String>()
        var unpairCount = 0
            private set

        override suspend fun send(line: String) {
            sent += line
        }

        override fun unpair() {
            unpairCount++
        }

        suspend fun emit(line: String) = _incoming.emit(line)
    }

    private val snapshotBusy = """{"running":1,"total":2,"msg":"thinking"}"""
    private val snapshotWithPrompt =
        """{"running":1,"prompt":{"id":"req_abc","tool":"Bash","hint":"rm -rf /tmp"}}"""

    /** Builds a view model on an eager test scope so launched work runs synchronously. */
    private fun TestScope.newViewModel(
        transport: FakeDesktopTransport,
        statusProvider: DeviceStatusProvider = DeviceStatusProvider.Empty,
        packProvider: CharacterPackProvider = CharacterPackProvider.None,
    ): BuddyViewModel {
        val scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return BuddyViewModel(transport, scope, statusProvider, packProvider = packProvider)
    }

    @Test
    fun `default state is idle and log is disabled and empty`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        assertEquals(BuddyActivity.IDLE, vm.state.value.activity)
        assertEquals(false, vm.state.value.isConnected)
        assertTrue(vm.log.value.entries.isEmpty())
        assertEquals(false, vm.log.value.enabled)
    }

    @Test
    fun `an inbound line marks the connection alive`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        transport.emit(snapshotBusy)

        assertEquals(true, vm.state.value.isConnected)
    }

    @Test
    fun `the connection goes stale after prolonged silence`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)
        transport.emit(snapshotBusy)

        advanceTimeBy(31_000)

        assertEquals(false, vm.state.value.isConnected)
    }

    @Test
    fun `a new inbound line resets the stale timer`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        transport.emit(snapshotBusy)
        advanceTimeBy(20_000)
        transport.emit(snapshotBusy)
        advanceTimeBy(20_000)

        assertEquals(true, vm.state.value.isConnected)
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
    fun `a status command is answered with a status ack from the provider`() = runTest {
        val transport = FakeDesktopTransport()
        val provider = DeviceStatusProvider {
            DeviceStatus(name = "Pixel", secure = false, battery = BatteryStatus(percent = 87, onUsb = true))
        }
        val vm = newViewModel(transport, provider)

        transport.emit("""{"cmd":"status"}""")

        assertEquals(
            listOf(
                """{"ack":"status","ok":true,"data":{"name":"Pixel","sec":false,""" +
                    """"bat":{"pct":87,"usb":true},"stats":{"appr":0,"deny":0,"vel":0,"nap":0,"lvl":0}}}""",
            ),
            transport.sent,
        )
    }

    @Test
    fun `the status ack reflects the current approval and denial counts`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)
        transport.emit(snapshotWithPrompt)
        vm.approve()

        transport.emit("""{"cmd":"status"}""")

        val statusAck = transport.sent.last()
        assertEquals(
            """{"ack":"status","ok":true,"data":{"sec":false,"stats":{"appr":1,"deny":0,"vel":0,"nap":0,"lvl":0}}}""",
            statusAck,
        )
    }

    @Test
    fun `the status ack reports the link security from the transport`() = runTest {
        val transport = FakeDesktopTransport(isLinkSecure = true)
        val vm = newViewModel(transport)

        transport.emit("""{"cmd":"status"}""")

        assertEquals(
            """{"ack":"status","ok":true,"data":{"sec":true,"stats":{"appr":0,"deny":0,"vel":0,"nap":0,"lvl":0}}}""",
            transport.sent.last(),
        )
    }

    @Test
    fun `an unpair command erases the bond via the transport`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        transport.emit("""{"cmd":"unpair"}""")

        assertEquals(1, transport.unpairCount)
        assertEquals(listOf("""{"ack":"unpair","ok":true}"""), transport.sent)
    }

    @Test
    fun `name owner and unpair commands are acknowledged`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        transport.emit("""{"cmd":"name","name":"Clawd"}""")
        transport.emit("""{"cmd":"owner","name":"Felix"}""")
        transport.emit("""{"cmd":"unpair"}""")

        assertEquals(
            listOf(
                """{"ack":"name","ok":true}""",
                """{"ack":"owner","ok":true}""",
                """{"ack":"unpair","ok":true}""",
            ),
            transport.sent,
        )
    }

    @Test
    fun `a name command overrides the device name in the status ack`() = runTest {
        val transport = FakeDesktopTransport()
        val provider = DeviceStatusProvider { DeviceStatus(name = "Pixel") }
        val vm = newViewModel(transport, provider)

        transport.emit("""{"cmd":"name","name":"Clawd"}""")
        transport.emit("""{"cmd":"status"}""")

        assertEquals(
            """{"ack":"status","ok":true,"data":{"name":"Clawd","sec":false,"stats":{"appr":0,"deny":0,"vel":0,"nap":0,"lvl":0}}}""",
            transport.sent.last(),
        )
    }

    @Test
    fun `a folder push is acknowledged step by step`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)

        // "hi" -> base64 "aGk=", two bytes.
        transport.emit("""{"cmd":"char_begin","name":"bufo","total":2}""")
        transport.emit("""{"cmd":"file","path":"a.txt","size":2}""")
        transport.emit("""{"cmd":"chunk","d":"aGk="}""")
        transport.emit("""{"cmd":"file_end"}""")
        transport.emit("""{"cmd":"char_end"}""")

        assertEquals(
            listOf(
                """{"ack":"char_begin","ok":true}""",
                """{"ack":"file","ok":true}""",
                """{"ack":"chunk","ok":true,"n":2}""",
                """{"ack":"file_end","ok":true,"n":2}""",
                """{"ack":"char_end","ok":true}""",
            ),
            transport.sent,
        )
    }

    @Test
    fun `the active character pack is loaded on creation`() = runTest {
        val pack = CharacterPack(
            CharacterManifest(name = "bufo", colors = null, states = mapOf("idle" to listOf("idle.gif"))),
            directoryPath = "/packs/bufo",
        )
        val vm = newViewModel(FakeDesktopTransport(), packProvider = { pack })

        assertEquals(pack, vm.characterPack.value)
    }

    @Test
    fun `a completed folder push refreshes the character pack`() = runTest {
        val transport = FakeDesktopTransport()
        var current: CharacterPack? = null
        val vm = newViewModel(transport, packProvider = { current })
        assertNull(vm.characterPack.value)

        // The pack becomes readable only once the desktop finishes sending it.
        current = CharacterPack(CharacterManifest(name = "bufo", colors = null, states = emptyMap()), "/packs/bufo")
        transport.emit("""{"cmd":"char_begin","name":"bufo","total":0}""")
        transport.emit("""{"cmd":"char_end"}""")

        assertEquals(current, vm.characterPack.value)
    }

    @Test
    fun `a folder push leaves the buddy state untouched`() = runTest {
        val transport = FakeDesktopTransport()
        val vm = newViewModel(transport)
        transport.emit(snapshotBusy)

        transport.emit("""{"cmd":"char_begin","name":"bufo","total":2}""")

        assertEquals(BuddyActivity.BUSY, vm.state.value.activity)
        assertEquals("thinking", vm.state.value.statusMessage)
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
