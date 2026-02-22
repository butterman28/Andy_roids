package com.example.greetingcard.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun AppNavigationBar(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    navItems: List<NavItem> = defaultNavItems()
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        navItems.forEach { item ->
            NavigationBarItem(
                selected = selectedRoute == item.route,
                onClick = { onNavigate(item.route) },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

// Default navigation items
fun defaultNavItems(): List<NavItem> {
    return listOf(
        NavItem(
            label = "Home",
            icon = Icons.Default.Home,
            route = "home"
        ),
        NavItem(
            label = "Music",
            icon = Icons.Default.MusicNote,
            route = "downloads"
        ),
        NavItem(
            label = "Playlist",
            icon = Icons.Default.QueueMusic,
            route = "playlist"
        ),
        NavItem(
            label = "Settings",
            icon = Icons.Default.Settings,
            route = "settings"
        )
    )
}

// Custom navigation items builder
@Composable
fun rememberNavItems(
    items: List<NavItem> = defaultNavItems()
): List<NavItem> {
    return remember { items }
}
