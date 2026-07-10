package com.slideindex.app.message

/** Resolves foreground app package for message suppression rules. */
interface MessageForegroundPort {
    fun foregroundPackage(): String?
}
