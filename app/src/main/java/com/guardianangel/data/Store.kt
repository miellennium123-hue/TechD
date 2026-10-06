package com.guardianangel.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** A JSON-backed value with synchronous reads, so the accessibility service never waits on disk. */
class Store<T>(
    context: Context,
    private val key: String,
    private val serializer: KSerializer<T>,
    default: T,
) {
    private val prefs = context.getSharedPreferences("guardian", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load(default))

    val flow: StateFlow<T> = _flow
    val value: T get() = _flow.value

    @Synchronized
    fun update(transform: (T) -> T): T {
        val next = transform(_flow.value)
        _flow.value = next
        prefs.edit().putString(key, json.encodeToString(serializer, next)).apply()
        return next
    }

    private fun load(default: T): T =
        prefs.getString(key, null)
            ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
            ?: default

    companion object {
        // Lenient on purpose: data saved by an older or newer version should still load
        // (unknown keys are skipped, unknown enum values fall back to the field's default).
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            coerceInputValues = true
        }
    }
}
