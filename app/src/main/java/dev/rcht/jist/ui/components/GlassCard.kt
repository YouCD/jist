package dev.rcht.jist.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.rcht.jist.ui.theme.GlassBorder
import dev.rcht.jist.ui.theme.GlassSurface

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    hazeState: HazeState? = null,
    containerColor: Color? = null,
    content: @Composable () -> Unit
) {
    val cardModifier = if (hazeState != null) {
        modifier.hazeEffect(
            state = hazeState,
            style = HazeMaterials.ultraThin()
        )
    } else {
        modifier
    }
    
    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: if (hazeState != null) Color.Transparent else GlassSurface,
            contentColor = Color.White
        ),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        content()
    }
}
