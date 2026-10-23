package com.example.susnet

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

data class StatusPalette(
    @ColorInt val bg: Int,
    @ColorInt val surface: Int,
    @ColorInt val border: Int,
    @ColorInt val borderNormal: Int,
    @ColorInt val borderAbnormal: Int,
    @ColorInt val accent: Int,
    @ColorInt val textAccent: Int,
    @ColorInt val chipBg: Int,
    @ColorInt val tile: Int,
    @ColorInt val tileNormal: Int,
    @ColorInt val ink: Int,
    @ColorInt val secondary: Int,
    @ColorInt val navBg: Int,
    @ColorInt val buttonBg: Int,
    @ColorInt val buttonText: Int,
    @ColorInt val normalChipBg: Int,
    @ColorInt val normalChipText: Int,
    @ColorInt val normalTileIcon: Int,
    @ColorInt val gaugeGreen: Int,
    @ColorInt val gaugeAmber: Int,
    @ColorInt val gaugeRed: Int
) {
    companion object {
        fun from(level: StatusLevel, context: Context): StatusPalette {
            val normalChipBg = ContextCompat.getColor(context, R.color.normal_chip_bg)
            val normalChipText = ContextCompat.getColor(context, R.color.normal_chip_text)
            val normalTileIcon = ContextCompat.getColor(context, R.color.normal_tile_icon)
            val gaugeGreen = ContextCompat.getColor(context, R.color.gauge_zone_green)
            val gaugeAmber = ContextCompat.getColor(context, R.color.gauge_zone_amber)
            val gaugeRed = ContextCompat.getColor(context, R.color.gauge_zone_red)

            return when (level) {
                StatusLevel.AMAN -> StatusPalette(
                    bg = ContextCompat.getColor(context, R.color.aman_bg),
                    surface = ContextCompat.getColor(context, R.color.aman_surface),
                    border = ContextCompat.getColor(context, R.color.aman_border),
                    borderNormal = ContextCompat.getColor(context, R.color.aman_border_normal),
                    borderAbnormal = ContextCompat.getColor(context, R.color.aman_border),
                    accent = ContextCompat.getColor(context, R.color.aman_accent),
                    textAccent = ContextCompat.getColor(context, R.color.aman_text_accent),
                    chipBg = ContextCompat.getColor(context, R.color.aman_chip_bg),
                    tile = ContextCompat.getColor(context, R.color.aman_tile),
                    tileNormal = ContextCompat.getColor(context, R.color.aman_tile),
                    ink = ContextCompat.getColor(context, R.color.aman_ink),
                    secondary = ContextCompat.getColor(context, R.color.aman_secondary),
                    navBg = ContextCompat.getColor(context, R.color.aman_nav_bg),
                    buttonBg = ContextCompat.getColor(context, R.color.aman_accent),
                    buttonText = ContextCompat.getColor(context, R.color.aman_surface),
                    normalChipBg = normalChipBg,
                    normalChipText = normalChipText,
                    normalTileIcon = normalTileIcon,
                    gaugeGreen = gaugeGreen,
                    gaugeAmber = gaugeAmber,
                    gaugeRed = gaugeRed
                )
                StatusLevel.WASPADA -> StatusPalette(
                    bg = ContextCompat.getColor(context, R.color.waspada_bg),
                    surface = ContextCompat.getColor(context, R.color.waspada_surface),
                    border = ContextCompat.getColor(context, R.color.waspada_border),
                    borderNormal = ContextCompat.getColor(context, R.color.waspada_border_normal),
                    borderAbnormal = ContextCompat.getColor(context, R.color.waspada_border_abnormal),
                    accent = ContextCompat.getColor(context, R.color.waspada_accent),
                    textAccent = ContextCompat.getColor(context, R.color.waspada_text_accent),
                    chipBg = ContextCompat.getColor(context, R.color.waspada_chip_bg),
                    tile = ContextCompat.getColor(context, R.color.waspada_tile),
                    tileNormal = ContextCompat.getColor(context, R.color.waspada_tile_normal),
                    ink = ContextCompat.getColor(context, R.color.waspada_ink),
                    secondary = ContextCompat.getColor(context, R.color.waspada_secondary),
                    navBg = ContextCompat.getColor(context, R.color.waspada_nav_bg),
                    buttonBg = ContextCompat.getColor(context, R.color.waspada_button_bg),
                    buttonText = ContextCompat.getColor(context, R.color.waspada_button_text),
                    normalChipBg = normalChipBg,
                    normalChipText = normalChipText,
                    normalTileIcon = normalTileIcon,
                    gaugeGreen = gaugeGreen,
                    gaugeAmber = gaugeAmber,
                    gaugeRed = gaugeRed
                )
                StatusLevel.BAHAYA -> StatusPalette(
                    bg = ContextCompat.getColor(context, R.color.bahaya_bg),
                    surface = ContextCompat.getColor(context, R.color.bahaya_surface),
                    border = ContextCompat.getColor(context, R.color.bahaya_border),
                    borderNormal = ContextCompat.getColor(context, R.color.bahaya_border_normal),
                    borderAbnormal = ContextCompat.getColor(context, R.color.bahaya_border_abnormal),
                    accent = ContextCompat.getColor(context, R.color.bahaya_accent),
                    textAccent = ContextCompat.getColor(context, R.color.bahaya_text_accent),
                    chipBg = ContextCompat.getColor(context, R.color.bahaya_chip_bg),
                    tile = ContextCompat.getColor(context, R.color.bahaya_tile),
                    tileNormal = ContextCompat.getColor(context, R.color.bahaya_tile_normal),
                    ink = ContextCompat.getColor(context, R.color.bahaya_ink),
                    secondary = ContextCompat.getColor(context, R.color.bahaya_secondary),
                    navBg = ContextCompat.getColor(context, R.color.bahaya_nav_bg),
                    buttonBg = ContextCompat.getColor(context, R.color.bahaya_button_bg),
                    buttonText = ContextCompat.getColor(context, R.color.bahaya_button_text),
                    normalChipBg = normalChipBg,
                    normalChipText = normalChipText,
                    normalTileIcon = normalTileIcon,
                    gaugeGreen = gaugeGreen,
                    gaugeAmber = gaugeAmber,
                    gaugeRed = gaugeRed
                )
            }
        }
    }
}
