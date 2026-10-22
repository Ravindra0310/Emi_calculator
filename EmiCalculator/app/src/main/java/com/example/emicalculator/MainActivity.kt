package com.example.emicalculator

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.adslibrary.AdsLibrary
import com.example.emicalculator.navigation.AppNavGraph
import com.example.emicalculator.ui.home.EmiViewModel
import com.example.emicalculator.ui.theme.EmiCalculatorTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val tag = "MainActivity"
    private val viewModel: EmiViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Gather / refresh UMP consent on every launch.
        // 2. Initialize the Mobile Ads SDK once consent permits ad requests.
        AdsLibrary.gatherConsent(this) { error ->
            if (error != null) {
                Log.w(tag, "Consent gathering returned notice: ${error.message}")
            }
            if (AdsLibrary.canRequestAds) {
                AdsLibrary.initializeSdk(this)
            }
        }
        // Also init immediately if the previous session already granted consent.
        if (AdsLibrary.canRequestAds) {
            AdsLibrary.initializeSdk(this)
        }

        setContent {
            val selectedLanguage by viewModel.selectedLanguage.collectAsState()

            LaunchedEffect(selectedLanguage) {
                val locale = Locale(selectedLanguage)
                Locale.setDefault(locale)
                val config = resources.configuration
                config.setLocale(locale)
                @Suppress("DEPRECATION")
                resources.updateConfiguration(config, resources.displayMetrics)
            }

            EmiCalculatorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavGraph(
                        navController = navController,
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
