package com.delizioso.app.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Claymorphism building blocks, ported from the mockups' box-shadow tokens.
 *
 * The look is neumorphic: every raised element casts **two** shadows — a dark one
 * down-right and a light one up-left — over the creamy canvas. Compose's built-in
 * `Modifier.shadow` only draws the dark one, which is why these are hand-drawn
 * with a blur mask filter.
 *
 * ```
 * .clay-surface  8px 8px 16px rgba(0,0,0,.05), -8px -8px 16px rgba(255,255,255,.8),
 *                inset 2px 2px 4px rgba(255,255,255,.4), inset -2px -2px 4px rgba(0,0,0,.05)
 * .clay-inset    inset 4px 4px 8px rgba(0,0,0,.08), inset -4px -4px 8px rgba(255,255,255,.8)
 * .clay-button   4px 4px 8px rgba(0,0,0,.1), -4px -4px 8px rgba(255,255,255,.8),
 *                inset 2px 2px 4px rgba(255,255,255,.6), inset -2px -2px 4px rgba(0,110,32,.2)
 * ```
 */
object ClayShadow {
    /** Down-right drop shadow (rgba(0,0,0,0.05)). */
    val dropDark = Color(0x0D000000)

    /** Up-left light "shadow" — the neumorphic tell (rgba(255,255,255,0.8)). */
    val dropLight = Color(0xCCFFFFFF)

    /** Inner highlight, top-left (white 40%). */
    val innerLight = Color(0x66FFFFFF)

    /** Inner shade, bottom-right (black 5%). */
    val innerDark = Color(0x0D000000)

    /** Mint inner shade used on primary buttons (rgba(0,110,32,0.2)). */
    val innerMint = Color(0x33006E20)

    val surfaceOffset = 8.dp
    val surfaceBlur = 16.dp
    val buttonOffset = 4.dp
    val buttonBlur = 8.dp
}

// ---- Raised ---------------------------------------------------------------

/**
 * The paired outer shadows that lift an element off the canvas.
 * Draws behind the content, so apply it *before* `clip`/`background`.
 */
fun Modifier.clayOuter(
    shape: Shape = RoundedCornerShape(24.dp),
    /** Blur radius; the offset defaults to half of it, as in the mockups' 8px/16px pairing. */
    elevation: Dp = ClayShadow.surfaceBlur,
    dark: Color = ClayShadow.dropDark,
    light: Color = ClayShadow.dropLight,
    offset: Dp = elevation / 2,
): Modifier = drawBehind {
    val path = shape.toPath(size, this)
    val o = offset.toPx()
    val b = elevation.toPx()
    drawBlurred(path, light, -o, -o, b)
    drawBlurred(path, dark, o, o, b)
}

/**
 * The tight inner bevel that gives a raised element volume: a light hit along the
 * top-left inner edge and a soft shade along the bottom-right.
 */
fun Modifier.clayBevel(
    shape: Shape = RoundedCornerShape(24.dp),
    offset: Dp = 2.dp,
    blur: Dp = 4.dp,
    light: Color = ClayShadow.innerLight,
    dark: Color = ClayShadow.innerDark,
): Modifier = drawBehind {
    drawInnerShadow(shape, light, -offset.toPx(), -offset.toPx(), blur.toPx())
    drawInnerShadow(shape, dark, offset.toPx(), offset.toPx(), blur.toPx())
}

/** Standard clay card: paired outer shadows + rounded container + inner bevel. */
@Composable
fun Modifier.clayCard(
    container: Color,
    cornerRadius: Dp = 24.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
): Modifier = this
    .clayOuter(shape)
    .clip(shape)
    .background(container)
    .clayBevel(shape)

/**
 * "Carved out of the clay" — inputs, progress tracks, segmented backgrounds.
 * Deeper and tighter than [clayBevel], with the light and dark sides swapped.
 */
@Composable
fun Modifier.clayInset(
    container: Color,
    cornerRadius: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
): Modifier = this
    .clip(shape)
    .background(container)
    .drawBehind {
        drawInnerShadow(shape, Color(0x14000000), 4.dp.toPx(), 4.dp.toPx(), 8.dp.toPx())
        drawInnerShadow(shape, Color(0xCCFFFFFF), -4.dp.toPx(), -4.dp.toPx(), 8.dp.toPx())
    }

/**
 * Primary pill button: paired outer shadows with a mint-tinted inner shade.
 * Pressing swaps to a pure inset — the button is physically squished.
 */
@Composable
fun Modifier.clayButton(
    container: Color,
    pressed: Boolean = false,
    shape: Shape = PillShape,
): Modifier = if (pressed) {
    this
        .clip(shape)
        .background(container)
        .drawBehind {
            drawInnerShadow(shape, ClayShadow.innerMint, 4.dp.toPx(), 4.dp.toPx(), 8.dp.toPx())
            drawInnerShadow(shape, Color(0x99FFFFFF), -4.dp.toPx(), -4.dp.toPx(), 8.dp.toPx())
        }
} else {
    this
        .clayOuter(shape, elevation = ClayShadow.buttonBlur, dark = Color(0x1A000000), offset = ClayShadow.buttonOffset)
        .clip(shape)
        .background(container)
        .clayBevel(shape, light = Color(0x99FFFFFF), dark = ClayShadow.innerMint)
}

// ---- Drawing primitives ----------------------------------------------------

private fun Shape.toPath(size: Size, density: DrawScope): Path =
    when (val outline = createOutline(size, density.layoutDirection, density)) {
        is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
        is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
        is Outline.Generic -> outline.path
    }

/** Fills [path] offset by ([dx], [dy]) through a Gaussian blur. */
private fun DrawScope.drawBlurred(path: Path, color: Color, dx: Float, dy: Float, blur: Float) {
    if (color.alpha == 0f) return
    drawIntoCanvas { canvas ->
        val paint = Paint()
        paint.asFrameworkPaint().apply {
            isAntiAlias = true
            this.color = color.toArgb()
            if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.save()
        canvas.translate(dx, dy)
        canvas.drawPath(path, paint)
        canvas.restore()
    }
}

/**
 * CSS `inset` box-shadow: the shadow lives *inside* the shape, hugging the edge
 * the offset points away from. Built by subtracting the offset shape from the
 * shape itself and blurring the remainder, clipped back to the shape.
 */
private fun DrawScope.drawInnerShadow(
    shape: Shape,
    color: Color,
    dx: Float,
    dy: Float,
    blur: Float,
) {
    if (color.alpha == 0f) return
    val path = shape.toPath(size, this)
    // A generous outer ring, minus the shape shifted by the offset: what's left is
    // exactly the sliver of shadow the offset exposes along the inner edge.
    val ring = Path().apply {
        addRect(
            androidx.compose.ui.geometry.Rect(
                -blur * 4f,
                -blur * 4f,
                size.width + blur * 4f,
                size.height + blur * 4f,
            )
        )
    }
    val shifted = Path().apply {
        addPath(path, androidx.compose.ui.geometry.Offset(dx, dy))
    }
    val shadow = Path().apply { op(ring, shifted, PathOperation.Difference) }
    clipPath(path) {
        drawIntoCanvas { canvas ->
            val paint = Paint()
            paint.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = color.toArgb()
                if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawPath(shadow, paint)
        }
    }
}
