package com.slideindex.app.shell

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellCommandNormalizationTest {
    @Test
    fun leavesPlainCommandUnchanged() {
        assertEquals("pm list packages", normalizeShellCommand("pm list packages"))
    }

    @Test
    fun stripsAdbShellPrefix() {
        assertEquals("pm list packages", normalizeShellCommand("adb shell pm list packages"))
    }

    @Test
    fun stripsAdbShellPrefixCaseInsensitive() {
        assertEquals("ls", normalizeShellCommand("ADB SHELL ls"))
    }

    @Test
    fun stripsAdbSerialShellPrefix() {
        assertEquals(
            "pm list packages",
            normalizeShellCommand("adb -s emulator-5554 shell pm list packages"),
        )
    }

    @Test
    fun stripsAdbDeviceFlagShellPrefix() {
        assertEquals("id", normalizeShellCommand("adb -d shell id"))
        assertEquals("id", normalizeShellCommand("adb -e shell id"))
    }

    @Test
    fun trimsOuterWhitespace() {
        assertEquals("pm list packages", normalizeShellCommand("  adb shell pm list packages  "))
    }

    @Test
    fun resolveShellCommandLabelUsesExplicitLabel() {
        assertEquals(
            "My command",
            resolveShellCommandLabel("  My command  ", "pm list packages"),
        )
    }

    @Test
    fun resolveShellCommandLabelAutoGeneratesFromCommand() {
        assertEquals(
            "pm list packages",
            resolveShellCommandLabel("", "pm list packages"),
        )
    }

    @Test
    fun resolveShellCommandLabelStripsAdbShellPrefix() {
        assertEquals(
            "pm list packages",
            resolveShellCommandLabel("", "adb shell pm list packages"),
        )
    }

    @Test
    fun resolveShellCommandLabelTruncatesLongCommand() {
        val longCommand = "settings put secure example_key " + "x".repeat(40)
        val resolved = resolveShellCommandLabel("", longCommand)
        assertEquals(25, resolved.length)
        assertEquals(true, resolved.endsWith("…"))
    }

    @Test
    fun resolveShellCommandLabelUsesFirstLineOnly() {
        assertEquals(
            "first",
            resolveShellCommandLabel("", "first\nsecond"),
        )
    }

    @Test
    fun withNormalizedCommandCopiesOtherFields() {
        val original = ShellCommand(
            id = "id-1",
            label = "Grant",
            command = "adb shell pm grant com.example android.permission.CAMERA",
            iconType = ShellCommandIconType.TEXT,
            textIcon = "G",
        )
        val normalized = original.withNormalizedCommand()
        assertEquals(original.id, normalized.id)
        assertEquals(original.label, normalized.label)
        assertEquals("pm grant com.example android.permission.CAMERA", normalized.command)
    }
}
