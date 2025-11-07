package com.example.momentag.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.momentag.R

val Pretandard =
    FontFamily(
        Font(R.font.pretendard_regular),
        Font(R.font.pretendard_bold, FontWeight.Bold),
    )

val MomenTagTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = Pretandard,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = Pretandard,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = Pretandard,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = Pretandard,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
        bodyLarge =
            TextStyle(
                fontFamily = Pretandard,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
            ),
    )
