package com.slideindex.app.update

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateWorkerEntryPoint {
    fun updateRepository(): UpdateRepository

    fun updatePreferencesStore(): UpdatePreferencesStore
}
