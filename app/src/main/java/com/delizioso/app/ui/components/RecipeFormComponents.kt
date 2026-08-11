package com.delizioso.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Primary
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayBevel
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

/** A text row with a delete affordance — used by ingredient and step editors. */
@Composable
fun EditableLineRow(
    value: String,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        ClayTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = singleLine,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.RemoveCircleOutline,
            contentDescription = stringResource(R.string.form_remove),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .clip(PillShape)
                .clickable(role = Role.Button, onClick = onDelete)
                .padding(6.dp)
                .size(22.dp),
        )
    }
}

/** The embossed step number pod used by the Create screen's instruction rows. */
@Composable
fun StepNumberPod(number: Int, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clayBevel(PillShape),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Titled clay panel that groups a form section ("Ingredients", "Instructions"). */
@Composable
fun FormSectionCard(
    title: String,
    onAdd: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 28.dp)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (onAdd != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clayBevel(PillShape)
                        .clickable(role = Role.Button, onClick = onAdd),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.form_add_row), tint = Primary, modifier = Modifier.size(20.dp))
                }
            }
        }
        content()
    }
}
