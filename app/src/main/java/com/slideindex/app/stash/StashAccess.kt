package com.slideindex.app.stash

object StashAccess {
    @Volatile
    var repository: StashRepository? = null
}
