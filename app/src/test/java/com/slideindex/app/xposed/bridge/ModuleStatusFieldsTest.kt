package com.slideindex.app.xposed.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleStatusFieldsTest {
    private val currentDetail =
        "ready:hook=ok,proxy=ok,enable=ok,start=ok,receiver=ok,controller=ready:4,groups=0," +
            "code=${ModuleHookBridgeContract.MODULE_CODE_VERSION},clip=ok"

    @Test
    fun codeState_reportsCurrentWhenCodeMatchesAndApkNotReplacedAfterBoot() {
        assertEquals(
            ModuleStatusFields.CodeState.Current,
            ModuleStatusFields.codeState(currentDetail, apkInstalledAfterBoot = false),
        )
    }

    @Test
    fun codeState_reportsStaleWhenApkInstalledAfterBootEvenIfCodeMatches() {
        assertEquals(
            ModuleStatusFields.CodeState.Stale,
            ModuleStatusFields.codeState(currentDetail, apkInstalledAfterBoot = true),
        )
    }

    @Test
    fun codeState_reportsStaleWhenCodeVersionDiffers() {
        val outdated = "ready:hook=ok,code=${ModuleHookBridgeContract.MODULE_CODE_VERSION - 1},clip=ok"
        assertEquals(
            ModuleStatusFields.CodeState.Stale,
            ModuleStatusFields.codeState(outdated, apkInstalledAfterBoot = false),
        )
    }

    @Test
    fun codeState_reportsStaleWhenCodeFieldMissing() {
        assertEquals(
            ModuleStatusFields.CodeState.Stale,
            ModuleStatusFields.codeState("ready:hook=ok,clip=ok", apkInstalledAfterBoot = false),
        )
    }

    @Test
    fun codeState_reportsUnknownWhenModuleDidNotRespond() {
        assertEquals(
            ModuleStatusFields.CodeState.Unknown,
            ModuleStatusFields.codeState(null, apkInstalledAfterBoot = true),
        )
        assertEquals(
            ModuleStatusFields.CodeState.Unknown,
            ModuleStatusFields.codeState(
                ModuleBridgeStatusProbe.NO_RESPONSE,
                apkInstalledAfterBoot = true,
            ),
        )
    }

    @Test
    fun isInstalledAfterBoot_comparesUpdateTimeAgainstBootWallClock() {
        val nowMs = 1_800_000_000_000L
        val uptimeMs = 60_000L
        // 开机前安装：开机时刻 = now - uptime，早于安装时刻才算"开机后覆盖安装"。
        assertFalse(ModuleStatusFields.isInstalledAfterBoot(nowMs - uptimeMs - 1L, nowMs, uptimeMs))
        assertTrue(ModuleStatusFields.isInstalledAfterBoot(nowMs - uptimeMs + 1L, nowMs, uptimeMs))
    }
}
