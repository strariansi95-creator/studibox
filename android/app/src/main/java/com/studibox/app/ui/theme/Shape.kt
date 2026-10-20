package com.studibox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val StudiboxShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Единые радиусы и отступы — чтобы экраны не расходились по геометрии. */
object AppShapes {
    val card = RoundedCornerShape(20.dp)
    val cardLarge = RoundedCornerShape(24.dp)
    val field = RoundedCornerShape(16.dp)
    val button = RoundedCornerShape(18.dp)
    val chip = RoundedCornerShape(14.dp)
    val pill = RoundedCornerShape(50)
    val bottomBar = RoundedCornerShape(24.dp)
}

object Spacing {
    val screenHorizontal = 20.dp
    val cardPadding = 16.dp
    val cardPaddingLarge = 20.dp
    val betweenCards = 10.dp
    val betweenSections = 22.dp
}
