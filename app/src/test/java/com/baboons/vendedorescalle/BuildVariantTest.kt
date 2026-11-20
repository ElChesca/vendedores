package com.baboons.vendedorescalle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildVariantTest {
    @Test
    fun laUrlYElApplicationIdSiguenAlFlavor() {
        val base = BuildConfig.BABOONS_API_BASE
        assertTrue(base.startsWith("https://"))
        assertTrue(base.endsWith("/api/"))
        when (BuildConfig.FLAVOR) {
            "prod" -> {
                assertEquals(false, BuildConfig.IS_TESTING)
                assertEquals("https://concesionarias.baboons.com.ar/api/", base)
                assertEquals("com.baboons.vendedorescalle", BuildConfig.APPLICATION_ID)
                assertEquals("0.1.0", BuildConfig.VERSION_NAME)
            }
            "qa" -> {
                assertEquals(true, BuildConfig.IS_TESTING)
                assertEquals("https://testing.baboons.com.ar/api/", base)
                assertEquals("com.baboons.vendedorescalle.testing", BuildConfig.APPLICATION_ID)
                assertEquals("0.1.0-testing", BuildConfig.VERSION_NAME)
            }
            else -> error("Flavor inesperado: ${BuildConfig.FLAVOR}")
        }
    }
}
