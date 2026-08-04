/* global cordova */
'use strict';

const exec = require('cordova/exec');
const SERVICE = 'GDevelopYandexAds';
const call = (action, args = []) => new Promise((resolve, reject) => {
  exec(resolve, reject, SERVICE, action, args);
});

const api = {
  initialize: (config) => call('initialize', [config || {}]),
  setPrivacy: (config) => call('setPrivacy', [config || {}]),

  loadBanner: (options) => call('loadBanner', [options || {}]),
  showBanner: () => call('showBanner'),
  hideBanner: () => call('hideBanner'),
  destroyBanner: () => call('destroyBanner'),

  loadAppOpen: () => call('loadAppOpen'),
  showAppOpen: () => call('showAppOpen'),
  loadInterstitial: () => call('loadInterstitial'),
  showInterstitial: () => call('showInterstitial'),
  loadRewarded: () => call('loadRewarded'),
  showRewarded: () => call('showRewarded'),

  getStatus: () => call('getStatus'),
  destroy: () => call('destroy')
};

module.exports = Object.freeze(api);
