package com.einkphoto.app.feature.aialbum

import android.content.Context

data class AiProviderConfiguration(
    val configured: Boolean = false,
    val profileId: String = "",
    val profileName: String = "",
    val endpoint: String = "",
    val imageModel: String = "",
    val keyLast4: String? = null,
)

/** Public model profile only. The complete API key is never parsed or retained. */
data class AiModelProfile(
    val id: String,
    val name: String,
    val endpoint: String,
    val imageModel: String,
    val keyLast4: String?,
    val active: Boolean,
)

class AiConfigRepository(
    private val context: Context,
) {
    private val preferences get() = context.getSharedPreferences("app_ai_provider", Context.MODE_PRIVATE)

    suspend fun read(): Result<AiProviderConfiguration> = runCatching {
        AiProviderConfiguration(
            configured = preferences.getBoolean("configured", false),
            profileId = preferences.getString("profile_id", "").orEmpty(),
            profileName = preferences.getString("profile_name", "").orEmpty(),
            endpoint = preferences.getString("endpoint", "").orEmpty(),
            imageModel = preferences.getString("model", "").orEmpty(),
            keyLast4 = preferences.getString("api_key", "").orEmpty().takeLast(4).takeIf { it.length == 4 },
        )
    }

    /** There is exactly one App-owned Seedream configuration; ESP profiles are no longer used. */
    suspend fun readProfiles(): Result<List<AiModelProfile>> = read().map { config ->
        if (!config.configured) emptyList() else listOf(
            AiModelProfile(
                id = config.profileId.ifBlank { DIRECT_PROFILE_ID },
                name = config.profileName.ifBlank { "Seedream 直连" },
                endpoint = config.endpoint,
                imageModel = config.imageModel,
                keyLast4 = config.keyLast4,
                active = true,
            ),
        )
    }

    suspend fun saveProfile(id: String, name: String, endpoint: String, imageModel: String, apiKey: String): Result<AiModelProfile> = runCatching {
        require(endpoint.startsWith("https://") && imageModel.isNotBlank() && apiKey.length >= 8) { "app_ai_not_configured" }
        preferences.edit()
            .putBoolean("configured", true)
            .putString("profile_id", DIRECT_PROFILE_ID)
            .putString("profile_name", name.trim().ifBlank { "Seedream 直连" })
            .putString("endpoint", endpoint.trim())
            .putString("model", imageModel.trim())
            .putString("api_key", apiKey)
            .apply()
        readProfiles().getOrThrow().single()
    }

    suspend fun activateProfile(id: String): Result<Unit> = runCatching {
        require(id == DIRECT_PROFILE_ID || id == preferences.getString("profile_id", "")) { "unknown_profile" }
    }

    suspend fun deleteProfile(id: String): Result<Unit> = clear()

    suspend fun save(endpoint: String, imageModel: String, apiKey: String): Result<AiProviderConfiguration> = runCatching {
        preferences.edit().putBoolean("configured", true).putString("profile_id", DIRECT_PROFILE_ID).putString("profile_name", "Seedream 直连").putString("endpoint", endpoint).putString("model", imageModel).putString("api_key", apiKey).apply()
        read().getOrThrow()
    }

    suspend fun clear(): Result<Unit> = runCatching { preferences.edit().clear().apply() }

    /** A non-billable local validation. Actual connectivity is checked only by the user's generation request. */
    suspend fun testConnection(allowBillableTest: Boolean): Result<AiConnectionTest> = read().mapCatching { config ->
        require(config.configured && config.endpoint.startsWith("https://") && config.imageModel.isNotBlank() && config.keyLast4 != null) {
            "app_ai_not_configured"
        }
        AiConnectionTest(
            code = "app_ai_configured",
            networkReachable = false,
            endpointReachable = false,
            authenticated = false,
            modelAvailable = false,
            providerMessage = "已完成本地配置校验；实际连接会在用户明确生成图片时由手机直连 Seedream。",
        )
    }

    private companion object {
        const val DIRECT_PROFILE_ID = "seedream-direct"
    }
}

data class AiConnectionTest(
    val code: String,
    val networkReachable: Boolean,
    val endpointReachable: Boolean,
    val authenticated: Boolean,
    val modelAvailable: Boolean,
    val providerMessage: String = "",
)
