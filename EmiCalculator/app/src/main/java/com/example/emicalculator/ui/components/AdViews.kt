package com.example.emicalculator.ui.components

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.emicalculator.R
import com.example.emicalculator.ads.AdConstants
import com.example.emicalculator.ads.AdManager
import com.example.emicalculator.ads.ConsentManager
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * 1. Composable wrapper for Google AdMob Banner Ads
 */
@Composable
fun BannerAdView(
    modifier: Modifier = Modifier,
    adUnitId: String = AdConstants.BANNER_HOME
) {
    val context = LocalContext.current
    val consentManager = remember { ConsentManager.getInstance(context) }

    if (!consentManager.canRequestAds) {
        return
    }

    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)
            this.adUnitId = adUnitId
            loadAd(AdRequest.Builder().build())
        }
    }

    DisposableEffect(adView) {
        onDispose {
            adView.destroy()
        }
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        factory = { adView }
    )
}

/**
 * 2. Composable wrapper for Google AdMob Native Unified Ads
 */
@Composable
fun NativeAdComposable(
    adUnitId: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val adManager = remember { AdManager.getInstance(context) }
    val isSdkInitialized by adManager.isSdkInitializedState.collectAsState()
    var loadedNativeAd by remember { mutableStateOf<NativeAd?>(null) }

    LaunchedEffect(adUnitId, isSdkInitialized) {
        if (isSdkInitialized) {
            adManager.loadNativeAd(
                context = context,
                adUnitId = adUnitId,
                onLoaded = { ad ->
                    loadedNativeAd = ad
                },
                onFailed = {
                    loadedNativeAd = null
                }
            )
        }
    }

    DisposableEffect(loadedNativeAd) {
        onDispose {
            loadedNativeAd?.destroy()
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        loadedNativeAd?.let { ad ->
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                factory = { ctx ->
                    val inflater = LayoutInflater.from(ctx)
                    val adView = inflater.inflate(R.layout.ad_unified_native, null, false) as NativeAdView

                    val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
                    val bodyView = adView.findViewById<TextView>(R.id.ad_body)
                    val ctaView = adView.findViewById<Button>(R.id.ad_call_to_action)
                    val iconView = adView.findViewById<ImageView>(R.id.ad_app_icon)
                    val advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser)

                    adView.headlineView = headlineView
                    adView.bodyView = bodyView
                    adView.callToActionView = ctaView
                    adView.iconView = iconView
                    adView.advertiserView = advertiserView

                    headlineView.text = ad.headline ?: ""
                    bodyView.text = ad.body ?: ""
                    ctaView.text = ad.callToAction ?: "Learn More"

                    if (ad.icon != null && ad.icon?.drawable != null) {
                        iconView.setImageDrawable(ad.icon?.drawable)
                        iconView.visibility = View.VISIBLE
                    } else {
                        iconView.visibility = View.GONE
                    }

                    if (ad.advertiser != null) {
                        advertiserView.text = ad.advertiser
                        advertiserView.visibility = View.VISIBLE
                    } else {
                        advertiserView.visibility = View.GONE
                    }

                    adView.setNativeAd(ad)
                    adView
                },
                update = { view ->
                    val headlineView = view.findViewById<TextView>(R.id.ad_headline)
                    val bodyView = view.findViewById<TextView>(R.id.ad_body)
                    val ctaView = view.findViewById<Button>(R.id.ad_call_to_action)

                    headlineView.text = ad.headline ?: ""
                    bodyView.text = ad.body ?: ""
                    ctaView.text = ad.callToAction ?: "Learn More"
                    view.setNativeAd(ad)
                }
            )
        }
    }
}
