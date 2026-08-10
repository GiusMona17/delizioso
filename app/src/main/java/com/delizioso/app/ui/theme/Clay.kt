package com.delizioso.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Claymorphism building blocks — "double inner shadows" + soft outer lift,
 * mirroring the mockups' box-shadow tokens (clay-outer / clay-inner / clay-btn).
 */
object ClayShadow {
    /** Soft neutral outer shadow (rgba(0,0,0,0.08)). */
    val outer = Color(0x14000000)
    val outerElevation = 16.dp

    /** Mint-tinted outer shadow for primary buttons (rgba(0,110,32,0.2)). */
    val buttonOuter = Color(0x33006E20)
    val buttonElevation = 12.dp

    /** Top-left inner highlight (white ~48%) — the "light hit". */
    val innerTopLight = Color(0x7AFFFFFF)

    /** Bottom-right inner shadow (darker version of the element color ~15%). */
    val innerBottomDark = Color(0x26000000)

    /** Pressed state: deeper, tighter inner shadow (button physically squished). */
    val pressedTopLight = Color(0x66FFFFFF)
    val pressedBottomDark = Color(0x40000000)
}

/** Soft colored outer lift, like the mockups' `clay-outer`. */
fun Modifier.clayOuter(
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color = ClayShadow.outer,
    elevation: Dp = ClayShadow.outerElevation,
): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    clip = false,
    ambientColor = color,
    spotColor = color,
)

/**
 * Inset ("carved out" / embossed) depth: white highlight top-left,
 * darker shadow bottom-right. `cornerRadius = null` yields a pill.
 */
fun Modifier.clayInner(
    shape: Shape = RoundedCornerShape(24.dp),
    cornerRadius: Dp? = 24.dp,
    topLight: Color = ClayShadow.innerTopLight,
    bottomDark: Color = ClayShadow.innerBottomDark,
): Modifier = clip(shape).drawWithCache {
    val radius = cornerRadius?.toPx() ?: (size.minDimension / 2f)
    val light = Brush.radialGradient(
        colors = listOf(topLight, Color.Transparent),
        center = Offset.Zero,
        radius = size.maxDimension,
    )
    val dark = Brush.radialGradient(
        colors = listOf(bottomDark, Color.Transparent),
        center = Offset(size.width, size.height),
        radius = size.maxDimension,
    )
    onDrawBehind {
        drawRoundRect(light, cornerRadius = CornerRadius(radius))
        drawRoundRect(dark, cornerRadius = CornerRadius(radius))
    }
}

/**
 * Standard clay card: soft outer shadow + rounded container.
 * (Inner depth is applied by `clayInner` on images/emphasized elements.)
 */
@Composable
fun Modifier.clayCard(
    container: Color,
    cornerRadius: Dp = 24.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
): Modifier {
    val cardShape = shape
    return this.then(
        Modifier
            .clayOuter(cardShape)
            .clip(cardShape)
            .background(container)
    )
}

/** Inset input / empty state: carved out of the clay canvas. */
@Composable
fun Modifier.clayInset(
    container: Color,
    cornerRadius: Dp = 16.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return this.then(
        Modifier
            .clip(shape)
            .background(container)
            .clayInner(shape, cornerRadius)
    )
}

/**
 * Primary pill button: tinted outer shadow + inner highlight/depth,
 * deepening when pressed (the button is "squished").
 */
@Composable
fun Modifier.clayButton(
    container: Color,
    pressed: Boolean = false,
): Modifier {
    val shape = PillShape
    return this.then(
        Modifier
            .then(if (pressed) Modifier else Modifier.clayOuter(shape, ClayShadow.buttonOuter, ClayShadow.buttonElevation))
            .clip(shape)
            .background(container)
            .clayInner(
                shape = shape,
                cornerRadius = null,
                topLight = if (pressed) ClayShadow.pressedTopLight else ClayShadow.innerTopLight,
                bottomDark = if (pressed) ClayShadow.pressedBottomDark else ClayShadow.innerBottomDark,
            )
    )
}
