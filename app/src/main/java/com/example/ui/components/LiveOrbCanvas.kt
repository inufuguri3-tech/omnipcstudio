package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.NeuralIndigo
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class OrbPhase {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun LiveOrbCanvas(
    phase: OrbPhase,
    rmsLevel: Float,
    statusLabel: String,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "orb_transition")
    val rotationAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orb_rotation"
    )
    val pulseScale by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_pulse"
    )

    val primaryAccent = when (phase) {
        OrbPhase.IDLE -> ElectricCyan
        OrbPhase.LISTENING -> EmeraldPulse
        OrbPhase.THINKING -> NeuralIndigo
        OrbPhase.SPEAKING -> ElectricCyan
    }
    val secondaryAccent = when (phase) {
        OrbPhase.IDLE -> NeuralIndigo
        OrbPhase.LISTENING -> ElectricCyan
        OrbPhase.THINKING -> ElectricCyan
        OrbPhase.SPEAKING -> EmeraldPulse
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(136.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val baseRadius = size.minDimension * 0.28f
                val voiceBoost = (rmsLevel / 12f).coerceIn(0f, 1f) * 0.22f
                val effectiveScale = when (phase) {
                    OrbPhase.IDLE -> 0.96f + (pulseScale - 1f) * 0.35f
                    OrbPhase.LISTENING -> pulseScale + voiceBoost
                    OrbPhase.THINKING -> pulseScale * 1.04f
                    OrbPhase.SPEAKING -> pulseScale * 1.08f
                }

                // Ambient glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryAccent.copy(alpha = 0.34f),
                            secondaryAccent.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.50f * effectiveScale
                    ),
                    radius = size.minDimension * 0.50f * effectiveScale,
                    center = center
                )

                // Outer orbital wave rings
                for (ringIndex in 0 until 3) {
                    val ringRadius = baseRadius * (1.28f + ringIndex * 0.24f) * effectiveScale
                    val rad = (rotationAngle + ringIndex * 45f) * (PI / 180.0)
                    val offsetX = (cos(rad) * 3.5f).toFloat()
                    val offsetY = (sin(rad) * 3.5f).toFloat()
                    drawCircle(
                        color = if (ringIndex % 2 == 0) {
                            primaryAccent.copy(alpha = 0.38f - ringIndex * 0.08f)
                        } else {
                            secondaryAccent.copy(alpha = 0.34f - ringIndex * 0.08f)
                        },
                        radius = ringRadius,
                        center = Offset(center.x + offsetX, center.y + offsetY),
                        style = Stroke(width = 2.2f)
                    )
                }

                // Core sphere
                drawCircle(
                    brush = Brush.linearGradient(
                        colors = listOf(primaryAccent, secondaryAccent),
                        start = Offset(center.x - baseRadius, center.y - baseRadius),
                        end = Offset(center.x + baseRadius, center.y + baseRadius)
                    ),
                    radius = baseRadius * effectiveScale,
                    center = center
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = statusLabel,
            style = MaterialTheme.typography.labelMedium,
            color = primaryAccent,
            fontWeight = FontWeight.SemiBold
        )
    }
}
