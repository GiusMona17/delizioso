package com.delizioso.app.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.delizioso.app.data.local.RecipeWithDetails
import java.io.File
import java.io.FileOutputStream

object RecipePdfExporter {

    // Standard A4 dimensions at 72 DPI (Points)
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842

    fun exportPdf(context: Context, details: RecipeWithDetails): File {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        // 1. Page Background & Border
        canvas.drawColor(Color.WHITE)

        val borderPaint = Paint().apply {
            color = Color.parseColor("#E0DDD5")
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        canvas.drawRect(RectF(24f, 24f, PAGE_WIDTH - 24f, PAGE_HEIGHT - 24f), borderPaint)

        var currentY = 56f

        // 2. Title & App Header
        val brandPaint = Paint().apply {
            color = Color.parseColor("#2E5940")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("DELIZIOSO KITCHEN RECIPE", 40f, currentY, brandPaint)
        currentY += 20f

        val titlePaint = TextPaint().apply {
            color = Color.parseColor("#1C1B18")
            textSize = 22f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val titleLayout = StaticLayout.Builder.obtain(
            details.recipe.title,
            0,
            details.recipe.title.length,
            titlePaint,
            PAGE_WIDTH - 80
        ).build()

        canvas.save()
        canvas.translate(40f, currentY)
        titleLayout.draw(canvas)
        canvas.restore()
        currentY += titleLayout.height + 12f

        // Description if present
        if (!details.recipe.description.isNullOrBlank()) {
            val descPaint = TextPaint().apply {
                color = Color.parseColor("#5A5751")
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                isAntiAlias = true
            }
            val descLayout = StaticLayout.Builder.obtain(
                details.recipe.description,
                0,
                details.recipe.description.length,
                descPaint,
                PAGE_WIDTH - 80
            ).setMaxLines(3).build()

            canvas.save()
            canvas.translate(40f, currentY)
            descLayout.draw(canvas)
            canvas.restore()
            currentY += descLayout.height + 14f
        }

        // Meta info strip (Servings, Prep, Cook, Calories, Macros)
        val metaBgPaint = Paint().apply {
            color = Color.parseColor("#F4F2EB")
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val metaRect = RectF(40f, currentY, PAGE_WIDTH - 40f, currentY + 32f)
        canvas.drawRoundRect(metaRect, 8f, 8f, metaBgPaint)

        val metaTextPaint = Paint().apply {
            color = Color.parseColor("#33312C")
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val metaParts = mutableListOf<String>()
        details.recipe.servings?.let { metaParts.add("Servings: $it") }
        details.recipe.prepTimeMinutes?.let { metaParts.add("Prep: ${it}m") }
        details.recipe.cookTimeMinutes?.let { metaParts.add("Cook: ${it}m") }
        details.recipe.caloriesKcal?.let { metaParts.add("Energy: ${it.toInt()} kcal") }
        if (details.recipe.proteinG != null || details.recipe.carbsG != null || details.recipe.fatG != null) {
            val p = details.recipe.proteinG?.toInt() ?: 0
            val c = details.recipe.carbsG?.toInt() ?: 0
            val f = details.recipe.fatG?.toInt() ?: 0
            metaParts.add("P: ${p}g • C: ${c}g • F: ${f}g")
        }

        val metaLine = metaParts.joinToString("   |   ")
        canvas.drawText(metaLine, 50f, currentY + 20f, metaTextPaint)
        currentY += 48f

        // 3. Two-Column Layout (Ingredients & Preparation)
        val colWidth = (PAGE_WIDTH - 80 - 24) / 2
        val leftColX = 40f
        val rightColX = 40f + colWidth + 24f

        val sectionTitlePaint = Paint().apply {
            color = Color.parseColor("#2E5940")
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("INGREDIENTS", leftColX, currentY + 12f, sectionTitlePaint)
        canvas.drawText("PREPARATION", rightColX, currentY + 12f, sectionTitlePaint)

        // Subtle divider line under headings
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#C8C4B8")
            strokeWidth = 1f
        }
        canvas.drawLine(leftColX, currentY + 18f, leftColX + colWidth, currentY + 18f, dividerPaint)
        canvas.drawLine(rightColX, currentY + 18f, rightColX + colWidth, currentY + 18f, dividerPaint)

        currentY += 30f
        val contentStartY = currentY

        // Ingredients Column (with checkboxes)
        val ingTextPaint = TextPaint().apply {
            color = Color.parseColor("#2B2925")
            textSize = 9.5f
            isAntiAlias = true
        }
        val boxPaint = Paint().apply {
            color = Color.parseColor("#888279")
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }

        var ingY = contentStartY
        for (ing in details.ingredients.sortedBy { it.position }) {
            canvas.drawRect(RectF(leftColX, ingY - 8f, leftColX + 8f, ingY), boxPaint)
            val amount = listOfNotNull(ing.quantity, ing.unit).joinToString(" ")
            val line = if (amount.isNotBlank()) "$amount ${ing.name}" else ing.name
            val ingLayout = StaticLayout.Builder.obtain(line, 0, line.length, ingTextPaint, colWidth - 16).build()

            canvas.save()
            canvas.translate(leftColX + 16f, ingY - 10f)
            ingLayout.draw(canvas)
            canvas.restore()
            ingY += ingLayout.height + 8f
        }

        // Preparation Steps Column
        val stepNumPaint = Paint().apply {
            color = Color.parseColor("#2E5940")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val stepTextPaint = TextPaint().apply {
            color = Color.parseColor("#2B2925")
            textSize = 9.5f
            isAntiAlias = true
        }

        var stepY = contentStartY
        for (step in details.steps.sortedBy { it.position }) {
            canvas.drawText("${step.position}.", rightColX, stepY, stepNumPaint)
            val stepLayout = StaticLayout.Builder.obtain(step.text, 0, step.text.length, stepTextPaint, colWidth - 18).build()

            canvas.save()
            canvas.translate(rightColX + 18f, stepY - 10f)
            stepLayout.draw(canvas)
            canvas.restore()
            stepY += stepLayout.height + 12f
        }

        // 4. Footer
        val footerPaint = Paint().apply {
            color = Color.parseColor("#9E9A90")
            textSize = 8f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Generated by Delizioso • Private On-Device Recipe Studio", PAGE_WIDTH / 2f, PAGE_HEIGHT - 36f, footerPaint)

        document.finishPage(page)

        // Save PDF to cache directory
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val exportFile = File(exportDir, "recipe_${details.recipe.id}.pdf")
        FileOutputStream(exportFile).use { out ->
            document.writeTo(out)
        }
        document.close()
        return exportFile
    }

    fun getShareableUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
