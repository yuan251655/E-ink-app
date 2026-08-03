package com.einkphoto.app.feature.aialbum

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import org.json.JSONObject

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
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) {
    suspend fun read(): Result<AiProviderConfiguration> = client.get("/api/v1/ai/config").mapCatching { root ->
        val data = root.getJSONObject("data")
        AiProviderConfiguration(
            configured = data.optBoolean("configured", false),
            profileId = data.optString("profile_id", ""),
            profileName = data.optString("profile_name", ""),
            endpoint = data.optString("endpoint", ""),
            imageModel = data.optString("model", ""),
            keyLast4 = data.optString("key_last4", "").takeIf { it.length == 4 },
        )
    }

    suspend fun readProfiles(): Result<List<AiModelProfile>> = client.get("/api/v1/ai/config/profiles").mapCatching { root ->
        val data = root.optJSONObject("data") ?: JSONObject()
        val models = data.optJSONArray("models") ?: data.optJSONArray("profiles") ?: org.json.JSONArray()
        buildList {
            for (index in 0 until models.length()) {
                val item = models.optJSONObject(index) ?: continue
                val id = item.optString("id", "").trim()
                if (id.isBlank()) continue
                add(
                    AiModelProfile(
                        id = id,
                        name = item.optString("name", "").trim().ifBlank { item.optString("model", "未命名模型") },
                        endpoint = item.optString("endpoint", ""),
                        imageModel = item.optString("model", ""),
                        keyLast4 = item.optString("key_last4", "").takeIf { it.length == 4 },
                        active = item.optBoolean("active", false),
                    ),
                )
            }
        }
    }

    suspend fun saveProfile(id: String, name: String, endpoint: String, imageModel: String, apiKey: String): Result<AiModelProfile> =
        client.postJson(
            "/api/v1/ai/config/profiles",
            JSONObject()
                .put("id", id)
                .put("name", name)
                .put("endpoint", endpoint)
                .put("model", imageModel)
                .apply { if (apiKey.isNotBlank()) put("api_key", apiKey) },
        ).mapCatching { root ->
            val data = root.optJSONObject("data") ?: root
            AiModelProfile(
                id = data.optString("id", id),
                name = data.optString("name", name),
                endpoint = data.optString("endpoint", endpoint),
                imageModel = data.optString("model", imageModel),
                keyLast4 = data.optString("key_last4", "").takeIf { it.length == 4 },
                active = data.optBoolean("active", false),
            )
        }

    suspend fun activateProfile(id: String): Result<Unit> =
        client.postJson("/api/v1/ai/config/profiles/activate", JSONObject().put("id", id)).map { Unit }

    suspend fun deleteProfile(id: String): Result<Unit> =
        client.deleteJson("/api/v1/ai/config/profiles", JSONObject().put("id", id)).map { Unit }

    suspend fun save(endpoint: String, imageModel: String, apiKey: String): Result<AiProviderConfiguration> =
        client.postJson(
            "/api/v1/ai/config",
            JSONObject().put("endpoint", endpoint).put("model", imageModel).put("api_key", apiKey),
        ).mapCatching { root ->
            val data = root.getJSONObject("data")
            AiProviderConfiguration(
                configured = data.optBoolean("configured", false),
                endpoint = data.optString("endpoint", ""),
                imageModel = data.optString("model", ""),
                keyLast4 = data.optString("key_last4", "").takeIf { it.length == 4 },
            )
        }

    suspend fun clear(): Result<Unit> = client.deleteJson("/api/v1/ai/config", JSONObject()).map { Unit }

    suspend fun testConnection(allowBillableTest: Boolean): Result<AiConnectionTest> =
        client.postJson("/api/v1/ai/config/test", JSONObject().put("allow_billable_test", allowBillableTest)).mapCatching { root ->
            val data = root.optJSONObject("data")
            AiConnectionTest(
                code = root.optString("code", "ai_service_unavailable"),
                networkReachable = data?.optBoolean("network_reachable", false) == true,
                endpointReachable = data?.optBoolean("endpoint_reachable", false) == true,
                authenticated = data?.optBoolean("authenticated", false) == true,
                modelAvailable = data?.optBoolean("model_available", false) == true,
                providerMessage = data?.optString("provider_message", "").orEmpty(),
            )
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
