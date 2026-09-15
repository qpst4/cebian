package com.slideindex.app.settings

import android.os.Build

enum class FreeWindowMode(
    val id: Int,
    val windowingMode: Int,
) {
    STANDARD(0, 5),
    COLOROS(1, 100),
    MAGICOS(2, 102),
    ORIGINOS(3, 106),
    FLYME(4, 11),
    NUBIA(5, 5),
    ;

    companion object {
        fun fromId(id: Int): FreeWindowMode =
            entries.firstOrNull { it.id == id } ?: detectDefault()

        fun detectDefault(): FreeWindowMode {
            val manufacturer = Build.MANUFACTURER.lowercase()
            val brand = Build.BRAND.lowercase()
            return when {
                manufacturer.contains("nubia") || brand.contains("nubia") ||
                    manufacturer.contains("redmagic") || brand.contains("redmagic") -> NUBIA
                manufacturer.contains("meizu") || brand.contains("meizu") -> FLYME
                manufacturer.contains("xiaomi") || brand.contains("redmi") -> STANDARD
                manufacturer.contains("oppo") || manufacturer.contains("realme") ||
                    brand.contains("oppo") || brand.contains("realme") -> COLOROS
                manufacturer.contains("vivo") || brand.contains("vivo") -> ORIGINOS
                manufacturer.contains("honor") || manufacturer.contains("huawei") ||
                    brand.contains("honor") || brand.contains("huawei") -> MAGICOS
                else -> STANDARD
            }
        }
    }
}

fun AppSettings.resolvedFreeWindowMode(): FreeWindowMode = FreeWindowMode.fromId(freeWindowModeId)

fun FreeWindowMode.usesNubiaFreeformIdentifier(): Boolean = this == FreeWindowMode.NUBIA
