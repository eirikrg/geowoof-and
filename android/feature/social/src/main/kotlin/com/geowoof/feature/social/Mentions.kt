package com.geowoof.feature.social

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import com.geowoof.core.data.repository.PeopleRepository
import com.geowoof.core.model.Outcome
import com.geowoof.core.model.UserSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** The `@something` that is being written, with where it starts and ends in the text. */
internal data class MentionToken(val start: Int, val end: Int, val query: String)

/** The mention that ends at the cursor, if there is one. An email is not a mention, so it cannot follow a letter. */
internal fun mentionTokenAt(text: String, cursor: Int): MentionToken? {
    if (cursor > text.length) return null
    var start = cursor
    while (start > 0 && text[start - 1].isUsernameChar()) start--
    val isMention = start > 0 && text[start - 1] == '@' && (start == 1 || !text[start - 2].isTextBeforeAMention())
    return if (isMention) MentionToken(start - 1, cursor, text.substring(start, cursor)) else null
}

private fun Char.isUsernameChar() = isLetterOrDigit() || this == '_' || this == '.'

private fun Char.isTextBeforeAMention() = isUsernameChar() || this == '@'

/**
 * Offers people while someone writes an `@` in a field, and writes the one they choose. The server decides later
 * which mentions count, so choosing someone here does not promise anything.
 */
internal class MentionSuggester(
    private val field: TextFieldState,
    private val people: PeopleRepository,
    scope: CoroutineScope,
) {
    private val found = MutableStateFlow<List<UserSummary>>(emptyList())

    val suggestions: StateFlow<List<UserSummary>> = found.asStateFlow()

    init {
        scope.launch {
            snapshotFlow { mentionTokenAt(field.text.toString(), field.selection.end)?.query }
                .distinctUntilChanged()
                .collectLatest { query ->
                    if (query == null || query.length < MIN_QUERY_LENGTH) {
                        found.value = emptyList()
                    } else {
                        delay(DEBOUNCE_MILLIS)
                        found.value = (people.suggestUsers(query) as? Outcome.Success)?.value.orEmpty()
                    }
                }
        }
    }

    fun onChoose(user: UserSummary) {
        val token = mentionTokenAt(field.text.toString(), field.selection.end) ?: return
        val replacement = "@${user.username} "
        field.edit {
            replace(token.start, token.end, replacement)
            selection = TextRange(token.start + replacement.length)
        }
        found.value = emptyList()
    }

    private companion object {
        /** With less, the server does not search. */
        const val MIN_QUERY_LENGTH = 2
        const val DEBOUNCE_MILLIS = 250L
    }
}
