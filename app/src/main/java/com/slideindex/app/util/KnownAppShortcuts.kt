package com.slideindex.app.util

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.slideindex.app.overlay.TaskSwitcherMenuItem
import com.slideindex.app.overlay.TaskSwitcherMenuItemType

/**
 * Hard-coded desktop-equivalent shortcuts for WeChat / QQ / Alipay when ShortcutManager
 * and Shizuku queries return nothing on many ROMs.
 */
internal object KnownAppShortcuts {
    private const val WECHAT = "com.tencent.mm"
    private const val QQ = "com.tencent.mobileqq"
    private const val TIM = "com.tencent.tim"
    private const val ALIPAY = "com.eg.android.AlipayGphone"

    private val supportedPackages = setOf(WECHAT, QQ, TIM, ALIPAY)

    fun supports(packageName: String): Boolean = packageName in supportedPackages

    fun packageForIntentUri(intentUri: String): String? {
        val target = runCatching {
            Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return null
        supportedPackages.forEach { packageName ->
            load(packageName).forEach { item ->
                item.shortcutIntent?.let { shortcutIntent ->
                    if (intentsMatchForIcon(target, shortcutIntent)) return packageName
                }
            }
        }
        return null
    }

    private fun intentsMatchForIcon(left: Intent, right: Intent): Boolean {
        if (left.action != right.action) return false
        if (!dataMatches(left.data, right.data)) return false
        val leftComponent = left.component
        val rightComponent = right.component
        if (leftComponent != null || rightComponent != null) {
            return leftComponent == rightComponent
        }
        return true
    }

    private fun dataMatches(left: Uri?, right: Uri?): Boolean {
        if (left == null && right == null) return true
        if (left == null || right == null) return false
        return left.toString() == right.toString()
    }

    fun load(packageName: String): List<TaskSwitcherMenuItem> {
        return when (packageName) {
            WECHAT -> weChatShortcuts()
            QQ, TIM -> qqShortcuts(packageName)
            ALIPAY -> alipayShortcuts()
            else -> emptyList()
        }
    }

    private fun weChatShortcuts(): List<TaskSwitcherMenuItem> {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return listOf(
            item(
                id = "wechat_scan",
                label = "扫一扫",
                intent = Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName(WECHAT, "com.tencent.mm.ui.LauncherUI")
                    putExtra("LauncherUI.From.Scaner.Shortcut", true)
                    this.flags = flags
                },
            ),
            item(
                id = "wechat_my_qrcode",
                label = "我的二维码",
                intent = Intent(Intent.ACTION_VIEW, "weixin://dl/myQRcode".toUri()).apply {
                    setPackage(WECHAT)
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            ),
            item(
                id = "wechat_pay",
                label = "收付款",
                intent = Intent(Intent.ACTION_VIEW).apply {
                    component = ComponentName(WECHAT, "com.tencent.mm.plugin.offline.ui.WalletOfflineCoinPurseUI")
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
            ),
        )
    }

    private fun qqShortcuts(packageName: String): List<TaskSwitcherMenuItem> {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return listOf(
            item(
                id = "qq_scan",
                label = "扫一扫",
                intent = Intent(
                    Intent.ACTION_VIEW,
                    "mqqapi://qrcode/scan_qrcode?version=1&src_type=app".toUri(),
                ).apply {
                    setPackage(packageName)
                    this.flags = flags
                },
            ),
            item(
                id = "qq_my_qrcode",
                label = "我的二维码",
                intent = Intent(
                    Intent.ACTION_VIEW,
                    "mqqapi://qrcode/showcard?version=1&src_type=internal".toUri(),
                ).apply {
                    setPackage(packageName)
                    this.flags = flags
                },
            ),
            item(
                id = "qq_pay",
                label = "收付款",
                intent = Intent(
                    Intent.ACTION_VIEW,
                    "mqqapi://wallet/pay?version=1&src_type=app".toUri(),
                ).apply {
                    setPackage(packageName)
                    this.flags = flags
                },
            ),
        )
    }

    private fun alipayShortcuts(): List<TaskSwitcherMenuItem> {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return listOf(
            item("alipay_scan", "扫一扫", viewUri("alipayqr://platformapi/startapp?saId=10000007", flags)),
            item("alipay_pay", "付款", viewUri("alipayqr://platformapi/startapp?saId=20000056", flags)),
            item("alipay_transfer", "转账", viewUri("alipayqr://platformapi/startapp?saId=20000116", flags)),
        )
    }

    private fun viewUri(uri: String, flags: Int): Intent {
        return Intent(Intent.ACTION_VIEW, uri.toUri()).apply { this.flags = flags }
    }

    private fun item(id: String, label: String, intent: Intent): TaskSwitcherMenuItem {
        return TaskSwitcherMenuItem(
            label = label,
            type = TaskSwitcherMenuItemType.SHORTCUT,
            shortcutId = id,
            shortcutIntent = intent,
        )
    }
}
