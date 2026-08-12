package com.delizioso.app.ui.screens.create

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.CardImageRadius
import com.delizioso.app.ui.theme.clayCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

@Composable
fun CreateScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: CreateViewModel = viewModel(factory = CreateViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val form = rememberRecipeFormState()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.onPhotoPicked(context, it) }
    }

    // OCR + AI filled the form — apply the draft once.
    LaunchedEffect(state.draft) {
        state.draft?.let(form::applyDraft)
    }

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(
            onMenu = onBack,
            menuIcon = Icons.AutoMirrored.Filled.ArrowBack,
            menuDescription = stringResource(R.string.topbar_back),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.create_title), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        stringResource(R.string.create_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                PhotoArea(
                    photoPath = state.photoPath,
                    busy = state.busy,
                    onClick = { photoPicker.launch("image/*") },
                )
            }
            item {
                ClayButton(
                    text = stringResource(R.string.create_scan_btn),
                    icon = Icons.Filled.DocumentScanner,
                    onClick = viewModel::scanCookbook,
                    enabled = state.photoPath != null && state.busy == null,
                    container = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            state.error?.let { error ->
                item {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            item { RecipeIdentityFields(form) }
            item { IngredientsCard(form) }
            item { InstructionsCard(form) }
            item { RecipeMetaFields(form) }
            item { Spacer(Modifier.height(8.dp)) }
        }
        ClayButton(
            text = stringResource(R.string.action_save),
            icon = Icons.Filled.Save,
            enabled = form.isValid,
            onClick = {
                scope.launch {
                    val id = viewModel.save(form.toStructuredRecipe(), state.photoPath, form.categoryList())
                    onSaved(id)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PhotoArea(
    photoPath: String?,
    busy: CreateBusy?,
    onClick: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = null, photoPath) {
        value = withContext(Dispatchers.IO) { photoPath?.let(::decodeBitmap) }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 10f)
            .clayCard(container = MaterialTheme.colorScheme.surfaceContainerLow, cornerRadius = 28.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.create_photo_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(CardImageRadius)),
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(44.dp),
                )
                Text(stringResource(R.string.create_photo_label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (busy != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (busy == CreateBusy.OCR) stringResource(R.string.create_ocr_reading) else stringResource(R.string.create_ocr_structuring),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

private fun decodeBitmap(path: String): Bitmap? = runCatching {
    android.graphics.BitmapFactory.decodeFile(File(path).absolutePath)
}.getOrNull()
