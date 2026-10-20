package com.example.emicalculator.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.emicalculator.ui.home.EmiViewModel
import com.example.emicalculator.ui.home.HomeScreen
import com.example.emicalculator.ui.onboarding.OnboardingScreen
import com.example.emicalculator.ui.result.ResultScreen
import com.example.emicalculator.ui.schedule.AmortizationScheduleScreen
import com.example.emicalculator.ui.settings.LanguageSettingsScreen

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Result : Screen("result")
    object Schedule : Screen("schedule")
    object Language : Screen("language")
    object Settings : Screen("settings")
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    viewModel: EmiViewModel
) {
    val isOnboardingDone by viewModel.isOnboardingCompleted.collectAsState()
    val startDestination = if (isOnboardingDone) Screen.Home.route else Screen.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onFinishOnboarding = {
                    viewModel.completeOnboarding()
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToResult = {
                    navController.navigate(Screen.Result.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(Screen.Result.route) {
            ResultScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToSchedule = {
                    navController.navigate(Screen.Schedule.route)
                }
            )
        }

        composable(Screen.Schedule.route) {
            AmortizationScheduleScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Screen.Settings.route) {
            LanguageSettingsScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
