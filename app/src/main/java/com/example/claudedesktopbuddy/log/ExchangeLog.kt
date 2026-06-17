package com.example.claudedesktopbuddy.log

/** Which way a logged line travelled across the wire. */
enum class LogDirection {
    /** desktop -> phone */
    INCOMING,

    /** phone -> desktop */
    OUTGOING,
}

/** One raw protocol line exactly as it crossed the wire, with the direction it travelled. */
data class LogEntry(
    val direction: LogDirection,
    val line: String,
)

/**
 * Immutable record of the raw line traffic exchanged with the desktop, for the logs screen.
 *
 * Logging is **disabled by default**; while disabled, [record] is a no-op so no traffic is
 * captured. The buffer is bounded to [capacity] lines — once full, the oldest entries are dropped
 * so memory stays flat during long sessions.
 *
 * Pure and independent of the protocol model: it stores raw lines, so it captures exactly what was
 * sent or received even if a line fails to parse.
 */
data class ExchangeLog(
    val enabled: Boolean = false,
    val entries: List<LogEntry> = emptyList(),
    val capacity: Int = DEFAULT_CAPACITY,
) {

    init {
        require(capacity > 0) { "capacity must be positive, was $capacity" }
    }

    /**
     * Appends [line] with its [direction] when logging is enabled, evicting the oldest entries
     * beyond [capacity]. When disabled, returns this log unchanged.
     */
    fun record(direction: LogDirection, line: String): ExchangeLog {
        if (!enabled) return this
        val appended = entries + LogEntry(direction, line)
        return copy(entries = appended.takeLast(capacity))
    }

    /** Returns a log with the same settings but no entries. */
    fun cleared(): ExchangeLog = copy(entries = emptyList())

    companion object {
        const val DEFAULT_CAPACITY = 500
    }
}
