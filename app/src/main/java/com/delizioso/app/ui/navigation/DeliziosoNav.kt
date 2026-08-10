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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
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
import com.delizioso.app.ui.theme.clayInner
import com.delizioso.app.ui.theme.clayOuter

object Routes {
    const val LIBRARY = "library"
    const val PLANNER = "planner"
    const val IMPORT = "import"
    const val PROFILE = "profile"
    const val CREATE = "create"
    const val RECIPE_DETAIL = "recipe/{recipeId}"
    const val IMPORT_PREVIEW = "importPreview"
    const val GROCERY = "grocery"
    const val COOK = "cook/{recipeId}"
    const val COOK_COMPLETE = "cookComplete/{recipeId}"

    fun cook(recipeId: Long) = "cook/$recipeId"

    fun cookComplete(recipeId: Long) = "cookComplete/$recipeId"

    fun recipeDetail(recipeId: Long) = "recipe/$recipeId"

    /** Routes that show the floating dock. */
    val tabRoutes = listOf(LIBRARY, PLANNER, IMPORT, PROFILE)
}

private data class DockItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/** Floating clay "dock" — Library / Planner / Import / Profile (per mockups). */
@Composable
fun ClayDock(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
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
            .clayInner(shape = PillShape, cornerRadius = null, topLight = Color(0x40FFFFFF), bottomDark = Color(0x14000000))
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            DockItemView(item = item, selected = currentRoute == item.route, onNavigate = onNavigate)
        }
    }
}

@Composable
private fun DockItemView(
    item: DockItem,
    selected: Boolean,
    onNavigate: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val activeTint = MaterialTheme.colorScheme.onPrimaryContainer
    val inactiveTint = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clayInner(shape = PillShape, cornerRadius = null, topLight = Color(0x50FFFFFF), bottomDark = Color(0x20006E20))
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                } else {
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                }
            )
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Tab, onClick = { onNavigate(item.route) })
            .scale(if (pressed) 0.92f else 1f),
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
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
