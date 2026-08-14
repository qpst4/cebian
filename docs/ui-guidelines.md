# cebian UI 规范

改 `app/src/main/java/com/slideindex/app/ui/` 下任何 Compose 界面前先读本文件。

## 页面骨架

- **标准设置子页**：`MiuixSettingsScreenScaffold` / `SettingsLazyScreenScaffold` + `LazyColumn`（经 `MiuixListScaffold`）。
- **LazyColumn** 必须加 `.scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection)`（脚手架已内置）。
- **二级页 LazyColumn** 根节点加 `.horizontalCutoutPadding()`（横屏侧边刘海/手势条）；主 Tab Hub 不加（外壳已处理）。
- **宽屏内容居中**：`WideContentBox` 算出 `sidePadding` 并入 `contentPadding`，LazyColumn **保持全宽**（禁止 `widthIn` 缩列宽）。见 `ui/miuix/WindowSize.kt`。
- **Hub 主页**：`MiuixHubScaffold`，内容在 `LazyListScope` 内直接写。
- **禁止**在需要滚动的设置页使用 `verticalScroll` + `Column`（交互网格编辑器等特例除外）。

## 脚手架选择

| 场景 | 脚手架 | 卡片写法 |
|------|--------|----------|
| Hub / 纯设置子页 | `MiuixSettingsScreenScaffold` / `MiuixHubScaffold` | Lazy 内 `groupedCardItems` / `settingsGroupedCardItems` |
| 混合 Lazy 屏（`items(apps)` + 设置分组） | `SettingsLazyScreenScaffold` | 同上，与 `items` 混排 |
| 非 Lazy 表面（对话框、滚动 Column） | 任意 | `settingsCardItems { }` + `.RenderRows()` |
| 长列表（应用选择器等） | `SettingsLazyScreenScaffold` | `items` / `groupedCardItems` |

## 多行卡片虚拟化（强制）

`LazyColumn` 里 **禁止** `item { Card { 多行 } }`——整卡一次性组合，行多时滚动卡顿。

### Mishka 唯一 Lazy 模式（强制）

**禁止** `rememberSettingsCardItems`、`emitSettingsCardItems` 及任何 Lazy 外收集 → Lazy 内发射的两阶段模式。

开关 / 条件子项的 state **必须在 lazy item 的 compose 作用域内读取**（`settingsCardItem { }` 或 `CardItem { }` 内）。

```kotlin
MiuixHubScaffold(...) {
    settingsLazySmallTitle(key = "service", title = serviceTitle, sectionTop = true)
    groupedCardItems(
        keyPrefix = "main_service",
        items = buildList {
            add(
                settingsCardScopeItem("gesture-enabled") {
                    SettingSwitchRow(
                        checked = settings.serviceEnabled,
                        onCheckedChange = onGestureEnabledChange,
                        ...
                    )
                },
            )
            if (settings.hapticEnabled) {
                add(settingsCardScopeItem("haptic-strength") { ... })
            }
        },
    )
}
```

多行 helper 可返回 `List<CardItem>`（`@Composable`，在 scaffold 外调用），再在 Lazy 内 `addAll(...)`。**Lazy 路径禁止** `settingsCardItems` + `settingsGroupedCardItems`。

### 非 Lazy 表面

对话框、嵌入滚动 Column 等无法拆 lazy item 时：

```kotlin
val card = settingsCardItems(settings.foo) {
    SettingSwitchRow(checked = settings.foo, ...)
}
Column {
    card.RenderRows()
}
```

### 自定义整块

```kotlin
LazySettingsItem(key = "trigger-side-section") {
    TriggerEntryList(...)
}
```

`scrollContent = false` 的子页由脚手架自动用 `LazySettingsItem("settings-screen-body")` 包裹整块内容。

## 实现要点

- 分角背景：`CardSegment` + `squircleSurface`，见 `GroupedCardItems.kt`。
- 卡片内 preference 行在 segment 模式下走 `LocalSettingsCardSegmentMode`。
- `SettingsCardScopeContent { }` 包裹 scope 扩展行 helper（`SettingSwitchRow` 等）。
- `stringResource` 可在 `settingsCardItem { }` / `item { }` 内调用；LazyListScope 顶层禁止调用 `@Composable`。
- Lazy item key 保持稳定（`keyPrefix:rowKey`），**禁止** `#rowSignature` 污染 key。

## 不适用拆行的例外

- 纯静态单块内容（单段提示、预览 Surface）。
- 含复杂内部布局、无法按行拆分的编辑网格（`HoneycombLayoutEditorScreen` 等）。

## 相关文件

- `ui/miuix/WindowSize.kt` — `WideContentBox`、`horizontalCutoutPadding`
- `ui/miuix/GroupedCardItems.kt` — `CardSegment`、`groupedCardItems`
- `ui/settings/components/SettingsListItems.kt` — `settingsCardItem`、`settingsCardItems`、`settingsGroupedCardItems`、`LazySettingsItem`
