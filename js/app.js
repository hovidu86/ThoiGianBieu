/* ===================================================================
   app.js - Giao diện, lưu trữ và điều phối đồng bộ.
   =================================================================== */
(function () {
  'use strict';

  var R = window.Rules;
  var KEY_SETTINGS = 'sleep.settings';
  var KEY_LOGS = 'sleep.logs';
  var KEY_TONIGHT = 'sleep.tonight';
  var KEY_NOTIFY = 'sleep.notified';

  var state = {
    settings: null,
    logs: [],
    tonight: { date: '', checked: [] },
    best: 0,
    streak: 0
  };

  var $ = function (id) { return document.getElementById(id); };

  /* ================= Lưu trữ ================= */

  function loadState() {
    var s = {};
    try { s = JSON.parse(localStorage.getItem(KEY_SETTINGS) || '{}'); } catch (e) { s = {}; }
    state.settings = Object.assign({}, R.DEFAULT_SETTINGS, s);
    /* mảng lồng nhau không merge được bằng Object.assign */
    ['tiers', 'milestones', 'routine'].forEach(function (k) {
      if (!Array.isArray(state.settings[k]) || !state.settings[k].length) {
        state.settings[k] = R.DEFAULT_SETTINGS[k].map(function (o) { return Object.assign({}, o); });
      }
    });

    try { state.logs = JSON.parse(localStorage.getItem(KEY_LOGS) || '[]'); } catch (e) { state.logs = []; }

    try { state.tonight = JSON.parse(localStorage.getItem(KEY_TONIGHT) || 'null') || { date: '', checked: [] }; }
    catch (e) { state.tonight = { date: '', checked: [] }; }

    var night = R.currentNightDate();
    if (state.tonight.date !== night) state.tonight = { date: night, checked: [] };

    recompute();
  }

  function saveSettings() { localStorage.setItem(KEY_SETTINGS, JSON.stringify(state.settings)); }
  function saveLogs() { localStorage.setItem(KEY_LOGS, JSON.stringify(state.logs)); }
  function saveTonight() { localStorage.setItem(KEY_TONIGHT, JSON.stringify(state.tonight)); }

  function sigOf(l) {
    return [l.date, l.time, l.wake || '', l.totalAmount, l.resultingStreak].join('|');
  }

  /** Tính lại toàn bộ; đêm nào đổi kết quả thì đánh dấu cần đồng bộ lại. */
  function recompute() {
    var before = {};
    state.logs.forEach(function (l) { before[l.date] = sigOf(l); });

    var out = R.recomputeAll(state.logs, state.settings);
    out.forEach(function (l) {
      if (before[l.date] && before[l.date] !== sigOf(l)) l.synced = false;
    });

    state.logs = out.slice();
    state.best = out.bestStreak || 0;
    state.streak = out.currentStreak || 0;
  }

  function balance() {
    return state.logs.reduce(function (a, l) { return a + (Number(l.totalAmount) || 0); }, 0);
  }

  /* ================= Đồng bộ ================= */

  var syncing = false;

  function setChip(cls, text) {
    var chip = $('sync-chip');
    chip.className = cls;
    $('sync-text').textContent = text;
  }

  function pending() { return state.logs.filter(function (l) { return !l.synced; }); }

  function refreshChip() {
    if (syncing) return setChip('wait', 'Đang gửi...');
    if (!window.Sync.isValidUrl(state.settings.scriptUrl)) return setChip('', 'Chưa kết nối');
    if (!navigator.onLine) return setChip('err', 'Mất mạng');
    var p = pending().length;
    if (p) return setChip('wait', 'Chờ gửi ' + p);
    return setChip('ok', 'Đã đồng bộ');
  }

  function flush(silent) {
    var url = state.settings.scriptUrl;
    var todo = pending();
    if (!todo.length || syncing) { refreshChip(); return Promise.resolve(); }
    if (!window.Sync.isValidUrl(url)) { refreshChip(); return Promise.resolve(); }
    if (!navigator.onLine) { refreshChip(); return Promise.resolve(); }

    syncing = true;
    refreshChip();

    return window.Sync.push(url, todo)
      .then(function (res) {
        todo.forEach(function (l) { l.synced = true; l.verified = !!res.verified; });
        saveLogs();
        syncing = false;
        refreshChip();
        if (!silent) {
          toast('ok', 'Đã đồng bộ', res.verified
            ? 'Đã ghi ' + todo.length + ' đêm lên Google Sheets.'
            : 'Đã gửi ' + todo.length + ' đêm (không đọc được xác nhận).');
        }
      })
      .catch(function (err) {
        syncing = false;
        setChip('err', 'Lỗi gửi');
        if (!silent) toast('err', 'Không gửi được', err.message);
      });
  }

  /* ================= Toast ================= */

  var toastTimer;
  function toast(kind, title, msg) {
    var el = $('toast');
    $('toast-ic').innerHTML = kind === 'ok' ? '&#10003;' : kind === 'err' ? '&#10007;' : '&#8505;';
    $('toast-title').textContent = title;
    $('toast-msg').textContent = msg || '';
    el.className = 'show ' + kind;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.className = kind; }, 3600);
  }

  /* ================= Màn hình Ghi nhận ================= */

  function renderHome() {
    var bal = balance();
    var b = $('ui-balance');
    b.textContent = R.formatMoney(bal);
    b.className = 'value ' + (bal >= 0 ? 'pos' : 'neg');
    $('ui-streak').textContent = state.streak;

    renderCountdown();
    renderRoutine();
    renderPreview();
    renderRecent();
  }

  function renderRecent() {
    var box = $('recent-list');
    var items = state.logs.slice(0, 3);
    if (!items.length) {
      box.innerHTML = '<div class="empty"><span class="em">&#128564;</span>Chưa có đêm nào được ghi.<br>Hãy đi ngủ sớm và bấm Xác nhận!</div>';
      return;
    }
    box.innerHTML = items.map(function (l) { return logCard(l, false); }).join('');
  }

  function logCard(l, withActions) {
    var cls = l.totalAmount > 0 ? 'good' : l.totalAmount < 0 ? 'bad' : '';
    var amtCls = l.totalAmount > 0 ? 'pos' : l.totalAmount < 0 ? 'neg' : '';
    var tags = '';
    if (l.streakBonus > 0) tags += '<span class="tag">&#128293; ' + esc(l.streakMsg) + ' +' + R.formatMoney(l.streakBonus) + '</span> ';
    if (l.routineBonus > 0) tags += '<span class="tag">&#9989; Đủ thói quen +' + R.formatMoney(l.routineBonus) + '</span> ';
    if (!l.synced) tags += '<span class="tag pending">&#8987; chờ đồng bộ</span>';

    var dur = l.duration ? ' &middot; ngủ ' + l.duration.toFixed(1) + 'h' : '';

    return '' +
      '<div class="panel log ' + cls + '">' +
        '<div style="flex:1;min-width:0">' +
          '<div><span class="time">' + esc(l.time) + '</span><span class="day">' + R.formatDay(l.date) + dur + '</span></div>' +
          '<div class="note">' + esc(l.msg) + '</div>' +
          (tags ? '<div>' + tags + '</div>' : '') +
        '</div>' +
        '<div>' +
          '<div class="amt ' + amtCls + '">' + R.formatSigned(l.totalAmount) + '</div>' +
          '<div class="meta">Chuỗi: ' + l.resultingStreak + '</div>' +
          (withActions ?
            '<div class="log-actions">' +
              '<button class="icon-btn" data-edit="' + l.date + '">Sửa</button>' +
              '<button class="icon-btn del" data-del="' + l.date + '">Xóa</button>' +
            '</div>' : '') +
        '</div>' +
      '</div>';
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  /* ---------- Đếm ngược ---------- */

  /** Mốc lên giường của ĐÊM HIỆN TẠI (rạng sáng vẫn thuộc đêm hôm trước) */
  function nextTargetDate() {
    var p = R.currentNightDate().split('-');
    var target = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]), 0, 0, 0, 0);
    target.setMinutes(Math.round(R.limitVal(state.settings.onTimeBefore) * 60));
    return target;
  }

  function humanGap(ms) {
    var mins = Math.round(Math.abs(ms) / 60000);
    var h = Math.floor(mins / 60), m = mins % 60;
    if (h && m) return h + ' giờ ' + m + ' phút';
    if (h) return h + ' giờ';
    return m + ' phút';
  }

  function renderCountdown() {
    var panel = $('countdown-panel');
    var main = $('cd-main'), sub = $('cd-sub'), fill = $('cd-fill');
    var night = R.currentNightDate();
    var logged = state.logs.filter(function (l) { return l.date === night; })[0];

    if (logged) {
      panel.className = 'panel';
      main.innerHTML = '&#10003; Đã ghi nhận ' + esc(logged.time);
      main.style.color = logged.totalAmount >= 0 ? 'var(--green)' : 'var(--red)';
      sub.innerHTML = esc(logged.msg) + ' &middot; <b>' + R.formatSigned(logged.totalAmount) + '</b>';
      fill.style.width = '100%';
      return;
    }
    main.style.color = '';

    var now = new Date();
    var target = nextTargetDate();
    var wd = (Number(state.settings.windDownMinutes) || 0) * 60000;
    var start = new Date(target.getTime() - wd);
    var diff = target.getTime() - now.getTime();

    if (diff <= 0) {
      panel.className = 'panel over';
      main.innerHTML = 'Trễ ' + humanGap(diff) + ' <small>so với giờ mục tiêu</small>';
      sub.textContent = 'Lên giường ngay, mỗi phút đều tính tiền.';
      fill.style.width = '100%';
      return;
    }

    var pct;
    if (now >= start) {
      panel.className = 'panel soon';
      main.innerHTML = 'Còn ' + humanGap(diff) + ' <small>tới giờ lên giường</small>';
      sub.textContent = 'Bắt đầu chuẩn bị ngủ ngay bây giờ.';
      pct = 100 - (diff / wd) * 20;
    } else {
      panel.className = 'panel';
      main.innerHTML = 'Còn ' + humanGap(diff) + ' <small>tới ' + state.settings.onTimeBefore + '</small>';
      sub.textContent = 'Bắt đầu chuẩn bị lúc ' +
        R.pad(start.getHours()) + ':' + R.pad(start.getMinutes()) + '.';
      var span = 5 * 3600e3;
      pct = Math.max(0, (span - (start.getTime() - now.getTime())) / span * 80);
    }
    fill.style.width = Math.max(2, Math.min(100, pct)) + '%';
  }

  /* ---------- Checklist chuẩn bị ngủ ---------- */

  function renderRoutine() {
    var list = $('routine-list');
    var steps = state.settings.routine || [];
    if (!steps.length) {
      list.innerHTML = '<div class="hint">Chưa có bước nào. Thêm ở tab Cài đặt.</div>';
      $('routine-count').textContent = '';
      $('routine-hint').textContent = '';
      return;
    }
    var checked = state.tonight.checked || [];
    list.innerHTML = steps.map(function (s, i) {
      var on = checked.indexOf(i) >= 0;
      return '<div class="step ' + (on ? 'done' : '') + '" data-step="' + i + '">' +
        '<span class="box">&#10003;</span>' +
        '<span class="lb">' + esc(s.label) + '</span>' +
        (Number(s.minutes) ? '<span class="mn">' + s.minutes + "'</span>" : '') +
      '</div>';
    }).join('');

    $('routine-count').textContent = '(' + checked.length + '/' + steps.length + ')';
    var total = steps.reduce(function (a, s) { return a + (Number(s.minutes) || 0); }, 0);
    $('routine-hint').innerHTML = checked.length >= steps.length
      ? 'Xong hết! Đêm nay được cộng thêm <b>' + R.formatMoney(state.settings.routineBonus) + '</b>.'
      : 'Làm đủ cả ' + steps.length + ' bước (khoảng ' + total + ' phút) để được thưởng thêm <b>' +
        R.formatMoney(state.settings.routineBonus) + '</b>.';
  }

  /* ---------- Xem trước thưởng/phạt ---------- */

  function renderPreview() {
    var time = $('in-time').value;
    var ev = time ? R.evaluate(time, state.settings) : null;
    if (!ev) {
      $('pv-msg').textContent = 'Chọn giờ để xem mức thưởng/phạt.';
      $('pv-extra').textContent = '';
      $('pv-amt').textContent = '--';
      $('pv-amt').className = 'pv-amt';
      return;
    }
    var steps = (state.settings.routine || []).length;
    var done = (state.tonight.checked || []).length;
    var rb = (steps && done >= steps) ? (Number(state.settings.routineBonus) || 0) : 0;

    var newStreak = ev.action === 'add' ? state.streak + 1 : ev.action === 'reset' ? 0 : state.streak;
    var ms = ev.action === 'add' ? R.milestoneFor(newStreak, state.settings) : { amount: 0, msg: '' };
    var total = ev.amount + ms.amount + rb;

    $('pv-msg').textContent = ev.msg;
    var extra = ['Chuỗi sau khi ghi: ' + newStreak + ' ngày'];
    if (ms.amount) extra.push(ms.msg + ' +' + R.formatMoney(ms.amount));
    if (rb) extra.push('Thói quen +' + R.formatMoney(rb));
    $('pv-extra').textContent = extra.join(' · ');
    $('pv-amt').textContent = R.formatSigned(total);
    $('pv-amt').className = 'pv-amt ' + (total > 0 ? 'pos' : total < 0 ? 'neg' : '');
  }

  /* ---------- Ghi nhận ---------- */

  function saveEntry() {
    var date = $('in-date').value;
    var time = $('in-time').value;
    var wake = $('in-wake').value;

    if (!date || !time) { toast('err', 'Thiếu dữ liệu', 'Hãy chọn ngày và giờ lên giường.'); return; }

    var existing = state.logs.filter(function (l) { return l.date === date; })[0];
    if (existing && !confirm('Đêm ' + R.formatDay(date) + ' đã có dữ liệu (' + existing.time + '). Ghi đè?')) return;

    state.logs = state.logs.filter(function (l) { return l.date !== date; });

    var isTonight = date === state.tonight.date;
    state.logs.push({
      id: Date.now(),
      date: date,
      time: time,
      wake: wake || '',
      routineDone: isTonight ? (state.tonight.checked || []).length : (existing ? existing.routineDone : 0),
      synced: false
    });

    recompute();
    saveLogs();
    renderAll();

    var saved = state.logs.filter(function (l) { return l.date === date; })[0];
    toast(saved.totalAmount >= 0 ? 'ok' : 'err',
      saved.totalAmount >= 0 ? 'Đã ghi nhận' : 'Đã ghi nhận (bị phạt)',
      R.formatSigned(saved.totalAmount) + ' · chuỗi ' + saved.resultingStreak + ' ngày');

    flush(true);
  }

  function deleteEntry(date) {
    if (!confirm('Xóa đêm ' + R.formatDay(date) + '?')) return;
    state.logs = state.logs.filter(function (l) { return l.date !== date; });
    recompute();
    saveLogs();
    renderAll();
    window.Sync.remove(state.settings.scriptUrl, date).catch(function () {});
    toast('ok', 'Đã xóa', 'Đêm ' + R.formatDay(date) + ' đã được gỡ bỏ.');
  }

  function editEntry(date) {
    var l = state.logs.filter(function (x) { return x.date === date; })[0];
    if (!l) return;
    $('in-date').value = l.date;
    $('in-time').value = l.time;
    $('in-wake').value = l.wake || '';
    switchTab('home');
    renderPreview();
    window.scrollTo(0, 0);
  }

  /* ================= Lịch sử ================= */

  function monthsAvailable() {
    var set = {};
    state.logs.forEach(function (l) { set[l.date.slice(0, 7)] = true; });
    return Object.keys(set).sort().reverse();
  }

  function renderHistory() {
    var sel = $('filter-month');
    var months = monthsAvailable();
    var current = sel.value;
    var opts = '<option value="all">Tất cả</option>' + months.map(function (m) {
      var p = m.split('-');
      return '<option value="' + m + '">Tháng ' + Number(p[1]) + '/' + p[0] + '</option>';
    }).join('');
    if (sel.innerHTML !== opts) {
      sel.innerHTML = opts;
      sel.value = (current && (current === 'all' || months.indexOf(current) >= 0)) ? current : 'all';
    }

    var f = sel.value;
    var rows = f === 'all' ? state.logs : state.logs.filter(function (l) { return l.date.indexOf(f) === 0; });
    var box = $('history-list');
    if (!rows.length) {
      box.innerHTML = '<div class="empty"><span class="em">&#128203;</span>Không có dữ liệu.</div>';
      return;
    }
    var sum = rows.reduce(function (a, l) { return a + l.totalAmount; }, 0);
    box.innerHTML =
      '<div class="panel" style="display:flex;justify-content:space-between;align-items:center">' +
        '<span style="color:var(--muted);font-size:13px">' + rows.length + ' đêm</span>' +
        '<span style="font-weight:800" class="' + (sum >= 0 ? 'pos' : 'neg') + '">' + R.formatSigned(sum) + '</span>' +
      '</div>' +
      rows.map(function (l) { return logCard(l, true); }).join('');
  }

  /* ================= Thống kê ================= */

  function renderStats() {
    var logs = state.logs;
    var now = new Date();
    var ym = now.getFullYear() + '-' + R.pad(now.getMonth() + 1);
    var rw = 0, pn = 0;
    logs.forEach(function (l) {
      if (l.date.indexOf(ym) !== 0) return;
      if (l.totalAmount > 0) rw += l.totalAmount; else pn += Math.abs(l.totalAmount);
    });
    $('st-month-reward').textContent = '+' + R.formatMoney(rw);
    $('st-month-penalty').textContent = '-' + R.formatMoney(pn);

    $('st-count').textContent = logs.length;
    var onTime = logs.filter(function (l) { return !l.late; }).length;
    $('st-rate').textContent = logs.length ? Math.round(onTime / logs.length * 100) + '%' : '0%';

    var vals = logs.map(function (l) { return l.val; }).filter(function (v) { return !isNaN(v); });
    $('st-avg').textContent = vals.length
      ? R.valToTime(vals.reduce(function (a, b) { return a + b; }, 0) / vals.length) : '--:--';

    var durs = logs.map(function (l) { return l.duration; }).filter(Boolean);
    $('st-dur').textContent = durs.length
      ? (durs.reduce(function (a, b) { return a + b; }, 0) / durs.length).toFixed(1) + ' giờ' : '--';

    $('st-best').textContent = state.best + ' ngày';
    $('st-cur').textContent = state.streak + ' ngày';
    var bal = balance();
    var t = $('st-total');
    t.textContent = R.formatMoney(bal);
    t.className = 'v ' + (bal >= 0 ? 'pos' : 'neg');

    renderChart();
  }

  function renderChart() {
    var byDate = {};
    state.logs.forEach(function (l) { byDate[l.date] = l; });

    var days = [];
    var base = new Date();
    if (base.getHours() < R.DAY_CUTOFF) base.setDate(base.getDate() - 1);
    for (var i = 13; i >= 0; i--) {
      var d = new Date(base);
      d.setDate(d.getDate() - i);
      days.push(R.toISODate(d));
    }

    var min = 21, max = 27;
    $('chart').innerHTML = days.map(function (iso) {
      var l = byDate[iso];
      var lbl = iso.slice(8);
      if (!l) return '<div class="col"><div class="bar none" style="height:10%"></div><span class="cl">' + lbl + '</span></div>';
      var v = Math.max(min, Math.min(max, l.val));
      var h = Math.round((v - min) / (max - min) * 88) + 12;
      return '<div class="col" title="' + iso + ' - ' + l.time + '">' +
        '<div class="bar ' + (l.late ? 'late' : '') + '" style="height:' + h + '%"></div>' +
        '<span class="cl">' + lbl + '</span></div>';
    }).join('');
  }

  /* ================= Cài đặt ================= */

  function renderSettings() {
    $('set-url').value = state.settings.scriptUrl || '';
    $('set-overflow').value = state.settings.overflowPerHour;
    $('set-ontime').value = clampTimeInput(state.settings.onTimeBefore);
    $('set-reset').value = clampTimeInput(state.settings.resetAfter);
    $('set-remind').value = clampTimeInput(state.settings.remindAt);
    $('set-routine-bonus').value = state.settings.routineBonus;
    $('set-winddown').value = state.settings.windDownMinutes;

    $('rules-list').innerHTML = state.settings.tiers.map(function (t, i) {
      return '<div class="rule-row">' +
        '<input data-r="until" data-i="' + i + '" value="' + esc(t.until) + '" placeholder="22:30">' +
        '<input data-r="amount" data-i="' + i + '" type="number" step="10000" value="' + (Number(t.amount) || 0) + '">' +
        '<input data-r="msg" data-i="' + i + '" value="' + esc(t.msg) + '">' +
        '<button class="rm" data-rm-tier="' + i + '" type="button">&times;</button>' +
      '</div>';
    }).join('');

    $('miles-list').innerHTML = state.settings.milestones.map(function (m, i) {
      return '<div class="rule-row" style="grid-template-columns:78px 1fr 34px">' +
        '<input data-m="days" data-i="' + i + '" type="number" value="' + (Number(m.days) || 0) + '">' +
        '<input data-m="amount" data-i="' + i + '" type="number" step="50000" value="' + (Number(m.amount) || 0) + '">' +
        '<button class="rm" data-rm-mile="' + i + '" type="button">&times;</button>' +
      '</div>';
    }).join('');

    $('routine-edit').innerHTML = state.settings.routine.map(function (s, i) {
      return '<div class="routine-edit-row">' +
        '<input data-s="label" data-i="' + i + '" value="' + esc(s.label) + '">' +
        '<input data-s="minutes" data-i="' + i + '" type="number" value="' + (Number(s.minutes) || 0) + '">' +
        '<button class="rm" data-rm-step="' + i + '" type="button">&times;</button>' +
      '</div>';
    }).join('');
  }

  /** input type=time không nhận 24:00+, quy về dạng 00:00 */
  function clampTimeInput(t) {
    var p = String(t || '').split(':');
    var h = Number(p[0]) || 0;
    return R.pad(h % 24) + ':' + R.pad(Number(p[1]) || 0);
  }

  function collectSettings() {
    var s = state.settings;
    document.querySelectorAll('#rules-list input').forEach(function (el) {
      var i = Number(el.dataset.i), k = el.dataset.r;
      if (!s.tiers[i]) return;
      s.tiers[i][k] = k === 'amount' ? Number(el.value) || 0 : el.value.trim();
    });
    document.querySelectorAll('#miles-list input').forEach(function (el) {
      var i = Number(el.dataset.i), k = el.dataset.m;
      if (s.milestones[i]) s.milestones[i][k] = Number(el.value) || 0;
    });
    document.querySelectorAll('#routine-edit input').forEach(function (el) {
      var i = Number(el.dataset.i), k = el.dataset.s;
      if (!s.routine[i]) return;
      s.routine[i][k] = k === 'minutes' ? Number(el.value) || 0 : el.value.trim();
    });
    s.tiers = s.tiers.filter(function (t) { return t.until; });
    s.milestones = s.milestones.filter(function (m) { return m.days > 0; });
    s.routine = s.routine.filter(function (r) { return r.label; });

    s.overflowPerHour = Number($('set-overflow').value) || 0;
    s.onTimeBefore = $('set-ontime').value || '22:30';
    s.resetAfter = $('set-reset').value || '23:30';
    s.remindAt = $('set-remind').value || '21:30';
    s.routineBonus = Number($('set-routine-bonus').value) || 0;
    s.windDownMinutes = Number($('set-winddown').value) || 0;
  }

  /* ================= Thông báo ================= */

  function notify(title, body, tag) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    try {
      if (navigator.serviceWorker && navigator.serviceWorker.ready) {
        navigator.serviceWorker.ready.then(function (reg) {
          reg.showNotification(title, { body: body, tag: tag, icon: 'icons/icon-192.png', badge: 'icons/icon-192.png' });
        }).catch(function () { new Notification(title, { body: body, tag: tag }); });
      } else {
        new Notification(title, { body: body, tag: tag });
      }
    } catch (e) { /* bỏ qua */ }
  }

  function checkReminders() {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    var night = R.currentNightDate();
    var done = {};
    try { done = JSON.parse(localStorage.getItem(KEY_NOTIFY) || '{}'); } catch (e) { done = {}; }
    if (done.date !== night) done = { date: night };

    if (state.logs.some(function (l) { return l.date === night; })) return;

    var now = new Date();
    var mins = now.getHours() * 60 + now.getMinutes();
    if (now.getHours() < R.DAY_CUTOFF) mins += 1440;

    function toMin(t) {
      var p = String(t).split(':');
      var m = Number(p[0]) * 60 + (Number(p[1]) || 0);
      if (m < R.DAY_CUTOFF * 60) m += 1440;
      return m;
    }

    var remind = toMin(state.settings.remindAt);
    var target = toMin(state.settings.onTimeBefore);

    if (!done.wind && mins >= remind && mins < remind + 30) {
      done.wind = 1;
      notify('Tới giờ chuẩn bị ngủ', 'Cất điện thoại, vận động nhẹ rồi lên giường trước ' +
        state.settings.onTimeBefore + '.', 'winddown');
    }
    if (!done.bed && mins >= target && mins < target + 45) {
      done.bed = 1;
      notify('Lên giường ngay!', 'Quá giờ mục tiêu là bắt đầu bị phạt tiền.', 'bedtime');
    }
    localStorage.setItem(KEY_NOTIFY, JSON.stringify(done));
  }

  /* ================= CSV ================= */

  function exportCsv() {
    var head = ['Ngay ngu', 'Gio len giuong', 'Gio thuc day', 'Thoi luong', 'Tien co ban', 'Thuong chuoi', 'Thuong thoi quen', 'Tong', 'Chuoi', 'Ly do'];
    var lines = [head.join(',')].concat(state.logs.map(function (l) {
      return [l.date, l.time, l.wake || '', l.duration || '', l.baseAmount, l.streakBonus,
        l.routineBonus || 0, l.totalAmount, l.resultingStreak, '"' + String(l.msg).replace(/"/g, '""') + '"'].join(',');
    }));
    var blob = new Blob(['﻿' + lines.join('\n')], { type: 'text/csv;charset=utf-8' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'giac-ngu-' + R.toISODate(new Date()) + '.csv';
    a.click();
    setTimeout(function () { URL.revokeObjectURL(a.href); }, 3000);
  }

  /* ================= Điều hướng ================= */

  function switchTab(name) {
    document.querySelectorAll('.tab').forEach(function (el) { el.classList.remove('active'); });
    $('tab-' + name).classList.add('active');
    document.querySelectorAll('nav button').forEach(function (b) {
      b.classList.toggle('on', b.dataset.tab === name);
    });
    if (name === 'history') renderHistory();
    if (name === 'stats') renderStats();
    if (name === 'settings') renderSettings();
  }

  function renderAll() {
    renderHome();
    if ($('tab-history').classList.contains('active')) renderHistory();
    if ($('tab-stats').classList.contains('active')) renderStats();
    refreshChip();
  }

  /* ================= Khởi động ================= */

  function bind() {
    document.querySelectorAll('nav button').forEach(function (b) {
      b.addEventListener('click', function () { switchTab(b.dataset.tab); });
    });

    $('in-time').addEventListener('input', renderPreview);
    $('in-time').addEventListener('change', renderPreview);

    $('btn-now').addEventListener('click', function () {
      var now = new Date();
      $('in-time').value = R.pad(now.getHours()) + ':' + R.pad(now.getMinutes());
      $('in-date').value = R.currentNightDate();
      renderPreview();
    });

    $('btn-save').addEventListener('click', saveEntry);
    $('sync-chip').addEventListener('click', function () { flush(false); });

    $('routine-list').addEventListener('click', function (e) {
      var el = e.target.closest('[data-step]');
      if (!el) return;
      var i = Number(el.dataset.step);
      var arr = state.tonight.checked || [];
      var pos = arr.indexOf(i);
      if (pos >= 0) arr.splice(pos, 1); else arr.push(i);
      state.tonight.checked = arr;
      saveTonight();
      renderRoutine();
      renderPreview();
    });

    $('history-list').addEventListener('click', function (e) {
      var d = e.target.dataset.del, ed = e.target.dataset.edit;
      if (d) deleteEntry(d);
      else if (ed) editEntry(ed);
    });
    $('filter-month').addEventListener('change', renderHistory);
    $('btn-export').addEventListener('click', exportCsv);

    $('btn-pull').addEventListener('click', function () {
      setChip('wait', 'Đang tải...');
      window.Sync.pull(state.settings.scriptUrl).then(function (rows) {
        var byDate = {};
        state.logs.forEach(function (l) { byDate[l.date] = l; });
        var added = 0;
        rows.forEach(function (r) {
          if (!r.date || !r.time) return;
          if (byDate[r.date]) return;
          byDate[r.date] = true;
          state.logs.push({
            id: r.date, date: r.date, time: r.time, wake: r.wake || '',
            routineDone: Number(r.routineDone) || 0, synced: true
          });
          added++;
        });
        recompute();
        saveLogs();
        renderAll();
        toast('ok', 'Đã tải về', added ? 'Bổ sung ' + added + ' đêm từ Google Sheets.' : 'Không có đêm nào mới.');
      }).catch(function (err) {
        setChip('err', 'Lỗi tải');
        toast('err', 'Không tải được', err.message);
      });
    });

    $('btn-save-url').addEventListener('click', function () {
      var url = $('set-url').value.trim();
      if (url && !window.Sync.isValidUrl(url)) {
        toast('err', 'URL không hợp lệ', 'URL phải bắt đầu bằng https://script.google.com và kết thúc bằng /exec');
        return;
      }
      state.settings.scriptUrl = url;
      saveSettings();
      refreshChip();
      toast('ok', 'Đã lưu URL', 'Bấm Kiểm tra để chắc chắn kết nối được.');
      flush(true);
    });

    $('btn-test').addEventListener('click', function () {
      var url = $('set-url').value.trim();
      $('conn-info').textContent = 'Đang kiểm tra...';
      window.Sync.ping(url).then(function (res) {
        $('conn-info').innerHTML = 'Kết nối tốt. Sheet <b>' + esc(res.sheet || '') + '</b> đang có <b>' +
          (res.rows || 0) + '</b> đêm.';
        toast('ok', 'Kết nối thành công', 'Google Sheets đã sẵn sàng.');
      }).catch(function (err) {
        $('conn-info').innerHTML = 'Lỗi: ' + esc(err.message) +
          '. Kiểm tra lại quyền truy cập Web App phải là <b>Anyone</b>.';
        toast('err', 'Không kết nối được', err.message);
      });
    });

    $('btn-add-rule').addEventListener('click', function () {
      collectSettings();
      state.settings.tiers.push({ until: '24:00', amount: -50000, msg: 'Mốc mới' });
      saveSettings(); renderSettings();
    });
    $('btn-add-mile').addEventListener('click', function () {
      collectSettings();
      state.settings.milestones.push({ days: 60, amount: 3000000 });
      saveSettings(); renderSettings();
    });
    $('btn-add-step').addEventListener('click', function () {
      collectSettings();
      state.settings.routine.push({ label: 'Việc mới', minutes: 5 });
      saveSettings(); renderSettings();
    });

    document.addEventListener('click', function (e) {
      var t = e.target;
      if (t.dataset.rmTier !== undefined) {
        collectSettings(); state.settings.tiers.splice(Number(t.dataset.rmTier), 1);
        saveSettings(); renderSettings(); recompute(); saveLogs(); renderHome();
      } else if (t.dataset.rmMile !== undefined) {
        collectSettings(); state.settings.milestones.splice(Number(t.dataset.rmMile), 1);
        saveSettings(); renderSettings(); recompute(); saveLogs(); renderHome();
      } else if (t.dataset.rmStep !== undefined) {
        collectSettings(); state.settings.routine.splice(Number(t.dataset.rmStep), 1);
        saveSettings(); renderSettings(); renderHome();
      }
    });

    $('btn-save-settings').addEventListener('click', function () {
      collectSettings();
      saveSettings();
      recompute();
      saveLogs();
      renderSettings();
      renderAll();
      toast('ok', 'Đã lưu cài đặt', 'Toàn bộ lịch sử đã được tính lại theo luật mới.');
      flush(true);
    });

    $('btn-clear').addEventListener('click', function () {
      if (!confirm('Xóa toàn bộ dữ liệu trên điện thoại? (Google Sheets vẫn giữ nguyên)')) return;
      state.logs = [];
      state.tonight = { date: R.currentNightDate(), checked: [] };
      saveLogs(); saveTonight();
      recompute();
      renderAll();
      toast('ok', 'Đã xóa', 'Có thể bấm "Tải từ Sheet" để khôi phục.');
    });

    $('btn-notify').addEventListener('click', function () {
      if (!('Notification' in window)) { toast('err', 'Không hỗ trợ', 'Trình duyệt này không có thông báo.'); return; }
      Notification.requestPermission().then(function (p) {
        if (p === 'granted') {
          notify('Đã bật nhắc nhở', 'Mỗi tối lúc ' + state.settings.remindAt + ' app sẽ nhắc bạn chuẩn bị ngủ.', 'test');
          toast('ok', 'Đã bật thông báo', 'Nhớ giữ app chạy nền để nhận nhắc nhở.');
        } else {
          toast('err', 'Bị từ chối', 'Hãy bật quyền thông báo trong cài đặt trình duyệt.');
        }
      });
    });

    window.addEventListener('online', function () { refreshChip(); flush(true); });
    window.addEventListener('offline', refreshChip);
    document.addEventListener('visibilitychange', function () {
      if (!document.hidden) { rollNight(); renderHome(); flush(true); checkReminders(); }
    });
  }

  /** Sang đêm mới thì reset checklist */
  function rollNight() {
    var night = R.currentNightDate();
    if (state.tonight.date !== night) {
      state.tonight = { date: night, checked: [] };
      saveTonight();
      $('in-date').value = night;
    }
  }

  function handleShortcut() {
    var q = new URLSearchParams(location.search);
    if (q.get('quick')) {
      var now = new Date();
      $('in-time').value = R.pad(now.getHours()) + ':' + R.pad(now.getMinutes());
      renderPreview();
      switchTab('home');
    }
    if (q.get('routine')) {
      switchTab('home');
      setTimeout(function () { $('routine-list').scrollIntoView({ behavior: 'smooth', block: 'center' }); }, 300);
    }
    if (q.get('quick') || q.get('routine')) history.replaceState({}, '', location.pathname);
  }

  function init() {
    loadState();
    $('in-date').value = R.currentNightDate();
    $('brand-sub').textContent = 'Giấc ngủ · mục tiêu trước ' + state.settings.onTimeBefore;

    bind();
    renderAll();
    handleShortcut();

    setInterval(function () { rollNight(); renderCountdown(); checkReminders(); }, 30000);
    setTimeout(function () { flush(true); }, 1200);

    if ('serviceWorker' in navigator) {
      navigator.serviceWorker.register('sw.js').catch(function () {});
    }
  }

  /* Nút cài đặt PWA */
  var deferredPrompt = null;
  window.addEventListener('beforeinstallprompt', function (e) {
    e.preventDefault();
    deferredPrompt = e;
    $('install-bar').classList.add('show');
  });
  window.addEventListener('appinstalled', function () { $('install-bar').classList.remove('show'); });
  document.addEventListener('DOMContentLoaded', function () {
    $('btn-install').addEventListener('click', function () {
      if (!deferredPrompt) return;
      deferredPrompt.prompt();
      deferredPrompt.userChoice.then(function () {
        deferredPrompt = null;
        $('install-bar').classList.remove('show');
      });
    });
    init();
  });
})();
