package com.slideindex.app.di

import com.slideindex.app.ocr.vlm.VlmOcrConfigManager
import com.slideindex.app.translate.CloudLlmTranslateConfig
import com.slideindex.app.translate.CloudLlmTranslateCredentials
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CloudTranslateModule {

    @Provides
    @Singleton
    fun provideCloudLlmTranslateConfig(
        vlmOcrConfigManager: VlmOcrConfigManager,
    ): CloudLlmTranslateConfig = CloudLlmTranslateConfig {
        val creds = vlmOcrConfigManager.activeTranslateCredentials()
        CloudLlmTranslateCredentials(
            apiKey = creds.apiKey,
            baseUrl = creds.baseUrl,
            model = creds.model,
        )
    }
}
