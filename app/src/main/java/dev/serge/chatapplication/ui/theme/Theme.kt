package dev.serge.chatapplication.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFE53935),      // slightly darker red
    onPrimary = Color.Black,

    secondary = Color(0xFF00D4E6),    // toned-down cyan
    onSecondary = Color.Black,

    tertiary = Color(0xFFE6E600),     // slightly muted yellow
    onTertiary = Color.Black,

    background = Color(0xFF1E1E1E),   // true black (base)
    onBackground = Color(0xFFEDEDED), // softer white (less eye strain)

    surface = Color(0xFF111111),      // darker than before
    onSurface = Color(0xFFEDEDED),

    error = Color(0xFFE53935),
    onError = Color.Black,

    outline = Color(0xFFEDEDED),      // not pure white → better balance
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFFF3B3B),      // bold red
    onPrimary = Color.Black,

    secondary = Color(0xFF00E5FF),    // neon cyan
    onSecondary = Color.Black,

    tertiary = Color(0xFFFFFF00),     // 🔥 yellow accent
    onTertiary = Color.Black,

    background = Color(0xFFF3F3F3),   // ✅ black background
    onBackground = Color.Black,

    surface = Color(0xFF000000),      // white cards
    onSurface = Color.White,

    error = Color(0xFFFF1744),        // brighter brutal red
    onError = Color.Black,

    outline = Color.White,            // borders visible on black
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black
)

@Composable
fun ChatApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(
            small = RoundedCornerShape(0.dp),
            medium = RoundedCornerShape(0.dp),
            large = RoundedCornerShape(0.dp),
        ),
        content = content
    )
}