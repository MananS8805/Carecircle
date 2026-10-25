package com.carecircle.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Minimal CareCircle mark: an open ring (the circle of family) around a heart, with a dot where the ring opens. */
@Composable
fun LogoMark(size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 52f
        withTransform({ scale(s, s, Offset.Zero) }) {
            drawPath(PathParser().parsePathString("M44.02,13.38 A22,22 0 1,1 20.31,4.75").toPath(), Palette.Ink, style = Stroke(4.5f, cap = StrokeCap.Round))
            drawCircle(Palette.Orange, 3.2f, Offset(33.5f, 5.3f))
            drawPath(
                PathParser().parsePathString("M26,39 C16,31.5 13,26 15.5,21.5 C18,17.5 23.5,18 26,22 C28.5,18 34,17.5 36.5,21.5 C39,26 36,31.5 26,39 Z").toPath(),
                Palette.Orange,
            )
        }
    }
}

@Composable
fun BrandRow(modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LogoMark(30.dp)
        Spacer(Modifier.width(10.dp))
        Text("CareCircle", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tint: Color = Palette.Surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = tint)
    val shape = RoundedCornerShape(28.dp)
    if (onClick != null) Card(onClick, modifier, shape = shape, colors = colors) { Column(Modifier.padding(18.dp), content = content) }
    else Card(modifier, shape = shape, colors = colors) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun Pill(text: String, bg: Color = Palette.OrangeSoft, fg: Color = Palette.Ink, modifier: Modifier = Modifier) {
    Text(
        text, modifier.clip(CircleShape).background(bg).padding(horizontal = 12.dp, vertical = 5.dp),
        color = fg, style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
fun BigButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Button(
        onClick, modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp), enabled = enabled, shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Palette.Ink, contentColor = Color.White),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SoftButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick, modifier.defaultMinSize(minHeight = 48.dp), enabled = enabled, shape = CircleShape,
        border = BorderStroke(1.dp, Palette.Line),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Palette.Surface, contentColor = Palette.Ink),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, accent: Color = Palette.Ink) {
    SoftCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Light, color = accent)
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = Palette.Muted)
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/** Bottom bar: the selected destination sits in a black circle, like the reference design. */
@Composable
fun PillNavBar(items: List<NavItem>, selected: String?, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Surface, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .navigationBarsPadding().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val on = item.route == selected
            Column(
                Modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Tab) { onSelect(item.route) }
                    .defaultMinSize(minWidth = 64.dp).padding(vertical = 2.dp)
                    .semantics { contentDescription = item.label },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(if (on) Palette.Ink else Color.Transparent),
                    contentAlignment = Alignment.Center,
                ) { Icon(item.icon, null, tint = if (on) Color.White else Palette.Muted, modifier = Modifier.size(24.dp)) }
                Text(item.label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp), color = if (on) Palette.Ink else Palette.Muted)
            }
        }
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier, color: Color = Palette.Orange) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Palette.Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(8.dp).clip(CircleShape).background(color))
    }
}
