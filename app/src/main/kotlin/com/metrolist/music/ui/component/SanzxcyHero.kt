package com.metrolist.music.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.theme.bbhBartle
import com.sanzxcy.music.com.R

@Composable
fun SanzxcyHeroBanner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "sanzxcyHeroGlow")
    val glow by transition.animateFloat(
        initialValue = .82f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "heroGlow",
    )
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(214.dp)
            .clip(shape)
            .graphicsLayer { alpha = .96f + ((glow - .82f) / .26f) * .04f },
    ) {
        Image(
            painter = painterResource(R.drawable.sanzz),
            contentDescription = "Sanzxmzz banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                Brush.horizontalGradient(
                    listOf(Color(0xA0061413), Color(0x781A3A32), Color(0x3658C9AF)),
                ),
            ),
        )
        Box(
            modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color(0x08101E1B), Color(0xB3081211))),
            ),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = .92f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) { Text("SANZXCY MUSIC", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
                Text("NOW PLAYING", style = MaterialTheme.typography.labelMedium, color = Color(0xFFD8FFF2))
            }
            Text(
                text = "Sanzxcy Music",
                fontFamily = bbhBartle,
                fontSize = 38.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "Your sound. Your space. Your moment.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD0E8E2),
            )
        }
    }
}
