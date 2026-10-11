package com.smartexpense.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartexpense.R

object Tokens {
    val background=Color(0xFFF6F5F2)
    val surface=Color(0xFFF0EEEA)
    val secondarySurface=Color(0xFFEAE8E3)
    val inputSurface=Color(0xFFF0EEEA)
    val border=Color(0xFFD6D4CF)
    val text=Color(0xFF111111)
    val muted=Color(0xFF716F69)
    val accent=Color(0xFF181818)
    val expense=text
    val positive=Color(0xFF111111)
    val negative=Color(0xFF703F39)
    val blue=accent
    val chartTones=listOf(accent,muted,Color(0xFF98958E),border,secondarySurface)
    val card=RoundedCornerShape(24.dp)
    val field=RoundedCornerShape(16.dp)
    val pageInset=24.dp
    val sectionGap=12.dp
}
private val ledgerFont=FontFamily.SansSerif
private fun type(size:Int,line:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=ledgerFont,fontSize=size.sp,lineHeight=line.sp,fontWeight=weight,fontFeatureSettings="tnum",letterSpacing=0.sp)
private val ledgerTypography=Typography(
    displaySmall=type(44,50,FontWeight.Medium),
    headlineLarge=type(30,36,FontWeight.SemiBold),
    headlineMedium=type(22,28,FontWeight.SemiBold),
    headlineSmall=type(22,28,FontWeight.SemiBold),
    titleLarge=type(17,24,FontWeight.SemiBold),
    titleMedium=type(15,22,FontWeight.SemiBold),
    titleSmall=type(14,20,FontWeight.Medium),
    bodyLarge=type(15,23),bodyMedium=type(14,21),bodySmall=type(12,18),
    labelLarge=type(14,20,FontWeight.SemiBold),labelMedium=type(12,18,FontWeight.Medium),labelSmall=type(11,16,FontWeight.Medium)
)
@Composable fun TrackerTheme(content:@Composable ()->Unit) {
    MaterialTheme(
        colorScheme=lightColorScheme(primary=Tokens.accent,onPrimary=Color.White,primaryContainer=Color(0xFFEAE8E3),onPrimaryContainer=Tokens.accent,secondary=Tokens.accent,onSecondary=Color.White,secondaryContainer=Tokens.secondarySurface,onSecondaryContainer=Tokens.text,tertiary=Tokens.positive,onTertiary=Tokens.background,background=Tokens.background,surface=Tokens.surface,surfaceVariant=Tokens.secondarySurface,surfaceContainerLowest=Tokens.background,surfaceContainerLow=Tokens.surface,surfaceContainer=Tokens.surface,surfaceContainerHigh=Tokens.secondarySurface,surfaceContainerHighest=Color(0xFFD6D4CF),surfaceTint=Tokens.background,onBackground=Tokens.text,onSurface=Tokens.text,onSurfaceVariant=Tokens.muted,outline=Tokens.muted,outlineVariant=Tokens.border,error=Tokens.negative,onError=Tokens.background,errorContainer=Color(0xFFF2E5E1),onErrorContainer=Tokens.negative,inverseSurface=Tokens.text,inverseOnSurface=Tokens.background,inversePrimary=Tokens.accent),
        typography=ledgerTypography,
        shapes=Shapes(small=Tokens.field,medium=Tokens.card,large=RoundedCornerShape(24.dp)),content=content
    )
}




