package com.mozhou.novelcraft.core

import java.io.File
import java.io.InputStream

/** Copies a document-provider stream once, so temporary URI grants remain valid. */
object CoverFileTransfer {
    const val MAX_COVER_BYTES = 20L * 1024L * 1024L

    fun copyToTemporaryFile(
        temporaryFile: File,
        maximumBytes: Long = MAX_COVER_BYTES,
        openSource: () -> InputStream?,
    ): Long {
        require(maximumBytes > 0) { "Cover size limit must be positive" }
        temporaryFile.parentFile?.mkdirs()
        var copiedBytes = 0L
        try {
            val input = openSource() ?: error("无法读取选择的图片")
            input.use { source ->
                temporaryFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = source.read(buffer)
                        if (read < 0) break
                        copiedBytes += read
                        require(copiedBytes <= maximumBytes) { "图片不能超过 ${maximumBytes / 1024 / 1024} MB" }
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(copiedBytes > 0) { "选择的图片为空" }
            return copiedBytes
        } catch (error: Throwable) {
            temporaryFile.delete()
            throw error
        }
    }
}

