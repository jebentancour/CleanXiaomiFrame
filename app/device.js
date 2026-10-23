/* Public Tizen application APIs, or window.CleanFrameHost in the Android build. No Mac server, network address or credentials. */
(function () {
  'use strict';
  var IDS = {
    stremio: 'IkWsFHtOY9.Stremio', netflix: 'org.tizen.netflix-app',
    disneyplus: 'MCmYXNxgcu.DisneyPlus', youtube: '9Ur5IzDKqV.TizenYouTube',
    appletv: 'com.samsung.tv.aria-video', primevideo: 'org.tizen.primevideo',
    abciview: 'mWK87Xghap.ABCiview', sbs: 'OyE7OuxlUi.SBSOnDemand',
    applemusic: 'org.tizen.apple.apple-music'
  };
  var STREAMING = ['as6cYRgkPy.Binge', '73iTZRTc34.Stan', '5b8c3eb16b.BeamCTVDev',
    'KaLitts8bq.tenplaySamsungTizen', 'eYuOiOLYQX.KayoSports', 'Ne2ZsPWdKl.9NOW',
    '3KA0pm7a7V.TubiTV', 'rJeHak5zRg.Spotify', 'icp4AvUPiA.7plus'];
  var CONSUMER_SYSTEM = ['org.tizen.browser', 'com.microsoft.copilot'];
  var EXTRA_BRANDS = {'rJeHak5zRg.Spotify': 'spotify', '5b8c3eb16b.BeamCTVDev': 'hbomax', '3KA0pm7a7V.TubiTV': 'tubi'};
  var INTERNAL = ['adplatform.ADPlayer', 'WykOS4t4F0.cmsWebApp', '25emanual3.emanual',
    'ZODRT12q2l.WebAccountExtension', 'ByJlgt0XeD.CastSettingsApp', 'tv6pbVMQFF.PrivacyChoices'];
  var SELF = 'nClrkHome1.FrameHome';
  /* Android packages that carry a bundled mark. */
  var ANDROID_BRANDS = { 'com.netflix.ninja': 'netflix', 'com.google.android.youtube.tv': 'youtube', 'com.amazon.amazonvideo.livingroom': 'primevideo',
    'com.disney.disneyplus': 'disneyplus', 'com.stremio.one': 'stremio', 'com.apple.atve.androidtv.appletv': 'appletv',
    'com.spotify.tv.android': 'spotify', 'com.wbd.stream': 'hbomax', 'com.tubitv': 'tubi', 'org.jellyfin.androidtv': 'jellyfin', 'com.tiktok.tv': 'tiktok' };
  function api() { return window.tizen && window.tizen.application; }
  function host() { return window.CleanFrameHost; }
  function hostCall(ok, done) { if (ok) done(null); else done(failure()); }
  /* Bundled marks for devices, by the name they announce over HDMI-CEC. */
  function deviceBrand(name) { return /playstation|xbox|nintendo/i.test(name) ? 'gamepad' : /apple ?tv/i.test(name) ? 'appletv' : ''; }
  /* HDMI-CEC devices as [{appId, name, brand}]. The browser preview shows the reference TV's two devices. */
  function devices() {
    var list = [];
    if (host()) { try { list = JSON.parse(host().devices()); } catch (err) { list = []; } }
    else if (!api()) list = [{ appId: 'hdmi1.playstation-5', name: 'PlayStation 5' }, { appId: 'hdmi3.apple-tv', name: 'Apple TV' }];
    return list.map(function (d) { return { appId: d.appId, name: d.name, brand: deviceBrand(d.name), icon: '' }; });
  }
  function isDevice(id) { return /^hdmi[0-9]+\./.test(id); }
  /* Package names of apps used recently, newest first (Android only; empty elsewhere). */
  function recent() {
    if (!host()) return [];
    try { return JSON.parse(host().recent()); } catch (err) { return []; }
  }
  /* Navigation sound: 'left', 'right', 'up', 'down' or 'select' (Android only; silent elsewhere). */
  function sound(kind) { if (host() && host().sound) host().sound(kind); }
  function close(id, done) { if (host()) hostCall(host().close(id), done); else unavailable(done); }
  function appInfo(id, done) { if (host()) hostCall(host().appInfo(id), done); else unavailable(done); }
  function unavailable(done) {
    done({ message: 'Abre CleanFrame en la TV para usar este control.', detail: 'La vista previa web no puede controlar la TV.' });
  }
  function failure(err) {
    return { message: 'La TV no pudo abrir esta pantalla.', detail: err && (err.message || err.name) || 'Vuelve a intentarlo, o presiona Inicio en el control para volver a CleanFrame.' };
  }
  function bounded(done) {
    var finished = false, timer = setTimeout(function () { finish({ message: 'La TV no respondió. Vuelve a intentarlo.' }); }, 8000);
    function finish(err, value) { if (finished) return; finished = true; clearTimeout(timer); done(err, value); }
    return finish;
  }
  function launchApp(id, done) {
    if (host()) { hostCall(isDevice(id) ? host().openDevice(id) : host().launch(id), done); return; }
    if (!api()) { unavailable(done); return; }
    var finish = bounded(done);
    try { api().launch(id, function () { finish(null); }, function (err) { finish(failure(err)); }); }
    catch (err) { finish(failure(err)); }
  }
  function launch(brand, done) {
    if (!IDS[brand]) { done({message: 'Esta app no tiene destino para abrirse.'}); return; }
    launchApp(IDS[brand], done);
  }
  function art(done) {
    if (host() || !api()) { unavailable(done); return; }
    var finish = bounded(done);
    try {
      var data = [new tizen.ApplicationControlData('mode', ['fullscreen']), new tizen.ApplicationControlData('requester', ['mobile'])];
      var control = new tizen.ApplicationControl('http://tizen.org/appcontrol/operation/default', null, null, null, data);
      api().launchAppControl(control, 'org.tizen.art-app', function () { finish(null); }, function (err) { finish(failure(err)); });
    } catch (err) { finish(failure(err)); }
  }
  function home(done) { if (host()) done(null); else launchApp('com.samsung.tv.csfs', done); }
  function settings(done) { if (host()) hostCall(host().tvSettings(), done); else launchApp('com.samsung.tv.coba.setting', done); }
  function androidApps(done) {
    var result;
    try { result = JSON.parse(host().apps()); } catch (err) { done(failure(err)); return; }
    result.forEach(function (a) { a.brand = ANDROID_BRANDS[a.appId] || ''; });
    result = result.concat(devices());
    result.sort(function (a, b) { return a.name.toLowerCase().localeCompare(b.name.toLowerCase()); });
    done(null, result);
  }
  function apps(done) {
    if (host()) { androidApps(done); return; }
    if (!api()) { unavailable(done); return; }
    var finish = bounded(done);
    try {
      api().getAppsInfo(function (list) {
        var known = {}, seen = {}, result = [];
        Object.keys(IDS).forEach(function (brand) { known[IDS[brand]] = brand; });
        list.forEach(function (a) {
          var primary = !!known[a.id], stream = STREAMING.indexOf(a.id) >= 0;
          var consumer = /^[A-Za-z0-9]{10}\.[A-Za-z0-9_-]+$/.test(a.id) || CONSUMER_SYSTEM.indexOf(a.id) >= 0;
          if (a.id === SELF || INTERNAL.indexOf(a.id) >= 0 || seen[a.id] || !a.name || (!primary && !stream && (!a.show || !consumer))) return;
          if (!primary && /(?:service|worker|preview|promotion|plugin|launcher|test|sdk|^ep[ -]|factory|daemon)/i.test(a.name + ' ' + a.id)) return;
          if (/-dummy$/.test(a.id) || a.id === 'com.samsung.tv.cobalt-yt') return;
          seen[a.id] = true;
          result.push({ appId: a.id, name: a.name, brand: known[a.id] || EXTRA_BRANDS[a.id] || '', icon: a.iconPath || '', rank: primary ? Object.keys(IDS).indexOf(known[a.id]) : (stream ? 20 : 30) });
        });
        result.sort(function (a, b) { return a.rank - b.rank || a.name.toLowerCase().localeCompare(b.name.toLowerCase()); });
        finish(null, result);
      }, function (err) { finish(failure(err)); });
    } catch (err) { finish(failure(err)); }
  }
  window.FrameDevice = { launch: launch, launchApp: launchApp, devices: devices, recent: recent, sound: sound, close: close, appInfo: appInfo, art: art, home: home, settings: settings, apps: apps };
}());
