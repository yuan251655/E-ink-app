package com.einkphoto.app.feature.aialbum

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import org.json.JSONObject

data class AiProviderConfiguration(
    val configured: Boolean = false,
    val endpoint: String = "",
    val imageModel: String = "",
    val keyLast4: String? = null,
)

class AiConfigRepository(
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) {
    suspend fun read(): Result<AiProviderConfiguration> = client.get("/api/v1/ai/config").mapCatching { root ->
        val data = root.getJSONObject("data")
        AiProviderConfiguration(
            configured = data.optBoolean("configured", false),
            endpoint = data.optString("endpoint", ""),
            imageModel = data.optString("model", ""),
            keyLast4 = data.optString("key_last4", "").takeIf { it.length == 4 },
        )
    }

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

    suspend fun testConnection(): Result<AiConnectionTest> =
        client.postJson("/api/v1/ai/config/test", JSONObject()).mapCatching { root ->
            val data = root.optJSONObject("data")
            AiConnectionTest(
                code = root.optString("code", "ai_service_unavailable"),
                endpointReachable = data?.optBoolean("endpoint_reachable", false) == true,
                authenticated = data?.optBoolean("authenticated", false) == true,
                modelAvailable = data?.optBoolean("model_available", false) == true,
            )
        }
}

data class AiConnectionTest(
    val code: String,
    val endpointReachable: Boolean,
    val authenticated: Boolean,
    val modelAvailable: Boolean,
)
