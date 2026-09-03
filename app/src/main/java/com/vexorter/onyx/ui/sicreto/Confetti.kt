package com.vexorter.onyx.ui.sicreto

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.vexorter.onyx.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val width: Float,
    val height: Float,
    var rotation: Float,
    val spin: Float,
)

private const val DURATION_SECONDS = 3.2f
private const val GRAVITY = 1500f
private const val DRAG = 0.985f

/**
 * Две хлопушки из нижних углов — как на выпускном.
 *
 * Рисуется поверх всего экрана и не перехватывает касания: расписанием можно
 * пользоваться, пока летит конфетти.
 */
@Composable
fun ConfettiOverlay(
    playing: Boolean,
    colors: List<Color>,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val particles = remember { mutableListOf<Particle>() }
    // Кадры двигают физику, а перерисовку заставляет делать этот счётчик.
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(playing, canvasSize) {
        if (!playing || canvasSize.width == 0 || canvasSize.height == 0) return@LaunchedEffect

        particles.clear()
        particles += launchBurst(canvasSize, colors, fromLeft = true)
        particles += launchBurst(canvasSize, colors, fromLeft = false)

        var elapsed = 0f
        var last = withFrameNanos { it }
        while (elapsed < DURATION_SECONDS) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000.0).toFloat().coerceAtMost(0.05f)
            last = now
            elapsed += dt

            particles.forEach { p ->
                p.vy += GRAVITY * dt
                p.vx *= DRAG
                p.vy *= DRAG
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rotation += p.spin * dt
            }
            frame++
        }

        particles.clear()
        frame++
        onFinished()
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { canvasSize = it }
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frame

        val fade = 1f
        particles.forEach { p ->
            rotate(degrees = p.rotation, pivot = Offset(p.x, p.y)) {
                drawRect(
                    color = p.color.copy(alpha = p.color.alpha * fade),
                    topLeft = Offset(p.x - p.width / 2f, p.y - p.height / 2f),
                    size = Size(p.width, p.height),
                )
            }
        }
    }
}

private fun launchBurst(
    size: IntSize,
    colors: List<Color>,
    fromLeft: Boolean,
): List<Particle> {
    val random = Random.Default
    val originX = if (fromLeft) 0f else size.width.toFloat()
    val originY = size.height * 0.92f
    val palette = colors.ifEmpty { listOf(Color.White) }

    return List(70) {
        // Влево-вверх или вправо-вверх: от 25° до 70° над горизонтом.
        val angle = Math.toRadians(random.nextDouble(25.0, 70.0)).toFloat()
        val speed = random.nextDouble(1300.0, 2400.0).toFloat()
        val direction = if (fromLeft) 1f else -1f

        Particle(
            x = originX + direction * random.nextDouble(0.0, 40.0).toFloat(),
            y = originY - random.nextDouble(0.0, 40.0).toFloat(),
            vx = direction * cos(angle) * speed,
            vy = -sin(angle) * speed,
            color = palette[random.nextInt(palette.size)],
            width = random.nextDouble(9.0, 20.0).toFloat(),
            height = random.nextDouble(5.0, 12.0).toFloat(),
            rotation = random.nextDouble(0.0, 360.0).toFloat(),
            spin = random.nextDouble(-540.0, 540.0).toFloat(),
        )
    }
}

/** Тот самый звук. Плеер освобождаем по завершении, иначе утекут ресурсы. */
fun playCelebrationSound(context: Context) {
    runCatching {
        MediaPlayer.create(context, R.raw.yippee)?.apply {
            setOnCompletionListener { player ->
                runCatching { player.release() }
            }
            start()
        }
    }
}
