// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.lysak.ping.R

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int) =
    Font(
        resId = R.font.inter_variable,
        weight = FontWeight(weight),
        variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
    )

val Inter =
    FontFamily(
        inter(300),
        inter(400),
        inter(500),
        inter(600),
        inter(700),
    )

private val Base = Typography()

val PingTypography =
    Typography(
        displayLarge = Base.displayLarge.copy(fontFamily = Inter),
        displayMedium = Base.displayMedium.copy(fontFamily = Inter),
        displaySmall = Base.displaySmall.copy(fontFamily = Inter),
        headlineLarge = Base.headlineLarge.copy(fontFamily = Inter),
        headlineMedium = Base.headlineMedium.copy(fontFamily = Inter),
        headlineSmall = Base.headlineSmall.copy(fontFamily = Inter),
        titleLarge = Base.titleLarge.copy(fontFamily = Inter),
        titleMedium = Base.titleMedium.copy(fontFamily = Inter),
        titleSmall = Base.titleSmall.copy(fontFamily = Inter),
        bodyLarge = Base.bodyLarge.copy(fontFamily = Inter),
        bodyMedium = Base.bodyMedium.copy(fontFamily = Inter),
        bodySmall = Base.bodySmall.copy(fontFamily = Inter),
        labelLarge = Base.labelLarge.copy(fontFamily = Inter),
        labelMedium = Base.labelMedium.copy(fontFamily = Inter),
        labelSmall = Base.labelSmall.copy(fontFamily = Inter),
    )
