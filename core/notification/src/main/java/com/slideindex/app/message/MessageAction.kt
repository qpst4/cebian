package com.slideindex.app.message

enum class MessageAction(val id: Int) {
    Read(0),
    ReadInSmallWindow(1),
    Ignore(2),
    IgnoreAndRemove(3),
    Dnd5Min(4),
    ;

    companion object {
        fun fromId(id: Int): MessageAction =
            entries.firstOrNull { it.id == id } ?: Ignore
    }
}
