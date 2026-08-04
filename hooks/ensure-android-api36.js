#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');

function log(message) {
  console.log('[GDevelopYandexAds/API36] ' + message);
}

function writeIfChanged(file, content) {
  const previous = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
  if (previous !== content) {
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.writeFileSync(file, content, 'utf8');
  }
}

function upsertProperty(text, key, value) {
  const line = `${key}=${value}`;
  const pattern = new RegExp(`^\\s*${key.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\$&')}\\s*=.*$`, 'm');
  if (pattern.test(text)) return text.replace(pattern, line);
  return text.replace(/\s*$/, '') + '\n' + line + '\n';
}

function replaceRequired(text, oldValue, newValue, label) {
  if (text.includes(newValue)) return text;
  if (!text.includes(oldValue)) {
    throw new Error(`Unable to patch ${label}: expected source fragment was not found.`);
  }
  return text.replace(oldValue, newValue);
}

function patchGradleConfig(androidRoot) {
  const file = path.join(androidRoot, 'cdv-gradle-config.json');
  if (!fs.existsSync(file)) return;
  const cfg = JSON.parse(fs.readFileSync(file, 'utf8'));
  cfg.MIN_SDK_VERSION = Math.max(Number(cfg.MIN_SDK_VERSION || 0), 24);
  cfg.SDK_VERSION = 36;
  cfg.COMPILE_SDK_VERSION = 36;
  cfg.MIN_BUILD_TOOLS_VERSION = '36.0.0';
  if (Object.prototype.hasOwnProperty.call(cfg, 'BUILD_TOOLS_VERSION')) cfg.BUILD_TOOLS_VERSION = '36.0.0';
  writeIfChanged(file, JSON.stringify(cfg, null, 2) + '\n');
  log('cdv-gradle-config.json forced to compile/target API 36.');
}

function patchGradleProperties(androidRoot) {
  const file = path.join(androidRoot, 'gradle.properties');
  let text = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : '';
  text = upsertProperty(text, 'cdvMinSdkVersion', '24');
  text = upsertProperty(text, 'cdvSdkVersion', '36');
  text = upsertProperty(text, 'cdvCompileSdkVersion', '36');
  text = upsertProperty(text, 'cdvBuildToolsVersion', '36.0.0');
  writeIfChanged(file, text);
}

function patchGeneratedConfigXml(androidRoot) {
  const candidates = [
    path.join(androidRoot, 'app', 'src', 'main', 'res', 'xml', 'config.xml'),
    path.join(androidRoot, 'res', 'xml', 'config.xml')
  ];
  const prefs = [
    ['android-minSdkVersion', '24'],
    ['android-targetSdkVersion', '36'],
    ['android-compileSdkVersion', '36'],
    ['android-buildToolsVersion', '36.0.0']
  ];
  for (const file of candidates) {
    if (!fs.existsSync(file)) continue;
    let xml = fs.readFileSync(file, 'utf8');
    for (const [name, value] of prefs) {
      const pattern = new RegExp(`<preference\\s+name=["']${name}["']\\s+value=["'][^"']*["']\\s*/>`, 'i');
      const node = `<preference name="${name}" value="${value}" />`;
      if (pattern.test(xml)) xml = xml.replace(pattern, node);
      else xml = xml.replace(/<\/widget>\s*$/i, `  ${node}\n</widget>`);
    }
    writeIfChanged(file, xml);
  }
}

function patchCordovaBaklavaFallback(androidRoot) {
  const candidates = [
    path.join(androidRoot, 'CordovaLib', 'src', 'org', 'apache', 'cordova', 'CoreAndroid.java'),
    path.join(androidRoot, 'framework', 'src', 'org', 'apache', 'cordova', 'CoreAndroid.java')
  ];
  for (const file of candidates) {
    if (!fs.existsSync(file)) continue;
    let text = fs.readFileSync(file, 'utf8');
    if (text.includes('Build.VERSION_CODES.BAKLAVA')) {
      text = text.replace(/(?:android\.os\.)?Build\.VERSION_CODES\.BAKLAVA/g, '36 /* Android 16 / Baklava */');
      writeIfChanged(file, text);
      log('Applied safe literal API 36 fallback in CoreAndroid.java.');
    }
  }
}

function patchBannerLayout(androidRoot) {
  const candidates = [
    path.join(androidRoot, 'app', 'src', 'main', 'java', 'io', 'gdevelop', 'yandexads', 'GDevelopYandexAdsPlugin.java'),
    path.join(androidRoot, 'src', 'io', 'gdevelop', 'yandexads', 'GDevelopYandexAdsPlugin.java')
  ];

  for (const file of candidates) {
    if (!fs.existsSync(file)) continue;
    let text = fs.readFileSync(file, 'utf8');
    if (text.includes('private boolean bannerReserveSpace = true;')) {
      log('Banner non-overlap patch is already present.');
      return;
    }

    text = replaceRequired(text,
`    private boolean bannerAtTop;
    private int bannerWidthDp;

    private FrameLayout bannerContainer;`,
`    private boolean bannerAtTop;
    private int bannerWidthDp;
    private int bannerHeightDp;
    private boolean bannerReserveSpace = true;
    private int bannerReservedHeightPx;

    private boolean webViewMarginsCaptured;
    private int originalWebViewTopMargin;
    private int originalWebViewBottomMargin;

    private FrameLayout bannerContainer;`, 'banner fields');

    text = replaceRequired(text,
`        bannerAtTop = config.optBoolean("bannerAtTop", false);
        bannerWidthDp = Math.max(0, config.optInt("bannerWidthDp", config.optInt("bannerWidth", 0)));

        applyPrivacy(config);`,
`        bannerAtTop = config.optBoolean("bannerAtTop", false);
        bannerWidthDp = Math.max(0, config.optInt("bannerWidthDp", config.optInt("bannerWidth", 0)));
        bannerHeightDp = Math.max(0, config.optInt("bannerHeightDp", config.optInt("bannerHeight", 0)));
        bannerReserveSpace = readBannerReserveSpace(config, true);

        applyPrivacy(config);`, 'initial banner configuration');

    text = replaceRequired(text,
`        int requestedWidth = config.optInt("widthDp", config.optInt("bannerWidth", bannerWidthDp));
        if (requestedWidth > 0) bannerWidthDp = requestedWidth;
        if (config.has("autoShow")) bannerShowRequested = config.optBoolean("autoShow");`,
`        int requestedWidth = config.optInt("widthDp", config.optInt("bannerWidth", bannerWidthDp));
        if (requestedWidth > 0) bannerWidthDp = requestedWidth;
        int requestedHeight = config.optInt("heightDp", config.optInt("bannerHeight", bannerHeightDp));
        if (requestedHeight >= 0) bannerHeightDp = requestedHeight;
        bannerReserveSpace = readBannerReserveSpace(config, bannerReserveSpace);
        if (config.has("autoShow")) bannerShowRequested = config.optBoolean("autoShow");`, 'banner load options');

    text = replaceRequired(text,
`        try {
            destroyBannerInternal();
            bannerLoading = true;`,
`        try {
            boolean showAfterLoad = bannerShowRequested;
            destroyBannerInternal();
            bannerShowRequested = showAfterLoad;
            bannerLoading = true;`, 'banner show request preservation');

    text = replaceRequired(text,
`            ensureBannerView();
            bannerAdView.setAdSize(BannerAdSize.sticky(activity, calculateBannerWidthDp()));
            bannerAdView.setBannerAdEventListener(new BannerAdEventListener() {`,
`            ensureBannerView();
            BannerAdSize bannerAdSize = BannerAdSize.sticky(activity, calculateBannerWidthDp());
            bannerAdView.setAdSize(bannerAdSize);
            bannerReservedHeightPx = calculateBannerReservedHeightPx(bannerAdSize);
            updateBannerContainerHeight(bannerReservedHeightPx);
            bannerAdView.setBannerAdEventListener(new BannerAdEventListener() {`, 'banner size calculation');

    text = replaceRequired(text,
`                        bannerLoading = false;
                        bannerLoaded = true;
                        emit("bannerLoaded", new JSONObject());
                        if (bannerShowRequested) setBannerVisibility(true, true);`,
`                        bannerLoading = false;
                        bannerLoaded = true;
                        JSONObject details = new JSONObject();
                        put(details, "widthDp", calculateBannerWidthDp());
                        put(details, "heightDp", pxToDp(bannerReservedHeightPx));
                        put(details, "heightPx", bannerReservedHeightPx);
                        put(details, "reserveSpace", bannerReserveSpace);
                        emit("bannerLoaded", details);
                        if (bannerShowRequested) setBannerVisibility(true, true);`, 'banner loaded event');

    text = replaceRequired(text,
`    private int calculateBannerWidthDp() {
        if (bannerWidthDp > 0) return bannerWidthDp;
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return Math.max(1, Math.round(metrics.widthPixels / metrics.density));
    }

    private void showBanner(CallbackContext callbackContext) {`,
`    private int calculateBannerWidthDp() {
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

    private void showBanner(CallbackContext callbackContext) {`, 'WebView reserved-space helpers');

    text = replaceRequired(text,
`    private void setBannerVisibility(boolean visible, boolean notify) {
        if (bannerContainer == null) return;
        int desired = visible ? View.VISIBLE : View.GONE;
        if (bannerContainer.getVisibility() == desired) return;
        bannerContainer.setVisibility(desired);
        bannerContainer.setClickable(visible);
        if (notify) emit(visible ? "bannerShown" : "bannerHidden", new JSONObject());
    }`,
`    private void setBannerVisibility(boolean visible, boolean notify) {
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
    }`, 'banner visibility');

    text = replaceRequired(text,
`    private void destroyBannerInternal() {
        bannerLoading = false;`,
`    private void destroyBannerInternal() {
        clearWebViewReservedSpace();
        bannerReservedHeightPx = 0;
        bannerLoading = false;`, 'banner cleanup');

    text = replaceRequired(text,
`        put(result, "bannerReady", bannerLoaded);
        put(result, "bannerVisible", bannerContainer != null && bannerContainer.getVisibility() == View.VISIBLE);`,
`        put(result, "bannerReady", bannerLoaded);
        put(result, "bannerVisible", bannerContainer != null && bannerContainer.getVisibility() == View.VISIBLE);
        put(result, "bannerReserveSpace", bannerReserveSpace);
        put(result, "bannerReservedHeightPx", bannerReservedHeightPx);
        put(result, "bannerWidthDp", bannerWidthDp);
        put(result, "bannerHeightDp", bannerHeightDp);`, 'banner status');

    writeIfChanged(file, text);
    log('Patched banner layout: Cordova WebView now reserves the banner height.');
    return;
  }

  log('Banner plugin Java source was not found yet; it will be retried before compile.');
}

module.exports = function (context) {
  try {
    const projectRoot = context && context.opts && context.opts.projectRoot
      ? context.opts.projectRoot
      : process.cwd();
    const androidRoot = path.join(projectRoot, 'platforms', 'android');
    if (!fs.existsSync(androidRoot)) {
      log('Android platform directory is not prepared yet; nothing to patch.');
      return;
    }
    patchGradleConfig(androidRoot);
    patchGradleProperties(androidRoot);
    patchGeneratedConfigXml(androidRoot);
    patchCordovaBaklavaFallback(androidRoot);
    patchBannerLayout(androidRoot);
  } catch (error) {
    // Do not hide the real Cordova error if a future Cordova layout changes.
    console.warn('[GDevelopYandexAds/API36] Compatibility hook warning:', error && error.stack ? error.stack : error);
  }
};
