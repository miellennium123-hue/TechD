package com.guardianangel.data

import kotlinx.serialization.Serializable

object Sites {
    /** Stay time stepper range in Settings (round 19). */
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 30
    const val DEFAULT_MINUTES = 5

    /**
     * Cleans up a site the user typed. Adds https:// when there's no scheme.
     * Only web pages are allowed, so anything that isn't http or https returns null.
     */
    fun normalize(input: String): String? {
        val text = input.trim()
        if (text.isEmpty() || text.any { it.isWhitespace() }) return null
        val lower = text.lowercase()
        val url = when {
            lower.startsWith("http://") || lower.startsWith("https://") -> text
            // Any other scheme (mailto:, javascript:, file://). "example.com:8080" is a port, not a scheme.
            Regex("^[a-z][a-z0-9+.-]*:(?!\\d)").containsMatchIn(lower) -> return null
            else -> "https://$text"
        }
        return if (host(url).substringBefore(':').isBlank()) null else url
    }

    /** Just the site name, for the warning screen when Discreet is off. */
    fun host(url: String): String =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfter('@')
            .removePrefix("www.")
}

/**
 * She is opening one of your sites. Three phases:
 * waiting to be shown ([warnedAt] 0), the 10 second warning ([openedAt] 0), then open in the browser.
 * Time only counts while the browser is in front ([stayedMs] plus the current stretch from [onSiteSince]).
 */
@Serializable
data class SiteVisit(
    val id: Long,
    val url: String,
    /** The browser package she opens it in. Leaving it is a failure. */
    val browser: String,
    val createdAt: Long,
    /** A visit waiting for the phone to be unlocked is dropped after this. */
    val showBy: Long,
    val stayMs: Long,
    /** You asked for it ("Ask her"), so quiet hours don't stop it. */
    val asked: Boolean = false,
    val warnedAt: Long = 0,
    val openedAt: Long = 0,
    val stayedMs: Long = 0,
    /** When the current stretch on the site began. 0 while paused (her app, a call, screen off). */
    val onSiteSince: Long = 0,
) {
    val pending: Boolean get() = warnedAt == 0L
    val warning: Boolean get() = warnedAt > 0 && openedAt == 0L
    val open: Boolean get() = openedAt > 0
}
