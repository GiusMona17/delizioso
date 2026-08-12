package com.delizioso.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.data.ImperialUnits
import com.delizioso.app.data.ai.RefineState
import androidx.compose.ui.res.stringResource
import com.delizioso.app.data.import.StructuredRecipe

/**
 * The "convert and translate" action, shared by the import preview and the edit
 * screen so a saved recipe gets the same treatment an imported one does.
 *
 * Highlighted only when the recipe actually contains cups or ounces; otherwise it
 * sits quietly as a secondary action, since translation is the rarer need.
 */
@Composable
fun RefineRecipeCard(
    draft: StructuredRecipe,
    state: RefineState,
    onRefine: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val imperial = ImperialUnits.isPresentIn(draft)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (imperial) {
            Text(
                stringResource(R.string.rewrite_imperial_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (state) {
            is RefineState.Failed -> Text(
                stringResource(R.string.rewrite_failed, state.message),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
            is RefineState.NothingToTranslate -> Text(
                stringResource(R.string.rewrite_already_translated),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Unit
        }
        ClayButton(
            text = stringResource(
                if (state is RefineState.Running) R.string.rewrite_running else R.string.rewrite_button
            ),
            icon = Icons.Filled.Translate,
            enabled = enabled && state !is RefineState.Running,
            onClick = onRefine,
            container = if (imperial) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            contentColor = if (imperial) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
