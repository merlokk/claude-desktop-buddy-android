package com.example.claudedesktopbuddy.protocol

/** The `cmd` values the desktop sends in an [InboundMessage.Command]. */
object CommandVerbs {
    const val STATUS = "status"
    const val NAME = "name"
    const val OWNER = "owner"
    const val UNPAIR = "unpair"
}
