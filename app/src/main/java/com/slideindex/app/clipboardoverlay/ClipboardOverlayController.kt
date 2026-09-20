package com.slideindex.app.clipboardoverlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.WindowInsets
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.slideindex.app.R
import com.slideindex.app.clipboard.ClipboardPayload
import com.slideindex.app.overlay.PickResultFromHistoryCoordinator
import java.util.concurrent.Executors

/**
 * Port of AOSP `ClipboardOverlayController`, rewritten onto public WindowManager APIs.
 */
internal class ClipboardOverlayController(
    private val context: Context,
    private val view: ClipboardOverlayView,
    private val onRemoved: () -> Unit,
) : ClipboardOverlayView.ClipboardOverlayCallbacks {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val timeoutRunnable = Runnable { animateOut() }

    private var clipboardModel: ClipboardModel? = null
    private var clipboardPayload: ClipboardPayload? = null
    private var enterAnimator: Animator? = null
    private var exitAnimator: Animator? = null
    private var isMinimized = false
    private var showingUi = false
    private var onShareTapped: (() -> Unit)? = null
    private var onRemoteCopyTapped: (() -> Unit)? = null
    private var onPreviewTapped: (() -> Unit)? = null
    private var closeDialogsReceiver: BroadcastReceiver? = null
    private var removed = false

    init {
        view.setCallbacks(this)
        view.setInsets(currentInsets(), context.resources.configuration.orientation)
        closeDialogsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS == intent.action) {
                    animateOut()
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            closeDialogsReceiver,
            IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun setClipData(payload: ClipboardPayload) {
        val model = ClipboardModel.fromPayload(context, payload)
        clipboardPayload = payload
        val wasExiting = exitAnimator?.isRunning == true
        if (wasExiting) exitAnimator?.cancel()
        val shouldAnimate = !model.dataMatches(clipboardModel) || wasExiting
        clipboardModel = model
        if (shouldAnimate) {
            reset()
            if (shouldShowMinimized(currentInsets())) {
                isMinimized = true
                view.setMinimized(true)
                animateIn()
            } else {
                setExpandedView { animateIn() }
            }
            view.announceForAccessibility(getAccessibilityAnnouncement(model.type))
        } else if (!isMinimized) {
            setExpandedView {}
        }
        resetTimeout()
    }

    fun onWindowInsetsChanged(insets: WindowInsets) {
        view.setInsets(insets, context.resources.configuration.orientation)
        if (shouldShowMinimized(insets) && !isMinimized) {
            isMinimized = true
            view.setMinimized(true)
        } else if (!shouldShowMinimized(insets) && isMinimized && showingUi) {
            animateFromMinimized()
        }
    }

    private fun setExpandedView(onViewReady: () -> Unit) {
        val model = clipboardModel ?: return
        view.setMinimized(false)
        when (model.type) {
            ClipboardModel.Type.TEXT -> {
                classifyText(model)
                if (model.isSensitive) {
                    view.showTextPreview(context.getString(R.string.clipboard_overlay_asterisks), hidden = true)
                } else {
                    view.showTextPreview(model.text?.toString().orEmpty(), hidden = false)
                }
                view.setEditAccessibilityAction(true)
                onPreviewTapped = { openPickPanel() }
                onViewReady()
            }
            ClipboardModel.Type.IMAGE -> {
                view.setEditAccessibilityAction(true)
                onPreviewTapped = { openPickPanel() }
                if (model.isSensitive) {
                    view.showImagePreview(null)
                    onViewReady()
                } else {
                    bgExecutor.execute {
                        val bitmap = model.loadThumbnail(context, view.previewSizePx)
                        view.post {
                            if (bitmap == null) {
                                view.showDefaultTextPreview()
                            } else {
                                view.showImagePreview(bitmap)
                            }
                            onViewReady()
                        }
                    }
                }
            }
            ClipboardModel.Type.URI, ClipboardModel.Type.OTHER -> {
                view.showDefaultTextPreview()
                view.setEditAccessibilityAction(true)
                onPreviewTapped = { openPickPanel() }
                onViewReady()
            }
        }
        maybeShowRemoteCopy(model.clipData)
        if (model.type != ClipboardModel.Type.OTHER) {
            onShareTapped = { shareContent(model.clipData) }
            view.showShareChip()
        }
    }

    private fun shouldShowMinimized(insets: WindowInsets): Boolean =
        insets.getInsets(WindowInsets.Type.ime()).bottom > 0

    private fun animateFromMinimized() {
        if (enterAnimator?.isRunning == true) enterAnimator?.cancel()
        enterAnimator = view.getMinimizedFadeoutAnimation()
        enterAnimator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (isMinimized) isMinimized = false
                setExpandedView { animateIn() }
            }
        })
        enterAnimator?.start()
    }

    private fun getAccessibilityAnnouncement(type: ClipboardModel.Type): String = when (type) {
        ClipboardModel.Type.TEXT -> context.getString(R.string.clipboard_overlay_text_copied)
        ClipboardModel.Type.IMAGE -> context.getString(R.string.clipboard_overlay_image_copied)
        else -> context.getString(R.string.clipboard_overlay_content_copied)
    }

    private fun classifyText(model: ClipboardModel) {
        if (model.isSensitive) return
        val text = model.text?.toString().orEmpty()
        if (text.isBlank()) return
        bgExecutor.execute {
            val spec = runCatching { ClipboardOverlaySmartActions.resolve(context, text) }.getOrNull()
                ?: return@execute
            if (model == clipboardModel) {
                view.post { bindSmartAction(spec) }
            }
        }
    }

    private fun bindSmartAction(spec: OverlaySmartActionSpec) {
        val icon = ContextCompat.getDrawable(context, spec.iconRes)
        view.setActionChip(icon, spec.label, spec.label) {
            when (val launch = spec.launch) {
                is OverlaySmartLaunch.Activity -> {
                    runCatching { context.startActivity(launch.intent) }
                        .onSuccess { animateOut() }
                        .onFailure {
                            Toast.makeText(context, R.string.float_ball_action_failed, Toast.LENGTH_SHORT).show()
                        }
                }
                is OverlaySmartLaunch.Translate -> {
                    PickResultFromHistoryCoordinator.openFromClipboardPayload(
                        context,
                        clipboardPayload,
                        autoTranslate = true,
                    )
                    animateOut()
                }
                is OverlaySmartLaunch.PickUrls -> {
                    val urls = launch.urls
                    hideImmediate()
                    ClipboardLinkPickerOverlay.show(context, urls)
                }
            }
        }
    }

    private fun maybeShowRemoteCopy(clipData: ClipData) {
        val remoteCopyIntent = ClipboardOverlayIntents.resolveRemoteCopyIntent(clipData, context)
        if (remoteCopyIntent != null) {
            view.setRemoteCopyVisibility(true)
            onRemoteCopyTapped = {
                context.startActivity(remoteCopyIntent)
                animateOut()
            }
        } else {
            view.setRemoteCopyVisibility(false)
        }
    }

    private fun openPickPanel() {
        val model = clipboardModel
        if (model == null || model.isSensitive) {
            animateOut()
            return
        }
        PickResultFromHistoryCoordinator.openFromClipboardPayload(context, clipboardPayload)
        animateOut()
    }

    private fun shareContent(clip: ClipData) {
        context.startActivity(ClipboardOverlayIntents.getShareIntent(clip, context))
        animateOut()
    }

    private fun animateIn() {
        if (enterAnimator?.isRunning == true) return
        enterAnimator = view.getEnterAnimation()
        enterAnimator?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                showingUi = true
            }
            override fun onAnimationEnd(animation: Animator) {
                if (isMinimized && !shouldShowMinimized(currentInsets())) {
                    animateFromMinimized()
                }
                resetTimeout()
            }
        })
        enterAnimator?.start()
    }

    fun animateOut() {
        if (exitAnimator?.isRunning == true) return
        exitAnimator = view.getExitAnimation()
        exitAnimator?.addListener(object : AnimatorListenerAdapter() {
            private var cancelled = false
            override fun onAnimationCancel(animation: Animator) {
                cancelled = true
            }
            override fun onAnimationEnd(animation: Animator) {
                if (!cancelled) hideImmediate()
            }
        })
        exitAnimator?.start()
    }

    fun hideImmediate() {
        if (removed) return
        removed = true
        mainHandler.removeCallbacks(timeoutRunnable)
        closeDialogsReceiver?.let { receiver ->
            runCatching { context.unregisterReceiver(receiver) }
            closeDialogsReceiver = null
        }
        bgExecutor.shutdownNow()
        onRemoved()
    }

    private fun reset() {
        onRemoteCopyTapped = null
        onShareTapped = null
        onPreviewTapped = null
        showingUi = false
        view.reset()
        mainHandler.removeCallbacks(timeoutRunnable)
    }

    private fun resetTimeout() {
        mainHandler.removeCallbacks(timeoutRunnable)
        mainHandler.postDelayed(timeoutRunnable, CLIPBOARD_DEFAULT_TIMEOUT_MILLIS)
    }

    private fun currentInsets(): WindowInsets =
        context.getSystemService(android.view.WindowManager::class.java)
            .currentWindowMetrics.windowInsets

    override fun onDismissButtonTapped() = animateOut()

    override fun onRemoteCopyButtonTapped() {
        onRemoteCopyTapped?.invoke()
    }

    override fun onShareButtonTapped() {
        onShareTapped?.invoke()
    }

    override fun onPreviewTapped() {
        onPreviewTapped?.invoke()
    }

    override fun onMinimizedViewTapped() {
        animateFromMinimized()
    }

    override fun onTapOutside() {
        animateOut()
    }

    override fun onInteraction() {
        resetTimeout()
    }

    override fun onSwipeDismissInitiated(animator: Animator) {
        if (exitAnimator?.isRunning == true) exitAnimator?.cancel()
        exitAnimator = animator
        mainHandler.removeCallbacks(timeoutRunnable)
    }

    override fun onDismissComplete() {
        hideImmediate()
    }

    companion object {
        private const val CLIPBOARD_DEFAULT_TIMEOUT_MILLIS = 6000L
    }
}
