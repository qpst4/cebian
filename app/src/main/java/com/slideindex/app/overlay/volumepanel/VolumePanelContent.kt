package com.slideindex.app.overlay.volumepanel

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.activity.ActivityShortcut
import com.slideindex.app.data.AppInfo
import com.slideindex.app.di.OverlayDependencyAccess
import com.slideindex.app.gesture.ActionExecutor
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.settings.AppSettings
import com.slideindex.app.ui.Md3PickerIconLeading
import com.slideindex.app.ui.Md3PickerLaunchShortcutLeading
import com.slideindex.app.ui.Md3PickerPackageLeading
import com.slideindex.app.ui.gestureActionIcon
import com.slideindex.app.ui.gesturepicker.gestureActionLabelText
import com.slideindex.app.util.BrightnessControlHelper
import com.slideindex.app.util.VolumeControlHelper
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun VolumePanelContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val deps = remember { OverlayDependencyAccess.overlayDependencies(context) }
    val scope = rememberCoroutineScope()
    var slotActions by remember { mutableStateOf(List<GestureAction?>(8) { null }) }
    var activityShortcuts by remember { mutableStateOf<List<ActivityShortcut>>(emptyList()) }
    var appSettings by remember { mutableStateOf(AppSettings()) }
    var isPickerMode by remember { mutableStateOf(false) }
    var pickerSlot by remember { mutableIntStateOf(-1) }
    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    val overlayContext = remember(context) {
        OverlayDependencyAccess.overlayHostContext() ?: context
    }
    val actionExecutor = remember(deps, overlayContext) {
        val repository = deps?.appRepository ?: return@remember null
        ActionExecutor(
            context = overlayContext,
            appRepository = repository,
            onShellCommandsPersist = { commands ->
                scope.launch {
                    deps.settingsRepository.setShellCommands(commands)
                }
            },
        )
    }

    LaunchedEffect(deps) {
        val repository = deps?.settingsRepository ?: return@LaunchedEffect
        repository.settings.collect { settings ->
            appSettings = settings
            slotActions = settings.expandPanelSlotActions
            activityShortcuts = settings.activityShortcuts
        }
    }
    LaunchedEffect(deps) {
        allApps = deps?.appRepository?.loadApps().orEmpty()
    }

    fun runSlotAction(action: GestureAction) {
        onDismiss()
        val metrics = overlayContext.resources.displayMetrics
        actionExecutor?.execute(
            action = action,
            settings = appSettings,
            anchorRawX = metrics.widthPixels / 2f,
            anchorRawY = metrics.heightPixels / 2f,
        )
    }

    fun saveSlotAction(index: Int, action: GestureAction?) {
        scope.launch {
            deps?.settingsRepository?.setExpandPanelSlotAction(index, action)
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surfaceContainer,
        ),
    ) {
        AnimatedContent(
            targetState = isPickerMode,
            transitionSpec = {
                if (targetState) {
                    (slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    ) + fadeIn()).togetherWith(
                        slideOutHorizontally(targetOffsetX = { -it / 4 }) + fadeOut(),
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    ) + fadeIn()).togetherWith(
                        slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                    )
                }
            },
            label = "expandPanelPicker",
        ) { pickerMode ->
            if (pickerMode) {
                ExpandPanelSlotPicker(
                    allApps = allApps,
                    activityShortcuts = activityShortcuts,
                    current = slotActions.getOrNull(pickerSlot),
                    onSelect = { action ->
                        if (pickerSlot in slotActions.indices) {
                            saveSlotAction(pickerSlot, action)
                        }
                        isPickerMode = false
                        pickerSlot = -1
                    },
                    onCancel = {
                        isPickerMode = false
                        pickerSlot = -1
                    },
                )
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.gesture_action_volume_panel),
                        style = MiuixTheme.textStyles.title4,
                    )
                    ExpandPanelSlidersSection()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.expand_panel_shortcuts_title),
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ExpandPanelShortcutsGrid(
                        slotActions = slotActions,
                        activityShortcuts = activityShortcuts,
                        onRun = ::runSlotAction,
                        onAssign = { index ->
                            pickerSlot = index
                            isPickerMode = true
                        },
                        onClear = { index -> saveSlotAction(index, null) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandPanelSlidersSection() {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    var brightness by remember {
        mutableFloatStateOf(BrightnessControlHelper.readBrightnessFraction(context))
    }
    val maxAlarm = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM).coerceAtLeast(1) }
    var alarmVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_ALARM).toFloat() / maxAlarm)
    }
    val maxNotification = remember {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_NOTIFICATION).coerceAtLeast(1)
    }
    var notificationVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION).toFloat() / maxNotification)
    }
    val maxRing = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_RING).coerceAtLeast(1) }
    var ringVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_RING).toFloat() / maxRing)
    }
    val maxMusic = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var mediaVolume by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxMusic)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .height(188.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_brightness),
            icon = Icons.Default.LightMode,
            value = brightness,
            onValueChange = {
                brightness = it
                BrightnessControlHelper.applyBrightnessFraction(context, it)
            },
        )
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_alarm),
            icon = Icons.Default.Alarm,
            value = alarmVolume,
            onValueChange = {
                alarmVolume = it
                VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.ALARM, it)
            },
        )
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_notification),
            icon = Icons.Default.NotificationsActive,
            value = notificationVolume,
            onValueChange = {
                notificationVolume = it
                VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.NOTIFICATION, it)
            },
        )
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_ring),
            icon = Icons.Default.Notifications,
            value = ringVolume,
            onValueChange = {
                ringVolume = it
                VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.RING, it)
            },
        )
        ExpandPanelSliderItem(
            label = stringResource(R.string.volume_panel_media),
            icon = Icons.Default.MusicNote,
            value = mediaVolume,
            onValueChange = {
                mediaVolume = it
                VolumeControlHelper.setFraction(context, VolumeControlHelper.Stream.MEDIA, it)
            },
        )
    }
}

@Composable
private fun ExpandPanelSliderItem(
    label: String,
    icon: ImageVector,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    var currentValue by remember { mutableFloatStateOf(value) }
    val cornerRadius = 14.dp
    val trackColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.34f)
    val fillColor = MiuixTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(148.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val delta = -dragAmount / size.height
                            currentValue = (currentValue + delta).coerceIn(0f, 1f)
                            onValueChange(currentValue)
                        },
                    )
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(cornerRadius)),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radiusPx = cornerRadius.toPx()
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset.Zero,
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(radiusPx, radiusPx),
                    )
                    val fillHeight = size.height * currentValue
                    if (fillHeight > 0f) {
                        drawRect(
                            color = fillColor,
                            topLeft = Offset(0f, size.height - fillHeight),
                            size = Size(size.width, fillHeight),
                        )
                    }
                }
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .size(22.dp),
                tint = if (currentValue > 0.18f) Color.White else fillColor.copy(alpha = 0.75f),
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun ExpandPanelShortcutsGrid(
    slotActions: List<GestureAction?>,
    activityShortcuts: List<ActivityShortcut>,
    onRun: (GestureAction) -> Unit,
    onAssign: (Int) -> Unit,
    onClear: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                repeat(4) { col ->
                    val index = row * 4 + col
                    val action = slotActions.getOrNull(index)
                    ExpandPanelShortcutCell(
                        action = action,
                        activityShortcuts = activityShortcuts,
                        onClick = {
                            if (action != null) onRun(action) else onAssign(index)
                        },
                        onLongClick = {
                            if (action != null) onClear(index) else onAssign(index)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandPanelShortcutCell(
    action: GestureAction?,
    activityShortcuts: List<ActivityShortcut>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(62.dp)
            .clip(shape)
            .pointerInput(action) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (action != null) {
            ExpandPanelSlotIcon(
                action = action,
                activityShortcuts = activityShortcuts,
            )
        } else {
            Box(
                modifier = Modifier.size(ExpandPanelSlotIconSize),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.expand_panel_shortcut_add),
                    modifier = Modifier.size(ExpandPanelSlotIconInner),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        }
    }
}

private val ExpandPanelSlotIconSize = 40.dp
private val ExpandPanelSlotIconInner = 24.dp

@Composable
private fun ExpandPanelSlotIcon(
    action: GestureAction,
    activityShortcuts: List<ActivityShortcut>,
) {
    val context = LocalContext.current
    when (action) {
        is GestureAction.LaunchApp -> {
            Md3PickerPackageLeading(
                packageName = action.packageName,
                contentDescription = action.packageName,
                selected = false,
            )
        }
        is GestureAction.LaunchShortcut -> {
            Md3PickerLaunchShortcutLeading(
                action = action,
                activityShortcuts = activityShortcuts,
                selected = false,
            )
        }
        else -> {
            Md3PickerIconLeading(
                icon = gestureActionIcon(action, outlined = true),
                selected = false,
                contentDescription = gestureActionLabelText(context, action),
            )
        }
    }
}
