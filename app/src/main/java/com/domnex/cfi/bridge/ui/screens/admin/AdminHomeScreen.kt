package com.domnex.cfi.bridge.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.auth.AccessFilter
import com.domnex.cfi.bridge.auth.LocalUserDirectory
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.ui.components.BridgeCard
import com.domnex.cfi.bridge.ui.components.DevDataBadge
import com.domnex.cfi.bridge.ui.components.SectionCaption
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary

private const val DEMO_ACTIVE_BRIDGES = 7

@Composable
fun AdminHomeScreen(
    onOpenAccesses: () -> Unit,
    onLogout: () -> Unit,
    dataVersion: Int = 0,
    modifier: Modifier = Modifier
) {
    val totalAccesses = remember(dataVersion) { LocalUserDirectory.listUsers().size }
    val clientsCount = remember(dataVersion) {
        LocalUserDirectory.listUsers(filter = AccessFilter.CLIENTS).size
    }
    val attentionCount = remember(dataVersion) {
        LocalUserDirectory.listUsers().count { it.status != UserStatus.ACTIVE }
    }
    val demoClients = remember(dataVersion) { LocalUserDirectory.findClientNames() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(30.dp))

        Text(text = "ADMINISTRAÇÃO", style = MicroCaps, color = Gold.copy(alpha = 0.75f))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "Domnex ",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Bridge",
                style = MaterialTheme.typography.headlineMedium,
                color = Gold
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Painel do administrador · perfil DOMNEX_ADMIN",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(14.dp))
        DevDataBadge()
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                label = "CLIENTES",
                value = clientsCount.toString(),
                caption = "clientes na base demo",
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "ACESSOS",
                value = totalAccesses.toString(),
                caption = "usuários com acesso criado",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KpiCard(
                label = "BRIDGES ATIVOS",
                value = DEMO_ACTIVE_BRIDGES.toString(),
                caption = "valor demonstrativo (DEV)",
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                label = "REQUER ATENÇÃO",
                value = attentionCount.toString(),
                caption = "pendentes ou suspensos",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionCaption("ATALHOS RÁPIDOS")
        Spacer(Modifier.height(10.dp))

        ShortcutRow(
            label = "Acessos · Usuários",
            description = "Criar, filtrar e gerenciar acessos",
            enabled = true,
            onClick = onOpenAccesses
        )
        Spacer(Modifier.height(8.dp))
        ShortcutRow(label = "Clientes", description = "Gestão de clientes", enabled = false, onClick = {})
        Spacer(Modifier.height(8.dp))
        ShortcutRow(label = "Bridges ativos", description = "Monitoramento por cliente", enabled = false, onClick = {})
        Spacer(Modifier.height(8.dp))
        ShortcutRow(label = "Diagnóstico", description = "Ferramentas de suporte", enabled = false, onClick = {})

        Spacer(Modifier.height(22.dp))
        BridgeCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "CLIENTES DE TESTE (DEV)",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
                    color = Gold.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                demoClients.forEach { client ->
                    Text(
                        text = "· $client",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onLogout, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(
                text = "Sair da conta",
                style = MaterialTheme.typography.bodyMedium,
                color = FailureRose,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun KpiCard(
    label: String,
    value: String,
    caption: String,
    modifier: Modifier = Modifier
) {
    BridgeCard(modifier = modifier) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = TextMuted,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = FontFamily.Monospace),
                color = Gold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ShortcutRow(
    label: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier else Modifier.alpha(0.45f)),
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.04f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (enabled) Gold else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (!enabled) {
                Text(
                    text = "EM BREVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "→",
                    style = MaterialTheme.typography.titleMedium,
                    color = Gold
                )
            }
        }
    }
}
