package com.delizioso.app

import android.content.Intent

/**
 * Pulls a recipe link out of an incoming share.
 *
 * Apps rarely share a bare URL: Instagram sends "Look at this…\nhttps://…",
 * TikTok appends its own blurb, and some senders put the link in the subject.
 * So take the first http(s) token from whichever extra carries one.
 */
object SharedLink {

    private val URL = Regex("""https?://\S+""")

    fun fromIntent(intent: Intent?): String? {
        if (intent == null) return null
        val candidates = when (intent.action) {
            Intent.ACTION_SEND -> listOf(
                intent.getStringExtra(Intent.EXTRA_TEXT),
                intent.getStringExtra(Intent.EXTRA_SUBJECT),
            )
            Intent.ACTION_VIEW -> listOf(intent.dataString)
            else -> return null
        }
        return candidates.firstNotNullOfOrNull { text -> text?.let(::extract) }
    }

    /** First http(s) URL in [text], with trailing punctuation trimmed. */
    fun extract(text: String): String? =
        URL.find(text)?.value?.trimEnd('.', ',', ')', ']', '"', '\'', '>')
}
