package com.einkphoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.einkphoto.app.core.device.DeviceEndpointConfig
import com.einkphoto.app.core.device.DeviceLanNetworkBinder
import com.einkphoto.app.ui.EInkPhotoApp
import com.einkphoto.app.feature.aialbum.VoiceGenerationServiceController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceEndpointConfig.initialize(applicationContext)
        DeviceLanNetworkBinder.bindPreferredWifi(applicationContext)
        VoiceGenerationServiceController.restoreIfEnabled(applicationContext)
        enableEdgeToEdge()
        setContent { EInkPhotoApp() }
    }

    override fun onResume() {
        super.onResume()
        DeviceLanNetworkBinder.bindPreferredWifi(applicationContext)
    }
}
