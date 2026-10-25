package com.carecircle.ui.family

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The family view has its own calm teal look so it never reads as the patient's health app. */
object FamilyColors {
    val Bg = Color(0xFFEAF3F4)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0B2E33)
    val Muted = Color(0xFF63797D)
    val Teal = Color(0xFF0E8A94)
    val TealSoft = Color(0xFFD3ECEE)
    val Good = Color(0xFF2E9E6B)
    val GoodSoft = Color(0xFFDDF3E8)
    val Bad = Color(0xFFE5533D)
    val BadSoft = Color(0xFFFCE3DE)
    val Warn = Color(0xFFD99A00)
    val WarnSoft = Color(0xFFFFF1CC)
    val Line = Color(0xFFD5E3E5)
}

@Composable
fun FCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, tint: Color = FamilyColors.Surface, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.clip(RoundedCornerShape(26.dp)).background(tint).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Column(m.padding(18.dp), content = content)
}

@Composable
fun FChip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(text, modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 5.dp), color = fg, fontSize = 13.sp)
}

/** A progress ring that sweeps to its value. Tap handling is up to the caller. */
@Composable
fun FRing(fraction: Float, size: Dp, stroke: Dp, color: Color, modifier: Modifier = Modifier, track: Color = FamilyColors.Line, center: @Composable () -> Unit) {
    val sweep by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx()
            val inset = w / 2f
            val arc = Size(this.size.width - w, this.size.height - w)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            if (sweep > 0f) drawArc(color, -90f, 360f * sweep, false, Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        center()
    }
}

data class FNav(val route: String, val label: String, val icon: ImageVector)

@Composable
fun FNavBar(items: List<FNav>, selected: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(FamilyColors.Surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).navigationBarsPadding().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val on = item.route == selected
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Tab) { onSelect(item.route) }.padding(horizontal = 18.dp, vertical = 2.dp)
                    .semantics { contentDescription = item.label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(if (on) FamilyColors.Teal else Color.Transparent), contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = if (on) Color.White else FamilyColors.Muted, modifier = Modifier.size(24.dp))
                }
                Text(item.label, fontSize = 11.sp, color = if (on) FamilyColors.Ink else FamilyColors.Muted)
            }
        }
    }
}
