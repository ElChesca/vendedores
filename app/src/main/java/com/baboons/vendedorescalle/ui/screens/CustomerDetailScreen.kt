package com.baboons.vendedorescalle.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.CustomerPurchases
import com.baboons.vendedorescalle.CustomerPurchasesState
import com.baboons.vendedorescalle.MonthPurchases
import com.baboons.vendedorescalle.SellerCustomer
import com.baboons.vendedorescalle.formatMonthLabel
import com.baboons.vendedorescalle.fuenteLabel
import com.baboons.vendedorescalle.money
import com.baboons.vendedorescalle.pedidosCountLabel
import com.baboons.vendedorescalle.ui.components.OrdersList
import com.baboons.vendedorescalle.ui.components.SectionTitle

@Composable
fun CustomerDetailScreen(
    customer: SellerCustomer,
    purchasesState: CustomerPurchasesState,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Volver", modifier = Modifier.padding(start = 6.dp))
            }
        }
        item {
            Text("Ficha del cliente", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(customer.name.ifBlank { "Cliente" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (customer.address.isNotBlank()) {
                        Text(customer.address, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (customer.phone.isNotBlank()) {
                        Text(customer.phone, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (customer.address.isBlank() && customer.phone.isBlank()) {
                        Text("Sin dirección ni teléfono cargados", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (purchasesState !is CustomerPurchasesState.Unavailable) {
            item { SectionTitle("Compras", "Pedidos confirmados del cliente") }
            item {
                when (purchasesState) {
                    CustomerPurchasesState.Loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Cargando compras…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is CustomerPurchasesState.Error -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(purchasesState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = onRetry, shape = MaterialTheme.shapes.medium) { Text("Reintentar") }
                    }
                    is CustomerPurchasesState.Ready -> PurchasesBlock(purchasesState.purchases)
                    CustomerPurchasesState.Unavailable -> Unit
                }
            }
        }
    }
}

@Composable
private fun PurchasesBlock(purchases: CustomerPurchases) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(fuenteLabel(purchases.fuente), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(
                    "Comprado en ${formatMonthLabel(purchases.compradoMes.mes)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                MonthAmounts(purchases.compradoMes)
            }
        }
        Text("Últimos pedidos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OrdersList(purchases.ultimosPedidos)
    }
}

@Composable
private fun MonthAmounts(month: MonthPurchases) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Neto ${money(month.neto)}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Total ${money(month.total)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(pedidosCountLabel(month.cantidad), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
