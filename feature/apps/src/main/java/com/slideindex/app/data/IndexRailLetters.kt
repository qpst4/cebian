package com.slideindex.app.data

object IndexRailLetters {
    val full: List<Char> = ('A'..'Z').toList() + '#'

    fun resolve(presentLetters: Collection<Char>, hideEmpty: Boolean): List<Char> {
        if (!hideEmpty) return full
        val present = presentLetters.toSet()
        return buildList {
            for (letter in 'A'..'Z') {
                if (letter in present) add(letter)
            }
            if ('#' in present) add('#')
        }.ifEmpty { full }
    }
}
