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
/**
 * The shadow colours the clay is built from.
 *
 * Neumorphism cannot simply be inverted for a dark theme: the illusion is "light
 * falls from the top-left", and in the dark that means a *faint* white edge over
 * a much deeper black — a flipped light palette reads as grey mush. So both sets
 * are written out, and [LocalClayShadows] hands the right one to every modifier.
 */
data class ClayShadows(
    /** Down-right drop shadow. */
    val dropDark: Color,
    /** Up-left light "shadow" — the neumorphic tell. */
    val dropLight: Color,
    /** Inner highlight, top-left. */
    val innerLight: Color,
    /** Inner shade, bottom-right. */
    val innerDark: Color,
    /** Accent inner shade used on primary buttons. */
    val innerAccent: Color,
    /** Stronger top-left highlight, for buttons and bars. */
    val highlight: Color,
    /** Faint accent wash used along the edge of bars and chips. */
    val accentSoft: Color,
    /** The carved-out look of inputs and tracks. */
    val insetDark: Color,
    val insetLight: Color,
    /** Drop shadow under a small raised control. */
    val buttonDark: Color,
    val buttonDarkPressed: Color,
) {
    companion object {
        val Light = ClayShadows(
            dropDark = Color(0x0D000000),
            dropLight = Color(0xCCFFFFFF),
            innerLight = Color(0x66FFFFFF),
            innerDark = Color(0x0D000000),
            innerAccent = Color(0x33006E20),
            highlight = Color(0x99FFFFFF),
            accentSoft = Color(0x1A006E20),
            insetDark = Color(0x14000000),
            insetLight = Color(0xCCFFFFFF),
            buttonDark = Color(0x1A000000),
            buttonDarkPressed = Color(0x40000000),
        )

        /**
         * Dark clay: the black shadow does nearly all the work, and the highlight
         * is a whisper. Using the light alphas here would wash the surface out.
         */
        val Dark = ClayShadows(
            dropDark = Color(0x73000000),
            dropLight = Color(0x0DFFFFFF),
            innerLight = Color(0x14FFFFFF),
            innerDark = Color(0x59000000),
            innerAccent = Color(0x4D8BE58F),
            highlight = Color(0x1AFFFFFF),
            // The bars bevel all four edges, which on a dark ground reads as a
            // drawn frame rather than a lit edge unless it is kept very faint.
            accentSoft = Color(0x148BE58F),
            insetDark = Color(0x66000000),
            insetLight = Color(0x14FFFFFF),
            buttonDark = Color(0x8C000000),
            buttonDarkPressed = Color(0xA6000000),
        )
    }
}

val LocalClayShadows = androidx.compose.runtime.staticCompositionLocalOf { ClayShadows.Light }

/** Shorthand for the shadow set in force, readable from any composable. */
val ClayShadow: ClayShadows
    @Composable get() = LocalClayShadows.current

object ClayMetrics {
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
@Composable
fun Modifier.clayOuter(
    shape: Shape = RoundedCornerShape(24.dp),
    /** Blur radius; the offset defaults to half of it, as in the mockups' 8px/16px pairing. */
    elevation: Dp = ClayMetrics.surfaceBlur,
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
@Composable
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
): Modifier {
    val shadows = ClayShadow
    return this
        .clip(shape)
        .background(container)
        .drawBehind {
            drawInnerShadow(shape, shadows.insetDark, 4.dp.toPx(), 4.dp.toPx(), 8.dp.toPx())
            drawInnerShadow(shape, shadows.insetLight, -4.dp.toPx(), -4.dp.toPx(), 8.dp.toPx())
        }
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
): Modifier {
    val shadows = ClayShadow
    return if (pressed) {
        this
            .clip(shape)
            .background(container)
            .drawBehind {
                drawInnerShadow(shape, shadows.innerAccent, 4.dp.toPx(), 4.dp.toPx(), 8.dp.toPx())
                drawInnerShadow(shape, shadows.highlight, -4.dp.toPx(), -4.dp.toPx(), 8.dp.toPx())
            }
    } else {
        this
            .clayOuter(
                shape,
                elevation = ClayMetrics.buttonBlur,
                dark = shadows.buttonDark,
                offset = ClayMetrics.buttonOffset,
            )
            .clip(shape)
            .background(container)
            .clayBevel(shape, light = shadows.highlight, dark = shadows.innerAccent)
    }
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
