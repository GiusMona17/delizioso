package com.delizioso.app.ui.screens.cook

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.data.ImageStore
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayOutlinedButton
import com.delizioso.app.ui.components.RecipeImage
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.clayOuter
import androidx.compose.ui.res.stringResource
import com.delizioso.app.R

/** "Culinary Masterpiece Complete!" — the celebration screen after cook mode. */
@Composable
fun CookCompleteScreen(
    recipeId: Long,
    onBackToLibrary: () -> Unit,
    viewModel: CookViewModel = viewModel(
        key = "cook-complete-$recipeId",
        factory = CookViewModel.factory(recipeId),
    ),
) {
    val details by viewModel.details.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val recipe = details?.recipe

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clayOuter(shape = PillShape, dark = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), elevation = 32.dp)
                .clip(PillShape),
        ) {
            RecipeImage(recipe?.imageUri, placeholderIconSize = 64.dp, modifier = Modifier.fillMaxSize())
        }
        Text(
            stringResource(R.string.cook_complete_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            stringResource(
                R.string.cook_complete_body,
                recipe?.title ?: stringResource(R.string.cook_complete_this_dish),
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
        val minutes = (recipe?.prepTimeMinutes ?: 0) + (recipe?.cookTimeMinutes ?: 0)
        if (minutes > 0) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                ClayChip(stringResource(R.string.cook_complete_time, minutes), icon = Icons.Filled.Schedule)
                details?.steps?.size?.takeIf { it > 0 }?.let {
                    ClayChip(stringResource(R.string.cook_complete_steps, it))
                }
            }
        }
        // The dish is on the counter right now — this is the only moment the app
        // can get a real photo of it. Imported reels almost never yield a usable
        // thumbnail, so the library is otherwise a wall of placeholders.
        var pendingPhoto by remember { mutableStateOf<String?>(null) }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val path = pendingPhoto
            pendingPhoto = null
            if (saved && path != null) {
                viewModel.setPhoto(context, path)
            } else {
                // Cancelled: don't leave an empty file behind in our own storage.
                path?.let { ImageStore.deleteIfOwned(context, it) }
            }
        }
        ClayButton(
            text = stringResource(
                if (recipe?.imageUri.isNullOrBlank()) R.string.cook_photo_add else R.string.cook_photo_replace
            ),
            icon = Icons.Filled.PhotoCamera,
            onClick = {
                val (path, uri) = ImageStore.newCameraTarget(context)
                pendingPhoto = path
                runCatching { camera.launch(uri) }.onFailure {
                    pendingPhoto = null
                    ImageStore.deleteIfOwned(context, path)
                }
            },
            container = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        )
        ClayButton(
            text = stringResource(R.string.cook_complete_back),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = onBackToLibrary,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
        val shareText = buildString {
            append(stringResource(R.string.cook_share_prefix))
            append(recipe?.title ?: stringResource(R.string.cook_complete_fallback))
            append(stringResource(R.string.cook_share_suffix))
        }
        val shareTitle = stringResource(R.string.cook_complete_share_title)
        ClayOutlinedButton(
            text = stringResource(R.string.cook_complete_share),
            icon = Icons.Filled.Share,
            onClick = {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(share, shareTitle))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}
