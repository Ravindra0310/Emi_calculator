package com.example.emicalculator.ads

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicBoolean

// =========================================================================
// 1. AD UNIT IDS (Google Test IDs)
// =========================================================================
object AdConstants {
    const val APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val BANNER_HOME = "ca-app-pub-3940256099942544/9214589741"
    const val INTERSTITIAL_CALCULATION = "ca-app-pub-3940256099942544/1033173712"
    const val INTERSTITIAL_SCHEDULE_RETURN = "ca-app-pub-3940256099942544/1033173712"
    const val REWARDED_SCHEDULE_UNLOCK = "ca-app-pub-3940256099942544/5224354917"
    const val REWARDED_PDF_EXPORT = "ca-app-pub-3940256099942544/5224354917"
    const val NATIVE_LANGUAGE_SCREEN = "ca-app-pub-3940256099942544/2247696110"
    const val NATIVE_ONBOARDING_A = "ca-app-pub-3940256099942544/1044960115"
    const val NATIVE_ONBOARDING_B = "ca-app-pub-3940256099942544/1044960115"
    const val NATIVE_RESULT_SCREEN = "ca-app-pub-3940256099942544/2247696110"
    const val APP_OPEN_AD = "ca-app-pub-3940256099942544/9257395921"
}

// =========================================================================
// 2. CENTRAL AD POLICY (Frequency & Throttling Rules)
// =========================================================================
object AdPolicy {
    private const val MIN_INTERSTITIAL_INTERVAL_MS = 60_000L // 60s cooldown
    private const val MIN_APP_OPEN_INTERVAL_MS = 30_000L     // 30s cooldown
    const val MAX_APP_OPEN_CACHE_AGE_MS = 4 * 60 * 60 * 1000L // 4 hours

    private var lastInterstitialShownTime: Long = 0L
    private var lastAppOpenShownTime: Long = 0L
    private var validCalculationCount: Int = 0

    val isFullScreenAdShowing = AtomicBoolean(false)
    var isFirstLaunch: Boolean = true

    private var onboardingNativeAShown: Boolean = false
    private var onboardingNativeBShown: Boolean = false

    @Synchronized
    fun onValidCalculation(): Boolean {
        validCalculationCount++
        return (validCalculationCount > 1 && validCalculationCount % 2 == 0) && canShowInterstitial()
    }

    @Synchronized
    fun canShowScheduleReturnInterstitial(): Boolean = canShowInterstitial()

    @Synchronized
    fun canShowInterstitial(): Boolean {
        if (isFullScreenAdShowing.get()) return false
        val elapsed = SystemClock.elapsedRealtime() - lastInterstitialShownTime
        return lastInterstitialShownTime == 0L || elapsed >= MIN_INTERSTITIAL_INTERVAL_MS
    }

    @Synchronized
    fun recordInterstitialShown() {
        lastInterstitialShownTime = SystemClock.elapsedRealtime()
    }

    @Synchronized
    fun canShowAppOpenAd(adLoadedWallClock: Long): Boolean {
        if (isFirstLaunch || isFullScreenAdShowing.get()) return false
        val intervalOk = lastAppOpenShownTime == 0L || (SystemClock.elapsedRealtime() - lastAppOpenShownTime >= MIN_APP_OPEN_INTERVAL_MS)
        val cacheValid = (System.currentTimeMillis() - adLoadedWallClock) in 0..MAX_APP_OPEN_CACHE_AGE_MS
        return intervalOk && cacheValid
    }

    @Synchronized
    fun recordAppOpenAdShown() {
        lastAppOpenShownTime = SystemClock.elapsedRealtime()
    }

    @Synchronized fun canShowOnboardingNativeA() = !onboardingNativeAShown
    @Synchronized fun markOnboardingNativeAShown() { onboardingNativeAShown = true }
    @Synchronized fun canShowOnboardingNativeB() = !onboardingNativeBShown
    @Synchronized fun markOnboardingNativeBShown() { onboardingNativeBShown = true }
}
