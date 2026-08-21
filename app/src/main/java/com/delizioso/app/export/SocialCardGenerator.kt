package com.delizioso.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.delizioso.app.data.Quantities
import com.delizioso.app.data.local.RecipeWithDetails
import java.io.File
import java.io.FileOutputStream

object SocialCardGenerator {

    const val CARD_WIDTH = 1080
    const val CARD_HEIGHT = 1350

    fun generateCard(context: Context, details: RecipeWithDetails): File {
        val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Warm Off-White / Clay Background
        canvas.drawColor(Color.parseColor("#F9F7F2"))

        // Subtle decorative outer border
        val borderPaint = Paint().apply {
            color = Color.parseColor("#E5DFD5")
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
        canvas.drawRoundRect(RectF(32f, 32f, CARD_WIDTH - 32f, CARD_HEIGHT - 32f), 36f, 36f, borderPaint)

        var currentY = 64f

        // 2. Hero Image or Stylized Header
        val heroHeight = 420f
        val heroRect = RectF(64f, currentY, CARD_WIDTH - 64f, currentY + heroHeight)
        val heroPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        var imageDrawn = false
        if (!details.recipe.imageUri.isNullOrBlank()) {
            runCatching {
                val uri = Uri.parse(details.recipe.imageUri)
                val stream = if (uri.scheme == "file") File(uri.path!!).inputStream() else context.contentResolver.openInputStream(uri)
                val originalBitmap = BitmapFactory.decodeStream(stream)
                stream?.close()
                if (originalBitmap != null) {
                    canvas.save()
                    val path = android.graphics.Path().apply {
                        addRoundRect(heroRect, 28f, 28f, android.graphics.Path.Direction.CW)
                    }
                    canvas.clipPath(path)
                    val srcRect = Rect(0, 0, originalBitmap.width, originalBitmap.height)
                    canvas.drawBitmap(originalBitmap, srcRect, heroRect, heroPaint)
                    canvas.restore()
                    imageDrawn = true
                }
            }
        }

        if (!imageDrawn) {
            // Elegant Mint/Sage Gradient Placeholder
            val placeholderPaint = Paint().apply {
                color = Color.parseColor("#E0EFE6")
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(heroRect, 28f, 28f, placeholderPaint)

            val placeholderTextPaint = Paint().apply {
                color = Color.parseColor("#2E5940")
                textSize = 64f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)
            }
            canvas.drawText("Delizioso Kitchen", heroRect.centerX(), heroRect.centerY() + 20f, placeholderTextPaint)
        }

        currentY += heroHeight + 40f

        // 3. Recipe Title & Badges
        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#1C1B18")
            textSize = 52f
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder.obtain(
            details.recipe.title,
            0,
            details.recipe.title.length,
            titlePaint,
            CARD_WIDTH - 128
        ).setMaxLines(2).build()

        canvas.save()
        canvas.translate(64f, currentY)
        titleLayout.draw(canvas)
        canvas.restore()
        currentY += titleLayout.height + 24f

        // Metadata Pills Row (Servings, Time, Calories)
        val pillBgPaint = Paint().apply {
            color = Color.parseColor("#EBE6DC")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val pillTextPaint = Paint().apply {
            color = Color.parseColor("#44413C")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val pills = mutableListOf<String>()
        details.recipe.servings?.let { pills.add("$it servings") }
        val totalTime = (details.recipe.prepTimeMinutes ?: 0) + (details.recipe.cookTimeMinutes ?: 0)
        if (totalTime > 0) pills.add("${totalTime}m total")
        details.recipe.caloriesKcal?.let { pills.add("${it.toInt()} kcal") }
        if (pills.isEmpty()) pills.add("Home Cooked")

        var pillX = 64f
        for (pill in pills) {
            val textWidth = pillTextPaint.measureText(pill)
            val pillRect = RectF(pillX, currentY, pillX + textWidth + 36f, currentY + 48f)
            canvas.drawRoundRect(pillRect, 24f, 24f, pillBgPaint)
            canvas.drawText(pill, pillX + 18f, currentY + 34f, pillTextPaint)
            pillX += textWidth + 48f
        }
        currentY += 72f

        // 4. Two-Column Content: Ingredients on Left, Steps on Right
        val colWidth = (CARD_WIDTH - 128 - 36) / 2
        val leftX = 64f
        val rightX = 64f + colWidth + 36f

        // Section Headers
        val sectionHeaderPaint = Paint().apply {
            color = Color.parseColor("#2E5940")
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("INGREDIENTS", leftX, currentY + 28f, sectionHeaderPaint)
        canvas.drawText("PREPARATION", rightX, currentY + 28f, sectionHeaderPaint)

        currentY += 48f
        val contentStartY = currentY

        // Ingredients Column
        val ingTextPaint = TextPaint().apply {
            color = Color.parseColor("#33312C")
            textSize = 26f
            isAntiAlias = true
        }
        var ingY = contentStartY
        val maxIngs = details.ingredients.sortedBy { it.position }.take(7)
        for (ing in maxIngs) {
            val amount = listOfNotNull(ing.quantity, ing.unit).joinToString(" ")
            val line = if (amount.isNotBlank()) "• $amount ${ing.name}" else "• ${ing.name}"
            val ingLayout = StaticLayout.Builder.obtain(line, 0, line.length, ingTextPaint, colWidth).setMaxLines(2).build()
            canvas.save()
            canvas.translate(leftX, ingY)
            ingLayout.draw(canvas)
            canvas.restore()
            ingY += ingLayout.height + 12f
        }
        if (details.ingredients.size > 7) {
            canvas.drawText("+ ${details.ingredients.size - 7} more ingredients…", leftX, ingY + 24f, ingTextPaint)
        }

        // Steps Column
        val stepTextPaint = TextPaint().apply {
            color = Color.parseColor("#33312C")
            textSize = 26f
            isAntiAlias = true
        }
        var stepY = contentStartY
        val maxSteps = details.steps.sortedBy { it.position }.take(4)
        for (step in maxSteps) {
            val text = "${step.position}. ${step.text}"
            val stepLayout = StaticLayout.Builder.obtain(text, 0, text.length, stepTextPaint, colWidth).setMaxLines(3).build()
            canvas.save()
            canvas.translate(rightX, stepY)
            stepLayout.draw(canvas)
            canvas.restore()
            stepY += stepLayout.height + 14f
        }

        // 5. Footer Branding
        val footerPaint = Paint().apply {
            color = Color.parseColor("#888279")
            textSize = 24f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }
        canvas.drawText("Delizioso • On-Device Culinary Studio", CARD_WIDTH / 2f, CARD_HEIGHT - 64f, footerPaint)

        // Save Bitmap to Cache Directory
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "recipe_${details.recipe.id}_card.png")
        FileOutputStream(exportFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return exportFile
    }

    fun getShareableUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
