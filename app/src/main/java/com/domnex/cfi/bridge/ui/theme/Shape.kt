package com.domnex.cfi.bridge.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ShapeInput = RoundedCornerShape(12.dp)
val ShapeControl = RoundedCornerShape(16.dp)
val ShapeCard = RoundedCornerShape(24.dp)
val ShapeSheet = RoundedCornerShape(32.dp)

val BridgeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = ShapeInput,
    medium = ShapeControl,
    large = ShapeCard,
    extraLarge = ShapeSheet
)
