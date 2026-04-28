package com.bbttvv.app.core.util

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.os.Build
import android.view.Display

object MediaUtils {
    /**
     * Check if HEVC (H.265) decoder is supported
     */
    fun isHevcSupported(): Boolean {
        return hasDecoder("video/hevc")
    }

    /**
     * Check if AV1 decoder is supported
     */
    fun isAv1Supported(): Boolean {
        // AV1 support is limited on older devices
        return hasDecoder("video/av01")
    }

    /**
     * Check if HDR (HDR10/HLG) video is supported
     * HDR requires both decoder support and display capability
     */
    fun isHdrSupported(context: Context? = null): Boolean {
        // HDR10 uses HEVC with specific profile
        // Check for HEVC support first, then verify display HDR capability when context is available
        if (!hasDecoder("video/hevc")) {
            return false
        }
        if (context == null) {
            return true
        }
        return hasHdrDisplaySupport(context)
    }
    
    /**
     * Check if Dolby Vision is supported
     * Dolby Vision requires specific hardware decoder
     */
    fun isDolbyVisionSupported(context: Context? = null): Boolean {
        // Dolby Vision MIME type
        val hasDolbyDecoder = hasDecoder("video/dolby-vision") || hasDecoder("video/dvhe") || hasDecoder("video/dvav")
        if (!hasDolbyDecoder) {
            return false
        }
        if (context == null) {
            return true
        }
        return hasDolbyVisionDisplaySupport(context)
    }

    private fun hasHdrDisplaySupport(context: Context): Boolean {
        return supportsGenericHdrTypes(getSupportedHdrTypes(context))
    }

    private fun hasDolbyVisionDisplaySupport(context: Context): Boolean {
        return supportsDolbyVisionType(getSupportedHdrTypes(context))
    }

    private fun getSupportedHdrTypes(context: Context): IntArray? {
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
            display?.hdrCapabilities?.supportedHdrTypes
        } catch (e: Exception) {
            Logger.e("MediaUtils", "Failed to read display HDR capabilities", e)
            null
        }
    }

    internal fun supportsGenericHdrTypes(supportedHdrTypes: IntArray?): Boolean {
        if (supportedHdrTypes == null || supportedHdrTypes.isEmpty()) {
            return false
        }
        for (type in supportedHdrTypes) {
            if (type == Display.HdrCapabilities.HDR_TYPE_HDR10 ||
                type == Display.HdrCapabilities.HDR_TYPE_HLG
            ) {
                return true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                type == Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS
            ) {
                return true
            }
        }
        return false
    }

    internal fun supportsDolbyVisionType(supportedHdrTypes: IntArray?): Boolean {
        val types = supportedHdrTypes ?: return false
        if (types.isEmpty()) {
            return false
        }
        return types.contains(Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION)
    }

    private fun hasDecoder(mimeType: String): Boolean {
        try {
            val list = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val codecs = list.codecInfos
            for (codec in codecs) {
                if (codec.isEncoder) continue
                val types = codec.supportedTypes
                for (type in types) {
                    if (type.equals(mimeType, ignoreCase = true)) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("MediaUtils", "Failed to check decoder support for $mimeType", e)
        }
        return false
    }
}

