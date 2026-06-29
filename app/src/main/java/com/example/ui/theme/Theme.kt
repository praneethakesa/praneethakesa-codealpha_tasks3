package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = BentoPrimary,
    primaryContainer = Color(0xFF1F3D0C),
    onPrimaryContainer = Color(0xFFE8FCD4),
    secondary = Color(0xFF1CB0F6), // Sky Blue Accent
    secondaryContainer = Color(0xFF0C384D),
    onSecondaryContainer = Color(0xFFDDF4FF),
    tertiary = Color(0xFFFF9600), // Gold Orange Accent
    tertiaryContainer = Color(0xFF4A2B00),
    background = Color(0xFF131F24), // Premium deep slate navy
    surface = Color(0xFF1B2B32), // Soft dark card slate
    onBackground = Color(0xFFECEFF1), // Crisp near-white
    onSurface = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFFB0BEC5), // Subtle gray
    outlineVariant = Color(0xFF354E5B) // Slate border outline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BentoPrimary,
    primaryContainer = BentoPrimaryContainer,
    onPrimaryContainer = BentoOnPrimaryContainer,
    secondary = Color(0xFF1CB0F6), // Vibrant Sky Blue
    secondaryContainer = BentoSecondaryContainer,
    onSecondaryContainer = Color(0xFF003554),
    tertiary = Color(0xFFFF9600), // Vibrant Gold/Orange
    tertiaryContainer = BentoTertiaryContainer,
    onTertiaryContainer = Color(0xFF4A2B00),
    background = BentoBg, // Elegant minty off-white
    surface = Color.White,
    onBackground = BentoText,
    onSurface = BentoText,
    onSurfaceVariant = BentoSubtext,
    outlineVariant = BentoBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
