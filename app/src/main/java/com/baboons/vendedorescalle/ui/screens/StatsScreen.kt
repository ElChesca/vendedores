
package com.baboons.vendedorescalle.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.SellerSummary
import com.baboons.vendedorescalle.money
import com.baboons.vendedorescalle.pretty
import com.baboons.vendedorescalle.ui.components.EmptyState
import com.baboons.vendedorescalle.ui.components.KpiCard
import java.time.LocalDate

@Composable
fun StatsScreen(summary: SellerSummary?, onRange: (String, String) -> Unit) {
    var periodDays by remember { mutableIntStateOf(1) }
    val until = LocalDate.now()
    val from = until.minusDays((periodDays - 1).toLong())
    LaunchedEffect(periodDays) { onRange(from.toString(), until.toString()) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                if (periodDays == 1) "Indicadores de hoy" else "Indicadores de los ultimos $periodDays dias",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "Hoy", 7 to "7 dias", 30 to "30 dias").forEach { (days, label) ->
                    FilterChip(selected = periodDays == days, onClick = { periodDays = days }, label = { Text(label) })
                }
            }
        }
        if (summary == null) {
            item { EmptyState("Todavia no hay datos", "Cuando haya proyectado o ventas, los indicadores van a aparecer aca.", Icons.Outlined.Insights) }
        } else {
            item {
                KpiCard(
                    title = "Cobertura",
                    value = "${summary.visitedCustomers} / ${summary.projectedCustomers}",
                    detail = "${summary.coveragePercent.pretty()}% de clientes proyectados visitados",
                    progress = (summary.coveragePercent / 100.0).toFloat()
                )
            }
            item {
                KpiCard(
                    title = "Conversion",
                    value = "${summary.customersWithSale} con venta",
                    detail = "${summary.conversionPercent.pretty()}% de las visitas terminaron en venta",
                    progress = (summary.conversionPercent / 100.0).toFloat()
                )
            }
            item {
                KpiCard(
                    title = "Objetivo",
                    value = money(summary.soldAmount),
                    detail = "${summary.targetPercent.pretty()}% del objetivo de ${money(summary.targetAmount)}",
                    progress = (summary.targetPercent / 100.0).toFloat()
                )
            }
            item {
                KpiCard(
                    title = "Venta vs. proyectado",
                    value = "${summary.soldVsProjectedPercent.pretty()}%",
                    detail = "Proyeccion ${money(summary.projectedAmount)}",
                    progress = (summary.soldVsProjectedPercent / 100.0).toFloat()
                )
            }
        }
        item {
            OutlinedButton(
                onClick = { onRange(from.toString(), until.toString()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) { Text("Actualizar indicadores") }
        }
    }
}
