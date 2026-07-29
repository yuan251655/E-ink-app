package com.einkphoto.app.core.device

import android.content.Context
import com.einkphoto.app.BuildConfig
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
    private const val preferenceKey = "api_base_url"
    @Volatile private var preferences: android.content.SharedPreferences? = null

    /** Call once at process startup.  Requests always read the current saved value. */
    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    }

    val apiBaseUrl: String
        get() = (preferences?.getString(preferenceKey, null) ?: BuildConfig.DEVICE_API_BASE_URL)
            .trimEnd('/')
            .takeIf(::isSafeBaseUrl)
            ?: defaultApBaseUrl

    fun saveApiBaseUrl(value: String): Boolean {
        val safeValue = value.trimEnd('/').takeIf(::isSafeBaseUrl) ?: return false
        preferences?.edit()?.putString(preferenceKey, safeValue)?.apply()
        return preferences != null
    }

    fun useApAddress(): Boolean = saveApiBaseUrl(defaultApBaseUrl)

    private fun isSafeBaseUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") &&
            !uri.host.isNullOrBlank() &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            (uri.path.isNullOrEmpty() || uri.path == "/")
    }.getOrDefault(false)
}
