# cebian UI 规范

改 `app/src/main/java/com/slideindex/app/ui/` 下任何 Compose 界面前先读本文件。

## 页面骨架

- **标准设置子页**：`MiuixSettingsScreenScaffold` 或 `SettingsLazyScreenScaffold` + `LazyColumn`（经 `MiuixListScaffold`）。
- **LazyColumn** 必须加 `.scrollEndHaptic().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection)`（脚手架已内置）。
- **二级页 LazyColumn** 根节点加 `.horizontalCutoutPadding()`（横屏侧边刘海/手势条）；主 Tab Hub 不加（外壳已处理）。
- **宽屏内容居中**：`WideContentBox` 算出 `sidePadding` 并入 `contentPadding`，LazyColumn **保持全宽**（禁止 `widthIn` 缩列宽）。见 `ui/miuix/WindowSize.kt`。
- **Hub 主页**：`MiuixHubScaffold`，内容经 `SettingsLazyEmitter` 自动拆行。
- **禁止**在需要滚动的设置页使用 `verticalScroll` + `Column`（交互网格编辑器等特例除外）。

## 脚手架选择

| 场景 | 脚手架 | 卡片写法 |
|------|--------|----------|
| Hub / 纯设置子页（无混合列表） | `MiuixSettingsScreenScaffold` / `MiuixHubScaffold` | 直接写 `SettingsCard { … }`，自动拆行 |
| 混合 Lazy 屏（`items(apps)` + 设置分组） | `SettingsLazyScreenScaffold` | `rememberSettingsCardGroup` + `emitSettingsCardGroup` |
| 长列表（应用选择器等） | `SettingsLazyScreenScaffold` | 每行 `items` / `groupedCardItems` |

## 多行卡片虚拟化（强制）

`LazyColumn` 里 **禁止** `item { SettingsCard { 多行 } }`——整卡一次性组合，行多时滚动卡顿。

### Emitter 脚手架内的自定义块（强制）

`MiuixHubScaffold` / `MiuixSettingsScreenScaffold` 通过 `SettingsLazyEmitter` 收集内容，**只有**以下 composable 会进入 `LazyColumn`：

- `SettingsCard { … }`（自动拆行）
- `MiuixSmallTitle`（内部已 `LazySettingsItem`）
- `LazySettingsItem(key) { … }` / `SettingsLazyBlock(key) { … }`

**禁止**在 emitter 脚手架内直接写裸 `Column`、`Card`、`Surface`、`Button`、`SettingSwitchRow`、自定义列表等——它们会在收集阶段执行但**不会显示**。

```kotlin
// ✅ 自定义整块（预览、表单、非 SettingsCard 列表）
LazySettingsItem(key = "trigger-side-section") {
    TriggerEntryList(...)
}

// ✅ 或别名
SettingsLazyBlock(key = "about-header") {
    AboutAppHeader()
}

// ❌ 不会出现在 LazyColumn 中
Column {
    TriggerEntryList(...)
}
```

`scrollContent = false` 的子页由脚手架自动用 `LazySettingsItem("settings-screen-body")` 包裹整块内容；可滚动子页须自行对非 `SettingsCard` 块调用 `LazySettingsItem`。

顶层 `SettingsHintText` 已内置 `LazySettingsItem`；卡片内请用 `SettingsCardScope.SettingsHintText`。

### 方式 A：Emitter 脚手架（推荐，纯设置页）

在 `MiuixSettingsScreenScaffold` / `MiuixHubScaffold` 内直接写：

```kotlin
SettingsCard(keyPrefix = "general") {
    SettingSwitchRow(...)
    SettingNavigationRow(...)
}
```

`SettingsCard` 在 `LocalSettingsLazyEmitter` 存在时自动登记为 `groupedCardItems`。

### 方式 B：混合 Lazy 屏

在 `@Composable` 阶段收集行，在 `LazyListScope` 阶段发射：

```kotlin
val card = rememberSettingsCardGroup("my-card") {
    SettingSwitchRow(...)
}
SettingsLazyScreenScaffold {
    settingsLazySmallTitle(key = "section", title = sectionTitle)
    emitSettingsCardGroup(card)
    items(apps, key = { it.packageName }) { app -> AppRow(app) }
}
```

### 方式 C：动态列表行

对非 `SettingsCardRow` 的自定义行，直接用 `groupedCardItems`：

```kotlin
groupedCardItems(
    keyPrefix = "engines",
    items = engines.map { CardItem(it.id) { EngineRow(it) } },
)
```

## 实现要点

- 分角背景：`CardSegment` + `squircleSurface`（首末段）/ `background`（中间段），见 `GroupedCardItems.kt`。
- 卡片内 preference 行在 segment 模式下走 `LocalSettingsCardSegmentMode`，不再叠加 `miuixGroupedCardItem`。
- `SettingsCardScope.SettingsHintText` 已走 `SettingsCardRow`，可安全拆行。
- 自定义卡片行须调用 `SettingsCardRow(key) { … }` 才能被拆分。
- `stringResource` 等 `@Composable` 调用必须在 `LazyListScope` **之外**（或 `item { }` lambda 内），不能写在 `emitSettingsCardGroup` 的参数列表里。

## 不适用拆行的例外

- 纯静态单块内容（单段提示、预览 Surface）。
- 含复杂内部布局、无法按行拆分的编辑网格（`HoneycombLayoutEditorScreen` 等）。

## 相关文件

- `ui/miuix/WindowSize.kt` — `WideContentBox`、`horizontalCutoutPadding`
- `ui/miuix/GroupedCardItems.kt` — `CardSegment`、`groupedCardItems`
- `ui/settings/components/SettingsLazyEmitter.kt` — Hub/子页 emitter、`LazySettingsItem`、`SettingsLazyBlock`
- `ui/settings/components/SettingsCardLazyGroup.kt` — `rememberSettingsCardGroup`、`emitSettingsCardGroup`
- `ui/M3eSettingsUi.kt` — `SettingsCard`
