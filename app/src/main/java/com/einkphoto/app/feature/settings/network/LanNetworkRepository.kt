package com.einkphoto.app.feature.settings.network

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import com.einkphoto.app.core.device.DeviceEndpointConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Reads only the device-authoritative network snapshot. Scan/write APIs wait for firmware support. */
class LanNetworkRepository(private val client: DevelopmentApHttpClient = DevelopmentApHttpClient()) : NetworkRepository {
    private val mutableSnapshot = MutableStateFlow(NetworkSnapshot("v1", "unknown", 0,
        ApStatus(false, "未连接", "—", 0, 0), StaStatus(false, StaState.Disabled, null, null, null, null), InternetState.Unknown))
    override val snapshot: StateFlow<NetworkSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refresh(): NetworkActionResult = client.get("/api/v1/network/status").fold(
        onSuccess = { root ->
            val data = root.getJSONObject("data"); val ap = data.getJSONObject("ap"); val sta = data.getJSONObject("sta")
            val state = when (sta.optString("state")) { "connected" -> StaState.Connected; "connecting" -> StaState.Connecting; "failed" -> StaState.Failed; else -> StaState.Disabled }
            val internet = when (data.optJSONObject("internet")?.optString("state")) { "reachable" -> InternetState.Reachable; "unreachable" -> InternetState.Unreachable; else -> InternetState.Unknown }
            val staIp = sta.optString("ip").takeIf { it.isNotBlank() }
            if (state == StaState.Connected && staIp != null) DeviceEndpointConfig.rememberStaAddress(staIp)
            mutableSnapshot.value = NetworkSnapshot(data.optString("api_version", "v1"), data.optString("device_id", "unknown"), data.optLong("revision", 0),
                ApStatus(ap.optBoolean("enabled"), ap.optString("ssid"), ap.optString("ip"), ap.optInt("channel"), ap.optInt("connected_clients")),
                StaStatus(sta.optBoolean("enabled"), state, sta.optString("ssid").takeIf { it.isNotBlank() }, staIp, sta.optString("gateway").takeIf { it.isNotBlank() }, if (sta.has("rssi_dbm")) sta.optInt("rssi_dbm") else null, sta.optString("last_error_code").takeIf { it.isNotBlank() }), internet)
            NetworkActionResult.Accepted
        }, onFailure = { NetworkActionResult.Rejected("network_unavailable", "无法读取设备网络状态：请先连接 esp_network") })

    override suspend fun scan24Ghz(): Result<List<WifiNetwork>> = client.postJson("/api/v1/network/scan", JSONObject()).map { root ->
        val items = root.getJSONObject("data").optJSONArray("networks")
        buildList {
            for (index in 0 until (items?.length() ?: 0)) {
                val item = items?.optJSONObject(index) ?: continue
                val ssid = item.optString("ssid").trim()
                if (ssid.isBlank()) continue
                add(WifiNetwork(ssid, item.optInt("rssi_dbm"), item.optInt("channel"), when (item.optString("security")) {
                    "open" -> WifiSecurity.Open; "wpa3" -> WifiSecurity.Wpa3; else -> WifiSecurity.Wpa2
                }))
            }
        }
    }

    override suspend fun testAndSaveSta(draft: StaConfigDraft): NetworkActionResult {
        if (draft.ssid.isBlank() || draft.ssid.length > 32 || draft.password.length !in 8..63) return NetworkActionResult.Rejected("invalid_request", "请输入 2.4 GHz Wi-Fi 名称和至少 8 位密码")
        return client.postJson("/api/v1/network/sta", JSONObject().put("ssid", draft.ssid).put("password", draft.password)).fold(
            onSuccess = { refresh() }, onFailure = { NetworkActionResult.Rejected("sta_connect_failed", "未能连接此 Wi-Fi；原有网络配置保持不变") },
        )
    }

    override suspend fun disableSta(): NetworkActionResult = client.deleteJson("/api/v1/network/sta", JSONObject()).fold(
        onSuccess = { refresh() }, onFailure = { NetworkActionResult.Rejected("sta_forget_failed", "无法清除已保存的 Wi-Fi 配置") },
    )

    override suspend fun saveAp(draft: ApConfigDraft): NetworkActionResult {
        if (draft.ssid.length !in 1..32 || draft.password.length !in 8..63) return NetworkActionResult.Rejected("invalid_request", "热点名称需为 1–32 位，密码需为 8–63 位")
        return client.postJson("/api/v1/network/ap", JSONObject().put("ssid", draft.ssid).put("password", draft.password)).fold(
            onSuccess = { refresh() }, onFailure = { NetworkActionResult.Rejected("ap_save_failed", "热点设置未保存，请保持与相框连接后重试") },
        )
    }

    override suspend fun restoreDefaultAp(): NetworkActionResult = client.postJson("/api/v1/network/ap/restore-default", JSONObject()).fold(
        onSuccess = { refresh() }, onFailure = { NetworkActionResult.Rejected("ap_restore_failed", "无法恢复默认热点") },
    )
}
