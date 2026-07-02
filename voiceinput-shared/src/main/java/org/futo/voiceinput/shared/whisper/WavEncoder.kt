package org.futo.voiceinput.shared.whisper

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

object WavEncoder {
    fun floatArrayToWavBytes(floatSamples: FloatArray): ByteArray {
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)

        val numSamples = floatSamples.size
        val subChunk2Size = numSamples * 2
        val chunkSize = 36 + subChunk2Size

        // 1. RIFF Header
        dataStream.writeBytes("RIFF")
        dataStream.writeInt(Integer.reverseBytes(chunkSize))
        dataStream.writeBytes("WAVE")

        // 2. fmt Subchunk
        dataStream.writeBytes("fmt ")
        dataStream.writeInt(Integer.reverseBytes(16))
        dataStream.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // PCM = 1
        dataStream.writeShort(java.lang.Short.reverseBytes(1.toShort()).toInt()) // Mono = 1
        dataStream.writeInt(Integer.reverseBytes(16000))                 // 16kHz
        dataStream.writeInt(Integer.reverseBytes(32000))                 // ByteRate (16000 * 2)
        dataStream.writeShort(java.lang.Short.reverseBytes(2.toShort()).toInt()) // BlockAlign (1 * 2)
        dataStream.writeShort(java.lang.Short.reverseBytes(16.toShort()).toInt())// 16-bit

        // 3. data Subchunk
        dataStream.writeBytes("data")
        dataStream.writeInt(Integer.reverseBytes(subChunk2Size))

        // 4. PCM Samples (converting floats back to clamped 16-bit shorts)
        for (sample in floatSamples) {
            val scaledSample = (sample.coerceIn(-1.0f, 1.0f) * java.lang.Short.MAX_VALUE).toInt().toShort()
            dataStream.writeShort(java.lang.Short.reverseBytes(scaledSample).toInt())
        }

        dataStream.flush()
        return byteStream.toByteArray()
    }
}
