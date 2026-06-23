package com.tji.device.product.speaker.audio

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.tji.device.product.speaker.core.SpeakerCoreAudioEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

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
        require(targetSampleRate == null || targetSampleRate > 0) { "语音设置有问题，请重试" }
        val engine = mutex.withLock { offlineTts ?: createEngine().also { offlineTts = it } }
        val normalizedSettings = settings.normalized()
        val audio = runCatching {
            engine.generate(normalizedText, normalizedSettings.voice.speakerId, normalizedSettings.speed)
        }.getOrElse { throwable ->
            throw IllegalStateException("语音生成失败，请重试", throwable)
        }
        val samples = audio.samples
        val sourceSampleRate = audio.sampleRate
        require(samples.isNotEmpty()) { "没有生成有效语音，请换一段文字试试" }
        val outputSampleRate = targetSampleRate ?: sourceSampleRate
        val pcm16 = SpeakerCoreAudioEngine.float32ToPcm16(samples, sourceSampleRate, outputSampleRate)
        Log.d(
            TAG,
            "local kokoro generated sourceRate=$sourceSampleRate targetRate=$outputSampleRate " +
                "sourceSamples=${samples.size} targetSamples=${pcm16.size / 2} bytes=${pcm16.size}"
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
            throw IllegalStateException("语音包加载失败，请安装完整版本后再试", throwable)
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
        require(children.isNotEmpty()) { "语音包不完整，请安装完整版本后再试" }
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

private fun Throwable.rootMessage(): String {
    var current: Throwable = this
    while (current.cause != null) current = current.cause!!
    return current.message ?: current::class.java.simpleName
}
