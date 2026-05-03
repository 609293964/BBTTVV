@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.bbttvv.app.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

object PlayerAudioBalanceController {
    @Volatile
    private var balance: Float = 0f

    fun setBalance(value: Float) {
        balance = value.coerceIn(-1f, 1f)
    }

    fun getBalance(): Float = balance

    fun reset() {
        balance = 0f
    }
}

object VolumeBalanceController {
    @Volatile
    private var level: AudioBalanceLevel = AudioBalanceLevel.Off

    @Volatile
    private var processor: VolumeBalanceAudioProcessor? = null

    fun init(initialLevel: AudioBalanceLevel) {
        level = initialLevel
    }

    fun registerProcessor(processor: VolumeBalanceAudioProcessor) {
        this.processor = processor
    }

    fun unregisterProcessor() {
        this.processor = null
    }

    fun setLevel(newLevel: AudioBalanceLevel) {
        level = newLevel
        processor?.setLevel(newLevel)
    }

    fun getLevel(): AudioBalanceLevel = level

    fun reset() {
        level = AudioBalanceLevel.Off
        processor = null
    }
}

internal class StereoBalanceAudioProcessor : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (
            inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount == 2
        ) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val remaining = inputBuffer.remaining()
        val outputBuffer = replaceOutputBuffer(remaining)
        val balance = PlayerAudioBalanceController.getBalance()

        if (abs(balance) < 0.01f) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val leftGain = if (balance > 0f) 1f - balance else 1f
        val rightGain = if (balance < 0f) 1f + balance else 1f

        while (inputBuffer.remaining() >= 4) {
            val leftSample = inputBuffer.short.toInt()
            val rightSample = inputBuffer.short.toInt()
            outputBuffer.putShort(scaleSample(leftSample, leftGain))
            outputBuffer.putShort(scaleSample(rightSample, rightGain))
        }

        outputBuffer.flip()
    }

    private fun scaleSample(sample: Int, gain: Float): Short {
        val scaled = (sample * gain).roundToInt()
        return scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}

class VolumeBalanceAudioProcessor(
    level: AudioBalanceLevel = AudioBalanceLevel.Off,
) : BaseAudioProcessor() {
    @Volatile
    private var level: AudioBalanceLevel = level

    @Volatile
    private var params: Params = paramsFor(level)

    private var sampleRateHz: Int = 48_000
    private var channelCount: Int = 2
    private var inputEncoding: Int = C.ENCODING_PCM_16BIT
    private var signalAccumulatedSec: Double = 0.0
    private var calibrationSumSquares: Double = 0.0
    private var calibrationSampleCount: Long = 0L
    private var programMeanSquare: Double? = null
    private var currentGain: Float = 1.0f

    init {
        resetAdaptiveState()
    }

    fun setLevel(level: AudioBalanceLevel) {
        if (this.level == level) return
        this.level = level
        this.params = paramsFor(level)
        resetAdaptiveState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val size = inputBuffer.remaining()
        val out = replaceOutputBuffer(size)
        out.order(ByteOrder.nativeOrder())

        val currentLevel = level
        if (currentLevel == AudioBalanceLevel.Off || !isActive) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        val p = params
        val bytesPerSample = when (inputEncoding) {
            C.ENCODING_PCM_FLOAT -> 4
            else -> 2
        }

        val inBuf = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        val startPos = inBuf.position()
        val endPos = inBuf.limit()

        val sampleCount = (endPos - startPos) / bytesPerSample
        if (sampleCount <= 0) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        var sumSquares = 0.0
        var peakAbs = 0.0
        when (inputEncoding) {
            C.ENCODING_PCM_FLOAT -> {
                while (inBuf.remaining() >= 4) {
                    val f = inBuf.getFloat().toDouble()
                    val a = abs(f)
                    peakAbs = max(peakAbs, a)
                    sumSquares += f * f
                }
            }
            else -> {
                while (inBuf.remaining() >= 2) {
                    val s = inBuf.getShort()
                    val v = s.toDouble() / 32768.0
                    val a = abs(v)
                    peakAbs = max(peakAbs, a)
                    sumSquares += v * v
                }
            }
        }
        val meanSquare = (sumSquares / sampleCount.toDouble()).coerceAtLeast(0.0)
        val rms = sqrt(meanSquare)
        val rmsDb = if (rms > 0.0) (20.0 * log10(rms)) else -120.0

        val sr = sampleRateHz.coerceAtLeast(8_000)
        val ch = channelCount.coerceAtLeast(1)
        val frameCount = max(1, sampleCount / ch)
        val dtSec = frameCount.toDouble() / sr.toDouble()

        if (rmsDb > p.silenceGateDb) {
            signalAccumulatedSec += dtSec
            if (programMeanSquare == null) {
                calibrationSumSquares += sumSquares
                calibrationSampleCount += sampleCount.toLong()
                if (signalAccumulatedSec >= p.calibrationSignalSec && calibrationSampleCount > 0L) {
                    programMeanSquare = calibrationSumSquares / calibrationSampleCount.toDouble()
                }
            } else {
                val prev = programMeanSquare ?: meanSquare
                val a = alphaForTimeConstant(dtSec, p.programIntegrationSec)
                programMeanSquare = prev + (a * (meanSquare - prev))
            }
        }

        val desiredGainDb = if (programMeanSquare == null) {
            p.startupGainDb
        } else {
            val programRms = sqrt((programMeanSquare ?: MIN_MEAN_SQUARE).coerceAtLeast(MIN_MEAN_SQUARE))
            val programRmsDb = 20.0 * log10(programRms)
            (TARGET_RMS_DB - programRmsDb).coerceIn(p.minGainDb, p.maxGainDb)
        }
        val desiredGainLinear = dbToLinear(desiredGainDb)

        val peakSafeGain = if (peakAbs > 0.0) {
            (LIMITER_CEILING / peakAbs).toFloat()
        } else {
            Float.POSITIVE_INFINITY
        }

        val desired = min(desiredGainLinear, peakSafeGain)
        val tau = if (desired < currentGain) p.gainAttackSec else p.gainReleaseSec
        val gainAlpha = alphaForTimeConstant(dtSec, tau).toFloat()
        currentGain = (currentGain + gainAlpha * (desired - currentGain)).coerceIn(p.minGainLinear, p.maxGainLinear)
        if (peakSafeGain.isFinite() && peakSafeGain < currentGain) {
            currentGain = peakSafeGain
        }

        val appliedGain = currentGain
        inBuf.position(startPos)
        when (inputEncoding) {
            C.ENCODING_PCM_FLOAT -> {
                while (inBuf.remaining() >= 4) {
                    val f = inBuf.getFloat()
                    val scaled = f * appliedGain
                    out.putFloat(scaled.coerceIn(-1.0f, 1.0f))
                }
            }
            else -> {
                while (inBuf.remaining() >= 2) {
                    val s = inBuf.getShort().toInt()
                    val scaled = (s.toFloat() * appliedGain).toInt()
                    val clipped = scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    out.putShort(clipped.toShort())
                }
            }
        }

        inputBuffer.position(endPos)
        out.flip()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> {
                inputEncoding = inputAudioFormat.encoding
                sampleRateHz = inputAudioFormat.sampleRate.takeIf { it > 0 } ?: 48_000
                channelCount = inputAudioFormat.channelCount.takeIf { it > 0 } ?: 2
                resetAdaptiveState()
                inputAudioFormat
            }
            else -> AudioProcessor.AudioFormat.NOT_SET
        }
    }

    override fun onFlush() {
        resetAdaptiveState()
    }

    override fun onReset() {
        resetAdaptiveState()
    }

    private fun resetAdaptiveState() {
        signalAccumulatedSec = 0.0
        calibrationSumSquares = 0.0
        calibrationSampleCount = 0L
        programMeanSquare = null
        currentGain = if (level == AudioBalanceLevel.Off) 1.0f else params.startupGainLinear
    }

    private data class Params(
        val startupGainDb: Double,
        val maxGainDb: Double,
        val minGainDb: Double,
        val silenceGateDb: Double,
        val calibrationSignalSec: Double,
        val programIntegrationSec: Double,
        val gainAttackSec: Double,
        val gainReleaseSec: Double,
        val startupGainLinear: Float,
        val maxGainLinear: Float,
        val minGainLinear: Float,
    )

    private companion object {
        private const val TARGET_RMS_DB = -20.0
        private const val LIMITER_CEILING = 0.98f
        private const val MIN_MEAN_SQUARE = 1.0e-12

        private fun paramsFor(level: AudioBalanceLevel): Params {
            val startupGainDb = when (level) {
                AudioBalanceLevel.High -> -3.0
                AudioBalanceLevel.Medium -> -1.5
                AudioBalanceLevel.Low -> -0.5
                AudioBalanceLevel.Off -> 0.0
            }
            val maxGainDb = when (level) {
                AudioBalanceLevel.High -> 2.5
                AudioBalanceLevel.Medium -> 2.0
                AudioBalanceLevel.Low -> 1.0
                AudioBalanceLevel.Off -> 0.0
            }
            val minGainDb = when (level) {
                AudioBalanceLevel.High -> -16.0
                AudioBalanceLevel.Medium -> -12.0
                AudioBalanceLevel.Low -> -8.0
                AudioBalanceLevel.Off -> 0.0
            }
            val silenceGateDb = when (level) {
                AudioBalanceLevel.High -> -55.0
                AudioBalanceLevel.Medium -> -53.0
                AudioBalanceLevel.Low -> -50.0
                AudioBalanceLevel.Off -> -120.0
            }
            val calibrationSignalSec = when (level) {
                AudioBalanceLevel.High -> 1.5
                AudioBalanceLevel.Medium -> 2.0
                AudioBalanceLevel.Low -> 2.5
                AudioBalanceLevel.Off -> 0.0
            }
            val programIntegrationSec = when (level) {
                AudioBalanceLevel.High -> 6.0
                AudioBalanceLevel.Medium -> 8.0
                AudioBalanceLevel.Low -> 10.0
                AudioBalanceLevel.Off -> 0.0
            }
            val attackSec = when (level) {
                AudioBalanceLevel.High -> 1.5
                AudioBalanceLevel.Medium -> 2.0
                AudioBalanceLevel.Low -> 3.0
                AudioBalanceLevel.Off -> 0.05
            }
            val releaseSec = when (level) {
                AudioBalanceLevel.High -> 5.0
                AudioBalanceLevel.Medium -> 6.0
                AudioBalanceLevel.Low -> 8.0
                AudioBalanceLevel.Off -> 1.20
            }

            return Params(
                startupGainDb = startupGainDb,
                maxGainDb = maxGainDb,
                minGainDb = minGainDb,
                silenceGateDb = silenceGateDb,
                calibrationSignalSec = calibrationSignalSec,
                programIntegrationSec = programIntegrationSec,
                gainAttackSec = attackSec,
                gainReleaseSec = releaseSec,
                startupGainLinear = dbToLinear(startupGainDb),
                maxGainLinear = dbToLinear(maxGainDb),
                minGainLinear = dbToLinear(minGainDb),
            )
        }

        private fun dbToLinear(db: Double): Float {
            if (!db.isFinite()) return 1.0f
            return 10.0.pow(db / 20.0).toFloat()
        }

        private fun alphaForTimeConstant(dtSec: Double, tauSec: Double): Double {
            if (!dtSec.isFinite() || !tauSec.isFinite()) return 1.0
            if (dtSec <= 0.0) return 0.0
            if (tauSec <= 0.0) return 1.0
            return (1.0 - exp(-dtSec / tauSec)).coerceIn(0.0, 1.0)
        }
    }
}
