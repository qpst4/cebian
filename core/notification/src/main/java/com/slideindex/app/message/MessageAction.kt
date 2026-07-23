package com.slideindex.app.message

enum class MessageAction(val id: Int) {
    Read(0),
    ReadInSmallWindow(1),
    Ignore(2),
    IgnoreAndRemove(3),
    Dnd5Min(4),
    QuickReply(5),
    IgnoreAll(6),
    IgnoreAndRemoveAll(7),
    QuickReplyAndIgnore(8),
    QuickReplyAndRemove(9),
    IgnoreSameSource(10),
    IgnoreSameSourceAndRemove(11),
    ;

    val affectsAllDisplayed: Boolean
        get() = this == IgnoreAll || this == IgnoreAndRemoveAll

    val affectsSameSource: Boolean
        get() = this == IgnoreSameSource || this == IgnoreSameSourceAndRemove

    val opensQuickReply: Boolean
        get() = this == QuickReply || this == QuickReplyAndIgnore || this == QuickReplyAndRemove

    companion object {
        fun fromId(id: Int): MessageAction =
            entries.firstOrNull { it.id == id } ?: Ignore
    }
}
