package com.slideindex.app.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayEntryPoint {
    fun overlayDependencies(): OverlayDependencies
}
