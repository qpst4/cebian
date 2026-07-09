package com.slideindex.app.message

enum class MessageStyle(val id: String) {
    FloatIcon("float_icon"),
    DarkCard("dark_card"),
    SideBubble("side_bubble"),
    Danmaku("danmaku"),
    ;

    companion object {
        fun fromId(id: String): MessageStyle =
            entries.firstOrNull { it.id == id } ?: DarkCard
    }
}
