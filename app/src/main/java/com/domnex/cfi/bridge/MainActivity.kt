package com.domnex.cfi.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.domnex.cfi.bridge.auth.LocalAuthGateway
import com.domnex.cfi.bridge.service.BridgeForegroundService
import com.domnex.cfi.bridge.ui.screens.MainScreen
import com.domnex.cfi.bridge.ui.theme.CFIBridgeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        LocalAuthGateway.init(applicationContext)
        BridgeForegroundService.start(this)
        setContent {
            CFIBridgeTheme {
                MainScreen()
            }
        }
    }
}
