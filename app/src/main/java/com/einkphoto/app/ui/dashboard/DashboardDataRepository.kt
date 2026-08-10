package com.einkphoto.app.ui.dashboard

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Device-authoritative dashboard settings. The e-paper refresh is deliberately a separate step. */
internal class DashboardDataRepository(
    private val client: DevelopmentApHttpClient = DevelopmentApHttpClient(),
) {
    suspend fun load(): Result<DashboardDocument> = client.get("/api/v1/dashboard").map { root ->
        parse(root.getJSONObject("data"))
    }

    suspend fun save(document: DashboardDocument): DashboardSaveResult = client.postJson(
        "/api/v1/dashboard",
        JSONObject()
            .put("request_id", "dashboard-${UUID.randomUUID()}".take(64))
            .put("expected_revision", document.revision)
            .put("patch", document.toPatchJson()),
    ).fold(
        onSuccess = { DashboardSaveResult.Saved(parse(it.getJSONObject("data"))) },
        onFailure = { error ->
            if (error.message?.contains("dashboard_revision_conflict", ignoreCase = true) == true) {
                load().fold(
                    onSuccess = { DashboardSaveResult.Conflict(it) },
                    onFailure = { DashboardSaveResult.Conflict(null) },
                )
            } else {
                DashboardSaveResult.Failed(error.message ?: "dashboard_save_failed")
            }
        },
    )

    private fun parse(data: JSONObject): DashboardDocument {
        val todos = data.optJSONArray("todos") ?: JSONArray()
        val location = data.optJSONObject("location") ?: JSONObject()
        val weatherJson = data.optJSONObject("weather") ?: JSONObject()
        val autoRefreshJson = data.optJSONObject("auto_refresh") ?: JSONObject()
        val forecast = weatherJson.optJSONArray("forecast") ?: JSONArray()
        return DashboardDocument(
            revision = data.optLong("revision", 0L),
            layoutId = data.optString("layout_id", "weather_memo_todo"),
            timezone = data.optString("timezone", "Asia/Shanghai"),
            cityName = location.optString("city_name"),
            latitude = location.optionalDouble("latitude"),
            longitude = location.optionalDouble("longitude"),
            memo = data.optJSONObject("memo")?.optString("text").orEmpty(),
            todos = buildList {
                for (index in 0 until todos.length()) {
                    val item = todos.optJSONObject(index) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isNotEmpty()) add(
                        DashboardTodoRecord(
                            id = id,
                            title = item.optString("title"),
                            completed = item.optBoolean("completed"),
                            position = item.optInt("position", index),
                        ),
                    )
                }
            },
            weather = DashboardWeather(
                state = weatherJson.optString("state", "waiting_location"),
                refreshing = weatherJson.optBoolean("refreshing"),
                lastSuccessAt = weatherJson.optionalLong("last_success_at"),
                lastErrorCode = weatherJson.optString("last_error_code").takeIf { it.isNotBlank() && it != "null" },
                forecast = buildList {
                    for (index in 0 until forecast.length()) {
                        val day = forecast.optJSONObject(index) ?: continue
                        val date = day.optString("date").trim()
                        if (date.isNotEmpty()) add(
                            DashboardWeatherDay(
                                date = date,
                                weatherCode = day.optInt("weather_code", -1),
                                temperatureMinC = day.optInt("temperature_min_c"),
                                temperatureMaxC = day.optInt("temperature_max_c"),
                            ),
                        )
                    }
                },
            ),
            autoRefreshEnabled = autoRefreshJson.optBoolean("enabled", false),
            autoRefreshIntervalSeconds = autoRefreshJson.optInt("interval_seconds", 3 * 60 * 60),
            nextAutoRefreshAt = autoRefreshJson.optionalLong("next_refresh_at"),
        )
    }
}

private fun JSONObject.optionalDouble(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name).takeIf { it.isFinite() }

private fun JSONObject.optionalLong(name: String): Long? =
    if (!has(name) || isNull(name)) null else optLong(name)

internal data class DashboardDocument(
    val revision: Long,
    val layoutId: String,
    val timezone: String,
    val cityName: String,
    val latitude: Double?,
    val longitude: Double?,
    val memo: String,
    val todos: List<DashboardTodoRecord>,
    val weather: DashboardWeather,
    val autoRefreshEnabled: Boolean = false,
    val autoRefreshIntervalSeconds: Int = 3 * 60 * 60,
    val nextAutoRefreshAt: Long? = null,
) {
    fun toPatchJson(): JSONObject = JSONObject()
        .put("layout_id", layoutId)
        .put("timezone", timezone)
        .put(
            "location",
            JSONObject()
                .put("city_name", cityName)
                .put("latitude", latitude ?: JSONObject.NULL)
                .put("longitude", longitude ?: JSONObject.NULL),
        )
        .put("memo", JSONObject().put("text", memo))
        .put(
            "auto_refresh",
            JSONObject()
                .put("enabled", autoRefreshEnabled)
                .put("interval_seconds", autoRefreshIntervalSeconds),
        )
        .put("todos", JSONArray().also { output ->
            todos.forEachIndexed { index, todo ->
                output.put(
                    JSONObject()
                        .put("id", todo.id)
                        .put("title", todo.title)
                        .put("completed", todo.completed)
                        .put("position", index),
                )
            }
        })
}

internal data class DashboardWeather(
    val state: String = "waiting_location",
    val refreshing: Boolean = false,
    val lastSuccessAt: Long? = null,
    val lastErrorCode: String? = null,
    val forecast: List<DashboardWeatherDay> = emptyList(),
)

internal data class DashboardWeatherDay(
    val date: String,
    val weatherCode: Int,
    val temperatureMinC: Int,
    val temperatureMaxC: Int,
)

internal data class DashboardTodoRecord(
    val id: String,
    val title: String,
    val completed: Boolean,
    val position: Int,
)

internal sealed interface DashboardSaveResult {
    data class Saved(val document: DashboardDocument) : DashboardSaveResult
    data class Conflict(val latest: DashboardDocument?) : DashboardSaveResult
    data class Failed(val code: String) : DashboardSaveResult
}
