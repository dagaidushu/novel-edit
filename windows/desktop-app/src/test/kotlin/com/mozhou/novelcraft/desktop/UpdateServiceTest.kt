package com.mozhou.novelcraft.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpdateServiceTest {
    @Test fun comparesSemanticVersionParts() {
        assertTrue(isNewerVersion("1.0.2", "1.0.1"))
        assertTrue(isNewerVersion("1.1", "1.0.99"))
        assertFalse(isNewerVersion("1.0.1", "1.0.1"))
        assertFalse(isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test fun selectsChecksumForTheDownloadedDistribution() {
        val update = UpdateInfo("1.0.2", "https://example.test/app.msi", "https://example.test/app.zip", "m".repeat(64), "z".repeat(64))
        assertEquals("m".repeat(64), update.checksum(portable = false))
        assertEquals("z".repeat(64), update.checksum(portable = true))
    }

    @Test fun acceptsEachUsersOptionalHttpProxyAndRejectsInvalidAddresses() {
        assertNull(updateProxyAddress(""))
        assertEquals(10808, requireNotNull(updateProxyAddress("http://127.0.0.1:10808")).port)
        assertEquals(7890, requireNotNull(updateProxyAddress("http://localhost:7890")).port)
        assertFailsWith<IllegalArgumentException> { updateProxyAddress("socks5://127.0.0.1:10808") }
        assertFailsWith<IllegalArgumentException> { updateProxyAddress("http://127.0.0.1") }
    }
}
