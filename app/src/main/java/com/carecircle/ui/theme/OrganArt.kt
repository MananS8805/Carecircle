package com.carecircle.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.carecircle.domain.OrganKind
import kotlinx.coroutines.launch

/**
 * Stylised organs drawn from vector paths on a 100x100 grid. Each one animates gently (heart beats,
 * lungs breathe, the rest float) and shows an orange "focus" dot where the condition acts.
 * Tapping sends a ripple from that dot and calls [onTap].
 */
@Composable
fun OrganView(
    kind: OrganKind,
    modifier: Modifier = Modifier,
    description: String = "",
    animate: Boolean = true,
    onTap: (() -> Unit)? = null,
) {
    val infinite = rememberInfiniteTransition(label = "organ")
    val slow by infinite.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "slow",
    )
    val beat by infinite.animateFloat(
        0f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 1100; 0f at 0; 1f at 130; 0.15f at 280; 0.6f at 400; 0f at 560; 0f at 1100 }),
        label = "beat",
    )
    val glow by infinite.animateFloat(0.35f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse), label = "glow")
    val ripple = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(onTap) {
                detectTapGestures {
                    scope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(800)) }
                    onTap?.invoke()
                }
            },
    ) {
        val s = size.minDimension / 100f
        val a = if (animate) 1f else 0f
        withTransform({
            translate((size.width - 100f * s) / 2f, (size.height - 100f * s) / 2f)
            scale(s, s, Offset.Zero)
        }) {
            drawGroundShadow()
            val bob = (slow - 0.5f) * 2.4f * a
            withTransform({ translate(0f, bob) }) {
                when (kind) {
                    OrganKind.HEART -> withTransform({ val k = 1f + 0.055f * beat * a; scale(k, k, Offset(50f, 58f)) }) { heart() }
                    OrganKind.LUNGS -> withTransform({ val k = 1f + 0.04f * slow * a; scale(k, k, Offset(50f, 60f)) }) { lungs() }
                    OrganKind.PANCREAS -> pancreas()
                    OrganKind.KIDNEY -> kidney()
                    OrganKind.BRAIN -> brain()
                    OrganKind.THYROID -> thyroid()
                    OrganKind.JOINT -> joint(glow * a)
                    OrganKind.BODY -> body()
                }
                focus(hotspot(kind), glow * a + (1f - a), ripple.value)
            }
        }
    }
}

private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

private val heartBody = path("M50,93 C18,72 8,50 16,32 C24,15 44,14 50,30 C56,14 78,15 85,33 C92,52 82,73 50,93 Z")
private val heartAorta = path("M44,30 C40,14 54,4 68,8 C80,12 80,26 74,34")
private val heartCava = path("M64,26 C66,16 66,12 66,6")
private val heartPulm = path("M52,30 C56,20 40,12 28,16")
private val heartVessel1 = path("M30,38 C34,52 42,66 52,82")
private val heartVessel2 = path("M66,40 C64,54 58,68 52,82")
private val heartShine = path("M24,42 C23,30 32,23 40,25 C33,31 30,40 30,52 Z")

private val pancreasBody = path("M8,60 C20,42 46,46 66,40 C84,34 96,42 92,54 C88,66 70,62 54,66 C38,70 22,80 10,70 C6,66 6,63 8,60 Z")
private val pancreasDuct = path("M12,64 C30,56 52,54 88,48")

private val lungLobe = path("M44,32 C28,28 10,50 10,74 C10,88 28,90 40,82 C48,76 48,52 44,32 Z")
private val lungBronchus = path("M50,36 C46,42 40,46 32,52")
private val lungBranchA = path("M40,46 C36,56 30,62 26,70")
private val lungBranchB = path("M38,50 C40,60 38,68 36,76")

private val kidneyBody = path("M62,8 C88,10 94,40 82,62 C74,78 58,94 42,88 C30,82 38,68 46,60 C52,52 40,44 34,36 C26,20 44,6 62,8 Z")
private val kidneyArtery = path("M44,50 C34,50 26,52 14,50")
private val kidneyVein = path("M44,58 C34,60 26,64 16,68")
private val kidneyUreter = path("M42,76 C38,86 36,92 38,99")
private val kidneyShine = path("M70,24 C80,36 78,52 66,66")

private val brainBody = path("M50,16 C68,4 94,18 90,40 C99,54 88,72 71,70 C65,84 40,86 33,71 C15,73 5,55 13,41 C7,25 31,6 50,16 Z")
private val brainStem = path("M52,70 C54,82 54,90 52,98")
private val brainFissure = path("M50,16 C46,32 52,46 48,66")
private val brainFolds = listOf(
    path("M24,36 C32,30 38,38 32,44 C28,50 36,54 40,48"),
    path("M76,34 C68,28 62,38 68,44 C72,50 64,54 60,48"),
    path("M20,56 C28,52 34,60 28,64"),
    path("M80,56 C72,52 66,60 72,64"),
    path("M36,20 C42,20 44,28 40,30"),
    path("M64,20 C58,20 56,28 60,30"),
)

private val thyroidLobe = path("M47,48 C42,32 20,28 15,42 C10,58 22,74 40,72 C46,70 48,60 47,48 Z")
private val thyroidIsthmus = path("M44,52 C48,48 52,48 56,52 L56,60 C52,63 48,63 44,60 Z")

private val femur = path("M37,0 L37,36 C20,38 20,62 40,60 L60,60 C80,62 80,38 63,36 L63,0 Z")
private val tibia = path("M32,100 L32,80 C30,72 34,70 40,70 L60,70 C66,70 70,72 68,80 L68,100 Z")

private val bodyShape = path("M30,36 C30,30 70,30 70,36 L74,64 C74,69 66,69 66,64 L64,98 L52,98 L50,72 L48,98 L36,98 L34,64 C34,69 26,69 26,64 Z")

private fun DrawScope.fill(p: Path, a: Color, b: Color, c: Color = b, from: Offset = Offset(30f, 20f), radius: Float = 90f) =
    drawPath(p, Brush.radialGradient(listOf(a, b, c), center = from, radius = radius))

private fun DrawScope.line(p: Path, color: Color, w: Float) =
    drawPath(p, color, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun DrawScope.drawGroundShadow() = drawOval(
    Brush.radialGradient(listOf(Color(0x33000000), Color.Transparent), center = Offset(50f, 98f), radius = 36f),
    topLeft = Offset(14f, 92f), size = Size(72f, 12f),
)

private fun DrawScope.heart() {
    line(heartCava, Color(0xFF9E2616), 6f)
    line(heartPulm, Color(0xFFB02D1B), 7f)
    line(heartAorta, Color(0xFFC22A18), 9f)
    fill(heartBody, Color(0xFFEA5236), Color(0xFFC8301C), Color(0xFF8E160C), from = Offset(36f, 34f), radius = 80f)
    line(heartVessel1, Color(0xCC3B5B91), 2.6f)
    line(heartVessel2, Color(0xCC3B5B91), 2.6f)
    drawPath(heartShine, Color(0x38FFFFFF))
}

private fun DrawScope.pancreas() {
    fill(pancreasBody, Color(0xFFFFC2A6), Color(0xFFEE8D70), Color(0xFFC95A44), from = Offset(30f, 46f), radius = 80f)
    line(pancreasDuct, Color(0x99B8503A), 2f)
    listOf(28f to 56f, 40f to 54f, 52f to 52f, 64f to 50f, 76f to 46f, 34f to 64f, 48f to 62f, 60f to 60f)
        .forEach { (x, y) -> drawCircle(Color(0x73FFF3E4), 1.7f, Offset(x, y)) }
}

private fun DrawScope.lungs() {
    line(Path().apply { moveTo(50f, 4f); lineTo(50f, 38f) }, Color(0xFFE9C7B8), 7f)
    for (y in listOf(10f, 17f, 24f, 31f)) line(Path().apply { moveTo(46.5f, y); lineTo(53.5f, y) }, Color(0xFFD2A998), 1.2f)
    repeat(2) { side ->
        withTransform({ if (side == 1) scale(-1f, 1f, Offset(50f, 0f)) }) {
            fill(lungLobe, Color(0xFFFFB7B0), Color(0xFFE9768A), Color(0xFFB94868), from = Offset(24f, 48f), radius = 62f)
            line(lungBronchus, Color(0xFFE9C7B8), 4f)
            line(lungBranchA, Color(0x99FFF1EA), 1.8f)
            line(lungBranchB, Color(0x99FFF1EA), 1.8f)
        }
    }
}

private fun DrawScope.kidney() {
    line(kidneyUreter, Color(0xFFD9A18E), 4f)
    fill(kidneyBody, Color(0xFFD9593F), Color(0xFFA2321F), Color(0xFF62150E), from = Offset(60f, 30f), radius = 72f)
    line(kidneyShine, Color(0x40FFFFFF), 2.2f)
    line(kidneyArtery, Color(0xFF8E2A22), 6f)
    line(kidneyVein, Color(0xFF4A64A0), 5f)
}

private fun DrawScope.brain() {
    line(brainStem, Color(0xFFE3A0AA), 8f)
    fill(brainBody, Color(0xFFFFC9CD), Color(0xFFEE97A4), Color(0xFFC96C80), from = Offset(36f, 28f), radius = 74f)
    line(brainFissure, Color(0xFFB45A6E), 2.2f)
    brainFolds.forEach { line(it, Color(0xFFC1707F), 2f) }
}

private fun DrawScope.thyroid() {
    drawRoundRect(Color(0xFFDDE1E8), Offset(43f, 0f), Size(14f, 100f), CornerRadius(7f))
    for (y in 6..94 step 9) line(Path().apply { moveTo(43f, y.toFloat()); lineTo(57f, y.toFloat()) }, Color(0xFFBCC3CF), 1.2f)
    repeat(2) { side ->
        withTransform({ if (side == 1) scale(-1f, 1f, Offset(50f, 0f)) }) {
            fill(thyroidLobe, Color(0xFFFFB27F), Color(0xFFE67F4E), Color(0xFFB04A2B), from = Offset(24f, 44f), radius = 50f)
        }
    }
    fill(thyroidIsthmus, Color(0xFFF09A66), Color(0xFFC85B35), from = Offset(50f, 52f), radius = 14f)
}

private fun DrawScope.joint(glow: Float) {
    fill(femur, Color(0xFFFFF7EC), Color(0xFFE6D3B6), Color(0xFFC9AE88), from = Offset(40f, 30f), radius = 60f)
    drawRoundRect(Color(0xFFBFD9F2), Offset(30f, 62f), Size(40f, 7f), CornerRadius(3.5f))
    fill(tibia, Color(0xFFFFF7EC), Color(0xFFE6D3B6), Color(0xFFC9AE88), from = Offset(50f, 80f), radius = 50f)
    drawCircle(Brush.radialGradient(listOf(Color(0x99F26A21).copy(alpha = 0.6f * glow), Color.Transparent), center = Offset(50f, 66f), radius = 26f), 26f, Offset(50f, 66f))
}

private fun DrawScope.body() {
    drawCircle(Brush.radialGradient(listOf(Color(0x33F26A21), Color.Transparent), center = Offset(50f, 52f), radius = 52f), 52f, Offset(50f, 52f))
    fill(bodyShape, Color(0xFFFFC3A0), Color(0xFFF08A5C), Color(0xFFD9562F), from = Offset(40f, 40f), radius = 60f)
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFC3A0), Color(0xFFEE7E50)), center = Offset(46f, 14f), radius = 14f), 11f, Offset(50f, 17f))
}

private fun hotspot(kind: OrganKind): Offset = when (kind) {
    OrganKind.HEART -> Offset(58f, 56f)
    OrganKind.PANCREAS -> Offset(62f, 52f)
    OrganKind.LUNGS -> Offset(24f, 64f)
    OrganKind.KIDNEY -> Offset(66f, 40f)
    OrganKind.BRAIN -> Offset(66f, 38f)
    OrganKind.THYROID -> Offset(28f, 56f)
    OrganKind.JOINT -> Offset(50f, 66f)
    OrganKind.BODY -> Offset(50f, 48f)
}

private fun DrawScope.focus(at: Offset, pulse: Float, ripple: Float) {
    drawCircle(Palette.Orange.copy(alpha = 0.22f * pulse), 6.5f + 3f * pulse, at)
    drawCircle(Color.White, 3.4f, at)
    drawCircle(Palette.Orange, 2.4f, at)
    if (ripple < 1f) {
        drawCircle(Palette.Orange.copy(alpha = 0.55f * (1f - ripple)), 4f + 34f * ripple, at, style = Stroke(1.6f))
    }
}

/** Small static organ used in lists and chips. */
@Composable
fun OrganIcon(kind: OrganKind, modifier: Modifier = Modifier) {
    OrganView(kind, modifier.fillMaxSize(), animate = false)
}
