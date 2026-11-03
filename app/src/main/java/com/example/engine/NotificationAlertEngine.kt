package com.example.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.Language
import java.util.Locale

class NotificationAlertEngine(private val context: Context) : TextToSpeech.OnInitListener {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        createNotificationChannel()
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.w("NotificationEngine", "Failed to initialize TTS engine: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsReady = true
            // Check Amharic support or fallback to English/default
            val amLocale = Locale.forLanguageTag("am-ET")
            val result = tts?.setLanguage(amLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.ENGLISH
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hawassa Transit Queue Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority queue warnings and 10-minute turn arrival alerts"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun triggerTenMinuteAlert(
        stationName: String,
        ticketNumber: Int,
        vehiclesAhead: Int,
        estimatedMinutes: Int,
        language: Language
    ) {
        // 1. Play high-pitch buzzer beep (reliable across all devices in noisy environments)
        playBuzzerTone()

        // 2. Play audible voice speech cue
        val spokenMessage = if (language == Language.AMHARIC) {
            "ተራዎ ሊደርስ 10 ደቂቃ ቀረዎት፤ ወደ ማድያ ይምጡ!"
        } else {
            "Your turn will arrive in 10 minutes; please come to the station!"
        }
        speakAlert(spokenMessage, language)

        // 3. Trigger phone hardware vibration
        vibratePhone()

        // 4. Post high-priority notification
        val title = if (language == Language.AMHARIC) {
            "⚠️ ተራዎ ሊደርስ 10 ደቂቃ ቀረዎት! (#$ticketNumber)"
        } else {
            "⚠️ 10 Minutes Left for Your Turn! (#$ticketNumber)"
        }

        val body = if (language == Language.AMHARIC) {
            "$stationName: ተራዎ ሊደርስ 10 ደቂቃ ቀረዎት፤ እባክዎ አሁን ወደ ማድያ ይምጡ!"
        } else {
            "$stationName: Your turn will arrive in 10 minutes; please come to the station!"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 600, 250, 600, 250, 900))
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun playBuzzerTone() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_NETWORK_LITE, 1200)
        } catch (e: Exception) {
            Log.w("NotificationEngine", "ToneGenerator error: ${e.message}")
        }
    }

    private fun vibratePhone() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 500, 200, 500, 200, 800),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(longArrayOf(0, 500, 200, 500, 200, 800), -1)
                }
            }
        } catch (e: Exception) {
            Log.w("NotificationEngine", "Vibration failed: ${e.message}")
        }
    }

    private fun speakAlert(text: String, language: Language) {
        if (isTtsReady && tts != null) {
            try {
                if (language == Language.AMHARIC) {
                    val amResult = tts?.setLanguage(Locale.forLanguageTag("am-ET"))
                    if (amResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.language = Locale.ENGLISH
                    }
                } else {
                    tts?.language = Locale.ENGLISH
                }
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ALERT_UTTERANCE")
            } catch (e: Exception) {
                Log.w("NotificationEngine", "TTS playback exception: ${e.message}")
            }
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            // Ignore
        }
    }

    companion object {
        const val CHANNEL_ID = "hawassa_queue_channel"
        const val NOTIFICATION_ID = 1001
    }
}
