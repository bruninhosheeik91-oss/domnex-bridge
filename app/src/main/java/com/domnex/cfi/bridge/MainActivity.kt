package com.domnex.cfi.bridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.service.BridgeForegroundService
import com.domnex.cfi.bridge.service.BridgeMonitor
import com.domnex.cfi.bridge.ui.screens.MainScreen
import com.domnex.cfi.bridge.ui.theme.CFIBridgeTheme
import com.domnex.cfi.bridge.update.UpdateProvider

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AuthProvider.init(applicationContext)
        UpdateProvider.init(applicationContext)
        // bridge_enabled = false (usuário pausou) -> não iniciar o monitoramento.
        BridgeMonitor.refresh(this)
        if (BridgeMonitor.isActive()) {
            BridgeForegroundService.start(this)
        }
        setContent {
            CFIBridgeTheme {
                MainScreen()
            }
        }
    }
}
