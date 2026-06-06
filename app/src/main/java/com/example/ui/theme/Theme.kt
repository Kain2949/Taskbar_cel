package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MonolithColorScheme = darkColorScheme(
    primary = ElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimary = Color(0xFF003B33),
    secondary = ElectricCyanSecondary,
    onSecondary = Color(0xFF003B33),
    background = CarbonBackground,
    onBackground = CrispWhite,
    surface = MonolithSurface,
    onSurface = CrispWhite,
    surfaceVariant = MonolithSurfaceVariant,
    onSurfaceVariant = TechMutedText,
    error = OrangeFlame,
    onError = Color.Black
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = CarbonBackground.toArgb()
                window.navigationBarColor = CarbonBackground.toArgb()
                
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.isAppearanceLightStatusBars = false
                insetsController.isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = MonolithColorScheme,
        typography = Typography,
        content = content
    )
}
