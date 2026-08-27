/* ===================================================================
   rules.js - Luật thưởng/phạt, chuỗi kỷ luật và các hàm tính toán.
   Không đụng tới DOM, có thể test độc lập.
   =================================================================== */
(function (global) {
  'use strict';

  /* Giờ trước mốc này (18:00) được coi là rạng sáng của đêm hôm trước.
     Ví dụ 01:30 -> 25.5 để so sánh liên tục với 22:00 -> 22 */
  var DAY_CUTOFF = 18;

  var DEFAULT_SETTINGS = {
    /* Không ghi URL vào đây: mã nguồn công khai trên GitHub, ai có URL cũng
       ghi được vào Sheet của bạn. Dán URL một lần trong tab Cài đặt của app,
       nó được lưu vĩnh viễn trên máy bạn. */
    scriptUrl: '',
    tiers: [
      { until: '22:00', amount: 50000, msg: 'Tuyệt vời! Ngủ rất sớm.' },
      { until: '22:30', amount: 30000, msg: 'Tốt! Đạt mục tiêu.' },
      { until: '23:00', amount: 0, msg: 'An toàn. Không thưởng phạt.' },
      { until: '23:30', amount: -20000, msg: 'Hơi trễ. Bị phạt nhẹ.' },
      { until: '24:00', amount: -50000, msg: 'Trễ! Phạt nặng và mất chuỗi.' },
      { until: '25:00', amount: -100000, msg: 'Quá trễ. Phạt 100k.' },
      { until: '26:00', amount: -150000, msg: 'Cú đêm. Phạt 150k.' },
      { until: '27:00', amount: -250000, msg: 'Rất nguy hại cho sức khỏe!' }
    ],
    overflowPerHour: -100000,
    onTimeBefore: '22:30',
    resetAfter: '23:30',
    milestones: [
      { days: 3, amount: 100000 },
      { days: 7, amount: 300000 },
      { days: 14, amount: 700000 },
      { days: 30, amount: 2000000 }
    ],
    remindAt: '21:30',
    windDownMinutes: 45,
    routineBonus: 20000,
    routine: [
      { label: 'Cất điện thoại ra khỏi giường', minutes: 0 },
      { label: 'Vận động nhẹ / giãn cơ', minutes: 10 },
      { label: 'Ngồi máy massage', minutes: 15 },
      { label: 'Tắm nước ấm / vệ sinh cá nhân', minutes: 10 },
      { label: 'Giảm đèn, không màn hình', minutes: 0 }
    ]
  };

  /* ---------- Thời gian ---------- */

  /** "23:45" -> 23.75 ; "01:30" -> 25.5 (thuộc về đêm hôm trước) */
  function toVal(timeStr) {
    if (!timeStr) return NaN;
    var p = String(timeStr).split(':');
    var v = Number(p[0]) + (Number(p[1]) || 0) / 60;
    if (isNaN(v)) return NaN;
    if (v < DAY_CUTOFF) v += 24;
    return v;
  }

  /** Giống toVal nhưng dùng cho các mốc cấu hình, cho phép nhập 24:00 - 27:00 */
  function limitVal(timeStr) {
    if (!timeStr) return NaN;
    var p = String(timeStr).split(':');
    var h = Number(p[0]), m = Number(p[1]) || 0;
    if (isNaN(h)) return NaN;
    var v = h + m / 60;
    if (v < DAY_CUTOFF) v += 24; /* 00:30 nhập ở ô time -> 24.5 */
    return v;
  }

  /** 25.5 -> "01:30" */
  function valToTime(v) {
    if (isNaN(v)) return '--:--';
    var x = ((v % 24) + 24) % 24;
    var h = Math.floor(x);
    var m = Math.round((x - h) * 60);
    if (m === 60) { m = 0; h = (h + 1) % 24; }
    return pad(h) + ':' + pad(m);
  }

  function pad(n) { return (n < 10 ? '0' : '') + n; }

  /** Ngày local dạng YYYY-MM-DD */
  function toISODate(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  }

  /** "Đêm" hiện tại: trước 18:00 thì tính là đêm hôm qua */
  function currentNightDate(now) {
    var d = new Date(now || Date.now());
    if (d.getHours() < DAY_CUTOFF) d.setDate(d.getDate() - 1);
    return toISODate(d);
  }

  /* ---------- Luật ---------- */

  function sortedTiers(settings) {
    return settings.tiers
      .filter(function (t) { return t && t.until; })
      .slice()
      .sort(function (a, b) { return limitVal(a.until) - limitVal(b.until); });
  }

  /**
   * Đánh giá một giờ đi ngủ.
   * @returns {{amount:number, msg:string, action:'add'|'keep'|'reset', val:number}}
   */
  function evaluate(timeStr, settings) {
    var val = toVal(timeStr);
    if (isNaN(val)) return null;

    var tiers = sortedTiers(settings);
    var amount = 0, msg = '';
    var matched = false;

    for (var i = 0; i < tiers.length; i++) {
      if (val <= limitVal(tiers[i].until)) {
        amount = Number(tiers[i].amount) || 0;
        msg = tiers[i].msg || '';
        matched = true;
        break;
      }
    }

    if (!matched) {
      /* Quá mốc cuối: cộng dồn tiền phạt theo từng giờ trôi qua */
      var last = tiers[tiers.length - 1];
      var lastVal = last ? limitVal(last.until) : 24;
      var base = last ? (Number(last.amount) || 0) : 0;
      var extraHours = Math.floor(val - lastVal) + 1;
      amount = base + extraHours * (Number(settings.overflowPerHour) || 0);
      msg = 'Kịch khung phạt - phá hủy sức khỏe!';
    }

    var onTime = limitVal(settings.onTimeBefore);
    var reset = limitVal(settings.resetAfter);
    var action = 'keep';
    if (val <= onTime) action = 'add';
    else if (val > reset) action = 'reset';

    return { amount: amount, msg: msg, action: action, val: val };
  }

  function milestoneFor(streak, settings) {
    var list = settings.milestones || [];
    for (var i = 0; i < list.length; i++) {
      if (Number(list[i].days) === streak) {
        return { amount: Number(list[i].amount) || 0, msg: 'Thưởng chuỗi ' + streak + ' ngày!' };
      }
    }
    return { amount: 0, msg: '' };
  }

  /**
   * Tính lại toàn bộ chuỗi + tiền cho danh sách log (sắp xếp theo ngày tăng dần).
   * Nhờ vậy sửa/xóa một đêm cũ vẫn cho kết quả đúng.
   * Trả về mảng đã sắp xếp giảm dần (mới nhất trước).
   */
  function recomputeAll(logs, settings) {
    var asc = logs.slice().sort(function (a, b) {
      return a.date < b.date ? -1 : a.date > b.date ? 1 : 0;
    });
    var streak = 0;
    var best = 0;

    asc.forEach(function (log) {
      var ev = evaluate(log.time, settings);
      if (!ev) return;
      if (ev.action === 'add') streak += 1;
      else if (ev.action === 'reset') streak = 0;

      var bonus = ev.action === 'add' ? milestoneFor(streak, settings) : { amount: 0, msg: '' };

      /* Thưởng khi hoàn thành đủ các bước chuẩn bị ngủ */
      var steps = (settings.routine || []).length;
      var done = Number(log.routineDone) || 0;
      log.routineBonus = (steps > 0 && done >= steps) ? (Number(settings.routineBonus) || 0) : 0;

      log.baseAmount = ev.amount;
      log.streakBonus = bonus.amount;
      log.streakMsg = bonus.msg;
      log.totalAmount = ev.amount + bonus.amount + log.routineBonus;
      log.msg = ev.msg;
      log.resultingStreak = streak;
      log.late = ev.action !== 'add';
      log.val = ev.val;
      log.duration = durationHours(log.time, log.wake);

      if (streak > best) best = streak;
    });

    asc.reverse();
    asc.bestStreak = best;
    asc.currentStreak = streak;
    return asc;
  }

  /** Số giờ ngủ giữa giờ lên giường và giờ thức dậy */
  function durationHours(bed, wake) {
    if (!bed || !wake) return null;
    var b = toVal(bed);
    var w = Number(wake.split(':')[0]) + (Number(wake.split(':')[1]) || 0) / 60 + 24;
    var d = w - b;
    while (d < 0) d += 24;
    if (d > 16) d -= 24;
    return d > 0 ? Math.round(d * 100) / 100 : null;
  }

  /* ---------- Định dạng ---------- */

  function formatMoney(n) {
    var num = Number(n) || 0;
    return num.toLocaleString('vi-VN') + ' ₫';
  }

  function formatSigned(n) {
    var num = Number(n) || 0;
    return (num > 0 ? '+' : '') + formatMoney(num);
  }

  function formatDay(iso) {
    var p = String(iso).split('-');
    return p[2] + '/' + p[1];
  }

  global.Rules = {
    DAY_CUTOFF: DAY_CUTOFF,
    DEFAULT_SETTINGS: DEFAULT_SETTINGS,
    toVal: toVal,
    limitVal: limitVal,
    valToTime: valToTime,
    toISODate: toISODate,
    currentNightDate: currentNightDate,
    evaluate: evaluate,
    milestoneFor: milestoneFor,
    recomputeAll: recomputeAll,
    durationHours: durationHours,
    formatMoney: formatMoney,
    formatSigned: formatSigned,
    formatDay: formatDay,
    pad: pad
  };
})(window);
