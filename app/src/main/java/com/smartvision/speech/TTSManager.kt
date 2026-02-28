package com.smartvision.speech

import android.content.Context
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TTSManager(context: Context) : TextToSpeech.OnInitListener {
    private val speechExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val tts = TextToSpeech(context, this)

    @Volatile
    private var ready = false

    @Volatile
    private var failed = false

    private var lastSpokenAt = 0L
    private val throttleMs = 1000L

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        failed = !ready
        if (ready) {
            tts.language = Locale.US
            tts.setSpeechRate(1.0f)
            tts.setPitch(1.0f)
        }
    }

    fun speakAsync(text: String) {
        speechExecutor.execute {
            if (!ready) return@execute
            val now = SystemClock.elapsedRealtime()
            if (now - lastSpokenAt < throttleMs) return@execute
            lastSpokenAt = now
            val result = tts.speak(text, TextToSpeech.QUEUE_ADD, null, "sv-$now")
            if (result == TextToSpeech.ERROR) {
                failed = true
                Log.w(TAG, "TTS speak failed, relying on TalkBack UI announcements")
            }
        }
    }

    fun isOperational(): Boolean = ready && !failed

    fun shutdown() {
        speechExecutor.shutdownNow()
        tts.stop()
        tts.shutdown()
    }

    companion object {
        private const val TAG = "TTSManager"
    }
}
