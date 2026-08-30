package com.devcraft.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devcraft.R

val BrandNavy = Color(0xFF14213D)
val BrandSteel = Color(0xFF4A6491)

// ponytail: light scheme only. Screens still carry ~30 hardcoded light-mode
// hex colors, so adding a dark scheme here would make them unreadable rather
// than dark. Extract those to tokens first, then add darkColorScheme().
private val DevCraftLightColors = lightColorScheme(
    primary = BrandNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE3F2),
    onPrimaryContainer = BrandNavy,
    secondary = BrandSteel,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4EAF5),
    onSecondaryContainer = Color(0xFF1B2A45),
    background = Color(0xFFFBFCFE),
    onBackground = Color(0xFF101828),
    surface = Color(0xFFFBFCFE),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0xFFEEF1F6),
    onSurfaceVariant = Color(0xFF4A5568),
    outline = Color(0xFFC4CCDA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun DevCraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DevCraftLightColors, content = content)
}

/**
 * The launcher icon rendered in-app: brand navy tile, white D. Same geometry as
 * res/drawable/ic_brand_d.xml, so the header and the home-screen icon read as
 * one mark.
 */
@Composable
fun DevCraftMark(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    containerColor: Color = BrandNavy,
    markColor: Color = Color.White,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_brand_d),
            contentDescription = null,
            tint = markColor,
            modifier = Modifier.size(size * 0.56f),
        )
    }
}

/** Mark + "DevCraft" + "by Neutron". */
@Composable
fun DevCraftLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 36.dp,
    nameSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    showByline: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DevCraftMark(size = markSize)
        Column {
            Text(
                text = stringResource(R.string.brand_name),
                fontSize = nameSize,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.2).sp,
            )
            if (showByline) {
                Text(
                    text = stringResource(R.string.brand_byline),
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
