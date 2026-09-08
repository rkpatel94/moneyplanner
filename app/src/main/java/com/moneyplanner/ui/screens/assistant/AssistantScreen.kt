package com.moneyplanner.ui.screens.assistant

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyplanner.domain.nlp.AnswerTone
import com.moneyplanner.domain.nlp.AssistantAction
import com.moneyplanner.domain.nlp.MoneyAssistant
import com.moneyplanner.ui.components.SectionCard
import com.moneyplanner.ui.theme.Elevation
import com.moneyplanner.ui.theme.MoneyTheme

/**
 * Ask about your money.
 *
 * The screen is deliberately plain about what it is: an on-device assistant that reads
 * the user's own records. It never claims to be more than that, and when it does not
 * understand a question it says so and lists what it can actually answer, rather than
 * producing a confident-sounding non-answer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    onNavigate: (AssistantAction) -> Unit,
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val voiceAvailable = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .resolveActivity(context.packageManager) != null
    }
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { viewModel.ask(it) }
    }

    LaunchedEffect(state.exchanges.size) {
        if (state.exchanges.isNotEmpty()) {
            listState.animateScrollToItem(state.exchanges.size - 1)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ask about your money") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = Elevation.level2
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask a question") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            imeAction = ImeAction.Send
                        ),
                        trailingIcon = {
                            if (voiceAvailable) {
                                IconButton(
                                    onClick = {
                                        voiceLauncher.launch(
                                            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                                                putExtra(
                                                    RecognizerIntent.EXTRA_PROMPT,
                                                    "Ask about your money"
                                                )
                                            }
                                        )
                                    }
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Ask by voice")
                                }
                            }
                        }
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { viewModel.askCurrentInput() },
                        enabled = input.isNotBlank()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Ask",
                            tint = if (input.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.exchanges.isEmpty()) {
                item {
                    SectionCard {
                        Text(
                            "Everything here is worked out on your phone from what you " +
                                "have recorded. Nothing is sent anywhere, and no figure " +
                                "is invented: each answer comes from the same " +
                                "calculations the rest of the app uses.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(state.exchanges) { exchange ->
                Column {
                    // The question, aligned right the way a sent message reads.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                exchange.question,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    AnswerCard(exchange, onNavigate)
                }
            }

            item {
                Text(
                    if (state.exchanges.isEmpty()) "Try asking" else "Ask something else",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoneyAssistant.SUGGESTIONS.take(4).forEach { suggestion ->
                        AssistChip(
                            onClick = { viewModel.ask(suggestion) },
                            label = { Text(suggestion) },
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MoneyAssistant.SUGGESTIONS.drop(4).forEach { suggestion ->
                        AssistChip(
                            onClick = { viewModel.ask(suggestion) },
                            label = { Text(suggestion) },
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnswerCard(exchange: Exchange, onNavigate: (AssistantAction) -> Unit) {
    val colors = MoneyTheme.colors
    val answer = exchange.answer

    val headlineColor = when (answer.tone) {
        AnswerTone.POSITIVE -> colors.positive
        AnswerTone.CAUTION -> colors.warning
        AnswerTone.NEGATIVE -> colors.negative
        AnswerTone.NEUTRAL -> MaterialTheme.colorScheme.primary
    }

    SectionCard(
        containerColor = if (answer.isUnderstood) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Text(
            answer.headline,
            style = if (answer.isUnderstood) {
                MaterialTheme.typography.displaySmall
            } else {
                MaterialTheme.typography.titleLarge
            },
            color = if (answer.isUnderstood) headlineColor else MaterialTheme.colorScheme.onSurface
        )
        if (answer.detail.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                answer.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (answer.action != AssistantAction.NONE) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onNavigate(answer.action) },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(actionLabel(answer.action))
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.height(16.dp)
                )
            }
        }
    }
}

private fun actionLabel(action: AssistantAction): String = when (action) {
    AssistantAction.OPEN_DASHBOARD -> "Open dashboard"
    AssistantAction.OPEN_FORECAST -> "See the forecast"
    AssistantAction.OPEN_PEOPLE -> "Open people"
    AssistantAction.OPEN_PLANS -> "Open plans"
    AssistantAction.OPEN_CALENDAR -> "Open calendar"
    AssistantAction.OPEN_GOALS -> "Open goals"
    AssistantAction.OPEN_EMERGENCY_FUND -> "Open emergency fund"
    AssistantAction.OPEN_REPORTS -> "See reports"
    AssistantAction.OPEN_INCOME -> "Open income"
    AssistantAction.OPEN_AFFORDABILITY -> "Open the affordability check"
    AssistantAction.NONE -> ""
}
