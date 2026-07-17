package com.slideindex.app.inspire

import android.graphics.Rect
import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.view.View
import android.view.WindowManager
import com.slideindex.app.inspire.ui.InspireView
import com.slideindex.app.inspire.ui.InspireViewCallback
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.service.SlideIndexAccessibilityService
import com.slideindex.app.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GestureEVO InspireFloating — fullscreen accessibility overlay for Inspire pick.
 */
@SuppressLint("StaticFieldLeak")
class InspireFloating private constructor(
    private val service: SlideIndexAccessibilityService,
) : BaseFloatingWindow(service, scope, TAG) {

    private val layoutParams = WindowManager.LayoutParams()
    private val inspireView: InspireView by lazy {
        val parent = FrameLayout(service)
        LayoutInflater.from(service).inflate(
            com.slideindex.app.R.layout.inspire_view,
            parent,
            false,
        ) as InspireView
    }
    private var taskJob: Job? = null
    private var appSettings: AppSettings = AppSettings()

    init {
        configureAccessibilityOverlay(layoutParams)
        initInspireView()
    }

    private fun initInspireView() {
        inspireView.visibility = View.GONE
        inspireView.inspireViewCallback = object : InspireViewCallback {
            override fun onDragUp(dragSelectRect: Rect) {
                if (taskJob?.isActive == true) return
                taskJob = scope.launch {
                    val targetRect = withContext(Dispatchers.Default) {
                        resolveTargetRect(dragSelectRect)
                    } ?: dragSelectRect
                    hide()
                    val result = InspireCoordinator.processScreenContent(
                        service = service,
                        context = service,
                        dragSelectRect = targetRect,
                        ocrFallbackEnabled = appSettings.floatBallOcrFallbackEnabled,
                        ocrModelId = appSettings.floatBallOcrModelId,
                    )
                    val anchorX = targetRect.centerX().toFloat()
                    val anchorY = targetRect.bottom.toFloat()
                    FloatBallPickResultPanel.showResult(service, anchorX, anchorY, result)
                }
            }

            override fun onDragCancel() {
                hide()
            }
        }
    }

    private fun resolveTargetRect(rect: Rect): Rect? {
        if (rect.width() <= 0 || rect.height() <= 0) return null
        return Rect(rect)
    }

    private suspend fun prepareVisibleInspireView() {
        withContext(Dispatchers.Main.immediate) {
            ensureAttached()
            inspireView.refreshDynamicColors()
            inspireView.visibility = View.VISIBLE
            inspireView.stopTransitionAnimation()
            inspireView.setInteractionEnabled(true)
        }
    }

    fun show(forceImageTextSelection: Boolean = false) {
        scope.launch {
            InspireDataHolder.setForceImageTextSelection(forceImageTextSelection)
            prepareVisibleInspireView()
            inspireView.checkIsNeedDoubleCheck()
        }
    }

    fun show(rect: Rect, forceImageTextSelection: Boolean = false) {
        scope.launch {
            InspireDataHolder.setForceImageTextSelection(forceImageTextSelection)
            prepareVisibleInspireView()
            inspireView.getDragSelectRect().set(rect)
            inspireView.inspireViewCallback.onDragUp(rect)
        }
    }

    fun hide() {
        scope.launch(Dispatchers.Main.immediate) {
            inspireView.visibility = View.GONE
            inspireView.setInteractionEnabled(true)
            detachIfNeeded()
        }
    }

    fun destroy() {
        hide()
    }

    private fun ensureAttached() {
        if (inspireView.isAttachedToWindow) return
        addViewSafely(inspireView, layoutParams)
    }

    private fun detachIfNeeded() {
        detachViewSafely(inspireView)
    }

    fun updateSettings(settings: AppSettings) {
        appSettings = settings
    }

    companion object {
        private const val TAG = "InspireFloating"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        @Volatile
        private var instance: InspireFloating? = null

        fun get(service: SlideIndexAccessibilityService): InspireFloating {
            return instance ?: synchronized(this) {
                instance ?: InspireFloating(service).also { instance = it }
            }
        }

        fun hide() {
            instance?.hide()
        }

        fun destroy() {
            instance?.destroy()
            instance = null
        }
    }
}
