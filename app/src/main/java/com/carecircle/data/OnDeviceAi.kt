package com.carecircle.data

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/**
 * Gemini Nano through ML Kit GenAI (runs inside Google AICore on the phone). Nothing here uses the network for the
 * text you give it. Only a supported, locked-bootloader phone with AICore can answer; everything else reports UNAVAILABLE.
 */
object OnDeviceAi {
    enum class Availability { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, ERROR }

    private val model by lazy { Generation.getClient() }

    suspend fun availability(): Availability = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> Availability.AVAILABLE
            FeatureStatus.DOWNLOADABLE -> Availability.DOWNLOADABLE
            FeatureStatus.DOWNLOADING -> Availability.DOWNLOADING
            else -> Availability.UNAVAILABLE
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Availability.ERROR
    }

    /** Starts the model download; returns true when it finished. */
    suspend fun download(): Boolean {
        var ok = false
        try {
            model.download().collect { s -> if (s is DownloadStatus.DownloadCompleted) ok = true }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        return ok
    }

    /** Runs one prompt on the phone. Returns null when the model is not usable. */
    suspend fun generate(prompt: String): String? = try {
        model.generateContent(prompt).candidates.firstOrNull()?.text
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}
