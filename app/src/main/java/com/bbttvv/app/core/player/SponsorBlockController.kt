package com.bbttvv.app.core.player

import com.bbttvv.app.core.plugin.PluginManager
import com.bbttvv.app.core.util.Logger
import com.bbttvv.app.data.model.response.SponsorSegment
import com.bbttvv.app.data.repository.SponsorBlockRepository
import com.bbttvv.app.feature.plugin.SponsorBlockConfig
import com.bbttvv.app.feature.plugin.SponsorCategoryMode
import com.bbttvv.app.feature.plugin.findSponsorBlockPlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal class SponsorBlockController(
    private val scope: CoroutineScope,
    private val playerEngineProvider: () -> PlayerEngine?,
    private val onSponsorSkipped: (SponsorSegment) -> Unit,
) {
    private val _segments = MutableStateFlow<List<SponsorSegment>>(emptyList())
    val segments: StateFlow<List<SponsorSegment>> = _segments.asStateFlow()

    private val _currentSegment = MutableStateFlow<SponsorSegment?>(null)
    val currentSegment: StateFlow<SponsorSegment?> = _currentSegment.asStateFlow()

    private val _showSkipButton = MutableStateFlow(false)
    val showSkipButton: StateFlow<Boolean> = _showSkipButton.asStateFlow()

    private val skippedSegmentIds = mutableSetOf<String>()
    private val reportedSegmentIds = mutableSetOf<String>()
    private var segmentsJob: Job? = null
    private var loadSequence: Long = 0L
    private var segmentsBvid: String = ""
    private var segmentsCid: Long = 0L

    fun load(bvid: String, cid: Long) {
        reset()
        val normalizedBvid = bvid.trim()
        if (normalizedBvid.isBlank() || cid <= 0L) return
        segmentsBvid = normalizedBvid
        segmentsCid = cid
        val sequence = ++loadSequence
        segmentsJob = scope.launch {
            try {
                val loadedSegments = SponsorBlockRepository.getSegments(normalizedBvid, cid)
                if (
                    !isActive ||
                    sequence != loadSequence ||
                    segmentsBvid != normalizedBvid ||
                    segmentsCid != cid
                ) {
                    return@launch
                }
                _segments.value = loadedSegments
                skippedSegmentIds.clear()
                Logger.d(TAG, " SponsorBlock: loaded ${loadedSegments.size} segments for $normalizedBvid cid=$cid")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Logger.w(TAG, " SponsorBlock: load failed: ${e.message}")
            }
        }
    }

    suspend fun checkAndSkip(): Boolean {
        val engine = playerEngineProvider() ?: return false
        val loadedSegments = _segments.value
        if (loadedSegments.isEmpty()) return false

        val sponsorConfig = resolveSponsorBlockConfig()
        val filteredSegments = loadedSegments.filter { segment ->
            val mode = sponsorConfig.playbackMode(segment.category)
            segment.isSkipType &&
                (mode == SponsorCategoryMode.MANUAL || mode == SponsorCategoryMode.AUTO)
        }
        if (filteredSegments.isEmpty()) {
            _currentSegment.value = null
            _showSkipButton.value = false
            return false
        }

        val currentPos = engine.currentPosition
        val segment = SponsorBlockRepository.findSegmentAtPosition(filteredSegments, currentPos)

        if (segment != null && segment.UUID !in skippedSegmentIds) {
            _currentSegment.value = segment

            if (sponsorConfig.playbackMode(segment.category) == SponsorCategoryMode.AUTO) {
                engine.seekTo(segment.endTimeMs)
                skippedSegmentIds.add(segment.UUID)
                _currentSegment.value = null
                _showSkipButton.value = false
                onSponsorSkipped(segment)
                reportViewed(segment)
                return true
            } else {
                _showSkipButton.value = true
            }
        } else if (segment == null) {
            _currentSegment.value = null
            _showSkipButton.value = false
        }

        return false
    }

    fun skipCurrent() {
        val segment = _currentSegment.value ?: return
        val engine = playerEngineProvider() ?: return

        engine.seekTo(segment.endTimeMs)
        skippedSegmentIds.add(segment.UUID)
        _currentSegment.value = null
        _showSkipButton.value = false

        onSponsorSkipped(segment)
        reportViewed(segment)
    }

    fun dismissCurrent() {
        val segment = _currentSegment.value ?: return
        skippedSegmentIds.add(segment.UUID)
        _currentSegment.value = null
        _showSkipButton.value = false
    }

    fun reset() {
        segmentsJob?.cancel()
        segmentsJob = null
        loadSequence += 1
        segmentsBvid = ""
        segmentsCid = 0L
        _segments.value = emptyList()
        _currentSegment.value = null
        _showSkipButton.value = false
        skippedSegmentIds.clear()
        reportedSegmentIds.clear()
    }

    private fun reportViewed(segment: SponsorSegment) {
        if (!reportedSegmentIds.add(segment.UUID)) return
        scope.launch {
            SponsorBlockRepository.reportViewedSegment(segment.UUID)
        }
    }

    private fun resolveSponsorBlockConfig(): SponsorBlockConfig {
        val sponsorPlugin = PluginManager.plugins.findSponsorBlockPlugin()
        return sponsorPlugin?.configState?.value ?: SponsorBlockConfig(autoSkip = false)
    }

    private companion object {
        const val TAG = "SponsorBlock"
    }
}
