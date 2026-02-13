package com.dropindh.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dropindh.app.ui.screens.charts.ChartsScreen
import com.dropindh.app.ui.screens.community.CommunityScreen
import com.dropindh.app.ui.screens.compare.CompareScreen
import com.dropindh.app.ui.screens.events.EventsScreen
import com.dropindh.app.ui.screens.history.HistoryScreen
import com.dropindh.app.ui.screens.home.HomeScreen
import com.dropindh.app.ui.screens.map.MapScreen
import com.dropindh.app.ui.screens.pro.ProScreen
import com.dropindh.app.ui.screens.recording.RecordingScreen
import com.dropindh.app.ui.screens.runsummary.RunSummaryScreen
import com.dropindh.app.ui.screens.trackdetail.TrackDetailScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Recording : Screen("recording/{trackId}") {
        fun createRoute(trackId: String) = "recording/$trackId"
    }
    data object RunSummary : Screen("run_summary/{runId}") {
        fun createRoute(runId: String) = "run_summary/$runId"
    }
    data object Compare : Screen("compare/{trackId}/{runIds}") {
        fun createRoute(trackId: String, runIds: List<String>) = 
            "compare/$trackId/${runIds.joinToString(",")}"
        // Legacy support for 2 runs
        fun createRoute(trackId: String, runAId: String, runBId: String) = 
            "compare/$trackId/$runAId,$runBId"
    }
    data object Charts : Screen("charts/{trackId}/{runIds}") {
        fun createRoute(trackId: String, runIds: List<String>) = 
            "charts/$trackId/${runIds.joinToString(",")}"
        // Legacy support for 2 runs
        fun createRoute(trackId: String, runAId: String, runBId: String) = 
            "charts/$trackId/$runAId,$runBId"
    }
    data object Events : Screen("events/{runId}") {
        fun createRoute(runId: String) = "events/$runId"
    }
    data object RunMap : Screen("run_map/{runId}") {
        fun createRoute(runId: String) = "run_map/$runId"
    }
    data object History : Screen("history")
    data object Pro : Screen("pro")
    data object Community : Screen("community")
    data object TrackDetail : Screen("track_detail/{trackId}") {
        fun createRoute(trackId: String) = "track_detail/$trackId"
    }
}

@Composable
fun DHMeterNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onStartRun = { trackId ->
                    navController.navigate(Screen.Recording.createRoute(trackId))
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToTrackDetail = { trackId ->
                    navController.navigate(Screen.TrackDetail.createRoute(trackId))
                },
                onNavigateToPro = {
                    navController.navigate(Screen.Pro.route)
                },
                onNavigateToCommunity = {
                    navController.navigate(Screen.Community.route)
                }
            )
        }

        composable(
            route = Screen.Recording.route,
            arguments = listOf(navArgument("trackId") { type = NavType.StringType })
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId") ?: return@composable
            RecordingScreen(
                trackId = trackId,
                onRunCompleted = { runId ->
                    navController.navigate(Screen.RunSummary.createRoute(runId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.RunSummary.route,
            arguments = listOf(navArgument("runId") { type = NavType.StringType })
        ) { backStackEntry ->
            val runId = backStackEntry.arguments?.getString("runId") ?: return@composable
            RunSummaryScreen(
                runId = runId,
                onCompare = { trackId, runAId, runBId ->
                    navController.navigate(Screen.Compare.createRoute(trackId, runAId, runBId))
                },
                onViewEvents = {
                    navController.navigate(Screen.Events.createRoute(runId))
                },
                onViewMap = {
                    navController.navigate(Screen.RunMap.createRoute(runId))
                },
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Screen.Home.route) {
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.Compare.route,
            arguments = listOf(
                navArgument("trackId") { type = NavType.StringType },
                navArgument("runIds") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId") ?: return@composable
            val runIdsStr = backStackEntry.arguments?.getString("runIds") ?: return@composable
            val runIds = runIdsStr.split(",")
            CompareScreen(
                trackId = trackId,
                runIds = runIds,
                onViewCharts = {
                    navController.navigate(Screen.Charts.createRoute(trackId, runIds))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Charts.route,
            arguments = listOf(
                navArgument("trackId") { type = NavType.StringType },
                navArgument("runIds") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId") ?: return@composable
            val runIdsStr = backStackEntry.arguments?.getString("runIds") ?: return@composable
            val runIds = runIdsStr.split(",")
            ChartsScreen(
                trackId = trackId,
                runIds = runIds,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.Events.route,
            arguments = listOf(navArgument("runId") { type = NavType.StringType })
        ) { backStackEntry ->
            val runId = backStackEntry.arguments?.getString("runId") ?: return@composable
            EventsScreen(
                runId = runId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.RunMap.route,
            arguments = listOf(navArgument("runId") { type = NavType.StringType })
        ) { backStackEntry ->
            val runId = backStackEntry.arguments?.getString("runId") ?: return@composable
            MapScreen(
                runId = runId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onTrackSelected = { trackId ->
                    navController.navigate(Screen.TrackDetail.createRoute(trackId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Community.route) {
            CommunityScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Pro.route) {
            ProScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.TrackDetail.route,
            arguments = listOf(navArgument("trackId") { type = NavType.StringType })
        ) { backStackEntry ->
            val trackId = backStackEntry.arguments?.getString("trackId") ?: return@composable
            TrackDetailScreen(
                trackId = trackId,
                onRunSelected = { runId ->
                    navController.navigate(Screen.RunSummary.createRoute(runId))
                },
                onCompareRuns = { runIds ->
                    navController.navigate(Screen.Compare.createRoute(trackId, runIds))
                },
                onStartNewRun = {
                    navController.navigate(Screen.Recording.createRoute(trackId))
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

