
package com.baboons.vendedorescalle.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.ui.components.BrandLockup
import com.baboons.vendedorescalle.ui.components.ErrorBanner
import com.baboons.vendedorescalle.ui.components.PrimaryButton

@Composable
fun LoginScreen(
    email: String,
    onEmail: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    busy: Boolean,
    error: String?,
    onLogin: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandLockup()
        Spacer(Modifier.height(28.dp))
        Text("Iniciar sesion", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Ingresa con tu usuario de Baboons",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            email, onEmail,
            Modifier.fillMaxWidth(),
            label = { Text("Correo") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            password, onPassword,
            Modifier.fillMaxWidth(),
            label = { Text("Contrasena") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            visualTransformation = PasswordVisualTransformation()
        )
        error?.let {
            Spacer(Modifier.height(16.dp))
            ErrorBanner(it)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton(
            text = "Entrar",
            onClick = onLogin,
            enabled = email.isNotBlank() && password.isNotBlank(),
            busy = busy
        )
    }
}
