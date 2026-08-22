package com.slideindex.app.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLauncherMergeFolderTest {

    @Test
    fun testMergeTwoAppsCreatesFolder() {
        val itemA = QuickLauncherItem.app("com.app.a", "App A")
        val itemB = QuickLauncherItem.app("com.app.b", "App B")
        val itemC = QuickLauncherItem.app("com.app.c", "App C")
        val items = listOf(itemA, itemB, itemC)

        // Drag itemA (index 0) over itemB (index 1)
        val merged = items.mergeIntoFolder(from = 0, target = 1, defaultFolderLabel = "")
        assertEquals(2, merged.size)

        val folder = merged[0]
        assertTrue(folder.isFolder)
        val children = folder.folderItems()
        assertEquals(2, children.size)
        assertEquals("com.app.b", children[0].payload)
        assertEquals("com.app.a", children[1].payload)
        assertEquals(itemC, merged[1])
    }

    @Test
    fun testMergeAppIntoExistingFolder() {
        val itemA = QuickLauncherItem.app("com.app.a", "App A")
        val itemB = QuickLauncherItem.app("com.app.b", "App B")
        val folder = QuickLauncherItem.folder(label = "Tools", items = listOf(itemA))
        val itemC = QuickLauncherItem.app("com.app.c", "App C")
        val items = listOf(folder, itemB, itemC)

        // Drag itemC (index 2) into folder (index 0)
        val merged = items.mergeIntoFolder(from = 2, target = 0)
        assertEquals(2, merged.size)

        val targetFolder = merged[0]
        assertTrue(targetFolder.isFolder)
        assertEquals("Tools", targetFolder.label)
        val children = targetFolder.folderItems()
        assertEquals(2, children.size)
        assertEquals("com.app.a", children[0].payload)
        assertEquals("com.app.c", children[1].payload)
        assertEquals(itemB, merged[1])
    }

    @Test
    fun testDisallowNestedFolderMerge() {
        val folder1 = QuickLauncherItem.folder(label = "F1", items = listOf(QuickLauncherItem.app("com.app.a")))
        val folder2 = QuickLauncherItem.folder(label = "F2", items = listOf(QuickLauncherItem.app("com.app.b")))
        val items = listOf(folder1, folder2)

        // Attempting to merge folder1 into folder2 should be rejected
        val merged = items.mergeIntoFolder(from = 0, target = 1)
        assertEquals(items, merged)
    }

    @Test
    fun testOutOfBoundsOrSelfMerge() {
        val itemA = QuickLauncherItem.app("com.app.a", "App A")
        val items = listOf(itemA)

        // Same index
        assertEquals(items, items.mergeIntoFolder(from = 0, target = 0))
        // Out of bounds
        assertEquals(items, items.mergeIntoFolder(from = 0, target = 5))
        assertEquals(items, items.mergeIntoFolder(from = -1, target = 0))
    }
}
