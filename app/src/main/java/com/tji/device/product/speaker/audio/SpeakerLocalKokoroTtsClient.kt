package com.tji.device.product.speaker.audio

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

class SpeakerLocalKokoroTtsClient(context: Context) {
    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var offlineTts: OfflineTts? = null

    suspend fun synthesizePcm16Mono(
        text: String,
        settings: SpeakerKokoroTtsSettings,
        targetSampleRate: Int? = SpeakerAudioConfig.Tts.LOCAL_KOKORO_TARGET_SAMPLE_RATE
    ): SpeakerLocalTtsAudio = withContext(Dispatchers.IO) {
        val normalizedText = text.trim()
        require(normalizedText.isNotBlank()) { "请输入喊话文本" }
        require(targetSampleRate == null || targetSampleRate > 0) { "本地 TTS 目标采样率无效: $targetSampleRate" }
        val engine = mutex.withLock { offlineTts ?: createEngine().also { offlineTts = it } }
        val normalizedSettings = settings.normalized()
        val audio = runCatching {
            engine.generate(normalizedText, normalizedSettings.voice.speakerId, normalizedSettings.speed)
        }.getOrElse { throwable ->
            throw IllegalStateException("本地 Kokoro 合成失败: ${throwable.rootMessage()}", throwable)
        }
        val samples = audio.samples
        val sourceSampleRate = audio.sampleRate
        require(samples.isNotEmpty()) { "本地 Kokoro 返回空音频" }
        val outputSampleRate = targetSampleRate ?: sourceSampleRate
        val targetSamples = if (sourceSampleRate == outputSampleRate) {
            samples
        } else {
            samples.resampleLinear(sourceSampleRate, outputSampleRate)
        }
        val pcm16 = targetSamples.toPcm16Le()
        Log.d(
            TAG,
            "local kokoro generated sourceRate=$sourceSampleRate targetRate=$outputSampleRate " +
                "sourceSamples=${samples.size} targetSamples=${targetSamples.size} bytes=${pcm16.size}"
        )
        SpeakerLocalTtsAudio(
            pcm16 = pcm16,
            sampleRate = outputSampleRate,
            sourceSampleRate = sourceSampleRate,
            sourceSamples = samples.size
        )
    }

    fun isRuntimeAvailable(): Boolean =
        runCatching {
            appContext.assets.open("${SpeakerAudioConfig.Tts.LOCAL_KOKORO_MODEL_DIR}/${SpeakerAudioConfig.Tts.LOCAL_KOKORO_MODEL_NAME}").close()
        }.isSuccess

    private fun createEngine(): OfflineTts {
        val ttsConfig = buildTtsConfig()
        Log.d(TAG, "initializing local kokoro model=${SpeakerAudioConfig.Tts.LOCAL_KOKORO_MODEL_DIR}")
        return runCatching {
            OfflineTts(assetManager = appContext.assets, config = ttsConfig)
        }.getOrElse { throwable ->
            throw IllegalStateException("本地 Kokoro 初始化失败: ${throwable.rootMessage()}", throwable)
        }
    }

    private fun buildTtsConfig(): OfflineTtsConfig {
        val modelDir = SpeakerAudioConfig.Tts.LOCAL_KOKORO_MODEL_DIR
        val copiedDataDir = copyAssetDirToFiles("$modelDir/espeak-ng-data")
        val kokoroConfig = OfflineTtsKokoroModelConfig(
            model = "$modelDir/${SpeakerAudioConfig.Tts.LOCAL_KOKORO_MODEL_NAME}",
            voices = "$modelDir/${SpeakerAudioConfig.Tts.LOCAL_KOKORO_VOICES}",
            tokens = "$modelDir/tokens.txt",
            dataDir = copiedDataDir.absolutePath,
            lexicon = SpeakerAudioConfig.Tts.LOCAL_KOKORO_LEXICON
        )
        val modelConfig = OfflineTtsModelConfig(
            kokoro = kokoroConfig,
            numThreads = LOCAL_KOKORO_THREADS,
            debug = false,
            provider = "cpu"
        )
        return OfflineTtsConfig(
            model = modelConfig,
            ruleFsts = SpeakerAudioConfig.Tts.LOCAL_KOKORO_RULE_FSTS,
            maxNumSentences = 1,
            silenceScale = 0.2f
        )
    }

    private fun copyAssetDirToFiles(assetDir: String): File {
        val targetDir = File(appContext.filesDir, "speaker-local-tts/$assetDir")
        if (targetDir.isDirectory && targetDir.listFiles()?.isNotEmpty() == true) return targetDir
        copyAssetDir(assetDir, targetDir)
        return targetDir
    }

    private fun copyAssetDir(assetDir: String, targetDir: File) {
        val children = appContext.assets.list(assetDir).orEmpty()
        require(children.isNotEmpty()) { "本地 Kokoro 缺少资源目录: assets/$assetDir" }
        targetDir.mkdirs()
        children.forEach { child ->
            val childAsset = "$assetDir/$child"
            val grandChildren = appContext.assets.list(childAsset).orEmpty()
            val target = File(targetDir, child)
            if (grandChildren.isEmpty()) {
                appContext.assets.open(childAsset).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            } else {
                copyAssetDir(childAsset, target)
            }
        }
    }

    private companion object {
        const val TAG = "SpeakerLocalKokoro"
        const val LOCAL_KOKORO_THREADS = 4
    }
}

data class SpeakerLocalTtsAudio(
    val pcm16: ByteArray,
    val sampleRate: Int,
    val sourceSampleRate: Int,
    val sourceSamples: Int
)

private fun FloatArray.resampleLinear(sourceRate: Int, targetRate: Int): FloatArray {
    require(sourceRate > 0 && targetRate > 0) { "采样率无效: $sourceRate -> $targetRate" }
    if (isEmpty() || sourceRate == targetRate) return this
    val outSize = (size.toLong() * targetRate / sourceRate).toInt().coerceAtLeast(1)
    val output = FloatArray(outSize)
    for (i in output.indices) {
        val sourcePos = i.toDouble() * sourceRate.toDouble() / targetRate.toDouble()
        val base = sourcePos.toInt().coerceIn(0, lastIndex)
        val next = (base + 1).coerceAtMost(lastIndex)
        val fraction = sourcePos - base
        output[i] = (this[base] + (this[next] - this[base]) * fraction).toFloat()
    }
    return output
}

private fun FloatArray.toPcm16Le(): ByteArray {
    val pcm = ByteArray(size * 2)
    for (i in indices) {
        val clamped = this[i].coerceIn(-1f, 1f)
        val value = if (clamped < 0f) {
            (clamped * SpeakerAudioConfig.Pcm.PCM_I16_NEGATIVE_SCALE).roundToInt()
        } else {
            (clamped * SpeakerAudioConfig.Pcm.PCM_I16_POSITIVE_SCALE).roundToInt()
        }.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        pcm[i * 2] = (value and 0xFF).toByte()
        pcm[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
    }
    return pcm
}

private fun Throwable.rootMessage(): String {
    var current: Throwable = this
    while (current.cause != null) current = current.cause!!
    return current.message ?: current::class.java.simpleName
}
