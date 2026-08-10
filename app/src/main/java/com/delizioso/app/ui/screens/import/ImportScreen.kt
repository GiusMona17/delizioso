package com.delizioso.app.ui.screens.import

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRecipeMiniCard
import com.delizioso.app.ui.components.ClaySectionHeader
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.components.ClayTopBar
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Primary
import com.delizioso.app.ui.theme.clayCard
import com.delizioso.app.ui.theme.clayBevel

@Composable
fun ImportScreen(
    onPreview: () -> Unit,
    onRecipeClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    sharedLink: String? = null,
    onSharedLinkHandled: () -> Unit = {},
    viewModel: ImportViewModel = viewModel(factory = ImportViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recent by viewModel.recentImports.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(state) {
        if (state is ImportUiState.Ready) onPreview()
    }

    // Shared straight from Instagram/TikTok/Chrome — fill the field and go.
    LaunchedEffect(sharedLink) {
        val link = sharedLink ?: return@LaunchedEffect
        url = link
        viewModel.importLink(link)
        onSharedLinkHandled()
    }

    val busy = state is ImportUiState.Fetching || state is ImportUiState.Structuring

    Column(Modifier.fillMaxSize()) {
        ClayTopBar(onProfile = onProfileClick)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Import a Recipe",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 20.dp, end = 20.dp),
            )
            Text(
                "Paste a link from Instagram, TikTok, YouTube or a blog to magically extract the recipe.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clayCard(container = MaterialTheme.colorScheme.surfaceContainer, cornerRadius = 32.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                ClayTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = "https://www.instagram.com/p/…",
                    leadingIcon = Icons.Filled.Link,
                    cornerRadius = 24.dp,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        Text(
                            "Paste",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .clip(PillShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clayBevel(PillShape)
                                .clickable { clipboard.getText()?.text?.let { url = it.trim() } }
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                        )
                    },
                )
                ClayButton(
                    text = "Extract Recipe",
                    icon = Icons.Filled.Download,
                    onClick = { viewModel.importLink(url) },
                    enabled = url.isNotBlank() && !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            ) {
                listOf("INSTAGRAM", "TIKTOK", "YOUTUBE", "WEB").forEach { ClayChip(it) }
            }

            when (val s = state) {
                is ImportUiState.Fetching -> BusyRow("Fetching the link…")
                is ImportUiState.Structuring -> BusyRow("Structuring with on-device AI…")
                is ImportUiState.Error -> ErrorCard(message = s.message, retryable = s.retryable, onRetry = viewModel::retry)
                is ImportUiState.AiConsentNeeded -> ConsentCard(onGrant = viewModel::grantConsent)
                else -> {}
            }

            if (recent.isNotEmpty()) {
                ClaySectionHeader(
                    title = "Recent Imports",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(recent, key = { it.recipe.id }) { details ->
                        ClayRecipeMiniCard(details = details, onClick = { onRecipeClick(details.recipe.id) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BusyRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 20.dp),
    ) {
        CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.padding(vertical = 12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorCard(message: String, retryable: Boolean, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clayCard(container = MaterialTheme.colorScheme.errorContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Couldn't import that link", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onErrorContainer)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        if (retryable) {
            ClayButton(text = "Retry", onClick = onRetry, container = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConsentCard(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clayCard(container = MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("On-device AI needs your consent", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(
            "Structuring this caption uses Gemini Nano — Google's on-device AI. Everything stays on your phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ClayButton(text = "Enable on-device AI", onClick = onGrant, modifier = Modifier.fillMaxWidth())
    }
}
