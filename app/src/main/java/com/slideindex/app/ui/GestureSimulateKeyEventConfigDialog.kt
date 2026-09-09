package com.slideindex.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.slideindex.app.R
import com.slideindex.app.gesture.GestureAction
import com.slideindex.app.gesture.KeyEventPresets
import com.slideindex.app.ui.miuix.CardItem
import com.slideindex.app.ui.miuix.MiuixHintText
import com.slideindex.app.ui.miuix.MiuixLabeledTextField
import com.slideindex.app.ui.miuix.MiuixSearchField
import top.yukonga.miuix.kmp.basic.SmallTitle
import com.slideindex.app.ui.miuix.groupedCardItems
import com.slideindex.app.ui.settings.components.LazySettingsItem
import com.slideindex.app.ui.settings.components.SettingsScreenScaffold
import com.slideindex.app.ui.settings.components.settingsLazySmallTitle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.TextButton as MiuixTextButton
import top.yukonga.miuix.kmp.preference.RadioButtonLocation
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

@Composable
fun GestureSimulateKeyEventScreen(
    initialAction: GestureAction.SimulateKeyEvent?,
    onBack: () -> Unit,
    onConfirm: (GestureAction.SimulateKeyEvent) -> Unit,
    embedInParentChrome: Boolean = false,
    overlayMode: Boolean = false,
    enableBackHandler: Boolean = true
) {
    var selectedCode by remember(initialAction) {
        mutableIntStateOf(initialAction?.keyCode ?: 82)
    }
    var customName by remember(initialAction) {
        mutableStateOf(initialAction?.keyName.orEmpty())
    }
    var isLongPress by remember(initialAction) {
        mutableStateOf(initialAction?.isLongPress ?: false)
    }
    var customCodeInput by remember(initialAction) {
        mutableStateOf(initialAction?.keyCode?.toString() ?: "82")
    }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    val handleSave = {
        val finalCode = customCodeInput.toIntOrNull() ?: selectedCode
        val resolvedName = if (customName.isNotBlank()) {
            customName.trim()
        } else {
            KeyEventPresets.getDisplayName(context, finalCode)
        }
        onConfirm(
            GestureAction.SimulateKeyEvent(
                keyCode = finalCode,
                keyName = resolvedName,
                isLongPress = isLongPress
            )
        )
    }

    val filteredPresets = remember(searchQuery, context) {
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            KeyEventPresets.presets
        } else {
            KeyEventPresets.presets.filter { item ->
                item.label(context).lowercase().contains(query) ||
                    item.constantName.lowercase().contains(query) ||
                    item.keyCode.toString().contains(query) ||
                    item.description(context).lowercase().contains(query)
            }
        }
    }

    val groupedPresets = remember(filteredPresets) {
        filteredPresets.groupBy { it.category }
    }

    val customConfigCardItems = remember(customCodeInput, customName, isLongPress) {
        listOf(
            CardItem(key = "key_code_input") {
                MiuixLabeledTextField(
                    value = customCodeInput,
                    onValueChange = { input ->
                        val digitsOnly = input.filter { it.isDigit() }
                        customCodeInput = digitsOnly
                        digitsOnly.toIntOrNull()?.let { code ->
                            selectedCode = code
                        }
                    },
                    label = stringResource(R.string.key_event_code_label),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            },
            CardItem(key = "key_name_input") {
                MiuixLabeledTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = stringResource(R.string.key_event_name_label),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            },
            CardItem(key = "long_press_switch") {
                SwitchPreference(
                    title = stringResource(R.string.key_event_long_press_title),
                    summary = stringResource(R.string.key_event_long_press_summary),
                    checked = isLongPress,
                    onCheckedChange = { isLongPress = it }
                )
            }
        )
    }

    val presetCardItemsByCategory = remember(groupedPresets, selectedCode, context) {
        groupedPresets.mapValues { (_, items) ->
            items.map { item ->
                CardItem(key = "preset_${item.keyCode}") {
                    val isSelected = selectedCode == item.keyCode
                    val itemLabel = item.label(context)
                    RadioButtonPreference(
                        title = itemLabel,
                        summary = "${item.constantName} (${item.keyCode})",
                        selected = isSelected,
                        onClick = {
                            selectedCode = item.keyCode
                            customCodeInput = item.keyCode.toString()
                            customName = itemLabel
                        },
                        radioButtonLocation = RadioButtonLocation.End
                    )
                }
            }
        }
    }

    val customConfigSectionTitle = stringResource(R.string.key_event_custom_config_title)

    if (embedInParentChrome) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MiuixSearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                hintResId = R.string.search_hint,
                modifier = Modifier.fillMaxWidth()
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "custom_key_config") {
                    SmallTitle(
                        text = stringResource(R.string.key_event_custom_config_title),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MiuixCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            MiuixLabeledTextField(
                                value = customCodeInput,
                                onValueChange = { input ->
                                    val digitsOnly = input.filter { it.isDigit() }
                                    customCodeInput = digitsOnly
                                    digitsOnly.toIntOrNull()?.let { code ->
                                        selectedCode = code
                                    }
                                },
                                label = stringResource(R.string.key_event_code_label),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            MiuixLabeledTextField(
                                value = customName,
                                onValueChange = { customName = it },
                                label = stringResource(R.string.key_event_name_label),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            SwitchPreference(
                                title = stringResource(R.string.key_event_long_press_title),
                                summary = stringResource(R.string.key_event_long_press_summary),
                                checked = isLongPress,
                                onCheckedChange = { isLongPress = it }
                            )
                        }
                    }
                    MiuixHintText(
                        text = stringResource(R.string.key_event_permission_hint),
                    )
                }

                groupedPresets.forEach { (category, items) ->
                    item(key = "cat_${category.name}") {
                        SmallTitle(
                            text = category.title(context),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    items.forEach { item ->
                        item(key = "preset_${item.keyCode}") {
                            val isSelected = selectedCode == item.keyCode
                            RadioButtonPreference(
                                title = item.label(context),
                                summary = "${item.constantName} (${item.keyCode}) - ${item.description(context)}",
                                selected = isSelected,
                                onClick = {
                                    selectedCode = item.keyCode
                                    customCodeInput = item.keyCode.toString()
                                    customName = item.label(context)
                                },
                                radioButtonLocation = RadioButtonLocation.End
                            )
                        }
                    }
                }
            }

            Button(
                onClick = handleSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
            ) {
                Text(
                    text = stringResource(R.string.shell_panel_save),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    } else {
        SettingsScreenScaffold(
            title = stringResource(R.string.key_event_config_title),
            pageHint = stringResource(R.string.gesture_action_simulate_key_event_desc),
            onBack = onBack,
            enableBackHandler = enableBackHandler,
            overlayMode = overlayMode,
            actions = {
                top.yukonga.miuix.kmp.basic.IconButton(onClick = handleSave) {
                    top.yukonga.miuix.kmp.basic.Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.shell_panel_save)
                    )
                }
            }
        ) {
            item(key = "search_box") {
                MiuixSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    hintResId = R.string.search_hint,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            settingsLazySmallTitle(
                key = "custom_key_title",
                title = customConfigSectionTitle
            )

            groupedCardItems(
                keyPrefix = "custom_key_config",
                items = customConfigCardItems
            )

            LazySettingsItem(key = "permission_hint_section") {
                MiuixHintText(
                    text = stringResource(R.string.key_event_permission_hint),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            groupedPresets.keys.forEach { category ->
                val items = presetCardItemsByCategory[category].orEmpty()
                if (items.isNotEmpty()) {
                    settingsLazySmallTitle(
                        key = "cat_title_${category.name}",
                        title = category.title(context)
                    )
                    groupedCardItems(
                        keyPrefix = "cat_${category.name}",
                        items = items
                    )
                }
            }
        }
    }
}
