package org.futo.voiceinput.shared.whisper

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
    val id: String,
    val architecture: ModelArchitecture? = null
)

@Serializable
private data class ModelArchitecture(
    val output_modalities: List<String> = emptyList()
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

    private fun endpointUrl(serverUrl: String, endpoint: String): HttpUrl {
        val url = serverUrl.trim().toHttpUrl()
        val basePath = url.encodedPath.trimEnd('/')
            .removeSuffix("/audio/transcriptions")
            .removeSuffix("/models")
            .removeSuffix("/v1")
        return url.newBuilder().encodedPath("$basePath/v1/$endpoint").build()
    }

    internal fun modelDiscoveryUrl(serverUrl: String): HttpUrl {
        val url = endpointUrl(serverUrl, "models")
        return if (url.host in setOf("openrouter.ai", "us.openrouter.ai", "eu.openrouter.ai")) {
            url.newBuilder().setQueryParameter("output_modalities", "transcription").build()
        } else {
            url
        }
    }

    private fun Request.Builder.withApiKey(apiKey: String): Request.Builder = apply {
        val key = apiKey.trim()
        if (key.isNotEmpty()) {
            require(key.all { it in '!'..'~' }) { "API key must not contain whitespace or non-ASCII characters" }
            header("Authorization", "Bearer $key")
        }
    }

    private fun serverError(code: Int, body: String?, apiKey: String): Exception {
        val key = apiKey.trim()
        val detail = body.orEmpty().let {
            if (key.isEmpty()) it else it.replace(key, "[redacted]")
        }
        return Exception("Server error: $code $detail")
    }

    fun transcribe(
        wavBytes: ByteArray,
        serverUrl: String,
        modelId: String,
        language: String?,
        apiKey: String = ""
    ): String {
        val fileBody = wavBytes.toRequestBody("audio/wav".toMediaTypeOrNull())

        val requestBodyBuilder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", "audio.wav", fileBody)
            .addFormDataPart("model", modelId)

        if (!language.isNullOrEmpty() && language != "auto") {
            requestBodyBuilder.addFormDataPart("language", language)
        }

        val request = Request.Builder()
            .url(endpointUrl(serverUrl, "audio/transcriptions"))
            .withApiKey(apiKey)
            .post(requestBodyBuilder.build())
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw serverError(response.code, response.body?.string(), apiKey)
            }
            val jsonString = response.body?.string() ?: throw Exception("Empty response body")
            val parsed = json.decodeFromString<TranscriptionResponse>(jsonString)

            // Collapse all whitespace sequences into a single space
            return parsed.text.replace(Regex("\\s+"), " ").trim()
        }
    }

    fun fetchModels(serverUrl: String, apiKey: String = ""): List<String> {
        val request = Request.Builder()
            .url(modelDiscoveryUrl(serverUrl))
            .withApiKey(apiKey)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw serverError(response.code, response.body?.string(), apiKey)
            }
            val jsonString = response.body?.string() ?: throw Exception("Empty response body")
            val parsed = json.decodeFromString<ModelsResponse>(jsonString)
            return parsed.data.filter {
                request.url.queryParameter("output_modalities") == "transcription" ||
                    it.architecture?.output_modalities?.contains("transcription") == true ||
                    it.id.contains("whisper", ignoreCase = true) ||
                    it.id.contains("moonshine", ignoreCase = true)
            }.map { it.id }
        }
    }
}
