package com.domnex.cfi.bridge.ui.screens.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.auth.AccessFilter
import com.domnex.cfi.bridge.auth.LocalUserDirectory
import com.domnex.cfi.bridge.ui.components.BridgeTextField
import com.domnex.cfi.bridge.ui.components.DevDataBadge
import com.domnex.cfi.bridge.ui.components.FilterPill
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.components.SectionCaption
import com.domnex.cfi.bridge.ui.components.UserListItem
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.TextMuted
import com.domnex.cfi.bridge.ui.theme.TextSecondary

@Composable
fun AccessManagementScreen(
    onBack: () -> Unit,
    onCreateAccess: () -> Unit,
    onOpenUser: (String) -> Unit,
    dataVersion: Int = 0,
    modifier: Modifier = Modifier
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filterIndex by rememberSaveable { mutableIntStateOf(0) }
    val filters = listOf(
        "Todos" to AccessFilter.ALL,
        "Clientes" to AccessFilter.CLIENTS,
        "Administradores" to AccessFilter.ADMINS,
        "Suspensos" to AccessFilter.SUSPENDED
    )
    val activeFilter = filters[filterIndex].second

    val users = remember(dataVersion, query, activeFilter) {
        LocalUserDirectory.listUsers(query = query, filter = activeFilter)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = onBack,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.05f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
            ) {
                Text(
                    text = "←",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                text = "Acessos · Usuários",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(14.dp))
        DevDataBadge()

        Spacer(Modifier.height(16.dp))
        BridgeTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Buscar usuário",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEachIndexed { index, (label, _) ->
                FilterPill(
                    text = label,
                    selected = index == filterIndex,
                    onClick = { filterIndex = index }
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        GoldPrimaryButton(
            text = "NOVO ACESSO",
            onClick = onCreateAccess,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(18.dp))
        SectionCaption("USUÁRIOS (${users.size})")
        Spacer(Modifier.height(10.dp))

        if (users.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Nenhum usuário encontrado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMuted
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)
            ) {
                items(users, key = { it.id }) { user ->
                    UserListItem(user = user, onClick = { onOpenUser(user.id) })
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Preferimos suspender acessos a excluí-los — o histórico fica preservado.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Perfis: CLIENT · DOMNEX_ADMIN",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
            color = Gold.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(16.dp))
    }
}
