package com.delizioso.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delizioso.app.data.local.RecipeWithDetails
import com.delizioso.app.ui.theme.CardImageRadius
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Primary
import com.delizioso.app.ui.theme.clayButton
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayInner
import com.delizioso.app.ui.theme.clayInset
import com.delizioso.app.ui.theme.clayOuter

/** Card corner radius used by the mockups' "clay-surface" panels. */
val ClayCardRadius = 28.dp

// ---- Top app bar -----------------------------------------------------------

/**
 * Menu · centred title · profile — the mockups' sticky header, with the inset
 * "light from below" shadow along its bottom edge.
 */
@Composable
fun ClayTopBar(
    modifier: Modifier = Modifier,
    title: String = "Delizioso!",
    onMenu: (() -> Unit)? = null,
    menuIcon: ImageVector = Icons.Filled.Menu,
    menuDescription: String = "Menu",
    onProfile: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayInner(
                shape = RoundedCornerShape(0.dp),
                cornerRadius = 0.dp,
                topLight = Color(0x99FFFFFF),
                bottomDark = Color(0x1A006E20),
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp)) {
            if (onMenu != null) {
                ClayRoundButton(icon = menuIcon, contentDescription = menuDescription, onClick = onMenu)
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = Primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Box(Modifier.size(48.dp)) {
            if (onProfile != null) {
                ClayRoundButton(icon = Icons.Filled.Person, contentDescription = "Profile", onClick = onProfile)
            }
        }
    }
}

/** Small circular clay button used for icon actions. */
@Composable
fun ClayRoundButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    tint: Color = Primary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Box(
        modifier = modifier
            .size(48.dp)
            .scale(if (pressed) 0.95f else 1f)
            .clayOuter(shape = PillShape, elevation = 12.dp)
            .clip(PillShape)
            .background(container)
            .clayInner(
                shape = PillShape,
                cornerRadius = null,
                topLight = if (pressed) Color(0x66FFFFFF) else Color(0x7AFFFFFF),
                bottomDark = if (pressed) Color(0x40000000) else Color(0x26000000),
            )
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

// ---- Buttons ---------------------------------------------------------------

@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier = modifier
            .scale(if (pressed) 0.97f else 1f)
            .clayButton(container = container, pressed = pressed)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(vertical = 16.dp, horizontal = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) contentColor else contentColor.copy(alpha = 0.6f))
    }
}

/** Outlined pill — the mockups' secondary action ("Share My Creation"). */
@Composable
fun ClayOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clayOuter(shape = PillShape, elevation = 10.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(1.dp, Primary.copy(alpha = 0.35f), PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 16.dp, horizontal = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Primary)
    }
}

// ---- Inputs ----------------------------------------------------------------

@Composable
fun ClayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: ImageVector? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clayInset(MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = cornerRadius)
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primaryContainer, shape)
                else Modifier
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = if (trailing != null) 6.dp else 16.dp, top = 10.dp, bottom = 10.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = singleLine,
                cursorBrush = SolidColor(Primary),
                keyboardOptions = keyboardOptions,
                interactionSource = interactionSource,
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                trailing()
            }
        }
    }
}

/** Labelled form field — "Recipe Title" over an inset input (Create screen). */
@Composable
fun ClayLabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ClayTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- Chips ----------------------------------------------------------------

@Composable
fun ClayChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(14.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = contentColor)
    }
}

/** Selectable category pill — raised when idle, "squished" mint when selected. */
@Composable
fun ClayFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "filterChipContainer",
    )
    Box(
        modifier = modifier
            .scale(if (pressed) 0.95f else 1f)
            .clayOuter(shape = PillShape, elevation = if (selected) 10.dp else 8.dp)
            .clip(PillShape)
            .background(container)
            .clayInner(
                shape = PillShape,
                cornerRadius = null,
                topLight = Color(0x99FFFFFF),
                bottomDark = if (selected) Color(0x33006E20) else Color(0x14000000),
            )
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 10.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dietary / category label with a 1px soft border, per DESIGN.md "Chips/Tags". */
@Composable
fun ClayTagChip(text: String, modifier: Modifier = Modifier) {
    val palette = tagPalette(text)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = palette.second,
        modifier = modifier
            .clip(PillShape)
            .background(palette.first.copy(alpha = 0.5f))
            .border(1.dp, palette.second.copy(alpha = 0.2f), PillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Stable container/content colours per tag name (mint / peach / olive rotation). */
@Composable
private fun tagPalette(tag: String): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (Math.floorMod(tag.lowercase().hashCode(), 3)) {
        0 -> scheme.primaryContainer to scheme.onPrimaryContainer
        1 -> scheme.secondaryContainer to scheme.onSecondaryContainer
        else -> scheme.tertiaryContainer to scheme.onTertiaryContainer
    }
}

// ---- Segmented toggle ------------------------------------------------------

/** Two-up inset toggle — "Ingredients | Instructions", "By Recipe | By Category". */
@Composable
fun ClaySegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayInset(MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 24.dp)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (selected) {
                            Modifier
                                .clayOuter(shape = PillShape, elevation = 8.dp)
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clayInner(PillShape, cornerRadius = null, topLight = Color(0x99FFFFFF), bottomDark = Color(0x33006E20))
                        } else {
                            Modifier.clip(PillShape)
                        }
                    )
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    option,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- Checkbox --------------------------------------------------------------

/** Rounded-square clay checkbox — "carved out" when empty, mint when ticked. */
@Composable
fun ClayCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .size(26.dp)
            .clip(shape)
            .background(if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clayInner(shape, cornerRadius = 8.dp, topLight = Color(0x33000000), bottomDark = Color(0x66FFFFFF))
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Primary, modifier = Modifier.size(18.dp))
        }
    }
}

/** Circular "step done" pod used by cook mode. */
@Composable
fun ClayStepPod(
    done: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(PillShape)
            .background(if (done) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest)
            .clayInner(PillShape, cornerRadius = null, topLight = Color(0x26000000), bottomDark = Color(0x66FFFFFF))
            .clickable(role = Role.Checkbox, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
        }
    }
}

// ---- Recipe cards ----------------------------------------------------------

/**
 * Library card, per `home_library_updated_nav`: photo with a time badge and a
 * favourite button, then tags, title and a two-line description.
 */
@Composable
fun ClayRecipeCard(
    details: RecipeWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleFavorite: (() -> Unit)? = null,
) {
    val recipe = details.recipe
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Column(
        modifier = modifier
            .scale(if (pressed) 0.985f else 1f)
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = ClayCardRadius)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(CardImageRadius)),
        ) {
            RecipeImage(recipe.imageUri, modifier = Modifier.fillMaxSize())
            totalMinutes(details)?.let { minutes ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                    Text("${minutes}m", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            if (onToggleFavorite != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(40.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                        .clickable(role = Role.Button, onClick = onToggleFavorite),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (recipe.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favourite",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        if (details.tags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                details.tags.take(3).forEach { ClayTagChip(it.name) }
            }
        }
        Text(
            text = recipe.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        recipe.description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Compact card used by the Import screen's "Recent Imports" rail. */
@Composable
fun ClayRecipeMiniCard(
    details: RecipeWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(170.dp)
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 24.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RecipeImage(
            details.recipe.imageUri,
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(CardImageRadius)),
        )
        Text(
            details.recipe.title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            sourceLabel(details),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Horizontal recipe row — planner meal slots and pickers. */
@Composable
fun ClayRecipeRow(
    details: RecipeWithDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 24.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RecipeImage(
            details.recipe.imageUri,
            placeholderIconSize = 24.dp,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(CardImageRadius)),
        )
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                details.recipe.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                totalMinutes(details)?.let {
                    ClayChip("${it}m", icon = Icons.Filled.Schedule)
                }
                details.tags.firstOrNull()?.let { ClayTagChip(it.name) }
            }
        }
        trailing?.invoke()
    }
}

/** Prep + cook, or whichever of the two is known. */
fun totalMinutes(details: RecipeWithDetails): Int? {
    val total = (details.recipe.prepTimeMinutes ?: 0) + (details.recipe.cookTimeMinutes ?: 0)
    return total.takeIf { it > 0 }
}

fun sourceLabel(details: RecipeWithDetails): String {
    val source = details.source ?: return "Created by you"
    source.author?.takeIf { it.isNotBlank() }?.let { return "From $it" }
    return "From " + when (source.platform) {
        "INSTAGRAM" -> "Instagram"
        "FACEBOOK" -> "Facebook"
        "TIKTOK" -> "TikTok"
        "YOUTUBE" -> "YouTube"
        "BLOG" -> "the web"
        "OCR" -> "a scan"
        else -> "Delizioso"
    }
}

// ---- Empty state -----------------------------------------------------------

@Composable
fun ClayEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clayOuter(shape = PillShape)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .clayInner(PillShape, cornerRadius = null),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

// ---- Section header --------------------------------------------------------

@Composable
fun ClaySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                style = MaterialTheme.typography.labelLarge,
                color = Primary,
                modifier = Modifier
                    .clip(PillShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

/** Small all-caps section label with a leading icon ("BREAKFAST", "Produce"). */
@Composable
fun ClayGroupLabel(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp),
        )
    }
}

/** Dashed "nothing here yet — tap to add" panel used by the planner. */
@Composable
fun ClayAddPanel(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = Primary,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clayOuter(shape = PillShape, elevation = 10.dp)
                .clip(PillShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(26.dp))
        }
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
