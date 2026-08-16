package com.einkphoto.app.feature.settings.network

enum class StaState { Disabled, Connecting, Connected, Failed }
enum class InternetState { Unknown, Checking, Reachable, Unreachable }
enum class WifiSecurity { Open, Wpa2, Wpa3 }

data class ApStatus(val enabled: Boolean, val ssid: String, val ip: String, val channel: Int, val clientCount: Int)
data class StaStatus(val enabled: Boolean, val state: StaState, val ssid: String?, val ip: String?, val gateway: String?, val rssiDbm: Int?, val errorCode: String? = null)
data class SavedWifiNetwork(val ssid: String, val active: Boolean)
data class WifiNetwork(val ssid: String, val rssiDbm: Int, val channel: Int, val security: WifiSecurity)
data class NetworkSnapshot(val apiVersion: String, val deviceId: String, val revision: Long, val ap: ApStatus, val sta: StaStatus, val internet: InternetState, val savedNetworks: List<SavedWifiNetwork> = emptyList())
data class StaConfigDraft(val ssid: String, val password: String)
data class ApConfigDraft(val ssid: String, val password: String)
