package com.mozhou.novelcraft.desktop

import com.mozhou.novelcraft.core.ModelConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SecurePreferencesTest {
    @Test fun apiKeysAreEncryptedAtRest(){
        val dir=Files.createTempDirectory("novelcraft-secret")
        val paths=AppPaths(dir,dir.resolve("db"),dir.resolve("covers"),dir.resolve("recovery"),true)
        val prefs=SecureModelPreferences(paths)
        prefs.save(ModelConfig(baseUrl="https://example.com/v1",apiKey="secret-value",model="writer"))
        assertFalse(Files.readString(dir.resolve("model-config.json")).contains("secret-value"))
        assertEquals("secret-value",prefs.load().apiKey)
    }
}
