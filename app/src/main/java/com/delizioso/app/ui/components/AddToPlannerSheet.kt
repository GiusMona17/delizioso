package com.delizioso.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.delizioso.app.data.local.MealSlot
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayInner
import com.delizioso.app.ui.theme.clayInset
import com.delizioso.app.ui.theme.clayOuter
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Icon per meal slot — matches the `add_to_planner_popup` mockup. */
fun mealSlotIcon(slot: String): ImageVector = when (slot) {
    MealSlot.BREAKFAST -> Icons.Filled.LocalCafe
    MealSlot.LUNCH -> Icons.Filled.LunchDining
    MealSlot.SNACK -> Icons.Filled.Cookie
    else -> Icons.Filled.Restaurant
}

/** How many days ahead the date picker offers. */
private const val DAYS_AHEAD = 14

/**
 * "Add to Planner" sheet content: the recipe, a date strip and a 2×2 meal-type
 * grid. Host it inside a `ModalBottomSheet`.
 */
@Composable
fun AddToPlannerSheet(
    details: RecipeWithDetails,
    onAdd: (epochDay: Long, slot: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    initialDate: LocalDate = LocalDate.now(),
    initialSlot: String = MealSlot.DINNER,
) {
    var selectedDay by rememberSaveable { mutableStateOf(initialDate.toEpochDay()) }
    var slot by rememberSaveable { mutableStateOf(initialSlot) }
    val today = LocalDate.now()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Add to Planner",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.Filled.Close,
                contentDescription = "Close",
                onClick = onClose,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 24.dp)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RecipeImage(
                details.recipe.imageUri,
                placeholderIconSize = 22.dp,
                modifier = Modifier.size(64.dp).clip(PillShape),
            )
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    details.recipe.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                )
                totalMinutes(details)?.let {
                    Text(
                        "$it mins",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Date", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayInset(MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 20.dp)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    dateLabel(LocalDate.ofEpochDay(selectedDay), today),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            LazyRow(
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items((0 until DAYS_AHEAD).toList(), key = { it }) { offset ->
                    val day = today.plusDays(offset.toLong())
                    DayPill(
                        day = day,
                        selected = day.toEpochDay() == selectedDay,
                        onClick = { selectedDay = day.toEpochDay() },
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Meal Type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MealSlot.all.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { option ->
                        MealTypeOption(
                            slot = option,
                            selected = slot == option,
                            onClick = { slot = option },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        ClayButton(
            text = "Add to Plan",
            icon = Icons.Filled.EventAvailable,
            onClick = { onAdd(selectedDay, slot) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DayPill(day: LocalDate, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clayOuter(shape = PillShape, elevation = if (selected) 12.dp else 8.dp)
            .clip(PillShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .clayInner(
                PillShape,
                cornerRadius = null,
                topLight = Color(0x99FFFFFF),
                bottomDark = if (selected) Color(0x33006E20) else Color(0x14000000),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "${day.dayOfMonth}",
            style = MaterialTheme.typography.headlineMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MealTypeOption(
    slot: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clayOuter(shape = PillShape, elevation = if (selected) 12.dp else 8.dp)
            .clip(PillShape)
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLowest
            )
            .clayInner(
                PillShape,
                cornerRadius = null,
                topLight = Color(0x99FFFFFF),
                bottomDark = if (selected) Color(0x33006E20) else Color(0x14000000),
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            mealSlotIcon(slot),
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Box(Modifier.size(8.dp))
        Text(
            MealSlot.label(slot),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dateLabel(day: LocalDate, today: LocalDate): String {
    val prefix = when (day.toEpochDay() - today.toEpochDay()) {
        0L -> "Today, "
        1L -> "Tomorrow, "
        else -> ""
    }
    return prefix + day.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + day.dayOfMonth
}
