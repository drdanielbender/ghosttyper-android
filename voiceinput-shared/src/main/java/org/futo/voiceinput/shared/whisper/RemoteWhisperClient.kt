package org.futo.voiceinput.shared.whisper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@Serializable
private data class TranscriptionResponse(
    val text: String
)

@Serializable
private data class ModelItem(
    val id: String
)

@Serializable
private data class ModelsResponse(
    val data: List<ModelItem>
)

object RemoteWhisperClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun transcribe(
        wavBytes: ByteArray,
        serverUrl: String,
        modelId: String,
        language: String?
    ): String {
        val fileBody = wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", fileBody)
            .addFormDataPart("model", modelId)

        if (!language.isNullOrEmpty() && language != "auto") {
            if (!(serverUrl.contains("api.openai.com") && language == "auto")) {
                requestBodyBuilder.addFormDataPart("language", language)
            }
        }

        // Normalize URL to prevent duplicate endpoint paths
        val pathSuffix = "/v1/audio/transcriptions"
        val targetUrl = serverUrl.trim()
        val fullUrl = if (targetUrl.contains(pathSuffix)) {
            targetUrl
        } else {
            "${targetUrl.trimEnd('/')}$pathSuffix"
        }

        val request = Request.Builder()
            .url(fullUrl)
            .post(requestBodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Server error: ${response.code} ${response.body?.string()}")
            }
            val jsonString = response.body?.string() ?: throw Exception("Empty response body")
            val parsed = json.decodeFromString<TranscriptionResponse>(jsonString)

            // Collapse all whitespace sequences into a single space
            return parsed.text.replace(Regex("\\s+"), " ").trim()
        }
    }

    fun fetchModels(fullUrl: String): List<String> {
        val request = Request.Builder()
            .url(fullUrl)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${response.body?.string() ?: ""}")
            }
            val jsonString = response.body?.string() ?: throw Exception("Empty response body")
            val parsed = json.decodeFromString<ModelsResponse>(jsonString)
            return parsed.data.map { it.id }.filter {
                it.contains("whisper", ignoreCase = true) || it.contains("moonshine", ignoreCase = true)
            }
        }
    }
}
