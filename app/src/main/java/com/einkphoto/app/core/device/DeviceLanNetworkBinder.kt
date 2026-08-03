package com.einkphoto.app.core.device

import android.content.Context
import android.net.ConnectivityManager
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
    fun bindPreferredWifi(context: Context) {
        val connectivity = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        val wifi = connectivity.allNetworks.firstOrNull { network ->
            connectivity.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        }
        connectivity.bindProcessToNetwork(wifi)
    }
}
