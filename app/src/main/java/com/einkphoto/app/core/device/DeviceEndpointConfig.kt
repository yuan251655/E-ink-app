package com.einkphoto.app.core.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.einkphoto.app.BuildConfig
import java.net.InetAddress
import java.net.URI

/**
 * Single source for the device API endpoint.
 *
 * Release always compiles the device AP default. Debug may inject a local emulator bridge through
 * `-Peink.debugDeviceApiBaseUrl=...`; invalid values fail closed to the same AP default and never
 * appear in user-facing UI.
 */
object DeviceEndpointConfig {
    private const val defaultApBaseUrl = "http://192.168.4.1"
    private const val preferenceName = "device_endpoint"
    private const val activePreferenceKey = "active_api_base_url"
    private const val staPreferenceKey = "sta_api_base_url"
    @Volatile private var preferences: android.content.SharedPreferences? = null
    @Volatile private var applicationContext: Context? = null

    /** Call once at process startup.  Requests always read the current saved value. */
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        preferences = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    }

    val apiBaseUrl: String
        get() = (preferences?.getString(activePreferenceKey, null) ?: BuildConfig.DEVICE_API_BASE_URL)
            .trimEnd('/')
            .takeIf(::isSafeBaseUrl)
            ?: defaultApBaseUrl

    /**
     * Try the last working address first. On a normal STA session, the remembered LAN address
     * must come before the AP recovery address: a missing AP otherwise adds a timeout to every
     * serial gallery-preview download.
     */
    val endpointCandidates: List<String>
        get() = buildList {
            val rememberedSta = preferences?.getString(staPreferenceKey, null)
                ?.trimEnd('/')
                ?.takeIf(::isSafeBaseUrl)
            // Prefer the endpoint that is on the phone's current Wi-Fi subnet.
            // This avoids waiting for an old STA address on the AP, while normal
            // same-LAN use still prefers the frame's STA address over its AP.
            when {
                isEndpointOnActiveWifi(defaultApBaseUrl) -> add(defaultApBaseUrl)
                rememberedSta != null && isEndpointOnActiveWifi(rememberedSta) -> add(rememberedSta)
            }
            add(apiBaseUrl)
            rememberedSta?.let(::add)
            add(defaultApBaseUrl)
        }.distinct()

    fun saveApiBaseUrl(value: String): Boolean {
        val safeValue = value.trimEnd('/').takeIf(::isSafeBaseUrl) ?: return false
        preferences?.edit()?.putString(activePreferenceKey, safeValue)?.apply()
        return preferences != null
    }

    /** A STA IP comes only from the device's own network-status response. */
    fun rememberStaAddress(ipAddress: String): Boolean {
        val safeValue = "http://${ipAddress.trim()}".trimEnd('/').takeIf(::isSafeBaseUrl) ?: return false
        preferences?.edit()?.putString(staPreferenceKey, safeValue)?.apply()
        return preferences != null
    }

    fun markEndpointReachable(value: String): Boolean = saveApiBaseUrl(value)

    fun useApAddress(): Boolean = saveApiBaseUrl(defaultApBaseUrl)

    private fun isEndpointOnActiveWifi(endpoint: String): Boolean {
        val target = runCatching { InetAddress.getByName(URI(endpoint).host) }.getOrNull() ?: return false
        val connectivity = applicationContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivity.activeNetwork ?: return false
        if (connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) != true) return false
        return connectivity.getLinkProperties(network)
            ?.linkAddresses
            ?.any { address -> sameSubnet(address.address.address, target.address, address.prefixLength) }
            ?: false
    }

    private fun sameSubnet(local: ByteArray, target: ByteArray, prefixLength: Int): Boolean {
        if (local.size != target.size || prefixLength !in 0..(local.size * 8)) return false
        var remaining = prefixLength
        for (index in local.indices) {
            if (remaining == 0) return true
            val mask = if (remaining >= 8) 0xff else 0xff shl (8 - remaining)
            if ((local[index].toInt() and mask) != (target[index].toInt() and mask)) return false
            remaining -= 8
        }
        return true
    }

    private fun isSafeBaseUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/")
    }.getOrDefault(false)
}
