package com.slideindex.app.nativeengine

sealed interface NativeEnginePackUpgradeResult {
    data object UpToDate : NativeEnginePackUpgradeResult

    /** 本地此前未安装该引擎包，已完成首次内置解压。 */
    data object FreshlyProvisioned : NativeEnginePackUpgradeResult

    data class Upgraded(
        val displayVersion: String?,
        val previousRevision: Int?,
    ) : NativeEnginePackUpgradeResult

    data class UpgradeFailed(
        val displayVersion: String?,
        val targetRevision: Int,
    ) : NativeEnginePackUpgradeResult
}
