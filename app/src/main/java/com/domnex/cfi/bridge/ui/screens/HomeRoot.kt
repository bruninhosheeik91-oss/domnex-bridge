package com.domnex.cfi.bridge.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun HomeRoot(
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showConfig by rememberSaveable { mutableStateOf(false) }
    if (showConfig) {
        BridgeConfigScreen(
            onBack = { showConfig = false },
            onLogout = onLogout,
            modifier = modifier
        )
    } else {
        HomeScreen(onOpenConfig = { showConfig = true })
    }
}
