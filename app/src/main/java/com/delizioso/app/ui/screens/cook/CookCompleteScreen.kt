package com.delizioso.app.ui.screens.cook

import android.content.Intent
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.delizioso.app.ui.components.ClayButton
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayOutlinedButton
import com.delizioso.app.ui.components.RecipeImage
import com.delizioso.app.ui.theme.PillShape
import com.delizioso.app.ui.theme.Primary
import com.delizioso.app.ui.theme.clayOuter

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
                .clayOuter(shape = PillShape, color = Primary.copy(alpha = 0.25f), elevation = 32.dp)
                .clip(PillShape),
        ) {
            RecipeImage(recipe?.imageUri, placeholderIconSize = 64.dp, modifier = Modifier.fillMaxSize())
        }
        Text(
            "Culinary Masterpiece Complete!",
            style = MaterialTheme.typography.displaySmall,
            color = Primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            "You've successfully prepared ${recipe?.title ?: "this dish"}",
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
                ClayChip("Time: ${minutes}m", icon = Icons.Filled.Schedule)
                details?.steps?.size?.takeIf { it > 0 }?.let {
                    ClayChip("$it steps")
                }
            }
        }
        ClayButton(
            text = "Back to Library",
            icon = Icons.AutoMirrored.Filled.MenuBook,
            onClick = onBackToLibrary,
            modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
        )
        ClayOutlinedButton(
            text = "Share My Creation",
            icon = Icons.Filled.Share,
            onClick = {
                val text = buildString {
                    append("I just cooked ")
                    append(recipe?.title ?: "something delicious")
                    append(" with Delizioso!")
                }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(share, "Share my creation"))
            },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}
