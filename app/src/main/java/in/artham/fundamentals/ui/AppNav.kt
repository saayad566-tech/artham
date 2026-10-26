package `in`.artham.fundamentals.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

object Routes {
    const val HOME = "home"
    const val SCREEN = "screen"
    const val WATCHLIST = "watchlist"
    const val AI = "ai"
    const val DATA = "data"
    const val MODELS = "models"
    const val SETTINGS = "settings"
    const val COMPANY = "company/{id}"
    fun company(id: Long) = "company/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Outlined.Home),
    Tab(Routes.SCREEN, "Screen", Icons.Outlined.FilterAlt),
    Tab(Routes.WATCHLIST, "Lists", Icons.Outlined.Star),
    Tab(Routes.AI, "Analyst", Icons.Outlined.Psychology),
    Tab(Routes.DATA, "Data", Icons.Outlined.Storage)
)

@Composable
fun RootNav(darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val nav: NavHostController = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBar = currentRoute in tabs.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    nav.navigate(tab.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenCompany = { nav.navigate(Routes.company(it)) },
                    onOpenScreen = { nav.navigate(Routes.SCREEN) },
                    onOpenModels = { nav.navigate(Routes.MODELS) },
                    onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                    onOpenData = { nav.navigate(Routes.DATA) }
                )
            }
            composable(Routes.SCREEN) {
                ScreenBuilderScreen(onOpenCompany = { nav.navigate(Routes.company(it)) })
            }
            composable(Routes.WATCHLIST) {
                WatchlistScreen(onOpenCompany = { nav.navigate(Routes.company(it)) })
            }
            composable(Routes.AI) {
                AiScreen(onOpenCompany = { nav.navigate(Routes.company(it)) })
            }
            composable(Routes.DATA) {
                DataScreen(onOpenModels = { nav.navigate(Routes.MODELS) })
            }
            composable(Routes.MODELS) {
                ModelsScreen(onBack = { nav.popBackStack() })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    darkTheme = darkTheme,
                    onToggleTheme = onToggleTheme,
                    onBack = { nav.popBackStack() },
                    onOpenModels = { nav.navigate(Routes.MODELS) }
                )
            }
            composable(Routes.COMPANY) { entry ->
                val id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L
                CompanyScreen(
                    companyId = id,
                    onBack = { nav.popBackStack() },
                    onOpenAi = { nav.navigate(Routes.AI) { launchSingleTop = true } }
                )
            }
        }
    }
}

