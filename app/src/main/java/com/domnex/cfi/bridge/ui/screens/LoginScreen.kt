package com.domnex.cfi.bridge.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.domnex.cfi.bridge.R
import com.domnex.cfi.bridge.auth.AuthProvider
import com.domnex.cfi.bridge.auth.AuthResult
import com.domnex.cfi.bridge.ui.components.BridgeTextField
import com.domnex.cfi.bridge.ui.components.GoldPrimaryButton
import com.domnex.cfi.bridge.ui.theme.FailureRose
import com.domnex.cfi.bridge.ui.theme.Gold
import com.domnex.cfi.bridge.ui.theme.MicroCaps
import com.domnex.cfi.bridge.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    onAuthenticated: (com.domnex.cfi.bridge.auth.AuthSession) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var submitting by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        focusManager.clearFocus()
        if (submitting) return
        submitting = true
        errorMessage = null
        scope.launch {
            // login pode envolver rede (Supabase Auth) -> executa em IO.
            val result = withContext(Dispatchers.IO) {
                AuthProvider.authGateway.login(email.trim(), password)
            }
            when (result) {
                is AuthResult.Authorized -> onAuthenticated(result.session)
                AuthResult.Rejected -> errorMessage = "Verifique seu e-mail e senha."
                is AuthResult.Denied -> errorMessage = result.reason.userMessage()
            }
            submitting = false
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
        // Ícone oficial do app (launcher icon adaptativo):
        // fundo @color/ic_launcher_background + foreground @mipmap/ic_launcher_foreground.
        // O foreground em 108dp sobre a área visível de 72dp reproduz
        // exatamente o recorte exibido pelo launcher.
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(colorResource(id = R.color.ic_launcher_background)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier.size(108.dp)
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

        if (errorMessage != null) {
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
                    text = errorMessage.orEmpty(),
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
                errorMessage = null
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
                errorMessage = null
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
            text = if (submitting) "ENTRANDO..." else "ENTRAR",
            onClick = { submit() },
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank() && password.isNotBlank() && !submitting
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
