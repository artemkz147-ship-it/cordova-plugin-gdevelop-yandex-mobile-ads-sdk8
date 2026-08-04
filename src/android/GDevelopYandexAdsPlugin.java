package io.gdevelop.yandexads;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.yandex.mobile.ads.appopenad.AppOpenAd;
import com.yandex.mobile.ads.appopenad.AppOpenAdEventListener;
import com.yandex.mobile.ads.appopenad.AppOpenAdLoadListener;
import com.yandex.mobile.ads.appopenad.AppOpenAdLoader;
import com.yandex.mobile.ads.banner.BannerAdEventListener;
import com.yandex.mobile.ads.banner.BannerAdSize;
import com.yandex.mobile.ads.banner.BannerAdView;
import com.yandex.mobile.ads.common.AdError;
import com.yandex.mobile.ads.common.AdRequest;
import com.yandex.mobile.ads.common.AdRequestError;
import com.yandex.mobile.ads.common.ImpressionData;
import com.yandex.mobile.ads.common.YandexAds;
import com.yandex.mobile.ads.interstitial.InterstitialAd;
import com.yandex.mobile.ads.interstitial.InterstitialAdEventListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoadListener;
import com.yandex.mobile.ads.interstitial.InterstitialAdLoader;
import com.yandex.mobile.ads.rewarded.Reward;
import com.yandex.mobile.ads.rewarded.RewardedAd;
import com.yandex.mobile.ads.rewarded.RewardedAdEventListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoadListener;
import com.yandex.mobile.ads.rewarded.RewardedAdLoader;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class GDevelopYandexAdsPlugin extends CordovaPlugin {
    private static final String TAG = "GDevelopYandexAds";
    private static final String EVENT_PREFIX = "gdevelop:yandex:";
    private static final String LEGACY_EVENT_PREFIX = "survival:yandex:";
    private static final String SDK_VERSION = "8.2.0";

    private Activity activity;
    private CordovaWebView appWebView;
    private boolean destroyed;

    private boolean sdkInitialized;
    private boolean sdkInitializing;
    private final List<CallbackContext> initCallbacks = new ArrayList<>();

    private String bannerId = "";
    private String appOpenId = "";
    private String interstitialId = "";
    private String rewardedId = "";
    private boolean bannerAtTop;
    private int bannerWidthDp;
    private int bannerHeightDp;
    private boolean bannerReserveSpace = true;
    private int bannerReservedHeightPx;

    private boolean webViewMarginsCaptured;
    private int originalWebViewTopMargin;
    private int originalWebViewBottomMargin;

    private FrameLayout bannerContainer;
    private BannerAdView bannerAdView;
    private boolean bannerLoading;
    private boolean bannerLoaded;
    private boolean bannerShowRequested;

    private AppOpenAdLoader appOpenLoader;
    private AppOpenAd appOpenAd;
    private boolean appOpenLoading;

    private InterstitialAdLoader interstitialLoader;
    private InterstitialAd interstitialAd;
    private boolean interstitialLoading;

    private RewardedAdLoader rewardedLoader;
    private RewardedAd rewardedAd;
    private boolean rewardedLoading;

    private boolean fullscreenShowing;
    private String fullscreenFormat = "";

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        activity = cordova.getActivity();
        appWebView = webView;
        destroyed = false;
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
        switch (action) {
            case "initialize":
                ui(() -> initializeSdk(args.optJSONObject(0), callbackContext));
                return true;
            case "setPrivacy":
                ui(() -> setPrivacy(args.optJSONObject(0), callbackContext));
                return true;
            case "loadBanner":
                ui(() -> loadBanner(args.optJSONObject(0), callbackContext));
                return true;
            case "showBanner":
                ui(() -> showBanner(callbackContext));
                return true;
            case "hideBanner":
                ui(() -> hideBanner(callbackContext));
                return true;
            case "destroyBanner":
                ui(() -> { destroyBannerInternal(); callbackContext.success(); });
                return true;
            case "loadAppOpen":
                ui(() -> loadAppOpen(callbackContext));
                return true;
            case "showAppOpen":
                ui(() -> showAppOpen(callbackContext));
                return true;
            case "loadInterstitial":
                ui(() -> loadInterstitial(callbackContext));
                return true;
            case "showInterstitial":
                ui(() -> showInterstitial(callbackContext));
                return true;
            case "loadRewarded":
                ui(() -> loadRewarded(callbackContext));
                return true;
            case "showRewarded":
                ui(() -> showRewarded(callbackContext));
                return true;
            case "getStatus":
                ui(() -> callbackContext.success(status()));
                return true;
            case "destroy":
                ui(() -> { releaseAll(); callbackContext.success(); });
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onReset() {
        ui(this::releaseAll);
        super.onReset();
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        ui(this::releaseAll);
        super.onDestroy();
    }

    private void initializeSdk(JSONObject cfg, CallbackContext callbackContext) {
        if (!usable()) {
            callbackContext.error("Android Activity is not available");
            return;
        }
        JSONObject config = cfg == null ? new JSONObject() : cfg;
        bannerId = clean(firstNonEmpty(config, "bannerAdUnitId", "bannerBlockId"));
        appOpenId = clean(firstNonEmpty(config, "appOpenAdUnitId", "openAppBlockId"));
        interstitialId = clean(firstNonEmpty(config, "interstitialAdUnitId", "interstitialBlockId"));
        rewardedId = clean(firstNonEmpty(config, "rewardedAdUnitId", "rewardedBlockId"));
        bannerAtTop = config.optBoolean("bannerAtTop", false);
        bannerWidthDp = Math.max(0, config.optInt("bannerWidthDp", config.optInt("bannerWidth", 0)));
        bannerHeightDp = Math.max(0, config.optInt("bannerHeightDp", config.optInt("bannerHeight", 0)));
        bannerReserveSpace = readBannerReserveSpace(config, true);

        applyPrivacy(config);
        initCallbacks.add(callbackContext);
        if (sdkInitialized) {
            finishInitSuccess();
            return;
        }
        if (sdkInitializing) return;
        sdkInitializing = true;
        try {
            YandexAds.initialize(activity.getApplicationContext(), () -> ui(this::finishInitSuccess));
        } catch (Throwable error) {
            finishInitError(error);
        }
    }

    private void setPrivacy(JSONObject cfg, CallbackContext callbackContext) {
        try {
            applyPrivacy(cfg == null ? new JSONObject() : cfg);
            callbackContext.success();
        } catch (Throwable error) {
            callbackContext.error(message(error, "Unable to apply privacy settings"));
        }
    }

    private void applyPrivacy(JSONObject config) {
        if (config.has("userConsent")) YandexAds.setUserConsent(config.optBoolean("userConsent"));
        if (config.has("ageRestricted")) YandexAds.setAgeRestricted(config.optBoolean("ageRestricted"));
        if (config.has("locationTracking")) YandexAds.setLocationTracking(config.optBoolean("locationTracking"));
        if (config.has("enableLogging")) YandexAds.enableLogging(config.optBoolean("enableLogging"));
        if (config.has("enableDebugErrorIndicator")) {
            YandexAds.enableDebugErrorIndicator(config.optBoolean("enableDebugErrorIndicator"));
        }
    }

    private void finishInitSuccess() {
        sdkInitialized = true;
        sdkInitializing = false;
        emit("initialized", put(new JSONObject(), "sdk", SDK_VERSION));
        List<CallbackContext> callbacks = new ArrayList<>(initCallbacks);
        initCallbacks.clear();
        for (CallbackContext callback : callbacks) callback.success(status());
    }

    private void finishInitError(Throwable error) {
        sdkInitialized = false;
        sdkInitializing = false;
        String errorMessage = message(error, "SDK initialization failed");
        emit("initializationFailed", put(new JSONObject(), "message", errorMessage));
        List<CallbackContext> callbacks = new ArrayList<>(initCallbacks);
        initCallbacks.clear();
        for (CallbackContext callback : callbacks) callback.error(errorMessage);
    }

    private void loadBanner(JSONObject options, CallbackContext callbackContext) {
        if (!requireSdk(callbackContext) || !requireId(bannerId, "Banner", callbackContext)) return;
        JSONObject config = options == null ? new JSONObject() : options;
        String position = clean(config.optString("position", bannerAtTop ? "top" : "bottom"));
        bannerAtTop = "top".equalsIgnoreCase(position) || config.optBoolean("bannerAtTop", bannerAtTop);
        int requestedWidth = config.optInt("widthDp", config.optInt("bannerWidth", bannerWidthDp));
        if (requestedWidth > 0) bannerWidthDp = requestedWidth;
        int requestedHeight = config.optInt("heightDp", config.optInt("bannerHeight", bannerHeightDp));
        if (requestedHeight >= 0) bannerHeightDp = requestedHeight;
        bannerReserveSpace = readBannerReserveSpace(config, bannerReserveSpace);
        if (config.has("autoShow")) bannerShowRequested = config.optBoolean("autoShow");

        if (bannerLoaded || bannerLoading) {
            callbackContext.success(status());
            return;
        }
        try {
            boolean showAfterLoad = bannerShowRequested;
            destroyBannerInternal();
            bannerShowRequested = showAfterLoad;
            bannerLoading = true;
            ensureBannerView();
            BannerAdSize bannerAdSize = BannerAdSize.sticky(activity, calculateBannerWidthDp());
            bannerAdView.setAdSize(bannerAdSize);
            bannerReservedHeightPx = calculateBannerReservedHeightPx(bannerAdSize);
            updateBannerContainerHeight(bannerReservedHeightPx);
            bannerAdView.setBannerAdEventListener(new BannerAdEventListener() {
                @Override
                public void onAdLoaded() {
                    ui(() -> {
                        if (destroyed || bannerAdView == null) return;
                        bannerLoading = false;
                        bannerLoaded = true;
                        JSONObject details = new JSONObject();
                        put(details, "widthDp", calculateBannerWidthDp());
                        put(details, "heightDp", pxToDp(bannerReservedHeightPx));
                        put(details, "heightPx", bannerReservedHeightPx);
                        put(details, "reserveSpace", bannerReserveSpace);
                        emit("bannerLoaded", details);
                        if (bannerShowRequested) setBannerVisibility(true, true);
                    });
                }

                @Override
                public void onAdFailedToLoad(AdRequestError error) {
                    ui(() -> {
                        bannerLoading = false;
                        bannerLoaded = false;
                        emit("bannerFailedToLoad", requestError(error));
                    });
                }

                @Override
                public void onAdClicked() {
                    emit("bannerClicked", new JSONObject());
                }

                @Override
                public void onImpression(ImpressionData data) {
                    emit("bannerImpression", impression(data));
                }
            });
            bannerAdView.loadAd(new AdRequest.Builder(bannerId).build());
            callbackContext.success();
        } catch (Throwable error) {
            bannerLoading = false;
            destroyBannerInternal();
            callbackContext.error(message(error, "Banner load failed"));
        }
    }

    private void ensureBannerView() {
        if (bannerContainer != null && bannerAdView != null) return;
        bannerContainer = new FrameLayout(activity);
        bannerContainer.setBackgroundColor(Color.TRANSPARENT);
        bannerContainer.setVisibility(View.GONE);
        bannerContainer.setClickable(false);

        bannerAdView = new BannerAdView(activity);
        FrameLayout.LayoutParams adParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        bannerContainer.addView(bannerAdView, adParams);

        FrameLayout.LayoutParams containerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                bannerAtTop ? Gravity.TOP : Gravity.BOTTOM
        );
        activity.addContentView(bannerContainer, containerParams);
    }

    private int calculateBannerWidthDp() {
        if (bannerWidthDp > 0) return bannerWidthDp;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return Math.max(1, Math.round(metrics.widthPixels / metrics.density));
    }

    private boolean readBannerReserveSpace(JSONObject config, boolean fallback) {
        if (config == null) return fallback;
        if (config.has("reserveSpace")) return config.optBoolean("reserveSpace", fallback);
        if (config.has("bannerReserveSpace")) return config.optBoolean("bannerReserveSpace", fallback);
        if (config.has("resizeGameForBanner")) return config.optBoolean("resizeGameForBanner", fallback);
        if (config.has("shrinkGameForBanner")) return config.optBoolean("shrinkGameForBanner", fallback);
        if (config.has("overlay")) return !config.optBoolean("overlay", !fallback);
        if (config.has("bannerOverlay")) return !config.optBoolean("bannerOverlay", !fallback);
        return fallback;
    }

    private int calculateBannerReservedHeightPx(BannerAdSize adSize) {
        int sdkHeightPx = 0;
        try {
            sdkHeightPx = adSize == null ? 0 : adSize.getHeightInPixels(activity);
        } catch (Throwable ignored) {}
        int requestedHeightPx = bannerHeightDp > 0 ? dpToPx(bannerHeightDp) : 0;
        return Math.max(1, Math.max(sdkHeightPx, requestedHeightPx));
    }

    private int dpToPx(int dp) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return Math.max(0, Math.round(dp * metrics.density));
    }

    private int pxToDp(int px) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return Math.max(0, Math.round(px / metrics.density));
    }

    private void updateBannerContainerHeight(int heightPx) {
        if (bannerContainer == null) return;
        ViewGroup.LayoutParams params = bannerContainer.getLayoutParams();
        if (params == null) {
            params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(1, heightPx),
                    bannerAtTop ? Gravity.TOP : Gravity.BOTTOM
            );
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = Math.max(1, heightPx);
            if (params instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) params).gravity = bannerAtTop ? Gravity.TOP : Gravity.BOTTOM;
            }
        }
        bannerContainer.setLayoutParams(params);
        bannerContainer.requestLayout();
    }

    private void applyWebViewReservedSpace(int heightPx) {
        if (!bannerReserveSpace || appWebView == null) {
            clearWebViewReservedSpace();
            return;
        }
        View webViewView = appWebView.getView();
        if (webViewView == null) return;
        ViewGroup.LayoutParams params = webViewView.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) {
            Log.w(TAG, "Unable to reserve banner space: Cordova WebView layout has no margins");
            return;
        }
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
        if (!webViewMarginsCaptured) {
            originalWebViewTopMargin = marginParams.topMargin;
            originalWebViewBottomMargin = marginParams.bottomMargin;
            webViewMarginsCaptured = true;
        }
        marginParams.topMargin = originalWebViewTopMargin + (bannerAtTop ? Math.max(0, heightPx) : 0);
        marginParams.bottomMargin = originalWebViewBottomMargin + (bannerAtTop ? 0 : Math.max(0, heightPx));
        webViewView.setLayoutParams(marginParams);
        webViewView.requestLayout();
        webViewView.post(() -> {
            try {
                if (appWebView != null) {
                    appWebView.getEngine().evaluateJavascript(
                            "window.dispatchEvent(new Event('resize'));", null);
                }
            } catch (Throwable ignored) {}
        });
    }

    private void clearWebViewReservedSpace() {
        if (!webViewMarginsCaptured || appWebView == null) return;
        View webViewView = appWebView.getView();
        if (webViewView == null) return;
        ViewGroup.LayoutParams params = webViewView.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) params;
        marginParams.topMargin = originalWebViewTopMargin;
        marginParams.bottomMargin = originalWebViewBottomMargin;
        webViewView.setLayoutParams(marginParams);
        webViewView.requestLayout();
        webViewView.post(() -> {
            try {
                if (appWebView != null) {
                    appWebView.getEngine().evaluateJavascript(
                            "window.dispatchEvent(new Event('resize'));", null);
                }
            } catch (Throwable ignored) {}
        });
    }

    private void showBanner(CallbackContext callbackContext) {
        if (!requireSdk(callbackContext) || !requireId(bannerId, "Banner", callbackContext)) return;
        bannerShowRequested = true;
        if (bannerLoaded && bannerContainer != null) setBannerVisibility(true, true);
        callbackContext.success();
    }

    private void hideBanner(CallbackContext callbackContext) {
        bannerShowRequested = false;
        setBannerVisibility(false, true);
        callbackContext.success();
    }

    private void setBannerVisibility(boolean visible, boolean notify) {
        if (bannerContainer == null) return;
        int desired = visible ? View.VISIBLE : View.GONE;
        boolean changed = bannerContainer.getVisibility() != desired;
        bannerContainer.setVisibility(desired);
        bannerContainer.setClickable(visible);
        if (visible && bannerReserveSpace) {
            applyWebViewReservedSpace(bannerReservedHeightPx);
        } else {
            clearWebViewReservedSpace();
        }
        if (notify && changed) emit(visible ? "bannerShown" : "bannerHidden", new JSONObject());
    }

    private void destroyBannerInternal() {
        clearWebViewReservedSpace();
        bannerReservedHeightPx = 0;
        bannerLoading = false;
        bannerLoaded = false;
        bannerShowRequested = false;
        if (bannerAdView != null) {
            try { bannerAdView.setBannerAdEventListener(null); } catch (Throwable ignored) {}
            try { bannerAdView.destroy(); } catch (Throwable ignored) {}
        }
        if (bannerContainer != null) {
            try {
                ViewGroup parent = (ViewGroup) bannerContainer.getParent();
                if (parent != null) parent.removeView(bannerContainer);
            } catch (Throwable ignored) {}
        }
        bannerAdView = null;
        bannerContainer = null;
    }

    private void loadAppOpen(CallbackContext callbackContext) {
        if (!requireSdk(callbackContext) || !requireId(appOpenId, "App Open", callbackContext)) return;
        if (appOpenAd != null || appOpenLoading) { callbackContext.success(); return; }
        try {
            if (appOpenLoader == null) appOpenLoader = new AppOpenAdLoader(activity.getApplicationContext());
            final AppOpenAdLoader loader = appOpenLoader;
            appOpenLoading = true;
            loader.loadAd(new AdRequest.Builder(appOpenId).build(), new AppOpenAdLoadListener() {
                @Override
                public void onAdLoaded(AppOpenAd ad) {
                    ui(() -> {
                        if (loader != appOpenLoader || destroyed) return;
                        clearAppOpen();
                        appOpenLoading = false;
                        appOpenAd = ad;
                        emit("appOpenLoaded", new JSONObject());
                    });
                }

                @Override
                public void onAdFailedToLoad(AdRequestError error) {
                    ui(() -> {
                        if (loader != appOpenLoader || destroyed) return;
                        appOpenLoading = false;
                        clearAppOpen();
                        emit("appOpenFailedToLoad", requestError(error));
                    });
                }
            });
            callbackContext.success();
        } catch (Throwable error) {
            appOpenLoading = false;
            callbackContext.error(message(error, "App Open load failed"));
        }
    }

    private void showAppOpen(CallbackContext callbackContext) {
        if (!requireSdk(callbackContext)) return;
        if (!beginFullscreen("appOpen", callbackContext)) return;
        AppOpenAd ad = appOpenAd;
        if (ad == null) { finishFullscreen("appOpen"); callbackContext.error("App Open ad is not loaded"); return; }
        ad.setAdEventListener(new AppOpenAdEventListener() {
            @Override public void onAdShown() { emit("appOpenShown", new JSONObject()); }
            @Override public void onAdFailedToShow(AdError error) { ui(() -> { finishFullscreen("appOpen"); clearAppOpen(); emit("appOpenFailedToShow", adError(error)); }); }
            @Override public void onAdDismissed() { ui(() -> { finishFullscreen("appOpen"); clearAppOpen(); emit("appOpenDismissed", new JSONObject()); }); }
            @Override public void onAdClicked() { emit("appOpenClicked", new JSONObject()); }
            @Override public void onAdImpression(ImpressionData data) { emit("appOpenImpression", impression(data)); }
        });
        try { ad.show(activity); callbackContext.success(); }
        catch (Throwable error) { finishFullscreen("appOpen"); clearAppOpen(); callbackContext.error(message(error, "App Open show failed")); }
    }

    private void loadInterstitial(CallbackContext callbackContext) {
        if (!requireSdk(callbackContext) || !requireId(interstitialId, "Interstitial", callbackContext)) return;
        if (interstitialAd != null || interstitialLoading) { callbackContext.success(); return; }
        try {
            if (interstitialLoader == null) interstitialLoader = new InterstitialAdLoader(activity.getApplicationContext());
            final InterstitialAdLoader loader = interstitialLoader;
            interstitialLoading = true;
            loader.loadAd(new AdRequest.Builder(interstitialId).build(), new InterstitialAdLoadListener() {
                @Override
                public void onAdLoaded(InterstitialAd ad) {
                    ui(() -> {
                        if (loader != interstitialLoader || destroyed) return;
                        clearInterstitial();
                        interstitialLoading = false;
                        interstitialAd = ad;
                        emit("interstitialLoaded", new JSONObject());
                    });
                }

                @Override
                public void onAdFailedToLoad(AdRequestError error) {
                    ui(() -> {
                        if (loader != interstitialLoader || destroyed) return;
                        interstitialLoading = false;
                        clearInterstitial();
                        emit("interstitialFailedToLoad", requestError(error));
                    });
                }
            });
            callbackContext.success();
        } catch (Throwable error) {
            interstitialLoading = false;
            callbackContext.error(message(error, "Interstitial load failed"));
        }
    }

    private void showInterstitial(CallbackContext callbackContext) {
        if (!requireSdk(callbackContext)) return;
        if (!beginFullscreen("interstitial", callbackContext)) return;
        InterstitialAd ad = interstitialAd;
        if (ad == null) { finishFullscreen("interstitial"); callbackContext.error("Interstitial ad is not loaded"); return; }
        ad.setAdEventListener(new InterstitialAdEventListener() {
            @Override public void onAdShown() { emit("interstitialShown", new JSONObject()); }
            @Override public void onAdFailedToShow(AdError error) { ui(() -> { finishFullscreen("interstitial"); clearInterstitial(); emit("interstitialFailedToShow", adError(error)); }); }
            @Override public void onAdDismissed() { ui(() -> { finishFullscreen("interstitial"); clearInterstitial(); emit("interstitialDismissed", new JSONObject()); }); }
            @Override public void onAdClicked() { emit("interstitialClicked", new JSONObject()); }
            @Override public void onAdImpression(ImpressionData data) { emit("interstitialImpression", impression(data)); }
        });
        try { ad.show(activity); callbackContext.success(); }
        catch (Throwable error) { finishFullscreen("interstitial"); clearInterstitial(); callbackContext.error(message(error, "Interstitial show failed")); }
    }

    private void loadRewarded(CallbackContext callbackContext) {
        if (!requireSdk(callbackContext) || !requireId(rewardedId, "Rewarded", callbackContext)) return;
        if (rewardedAd != null || rewardedLoading) { callbackContext.success(); return; }
        try {
            if (rewardedLoader == null) rewardedLoader = new RewardedAdLoader(activity.getApplicationContext());
            final RewardedAdLoader loader = rewardedLoader;
            rewardedLoading = true;
            loader.loadAd(new AdRequest.Builder(rewardedId).build(), new RewardedAdLoadListener() {
                @Override
                public void onAdLoaded(RewardedAd ad) {
                    ui(() -> {
                        if (loader != rewardedLoader || destroyed) return;
                        clearRewarded();
                        rewardedLoading = false;
                        rewardedAd = ad;
                        emit("rewardedLoaded", new JSONObject());
                    });
                }

                @Override
                public void onAdFailedToLoad(AdRequestError error) {
                    ui(() -> {
                        if (loader != rewardedLoader || destroyed) return;
                        rewardedLoading = false;
                        clearRewarded();
                        emit("rewardedFailedToLoad", requestError(error));
                    });
                }
            });
            callbackContext.success();
        } catch (Throwable error) {
            rewardedLoading = false;
            callbackContext.error(message(error, "Rewarded load failed"));
        }
    }

    private void showRewarded(CallbackContext callbackContext) {
        if (!requireSdk(callbackContext)) return;
        if (!beginFullscreen("rewarded", callbackContext)) return;
        RewardedAd ad = rewardedAd;
        if (ad == null) { finishFullscreen("rewarded"); callbackContext.error("Rewarded ad is not loaded"); return; }
        ad.setAdEventListener(new RewardedAdEventListener() {
            @Override public void onAdShown() { emit("rewardedShown", new JSONObject()); }
            @Override public void onAdFailedToShow(AdError error) { ui(() -> { finishFullscreen("rewarded"); clearRewarded(); emit("rewardedFailedToShow", adError(error)); }); }
            @Override public void onAdDismissed() { ui(() -> { finishFullscreen("rewarded"); clearRewarded(); emit("rewardedDismissed", new JSONObject()); }); }
            @Override public void onAdClicked() { emit("rewardedClicked", new JSONObject()); }
            @Override public void onAdImpression(ImpressionData data) { emit("rewardedImpression", impression(data)); }
            @Override public void onRewarded(Reward reward) {
                JSONObject details = new JSONObject();
                if (reward != null) put(details, "amount", reward.getAmount());
                emit("rewarded", details);
            }
        });
        try { ad.show(activity); callbackContext.success(); }
        catch (Throwable error) { finishFullscreen("rewarded"); clearRewarded(); callbackContext.error(message(error, "Rewarded show failed")); }
    }

    private boolean beginFullscreen(String format, CallbackContext callbackContext) {
        if (fullscreenShowing) {
            callbackContext.error("Another fullscreen ad is already showing: " + fullscreenFormat);
            return false;
        }
        fullscreenShowing = true;
        fullscreenFormat = format;
        return true;
    }

    private void finishFullscreen(String expected) {
        if (expected.equals(fullscreenFormat)) {
            fullscreenShowing = false;
            fullscreenFormat = "";
        }
    }

    private void releaseAll() {
        destroyBannerInternal();
        appOpenLoader = null;
        appOpenLoading = false;
        clearAppOpen();
        interstitialLoader = null;
        interstitialLoading = false;
        clearInterstitial();
        rewardedLoader = null;
        rewardedLoading = false;
        clearRewarded();
        fullscreenShowing = false;
        fullscreenFormat = "";
    }

    private void clearAppOpen() {
        if (appOpenAd != null) {
            try { appOpenAd.setAdEventListener(null); } catch (Throwable ignored) {}
        }
        appOpenAd = null;
    }

    private void clearInterstitial() {
        if (interstitialAd != null) {
            try { interstitialAd.setAdEventListener(null); } catch (Throwable ignored) {}
        }
        interstitialAd = null;
    }

    private void clearRewarded() {
        if (rewardedAd != null) {
            try { rewardedAd.setAdEventListener(null); } catch (Throwable ignored) {}
        }
        rewardedAd = null;
    }

    private boolean requireSdk(CallbackContext callbackContext) {
        if (destroyed) { callbackContext.error("Ads plugin is destroyed"); return false; }
        if (!sdkInitialized) { callbackContext.error("Yandex Mobile Ads SDK is not initialized"); return false; }
        if (!usable()) { callbackContext.error("Android Activity is not available"); return false; }
        return true;
    }

    private boolean requireId(String id, String format, CallbackContext callbackContext) {
        if (id != null && !id.isEmpty()) return true;
        callbackContext.error(format + " ad unit ID is empty");
        return false;
    }

    private boolean usable() {
        return activity != null && !activity.isFinishing() && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }

    private void ui(Runnable runnable) {
        if (activity != null) activity.runOnUiThread(runnable);
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonEmpty(JSONObject object, String first, String second) {
        String a = object.optString(first, "");
        return clean(a).isEmpty() ? object.optString(second, "") : a;
    }

    private String message(Throwable error, String fallback) {
        return error == null || error.getMessage() == null || error.getMessage().trim().isEmpty()
                ? fallback : error.getMessage();
    }

    private JSONObject status() {
        JSONObject result = new JSONObject();
        put(result, "sdkVersion", SDK_VERSION);
        put(result, "sdkInitialized", sdkInitialized);
        put(result, "bannerReady", bannerLoaded);
        put(result, "bannerVisible", bannerContainer != null && bannerContainer.getVisibility() == View.VISIBLE);
        put(result, "bannerReserveSpace", bannerReserveSpace);
        put(result, "bannerReservedHeightPx", bannerReservedHeightPx);
        put(result, "bannerWidthDp", bannerWidthDp);
        put(result, "bannerHeightDp", bannerHeightDp);
        put(result, "appOpenReady", appOpenAd != null);
        put(result, "interstitialReady", interstitialAd != null);
        put(result, "rewardedReady", rewardedAd != null);
        put(result, "fullscreenShowing", fullscreenShowing);
        put(result, "fullscreenFormat", fullscreenFormat);
        return result;
    }

    private JSONObject requestError(AdRequestError error) {
        JSONObject result = new JSONObject();
        put(result, "message", error == null ? "Ad request failed" : error.getDescription());
        return result;
    }

    private JSONObject adError(AdError error) {
        JSONObject result = new JSONObject();
        put(result, "message", error == null ? "Ad show failed" : error.getDescription());
        return result;
    }

    private JSONObject impression(ImpressionData data) {
        JSONObject result = new JSONObject();
        if (data != null) put(result, "rawData", data.getRawData());
        return result;
    }

    private JSONObject put(JSONObject object, String key, Object value) {
        try { object.put(key, value); } catch (Exception ignored) {}
        return object;
    }

    private void emit(String name, JSONObject details) {
        if (destroyed || appWebView == null) return;
        JSONObject safeDetails = details == null ? new JSONObject() : details;
        emitToPrefix(EVENT_PREFIX, name, safeDetails);
        emitToPrefix(LEGACY_EVENT_PREFIX, name, safeDetails);
    }

    private void emitToPrefix(String prefix, String name, JSONObject details) {
        String eventName = prefix + name;
        String script = "window.dispatchEvent(new CustomEvent(" + JSONObject.quote(eventName)
                + ",{detail:" + details.toString() + "}));";
        Log.i(TAG, eventName + " " + details);
        ui(() -> {
            if (!destroyed && appWebView != null) {
                appWebView.getEngine().evaluateJavascript(script, null);
            }
        });
    }
}
