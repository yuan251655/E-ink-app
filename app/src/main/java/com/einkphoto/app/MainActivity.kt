package com.einkphoto.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.einkphoto.app.core.device.DeviceEndpointConfig
import com.einkphoto.app.ui.EInkPhotoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DeviceEndpointConfig.initialize(applicationContext)
        enableEdgeToEdge()
        setContent { EInkPhotoApp() }
    }
}
