package com.slideindex.app.common

inline fun <T> repositoryRunCatching(block: () -> T): Result<T> = runCatching(block)
