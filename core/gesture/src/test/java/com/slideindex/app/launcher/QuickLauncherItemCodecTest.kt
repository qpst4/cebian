package com.slideindex.app.launcher

import com.slideindex.app.gesture.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickLauncherItemCodecTest {

    @Test
    fun encodeDecode_roundTrip_preservesItem() {
        val original = QuickLauncherItem.app("com.example.app", "示例")

        val decoded = QuickLauncherItemCodec.decode(QuickLauncherItemCodec.encode(original))

        assertEquals(original, decoded)
    }

    @Test
    fun encodeAll_decodeAll_roundTrip_preservesList() {
        val items = listOf(
            QuickLauncherItem.app("com.one", "One"),
            QuickLauncherItem.dynamicShortcut("com.two", "shortcut-id", "Two"),
            QuickLauncherItem.action(GestureAction.Back, "返回"),
        )

        val decoded = QuickLauncherItemCodec.decodeAll(QuickLauncherItemCodec.encodeAll(items))

        assertEquals(items, decoded)
    }

    @Test
    fun parseActionPayload_roundTrip() {
        val action = GestureAction.LaunchApp("com.example.app")

        val payload = QuickLauncherItemCodec.encodeActionPayload(action)
        val parsed = QuickLauncherItemCodec.parseActionPayload(payload)

        assertEquals(action, parsed)
    }

    @Test
    fun parseIntentPayload_extractsUri() {
        val item = QuickLauncherItem.intentShortcut("#Intent;action=android.intent.action.VIEW;end", "打开")

        val uri = QuickLauncherItemCodec.parseIntentPayload(item.payload)

        assertEquals("#Intent;action=android.intent.action.VIEW;end", uri)
    }

    @Test
    fun parseIntentListPayload_extractsAllUris() {
        val item = QuickLauncherItem.intentShortcuts(
            listOf(
                "#Intent;action=one;end",
                "#Intent;action=two;end",
            ),
            "多意图",
        )

        val uris = QuickLauncherItemCodec.parseIntentListPayload(item.payload)

        assertEquals(
            listOf("#Intent;action=one;end", "#Intent;action=two;end"),
            uris,
        )
    }

    @Test
    fun shortcutItemKey_distinguishesShortcutKinds() {
        val dynamic = QuickLauncherItem.dynamicShortcut("com.app", "id-1")
        val intent = QuickLauncherItem.intentShortcut("#Intent;action=view;end")

        assertTrue(QuickLauncherItemCodec.shortcutItemKey(dynamic)!!.contains("com.app"))
        assertEquals("intent:#Intent;action=view;end", QuickLauncherItemCodec.shortcutItemKey(intent))
    }

    @Test
    fun folderItem_roundTrip_preservesChildrenAndName() {
        val child1 = QuickLauncherItem.app("com.tencent.mm", "微信")
        val child2 = QuickLauncherItem.action(GestureAction.Back, "返回")
        val child3 = QuickLauncherItem.dynamicShortcut("com.eg.android.AlipayGphone", "scan", "扫一扫")
        val folder = QuickLauncherItem.folder("常用工具", listOf(child1, child2, child3))

        assertEquals(QuickLauncherItemType.FOLDER, folder.type)
        assertEquals("常用工具", folder.label)
        val decodedChildren = folder.folderItems()
        assertEquals(3, decodedChildren.size)
        assertEquals(child1, decodedChildren[0])
        assertEquals(child2, decodedChildren[1])
        assertEquals(child3, decodedChildren[2])

        val encoded = QuickLauncherItemCodec.encode(folder)
        val decoded = QuickLauncherItemCodec.decode(encoded)
        assertEquals(folder, decoded)
        assertEquals(listOf(child1, child2, child3), decoded?.folderItems())
    }

    @Test
    fun folderItem_antiNesting_preventsFoldersInsideFolders() {
        val childFolder = QuickLauncherItem.folder("子文件夹", listOf(QuickLauncherItem.app("com.app", "App")))
        val normalChild = QuickLauncherItem.app("com.app.main", "Main")
        val parentFolder = QuickLauncherItem.folder("父文件夹", listOf(childFolder, normalChild))

        val children = parentFolder.folderItems()
        assertEquals(1, children.size)
        assertEquals(normalChild, children[0])
    }

    @Test
    fun folderItem_mutators_workAsExpected() {
        val initial = QuickLauncherItem.folder("初始", listOf(QuickLauncherItem.app("com.a", "A")))
        val renamed = initial.withFolderLabel("重命名")
        assertEquals("重命名", renamed.label)
        assertEquals(1, renamed.folderItems().size)

        val updatedItems = renamed.withFolderItems(
            listOf(
                QuickLauncherItem.app("com.a", "A"),
                QuickLauncherItem.app("com.b", "B"),
            ),
        )
        assertEquals("重命名", updatedItems.label)
        assertEquals(2, updatedItems.folderItems().size)
    }
}
