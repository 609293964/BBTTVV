package com.bbttvv.app.core.plugin.json

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.UUID

internal object JsonPluginStorage {
    /**
     * Writes in the target directory, fsyncs the temp file, then renames it into place.
     * On Android/Linux the rename is atomic within one filesystem. If rename fails the
     * existing target is left untouched and the temp file is cleaned up.
     */
    fun writeAtomically(
        target: File,
        content: String,
        rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }
    ) {
        val parent = target.parentFile ?: throw IOException("插件目录无效")
        if (!parent.exists() && !parent.mkdirs()) {
            throw IOException("无法创建插件目录")
        }

        val temp = File(parent, ".${target.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                    output.fd.sync()
                }
            }
            if (!rename(temp, target)) {
                throw IOException("原子替换插件文件失败")
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun deleteConsistently(
        target: File,
        delete: (File) -> Boolean = { it.delete() }
    ): Boolean {
        return !target.exists() || delete(target)
    }
}
