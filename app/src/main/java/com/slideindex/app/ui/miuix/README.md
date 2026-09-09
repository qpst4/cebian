# Miuix 设置页 UI 规范

侧缘设置页在**灰底**上放**白 Card**（`surfaceContainer`）。新页先判断属于下面哪一种，再选组件。

## 类型 1：列表（最常见）

**长什么样：** 白 Card 内一行行开关 / 右箭头 › / 滑条，**没有**顶 Tab。

**怎么写：**

```kotlin
MiuixListSettingsCard(
    keyPrefix = "my-section",
    items = listOf(
        CardItem("switch-row") { MiuixSwitchRow(...) },
        CardItem("slider-row") { MiuixSliderRow(...) },
    ),
)
```

- 等价于 `groupedCardItems`，推荐用 `MiuixListSettingsCard` 作为统一入口。
- 段内自定义 `Row` 用 `Modifier.miuixGroupedRowInsets()`，不要手写 `padding(16, 12)`。
- **禁止** `item { Card { … } }` 手拼列表。

**参考页：** `ShakeGesturesScreen`（晃动手势）

## 类型 2：Tab 在白 Card 内

**长什么样：** 白 Card **最上一行灰 Tab 条**（选中 Tab 为白 pill），下面是内容。

**怎么写：**

```kotlin
MiuixTabSettingsCard(
    tabs = listOf("Tab1", "Tab2"), // 单面板无 Tab 时传 null
    selectedTabIndex = index,
    onTabSelected = { index = it },
) {
    // Tab 内若是 Preference 行，传 insideMargin = MiuixInsetCardComponentMargin
}
```

- 外层已在 `padding(horizontal = 12.dp)` 的容器内时，传 `outerHorizontalPadding = 0.dp`。

- Card 固定 `insideMargin = 16.dp`；Tab 下内容区 `padding(top = 12.dp)`。
- Tab 内的 Switch/Arrow 行用 `MiuixInsetCardComponentMargin`，避免水平 double padding。

**参考页：**

- `SideGestureSettingsScreen`（侧滑 → 滑动手势）
- `SearchEngineEditorScreen`（搜索引擎编辑）
- `QuickLauncherPanelManagementSection`（快速启动器 → 启动面板）

## 类型 3：自定义块（不统一成列表）

表单（API Key）、下载进度、编辑器等。**外圈左右 12dp**，内容自由。

- 进度块：`MiuixProgressCard { … }`
- 不必改成类型 1。

## 新页 Checklist

1. 是列表、Tab 卡，还是自定义？
2. 列表 → `MiuixListSettingsCard`
3. Tab 卡 → `MiuixTabSettingsCard` + 必要时 `MiuixInsetCardComponentMargin`
4. 自定义 → 外 12dp；段内 Row 用 `miuixGroupedRowInsets()` 或 Preference 默认 inset

## 相关文件

| 文件 | 作用 |
|------|------|
| `MiuixSettingsCards.kt` | `MiuixListSettingsCard` / `MiuixTabSettingsCard` / `MiuixProgressCard` |
| `GroupedCardItems.kt` | `CardSegment` / `groupedCardItems` 底层 |
| `MiuixCardInsets.kt` | `MiuixInsetCardComponentMargin` / `miuixGroupedRowInsets()` |
| `MiuixTabRow.kt` | `MiuixTabRowWithContourInCard` |
