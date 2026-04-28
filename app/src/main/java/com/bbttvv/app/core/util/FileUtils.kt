package com.bbttvv.app.core.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import java.io.File

object FileUtils {

    /**
     * 将 SAF Tree URI 转换为绝对路径
     * 注意：这主要针对 "primary" 存储卷（内置存储）
     */
    fun getPathFromUri(context: Context, uri: Uri): String? {
        try {
            if (DocumentsContract.isTreeUri(uri)) {
                val docId = DocumentsContract.getTreeDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val path = split[1]
                    
                    if ("primary".equals(type, ignoreCase = true)) {
                        return Environment.getExternalStorageDirectory().toString() + "/" + path
                    } else {
                        // 尝试处理 SD 卡或其他卷
                        // 这部分比较复杂，简单实现可能无法覆盖所有情况
                        // 这里只是一个简单的回退
                        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as android.os.storage.StorageManager
                        val storageVolume = storageManager.storageVolumes.find { 
                            // 尝试匹配 uuid
                            it.uuid == type 
                        }
                        if (storageVolume != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val directory = storageVolume.directory
                                if (directory != null) {
                                    return File(directory, path).absolutePath
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e("FileUtils", "Failed to parse URI: $uri", e)
        }
        return null
    }
}

