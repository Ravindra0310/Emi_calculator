package com.example.emicalculator

import android.app.Application
import com.example.emicalculator.ads.AdPolicy
import com.example.emicalculator.ads.AppOpenAdManager
import com.example.emicalculator.data.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EmiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    lateinit var appOpenAdManager: AppOpenAdManager
        private set

    override fun onCreate() {
        super.onCreate()

        appOpenAdManager = AppOpenAdManager(this)

        // Check if user has already used the app to set firstLaunch policy
        applicationScope.launch {
            val prefs = AppPreferencesRepository.getInstance(this@EmiApplication)
            val isOnboardingDone = prefs.isOnboardingCompleted.first()
            AdPolicy.isFirstLaunch = !isOnboardingDone
        }
    }
}
