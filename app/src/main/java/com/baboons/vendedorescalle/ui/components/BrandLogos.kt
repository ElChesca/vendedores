
package com.baboons.vendedorescalle.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.baboons.vendedorescalle.R

@Composable
fun BrandLogos(modifier: Modifier = Modifier, height: Dp = 36.dp) {
    val dark = isSystemInDarkTheme()
    val logica = if (dark) R.drawable.logo_logica_darkbg else R.drawable.logo_logica_lightbg
    val baboons = if (dark) R.drawable.logo_baboons_darkbg else R.drawable.logo_baboons_lightbg
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(baboons),
            contentDescription = "Baboons",
            modifier = Modifier.height(height),
            contentScale = ContentScale.Fit
        )
        Text("&", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Image(
            painter = painterResource(logica),
            contentDescription = "Lógica",
            modifier = Modifier.height(height * 0.72f),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun BrandLockup(modifier: Modifier = Modifier, subtitle: String? = "Baboons & Lógica") {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BrandLogos(height = 52.dp)
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
