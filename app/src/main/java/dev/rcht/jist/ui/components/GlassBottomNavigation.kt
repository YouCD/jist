package dev.rcht.jist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.navigation.NavController
import dev.rcht.jist.ui.BottomNavItem
import dev.rcht.jist.ui.navigation.Screen
import dev.rcht.jist.ui.theme.JistCyan
import dev.rcht.jist.ui.theme.JistPurple

@Composable
fun GlassBottomNavigation(
    navController: NavController,
    items: List<BottomNavItem>,
    currentRoute: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 0.dp, vertical = 0.dp)
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                spotColor = JistCyan.copy(alpha = 0.2f),
                ambientColor = Color.Black
            )
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E).copy(alpha = 0.95f),
                        Color(0xFF16213E).copy(alpha = 0.98f)
                    )
                )
            )
            // Add border for glass effect
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.05f)
                    )
                ),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 0.dp),
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
                                popUpTo(Screen.Dashboard.route) {
                                    saveState = true
                                }
                                restoreState = true
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
    
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null, // Disable ripple for cleaner look
                onClick = onClick
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing Indicator
        Box(
            modifier = Modifier
                .height(3.dp)
                .width(40.dp)
                .offset(y = (-9).dp)
                .background(
                    color = if (selected) JistCyan else Color.Transparent,
                    shape = RoundedCornerShape(2.dp)
                )
                .shadow(
                    elevation = if (selected) 4.dp else 0.dp,
                    shape = RoundedCornerShape(2.dp),
                    spotColor = JistCyan,
                    ambientColor = JistCyan
                )
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Icon
        Icon(
            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
            contentDescription = item.label,
            tint = if (selected) JistCyan else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Label
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) JistCyan else Color.White.copy(alpha = 0.4f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
private fun GlassBottomNavigationPreview() {
    dev.rcht.jist.ui.theme.JistTheme {
        Box(modifier = Modifier.background(Color.Black).padding(0
            .dp)) {
            val navController = androidx.navigation.compose.rememberNavController()
            GlassBottomNavigation(
                navController = navController,
                items = dev.rcht.jist.ui.bottomNavItems,
                currentRoute = "dashboard"
            )
        }
    }
}
