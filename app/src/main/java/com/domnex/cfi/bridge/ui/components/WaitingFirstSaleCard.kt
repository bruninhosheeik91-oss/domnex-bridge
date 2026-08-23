package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextSecondary

@Composable
fun WaitingFirstSaleCard(
    isMonitoring: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(color = Color.White.copy(alpha = 0.20f), cornerRadius = 24.dp)
            .background(color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .padding(horizontal = 20.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color = SuccessGreen.copy(alpha = 0.10f), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            BridgeStatusDot(color = SuccessGreen, size = 14.dp, glowRadius = 8.dp)
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = "PRONTO PARA CAPTURAR",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp),
            color = Gold,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Aguardando primeira venda",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "O Domnex Bridge está pronto. Quando uma nova venda for identificada na TON, ela aparecerá aqui.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        if (isMonitoring) {
            Spacer(Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = Color.White.copy(alpha = 0.05f),
                        shape = CircleShape
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                BridgeStatusDot(color = SuccessGreen, size = 7.dp, glowRadius = 5.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Leitura da TON em segundo plano ativa",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
