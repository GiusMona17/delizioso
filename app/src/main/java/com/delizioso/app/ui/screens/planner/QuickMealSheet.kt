package com.delizioso.app.ui.screens.planner

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.delizioso.app.R
import com.delizioso.app.data.ai.QuickMealEstimator
import com.delizioso.app.data.export.QuickMealPrompt
import com.delizioso.app.data.import.QuickMealJsonParser
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.theme.clayCard
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun QuickMealSheet(
    slotTitle: String,
    initialDescription: String = "",
    estimator: QuickMealEstimator? = null,
    onSave: (name: String, kcal: Double?, p: Double?, f: Double?, c: Double?) -> Unit,
    onClose: () -> Unit,
) {
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var name by rememberSaveable { mutableStateOf(initialDescription) }
    var kcal by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var isEstimating by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val language = remember { Locale.getDefault().getDisplayLanguage(Locale.ENGLISH) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.quick_meal_title) + " ($slotTitle)",
                style = MaterialTheme.typography.headlineMedium,
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

        ClayTextField(
            value = description,
            onValueChange = {
                description = it
                if (name.isBlank() || name == description.take(name.length)) {
                    name = it
                }
            },
            placeholder = stringResource(R.string.quick_meal_food_hint),
            singleLine = false,
            minLines = 2,
            cornerRadius = 20.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        // AI Tools Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLowest, cornerRadius = 20.dp)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ClayButton(
                    text = stringResource(R.string.quick_meal_ai_copy),
                    icon = Icons.Filled.AutoAwesome,
                    onClick = {
                        val prompt = QuickMealPrompt.buildPrompt(description.ifBlank { name }, language)
                        clipboard.setText(AnnotatedString(prompt))
                    },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ClayButton(
                    text = stringResource(R.string.quick_meal_ai_send),
                    icon = Icons.Filled.IosShare,
                    onClick = {
                        val prompt = QuickMealPrompt.buildPrompt(description.ifBlank { name }, language)
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, prompt)
                        }
                        context.startActivity(Intent.createChooser(send, context.getString(R.string.quick_meal_ai_send)))
                    },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ClayButton(
                    text = stringResource(R.string.quick_meal_ai_paste),
                    icon = Icons.Filled.ContentPaste,
                    onClick = { showPasteDialog = true },
                    container = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                if (estimator != null) {
                    ClayButton(
                        text = if (isEstimating) stringResource(R.string.detail_thinking) else stringResource(R.string.quick_meal_ai_estimate),
                        icon = Icons.Filled.Bolt,
                        enabled = !isEstimating && description.isNotBlank(),
                        onClick = {
                            scope.launch {
                                isEstimating = true
                                val result = estimator.estimate(description, language)
                                if (result != null) {
                                    name = result.name
                                    result.caloriesKcal?.let { kcal = it.toInt().toString() }
                                    result.proteinG?.let { protein = it.toInt().toString() }
                                    result.carbsG?.let { carbs = it.toInt().toString() }
                                    result.fatG?.let { fat = it.toInt().toString() }
                                }
                                isEstimating = false
                            }
                        },
                        container = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        ClayTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = stringResource(R.string.quick_meal_name_hint),
            singleLine = true,
            cornerRadius = 18.dp,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ClayTextField(
                value = kcal,
                onValueChange = { kcal = it },
                placeholder = stringResource(R.string.quick_meal_kcal),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cornerRadius = 16.dp,
                modifier = Modifier.weight(1f),
            )
            ClayTextField(
                value = protein,
                onValueChange = { protein = it },
                placeholder = stringResource(R.string.quick_meal_protein),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cornerRadius = 16.dp,
                modifier = Modifier.weight(1f),
            )
            ClayTextField(
                value = carbs,
                onValueChange = { carbs = it },
                placeholder = stringResource(R.string.quick_meal_carbs),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cornerRadius = 16.dp,
                modifier = Modifier.weight(1f),
            )
            ClayTextField(
                value = fat,
                onValueChange = { fat = it },
                placeholder = stringResource(R.string.quick_meal_fat),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cornerRadius = 16.dp,
                modifier = Modifier.weight(1f),
            )
        }

        ClayButton(
            text = stringResource(R.string.quick_meal_save),
            icon = Icons.Filled.Check,
            enabled = name.isNotBlank(),
            onClick = {
                onSave(
                    name,
                    kcal.toDoubleOrNull(),
                    protein.toDoubleOrNull(),
                    fat.toDoubleOrNull(),
                    carbs.toDoubleOrNull(),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showPasteDialog) {
        var pasteText by remember { mutableStateOf("") }
        var failed by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text(stringResource(R.string.edit_ai_paste_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        stringResource(if (failed) R.string.edit_ai_paste_failed else R.string.edit_ai_paste_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ClayTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it; failed = false },
                        placeholder = stringResource(R.string.edit_ai_paste_placeholder),
                        singleLine = false,
                        minLines = 4,
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    ClayButton(
                        text = stringResource(R.string.action_paste),
                        icon = Icons.Filled.ContentPaste,
                        onClick = { clipboard.getText()?.text?.let { pasteText = it; failed = false } },
                        container = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = pasteText.isNotBlank(),
                    onClick = {
                        val parsed = QuickMealJsonParser.parse(pasteText)
                        if (parsed != null) {
                            name = parsed.name
                            parsed.caloriesKcal?.let { kcal = it.toInt().toString() }
                            parsed.proteinG?.let { protein = it.toInt().toString() }
                            parsed.carbsG?.let { carbs = it.toInt().toString() }
                            parsed.fatG?.let { fat = it.toInt().toString() }
                            showPasteDialog = false
                        } else {
                            failed = true
                        }
                    },
                ) { Text(stringResource(R.string.edit_ai_paste_apply)) }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
