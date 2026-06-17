package com.example.claudedesktopbuddy.buddy

/**
 * Destination for a folder pushed by the desktop (the Hardware Buddy window's drop target).
 *
 * The protocol is sequential — one file at a time, one chunk at a time — so the sink is a simple
 * streaming writer: [beginFile] opens a file, [write] appends decoded bytes to it, [endFile] closes
 * it, all bracketed by [beginPack] / [endPack]. Framework-free so the receiver can be unit-tested on
 * the JVM with a fake; the Android implementation persists to app storage.
 */
interface CharacterPackSink {

    /** A new pack named [name] is starting; [totalBytes] is its declared total size across all files. */
    fun beginPack(name: String, totalBytes: Long)

    /** A new file at the pack-relative [path] is starting; [sizeBytes] is its declared size. */
    fun beginFile(path: String, sizeBytes: Long)

    /** Appends the next decoded slice of the current file. */
    fun write(bytes: ByteArray)

    /** Closes the current file. */
    fun endFile()

    /** The pack is complete. */
    fun endPack()

    companion object {
        /** A sink that discards everything — used by default and in tests. */
        val NoOp = object : CharacterPackSink {
            override fun beginPack(name: String, totalBytes: Long) {}
            override fun beginFile(path: String, sizeBytes: Long) {}
            override fun write(bytes: ByteArray) {}
            override fun endFile() {}
            override fun endPack() {}
        }
    }
}
