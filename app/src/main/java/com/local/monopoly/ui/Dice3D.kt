package com.local.monopoly.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.local.monopoly.game.Engine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The two dice shown in the board center. Each die is a flat cube face that
 * tumbles in 3D (rotation on X and Y with perspective) and always lands
 * flat showing the exact rolled value. The pip faces flicker during the
 * tumble purely for effect — the RESULT comes from the engine's
 * SecureRandom before the animation even starts.
 */
@Composable
fun DicePair(engine: Engine) {
    val state = engine.state ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DieTumble(
            value = state.dice?.first ?: 1,
            rollId = state.diceAnimId,
            size = 44.dp,
            onSettled = { engine.onDiceAnimationDone() }
        )
        DieTumble(
            value = state.dice?.second ?: 1,
            rollId = state.diceAnimId,
            size = 44.dp,
            onSettled = { engine.onDiceAnimationDone() }
        )
    }
}

@Composable
fun DieTumble(value: Int, rollId: Int, size: Dp, onSettled: () -> Unit) {
    val density = LocalDensity.current
    val camDistance = with(density) { 14.dp.toPx() }

    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    var face by remember { mutableStateOf(value) }

    LaunchedEffect(rollId) {
        if (rollId == 0) return@LaunchedEffect

        // Tumble at least 2 full turns per axis, always ending perfectly flat.
        fun flatTarget(current: Float, spins: Float): Float {
            val base = current + spins * 360f
            val mod = ((base % 360f) + 360f) % 360f
            return base + ((360f - mod) % 360f)
        }
        val tx = flatTarget(rotX.value, 2f)
        val ty = flatTarget(rotY.value, 2f)

        val flicker = launch {
            repeat(5) {
                // cosmetic only — the outcome was already decided by SecureRandom
                face = (Math.random() * 6).toInt() + 1
                delay(110)
            }
        }
        val ax = launch { rotX.animateTo(tx, tween(durationMillis = 780)) }
        val bx = launch { rotY.animateTo(ty, tween(durationMillis = 780)) }
        ax.join()
        bx.join()
        flicker.cancel()
        face = value

        // little landing bounce
        scale.animateTo(1.14f, tween(durationMillis = 110))
        scale.animateTo(1f, tween(durationMillis = 150))
        onSettled()
    }

    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                rotationX = rotX.value
                rotationY = rotY.value
                cameraDistance = camDistance
                scaleX = scale.value
                scaleY = scale.value
            }
            .background(Color.White, RoundedCornerShape(9.dp))
            .border(1.5.dp, Color(0xFFB5AF9E), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Pips(face, size)
    }
}

@Composable
private fun Pips(value: Int, size: Dp) {
    Canvas(Modifier.size(size)) {
        val c = Color(0xFF20242B)
        val r = this.size.minDimension * 0.085f
        val w = this.size.width
        val h = this.size.height
        fun dot(fx: Float, fy: Float) {
            drawCircle(color = c, radius = r, center = Offset(fx * w, fy * h))
        }
        when (value) {
            1 -> dot(0.5f, 0.5f)
            2 -> { dot(0.28f, 0.28f); dot(0.72f, 0.72f) }
            3 -> { dot(0.28f, 0.28f); dot(0.5f, 0.5f); dot(0.72f, 0.72f) }
            4 -> { dot(0.28f, 0.28f); dot(0.72f, 0.28f); dot(0.28f, 0.72f); dot(0.72f, 0.72f) }
            5 -> { dot(0.28f, 0.28f); dot(0.72f, 0.28f); dot(0.5f, 0.5f); dot(0.28f, 0.72f); dot(0.72f, 0.72f) }
            else -> { dot(0.28f, 0.26f); dot(0.72f, 0.26f); dot(0.28f, 0.5f); dot(0.72f, 0.5f); dot(0.28f, 0.74f); dot(0.72f, 0.74f) }
        }
    }
}
