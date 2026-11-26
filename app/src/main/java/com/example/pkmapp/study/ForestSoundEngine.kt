package com.example.pkmapp.study

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Zero-asset procedural forest white-noise synthesizer backed by native AudioTrack.
 * Synthesizes seamless, non-repeating Rain, Fireplace, and Pine Wind in real time.
 */
object ForestSoundEngine {

    enum class AmbientSound(val id: String, val title: String, val subtitle: String, val shortLabel: String) {
        NONE("none", "静音", "保持安静专注", "静音"),
        RAIN("rain", "林间微雨", "细密雨滴与树叶轻响", "微雨"),
        FIRE("fire", "木屋炉火", "温暖柴火低鸣与轻微噼啪声", "炉火"),
        WIND("wind", "林梢清风", "舒缓起伏的松林微风声", "清风");

        companion object {
            @JvmStatic
            fun fromId(raw: String?): AmbientSound =
                entries.firstOrNull { it.id == raw } ?: NONE
        }
    }

    internal class SynthState(seed: Long = 20261126L) {
        val random = Random(seed)
        // Pink noise (Paul Kellet) state for Rain
        var b0 = 0.0
        var b1 = 0.0
        var b2 = 0.0
        var b3 = 0.0
        var b4 = 0.0
        var b5 = 0.0
        var b6 = 0.0
        var dropEnv = 0.0
        var dropPhase = 0.0
        var dropFreq = 1200.0
        // Brown noise + crackle state for Fire
        var brown = 0.0
        var lowPass = 0.0
        var crackleEnv = 0.0
        var crackleSign = 1.0
        // Bandpass resonant filter state for Wind
        var bp1 = 0.0
        var bp2 = 0.0
        // Global phase & smooth envelope
        var sampleIndex = 0L
        var fadeGain = 0.0
    }

    private const val TAG = "ForestSoundEngine"
    private const val PREFS_NAME = "forest_sound_prefs"
    private const val KEY_SOUND = "selected_sound"
    private const val KEY_VOLUME = "sound_volume"
    private const val SAMPLE_RATE = 22050
    private const val FRAME_SAMPLES = 1024

    private val lock = Any()
    private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile
    private var playing = false

    @Volatile
    private var activeSound: AmbientSound = AmbientSound.NONE

    @Volatile
    private var currentVolume: Float = 0.55f

    @Volatile
    private var previewActive = false

    private var workerThread: Thread? = null
    private var previewTimeoutRunnable: Runnable? = null

    @JvmStatic
    fun selectedSound(context: Context): AmbientSound {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return AmbientSound.fromId(prefs.getString(KEY_SOUND, AmbientSound.NONE.id))
    }

    @JvmStatic
    fun setSelectedSound(context: Context, sound: AmbientSound) {
        val appCtx = context.applicationContext
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOUND, sound.id)
            .apply()
        val running = StudyStore.get(appCtx).notebook().let { it.hasSession() && it.running() }
        if (!previewActive) {
            syncWithFocusState(appCtx, running)
        }
    }

    @JvmStatic
    fun volume(context: Context): Float {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_VOLUME, 0.55f).coerceIn(0.05f, 1.0f)
    }

    @JvmStatic
    fun setVolume(context: Context, value: Float) {
        val clamped = value.coerceIn(0.05f, 1.0f)
        currentVolume = clamped
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_VOLUME, clamped)
            .apply()
    }

    @JvmStatic
    fun syncWithFocusState(context: Context, focusRunning: Boolean) {
        val appCtx = context.applicationContext
        if (previewActive) return
        val sound = selectedSound(appCtx)
        currentVolume = volume(appCtx)
        if (focusRunning && sound != AmbientSound.NONE) {
            startLoop(sound)
        } else {
            stopLoop()
        }
    }

    @JvmStatic
    fun preview(
        context: Context,
        sound: AmbientSound,
        durationMs: Long = 5_000L,
        onFinished: (() -> Unit)? = null
    ) {
        val appCtx = context.applicationContext
        cancelPreviewInternal()
        if (sound == AmbientSound.NONE) {
            stopLoop()
            onFinished?.invoke()
            return
        }
        currentVolume = volume(appCtx)
        previewActive = true
        startLoop(sound)
        val timeout = Runnable {
            previewActive = false
            previewTimeoutRunnable = null
            val running = try {
                StudyStore.get(appCtx).notebook().let { it.hasSession() && it.running() }
            } catch (_: RuntimeException) {
                false
            }
            syncWithFocusState(appCtx, running)
            onFinished?.invoke()
        }
        previewTimeoutRunnable = timeout
        mainHandler.postDelayed(timeout, durationMs)
    }

    @JvmStatic
    fun stopPreviewAndRestore(context: Context) {
        val appCtx = context.applicationContext
        val wasPreview = previewActive
        cancelPreviewInternal()
        if (wasPreview) {
            val running = try {
                StudyStore.get(appCtx).notebook().let { it.hasSession() && it.running() }
            } catch (_: RuntimeException) {
                false
            }
            syncWithFocusState(appCtx, running)
        }
    }

    @JvmStatic
    fun stopAmbient() {
        cancelPreviewInternal()
        stopLoop()
    }

    private fun cancelPreviewInternal() {
        previewTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        previewTimeoutRunnable = null
        previewActive = false
    }

    private fun startLoop(sound: AmbientSound) {
        synchronized(lock) {
            if (playing && activeSound == sound && workerThread?.isAlive == true) {
                return
            }
            stopLoopLocked()
            activeSound = sound
            playing = true
            val thread = Thread({ runAudioLoop() }, "forest-ambient-synth").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
            workerThread = thread
            thread.start()
        }
    }

    private fun stopLoop() {
        synchronized(lock) {
            stopLoopLocked()
        }
    }

    private fun stopLoopLocked() {
        playing = false
        activeSound = AmbientSound.NONE
        val old = workerThread
        workerThread = null
        if (old != null && old !== Thread.currentThread()) {
            old.interrupt()
        }
    }

    private fun runAudioLoop() {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) {
            Log.w(TAG, "AudioTrack min buffer unavailable: $minBuf")
            playing = false
            return
        }
        val bufferBytes = max(minBuf * 2, FRAME_SAMPLES * 4)
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val pcm = ShortArray(FRAME_SAMPLES)
            val state = SynthState(System.nanoTime())
            track.play()
            while (playing && !Thread.currentThread().isInterrupted) {
                val sound = activeSound
                if (sound == AmbientSound.NONE) break
                synthesizeFrame(sound, pcm, state, currentVolume)
                val written = track.write(pcm, 0, pcm.size)
                if (written < 0) break
            }
        } catch (error: RuntimeException) {
            Log.w(TAG, "Ambient playback interrupted: ${error.message}")
        } finally {
            try {
                track?.pause()
                track?.flush()
                track?.stop()
                track?.release()
            } catch (_: RuntimeException) {
            }
        }
    }

    /** Pure PCM frame synthesizer; deterministic enough for JVM unit tests and seamless on device. */
    internal fun synthesizeFrame(
        sound: AmbientSound,
        buffer: ShortArray,
        state: SynthState,
        volume: Float
    ) {
        if (sound == AmbientSound.NONE) {
            buffer.fill(0)
            return
        }
        val targetGain = volume.coerceIn(0f, 1f).toDouble()
        for (i in buffer.indices) {
            // Smooth ~280ms fade-in / gain tracking to eliminate clicks
            state.fadeGain += (targetGain - state.fadeGain) * 0.00065
            val t = state.sampleIndex.toDouble() / SAMPLE_RATE.toDouble()
            state.sampleIndex++

            val raw = when (sound) {
                AmbientSound.RAIN -> nextRainSample(state, t)
                AmbientSound.FIRE -> nextFireSample(state, t)
                AmbientSound.WIND -> nextWindSample(state, t)
                AmbientSound.NONE -> 0.0
            }
            val scaled = (raw * state.fadeGain * 26000.0).coerceIn(-32000.0, 32000.0)
            buffer[i] = scaled.roundToInt().toShort()
        }
    }

    private fun nextRainSample(s: SynthState, t: Double): Double {
        val white = s.random.nextDouble() * 2.0 - 1.0
        // Paul Kellet pink noise approximation
        s.b0 = 0.99886 * s.b0 + white * 0.0555179
        s.b1 = 0.99332 * s.b1 + white * 0.0750759
        s.b2 = 0.96900 * s.b2 + white * 0.1538520
        s.b3 = 0.86650 * s.b3 + white * 0.3104856
        s.b4 = 0.55000 * s.b4 + white * 0.5329522
        s.b5 = -0.7616 * s.b5 - white * 0.0168980
        val pink = (s.b0 + s.b1 + s.b2 + s.b3 + s.b4 + s.b5 + s.b6 + white * 0.5362) * 0.065
        s.b6 = white * 0.115926

        // Subtle soft raindrop pings on leaves
        if (s.dropEnv < 0.002 && s.random.nextDouble() < 0.00055) {
            s.dropEnv = 0.12 + s.random.nextDouble() * 0.14
            s.dropFreq = 900.0 + s.random.nextDouble() * 1400.0
            s.dropPhase = 0.0
        }
        var drop = 0.0
        if (s.dropEnv > 0.001) {
            s.dropPhase += 2.0 * PI * s.dropFreq / SAMPLE_RATE
            drop = sin(s.dropPhase) * s.dropEnv
            s.dropEnv *= 0.988
        }
        val swell = 0.86 + 0.14 * sin(2.0 * PI * 0.11 * t)
        return (pink + drop * 0.35) * swell
    }

    private fun nextFireSample(s: SynthState, t: Double): Double {
        val white = s.random.nextDouble() * 2.0 - 1.0
        // Warm low-frequency rumble (leaky brownian integrator)
        s.brown = (s.brown + 0.032 * white) * 0.985
        s.lowPass += 0.09 * (s.brown - s.lowPass)
        val flameSwell = 0.82 + 0.18 * sin(2.0 * PI * 0.19 * t) * cos(2.0 * PI * 0.07 * t)

        // Occasional gentle ember crackle
        if (s.crackleEnv < 0.005 && s.random.nextDouble() < 0.00038) {
            s.crackleEnv = 0.35 + s.random.nextDouble() * 0.50
            s.crackleSign = if (s.random.nextBoolean()) 1.0 else -1.0
        }
        var crackle = 0.0
        if (s.crackleEnv > 0.004) {
            crackle = (white * 0.65 + s.crackleSign * 0.35) * s.crackleEnv * 0.42
            s.crackleEnv *= 0.92
        }
        return s.lowPass * 2.4 * flameSwell + crackle
    }

    private fun nextWindSample(s: SynthState, t: Double): Double {
        val white = s.random.nextDouble() * 2.0 - 1.0
        // Two incommensurate slow LFOs for natural pine-forest breeze breathing
        val gust = 0.55 + 0.30 * sin(2.0 * PI * 0.07 * t) + 0.15 * sin(2.0 * PI * 0.13 * t + 1.1)
        val centerHz = 210.0 + 290.0 * gust
        val f = min(0.45, 2.0 * sin(PI * centerHz / SAMPLE_RATE))
        val q = 0.18 // gentle smooth resonance
        val low = s.bp2 + f * s.bp1
        val high = white - low - q * s.bp1
        s.bp1 += f * high
        s.bp2 = low
        // Clamp filter state for numerical stability
        s.bp1 = s.bp1.coerceIn(-4.0, 4.0)
        s.bp2 = s.bp2.coerceIn(-4.0, 4.0)
        return s.bp1 * 0.24 * gust
    }
}
