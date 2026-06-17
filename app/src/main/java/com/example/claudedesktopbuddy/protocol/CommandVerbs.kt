package com.example.claudedesktopbuddy.protocol

/** The `cmd` values the desktop sends in an [InboundMessage.Command]. */
object CommandVerbs {
    const val STATUS = "status"
    const val NAME = "name"
    const val OWNER = "owner"
    const val UNPAIR = "unpair"

    // Folder push (the desktop's folder-drop stream).
    const val CHAR_BEGIN = "char_begin"
    const val FILE = "file"
    const val CHUNK = "chunk"
    const val FILE_END = "file_end"
    const val CHAR_END = "char_end"
}
