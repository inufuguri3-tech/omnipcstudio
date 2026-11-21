package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.security.configureAppCheck
import com.example.ui.screens.HistoryAndSettingsScreen
import com.example.ui.screens.LiveProScreen
import com.example.ui.screens.OperatorScreen
import com.example.ui.screens.PcOperatorScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.OmniLiveTheme
import com.example.viewmodel.OmniLiveViewModel

object NavRoutes {
    const val LIVE_PRO = "live_pro"
    const val OPERATOR = "operator"
    const val PC_OPERATOR = "pc_operator"
    const val HISTORY = "history"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure App Check with launch intent before rendering UI or creating ViewModels.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            OmniLiveTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg
                ) {
                    OmniLiveApp()
                }
            }
        }
    }
}

@Composable
fun OmniLiveApp(
    viewModel: OmniLiveViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: NavRoutes.LIVE_PRO

    val destinations = listOf(
        NavDestinationItem(
            route = NavRoutes.LIVE_PRO,
            label = "Live Pro",
            selectedIcon = Icons.Filled.AutoAwesome,
            unselectedIcon = Icons.Outlined.AutoAwesome,
            testTag = "nav_tab_live_pro"
        ),
        NavDestinationItem(
            route = NavRoutes.OPERATOR,
            label = "スマホ操作",
            selectedIcon = Icons.Filled.AccessibilityNew,
            unselectedIcon = Icons.Outlined.AccessibilityNew,
            testTag = "nav_tab_operator"
        ),
        NavDestinationItem(
            route = NavRoutes.PC_OPERATOR,
            label = "PC操作",
            selectedIcon = Icons.Filled.Computer,
            unselectedIcon = Icons.Outlined.Computer,
            testTag = "nav_tab_pc_operator"
        ),
        NavDestinationItem(
            route = NavRoutes.HISTORY,
            label = "履歴・設定",
            selectedIcon = Icons.Filled.History,
            unselectedIcon = Icons.Outlined.History,
            testTag = "nav_tab_history"
        )
    )

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = ObsidianBg,
            bottomBar = {
                if (!useRail) {
                    NavigationBar(containerColor = ObsidianSurface) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(containerColor = ObsidianSurface) {
                        destinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTo(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.LIVE_PRO,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(NavRoutes.LIVE_PRO) {
                        LiveProScreen(
                            viewModel = viewModel,
                            onQuickSwitchToOperator = { presetGoal ->
                                viewModel.updateOperatorGoal(presetGoal)
                                navigateTo(NavRoutes.OPERATOR)
                            }
                        )
                    }
                    composable(NavRoutes.OPERATOR) {
                        OperatorScreen(viewModel = viewModel)
                    }
                    composable(NavRoutes.PC_OPERATOR) {
                        PcOperatorScreen(viewModel = viewModel)
                    }
                    composable(NavRoutes.HISTORY) {
                        HistoryAndSettingsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
