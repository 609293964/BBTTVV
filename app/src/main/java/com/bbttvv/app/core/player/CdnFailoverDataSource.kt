@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.bbttvv.app.core.player

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

internal enum class PlaybackStreamKind {
    Video,
    Audio,
    Segment,
    Main,
}

internal class CdnFailoverState(
    val kind: PlaybackStreamKind,
    candidates: List<Uri>,
) {
    val candidates: List<Uri> = candidates.distinct()
    private val cursor = CdnCandidateCursor { this.candidates.size }

    fun preferredIndex(): Int {
        return cursor.preferredIndex()
    }

    fun prefer(index: Int) {
        cursor.prefer(index)
    }

    fun advanceAfterFailure(index: Int) {
        cursor.advanceAfterFailure(index)
    }
}

internal class CdnCandidateCursor(
    private val candidateCount: () -> Int,
) {
    @Volatile
    private var preferredIndex: Int = 0

    @Synchronized
    fun preferredIndex(): Int {
        return coerceIndex(preferredIndex)
    }

    @Synchronized
    fun prefer(index: Int) {
        preferredIndex = coerceIndex(index)
    }

    @Synchronized
    fun advanceAfterFailure(index: Int) {
        val count = candidateCount().coerceAtLeast(0)
        if (count <= 1) {
            preferredIndex = 0
            return
        }

        val failedIndex = coerceIndex(index)
        if (coerceIndex(preferredIndex) == failedIndex) {
            preferredIndex = (failedIndex + 1) % count
        }
    }

    private fun coerceIndex(index: Int): Int {
        val lastIndex = candidateCount().coerceAtLeast(0) - 1
        return index.coerceIn(0, lastIndex.coerceAtLeast(0))
    }
}

internal class CdnFailoverDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val state: CdnFailoverState,
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return CdnFailoverDataSource(
            upstreamFactory = upstreamFactory,
            state = state,
        )
    }
}

internal class CdnFailoverDataSource(
    private val upstreamFactory: DataSource.Factory,
    private val state: CdnFailoverState,
) : DataSource {
    private val transferListeners = ArrayList<TransferListener>(2)
    private var upstream: DataSource? = null
    private var currentCandidateIndex: Int = -1

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        upstream?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        closeQuietly()
        val candidates = state.candidates
        if (candidates.isEmpty()) {
            throw IOException("No CDN candidates for ${state.kind}")
        }

        val startIndex = state.preferredIndex()
        var lastError: IOException? = null
        for (attempt in candidates.indices) {
            val candidateIndex = (startIndex + attempt) % candidates.size
            val dataSource = upstreamFactory.createDataSource()
            transferListeners.forEach(dataSource::addTransferListener)
            val candidateSpec = dataSpec.buildUpon()
                .setUri(candidates[candidateIndex])
                .build()
            try {
                val openedLength = dataSource.open(candidateSpec)
                upstream = dataSource
                currentCandidateIndex = candidateIndex
                state.prefer(candidateIndex)
                return openedLength
            } catch (error: IOException) {
                runCatching { dataSource.close() }
                state.advanceAfterFailure(candidateIndex)
                lastError = error
            }
        }

        throw lastError ?: IOException("Failed to open any CDN candidate for ${state.kind}")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val dataSource = upstream ?: throw IllegalStateException("read() before open() for ${state.kind}")
        return try {
            dataSource.read(buffer, offset, length)
        } catch (error: IOException) {
            currentCandidateIndex.takeIf { it >= 0 }?.let(state::advanceAfterFailure)
            closeQuietly()
            throw error
        }
    }

    override fun getUri(): Uri? = upstream?.uri

    override fun close() {
        closeQuietly()
    }

    private fun closeQuietly() {
        runCatching { upstream?.close() }
        upstream = null
        currentCandidateIndex = -1
    }
}
