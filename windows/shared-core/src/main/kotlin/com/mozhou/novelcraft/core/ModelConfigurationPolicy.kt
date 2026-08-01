package com.mozhou.novelcraft.core

internal fun ModelConfig.hasTextGenerationConfiguration(): Boolean =
    baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()

