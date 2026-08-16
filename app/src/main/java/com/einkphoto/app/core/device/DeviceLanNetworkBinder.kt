package com.einkphoto.app.core.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/**
 * Prefer the user's Wi-Fi transport for private LAN device addresses.
 *
 * Some phones retain mobile data as the process default when Wi-Fi is marked
 * "without Internet". In that state an HTTP request to a 192.168.x.x device
 * can be answered by a carrier/captive-portal HTML page instead of the device
 * API. Binding only when a Wi-Fi network exists keeps AP and STA control on
 * the same LAN and leaves normal mobile fallback available when Wi-Fi is gone.
 */
object DeviceLanNetworkBinder {
    @Volatile private var started = false

    /** Keep the process on the phone's current Wi-Fi after AP/STA switches. */
    fun start(context: Context) {
        if (started) return
        val connectivity = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        started = true
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = bindPreferredWifi(context)
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = bindPreferredWifi(context)
            override fun onLost(network: Network) = bindPreferredWifi(context)
        })
        bindPreferredWifi(context)
    }

    fun bindPreferredWifi(context: Context) {
        val connectivity = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val active = connectivity.activeNetwork
        val wifi = active?.takeIf { network ->
            connectivity.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        connectivity.bindProcessToNetwork(wifi)
    }
}
