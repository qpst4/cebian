package com.slideindex.app.message

import com.slideindex.app.R

internal object WeipopSideThemes {
    fun themes(): List<MessageThemeSpec> = baseShapeThemes + colorSkinThemes

    private val baseShapeThemes = listOf(
        weipopSideTheme(
            id = "side_weipop_round_black",
            labelRes = R.string.message_theme_weipop_round_black,
            leftRes = R.drawable.bg_weipop_item1_black,
            rightRes = R.drawable.bg_weipop_item1_black,
            titleColor = "#FFFFFF",
            paddingH = 12f,
            paddingV = 5f,
            cornerRadius = 10f,
        ),
        weipopSideTheme(
            id = "side_weipop_round_white",
            labelRes = R.string.message_theme_weipop_round_white,
            leftRes = R.drawable.bg_weipop_item1_white,
            rightRes = R.drawable.bg_weipop_item1_white,
            titleColor = "#474747",
            paddingH = 12f,
            paddingV = 5f,
            cornerRadius = 10f,
        ),
        weipopSideTheme(
            id = "side_weipop_tail3_black",
            labelRes = R.string.message_theme_weipop_tail3_black,
            leftRes = R.drawable.bg_weipop_item3_black_left,
            rightRes = R.drawable.bg_weipop_item3_black_right,
            titleColor = "#FFFFFF",
            paddingH = 14f,
            paddingV = 10f,
            cornerRadius = 0f,
        ),
        weipopSideTheme(
            id = "side_weipop_tail3_white",
            labelRes = R.string.message_theme_weipop_tail3_white,
            leftRes = R.drawable.bg_weipop_item3_white_left,
            rightRes = R.drawable.bg_weipop_item3_white_right,
            titleColor = "#474747",
            paddingH = 14f,
            paddingV = 10f,
            cornerRadius = 0f,
        ),
        weipopSideTheme(
            id = "side_weipop_tail4_black",
            labelRes = R.string.message_theme_weipop_tail4_black,
            leftRes = R.drawable.bg_weipop_item4_black_left,
            rightRes = R.drawable.bg_weipop_item4_black_right,
            titleColor = "#FFFFFF",
            paddingH = 12f,
            paddingV = 10f,
            cornerRadius = 0f,
        ),
        weipopSideTheme(
            id = "side_weipop_tail4_white",
            labelRes = R.string.message_theme_weipop_tail4_white,
            leftRes = R.drawable.bg_weipop_item4_white_left,
            rightRes = R.drawable.bg_weipop_item4_white_right,
            titleColor = "#474747",
            paddingH = 12f,
            paddingV = 10f,
            cornerRadius = 0f,
        ),
        weipopSideTheme(
            id = "side_weipop_compact_black",
            labelRes = R.string.message_theme_weipop_compact_black,
            leftRes = R.drawable.bg_weipop_item5_black,
            rightRes = R.drawable.bg_weipop_item5_black,
            titleColor = "#FFFFFF",
            paddingH = 12f,
            paddingV = 8f,
            cornerRadius = 0f,
        ),
        weipopSideTheme(
            id = "side_weipop_compact_white",
            labelRes = R.string.message_theme_weipop_compact_white,
            leftRes = R.drawable.bg_weipop_item5_white,
            rightRes = R.drawable.bg_weipop_item5_white,
            titleColor = "#474747",
            paddingH = 12f,
            paddingV = 8f,
            cornerRadius = 0f,
        ),
    )

    private val colorSkinThemes = listOf(
        weipopSkinTheme(1),
        weipopSkinTheme(2),
        weipopSkinTheme(3),
        weipopSkinTheme(4),
        weipopSkinTheme(5),
        weipopSkinTheme(6),
        weipopSkinTheme(7),
        weipopSkinTheme(8),
        weipopSkinTheme(9),
        weipopSkinTheme(10),
        weipopSkinTheme(11),
        weipopSkinTheme(12),
        weipopSkinTheme(13),
        weipopSkinTheme(14),
        weipopSkinTheme(15),
        weipopSkinTheme(16),
        weipopSkinTheme(17),
        weipopSkinTheme(18),
        weipopSkinTheme(19),
        weipopSkinTheme(20),
        weipopSkinTheme(21),
        weipopSkinTheme(22),
        weipopSkinTheme(23),
    )

    private fun weipopSkinTheme(index: Int): MessageThemeSpec {
        val idSuffix = index.toString().padStart(2, '0')
        val labelRes = when (index) {
            1 -> R.string.message_theme_weipop_skin_01
            2 -> R.string.message_theme_weipop_skin_02
            3 -> R.string.message_theme_weipop_skin_03
            4 -> R.string.message_theme_weipop_skin_04
            5 -> R.string.message_theme_weipop_skin_05
            6 -> R.string.message_theme_weipop_skin_06
            7 -> R.string.message_theme_weipop_skin_07
            8 -> R.string.message_theme_weipop_skin_08
            9 -> R.string.message_theme_weipop_skin_09
            10 -> R.string.message_theme_weipop_skin_10
            11 -> R.string.message_theme_weipop_skin_11
            12 -> R.string.message_theme_weipop_skin_12
            13 -> R.string.message_theme_weipop_skin_13
            14 -> R.string.message_theme_weipop_skin_14
            15 -> R.string.message_theme_weipop_skin_15
            16 -> R.string.message_theme_weipop_skin_16
            17 -> R.string.message_theme_weipop_skin_17
            18 -> R.string.message_theme_weipop_skin_18
            19 -> R.string.message_theme_weipop_skin_19
            20 -> R.string.message_theme_weipop_skin_20
            21 -> R.string.message_theme_weipop_skin_21
            22 -> R.string.message_theme_weipop_skin_22
            else -> R.string.message_theme_weipop_skin_23
        }
        val leftRes = when (index) {
            1 -> R.drawable.bg_weipop_skin_01_left
            2 -> R.drawable.bg_weipop_skin_02_left
            3 -> R.drawable.bg_weipop_skin_03_left
            4 -> R.drawable.bg_weipop_skin_04_left
            5 -> R.drawable.bg_weipop_skin_05_left
            6 -> R.drawable.bg_weipop_skin_06_left
            7 -> R.drawable.bg_weipop_skin_07_left
            8 -> R.drawable.bg_weipop_skin_08_left
            9 -> R.drawable.bg_weipop_skin_09_left
            10 -> R.drawable.bg_weipop_skin_10_left
            11 -> R.drawable.bg_weipop_skin_11_left
            12 -> R.drawable.bg_weipop_skin_12_left
            13 -> R.drawable.bg_weipop_skin_13_left
            14 -> R.drawable.bg_weipop_skin_14_left
            15 -> R.drawable.bg_weipop_skin_15_left
            16 -> R.drawable.bg_weipop_skin_16_left
            17 -> R.drawable.bg_weipop_skin_17_left
            18 -> R.drawable.bg_weipop_skin_18_left
            19 -> R.drawable.bg_weipop_skin_19_left
            20 -> R.drawable.bg_weipop_skin_20_left
            21 -> R.drawable.bg_weipop_skin_21_left
            22 -> R.drawable.bg_weipop_skin_22_left
            else -> R.drawable.bg_weipop_skin_23_left
        }
        val rightRes = when (index) {
            1 -> R.drawable.bg_weipop_skin_01_right
            2 -> R.drawable.bg_weipop_skin_02_right
            3 -> R.drawable.bg_weipop_skin_03_right
            4 -> R.drawable.bg_weipop_skin_04_right
            5 -> R.drawable.bg_weipop_skin_05_right
            6 -> R.drawable.bg_weipop_skin_06_right
            7 -> R.drawable.bg_weipop_skin_07_right
            8 -> R.drawable.bg_weipop_skin_08_right
            9 -> R.drawable.bg_weipop_skin_09_right
            10 -> R.drawable.bg_weipop_skin_10_right
            11 -> R.drawable.bg_weipop_skin_11_right
            12 -> R.drawable.bg_weipop_skin_12_right
            13 -> R.drawable.bg_weipop_skin_13_right
            14 -> R.drawable.bg_weipop_skin_14_right
            15 -> R.drawable.bg_weipop_skin_15_right
            16 -> R.drawable.bg_weipop_skin_16_right
            17 -> R.drawable.bg_weipop_skin_17_right
            18 -> R.drawable.bg_weipop_skin_18_right
            19 -> R.drawable.bg_weipop_skin_19_right
            20 -> R.drawable.bg_weipop_skin_20_right
            21 -> R.drawable.bg_weipop_skin_21_right
            22 -> R.drawable.bg_weipop_skin_22_right
            else -> R.drawable.bg_weipop_skin_23_right
        }
        return weipopSideTheme(
            id = "side_weipop_skin_$idSuffix",
            labelRes = labelRes,
            leftRes = leftRes,
            rightRes = rightRes,
            titleColor = "#474747",
            paddingH = 14f,
            paddingV = 8f,
            cornerRadius = 0f,
        )
    }

    private fun weipopSideTheme(
        id: String,
        labelRes: Int,
        leftRes: Int,
        rightRes: Int,
        titleColor: String,
        paddingH: Float,
        paddingV: Float,
        cornerRadius: Float,
    ): MessageThemeSpec {
        val titleArgb = MessageThemeColors.parseHex(titleColor)
        return MessageThemeSpec(
            id = id,
            style = MessageStyle.SideBubble,
            labelRes = labelRes,
            backgroundResId = rightRes,
            sideLeftResId = leftRes,
            sideRightResId = rightRes,
            titleColorArgb = titleArgb,
            contentColorArgb = MessageThemeColors.contentColor(titleArgb),
            cornerRadiusDp = cornerRadius,
            paddingHorizontalDp = paddingH,
            paddingVerticalDp = paddingV,
            avatarStartPaddingDp = 8f,
        )
    }
}
