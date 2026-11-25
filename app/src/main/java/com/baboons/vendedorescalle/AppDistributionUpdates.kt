package com.baboons.vendedorescalle

import android.content.Context
import android.util.Log
import com.google.firebase.appdistribution.FirebaseAppDistribution
import com.google.firebase.appdistribution.FirebaseAppDistributionException

/**
 * Alertas in-app de Firebase App Distribution.
 *
 * El SDK completo solo entra en las variantes debug (las que se suben a testers).
 * En release las llamadas no hacen nada ([FirebaseAppDistributionException.Status.NOT_IMPLEMENTED]).
 * Si falla la red, el usuario no es tester, o Firebase no está inicializado, no se interrumpe la app.
 */
object AppDistributionUpdates {
    private const val TAG = "BondVendedores"

    fun checkIfNeeded(context: Context) {
        if (SessionStore.load(context) == null) return
        checkForUpdate()
    }

    private fun checkForUpdate() {
        try {
            FirebaseAppDistribution.getInstance()
                .updateIfNewReleaseAvailable()
                .addOnFailureListener { error ->
                    if (error is FirebaseAppDistributionException) {
                        if (error.errorCode == FirebaseAppDistributionException.Status.NOT_IMPLEMENTED) {
                            return@addOnFailureListener
                        }
                        Log.w(TAG, "App Distribution: ${error.errorCode}", error)
                    } else {
                        Log.w(TAG, "App Distribution: el chequeo de versión falló", error)
                    }
                }
        } catch (error: Exception) {
            Log.w(TAG, "App Distribution no disponible", error)
        }
    }
}
