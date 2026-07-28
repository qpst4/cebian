package com.slideindex.app.nativeengine

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NativeEngineEntryPoint {
    fun nativeEnginePackDownloader(): NativeEnginePackDownloader
}
