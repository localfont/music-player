package com.github.anrimian.musicplayer.ui.common

import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

fun isWhiteContrast(@ColorInt color: Int) = ColorUtils.calculateLuminance(color) >= 0.5f