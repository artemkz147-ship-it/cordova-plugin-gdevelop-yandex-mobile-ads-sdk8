# GDevelop Yandex Mobile Ads SDK 8 — Cordova plugin 4.0.0

Поддерживается Android-сборка GDevelop/Cordova:

- Yandex Mobile Ads SDK 8.2.0;
- адаптивный sticky-баннер;
- App Open;
- interstitial;
- rewarded;
- Cordova Android 15.x, Android API 36, Build Tools 36.0.0;
- обратная совместимость с `window.SurvivalYandexAds` и событиями `survival:yandex:*` из приложенного проекта.

Основной JS-объект: `window.GDevelopYandexAds`.

События: `gdevelop:yandex:initialized`, `bannerLoaded`, `bannerFailedToLoad`, `bannerShown`, `bannerHidden`, `appOpenLoaded`, `appOpenShown`, `appOpenDismissed`, `interstitialLoaded`, `interstitialShown`, `interstitialDismissed`, `rewardedLoaded`, `rewardedShown`, `rewarded`, `rewardedDismissed` и соответствующие события ошибок/кликов/показов.

Плагин содержит hook совместимости, который перед компиляцией выставляет compile/target API 36. Дополнительно он заменяет ссылку `Build.VERSION_CODES.BAKLAVA` в сгенерированном CordovaLib на безопасный литерал API 36, если внешний сборщик GDevelop ошибочно оставил compileSdk ниже 36.

## Подключение к GDevelop

В однофайловом расширении GDevelop зависимость указывается через публичный GitHub tag:

```json
{
  "exportName": "cordova-plugin-gdevelop-yandex-mobile-ads-sdk8",
  "name": "GDevelop Yandex Mobile Ads SDK 8.2",
  "type": "cordova",
  "version": "https://github.com/artemkz147-ship-it/cordova-plugin-gdevelop-yandex-mobile-ads-sdk8.git#v4.0.0"
}
```

После импорта одного JSON-файла облачный Android-экспорт GDevelop должен сам скачать нативный Cordova-плагин из этого репозитория.
