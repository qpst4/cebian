package com.slideindex.app.ui.viewmodel

import com.slideindex.app.settings.AppSettings

internal fun mergeOptimisticSettings(
    repositorySettings: AppSettings,
    optimisticTransform: ((AppSettings) -> AppSettings)?,
): AppSettings = optimisticTransform?.invoke(repositorySettings) ?: repositorySettings
