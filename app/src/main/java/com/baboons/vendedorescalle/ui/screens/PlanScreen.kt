
package com.baboons.vendedorescalle.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.PlanEntry
import com.baboons.vendedorescalle.SellerCustomer
import com.baboons.vendedorescalle.ui.components.EmptyState
import com.baboons.vendedorescalle.ui.components.SectionTitle

@Composable
fun PlanScreen(
    customers: List<SellerCustomer>,
    plan: List<PlanEntry>,
    open: Boolean,
    pending: Boolean,
    busy: Boolean,
    onVisit: (PlanEntry, String, String) -> Unit,
    onSave: (Map<Long, Double>) -> Unit,
    onRefresh: () -> Unit,
    onAddException: (Long, String) -> Unit,
    onSearchCustomers: (String, (List<SellerCustomer>) -> Unit) -> Unit
) {
    val selected = remember { mutableStateMapOf<Long, Double>() }
    var visitTarget by remember { mutableStateOf<PlanEntry?>(null) }
    var visitResult by remember { mutableStateOf("venta") }
    var visitNotes by remember { mutableStateOf("") }
    var exceptionDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<SellerCustomer>()) }
    var chosenCustomer by remember { mutableStateOf<SellerCustomer?>(null) }
    var exceptionReason by remember { mutableStateOf("") }

    LaunchedEffect(plan) { selected.clear(); plan.forEach { selected[it.customerId] = it.estimatedAmount } }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text("Proyectado del dia", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Clientes habilitados para tu zona y el dia de hoy",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )
        when {
            !open -> EmptyState("Comenza la jornada", "Para armar el proyectado y registrar visitas tenes que iniciar la jornada.", Icons.Outlined.Schedule, Modifier.weight(1f))
            pending -> EmptyState("Mensajes pendientes", "Primero lee y acepta los mensajes obligatorios en Jornada.", Icons.Outlined.Lock, Modifier.weight(1f))
            customers.isEmpty() -> EmptyState("Sin clientes para hoy", "No hay clientes asignados. Podes agregar una excepcion o actualizar la lista.", Icons.Outlined.Group, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(customers, key = { it.id }) { customer ->
                    val amount = selected[customer.id]
                    val planEntry = plan.firstOrNull { it.customerId == customer.id }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(amount != null, { checked -> if (checked) selected[customer.id] = 0.0 else selected.remove(customer.id) })
                            Column(Modifier.weight(1f)) {
                                Text(customer.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                                Text(customer.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (planEntry != null) {
                                    TextButton(onClick = { visitTarget = planEntry }) { Text("Registrar visita") }
                                }
                            }
                            if (amount != null) {
                                OutlinedTextField(
                                    amount.takeIf { it > 0 }?.toString().orEmpty(),
                                    { raw -> selected[customer.id] = raw.toDoubleOrNull() ?: 0.0 },
                                    Modifier.width(110.dp),
                                    label = { Text("Estimado") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.small,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    exceptionDialog = true; searchQuery = ""; searchResults = emptyList(); chosenCustomer = null; exceptionReason = ""
                },
                enabled = open && !pending,
                modifier = Modifier.weight(1.2f).height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("+ Excepcion") }
            OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(0.9f).height(52.dp), shape = MaterialTheme.shapes.medium) { Text("Actualizar") }
            Button(
                onClick = { onSave(selected.toMap()) },
                enabled = open && !pending && selected.isNotEmpty() && !busy,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Guardar (${selected.size})") }
        }
    }
    if (exceptionDialog) {
        AlertDialog(
            onDismissRequest = { exceptionDialog = false },
            title = { Text("Cliente fuera de zona") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (chosenCustomer == null) {
                        OutlinedTextField(searchQuery, { searchQuery = it }, label = { Text("Buscar nombre o direccion") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                        Button(
                            onClick = { if (searchQuery.trim().length >= 2) onSearchCustomers(searchQuery.trim()) { searchResults = it } },
                            enabled = searchQuery.trim().length >= 2,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) { Text("Buscar") }
                        if (searchResults.isEmpty() && searchQuery.length >= 2) {
                            Text("Sin coincidencias", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        searchResults.forEach { c ->
                            Card(modifier = Modifier.fillMaxWidth().clickable { chosenCustomer = c }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(c.name, fontWeight = FontWeight.Bold)
                                    Text(c.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        Text("Seleccionado: ${chosenCustomer!!.name}", fontWeight = FontWeight.Bold)
                        Text(chosenCustomer!!.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(exceptionReason, { exceptionReason = it }, label = { Text("Motivo de la excepcion *") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                        TextButton(onClick = { chosenCustomer = null }) { Text("Cambiar cliente") }
                    }
                }
            },
            confirmButton = {
                if (chosenCustomer != null) {
                    Button(
                        onClick = {
                            if (exceptionReason.isNotBlank()) {
                                onAddException(chosenCustomer!!.id, exceptionReason.trim())
                                exceptionDialog = false
                            }
                        },
                        enabled = exceptionReason.isNotBlank()
                    ) { Text("Agregar") }
                }
            },
            dismissButton = { TextButton(onClick = { exceptionDialog = false }) { Text("Cancelar") } }
        )
    }
    visitTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { visitTarget = null },
            title = { Text("Resultado de la visita") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("venta" to "Hubo venta", "sin_venta" to "Visitado, sin venta", "no_encontrado" to "No se pudo visitar").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { visitResult = value }) {
                            RadioButton(selected = visitResult == value, onClick = { visitResult = value }); Text(label)
                        }
                    }
                    OutlinedTextField(visitNotes, { visitNotes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small)
                }
            },
            confirmButton = { Button(onClick = { onVisit(entry, visitResult, visitNotes); visitTarget = null; visitNotes = "" }) { Text("Guardar visita") } },
            dismissButton = { TextButton(onClick = { visitTarget = null }) { Text("Cancelar") } }
        )
    }
}
