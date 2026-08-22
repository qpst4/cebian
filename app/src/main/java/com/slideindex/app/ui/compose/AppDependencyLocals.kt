package com.slideindex.app.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.slideindex.app.data.AppRepository
import com.slideindex.app.di.AppDependencies

import com.slideindex.app.settings.SettingsRepository

val LocalAppDependencies = compositionLocalOf<AppDependencies> {
    error("AppDependencies not provided. Wrap content in CompositionLocalProvider(LocalAppDependencies provides deps).")
}

@Composable
fun rememberAppDependencies(): AppDependencies = LocalAppDependencies.current

@Composable
fun rememberAppRepository(): AppRepository = rememberAppDependencies().appRepository

@Composable
fun rememberSettingsRepository(): SettingsRepository = rememberAppDependencies().settingsRepository

