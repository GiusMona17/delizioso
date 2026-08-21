package com.delizioso.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.ClayShadow
import com.delizioso.app.ui.theme.clayBevel
import com.delizioso.app.ui.theme.clayOuter

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val PLANNER = "planner"
    const val IMPORT = "import"
    const val PROFILE = "profile"
    const val CREATE = "create"
    const val RECIPE_DETAIL = "recipe/{recipeId}"
    const val RECIPE_EDIT = "recipe/{recipeId}/edit"
    const val IMPORT_PREVIEW = "importPreview"
    const val IMPORT_SEARCH = "importSearch"
    const val GROCERY = "grocery"
    const val RECIPE_SOURCES = "profile/sources"
    const val COOK = "cook/{recipeId}"
    const val COOK_COMPLETE = "cookComplete/{recipeId}"

    fun cook(recipeId: Long) = "cook/$recipeId"

    fun cookComplete(recipeId: Long) = "cookComplete/$recipeId"

    fun recipeDetail(recipeId: Long) = "recipe/$recipeId"

    fun recipeEdit(recipeId: Long) = "recipe/$recipeId/edit"

    /** Routes that show the floating dock. */
    val tabRoutes = listOf(HOME, LIBRARY, PLANNER, IMPORT, PROFILE)
}

private data class DockItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** Floating clay "dock" — Home / Library / Planner / Import / Profile. */
@Composable
fun ClayDock(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        DockItem(Routes.HOME, stringResource(R.string.nav_home), Icons.Filled.Home),
        DockItem(Routes.LIBRARY, stringResource(R.string.nav_library), Icons.AutoMirrored.Filled.MenuBook),
        DockItem(Routes.PLANNER, stringResource(R.string.nav_planner), Icons.Filled.CalendarMonth),
        DockItem(Routes.IMPORT, stringResource(R.string.nav_import), Icons.Filled.Download),
        DockItem(Routes.PROFILE, stringResource(R.string.nav_profile), Icons.Filled.Person),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .clayOuter(shape = PillShape, elevation = 24.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clayBevel(PillShape, light = Color(0x40FFFFFF), dark = ClayShadow.insetDark)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            DockItemView(
                item = item,
                selected = currentRoute == item.route,
                onNavigate = onNavigate,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DockItemView(
    item: DockItem,
    selected: Boolean,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val activeTint = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier
            .scale(if (pressed) 0.92f else 1f)
            .then(
                if (selected) {
                    Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clayBevel(PillShape, light = ClayShadow.innerLight, dark = ClayShadow.innerAccent)
                } else {
                    Modifier.clip(PillShape)
                }
            )
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = { onNavigate(item.route) })
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            item.icon,
            contentDescription = item.label,
            tint = if (selected) activeTint else inactiveTint,
            modifier = Modifier.size(24.dp),
        )
        Text(
            item.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) activeTint else inactiveTint,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
