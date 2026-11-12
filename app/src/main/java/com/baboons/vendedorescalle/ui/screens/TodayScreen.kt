
package com.baboons.vendedorescalle.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.DailyMessage
import com.baboons.vendedorescalle.SellerSession
import com.baboons.vendedorescalle.ui.components.EmptyState
import com.baboons.vendedorescalle.ui.components.PrimaryButton
import com.baboons.vendedorescalle.ui.components.SectionTitle
import com.baboons.vendedorescalle.ui.components.StatusPill

@Composable
fun TodayScreen(
    session: SellerSession,
    date: String,
    zone: String,
    open: Boolean,
    messages: List<DailyMessage>,
    pending: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
    onRespond: (DailyMessage, Boolean, String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit
) {
    val replies = remember { mutableStateMapOf<Long, String>() }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 28.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (open) "Jornada en curso" else "Tu dia empieza aca", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        StatusPill(if (open) "En curso" else "Sin iniciar", active = open)
                    }
                    Text(
                        buildString {
                            append(date)
                            if (zone.isNotBlank()) append("  ·  Zona $zone")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!open) {
                        Spacer(Modifier.height(6.dp))
                        PrimaryButton("Comenzar jornada", onStart, busy = busy)
                    } else {
                        Text("Seguimiento de ubicacion activo", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) {
                            Text("Cerrar jornada")
                        }
                    }
                }
            }
        }
        item { SectionTitle("Mensajes", "Avisos del administrador para hoy") }
        if (messages.isEmpty()) {
            item { EmptyState("No hay mensajes para hoy", "Cuando administracion envie un aviso, va a aparecer aca.", Icons.Outlined.Forum) }
        }
        items(messages, key = { it.id }) { message ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(message.title.ifBlank { "Mensaje del dia" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        when (message.accepted) {
                            true -> StatusPill("Aceptado", true)
                            false -> StatusPill("No aceptado", false)
                            null -> if (message.required) StatusPill("Obligatorio", false)
                        }
                    }
                    Text(message.content, style = MaterialTheme.typography.bodyMedium)
                    if (message.accepted == null) {
                        OutlinedTextField(
                            value = replies[message.id].orEmpty(),
                            onValueChange = { replies[message.id] = it },
                            label = { Text("Respuesta o comentario") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onRespond(message, true, replies[message.id].orEmpty().ifBlank { "Aceptado" }) },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = MaterialTheme.shapes.medium
                            ) { Text(if (message.required) "Leido y aceptado" else "Enviar respuesta") }
                            if (!message.required) {
                                TextButton(onClick = { onRespond(message, false, replies[message.id].orEmpty().ifBlank { "No aceptado" }) }) {
                                    Text("No acepto")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (pending && open) {
            item { Text("Acepta los mensajes obligatorios para habilitar el proyectado.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        item { OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth().height(52.dp), shape = MaterialTheme.shapes.medium) { Text("Actualizar") } }
    }
}
