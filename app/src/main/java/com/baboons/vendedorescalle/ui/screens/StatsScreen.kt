package com.baboons.vendedorescalle.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.SellerSummary
import com.baboons.vendedorescalle.diasHabilesLabel
import com.baboons.vendedorescalle.fuenteLabel
import com.baboons.vendedorescalle.money
import com.baboons.vendedorescalle.percentLabel
import com.baboons.vendedorescalle.pretty
import com.baboons.vendedorescalle.semaforoLabel
import com.baboons.vendedorescalle.ui.components.EmptyState
import com.baboons.vendedorescalle.ui.components.KpiCard
import com.baboons.vendedorescalle.ui.components.OrdersList
import com.baboons.vendedorescalle.ui.components.SectionTitle
import com.baboons.vendedorescalle.ui.theme.semaforoColor
import java.time.LocalDate
import kotlin.math.abs

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
        } else if (!summary.contratoNuevo) {
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
        } else {
            item { SourceChip(fuenteLabel(summary.fuente)) }
            item { PeriodGoalCard(summary) }
            item { MonthRemainderCard(summary) }
            item { SectionTitle("Últimos pedidos", "Confirmados más recientes") }
            item { OrdersList(summary.recentOrders) }
            item { SectionTitle("Cobertura y proyección") }
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
                    title = "Conversión",
                    value = "${summary.customersWithSale} con venta",
                    detail = "${summary.conversionPercent.pretty()}% de las visitas terminaron en venta",
                    progress = (summary.conversionPercent / 100.0).toFloat()
                )
            }
            item {
                KpiCard(
                    title = "Venta vs. proyectado",
                    value = "${summary.soldVsProjectedPercent.pretty()}%",
                    detail = "Proyección ${money(summary.projectedAmount)}",
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

@Composable
private fun SourceChip(label: String) {
    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun PeriodGoalCard(summary: SellerSummary) {
    val color = semaforoColor(summary.semaforo)
    val fulfillment = summary.fulfillmentPercent
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (color != null) {
                Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            }
            Column(Modifier.weight(1f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vendido vs objetivo", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(summary.netAmount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                val target = summary.periodTarget
                Text(
                    if (target != null) "Objetivo del período ${money(target)}" else "Sin objetivo del período",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (abs(summary.grossAmount - summary.netAmount) > 0.009) {
                    Text(
                        "Total con impuestos ${money(summary.grossAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (fulfillment != null) {
                    LinearProgressIndicator(
                        progress = { (fulfillment / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = color ?: MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    SemaphoreBadge(fulfillment, summary.semaforo, color)
                    Text(
                        "Verde desde ${summary.umbralVerde.percentLabel()} · Amarillo desde ${summary.umbralAmarillo.percentLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                summary.difference?.let { difference ->
                    MetricLine("Diferencia", money(difference))
                    Text(
                        when {
                            difference > 0.0 -> "Por encima del objetivo del período"
                            difference < 0.0 -> "Por debajo del objetivo del período"
                            else -> "En el objetivo del período"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SemaphoreBadge(fulfillment: Double, semaforo: String?, color: Color?) {
    val label = buildString {
        append("Cumplimiento ${fulfillment.percentLabel()}")
        if (semaforo != null) append(" · ${semaforoLabel(semaforo)}")
    }
    val content = color ?: MaterialTheme.colorScheme.onSurface
    Surface(shape = MaterialTheme.shapes.small, color = content.copy(alpha = 0.14f)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = content,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MonthRemainderCard(summary: SellerSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Resto del mes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            MetricLine("Falta del mes", money(summary.monthRemaining))
            val monthCovered = summary.monthRemaining <= 0.0 && (summary.monthTarget ?: 0.0) > 0.0
            val withoutMonthTarget = summary.monthTarget == null || summary.monthTarget <= 0.0
            Text(
                when {
                    monthCovered -> "Objetivo del mes cubierto"
                    withoutMonthTarget -> "Sin objetivo del mes"
                    else -> "Lo que falta para el objetivo del mes"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            MetricLine("Meta diaria restante", money(summary.dailyTargetRemaining))
            Text(
                diasHabilesLabel(summary.businessDaysRemaining),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            summary.monthSold?.let { MetricLine("Vendido del mes", money(it)) }
            summary.monthTarget?.let { MetricLine("Objetivo del mes", money(it)) }
        }
    }
}

@Composable
private fun MetricLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
