package com.example.data

import android.util.Log
import com.example.model.FuelStatus
import com.example.model.Language
import com.example.model.StationInfo
import com.example.model.VehicleCategory
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

private const val TAG = "HawassaAiAssistant"

/**
 * Hawassa Transit & Fuel Gemini AI Assistant
 * Powered by Firebase AI Logic (gemini-3.8-flash)
 */
class HawassaAiAssistant {

    // Mandated by Firebase AI Logic skill: initialize lazily to avoid eager evaluation
    private val generativeModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    /**
     * Build context summary string from current live stations
     */
    fun buildStationsContext(stations: List<StationInfo>): String {
        val sb = StringBuilder("=== LIVE HAWASSA STATIONS REAL-TIME STATUS ===\n")
        stations.forEach { station ->
            sb.append("- Station: ${station.nameEn} (${station.nameAm})\n")
            sb.append("  Active/Permitted Category: ${station.activeCalledCategory.name}\n")
            sb.append("  Fuel Availability: Benzene=${if (station.benzeneStatus == FuelStatus.AVAILABLE) "YES" else "NO"}, Diesel=${if (station.dieselStatus == FuelStatus.AVAILABLE) "YES" else "NO"}\n")
            sb.append("  Congestion: ${station.congestionLevel.name}\n")
            station.lanes.forEach { (cat, lane) ->
                val waiting = maxOf(0, lane.lastAssignedNumber - lane.currentlyServingNumber)
                sb.append("  * Lane [${cat.name}]: Serving #${lane.currentlyServingNumber}, Last #${lane.lastAssignedNumber}, Waiting=$waiting vehicles, Est Wait=${waiting * (lane.avgDurationSeconds / 60)} mins\n")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    /**
     * Generate real-time advice with Gemini 3.8 Flash
     */
    suspend fun askAssistant(
        stations: List<StationInfo>,
        userQuery: String,
        language: Language,
        selectedCategory: VehicleCategory? = null
    ): String = withContext(Dispatchers.IO) {
        val liveContext = buildStationsContext(stations)
        val langInstruction = if (language == Language.AMHARIC) {
            "Respond primarily in clear, natural Amharic (አማርኛ). Be helpful, polite, and practical for Ethiopian drivers in Hawassa."
        } else {
            "Respond in concise, professional English. Mention Amharic station names in parentheses where helpful."
        }

        val prompt = """
            You are Hawassa Transit AI (የሐዋሳ ትራንዚት እና ነዳጅ ረዳት), an intelligent smart dispatcher and queue advisor for drivers, Bajaj operators, minibus taxis, buses, and fuel station coordinators in Hawassa, Sidama Region, Ethiopia.

            $langInstruction

            Key terminals in Hawassa:
            - Piazza Terminal (ፒያሳ ተርሚናል)
            - Menharia Central Terminal (መናኸሪያ ዋና ተርሚናል)
            - Atote Terminal (አቶቴ ተርሚናል)
            - Arada Market Terminal (አራዳ ገበያ ተርሚናል)
            - Chefe Station (ጨፌ ጣቢያ)

            $liveContext

            User Question/Request:
            $userQuery

            Instructions:
            - Give immediate, direct answers based strictly on the live station data above.
            - If asking about fuel (Benzene/Diesel), identify the stations where the fuel is available right now.
            - If asking about shortest queue or fastest lane, calculate the vehicles waiting and recommend the best station.
            - Keep the answer concise (2-4 bullet points or short paragraphs) so drivers can read it quickly.
        """.trimIndent()

        try {
            // Realistic 30-60s timeout mandated by Firebase AI Logic skill
            val response = withTimeout(45_000L) {
                generativeModel.generateContent(prompt)
            }
            val text = response.text
            if (!text.isNullOrBlank()) {
                text.trim()
            } else {
                if (language == Language.AMHARIC) {
                    "ይቅርታ፣ በቂ መረጃ ማግኘት አልተቻለም። እባክዎ እንደገና ይሞክሩ።"
                } else {
                    "No response was returned by the AI assistant. Please try again."
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI generation failed", e)
            val errorMsg = e.localizedMessage ?: "Unknown error"
            if (language == Language.AMHARIC) {
                "የ AI ግንኙነት ስህተት አጋጥሟል ($errorMsg)። እባክዎ የአውታረ መረብ ግንኙነትዎን ያረጋግጡ።"
            } else {
                "AI Assistant unavailable ($errorMsg). Please check network or Firebase settings."
            }
        }
    }

    /**
     * Stream transit advice token-by-token
     */
    fun askAssistantStream(
        stations: List<StationInfo>,
        userQuery: String,
        language: Language
    ): Flow<String> = flow {
        val liveContext = buildStationsContext(stations)
        val langInstruction = if (language == Language.AMHARIC) {
            "Respond in natural Amharic (አማርኛ)."
        } else {
            "Respond in clear, concise English."
        }

        val prompt = """
            You are Hawassa Transit AI (የሐዋሳ ትራንዚት እና ነዳጅ ረዳት) in Hawassa, Ethiopia.
            $langInstruction
            $liveContext
            User: $userQuery
        """.trimIndent()

        try {
            val responseStream = generativeModel.generateContentStream(prompt)
            responseStream.collect { chunk ->
                chunk.text?.let { emit(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Stream failed", e)
            emit("\n[Error: ${e.localizedMessage}]")
        }
    }.flowOn(Dispatchers.IO)
}
