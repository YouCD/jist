package dev.rcht.jist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.rcht.jist.ui.BottomNavItem
import dev.rcht.jist.ui.navigation.Screen
import dev.rcht.jist.ui.theme.AppBackground
import dev.rcht.jist.ui.theme.JistCyan
import dev.rcht.jist.ui.theme.JistPurple

@Composable
@OptIn(ExperimentalHazeMaterialsApi::class)
fun GlassBottomNavigation(
    navController: NavController,
    items: List<BottomNavItem>,
    currentRoute: String?,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    val navShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val contentHeight = 72.dp
    val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight + navigationBarPadding)
            .zIndex(1f)
            .shadow(
                elevation = 16.dp,
                shape = navShape,
                spotColor = JistCyan.copy(alpha = 0.2f),
                ambientColor = Color.Black
            )
            .clip(navShape)
            .hazeEffect(
                state = hazeState,
                style = HazeMaterials.ultraThin()
            )
    ) {
        // Base tint with AppBackground color at 85% opacity
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppBackground.copy(alpha = 0.6f))
        )

        // Border / highlight for glass effect
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    ),
                    shape = navShape
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                GlassBottomNavItem(
                    item = item,
                    selected = selected,
                    onClick = {
                        if (!selected) {
                            navController.navigate(item.route) {
                                launchSingleTop = true
                                popUpTo(Screen.Dashboard.route) { inclusive = false }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GlassBottomNavItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pillShape = RoundedCornerShape(16.dp)
    val itemColor = if (selected) JistCyan else Color.White.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (selected) JistCyan.copy(alpha = 0.15f) else Color.Transparent,
                    shape = pillShape
                )
                .padding(horizontal = 16.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                    contentDescription = item.label,
                    tint = itemColor,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = itemColor,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun GlassBottomNavigationPreview() {
    dev.rcht.jist.ui.theme.JistTheme {
        Box(modifier = Modifier.background(AppBackground).padding(0.dp)) {
            val navController = androidx.navigation.compose.rememberNavController()
            val hazeState = remember { HazeState() }
            GlassBottomNavigation(
                navController = navController,
                items = dev.rcht.jist.ui.bottomNavItems(),
                currentRoute = "dashboard",
                hazeState = hazeState
            )
        }
    }
}
