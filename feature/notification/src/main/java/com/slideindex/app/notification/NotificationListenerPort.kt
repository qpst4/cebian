package com.slideindex.app.notification

import android.service.notification.NotificationListenerService

/** Access to the bound [NotificationListenerService] from the app process. */
interface NotificationListenerPort {
    fun listenerOrNull(): NotificationListenerService?

    /**
     * 当前通知栏通知的快照。
     *
     * 监听服务只在 `:overlay` 进程里存在，UI 在主进程：[listenerOrNull] 在主进程永远是 null，
     * 「实时」tab 就会长期空着。实现方需要在跨进程场景下返回 overlay 进程广播过来的镜像。
     *
     * null 表示「本进程暂时拿不到镜像」，调用方应回退到 [listenerOrNull]。
     */
    fun activeNotificationSnapshotsOrNull(): List<ActiveNotificationSnapshot>? = null
}
