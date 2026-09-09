package com.moneyplanner.ui.screens.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moneyplanner.data.repo.SnapshotRepository
import com.moneyplanner.domain.nlp.AssistantAnswer
import com.moneyplanner.domain.nlp.MoneyAssistant
import com.moneyplanner.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the conversation.
 *
 * Each question is answered against a snapshot taken at the moment it is asked, so an
 * answer stays a record of what was true when it was given rather than quietly rewriting
 * itself as the data changes underneath it.
 *
 * Nothing is persisted: the conversation is deliberately transient, because a stored log
 * of financial questions is a privacy liability with no real benefit.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val snapshotRepository: SnapshotRepository,
    @DefaultDispatcher private val computation: CoroutineDispatcher
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantState())
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    /**
     * Starting questions chosen from what the user actually has recorded.
     *
     * A fixed list offers "who owes me money" to somebody with no people recorded, and
     * the honest answer to that is "nobody" — which teaches them the assistant is not
     * worth asking.
     */
    val suggestions: StateFlow<List<String>> = snapshotRepository.snapshot
        .map { MoneyAssistant.suggestionsFor(it) }
        .flowOn(computation)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MoneyAssistant.SUGGESTIONS.take(4)
        )

    fun updateInput(value: String) {
        _input.value = value
    }

    fun askCurrentInput() {
        val question = _input.value.trim()
        if (question.isEmpty()) return
        ask(question)
    }

    fun ask(question: String) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch {
            // Answering runs the same calculators the dashboard does, so it is kept off
            // the thread drawing the conversation.
            val answer = withContext(computation) {
                MoneyAssistant.answer(trimmed, snapshotRepository.snapshot.first())
            }
            _state.update { current ->
                current.copy(exchanges = current.exchanges + Exchange(trimmed, answer))
            }
            _input.value = ""
        }
    }

    fun clear() {
        _state.value = AssistantState()
    }
}

data class Exchange(
    val question: String,
    val answer: AssistantAnswer
)

data class AssistantState(
    val exchanges: List<Exchange> = emptyList()
)
