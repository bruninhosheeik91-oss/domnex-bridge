package com.domnex.cfi.bridge.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.domnex.cfi.bridge.auth.UserAccount
import com.domnex.cfi.bridge.auth.UserRole
import com.domnex.cfi.bridge.auth.UserStatus
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import com.domnex.cfi.bridge.ui.theme.WarningAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        fontWeight = FontWeight.Bold,
        modifier = modifier
    )
}

@Composable
fun FieldRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth()) {
        SectionCaption(label)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun DevDataBadge(modifier: Modifier = Modifier) {
    StatusBadge(text = "DADOS DE DESENVOLVIMENTO", tone = BadgeTone.Warning, modifier = modifier)
}

fun statusColor(status: UserStatus): Color = when (status) {
    UserStatus.ACTIVE -> SuccessGreen
    UserStatus.PENDING -> WarningAmber
    UserStatus.SUSPENDED -> FailureRose
}

fun statusLabel(status: UserStatus): String = when (status) {
    UserStatus.ACTIVE -> "Ativo"
    UserStatus.PENDING -> "Pendente"
    UserStatus.SUSPENDED -> "Suspenso"
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(millis))

@Composable
fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (selected) Gold else TextSecondary
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) Gold.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f),
        contentColor = accent,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) Gold.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.12f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun UserListItem(
    user: UserAccount,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = Color.White.copy(alpha = 0.04f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.07f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BridgeStatusDot(color = statusColor(user.status), size = 8.dp, glowRadius = 4.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = user.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                val linked = user.clientName
                if (linked != null && user.role == UserRole.CLIENT) {
                    Text(text = linked, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(text = user.role.name, tone = BadgeTone.Neutral)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusLabel(user.status),
                    style = MaterialTheme.typography.labelMedium,
                    color = statusColor(user.status),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatDate(user.createdAtMillis),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = TextMuted
                )
            }
        }
    }
}
