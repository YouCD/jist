package dev.rcht.jist.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

@Composable
fun StatPanelCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    counter: Int,
    suffix: String,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    height: Dp = 160.dp,
    unreadCount: Int? = null,
    onClick: (() -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null
) {
    GlassCard(
        modifier = (if (onClick != null) modifier.height(height).clickable(onClick = onClick) else modifier.height(height)),
        hazeState = hazeState
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = iconTint,
                    fontWeight = FontWeight.Bold
                )
                if (unreadCount != null && unreadCount > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(iconTint.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "$unreadCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = iconTint,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedCounter(
                    target = counter,
                    suffix = suffix
                )
            }

            if (bottomContent != null) {
                bottomContent()
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun AnimatedCounter(
    target: Int,
    suffix: String,
    color: Color = Color.White
) {
    var animatedState by remember { mutableIntStateOf(0) }
    LaunchedEffect(target) {
        val frames = 20
        for (i in 1..frames) {
            delay(40)
            animatedState = target * i / frames
        }
        animatedState = target
    }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) {
                append(animatedState.toString())
            }
            withStyle(SpanStyle(fontSize = 12.sp, color = color.copy(alpha = 0.6f))) {
                append("\n$suffix")
            }
        },
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
