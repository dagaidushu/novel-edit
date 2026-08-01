package com.mozhou.novelcraft.core

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class CoverFileTransferTest {
    private val files = mutableListOf<File>()

    @After
    fun cleanUp() {
        files.forEach(File::delete)
    }

    @Test
    fun copiesFromSingleUseProviderWithOnlyOneOpen() {
        val temporary = tempFile()
        val expected = byteArrayOf(1, 2, 3, 4, 5)
        var openCount = 0

        val copied = CoverFileTransfer.copyToTemporaryFile(temporary) {
            openCount += 1
            check(openCount == 1) { "provider was opened more than once" }
            expected.inputStream()
        }

        assertEquals(expected.size.toLong(), copied)
        assertEquals(1, openCount)
        assertArrayEquals(expected, temporary.readBytes())
    }

    @Test
    fun rejectsOversizedInputAndRemovesPartialTemporaryFile() {
        val temporary = tempFile()

        try {
            CoverFileTransfer.copyToTemporaryFile(temporary, maximumBytes = 4) {
                byteArrayOf(1, 2, 3, 4, 5).inputStream()
            }
            fail("Expected an oversized image to fail")
        } catch (_: IllegalArgumentException) {
            assertFalse(temporary.exists())
        }
    }

    private fun tempFile(): File = File.createTempFile("cover-transfer", ".tmp").also(files::add)
}

