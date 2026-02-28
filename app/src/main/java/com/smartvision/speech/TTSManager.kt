package com.smartvision.speech

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSManager(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context, this)
    private var ready = false
    private var lastSpeakTs = 0L
    private val throttleMs = 1200L

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.US
            tts.setSpeechRate(1.0f)
        }
    }

    fun speak(text: String) {
        if (!ready) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastSpeakTs < throttleMs) return
        lastSpeakTs = now
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, "smartvision-$now")
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
