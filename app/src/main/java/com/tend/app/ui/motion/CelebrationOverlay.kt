package com.tend.app.ui.motion

import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tend.app.R
import com.tend.app.ui.theme.Card
import com.tend.app.ui.theme.CardBorder
import com.tend.app.ui.theme.Muted
import com.tend.app.ui.theme.Scrim
import com.tend.app.ui.theme.SpaceGrotesk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A celebration worth having and then getting out of the way.
 *
 * Three rules shape this:
 * - **It never blocks.** The scrim is tap-to-dismiss and the whole thing
 *   retires itself after [TendMotion.CelebrationMs]. Nothing waits on it.
 * - **The card leads, the confetti follows.** The headline springs in
 *   immediately so the message is readable from frame one; the burst plays
 *   behind it rather than in front of it.
 * - **Haptics fire once, with the card.** The two-beat flourish lands as the
 *   card arrives, not staggered across the confetti.
 */
/**
 * How loud a celebration should be. Finishing an entire day earns confetti;
 * a single habit's streak milestone gets the quieter ring, so the big moment
 * stays distinguishable from the frequent one.
 */
enum class CelebrationStyle(@RawRes internal val asset: Int) {
    Burst(R.raw.tend_celebration),
    Ring(R.raw.tend_success_ring),
}

@Composable
fun CelebrationOverlay(
    headline: String,
    detail: String,
    style: CelebrationStyle = CelebrationStyle.Burst,
    onDismiss: () -> Unit,
) {
    val haptics = LocalTendHaptics.current
    val reduceMotion = LocalReduceMotion.current
    val cardScale = remember { Animatable(if (reduceMotion) 1f else 0.7f) }
    val cardAlpha = remember { Animatable(if (reduceMotion) 1f else 0f) }

    LaunchedEffect(headline) {
        launch { haptics.celebrate() }
        if (!reduceMotion) {
            launch { cardAlpha.animateTo(1f, TendMotion.Settle) }
            cardScale.animateTo(1f, TendMotion.EnterSpring)
        }
        delay(TendMotion.CelebrationMs)
        onDismiss()
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Scrim)
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        TendLottie(
            resId = style.asset,
            modifier = Modifier.fillMaxSize(),
            fallback = { ConfettiFallback() },
        )

        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = cardAlpha.value
                }
                .background(Card, RoundedCornerShape(22.dp))
                .border(1.dp, CardBorder, RoundedCornerShape(22.dp))
                .padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                headline,
                fontFamily = SpaceGrotesk,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                detail,
                fontSize = 13.sp,
                color = Muted,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
