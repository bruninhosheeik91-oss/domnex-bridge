package com.domnex.cfi.bridge.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.auth.AuthResult
import com.domnex.cfi.bridge.auth.LocalAuthGateway
import com.domnex.cfi.bridge.ui.components.BridgeStatusDot
import com.domnex.cfi.bridge.ui.components.BridgeTextField
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.NavyCard
import com.domnex.cfi.bridge.ui.theme.SuccessGreen
import com.domnex.cfi.bridge.ui.theme.TextSecondary

@Composable
fun LoginScreen(
    onAuthenticated: (com.domnex.cfi.bridge.auth.AuthSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var showError by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        focusManager.clearFocus()
        when (val result = LocalAuthGateway.login(email.trim(), password)) {
            is AuthResult.Authorized -> onAuthenticated(result.session)
            AuthResult.Rejected -> showError = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(color = NavyCard, shape = RoundedCornerShape(20.dp))
                    .border(1.dp, Gold.copy(alpha = 0.35f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                BridgeStatusDot(color = Gold, size = 20.dp, glowRadius = 10.dp)
            }
            BridgeStatusDot(
                color = SuccessGreen,
                size = 12.dp,
                glowRadius = 5.dp,
                modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "BY DOMNEX TECH",
            style = MicroCaps,
            color = Gold.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(4.dp))
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Acesse com sua conta Domnex Bridge.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(Modifier.height(28.dp))

        if (showError) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = FailureRose.copy(alpha = 0.10f),
                        shape = MaterialTheme.shapes.small
                    )
                    .border(
                        1.dp,
                        FailureRose.copy(alpha = 0.30f),
                        MaterialTheme.shapes.small
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Verifique seu e-mail e senha.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FailureRose,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        BridgeTextField(
            value = email,
            onValueChange = {
                email = it
                showError = false
            },
            placeholder = "E-mail",
            keyboardType = KeyboardType.Email,
            modifier = Modifier.fillMaxWidth(),
            trailing = null
        )

        Spacer(Modifier.height(12.dp))

        val passwordTrailing: @Composable () -> Unit = {
            TextButton(onClick = { showPassword = !showPassword }) {
                Text(
                    text = if (showPassword) "Ocultar" else "Mostrar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        BridgeTextField(
            value = password,
            onValueChange = {
                password = it
                showError = false
            },
            placeholder = "Senha",
            keyboardType = KeyboardType.Password,
            visualTransformation = if (showPassword) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailing = passwordTrailing,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(22.dp))
        GoldPrimaryButton(
            text = "ENTRAR",
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank() && password.isNotBlank()
        )

        Spacer(Modifier.height(14.dp))
        TextButton(onClick = { }) {
            Text(
                text = "Esqueci minha senha",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        TextButton(onClick = { }) {
            Text(
                text = "Ativar Domnex Bridge",
                style = MaterialTheme.typography.bodyMedium,
                color = Gold,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
