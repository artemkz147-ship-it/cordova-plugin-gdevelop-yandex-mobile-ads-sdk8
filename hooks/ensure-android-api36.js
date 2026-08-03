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
  } catch (error) {
    // Do not hide the real Cordova error if a future Cordova layout changes.
    console.warn('[GDevelopYandexAds/API36] Compatibility hook warning:', error && error.stack ? error.stack : error);
  }
};
