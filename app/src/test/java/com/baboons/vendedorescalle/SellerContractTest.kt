package com.baboons.vendedorescalle

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SellerContractTest {
    @Test
    fun tableroViejoConservaLosCamposYNoActivaLaVistaNueva() {
        val summary = SellerSummary.fromJson(
            JSONObject(
                """
                {
                  "vendedores": [{
                    "vendedor_id": 7,
                    "clientes_proyectados": 10,
                    "clientes_visitados": 4,
                    "clientes_con_venta": 1,
                    "importe_proyectado": 80000,
                    "importe_vendido": 50000,
                    "importe_objetivo": 100000,
                    "cobertura_pct": 40,
                    "conversion_pct": 25,
                    "objetivo_pct": 50,
                    "venta_vs_proyectado_pct": 62.5
                  }]
                }
                """.trimIndent()
            )
        )

        assertFalse(summary.contratoNuevo)
        assertEquals("erp", summary.fuente)
        assertEquals(10, summary.projectedCustomers)
        assertEquals(4, summary.visitedCustomers)
        assertEquals(1, summary.customersWithSale)
        assertEquals(80000.0, summary.projectedAmount, 0.001)
        assertEquals(50000.0, summary.soldAmount, 0.001)
        assertEquals(50000.0, summary.netAmount, 0.001)
        assertEquals(100000.0, summary.targetAmount, 0.001)
        assertEquals(40.0, summary.coveragePercent, 0.001)
        assertEquals(25.0, summary.conversionPercent, 0.001)
        assertEquals(50.0, summary.targetPercent, 0.001)
        assertEquals(62.5, summary.soldVsProjectedPercent, 0.001)
        assertNull(summary.periodTarget)
        assertNull(summary.fulfillmentPercent)
        assertNull(summary.semaforo)
        assertTrue(summary.recentOrders.isEmpty())
        assertEquals(90.0, summary.umbralVerde, 0.001)
        assertEquals(70.0, summary.umbralAmarillo, 0.001)
    }

    @Test
    fun tableroNuevoParseaOdooObjetivoYPedidos() {
        val summary = SellerSummary.fromJson(
            JSONObject(
                """
                {
                  "desde": "2026-11-01",
                  "hasta": "2026-11-20",
                  "fuente": "odoo",
                  "umbrales": {"verde": 90, "amarillo": 70},
                  "vendedores": [{
                    "vendedor_id": 126,
                    "clientes_proyectados": 0,
                    "importe_proyectado": 0,
                    "clientes_visitados": 0,
                    "clientes_con_venta": 12,
                    "importe_vendido": 1500000.0,
                    "importe_neto": 1500000.0,
                    "importe_total": 1815000.0,
                    "importe_objetivo": 2000000.0,
                    "objetivo_periodo": 1600000.0,
                    "objetivo_mes": 2500000.0,
                    "vendido_mes": 1500000.0,
                    "cumplimiento_pct": 93.75,
                    "diferencia": -100000.0,
                    "falta_mes": 1000000.0,
                    "meta_diaria_restante": 111111.11,
                    "dias_habiles_restantes": 9,
                    "semaforo": "verde",
                    "objetivo_pct": 75.0,
                    "cobertura_pct": 0,
                    "conversion_pct": 0,
                    "venta_vs_proyectado_pct": 0,
                    "fuente": "odoo",
                    "ultimos_pedidos": [{
                      "name": "SO00123",
                      "fecha": "2026-11-20 11:30:00",
                      "cliente": "Kiosco Sur",
                      "total": 18150.0,
                      "neto": 15000.0,
                      "odoo_order_id": 900,
                      "state": "sale"
                    }]
                  }]
                }
                """.trimIndent()
            )
        )

        assertTrue(summary.contratoNuevo)
        assertEquals("odoo", summary.fuente)
        assertEquals(90.0, summary.umbralVerde, 0.001)
        assertEquals(70.0, summary.umbralAmarillo, 0.001)
        assertEquals(1500000.0, summary.soldAmount, 0.001)
        assertEquals(1500000.0, summary.netAmount, 0.001)
        assertEquals(1815000.0, summary.grossAmount, 0.001)
        assertEquals(1600000.0, summary.periodTarget!!, 0.001)
        assertEquals(2500000.0, summary.monthTarget!!, 0.001)
        assertEquals(1500000.0, summary.monthSold!!, 0.001)
        assertEquals(93.75, summary.fulfillmentPercent!!, 0.001)
        assertEquals(-100000.0, summary.difference!!, 0.001)
        assertEquals(1000000.0, summary.monthRemaining, 0.001)
        assertEquals(111111.11, summary.dailyTargetRemaining, 0.001)
        assertEquals(9, summary.businessDaysRemaining)
        assertEquals("verde", summary.semaforo)
        assertEquals(75.0, summary.targetPercent, 0.001)
        assertEquals(0.0, summary.coveragePercent, 0.001)
        assertEquals(0.0, summary.conversionPercent, 0.001)
        val order = summary.recentOrders.single()
        assertEquals("SO00123", order.name)
        assertEquals("Kiosco Sur", order.cliente)
        assertEquals(18150.0, order.total, 0.001)
        assertEquals(15000.0, order.neto, 0.001)
        assertEquals(900L, order.odooOrderId)
        assertEquals("sale", order.state)
        assertEquals("20/11/2026 11:30", formatOrderDate(order.fecha))
    }

    @Test
    fun camposNuevosAusentesOInvalidosUsanDefault() {
        val summary = SellerSummary.fromJson(
            JSONObject(
                """
                {
                  "fuente": "ERP",
                  "vendedores": [{
                    "importe_vendido": "1500.5",
                    "cumplimiento_pct": null,
                    "semaforo": null,
                    "fuente": "otro",
                    "ultimos_pedidos": null
                  }]
                }
                """.trimIndent()
            )
        )

        assertTrue(summary.contratoNuevo)
        assertEquals("erp", summary.fuente)
        assertEquals(1500.5, summary.soldAmount, 0.001)
        assertEquals(1500.5, summary.netAmount, 0.001)
        assertNull(summary.fulfillmentPercent)
        assertNull(summary.semaforo)
        assertTrue(summary.recentOrders.isEmpty())
        assertEquals(0.0, summary.monthRemaining, 0.001)
        assertEquals(0, summary.businessDaysRemaining)
    }

    @Test
    fun semaforoSeCalculaConUmbralesSiElErpNoLoManda() {
        val summary = SellerSummary.fromJson(
            JSONObject(
                """
                {"umbrales":{"verde":95,"amarillo":80},"vendedores":[{"cumplimiento_pct":92}]}
                """.trimIndent()
            )
        )
        assertEquals("amarillo", summary.semaforo)
        assertEquals(95.0, summary.umbralVerde, 0.001)
        assertEquals(80.0, summary.umbralAmarillo, 0.001)
    }

    @Test
    fun pedidosDelClienteY404() {
        val json = JSONObject(
            """
            {
              "cliente_id": 4401,
              "cliente_nombre": "Kiosco Sur",
              "fuente": "odoo",
              "comprado_mes": {"mes": "2026-11", "neto": 48000.0, "total": 58080.0, "cantidad": 3},
              "ultimos_pedidos": [{
                "name": "",
                "fecha": "2026-11-20 11:30:00",
                "cliente": "Kiosco Sur",
                "total": 18150.0,
                "neto": 15000.0,
                "venta_id": 123,
                "state": "sale"
              }]
            }
            """.trimIndent()
        )
        val ready = customerPurchasesState(200, null, json) as CustomerPurchasesState.Ready
        assertEquals(4401L, ready.purchases.clienteId)
        assertEquals("Kiosco Sur", ready.purchases.clienteNombre)
        assertEquals("odoo", ready.purchases.fuente)
        assertEquals("2026-11", ready.purchases.compradoMes.mes)
        assertEquals(48000.0, ready.purchases.compradoMes.neto, 0.001)
        assertEquals(58080.0, ready.purchases.compradoMes.total, 0.001)
        assertEquals(3, ready.purchases.compradoMes.cantidad)
        assertEquals("V-123", ready.purchases.ultimosPedidos.single().title)
        assertEquals("Noviembre 2026", formatMonthLabel("2026-11"))

        assertTrue(customerPurchasesState(404, "No encontrado", null) is CustomerPurchasesState.Unavailable)
        val failed = customerPurchasesState(500, "Error HTTP 500", null) as CustomerPurchasesState.Error
        assertEquals("Error HTTP 500", failed.message)

        val empty = (customerPurchasesState(200, null, JSONObject()) as CustomerPurchasesState.Ready).purchases
        assertEquals("erp", empty.fuente)
        assertEquals(0.0, empty.compradoMes.neto, 0.001)
        assertTrue(empty.ultimosPedidos.isEmpty())
    }

    @Test
    fun textosDeMontosYDiasEnEspanol() {
        assertEquals("Ventas de Odoo", fuenteLabel("odoo"))
        assertEquals("Ventas del ERP", fuenteLabel("erp"))
        assertEquals("Confirmado", orderStateLabel("sale"))
        assertEquals("93,75%", 93.75.percentLabel())
        assertTrue(money(1500.5).contains("1.500"))
        assertEquals("9 días hábiles restantes (lunes a sábado)", diasHabilesLabel(9))
        assertEquals("1 día hábil restante (lunes a sábado)", diasHabilesLabel(1))
        assertEquals("3 pedidos", pedidosCountLabel(3))
    }
}
