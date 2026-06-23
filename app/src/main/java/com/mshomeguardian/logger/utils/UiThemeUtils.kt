package com.mshomeguardian.logger.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors

fun Context.isDarkTheme(): Boolean {
    return resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
}

fun Context.themeColor(attr: Int, fallback: Int): Int {
    return MaterialColors.getColor(this, attr, fallback)
}

fun Context.surfaceColor(): Int {
    return themeColor(
        com.google.android.material.R.attr.colorSurface,
        if (isDarkTheme()) Color.parseColor("#121212") else Color.WHITE
    )
}

fun Context.surfaceVariantColor(): Int {
    return themeColor(
        com.google.android.material.R.attr.colorSurfaceVariant,
        if (isDarkTheme()) Color.parseColor("#1E1E1E") else Color.parseColor("#F1F3F4")
    )
}

fun Context.onSurfaceColor(): Int {
    return themeColor(
        com.google.android.material.R.attr.colorOnSurface,
        if (isDarkTheme()) Color.WHITE else Color.parseColor("#1F1F1F")
    )
}

fun Context.onSurfaceVariantColor(): Int {
    return themeColor(
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        if (isDarkTheme()) Color.parseColor("#B0B0B0") else Color.parseColor("#5F6368")
    )
}

fun Context.primaryColor(): Int {
    return themeColor(
        com.google.android.material.R.attr.colorPrimary,
        if (isDarkTheme()) Color.parseColor("#BB86FC") else Color.parseColor("#3F51B5")
    )
}

fun Context.onPrimaryColor(): Int {
    return themeColor(
        com.google.android.material.R.attr.colorOnPrimary,
        if (isDarkTheme()) Color.BLACK else Color.WHITE
    )
}

fun Context.outlineColor(): Int {
    return themeColor(
        com.google.android.material.R.attr.colorOutline,
        if (isDarkTheme()) Color.parseColor("#3A3A3A") else Color.parseColor("#D1D1D1")
    )
}

fun Context.successColor(): Int {
    return if (isDarkTheme()) Color.parseColor("#81C784") else Color.parseColor("#2E7D32")
}

fun Context.warningColor(): Int {
    return if (isDarkTheme()) Color.parseColor("#FFB74D") else Color.parseColor("#E65100")
}

fun Context.errorColor(): Int {
    return if (isDarkTheme()) Color.parseColor("#EF9A9A") else Color.parseColor("#C62828")
}

fun Context.infoColor(): Int {
    return if (isDarkTheme()) Color.parseColor("#90CAF9") else Color.parseColor("#1565C0")
}

fun Context.headerTintColor(): Int {
    return ColorUtils.blendARGB(surfaceColor(), primaryColor(), if (isDarkTheme()) 0.18f else 0.10f)
}

fun View.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

fun View.applyRoundedBackground(fillColor: Int, strokeColor: Int, radiusDp: Int = 16, strokeWidthDp: Int = 1) {
    background = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radiusDp).toFloat()
        setColor(fillColor)
        setStroke(dp(strokeWidthDp), strokeColor)
    }
}
