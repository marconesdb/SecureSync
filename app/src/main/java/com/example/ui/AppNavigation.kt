package com.example.ui

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.viewmodel.AuthViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.TaskViewModel

@Composable
fun AppNavigation(
    authViewModel: AuthViewModel,
    taskViewModel: TaskViewModel,
    settingsViewModel: SettingsViewModel
) {
    val navController = rememberNavController()
    val currentUserId by authViewModel.currentUserId.collectAsState()

    // Determine startup screen depending on active session state
    val startDestination = if (currentUserId != null) "dashboard" else "login"

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // Login Screen Route
        composable("login") {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToSignUp = {
                    navController.navigate("signup")
                },
                onNavigateToForgotPassword = {
                    navController.navigate("forgot_password")
                }
            )
        }

        // Signup Route
        composable("signup") {
            SignUpScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDashboard = {
                    navController.navigate("dashboard") {
                        popUpTo("login") { inclusive = true }
                        popUpTo("signup") { inclusive = true }
                    }
                }
            )
        }

        // Forgot password route
        composable("forgot_password") {
            ForgotPasswordScreen(
                viewModel = authViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Dashboard Screen Route
        composable("dashboard") {
            DashboardScreen(
                authViewModel = authViewModel,
                taskViewModel = taskViewModel,
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToTaskForm = { taskId ->
                    navController.navigate("task_form/$taskId")
                }
            )
        }

        // Task Form Route (Add & Edit combined)
        composable(
            route = "task_form/{taskId}",
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: "new"
            TaskFormScreen(
                taskId = taskId,
                taskViewModel = taskViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // Settings Route
        composable("settings") {
            SettingsScreen(
                authViewModel = authViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate("login") {
                        popUpTo("dashboard") { inclusive = true }
                        popUpTo("settings") { inclusive = true }
                    }
                }
            )
        }
    }
}
