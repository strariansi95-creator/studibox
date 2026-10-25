package com.studibox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Тетрадный стиль: скругления небольшие, как у листа и кнопки, а не «таблетки». */
val StudiboxShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(10.dp),
    extraLarge = RoundedCornerShape(14.dp)
)

/** Единые радиусы и отступы — чтобы экраны не расходились по геометрии. */
object AppShapes {
    val card = RoundedCornerShape(8.dp)
    val cardLarge = RoundedCornerShape(10.dp)
    val field = RoundedCornerShape(10.dp)
    val button = RoundedCornerShape(10.dp)
    val chip = RoundedCornerShape(8.dp)
    val pill = RoundedCornerShape(50)
    val bottomBar = RoundedCornerShape(12.dp)
}

object Spacing {
    /** Отступ слева учитывает поле: текст начинается правее красной линии. */
    val screenHorizontal = 22.dp
    val cardPadding = 16.dp
    val cardPaddingLarge = 18.dp
    val betweenCards = 10.dp
    val betweenSections = 24.dp
}
