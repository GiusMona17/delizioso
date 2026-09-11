package com.delizioso.app.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.data.local.CookingLogEntity
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.RecipeImage
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val StarAmber = Color(0xFFFFB300)

@Composable
fun CookingDiarySection(
    logs: List<CookingLogEntity>,
    onAddLog: () -> Unit,
    onEditLog: (CookingLogEntity) -> Unit,
    onDeleteLog: (CookingLogEntity) -> Unit,
    onPhotoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedLogs = remember(logs) {
        logs.sortedWith(compareByDescending<CookingLogEntity> { it.dateEpochDay }.thenByDescending { it.id })
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 24.dp)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Filled.HistoryEdu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = stringResource(R.string.cooking_diary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (logs.isNotEmpty()) {
                    ClayChip(
                        text = if (logs.size == 1) {
                            stringResource(R.string.cooking_diary_badge_single)
                        } else {
                            stringResource(R.string.cooking_diary_badge_multiple, logs.size)
                        },
                        container = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            if (logs.isNotEmpty()) {
                ClayRoundButton(
                    icon = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cooking_diary_add_action),
                    onClick = onAddLog,
                    container = MaterialTheme.colorScheme.surfaceContainerLowest,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Empty state
        if (sortedLogs.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 20.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.cooking_diary_empty_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.cooking_diary_empty_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ClayButton(
                    text = stringResource(R.string.cooking_diary_add_action),
                    icon = Icons.Filled.Add,
                    onClick = onAddLog,
                    container = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            // List of logged preparations
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sortedLogs.forEach { log ->
                    CookingLogItemCard(
                        log = log,
                        onEdit = { onEditLog(log) },
                        onDelete = { onDeleteLog(log) },
                        onPhotoClick = onPhotoClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun CookingLogItemCard(
    log: CookingLogEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPhotoClick: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val today = remember { LocalDate.now() }
    val yesterday = remember { today.minusDays(1) }
    val logDate = remember(log.dateEpochDay) { LocalDate.ofEpochDay(log.dateEpochDay) }
    val dateText = remember(logDate) {
        when (logDate) {
            today -> today.format(DateTimeFormatter.ofPattern("d MMM yyyy")) // e.g. "11 set 2026"
            yesterday -> yesterday.format(DateTimeFormatter.ofPattern("d MMM yyyy"))
            else -> logDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
        }
    }
    val relativeDate = remember(logDate) {
        when (logDate) {
            today -> "Oggi"
            yesterday -> "Ieri"
            else -> null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 20.dp)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Date, Rating, and Options
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (relativeDate != null) {
                    ClayChip(
                        text = relativeDate,
                        container = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Text(
                    text = dateText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Rating stars
            if (log.rating != null && log.rating > 0) {
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { star ->
                        Icon(
                            imageVector = if (star <= log.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null,
                            tint = if (star <= log.rating) StarAmber else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Box {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.planner_meal_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(PillShape)
                        .clickable { menuOpen = true }
                        .padding(6.dp),
                )
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cooking_diary_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cooking_diary_delete)) },
                        leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            showDeleteConfirm = true
                        },
                    )
                }
            }
        }

        // Content: Photo + Note text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!log.photoUri.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onPhotoClick(log.photoUri) },
                ) {
                    RecipeImage(
                        imageUri = log.photoUri,
                        placeholderIconSize = 24.dp,
                        modifier = Modifier.size(76.dp),
                    )
                }
            }

            if (log.notes.isNotBlank()) {
                Text(
                    text = log.notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.cooking_diary_delete)) },
            text = { Text(stringResource(R.string.cooking_diary_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.cooking_diary_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
