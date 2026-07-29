package com.einkphoto.app.feature.settings.network

import com.einkphoto.app.core.device.DevelopmentApHttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
            mutableSnapshot.value = NetworkSnapshot(data.optString("api_version", "v1"), data.optString("device_id", "unknown"), data.optLong("revision", 0),
                ApStatus(ap.optBoolean("enabled"), ap.optString("ssid"), ap.optString("ip"), ap.optInt("channel"), ap.optInt("connected_clients")),
                StaStatus(sta.optBoolean("enabled"), state, sta.optString("ssid").takeIf { it.isNotBlank() }, sta.optString("ip").takeIf { it.isNotBlank() }, sta.optString("gateway").takeIf { it.isNotBlank() }, if (sta.has("rssi_dbm")) sta.optInt("rssi_dbm") else null, sta.optString("last_error_code").takeIf { it.isNotBlank() }), internet)
            NetworkActionResult.Accepted
        }, onFailure = { NetworkActionResult.Rejected("network_unavailable", "无法读取设备网络状态：请先连接 esp_network") })

    override suspend fun scan24Ghz(): Result<List<WifiNetwork>> = Result.failure(UnsupportedOperationException("设备端扫描接口尚未实现"))
    override suspend fun testAndSaveSta(draft: StaConfigDraft) = NetworkActionResult.Rejected("unsupported", "设备端 STA 配置接口尚未实现")
    override suspend fun disableSta() = NetworkActionResult.Rejected("unsupported", "设备端 STA 配置接口尚未实现")
    override suspend fun saveAp(draft: ApConfigDraft) = NetworkActionResult.Rejected("unsupported", "设备端 AP 配置接口尚未实现")
    override suspend fun restoreDefaultAp() = NetworkActionResult.Rejected("unsupported", "设备端 AP 配置接口尚未实现")
}
