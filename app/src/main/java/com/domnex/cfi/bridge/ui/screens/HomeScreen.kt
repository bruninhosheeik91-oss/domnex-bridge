package com.domnex.cfi.bridge.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.service.TonAccessibilityService
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.BridgeSectionTitle
import com.domnex.cfi.bridge.ui.components.LastSaleCard
import com.domnex.cfi.bridge.ui.components.MonitoringCard
import com.domnex.cfi.bridge.ui.components.OperationalHealthIndicator
import com.domnex.cfi.bridge.ui.components.WaitingFirstSaleCard
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.MonoSmall
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextSecondary

@Composable
fun HomeScreen() {
    val isRunning by TonAccessibilityService.isRunning.collectAsState()
    val lastSale by TonAccessibilityService.lastSale.collectAsState()
    val lastLog by TonAccessibilityService.lastLog.collectAsState()
    val context = LocalContext.current

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AmbientOrbs()
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(30.dp))
            HomeHeader()
            Spacer(Modifier.height(24.dp))
            MonitoringCard(
                active = isRunning,
                onOpenAccessibilitySettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
            Spacer(Modifier.height(18.dp))
            OperationalHealthIndicator(isRunning = isRunning, lastLog = lastLog)
            Spacer(Modifier.height(24.dp))

            if (lastSale.hasData) {
                LastSaleCard(sale = lastSale)
            } else {
                WaitingFirstSaleCard(isMonitoring = isRunning)
            }

            if (lastLog.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                MotorLogCard(log = lastLog)
            }

            Spacer(Modifier.height(28.dp))
            BridgeSectionTitle(title = "Configuração")
            Spacer(Modifier.height(12.dp))
            CfiConfigSection()
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "DOMNEX ",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "BRIDGE",
            style = MaterialTheme.typography.headlineMedium,
            color = Gold
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = "BY DOMNEX TECH",
        style = MicroCaps,
        color = Gold.copy(alpha = 0.75f)
    )
}

@Composable
private fun MotorLogCard(log: String) {
    BridgeCard(contentPadding = PaddingValues(14.dp)) {
        Text(
            text = "ÚLTIMO EVENTO DO MOTOR",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
            color = TextSecondary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = log,
            style = MonoSmall.copy(fontFamily = FontFamily.Monospace),
            color = TextSecondary.copy(alpha = 0.85f),
            maxLines = 6,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AmbientOrbs() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = (-50).dp)
                .size(280.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(SuccessGreen.copy(alpha = 0.07f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-80).dp, y = 340.dp)
                .size(300.dp)
                .background(
                    brush = Brush.radialGradient(
                        listOf(Gold.copy(alpha = 0.05f), Color.Transparent)
                    ),
                    shape = CircleShape
                )
        )
    }
}
