package com.einkphoto.app.feature.settings.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeNetworkRepository : NetworkRepository {
    private val mutableSnapshot = MutableStateFlow(
        NetworkSnapshot("v1", "fake-photopainter-001", 1,
            ApStatus(true, "esp_network", "192.168.4.1", 6, 1),
            StaStatus(false, StaState.Disabled, null, null, null, null), InternetState.Unknown),
    )
    override val snapshot: StateFlow<NetworkSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun refresh() = NetworkActionResult.Accepted

    override suspend fun scan24Ghz(): Result<List<WifiNetwork>> = Result.success(listOf(
        WifiNetwork("Studio-2.4G", -46, 6, WifiSecurity.Wpa2),
        WifiNetwork("Lab-IoT", -63, 11, WifiSecurity.Wpa3),
        WifiNetwork("Guest-2.4G", -72, 1, WifiSecurity.Open),
    ))

    override suspend fun testAndSaveSta(draft: StaConfigDraft): NetworkActionResult {
        if (draft.ssid.isBlank()) return NetworkActionResult.Rejected("invalid_request", "请选择 2.4 GHz Wi-Fi")
        if (draft.password == "wrong") return NetworkActionResult.Rejected("sta_connect_failed", "密码错误；原 STA 配置未改变，AP 仍可用")
        val old = mutableSnapshot.value
        val profiles = (old.savedNetworks.filterNot { it.ssid == draft.ssid } + SavedWifiNetwork(draft.ssid, true)).map { it.copy(active = it.ssid == draft.ssid) }
        mutableSnapshot.value = old.copy(revision = old.revision + 1, sta = StaStatus(true, StaState.Connected, draft.ssid, "192.168.1.88", "192.168.1.1", -48), internet = InternetState.Reachable, savedNetworks = profiles)
        return NetworkActionResult.Accepted
    }

    override suspend fun activateSavedSta(ssid: String): NetworkActionResult {
        val old = mutableSnapshot.value
        if (old.savedNetworks.none { it.ssid == ssid }) return NetworkActionResult.Rejected("saved_network_unavailable", "未找到此 Wi-Fi")
        mutableSnapshot.value = old.copy(revision = old.revision + 1, sta = old.sta.copy(state = StaState.Connected, ssid = ssid), savedNetworks = old.savedNetworks.map { it.copy(active = it.ssid == ssid) })
        return NetworkActionResult.Accepted
    }

    override suspend fun forgetSavedSta(ssid: String): NetworkActionResult {
        val old = mutableSnapshot.value
        val deletingActive = old.savedNetworks.firstOrNull { it.ssid == ssid }?.active == true
        mutableSnapshot.value = old.copy(revision = old.revision + 1, sta = if (deletingActive) StaStatus(false, StaState.Disabled, null, null, null, null) else old.sta, savedNetworks = old.savedNetworks.filterNot { it.ssid == ssid })
        return NetworkActionResult.Accepted
    }

    override suspend fun disableSta(): NetworkActionResult {
        val old = mutableSnapshot.value
        mutableSnapshot.value = old.copy(revision = old.revision + 1, sta = StaStatus(false, StaState.Disabled, null, null, null, null), internet = InternetState.Unknown, savedNetworks = emptyList())
        return NetworkActionResult.Accepted
    }

    override suspend fun saveAp(draft: ApConfigDraft): NetworkActionResult {
        if (draft.ssid.length !in 1..32) return NetworkActionResult.Rejected("invalid_request", "AP SSID 长度应为 1–32 个字符；旧配置未改变")
        val old = mutableSnapshot.value
        mutableSnapshot.value = old.copy(revision = old.revision + 1, ap = old.ap.copy(ssid = draft.ssid, channel = 11))
        return NetworkActionResult.Accepted
    }

    override suspend fun restoreDefaultAp(): NetworkActionResult {
        val old = mutableSnapshot.value
        mutableSnapshot.value = old.copy(revision = old.revision + 1, ap = ApStatus(true, "esp_network", "192.168.4.1", 6, old.ap.clientCount))
        return NetworkActionResult.Accepted
    }
}
