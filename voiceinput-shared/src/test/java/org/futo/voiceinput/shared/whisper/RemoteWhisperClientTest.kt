package org.futo.voiceinput.shared.whisper

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RemoteWhisperClientTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() {
        server.shutdown()
    }

    @Test fun transcriptionAcceptsRootVersionAndEndpointUrls() {
        for (path in listOf("/", "/v1", "/v1/", "/v1/models", "/v1/audio/transcriptions/")) {
            server.enqueue(MockResponse().setBody("""{"text":"  Hello\n world!  ","extra":true}"""))
            val text = RemoteWhisperClient.transcribe(
                WavEncoder.floatArrayToWavBytes(floatArrayOf(0f, 0.5f)),
                " ${server.url(path)} ", "Whisper-Base", "de"
            )
            assertEquals("Hello world!", text)
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/v1/audio/transcriptions", request.path)
            assertEquals("POST", request.method)
            assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data;"))
            val body = request.body.readUtf8()
            assertTrue(body.contains("name=\"file\"; filename=\"audio.wav\""))
            assertTrue(body.contains("Content-Type: audio/wav"))
            assertTrue(body.contains("RIFF"))
            assertTrue(body.contains("name=\"model\""))
            assertTrue(body.contains("\r\n\r\nWhisper-Base\r\n"))
            assertTrue(body.contains("name=\"language\""))
            assertTrue(body.contains("\r\n\r\nde\r\n"))
        }
    }

    @Test fun modelDiscoveryUsesTheSameUrlNormalization() {
        for (path in listOf("/", "/v1", "/v1/", "/v1/models/", "/v1/audio/transcriptions")) {
            server.enqueue(MockResponse().setBody("""{"data":[{"id":"Whisper-Base"},{"id":"moonshine"},{"id":"chat-model"}]}"""))
            assertEquals(listOf("Whisper-Base", "moonshine"), RemoteWhisperClient.fetchModels(server.url(path).toString()))
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("GET", request.method)
            assertEquals("/v1/models", request.path)
        }
    }

    @Test fun proxyPrefixAndQueryArePreserved() {
        server.enqueue(MockResponse().setBody("""{"text":"Hello"}"""))
        RemoteWhisperClient.transcribe(byteArrayOf(), server.url("/speech/v1/?key=value").toString(), "Whisper-Base", null)
        assertEquals("/speech/v1/audio/transcriptions?key=value", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
    }

    @Test fun automaticLanguageIsOmitted() {
        for (language in listOf(null, "", "auto")) {
            server.enqueue(MockResponse().setBody("""{"text":"Hello"}"""))
            RemoteWhisperClient.transcribe(byteArrayOf(), server.url("/").toString(), "Whisper-Base", language)
            assertFalse(server.takeRequest(1, TimeUnit.SECONDS)!!.body.readUtf8().contains("name=\"language\""))
        }
    }

    @Test fun serverErrorsAreReported() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val error = assertThrows(Exception::class.java) {
            RemoteWhisperClient.transcribe(byteArrayOf(), server.url("/").toString(), "Whisper-Base", null)
        }
        assertTrue(error.message!!.contains("503"))
        assertTrue(error.message!!.contains("unavailable"))
    }

    @Test fun malformedTranscriptionIsRejected() {
        server.enqueue(MockResponse().setBody("""{"unexpected":"no text"}"""))
        assertThrows(Exception::class.java) {
            RemoteWhisperClient.transcribe(byteArrayOf(), server.url("/").toString(), "Whisper-Base", null)
        }
    }

    @Test fun apiKeyAuthenticatesModelDiscoveryAndTranscription() {
        val baseUrl = server.url("/api/v1").toString()
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"openai/whisper-large-v3"}]}"""))
        assertEquals(listOf("openai/whisper-large-v3"), RemoteWhisperClient.fetchModels(baseUrl, " test-api-key "))
        val modelsRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/models", modelsRequest.path)
        assertEquals("Bearer test-api-key", modelsRequest.getHeader("Authorization"))

        server.enqueue(MockResponse().setBody("""{"text":"Hello"}"""))
        assertEquals("Hello", RemoteWhisperClient.transcribe(byteArrayOf(), baseUrl, "openai/whisper-large-v3", null, " test-api-key "))
        val transcriptionRequest = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/api/v1/audio/transcriptions", transcriptionRequest.path)
        assertEquals("Bearer test-api-key", transcriptionRequest.getHeader("Authorization"))
        val body = transcriptionRequest.body.readUtf8()
        assertTrue(body.contains("\r\n\r\nopenai/whisper-large-v3\r\n"))
        assertFalse(body.contains("test-api-key"))
        assertFalse(transcriptionRequest.path!!.contains("test-api-key"))
    }

    @Test fun blankApiKeyLeavesSelfHostedRequestsUnauthenticated() {
        for (key in listOf("", " \t\n")) {
            server.enqueue(MockResponse().setBody("""{"data":[]}"""))
            RemoteWhisperClient.fetchModels(server.url("/").toString(), key)
            assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("""{"text":"Hello"}"""))
            RemoteWhisperClient.transcribe(byteArrayOf(), server.url("/").toString(), "Whisper-Base", null, key)
            assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        }
    }

    @Test fun openRouterDiscoveryRequestsTranscriptionModels() {
        for (host in listOf("openrouter.ai", "us.openrouter.ai", "eu.openrouter.ai")) {
            for (path in listOf("/api/v1", "/api/v1/", "/api/v1/models", "/api/v1/audio/transcriptions")) {
                val url = RemoteWhisperClient.modelDiscoveryUrl("https://$host$path")
                assertEquals("https", url.scheme)
                assertEquals(host, url.host)
                assertEquals("/api/v1/models", url.encodedPath)
                assertEquals("transcription", url.queryParameter("output_modalities"))
            }
            val url = RemoteWhisperClient.modelDiscoveryUrl("https://$host/api/v1?region=eu&output_modalities=text")
            assertEquals("eu", url.queryParameter("region"))
            assertEquals(listOf("transcription"), url.queryParameterValues("output_modalities"))
        }
        val localUrl = RemoteWhisperClient.modelDiscoveryUrl(server.url("/v1").toString())
        assertNull(localUrl.queryParameter("output_modalities"))
        for (host in listOf("openrouter.ai.example.com", "us.openrouter.ai.example.com", "eu.openrouter.ai.example.com", "notopenrouter.ai")) {
            val otherHost = RemoteWhisperClient.modelDiscoveryUrl("https://$host/api/v1")
            assertNull(otherHost.queryParameter("output_modalities"))
        }
    }

    @Test fun filteredCatalogIncludesTranscriptionModelsOtherThanWhisper() {
        server.enqueue(MockResponse().setBody("""{"data":[{"id":"google/chirp-3"},{"id":"openai/gpt-4o-transcribe"},{"id":"openai/whisper-large-v3"}]}"""))
        val models = RemoteWhisperClient.fetchModels(server.url("/api/v1?output_modalities=transcription").toString(), "test-api-key")
        assertEquals(listOf("google/chirp-3", "openai/gpt-4o-transcribe", "openai/whisper-large-v3"), models)
    }

    @Test fun modelMetadataIdentifiesTranscriptionModelsInUnfilteredCatalogs() {
        server.enqueue(MockResponse().setBody("""{"data":[
            {"id":"google/chirp-3","architecture":{"output_modalities":["transcription"]}},
            {"id":"openai/gpt-4o-transcribe","architecture":{"output_modalities":["transcription"]}},
            {"id":"chat-model","architecture":{"output_modalities":["text"]}},
            {"id":"Whisper-Base"}
        ]}"""))
        assertEquals(listOf("google/chirp-3", "openai/gpt-4o-transcribe", "Whisper-Base"), RemoteWhisperClient.fetchModels(server.url("/").toString()))
    }

    @Test fun serverErrorsDoNotExposeTheApiKey() {
        for (transcription in listOf(false, true)) {
            server.enqueue(MockResponse().setResponseCode(401).setBody("Rejected Bearer test-api-key"))
            val error = assertThrows(Exception::class.java) {
                if (transcription) {
                    RemoteWhisperClient.transcribe(byteArrayOf(), server.url("/").toString(), "Whisper-Base", null, "test-api-key")
                } else {
                    RemoteWhisperClient.fetchModels(server.url("/").toString(), "test-api-key")
                }
            }
            assertTrue(error.message!!.contains("401"))
            assertFalse(error.message!!.contains("test-api-key"))
        }
    }

    @Test fun invalidHeaderCharactersAreRejectedWithoutExposingTheKey() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            RemoteWhisperClient.fetchModels(server.url("/").toString(), "secret\ninjected-header")
        }
        assertFalse(error.message!!.contains("secret"))
        assertEquals(0, server.requestCount)
    }
}
