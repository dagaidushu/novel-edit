package com.mozhou.novelcraft.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelConfigurationPolicyTest {
    @Test
    fun requiresEveryTextModelFieldBeforeCreatingAProject() {
        assertFalse(ModelConfig(baseUrl = "https://api.example.com", apiKey = "key").hasTextGenerationConfiguration())
        assertFalse(ModelConfig(baseUrl = "https://api.example.com", model = "model").hasTextGenerationConfiguration())
        assertTrue(ModelConfig(baseUrl = "https://api.example.com", apiKey = "key", model = "model").hasTextGenerationConfiguration())
    }
}

