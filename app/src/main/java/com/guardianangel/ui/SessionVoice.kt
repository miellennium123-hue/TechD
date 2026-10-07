package com.guardianangel.ui

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech

/**
 * Her voice in sessions (round 79): the phone's own text to speech reads her commands and counts CBT
 * out loud, so you can watch yourself instead of reading. [whisper]: late at night she speaks softer
 * and slower. On the phone only; nothing is recorded. Quietly does nothing if the phone has no voice.
 */
class SessionVoice(context: Context, private val whisper: Boolean) {
    @Volatile private var ready = false
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.apply {
        setPitch(if (whisper) 0.95f else 1.1f)
        setSpeechRate(if (whisper) 0.8f else 0.95f)
    }
    private val params = Bundle().apply {
        if (whisper) putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.35f)
    }

    /** Says [text], cutting off whatever she was saying. */
    fun say(text: String) {
        if (ready && text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "her")
    }

    /** Says [text] after whatever she's saying now. */
    fun then(text: String) {
        if (ready && text.isNotBlank()) tts.speak(text, TextToSpeech.QUEUE_ADD, params, "her")
    }

    fun shutdown() {
        runCatching { tts.shutdown() }
    }
}
