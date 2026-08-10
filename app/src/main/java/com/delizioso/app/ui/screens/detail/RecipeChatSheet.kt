package com.delizioso.app.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.delizioso.app.data.ai.ChatMessage
import com.delizioso.app.ui.components.ClayChip
import com.delizioso.app.ui.components.ClayRoundButton
import com.delizioso.app.ui.components.ClayTextField
import com.delizioso.app.ui.theme.Primary
import com.delizioso.app.ui.theme.clayCard

/** Starter questions, so the first turn doesn't need typing. */
private val SUGGESTIONS = listOf(
    "What can I use instead?",
    "Make it vegetarian",
    "Can I prepare it ahead?",
    "How do I store leftovers?",
)

/** "Ask about this recipe" — a conversation grounded in the open recipe. */
@Composable
fun RecipeChatSheet(
    recipeTitle: String,
    state: ChatState,
    onAsk: (String) -> Unit,
    onDismissError: () -> Unit,
) {
    var question by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Follow the answer as it streams in.
    LaunchedEffect(state.messages.size, state.streaming) {
        val last = state.messages.size + if (state.streaming != null) 1 else 0
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column {
            Text(
                "Ask about this recipe",
                style = MaterialTheme.typography.headlineLarge,
                color = Primary,
            )
            Text(
                recipeTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (state.messages.isEmpty() && state.streaming == null) {
            Text(
                "Answers come from Gemini Nano running on your phone. It knows this recipe — " +
                    "ask about swaps, technique, timing or storage.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages) { message -> ChatBubble(message.role, message.text) }
                state.streaming?.let { partial ->
                    item {
                        if (partial.isBlank()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(color = Primary, strokeWidth = 3.dp, modifier = Modifier.size(18.dp))
                                Text(
                                    if (state.preparingModel) {
                                        "Setting up Gemini Nano — the first run downloads the model, which can take a few minutes."
                                    } else {
                                        "Thinking…"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            ChatBubble(ChatMessage.Role.ASSISTANT, partial)
                        }
                    }
                }
            }
        }

        state.error?.let { error ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayCard(container = MaterialTheme.colorScheme.errorContainer, cornerRadius = 20.dp)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Dismiss",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(start = 12.dp).clickable(onClick = onDismissError),
                )
            }
        }

        if (state.messages.isEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(SUGGESTIONS) { suggestion ->
                    Box(modifier = Modifier.clickable { onAsk(suggestion) }) {
                        ClayChip(
                            suggestion,
                            container = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ClayTextField(
                value = question,
                onValueChange = { question = it },
                placeholder = "Ask anything…",
                cornerRadius = 24.dp,
                modifier = Modifier.weight(1f),
            )
            ClayRoundButton(
                icon = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                onClick = {
                    onAsk(question)
                    question = ""
                },
                container = if (state.busy) {
                    MaterialTheme.colorScheme.surfaceContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
            )
        }
    }
}

@Composable
private fun ChatBubble(role: ChatMessage.Role, text: String) {
    val fromUser = role == ChatMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (fromUser) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clayCard(
                    container = if (fromUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    cornerRadius = 20.dp,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}
