package com.einkphoto.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val SystemFont = FontFamily.Default

private fun appTextStyle(
    weight: FontWeight,
    size: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    letterSpacing: androidx.compose.ui.unit.TextUnit = 0.sp,
) = TextStyle(
    fontFamily = SystemFont,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
)

val AppTypography = Typography(
    headlineLarge = appTextStyle(FontWeight.Bold, 34.sp, 40.sp, (-0.8).sp),
    headlineMedium = appTextStyle(FontWeight.Bold, 28.sp, 34.sp, (-0.6).sp),
    headlineSmall = appTextStyle(FontWeight.Bold, 24.sp, 30.sp, (-0.4).sp),
    titleLarge = appTextStyle(FontWeight.SemiBold, 20.sp, 26.sp, (-0.25).sp),
    titleMedium = appTextStyle(FontWeight.SemiBold, 16.sp, 22.sp, (-0.15).sp),
    titleSmall = appTextStyle(FontWeight.SemiBold, 14.sp, 20.sp),
    bodyLarge = appTextStyle(FontWeight.Normal, 16.sp, 25.sp),
    bodyMedium = appTextStyle(FontWeight.Normal, 14.sp, 21.sp),
    bodySmall = appTextStyle(FontWeight.Normal, 12.sp, 18.sp),
    labelLarge = appTextStyle(FontWeight.SemiBold, 14.sp, 20.sp),
    labelMedium = appTextStyle(FontWeight.Medium, 12.sp, 17.sp),
    labelSmall = appTextStyle(FontWeight.Medium, 11.sp, 16.sp),
)
