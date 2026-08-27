/* ===================================================================
   sync.js - Giao tiếp với Google Apps Script Web App.
   Dùng POST kiểu "simple request" (text/plain) để không dính CORS
   preflight. Nếu trình duyệt vẫn chặn đọc phản hồi, tự động gửi lại
   bằng no-cors để ít nhất dữ liệu vẫn được ghi.
   =================================================================== */
(function (global) {
  'use strict';

  var TIMEOUT = 15000;

  function withTimeout(promise, ms) {
    return new Promise(function (resolve, reject) {
      var t = setTimeout(function () { reject(new Error('Hết thời gian chờ')); }, ms);
      promise.then(function (v) { clearTimeout(t); resolve(v); },
                   function (e) { clearTimeout(t); reject(e); });
    });
  }

  function isValidUrl(url) {
    return typeof url === 'string' && /^https:\/\/script\.google\.com\/.+\/exec$/.test(url.trim());
  }

  /** POST đọc được phản hồi (cần Apps Script deploy quyền "Anyone") */
  function postJson(url, payload) {
    return withTimeout(fetch(url, {
      method: 'POST',
      /* text/plain => simple request => không có preflight OPTIONS */
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: JSON.stringify(payload),
      redirect: 'follow'
    }).then(function (res) {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.text();
    }).then(function (txt) {
      try { return JSON.parse(txt); }
      catch (e) { throw new Error('Phản hồi không hợp lệ (kiểm tra quyền truy cập Web App)'); }
    }), TIMEOUT);
  }

  /** Gửi "mù": không đọc được phản hồi nhưng dữ liệu vẫn tới nơi */
  function postBlind(url, payload) {
    var body = new URLSearchParams();
    body.append('payload', JSON.stringify(payload));
    return fetch(url, { method: 'POST', mode: 'no-cors', body: body })
      .then(function () { return { status: 'blind' }; });
  }

  function toRow(log) {
    return {
      date: log.date,
      time: log.time,
      wake: log.wake || '',
      duration: log.duration || '',
      baseAmount: log.baseAmount || 0,
      streakBonus: log.streakBonus || 0,
      routineBonus: log.routineBonus || 0,
      routineDone: log.routineDone || 0,
      totalAmount: log.totalAmount || 0,
      resultingStreak: log.resultingStreak || 0,
      msg: log.msg || '',
      streakMsg: log.streakMsg || ''
    };
  }

  var Sync = {
    isValidUrl: isValidUrl,

    /** Kiểm tra kết nối. Trả về {ok, rows, sheet} */
    ping: function (url) {
      if (!isValidUrl(url)) return Promise.reject(new Error('URL phải kết thúc bằng /exec'));
      return withTimeout(fetch(url + '?action=ping', { method: 'GET', redirect: 'follow' })
        .then(function (r) { return r.json(); }), TIMEOUT);
    },

    /** Lấy toàn bộ dữ liệu đã lưu trên Sheet */
    pull: function (url) {
      if (!isValidUrl(url)) return Promise.reject(new Error('URL phải kết thúc bằng /exec'));
      return withTimeout(fetch(url + '?action=list', { method: 'GET', redirect: 'follow' })
        .then(function (r) { return r.json(); })
        .then(function (data) {
          if (!data || data.status !== 'success') throw new Error((data && data.message) || 'Không đọc được dữ liệu');
          return data.rows || [];
        }), TIMEOUT);
    },

    /**
     * Đẩy 1 hoặc nhiều đêm lên Sheet. Cùng "Ngày ngủ" thì ghi đè, không nhân bản.
     * @returns {Promise<{verified:boolean}>}
     */
    push: function (url, logs) {
      if (!isValidUrl(url)) return Promise.reject(new Error('Chưa cấu hình URL Google Sheets'));
      var payload = { action: 'save', rows: (logs || []).map(toRow) };
      if (!payload.rows.length) return Promise.resolve({ verified: true });

      return postJson(url, payload)
        .then(function (res) {
          if (!res || res.status !== 'success') throw new Error((res && res.message) || 'Ghi thất bại');
          return { verified: true };
        })
        .catch(function (err) {
          /* Lỗi mạng/CORS: thử lại kiểu gửi mù trước khi báo hỏng */
          return postBlind(url, payload)
            .then(function () { return { verified: false, reason: err.message }; })
            .catch(function () { throw err; });
        });
    },

    /** Xóa một đêm khỏi Sheet */
    remove: function (url, date) {
      if (!isValidUrl(url)) return Promise.resolve({ verified: false });
      var payload = { action: 'delete', date: date };
      return postJson(url, payload).catch(function () { return postBlind(url, payload); });
    }
  };

  global.Sync = Sync;
})(window);
