package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val SecurityLightColorScheme = lightColorScheme(
    primary = SecurityGreen,
    onPrimary = Color.White,
    primaryContainer = SecurityGreenContainer,
    onPrimaryContainer = OnSecurityGreenContainer,
    secondary = BluetoothCyan,
    onSecondary = Color.White,
    secondaryContainer = BluetoothCyanContainer,
    onSecondaryContainer = OnBluetoothCyanContainer,
    tertiary = WarningAmber,
    onTertiary = Color.White,
    tertiaryContainer = WarningAmberContainer,
    onTertiaryContainer = OnWarningAmberContainer,
    error = AlarmRed,
    onError = Color.White,
    errorContainer = AlarmRedContainer,
    onErrorContainer = OnAlarmRedContainer,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = NavySurface,
    onSurface = TextPrimary,
    surfaceVariant = NavySurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = NavyBorder
)

val SecurityShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SecurityLightColorScheme,
        typography = Typography,
        shapes = SecurityShapes,
        content = content
    )
}
