package com.bbttvv.app.core.plugin.json

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

internal object JsonPluginStorage {
    /**
     * Writes in the target directory, fsyncs the temp file, then atomically replaces the
     * target on the same filesystem. If moving fails, the previous target remains intact.
     */
    fun writeAtomically(
        target: File,
        content: String,
        move: (File, File) -> Unit = { from, to ->
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            Unit
        }
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
            move(temp, target)
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
