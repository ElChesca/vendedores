package com.baboons.vendedorescalle.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.SellerOrder
import com.baboons.vendedorescalle.formatOrderDate
import com.baboons.vendedorescalle.money
import com.baboons.vendedorescalle.orderStateLabel
import kotlin.math.abs

@Composable
fun OrdersList(orders: List<SellerOrder>, modifier: Modifier = Modifier) {
    if (orders.isEmpty()) {
        Text(
            "Sin pedidos recientes",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        orders.forEach { order ->
            val stateLabel = orderStateLabel(order.state)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(order.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (stateLabel.isNotBlank()) {
                            StatusPill(stateLabel, active = order.state.equals("sale", ignoreCase = true))
                        }
                    }
                    val subtitle = listOf(formatOrderDate(order.fecha), order.cliente).filter { it.isNotBlank() }.joinToString("  ·  ")
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("Neto ${money(order.neto)}", style = MaterialTheme.typography.bodyMedium)
                    if (abs(order.total - order.neto) > 0.009) {
                        Text("Total ${money(order.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
