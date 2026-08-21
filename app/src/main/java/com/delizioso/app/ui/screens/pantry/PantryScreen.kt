package com.delizioso.app.ui.screens.pantry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.R
import com.delizioso.app.data.local.PantryItemEntity
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClaySegmentedTabs
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayBevel
import com.delizioso.app.ui.theme.clayCard

private val COMMON_STAPLES = listOf(
    "Olio d'oliva" to "Spices",
    "Sale" to "Spices",
    "Pepe nero" to "Spices",
    "Aglio" to "Produce",
    "Cipolla" to "Produce",
    "Uova" to "Dairy",
    "Burro" to "Dairy",
    "Latte" to "Dairy",
    "Farina 00" to "Pantry",
    "Pasta" to "Pantry",
    "Riso" to "Pantry",
    "Passata di pomodoro" to "Pantry",
    "Parmigiano Reggiano" to "Dairy",
    "Mozzarella" to "Dairy",
    "Guanciale" to "Meat",
    "Petto di pollo" to "Meat",
    "Basilico" to "Produce",
    "Limone" to "Produce",
)

@Composable
fun PantryScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PantryViewModel = viewModel(factory = PantryViewModel.Factory),
) {
    val items by viewModel.pantryItems.collectAsStateWithLifecycle()
    var selectedCategoryIndex by rememberSaveable { mutableIntStateOf(0) }
    var newItemName by rememberSaveable { mutableStateOf("") }
    var newItemQuantity by rememberSaveable { mutableStateOf("") }

    val categories = listOf(
        stringResource(R.string.pantry_cat_all),
        stringResource(R.string.pantry_cat_produce),
        stringResource(R.string.pantry_cat_dairy),
        stringResource(R.string.pantry_cat_meat),
        stringResource(R.string.pantry_cat_pantry),
        stringResource(R.string.pantry_cat_spices),
    )

    val categoryKeys = listOf("All", "Produce", "Dairy", "Meat", "Pantry", "Spices")
    val activeKey = categoryKeys.getOrElse(selectedCategoryIndex) { "All" }

    val filteredItems = if (activeKey == "All") {
        items
    } else {
        items.filter { it.category.equals(activeKey, ignoreCase = true) }
    }

    val inStock = filteredItems.filter { it.inStock }
    val outOfStock = filteredItems.filter { !it.inStock }

    val existingNames = remember(items) { items.map { it.name.lowercase().trim() }.toSet() }
    val availableStaples = remember(existingNames) {
        COMMON_STAPLES.filter { it.first.lowercase().trim() !in existingNames }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ClayTopBar(
            title = stringResource(R.string.pantry_title),
            onMenu = onBack,
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = stringResource(R.string.topbar_back),
        )

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Category Tabs
            item {
                ClaySegmentedTabs(
                    options = categories,
                    selectedIndex = selectedCategoryIndex,
                    onSelect = { selectedCategoryIndex = it },
                )
            }

            // Add Item Input Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 24.dp)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.pantry_add_item),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ClayTextField(
                            value = newItemName,
                            onValueChange = { newItemName = it },
                            placeholder = stringResource(R.string.pantry_name_label),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        ClayRoundButton(
                            icon = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.pantry_add_item),
                            onClick = {
                                if (newItemName.isNotBlank()) {
                                    val cat = if (activeKey != "All") activeKey else "Pantry"
                                    viewModel.addItem(newItemName, category = cat, quantity = newItemQuantity)
                                    newItemName = ""
                                    newItemQuantity = ""
                                }
                            },
                            container = MaterialTheme.colorScheme.primary,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            // Quick Staples suggestion rail
            if (availableStaples.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            stringResource(R.string.pantry_add_staple),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableStaples, key = { it.first }) { staple ->
                                Row(
                                    modifier = Modifier
                                        .clip(PillShape)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clayBevel(PillShape)
                                        .clickable { viewModel.addStaple(staple.first, staple.second) }
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        staple.first,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // In Stock Section
            if (inStock.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.pantry_in_stock, inStock.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(inStock, key = { it.id }) { item ->
                    PantryItemRow(
                        item = item,
                        onToggleStock = { viewModel.toggleStock(item.id, !item.inStock) },
                        onDelete = { viewModel.deleteItem(item.id) },
                    )
                }
            }

            // Out of Stock Section
            if (outOfStock.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.pantry_out_of_stock, outOfStock.size),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.pantry_clear_out),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(PillShape)
                                .clickable { viewModel.clearOutOfStock() }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                items(outOfStock, key = { it.id }) { item ->
                    PantryItemRow(
                        item = item,
                        onToggleStock = { viewModel.toggleStock(item.id, !item.inStock) },
                        onDelete = { viewModel.deleteItem(item.id) },
                    )
                }
            }

            // Empty state
            if (filteredItems.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Filled.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(48.dp),
                        )
                        Text(
                            stringResource(R.string.pantry_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(R.string.pantry_empty_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun PantryItemRow(
    item: PantryItemEntity,
    onToggleStock: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayCard(
                container = if (item.inStock) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerLowest,
                cornerRadius = 20.dp,
            )
            .clickable(onClick = onToggleStock)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (item.inStock) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (item.inStock) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(24.dp),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (item.inStock) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = if (item.inStock) null else TextDecoration.LineThrough,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.quantity.isNullOrBlank()) {
                Text(
                    item.quantity,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
