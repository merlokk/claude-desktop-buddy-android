package com.example.claudedesktopbuddy.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExchangeLogTest {

    @Test
    fun `logging is disabled and empty by default`() {
        val log = ExchangeLog()

        assertFalse(log.enabled)
        assertTrue(log.entries.isEmpty())
    }

    @Test
    fun `record is a no-op while disabled`() {
        val log = ExchangeLog(enabled = false)

        val result = log.record(LogDirection.INCOMING, """{"a":1}""")

        assertTrue(result.entries.isEmpty())
        assertSame("disabled record should return the same instance", log, result)
    }

    @Test
    fun `record appends an entry while enabled`() {
        val log = ExchangeLog(enabled = true)

        val result = log.record(LogDirection.INCOMING, """{"a":1}""")

        assertEquals(listOf(LogEntry(LogDirection.INCOMING, """{"a":1}""")), result.entries)
    }

    @Test
    fun `records preserve order and direction`() {
        val log = ExchangeLog(enabled = true)
            .record(LogDirection.INCOMING, "in-1")
            .record(LogDirection.OUTGOING, "out-1")
            .record(LogDirection.INCOMING, "in-2")

        assertEquals(
            listOf(
                LogEntry(LogDirection.INCOMING, "in-1"),
                LogEntry(LogDirection.OUTGOING, "out-1"),
                LogEntry(LogDirection.INCOMING, "in-2"),
            ),
            log.entries,
        )
    }

    @Test
    fun `the buffer is bounded and drops the oldest entries`() {
        var log = ExchangeLog(enabled = true, capacity = 3)
        repeat(5) { log = log.record(LogDirection.OUTGOING, "line-$it") }

        assertEquals(3, log.entries.size)
        assertEquals(
            listOf("line-2", "line-3", "line-4"),
            log.entries.map { it.line },
        )
    }

    @Test
    fun `cleared removes entries but keeps the enabled flag`() {
        val log = ExchangeLog(enabled = true)
            .record(LogDirection.INCOMING, "in-1")
            .cleared()

        assertTrue(log.entries.isEmpty())
        assertTrue(log.enabled)
    }

    @Test
    fun `disabling keeps existing entries but stops new recording`() {
        val enabled = ExchangeLog(enabled = true).record(LogDirection.INCOMING, "in-1")

        val disabled = enabled.copy(enabled = false)
        val afterRecord = disabled.record(LogDirection.OUTGOING, "out-1")

        assertEquals(1, disabled.entries.size)
        assertEquals(disabled.entries, afterRecord.entries)
    }

    @Test
    fun `a non-positive capacity is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExchangeLog(capacity = 0)
        }
    }
}
