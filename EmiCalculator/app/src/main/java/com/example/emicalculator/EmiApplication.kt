package com.example.emicalculator

import android.app.Application
import com.example.adslibrary.AdsConfig
import com.example.adslibrary.AdsLibrary
import com.example.adslibrary.TestAdUnitIds
import com.example.emicalculator.data.AppPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EmiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        // Initialize the AdsLibrary once.
        // Replace test IDs below with your production ad unit IDs before release.
        AdsLibrary.initialize(
            context = this,
            config = AdsConfig(
                appId                = TestAdUnitIds.APP_ID,          // replace with real APP ID
                testMode             = true,                          // set false for production
                enableBannerAds      = true,
                enableInterstitialAds = true,
                enableRewardedAds    = true,
                enableNativeAds      = true,
                enableAppOpenAds     = true,
                showAppOpenOnFirstLaunch = false,
                enableLogging        = true,                          // set false for production
            ),
        )

        // Suppress App Open ad on very first onboarding launch.
        applicationScope.launch {
            val prefs = AppPreferencesRepository.getInstance(this@EmiApplication)
            val isOnboardingDone = prefs.isOnboardingCompleted.first()
            // The AdsLibrary handles first-launch suppression via showAppOpenOnFirstLaunch;
            // this block keeps the existing onboarding-aware logic intact.
            if (!isOnboardingDone) {
                // Nothing to do — showAppOpenOnFirstLaunch=false already handles this.
            }
        }
    }
}
