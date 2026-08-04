# GDevelop Yandex Mobile Ads SDK 8 — Cordova plugin 4.1.0

Поддерживается Android-сборка GDevelop/Cordova:

- Yandex Mobile Ads SDK 8.2.0;
- адаптивный sticky-баннер, который по умолчанию не перекрывает игру;
- App Open;
- interstitial;
- rewarded;
- Cordova Android 15.x, Android API 36, Build Tools 36.0.0;
- обратная совместимость с `window.SurvivalYandexAds` и событиями `survival:yandex:*`.

## Баннер без перекрытия интерфейса

Начиная с версии 4.1.0 баннер по умолчанию резервирует место сверху или снизу и уменьшает область Cordova WebView/GDevelop. Игровой интерфейс больше не остаётся под рекламой.

- `bannerWidthDp: 0` — адаптивная ширина по экрану;
- `bannerHeightDp: 0` — высота берётся из фактического размера, рассчитанного Yandex SDK;
- `bannerHeightDp: 100` — зарезервировать не менее 100 dp;
- `bannerReserveSpace: true` — уменьшать игровую область;
- `bannerReserveSpace: false` или `bannerOverlay: true` — показывать баннер поверх игры.

При скрытии или удалении баннера исходный размер игровой области автоматически восстанавливается.

Основной JS-объект: `window.GDevelopYandexAds`.

События: `gdevelop:yandex:initialized`, `bannerLoaded`, `bannerFailedToLoad`, `bannerShown`, `bannerHidden`, `appOpenLoaded`, `appOpenShown`, `appOpenDismissed`, `interstitialLoaded`, `interstitialShown`, `interstitialDismissed`, `rewardedLoaded`, `rewardedShown`, `rewarded`, `rewardedDismissed` и соответствующие события ошибок/кликов/показов.

Плагин содержит hook совместимости, который перед компиляцией выставляет compile/target API 36 и дополнительно проверяет код баннера.
