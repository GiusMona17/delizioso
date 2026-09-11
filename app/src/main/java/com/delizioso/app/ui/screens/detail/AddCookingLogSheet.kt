package com.delizioso.app.ui.screens.detail

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.data.ImageStore
import com.delizioso.app.data.local.CookingLogEntity
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayOutlinedButton
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.components.RecipeImage
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val StarAmber = Color(0xFFFFB300)

@Composable
fun AddCookingLogSheet(
    initialLog: CookingLogEntity? = null,
    onSave: (dateEpochDay: Long, notes: String, photoUri: String?, rating: Int?) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var dateEpochDay by rememberSaveable { mutableStateOf(initialLog?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var notes by rememberSaveable { mutableStateOf(initialLog?.notes ?: "") }
    var photoUri by rememberSaveable { mutableStateOf(initialLog?.photoUri) }
    var rating by rememberSaveable { mutableStateOf(initialLog?.rating) }

    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingCameraPath
        pendingCameraPath = null
        if (success && path != null) {
            photoUri = path
        } else if (path != null) {
            ImageStore.deleteIfOwned(context, path)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val savedPath = runCatching { ImageStore.saveToInternal(context, uri) }.getOrNull()
            if (savedPath != null) {
                photoUri = savedPath
            }
        }
    }

    val selectedDate = remember(dateEpochDay) { LocalDate.ofEpochDay(dateEpochDay) }
    val today = remember { LocalDate.now() }
    val yesterday = remember { today.minusDays(1) }
    val dateLabel = remember(selectedDate) {
        when (selectedDate) {
            today -> "${context.getString(R.string.cooking_diary_today)} (${selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))})"
            yesterday -> "${context.getString(R.string.cooking_diary_yesterday)} (${selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))})"
            else -> selectedDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (initialLog != null) R.string.cooking_diary_edit_title else R.string.cooking_diary_new_title
                ),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = stringResource(R.string.topbar_close),
                onClick = onClose,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
        }

        // Date selection
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.cooking_diary_date),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 18.dp)
                    .clickable {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val picked = LocalDate.of(year, month + 1, dayOfMonth)
                                dateEpochDay = picked.toEpochDay()
                            },
                            selectedDate.year,
                            selectedDate.monthValue - 1,
                            selectedDate.dayOfMonth,
                        ).show()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Rating
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.cooking_diary_rating),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (1..5).forEach { starIndex ->
                    val isFilled = (rating ?: 0) >= starIndex
                    Icon(
                        imageVector = if (isFilled) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "$starIndex stars",
                        tint = if (isFilled) StarAmber else MaterialTheme.colorScheme.outline,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable {
                                rating = if (rating == starIndex) null else starIndex
                            }
                            .padding(2.dp),
                    )
                }
            }
        }

        // Notes field
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.cooking_diary_notes_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ClayTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = stringResource(R.string.cooking_diary_notes_hint),
                singleLine = false,
                minLines = 4,
                cornerRadius = 20.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Photo Attachment
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.cooking_diary_photo_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val currentPhoto = photoUri
            if (!currentPhoto.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(20.dp)),
                ) {
                    RecipeImage(
                        imageUri = currentPhoto,
                        placeholderIconSize = 36.dp,
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                            .clickable { photoUri = null },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.cooking_diary_remove_photo),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ClayOutlinedButton(
                        text = stringResource(R.string.cooking_diary_take_photo),
                        icon = Icons.Filled.PhotoCamera,
                        onClick = {
                            val (path, uri) = ImageStore.newCameraTarget(context)
                            pendingCameraPath = path
                            runCatching { cameraLauncher.launch(uri) }.onFailure {
                                pendingCameraPath = null
                                ImageStore.deleteIfOwned(context, path)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ClayOutlinedButton(
                        text = stringResource(R.string.cooking_diary_pick_photo),
                        icon = Icons.Filled.Image,
                        onClick = { galleryLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Save Button
        ClayButton(
            text = stringResource(R.string.cooking_diary_save),
            onClick = {
                onSave(dateEpochDay, notes, photoUri, rating)
            },
            enabled = notes.isNotBlank() || !photoUri.isNullOrBlank(),
            container = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
