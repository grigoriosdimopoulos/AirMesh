package com.airmesh.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.airmesh.ui.screens.chatlist.ChatListScreen
import com.airmesh.ui.screens.conversation.ConversationScreen
import com.airmesh.ui.screens.newchat.NewChatScreen
import com.airmesh.ui.screens.settings.SettingsScreen
import com.airmesh.ui.screens.statistics.StatisticsScreen
import com.airmesh.ui.theme.Gold

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object ChatList : Screen("chatlist", "Chats", Icons.Default.Chat)
    object Statistics : Screen("statistics", "Stats", Icons.Default.BarChart)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

private val bottomNavItems = listOf(Screen.ChatList, Screen.Statistics, Screen.Settings)

@Composable
fun NavGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy
                            ?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = selected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Gold,
                                selectedTextColor = Gold,
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.ChatList.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.ChatList.route) {
                ChatListScreen(
                    onOpenConversation = { peer ->
                        navController.navigate("conversation/$peer")
                    },
                    onNewChat = {
                        navController.navigate("newchat")
                    }
                )
            }
            composable(
                route = "conversation/{peerName}",
                arguments = listOf(navArgument("peerName") { type = NavType.StringType })
            ) { backStackEntry ->
                val peerName = backStackEntry.arguments?.getString("peerName") ?: ""
                ConversationScreen(
                    peerName = peerName,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("newchat") {
                NewChatScreen(
                    onStartChat = { peer ->
                        navController.navigate("conversation/$peer") {
                            popUpTo("newchat") { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Statistics.route) {
                StatisticsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }
}
