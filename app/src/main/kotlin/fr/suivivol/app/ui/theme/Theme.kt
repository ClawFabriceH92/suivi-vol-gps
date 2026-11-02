package fr.suivivol.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.suivivol.app.data.ThemeApp

private val CouleursClaires: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B3D91),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E3FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF565E71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF131B2C),
    tertiary = Color(0xFFB3261E),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    error = Color(0xFFBA1A1A),
)

private val CouleursSombres: ColorScheme = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002D6C),
    primaryContainer = Color(0xFF004397),
    onPrimaryContainer = Color(0xFFD9E3FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFFFB4AB),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF151B22),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ThemeSuiviVol(theme: ThemeApp, contenu: @Composable () -> Unit) {
    val sombre = when (theme) {
        ThemeApp.SYSTEME -> isSystemInDarkTheme()
        ThemeApp.CLAIR -> false
        ThemeApp.SOMBRE -> true
    }
    MaterialTheme(
        colorScheme = if (sombre) CouleursSombres else CouleursClaires,
        content = contenu,
    )
}
