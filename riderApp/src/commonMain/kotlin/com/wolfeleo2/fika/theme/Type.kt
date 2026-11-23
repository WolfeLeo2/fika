package com.wolfeleo2.fika.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fika.riderapp.generated.resources.Res
import fika.riderapp.generated.resources.google_sans_flex
import org.jetbrains.compose.resources.Font

/** Google Sans Flex at normal width, for body and labels. */
@Composable
private fun bodyFamily(): FontFamily = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(
            Res.font.google_sans_flex,
            weight = w,
            variationSettings = FontVariation.Settings(FontVariation.weight(w.weight), FontVariation.width(100f)),
        )
    },
)

/** Wide, heavy, fully rounded (ROND 100): display text and key numbers (ETA, KES). */
@Composable
private fun displayFamily(): FontFamily = FontFamily(
    Font(
        Res.font.google_sans_flex,
        weight = FontWeight.Black,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(800),
            FontVariation.opticalSizing(12.sp)

        ),
    ),
)

@Composable
fun fikaTypography(): Typography {
    val body = bodyFamily()
    val display = displayFamily()
    val base = Typography()
    fun TextStyle.display() = copy(fontFamily = display, fontWeight = FontWeight.Black)
    fun TextStyle.body(weight: FontWeight? = null) = copy(fontFamily = body, fontWeight = weight ?: fontWeight)
    return Typography(
        displayLarge = base.displayLarge.display(),
        displayMedium = base.displayMedium.display(),
        displaySmall = base.displaySmall.display(),
        headlineLarge = base.headlineLarge.display(),
        headlineMedium = base.headlineMedium.display(),
        headlineSmall = base.headlineSmall.display(),
        titleLarge = base.titleLarge.body(FontWeight.Bold),
        titleMedium = base.titleMedium.body(FontWeight.Bold),
        titleSmall = base.titleSmall.body(FontWeight.Bold),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.body(FontWeight.Bold),
        labelMedium = base.labelMedium.body(FontWeight.SemiBold),
        labelSmall = base.labelSmall.body(FontWeight.SemiBold),
    )
}
