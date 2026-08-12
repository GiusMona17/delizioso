package com.delizioso.app.ui.screens.grocery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.data.GroceryCategories
import com.delizioso.app.data.GroceryItem
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayCheckbox
import com.delizioso.app.ui.components.ClayEmptyState
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClaySegmentedTabs
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.ClayShadow
import com.delizioso.app.ui.theme.clayBevel
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

private const val GROUP_BY_RECIPE = 0

@Composable
fun GroceryScreen(
    onBack: () -> Unit,
    viewModel: GroceryViewModel = viewModel(factory = GroceryViewModel.Factory),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val checked by viewModel.checked.collectAsStateWithLifecycle()
    val customItems by viewModel.customItems.collectAsStateWithLifecycle()

    var grouping by rememberSaveable { mutableStateOf(GROUP_BY_RECIPE) }
    var newItem by rememberSaveable { mutableStateOf("") }

    val groups: List<Pair<String, List<GroceryItem>>> = if (grouping == GROUP_BY_RECIPE) {
        items
            .flatMap { item -> item.recipeTitles.ifEmpty { listOf(CUSTOM_ITEM_SOURCE) }.map { it to item } }
            .groupBy({ it.first }, { it.second })
            .toList()
            .sortedBy { it.first.lowercase() }
    } else {
        items
            .groupBy { it.category }
            .toList()
            .sortedBy { GroceryCategories.ORDER.indexOf(it.first).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayBevel(androidx.compose.foundation.shape.RoundedCornerShape(0.dp), light = ClayShadow.highlight, dark = ClayShadow.accentSoft)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClayRoundButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                container = MaterialTheme.colorScheme.surfaceContainerLowest,
            )
            Text(
                stringResource(R.string.grocery_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.size(48.dp))
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                ClaySegmentedTabs(
                    options = listOf(stringResource(R.string.grocery_by_recipe), stringResource(R.string.grocery_by_category)),
                    selectedIndex = grouping,
                    onSelect = { grouping = it },
                )
            }

            if (items.isEmpty()) {
                item {
                    ClayEmptyState(
                        icon = Icons.Filled.ShoppingCart,
                        title = stringResource(R.string.grocery_empty_title),
                        subtitle = stringResource(R.string.grocery_empty_sub),
                    )
                }
            }

            groups.forEach { (groupName, groupItems) ->
                item(key = "header-$groupName") {
                    Text(
                        if (groupName == CUSTOM_ITEM_SOURCE) stringResource(R.string.grocery_custom_source) else groupName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                items(groupItems, key = { "$groupName-${it.line}" }) { item ->
                    GroceryRow(
                        item = item,
                        checked = item.line in checked,
                        showSource = grouping != GROUP_BY_RECIPE,
                        onToggle = { viewModel.toggleChecked(item.line) },
                        onRemove = if (item.line in customItems) {
                            { viewModel.removeCustomItem(item.line) }
                        } else null,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ClayTextField(
                        value = newItem,
                        onValueChange = { newItem = it },
                        placeholder = stringResource(R.string.grocery_add_custom),
                        leadingIcon = Icons.Filled.Add,
                        cornerRadius = 24.dp,
                        modifier = Modifier.weight(1f),
                    )
                    ClayRoundButton(
                        icon = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.grocery_add_item),
                        onClick = {
                            viewModel.addCustomItem(newItem)
                            newItem = ""
                        },
                        container = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }

            if (checked.isNotEmpty()) {
                item {
                    ClayButton(
                        text = stringResource(R.string.grocery_clear_ticked, checked.size),
                        onClick = viewModel::clearChecked,
                        container = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun GroceryRow(
    item: GroceryItem,
    checked: Boolean,
    showSource: Boolean,
    onToggle: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 22.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ClayCheckbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                item.line,
                style = MaterialTheme.typography.bodyLarge,
                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None,
            )
            if (showSource && item.recipeTitles.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Icon(
                        Icons.Filled.Restaurant,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        item.recipeTitles
                            .map { title -> if (title == CUSTOM_ITEM_SOURCE) stringResource(R.string.grocery_custom_source) else title }
                            .joinToString(", "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (onRemove != null) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.grocery_remove_item),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(onClick = onRemove)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}
