package com.baboons.vendedorescalle

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomerVerificationTest {
    private val customer = SellerCustomer(
        id = 44,
        name = "Kiosco Sur",
        address = "Av. Illia 10",
        phone = "2664000000",
        latitude = -33.3012,
        longitude = -66.3371,
        email = "sur@example.com"
    )

    @Test
    fun fichaCompletaIncluyeLaUltimaVerificacion() {
        val ficha = CustomerFicha.fromJson(
            JSONObject(
                """
                {
                  "cliente_id": 44,
                  "nombre": "Kiosco Sur",
                  "direccion": "Av. Illia 10",
                  "telefono": "2664000000",
                  "email": "sur@example.com",
                  "dni": "20123456783",
                  "tipo_documento": "CUIT",
                  "ciudad": "San Luis",
                  "codigo_postal": "5700",
                  "latitud": -33.3012,
                  "longitud": -66.3371,
                  "odoo_partner_id": 900,
                  "ultima_verificacion": {"id": 8, "estado": "pendiente", "creado_en": "2026-11-23T15:00:00-03:00"}
                }
                """.trimIndent()
            )
        )

        assertEquals(44L, ficha.id)
        assertEquals("20123456783", ficha.dni)
        assertEquals("San Luis", ficha.city)
        assertEquals("5700", ficha.postalCode)
        assertEquals("CUIT", ficha.documentType)
        assertEquals(900L, ficha.odooPartnerId)
        assertEquals(8L, ficha.lastVerification?.id)
        assertEquals("pendiente", ficha.lastVerification?.estado)
        assertEquals("2026-11-23T15:00:00-03:00", ficha.lastVerification?.creadoEn)
        assertEquals("23/11/2026 15:00", formatOrderDate(ficha.lastVerification!!.creadoEn!!))
        assertTrue(ficha.displayFields().none { it.missing })
    }

    @Test
    fun sinUltimaVerificacionYCamposVaciosQuedanEnRojo() {
        val ficha = CustomerFicha.fromJson(JSONObject("""{"cliente_id": 7, "nombre": "Almacen", "ultima_verificacion": null}"""))
        assertNull(ficha.lastVerification)
        assertEquals(7L, ficha.id)
        assertEquals("Almacen", ficha.name)
        val missing = ficha.displayFields().filter { it.missing }.map { it.label }
        assertEquals(
            listOf("Teléfono", "Email", "Dirección", "CUIT/DNI", "Tipo de documento", "Ciudad", "Código postal", "Ubicación"),
            missing
        )
    }

    @Test
    fun un404ArmaLaFichaConElClienteLocal() {
        val resolved = resolveCustomerFicha(404, "No encontrado", null, customer)
        assertFalse(resolved.noteIsError)
        assertEquals("Kiosco Sur", resolved.ficha.name)
        assertEquals("sur@example.com", resolved.ficha.email)
        assertEquals("2664000000", resolved.ficha.phone)
        assertEquals(-33.3012, resolved.ficha.latitude!!, 0.0001)
        assertNull(resolved.ficha.lastVerification)
        assertTrue(resolved.note!!.contains("no está disponible"))
    }

    @Test
    fun sinRedTambienUsaLosDatosLocales() {
        val resolved = resolveCustomerFicha(null, "Unable to resolve host", null, customer)
        assertFalse(resolved.noteIsError)
        assertEquals(customer.id, resolved.ficha.id)
        assertTrue(resolved.note!!.contains("Sin conexión"))
        val unavailable = resolveCustomerFicha(503, "Falta la tabla", null, customer)
        assertTrue(unavailable.noteIsError)
        assertTrue(unavailable.note!!.contains("Función no disponible todavía en el servidor"))
        assertEquals(customer.name, unavailable.ficha.name)
    }

    @Test
    fun el200LeeLaFichaYCompletaElNombreSiVieneVacio() {
        val resolved = resolveCustomerFicha(
            200,
            null,
            JSONObject("""{"id": 44, "nombre": "", "telefono": "1", "dni": null}"""),
            customer
        )
        assertNull(resolved.note)
        assertEquals("Kiosco Sur", resolved.ficha.name)
        assertEquals("1", resolved.ficha.phone)
        assertEquals("", resolved.ficha.dni)
    }

    @Test
    fun emailYDocumento() {
        assertNull(emailVerificationError(""))
        assertNull(emailVerificationError("a.b+c@dominio.com.ar"))
        assertEquals("El email no tiene un formato válido.", emailVerificationError("no-es-mail"))
        assertNull(dniVerificationError(""))
        assertNull(dniVerificationError("1234567"))
        assertNull(dniVerificationError("20-12345678-3"))
        assertEquals("20123456783", normalizeDni("20-12345678-3"))
        assertEquals("20123456783", normalizeDni("AR20-12345678-3"))
        assertEquals("12345678", normalizeDni("12.345.678"))
        assertNull(dniVerificationError("20123456783"))
        assertNull(phoneVerificationError(""))
        assertNull(phoneVerificationError("266 400-0000"))
        assertTrue(phoneVerificationError("12345")!!.contains("6 dígitos"))
        assertTrue(dniVerificationError("123456")!!.contains("7 y 11"))
        assertTrue(dniVerificationError("123456789012")!!.contains("solo dígitos"))
        assertTrue(dniVerificationError("abc1234567")!!.contains("solo dígitos"))
        assertTrue(dniVerificationError("0000000")!!.contains("solo dígitos"))
        assertNull(verificationPayloadError(VerificationDraft("2664000000", "", "", "", "", "", ""), null))
        assertNull(verificationPayloadError(VerificationDraft("", "", "", "", "", "", "solo notas"), LocationFix(-33.3, -66.3, 10f)))
        assertTrue(verificationPayloadError(VerificationDraft("", "", "", "", "", "", "solo notas"), null)!!.contains("al menos un dato"))
    }

    @Test
    fun elCuerpoLlevaLaClaveYOmiteLaVisitaSiNoHay() {
        val pending = samplePending(visitId = null)
        val body = pending.toBody()
        assertEquals(pending.idempotencyKey, body.getString("idempotency_key"))
        assertEquals(-33.3012, body.getDouble("latitud"), 0.0001)
        assertEquals(-66.3371, body.getDouble("longitud"), 0.0001)
        assertEquals(80.0, body.getDouble("precision_m"), 0.001)
        assertEquals("sur@example.com", body.getString("email"))
        assertEquals("20123456783", body.getString("dni"))
        assertFalse(body.has("visita_id"))

        val withoutPin = pending.copy(
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            email = "",
            address = "",
            dni = "",
            city = "",
            postalCode = "",
            notes = ""
        ).toBody()
        assertFalse(withoutPin.has("latitud"))
        assertFalse(withoutPin.has("longitud"))
        assertFalse(withoutPin.has("precision_m"))
        assertFalse(withoutPin.has("email"))
        assertEquals("2664000000", withoutPin.getString("telefono"))
        assertEquals(pending.idempotencyKey, withoutPin.getString("idempotency_key"))

        val withVisit = pending.copy(visitId = 15).toBody()
        assertEquals(15L, withVisit.getLong("visita_id"))
        assertEquals(pending.idempotencyKey, withVisit.getString("idempotency_key"))
    }

    @Test
    fun laColaLocalConservaLaMismaClave() {
        val original = samplePending(visitId = 9)
        val restored = decodePendingList(encodePendingList(listOf(original))).single()
        assertEquals(original.idempotencyKey, restored.idempotencyKey)
        assertEquals(original.customerId, restored.customerId)
        assertEquals(9L, restored.visitId)
        assertEquals(original.toBody().toString(), restored.toBody().toString())
    }

    @Test
    fun laPrecisionMalaAvisaYLaDe50MetrosNo() {
        assertNull(accuracyWarning(50f))
        assertNull(accuracyWarning(12.4f))
        assertTrue(accuracyWarning(50.4f)!!.contains("peor que 50"))
        assertTrue(accuracyWarning(0f)!!.contains("Podés enviar igual"))
    }

    @Test
    fun elResultadoDelPost() {
        val accepted = interpretVerification(
            ApiResult(JSONObject("""{"id": 3, "estado": "pendiente"}"""), null, null, 201)
        ) as VerificationSubmitResult.Accepted
        assertEquals(3L, accepted.id)
        assertEquals("pendiente", accepted.estado)
        assertFalse(accepted.idempotent)
        assertFalse(shouldKeepPending(accepted))

        val again = interpretVerification(
            ApiResult(JSONObject("""{"id": 3, "estado": "aprobado", "idempotente": true}"""), null, null, 200)
        ) as VerificationSubmitResult.Accepted
        assertEquals("aprobado", again.estado)
        assertTrue(again.idempotent)
        assertFalse(shouldKeepPending(again))

        val offline = interpretVerification(ApiResult(null, null, "Unable to resolve host", null))
        assertTrue(offline is VerificationSubmitResult.Offline)
        assertTrue(shouldKeepPending(offline))

        val conflict = interpretVerification(ApiResult(null, null, "No hay jornada abierta", 409))
            as VerificationSubmitResult.Rejected
        assertEquals("No hay jornada abierta", conflict.message)
        assertFalse(shouldKeepPending(conflict))

        val server = interpretVerification(ApiResult(null, null, "Error HTTP 500", 500))
        assertTrue(shouldKeepPending(server))
        assertTrue((server as VerificationSubmitResult.Rejected).message.contains("reintentar"))

        val missing = interpretVerification(ApiResult(null, null, "Error HTTP 404", 404))
            as VerificationSubmitResult.Rejected
        assertTrue(missing.message.contains("no está en este negocio"))
        assertFalse(shouldKeepPending(missing))

        val unavailable = interpretVerification(ApiResult(null, null, "Falta la tabla clientes_verificaciones", 503))
            as VerificationSubmitResult.Rejected
        assertTrue(unavailable.message.contains("Función no disponible todavía en el servidor"))
        assertTrue(unavailable.message.contains("guardada para reintentar"))
        assertTrue(shouldKeepPending(unavailable))
    }

    @Test
    fun elClienteLocalLeeEmailSiElListadoLoTrae() {
        val parsed = SellerCustomer.fromJson(
            JSONObject("""{"id": 8, "nombre": "Norte", "direccion": "Mitre 1", "telefono": "1", "email": "n@b.com", "dni": null}""")
        )
        assertEquals("n@b.com", parsed.email)
        assertEquals("", parsed.dni)
    }

    private fun samplePending(visitId: Long?) = PendingVerification(
        customerId = 44,
        idempotencyKey = "6f1c3c4e-2b1a-4f0e-9c1d-0a0b0c0d0e0f",
        latitude = -33.3012,
        longitude = -66.3371,
        accuracyMeters = 80.0,
        phone = "2664000000",
        email = "sur@example.com",
        address = "Av. Illia 10",
        dni = "20123456783",
        city = "San Luis",
        postalCode = "5700",
        notes = "Local en la esquina",
        visitId = visitId,
        savedAtMs = 1_763_000_000_000
    )
}
