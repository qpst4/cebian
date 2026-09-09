#!/usr/bin/env python3
"""Split FloatBallPickResultPanel.kt UI from window orchestrator."""
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
SRC_PATH = REPO / "app/src/main/java/com/slideindex/app/overlay/FloatBallPickResultPanel.kt"
PICK = REPO / "app/src/main/java/com/slideindex/app/overlay/pickresult"
lines = SRC_PATH.read_text(encoding="utf-8").splitlines()

PKG_OVERLAY = "package com.slideindex.app.overlay\n"
PKG_PICK = "package com.slideindex.app.overlay.pickresult\n"


def slice(start: int, end: int) -> str:
    return "\n".join(lines[start - 1 : end]) + "\n"


def privatize_to_internal(text: str) -> str:
    return (
        text.replace("private class AuxiliaryCollapseController", "internal class AuxiliaryCollapseController")
        .replace("private data class PickResultCollapseHeights", "internal data class PickResultCollapseHeights")
        .replace("private fun computePickResultCollapseHeights", "internal fun computePickResultCollapseHeights")
        .replace("private fun computePickResultExpandedPanelOuterHeight", "internal fun computePickResultExpandedPanelOuterHeight")
        .replace("private fun pickPanelSlideAnimationSpec", "internal fun pickPanelSlideAnimationSpec")
        .replace("private val PANEL_", "internal val PANEL_")
        .replace("private const val LANDSCAPE_", "internal const val LANDSCAPE_")
        .replace("private const val AUXILIARY_", "internal const val AUXILIARY_")
        .replace("private const val EDIT_MODE_", "internal const val EDIT_MODE_")
        .replace("@Composable\nprivate fun ", "@Composable\ninternal fun ")
        .replace("private fun translateErrorMessage", "internal fun translateErrorMessage")
    )


LAYOUT_IMPORTS = """
import android.graphics.Bitmap
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.FloatBallOverlay
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.overlayBottomPanelMaxHeightFraction
import com.slideindex.app.overlay.overlayBottomPanelMaxWidth
import com.slideindex.app.overlay.overlayBottomPanelWidth
import com.slideindex.app.overlay.overlayContainerHeightDp
import com.slideindex.app.overlay.overlayContainerWidthDp
import com.slideindex.app.overlay.overlayIsLandscape
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineType
import com.slideindex.app.settings.launchPolicyLongPressEligible
import com.slideindex.app.ui.theme.LocalAppDarkTheme
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
import kotlin.math.abs
import kotlin.math.roundToInt
""".strip() + "\n"

(PICK / "PickResultPanelLayout.kt").write_text(
    PKG_PICK + "\n" + LAYOUT_IMPORTS + "\n" + privatize_to_internal(slice(156, 1228)),
    encoding="utf-8",
)
print("wrote PickResultPanelLayout.kt")

CONTENT_IMPORTS = """
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.barcode.BarcodeScanResult
import com.slideindex.app.overlay.FloatBallPickResultPanel
import com.slideindex.app.overlay.OverlayImeInsets.rememberOverlayImeBottomHeight
import com.slideindex.app.overlay.PickResultTextSource
import com.slideindex.app.overlay.ScreenshotLayoutMeta
import com.slideindex.app.overlay.overlayBottomPanelWidth
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.ui.theme.OverlayAwareModuleTheme
""".strip() + "\n"

(PICK / "FloatBallPickResultContent.kt").write_text(
    PKG_PICK + "\n" + CONTENT_IMPORTS + "\n" + privatize_to_internal(slice(2376, 2866)),
    encoding="utf-8",
)
print("wrote FloatBallPickResultContent.kt")

IMAGE_IMPORTS = """
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.settings.SearchEngineConfig
""".strip() + "\n"

(PICK / "PickResultImageSection.kt").write_text(
    PKG_PICK + "\n" + IMAGE_IMPORTS + "\n" + privatize_to_internal(slice(2868, 3051)),
    encoding="utf-8",
)
print("wrote PickResultImageSection.kt")

(PICK / "PickResultPanelMessages.kt").write_text(
    PKG_PICK
    + "\nimport android.content.Context\nimport com.slideindex.app.R\n\n"
    + privatize_to_internal(slice(3053, 3062)),
    encoding="utf-8",
)
print("wrote PickResultPanelMessages.kt")

# Orchestrator: package + imports (trimmed) + object block only
object_block = slice(1233, 2374)
orchestrator_imports = """
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.overlay.compositor.OverlaySceneController
import com.slideindex.app.overlay.pickresult.FloatBallPickResultContent
import com.slideindex.app.overlay.pickresult.PickResultTextMode
import com.slideindex.app.overlay.pickresult.preloadPickResultSearchEngineIcons
import com.slideindex.app.overlay.pickresult.translateErrorMessage
import com.slideindex.app.perf.PickPerf
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.settings.SearchEngineStore
import com.slideindex.app.service.RegionalScreenshotOcr
import com.slideindex.app.service.ShareImageOcrCoordinator
import com.slideindex.app.stash.StashCoordinator
import com.slideindex.app.ocr.OcrDependencyAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
""".strip() + "\n"

SRC_PATH.write_text(
    PKG_OVERLAY + "\n" + orchestrator_imports + "\n" + object_block,
    encoding="utf-8",
)
print("updated FloatBallPickResultPanel.kt")
