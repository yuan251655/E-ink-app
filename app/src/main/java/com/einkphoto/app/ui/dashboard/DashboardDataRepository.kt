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
        return DashboardDocument(
            revision = data.optLong("revision", 0L),
            layoutId = data.optString("layout_id", "weather_memo_todo"),
            timezone = data.optString("timezone", "Asia/Shanghai"),
            cityName = data.optJSONObject("location")?.optString("city_name").orEmpty(),
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
        )
    }
}

internal data class DashboardDocument(
    val revision: Long,
    val layoutId: String,
    val timezone: String,
    val cityName: String,
    val memo: String,
    val todos: List<DashboardTodoRecord>,
) {
    fun toPatchJson(): JSONObject = JSONObject()
        .put("layout_id", layoutId)
        .put("timezone", timezone)
        .put("location", JSONObject().put("city_name", cityName))
        .put("memo", JSONObject().put("text", memo))
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
