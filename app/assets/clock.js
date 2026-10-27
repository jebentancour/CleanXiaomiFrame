/* The Home clock's text and typefaces, shared by Home, the Android quick menu and the screensaver. */
(function () {
  'use strict';
  var DAYS = ['Domingo', 'Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
  var MONTHS = ['Ene', 'Feb', 'Mar', 'Abr', 'May', 'Jun', 'Jul', 'Ago', 'Sep', 'Oct', 'Nov', 'Dic'];
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  window.CleanFrameClock = {
    /* 14:25 */
    time: function (d) { d = d || new Date(); return pad(d.getHours()) + ':' + pad(d.getMinutes()); },
    /* Lunes 26 de Oct */
    date: function (d) { d = d || new Date(); return DAYS[d.getDay()] + ' ' + d.getDate() + ' de ' + MONTHS[d.getMonth()]; },
    fonts: {
      manrope: { name: 'Manrope', family: 'Manrope', display: 600, ui: 500, clock: 500 },
      montreal: { name: 'Neue Montreal', family: 'Study Montreal', display: 300, ui: 400, clock: 300 },
      mori: { name: 'Mori', family: 'Study Mori', display: 200, ui: 400, clock: 200 },
      pangram: { name: 'Pangram Sans', family: 'Study Pangram', display: 300, ui: 400, clock: 300 },
      geist: { name: 'Geist', family: 'Study Geist', display: 300, ui: 400, clock: 300 },
      newsreader: { name: 'Newsreader', family: 'Study Newsreader', display: 400, ui: 400, clock: 400 }
    },
    /* The Home font the launcher saved, for pages that share its storage. */
    savedFont: function () {
      try { var s = JSON.parse(localStorage.getItem('cleanframe.home.v1')) || {}, id = (s.look && s.look.font) || s.font; return this.fonts[id] ? id : 'manrope'; }
      catch (e) { return 'manrope'; }
    }
  };
}());
