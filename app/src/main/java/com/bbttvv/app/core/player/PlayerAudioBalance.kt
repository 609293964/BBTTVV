@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.bbttvv.app.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import kotlin.math.abs
import kotlin.math.roundToInt

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
