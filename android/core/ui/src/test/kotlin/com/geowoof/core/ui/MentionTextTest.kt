package com.geowoof.core.ui

import com.geowoof.core.model.Mention
import org.junit.Assert.assertEquals
import org.junit.Test

class MentionTextTest {
    private val bea = Mention("bea", "bea-id")
    private val ana = Mention("ana", "ana-id")

    private fun found(text: String, vararg mentions: Mention) =
        mentionRanges(text, mentions.toList()).map { (range, mention) -> text.substring(range) to mention.userId }

    @Test
    fun a_mention_is_found_without_caring_about_case() {
        assertEquals(listOf("@Bea" to "bea-id"), found("Hola @Bea", bea))
    }

    @Test
    fun they_come_in_the_order_of_the_text_and_each_time_it_appears() {
        assertEquals(
            listOf("@ana" to "ana-id", "@bea" to "bea-id", "@ana" to "ana-id"),
            found("Con @ana, @bea y otra vez @ana", bea, ana),
        )
    }

    @Test
    fun only_what_the_server_says_counts_as_a_mention() {
        assertEquals(emptyList<Pair<String, String>>(), found("Hola @carlos", bea))
    }

    @Test
    fun a_longer_username_or_an_email_is_someone_else() {
        assertEquals(emptyList<Pair<String, String>>(), found("Hola @beatriz y yo@bea.es", bea))
    }

    @Test
    fun the_full_stop_after_a_mention_is_not_part_of_it() {
        assertEquals(listOf("@bea" to "bea-id"), found("Gracias, @bea.", bea))
    }
}
