package com.example.emicalculator.ads

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference
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

// =========================================================================
// 3. GOOGLE UMP CONSENT MANAGER
// =========================================================================
class ConsentManager private constructor(context: Context) {
    private val consentInfo: ConsentInformation = UserMessagingPlatform.getConsentInformation(context.applicationContext)

    val canRequestAds: Boolean get() = consentInfo.canRequestAds()
    val isPrivacyOptionsRequired: Boolean get() = consentInfo.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun gatherConsent(activity: Activity, onDone: (FormError?) -> Unit) {
        val params = ConsentRequestParameters.Builder().build()
        consentInfo.requestConsentInfoUpdate(activity, params, {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError -> onDone(formError) }
        }, { requestError -> onDone(requestError) })
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismiss: (FormError?) -> Unit) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error -> onDismiss(error) }
    }

    companion object {
        @Volatile private var instance: ConsentManager? = null
        fun getInstance(context: Context): ConsentManager = instance ?: synchronized(this) {
            instance ?: ConsentManager(context).also { instance = it }
        }
    }
}

// =========================================================================
// 4. APP OPEN AD MANAGER (Foreground Lifecycle Handler)
// =========================================================================
class AppOpenAdManager(private val app: Application) : Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    private val consentManager = ConsentManager.getInstance(app)
    private var appOpenAd: AppOpenAd? = null
    private var loadTime: Long = 0L
    private val isLoading = AtomicBoolean(false)
    private var currentActivityRef: WeakReference<Activity>? = null

    init {
        app.registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun loadAd() {
        if (!consentManager.canRequestAds || appOpenAd != null || isLoading.get()) return
        isLoading.set(true)
        AppOpenAd.load(app, AdConstants.APP_OPEN_AD, AdRequest.Builder().build(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                isLoading.set(false)
                appOpenAd = ad
                loadTime = System.currentTimeMillis()
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                isLoading.set(false)
                appOpenAd = null
            }
        })
    }

    override fun onStart(owner: LifecycleOwner) {
        val activity = currentActivityRef?.get() ?: return
        val ad = appOpenAd ?: run { loadAd(); return }
        if (AdPolicy.canShowAppOpenAd(loadTime) && AdPolicy.isFullScreenAdShowing.compareAndSet(false, true)) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    AdPolicy.isFullScreenAdShowing.set(false)
                    AdPolicy.recordAppOpenAdShown()
                    appOpenAd = null
                    loadAd()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    AdPolicy.isFullScreenAdShowing.set(false)
                    appOpenAd = null
                    loadAd()
                }
            }
            ad.show(activity)
        }
    }

    override fun onActivityStarted(a: Activity) { currentActivityRef = WeakReference(a) }
    override fun onActivityResumed(a: Activity) { currentActivityRef = WeakReference(a) }
    override fun onActivityStopped(a: Activity) { if (currentActivityRef?.get() === a) currentActivityRef = null }
    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityPaused(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) { if (currentActivityRef?.get() === a) currentActivityRef = null }
}

// =========================================================================
// 5. UNIFIED AD MANAGER (SDK, Interstitials, Rewarded, Native)
// =========================================================================
class AdManager private constructor(private val appContext: Context) {
    private val consentManager = ConsentManager.getInstance(appContext)
    private val isSdkInitialized = AtomicBoolean(false)

    private val _isSdkInitializedState = MutableStateFlow(false)
    val isSdkInitializedState: StateFlow<Boolean> = _isSdkInitializedState.asStateFlow()

    private var interstitialAd: InterstitialAd? = null
    private var rewardedScheduleAd: RewardedAd? = null
    private var rewardedPdfAd: RewardedAd? = null

    fun initializeSdk(onInitialized: () -> Unit = {}) {
        if (!consentManager.canRequestAds) return

        if (isSdkInitialized.compareAndSet(false, true)) {
            MobileAds.setRequestConfiguration(
                RequestConfiguration.Builder().setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR)).build()
            )
            MobileAds.initialize(appContext) {
                _isSdkInitializedState.value = true
                preloadAds()
                onInitialized()
            }
        } else {
            _isSdkInitializedState.value = true
            onInitialized()
        }
    }

    fun preloadAds() {
        loadInterstitial()
        loadRewarded(AdConstants.REWARDED_SCHEDULE_UNLOCK) { rewardedScheduleAd = it }
        loadRewarded(AdConstants.REWARDED_PDF_EXPORT) { rewardedPdfAd = it }
    }

    // --- Interstitial Ad ---
    fun loadInterstitial() {
        if (!consentManager.canRequestAds || interstitialAd != null) return
        InterstitialAd.load(appContext, AdConstants.INTERSTITIAL_CALCULATION, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { interstitialAd = null }
        })
    }

    fun showInterstitialIfAllowed(activity: Activity, onAdClosed: () -> Unit) {
        val currentAd = interstitialAd
        if (currentAd != null && AdPolicy.canShowInterstitial() && AdPolicy.isFullScreenAdShowing.compareAndSet(false, true)) {
            currentAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    AdPolicy.isFullScreenAdShowing.set(false)
                    AdPolicy.recordInterstitialShown()
                    interstitialAd = null
                    loadInterstitial()
                    onAdClosed()
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    AdPolicy.isFullScreenAdShowing.set(false)
                    interstitialAd = null
                    loadInterstitial()
                    onAdClosed()
                }
            }
            currentAd.show(activity)
        } else {
            if (currentAd == null) loadInterstitial()
            onAdClosed()
        }
    }

    // --- Clean Reusable Rewarded Ad Handler ---
    private fun loadRewarded(adUnitId: String, onLoaded: (RewardedAd?) -> Unit) {
        if (!consentManager.canRequestAds) { onLoaded(null); return }
        RewardedAd.load(appContext, adUnitId, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { onLoaded(ad) }
            override fun onAdFailedToLoad(error: LoadAdError) { onLoaded(null) }
        })
    }

    fun showRewardedSchedule(activity: Activity, onRewardEarned: () -> Unit, onDismissed: (earned: Boolean) -> Unit) {
        showOrLoadRewarded(activity, rewardedScheduleAd, AdConstants.REWARDED_SCHEDULE_UNLOCK, onRewardEarned) { earned ->
            rewardedScheduleAd = null
            loadRewarded(AdConstants.REWARDED_SCHEDULE_UNLOCK) { rewardedScheduleAd = it }
            onDismissed(earned)
        }
    }

    fun showRewardedPdf(activity: Activity, onRewardEarned: () -> Unit, onDismissed: (earned: Boolean) -> Unit) {
        showOrLoadRewarded(activity, rewardedPdfAd, AdConstants.REWARDED_PDF_EXPORT, onRewardEarned) { earned ->
            rewardedPdfAd = null
            loadRewarded(AdConstants.REWARDED_PDF_EXPORT) { rewardedPdfAd = it }
            onDismissed(earned)
        }
    }

    private fun showOrLoadRewarded(activity: Activity, cachedAd: RewardedAd?, adUnitId: String, onRewardEarned: () -> Unit, onFinish: (earned: Boolean) -> Unit) {
        if (cachedAd != null && !AdPolicy.isFullScreenAdShowing.get()) {
            displayRewarded(activity, cachedAd, onRewardEarned, onFinish)
        } else {
            Toast.makeText(activity, "Loading video ad, please wait a moment...", Toast.LENGTH_SHORT).show()
            loadRewarded(adUnitId) { freshAd ->
                if (freshAd != null && !AdPolicy.isFullScreenAdShowing.get()) {
                    displayRewarded(activity, freshAd, onRewardEarned, onFinish)
                } else {
                    onFinish(false)
                }
            }
        }
    }

    private fun displayRewarded(activity: Activity, ad: RewardedAd, onRewardEarned: () -> Unit, onFinish: (earned: Boolean) -> Unit) {
        if (AdPolicy.isFullScreenAdShowing.compareAndSet(false, true)) {
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    AdPolicy.isFullScreenAdShowing.set(false)
                    onFinish(earned)
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    AdPolicy.isFullScreenAdShowing.set(false)
                    onFinish(false)
                }
            }
            ad.show(activity) { earned = true; onRewardEarned() }
        } else {
            onFinish(false)
        }
    }

    // --- Native Ads ---
    fun loadNativeAd(context: Context, adUnitId: String, onLoaded: (NativeAd) -> Unit, onFailed: (LoadAdError) -> Unit) {
        if (!consentManager.canRequestAds) {
            onFailed(LoadAdError(1, "Consent not granted", "App", null, null))
            return
        }
        AdLoader.Builder(context, adUnitId)
            .forNativeAd { onLoaded(it) }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) { onFailed(error) }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    companion object {
        @Volatile private var instance: AdManager? = null
        fun getInstance(context: Context): AdManager = instance ?: synchronized(this) {
            instance ?: AdManager(context.applicationContext).also { instance = it }
        }
    }
}
