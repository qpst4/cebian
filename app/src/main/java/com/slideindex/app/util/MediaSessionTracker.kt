package com.slideindex.app.util

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import com.slideindex.app.notification.NotificationListenerPort
import com.slideindex.app.service.MediaNotificationListener
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Live media-session snapshot for the quick-tools panel. Updated via [MediaController.Callback]
 * when [MediaNotificationListener] is enabled, otherwise refreshed on demand by [MediaSessionHelper].
 */
object MediaSessionTracker {
    data class Snapshot(
        val packageName: String?,
        val isPlaying: Boolean,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(Snapshot) -> Unit>()
    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    @Volatile
    private var snapshot: Snapshot = Snapshot(packageName = null, isPlaying = false)

    fun currentSnapshot(): Snapshot = snapshot

    fun addListener(listener: (Snapshot) -> Unit) {
        listeners.add(listener)
        listener(snapshot)
    }

    fun removeListener(listener: (Snapshot) -> Unit) {
        listeners.remove(listener)
    }

    fun onListenerConnected(service: NotificationListenerService) {
        refreshControllers(service, service.packageName)
    }

    fun onListenerDisconnected() {
        detachControllers()
        publish(Snapshot(null, false))
    }

    fun onNotificationsChanged(service: NotificationListenerService) {
        refreshControllers(service, service.packageName)
    }

    fun refreshIfPossible(context: Context, listenerPort: NotificationListenerPort? = null) {
        val appContext = context.applicationContext
        val selfPackage = appContext.packageName
        val service = listenerPort?.listenerOrNull()
        if (service != null) {
            refreshControllers(service, selfPackage)
            return
        }
        if (!MediaSessionHelper.isNotificationListenerEnabled(appContext)) return
        val manager = appContext.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(appContext, MediaNotificationListener::class.java)
        val controllers = runCatching { manager.getActiveSessions(component) }.getOrElse { emptyList() }
        publish(pickBest(controllers, selfPackage))
    }

    private fun refreshControllers(service: NotificationListenerService, selfPackage: String) {
        val manager = service.getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(service, MediaNotificationListener::class.java)
        val controllers = runCatching { manager.getActiveSessions(component) }.getOrElse { emptyList() }
        attachControllers(controllers, selfPackage)
        publish(pickBest(controllers, selfPackage))
    }

    private fun attachControllers(controllers: List<MediaController>, selfPackage: String) {
        val keep = controllers.toSet()
        controllerCallbacks.keys.filter { it !in keep }.forEach { detachController(it) }
        for (controller in controllers) {
            if (controllerCallbacks.containsKey(controller)) continue
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    publish(pickBest(controllerCallbacks.keys.toList(), selfPackage))
                }
            }
            runCatching { controller.registerCallback(callback) }
            controllerCallbacks[controller] = callback
        }
    }

    private fun detachController(controller: MediaController) {
        controllerCallbacks.remove(controller)?.let { callback ->
            runCatching { controller.unregisterCallback(callback) }
        }
    }

    private fun detachControllers() {
        controllerCallbacks.toMap().forEach { (controller, callback) ->
            runCatching { controller.unregisterCallback(callback) }
        }
        controllerCallbacks.clear()
    }

    private fun pickBest(controllers: Collection<MediaController>, selfPackage: String): Snapshot {
        var bestPkg: String? = null
        var bestScore = Int.MIN_VALUE
        var bestPlaying = false
        for (controller in controllers) {
            val pkg = controller.packageName ?: continue
            if (MediaSessionHelper.isIgnoredMediaPackage(pkg, selfPackage)) continue
            val state = controller.playbackState?.state ?: PlaybackState.STATE_NONE
            val score = MediaSessionHelper.playbackScore(state)
            if (score > bestScore) {
                bestScore = score
                bestPkg = pkg
                bestPlaying = MediaSessionHelper.isPlayingState(state)
            }
        }
        return if (bestPkg != null && bestScore > 0) {
            Snapshot(bestPkg, bestPlaying)
        } else {
            Snapshot(null, false)
        }
    }

    private fun publish(next: Snapshot) {
        snapshot = next
        mainHandler.post {
            listeners.forEach { it(next) }
        }
    }
}
