/*******************************************************************
 * THỜI GIAN BIỂU - Module Giấc ngủ
 *
 * CÁCH DÙNG
 *  1. Mở Google Sheet -> Tiện ích mở rộng -> Apps Script.
 *  2. Xóa hết mã cũ, dán toàn bộ file này vào, bấm Lưu.
 *  3. Deploy -> New deployment -> Web app
 *       Execute as:      Me
 *       Who has access:  Anyone      <-- bắt buộc, nếu không app sẽ báo lỗi
 *     Copy "Web app URL" (kết thúc bằng /exec) dán vào phần Cài đặt của app.
 *  4. TỰ ĐỘNG HÓA: chọn hàm setupTriggers ở thanh trên cùng rồi bấm Run.
 *     Từ đó Google sẽ tự làm 3 việc mỗi ngày, kể cả khi bạn không mở app:
 *       - Nhắc chuẩn bị ngủ qua email đúng giờ REMIND_HOUR.
 *       - Sáng hôm sau nếu quên ghi thì tự chấm "Không ghi nhận" + phạt.
 *       - Tối Chủ nhật gửi báo cáo tuần.
 *
 *  Mỗi lần sửa mã phải Deploy -> Manage deployments -> Edit -> New version.
 *******************************************************************/

/* ===================== CẤU HÌNH ===================== */

var CONFIG = {
  SHEET_NAME: '',          // để trống = dùng sheet đầu tiên
  TARGET_TIME: '22:30',    // giờ mục tiêu lên giường
  REMIND_HOUR: 21,         // giờ gửi email nhắc chuẩn bị ngủ
  MISS_PENALTY: -100000,   // phạt khi quên không ghi nhận
  MISS_CHECK_HOUR: 8,      // giờ sáng kiểm tra đêm hôm trước
  WEEKLY_REPORT_HOUR: 20,  // giờ tối Chủ nhật gửi báo cáo tuần
  EMAIL: ''                // để trống = email chủ sở hữu Sheet
};

var HEADERS = [
  'Thời gian nhập', 'Ngày ngủ', 'Giờ lên giường', 'Biến động (VNĐ)',
  'Chuỗi hiện tại (Ngày)', 'Lý do', 'Thưởng chuỗi', 'Giờ thức dậy',
  'Thời lượng (giờ)', 'Bước chuẩn bị', 'Thưởng thói quen', 'Tiền cơ bản'
];

var COL = { STAMP: 1, DATE: 2, TIME: 3, TOTAL: 4, STREAK: 5, MSG: 6, STREAK_BONUS: 7,
            WAKE: 8, DURATION: 9, ROUTINE: 10, ROUTINE_BONUS: 11, BASE: 12 };

/* ===================== ĐIỂM VÀO ===================== */

function doPost(e) {
  try {
    return json(handle(readRequest(e)));
  } catch (err) {
    return json({ status: 'error', message: String(err && err.message || err) });
  }
}

function doGet(e) {
  try {
    var p = (e && e.parameter) || {};
    var action = p.action || 'ping';

    if (action === 'list') return json({ status: 'success', rows: listRows() });
    if (action === 'ping') {
      var sheet = getSheet();
      return json({
        status: 'success',
        sheet: sheet.getName(),
        rows: Math.max(0, sheet.getLastRow() - 1),
        time: new Date().toISOString()
      });
    }
    /* Cho phép ghi qua GET để tiện gỡ lỗi */
    return json(handle(readRequest(e)));
  } catch (err) {
    return json({ status: 'error', message: String(err && err.message || err) });
  }
}

/** Gộp mọi kiểu gửi dữ liệu về cùng một dạng */
function readRequest(e) {
  e = e || {};
  var p = e.parameter || {};

  if (e.postData && e.postData.contents) {
    try { return JSON.parse(e.postData.contents); } catch (ignore) {}
  }
  if (p.payload) {
    try { return JSON.parse(p.payload); } catch (ignore) {}
  }
  /* Tương thích ngược với phiên bản app cũ gửi từng tham số rời */
  if (p.date || p.time) {
    return { action: 'save', rows: [{
      date: p.date || '', time: p.time || '',
      totalAmount: Number(p.totalAmount || 0),
      resultingStreak: Number(p.resultingStreak || 0),
      msg: p.msg || '', streakMsg: p.streakMsg || ''
    }] };
  }
  return { action: p.action || 'noop' };
}

function handle(req) {
  var action = req.action || 'save';

  if (action === 'save') {
    var rows = req.rows || [];
    var n = 0;
    for (var i = 0; i < rows.length; i++) { if (upsert(rows[i])) n++; }
    return { status: 'success', saved: n };
  }

  if (action === 'delete') {
    return { status: 'success', deleted: removeByDate(req.date) };
  }

  if (action === 'list') return { status: 'success', rows: listRows() };

  return { status: 'success', message: 'Không có gì để làm' };
}

function json(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}

/* ===================== SHEET ===================== */

function getSheet() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = CONFIG.SHEET_NAME ? ss.getSheetByName(CONFIG.SHEET_NAME) : ss.getSheets()[0];
  if (!sheet) sheet = ss.insertSheet(CONFIG.SHEET_NAME || 'Giấc ngủ');
  ensureHeaders(sheet);
  return sheet;
}

function ensureHeaders(sheet) {
  if (sheet.getLastRow() === 0) {
    sheet.appendRow(HEADERS);
  } else {
    var width = sheet.getLastColumn();
    if (width < HEADERS.length) {
      sheet.getRange(1, 1, 1, HEADERS.length).setValues([HEADERS]);
    }
  }
  sheet.getRange(1, 1, 1, HEADERS.length)
    .setFontWeight('bold')
    .setBackground('#e2e8f0');
  sheet.setFrozenRows(1);
}

/** Chuẩn hóa ô ngày về chuỗi YYYY-MM-DD dù Sheet lưu kiểu Date hay text */
function normDate(v) {
  if (v instanceof Date) {
    return Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  }
  return String(v || '').trim();
}

function findRowByDate(sheet, date) {
  var last = sheet.getLastRow();
  if (last < 2) return -1;
  var col = sheet.getRange(2, COL.DATE, last - 1, 1).getValues();
  for (var i = 0; i < col.length; i++) {
    if (normDate(col[i][0]) === date) return i + 2;
  }
  return -1;
}

/** Ghi mới hoặc ghi đè đêm cùng ngày -> Sheet không bao giờ bị nhân bản */
function upsert(row) {
  if (!row || !row.date) return false;
  var sheet = getSheet();
  var lock = LockService.getScriptLock();
  try { lock.waitLock(15000); } catch (e) { /* vẫn ghi tiếp */ }

  try {
    var values = [
      new Date(),
      row.date,
      row.time || '',
      Number(row.totalAmount) || 0,
      Number(row.resultingStreak) || 0,
      row.msg || '',
      row.streakMsg || '',
      row.wake || '',
      row.duration === '' || row.duration == null ? '' : Number(row.duration),
      Number(row.routineDone) || 0,
      Number(row.routineBonus) || 0,
      Number(row.baseAmount) || 0
    ];

    var at = findRowByDate(sheet, row.date);
    if (at === -1) {
      sheet.appendRow(values);
      at = sheet.getLastRow();
    } else {
      sheet.getRange(at, 1, 1, values.length).setValues([values]);
    }

    sheet.getRange(at, COL.TOTAL).setNumberFormat('#,##0 ₫');
    sheet.getRange(at, COL.STREAK_BONUS).setNumberFormat('#,##0 ₫');
    sheet.getRange(at, COL.ROUTINE_BONUS).setNumberFormat('#,##0 ₫');
    sheet.getRange(at, COL.BASE).setNumberFormat('#,##0 ₫');
    sheet.getRange(at, COL.DATE).setNumberFormat('@');

    /* Tô màu dòng theo kết quả */
    var color = values[3] > 0 ? '#e7f8f0' : values[3] < 0 ? '#fdeaec' : '#ffffff';
    sheet.getRange(at, 1, 1, HEADERS.length).setBackground(color);
    return true;
  } finally {
    try { lock.releaseLock(); } catch (e) {}
  }
}

function removeByDate(date) {
  if (!date) return 0;
  var sheet = getSheet();
  var at = findRowByDate(sheet, date);
  if (at === -1) return 0;
  sheet.deleteRow(at);
  return 1;
}

function listRows() {
  var sheet = getSheet();
  var last = sheet.getLastRow();
  if (last < 2) return [];
  var data = sheet.getRange(2, 1, last - 1, HEADERS.length).getValues();
  var out = [];
  for (var i = 0; i < data.length; i++) {
    var r = data[i];
    if (!r[COL.DATE - 1]) continue;
    out.push({
      date: normDate(r[COL.DATE - 1]),
      time: formatTimeCell(r[COL.TIME - 1]),
      totalAmount: Number(r[COL.TOTAL - 1]) || 0,
      resultingStreak: Number(r[COL.STREAK - 1]) || 0,
      msg: String(r[COL.MSG - 1] || ''),
      streakMsg: String(r[COL.STREAK_BONUS - 1] || ''),
      wake: formatTimeCell(r[COL.WAKE - 1]),
      duration: Number(r[COL.DURATION - 1]) || 0,
      routineDone: Number(r[COL.ROUTINE - 1]) || 0
    });
  }
  return out;
}

/** Sheet có thể tự đổi "22:30" thành kiểu Date, đưa về lại chuỗi HH:mm */
function formatTimeCell(v) {
  if (v instanceof Date) {
    return Utilities.formatDate(v, Session.getScriptTimeZone(), 'HH:mm');
  }
  return String(v || '').trim();
}

/* ===================== TỰ ĐỘNG HÓA ===================== */

/** Chạy hàm này MỘT LẦN để bật toàn bộ tự động hóa. */
function setupTriggers() {
  var existing = ScriptApp.getProjectTriggers();
  for (var i = 0; i < existing.length; i++) {
    ScriptApp.deleteTrigger(existing[i]);
  }

  ScriptApp.newTrigger('dailyReminder').timeBased()
    .everyDays(1).atHour(CONFIG.REMIND_HOUR).nearMinute(0).create();

  ScriptApp.newTrigger('checkMissingNight').timeBased()
    .everyDays(1).atHour(CONFIG.MISS_CHECK_HOUR).nearMinute(0).create();

  ScriptApp.newTrigger('weeklyReport').timeBased()
    .onWeekDay(ScriptApp.WeekDay.SUNDAY).atHour(CONFIG.WEEKLY_REPORT_HOUR).nearMinute(0).create();

  getSheet();
  return 'Đã bật 3 tự động: nhắc ngủ ' + CONFIG.REMIND_HOUR + 'h, kiểm tra lúc ' +
    CONFIG.MISS_CHECK_HOUR + 'h, báo cáo tuần Chủ nhật ' + CONFIG.WEEKLY_REPORT_HOUR + 'h.';
}

function removeTriggers() {
  var list = ScriptApp.getProjectTriggers();
  for (var i = 0; i < list.length; i++) ScriptApp.deleteTrigger(list[i]);
  return 'Đã tắt toàn bộ tự động.';
}

function mailTo() {
  return CONFIG.EMAIL || Session.getEffectiveUser().getEmail();
}

function todayIso(offsetDays) {
  var d = new Date();
  if (offsetDays) d.setDate(d.getDate() + offsetDays);
  return Utilities.formatDate(d, Session.getScriptTimeZone(), 'yyyy-MM-dd');
}

/** Email nhắc chuẩn bị ngủ mỗi tối */
function dailyReminder() {
  var streak = currentStreak();
  var body =
    'Còn khoảng ' + (targetMinutes() - CONFIG.REMIND_HOUR * 60) + ' phút nữa là tới giờ lên giường (' +
    CONFIG.TARGET_TIME + ').\n\n' +
    'Việc cần làm ngay bây giờ:\n' +
    '  1. Cất điện thoại ra khỏi giường\n' +
    '  2. Vận động nhẹ / giãn cơ 10 phút\n' +
    '  3. Ngồi máy massage 15 phút\n' +
    '  4. Tắm nước ấm, giảm đèn\n\n' +
    'Chuỗi kỷ luật hiện tại: ' + streak + ' ngày. Đừng để mất đêm nay.';

  MailApp.sendEmail(mailTo(), '🌙 Tới giờ chuẩn bị ngủ', body);
}

function targetMinutes() {
  var p = String(CONFIG.TARGET_TIME).split(':');
  return Number(p[0]) * 60 + (Number(p[1]) || 0);
}

/** Sáng hôm sau: quên ghi nhận thì tự chấm và phạt */
function checkMissingNight() {
  var night = todayIso(-1);
  var sheet = getSheet();
  if (findRowByDate(sheet, night) !== -1) return;

  upsert({
    date: night,
    time: '',
    totalAmount: CONFIG.MISS_PENALTY,
    baseAmount: CONFIG.MISS_PENALTY,
    resultingStreak: 0,
    msg: 'Không ghi nhận - tự động phạt và mất chuỗi',
    streakMsg: '',
    wake: '', duration: '', routineDone: 0, routineBonus: 0
  });

  MailApp.sendEmail(mailTo(), '⚠️ Đêm ' + night + ' chưa được ghi nhận',
    'Bạn quên ghi giờ đi ngủ đêm ' + night + '.\n' +
    'Hệ thống đã tự ghi mức phạt ' + CONFIG.MISS_PENALTY.toLocaleString('vi-VN') + ' ₫ và đặt lại chuỗi về 0.\n\n' +
    'Nếu thực ra bạn ngủ đúng giờ, hãy mở app, ghi lại đêm đó - dữ liệu sẽ được ghi đè.');
}

/** Báo cáo tuần */
function weeklyReport() {
  var rows = listRows();
  var from = todayIso(-6);
  var week = rows.filter(function (r) { return r.date >= from; });

  var total = 0, late = 0;
  week.forEach(function (r) {
    total += r.totalAmount;
    if (r.totalAmount < 0) late++;
  });

  var lines = week.sort(function (a, b) { return a.date < b.date ? -1 : 1; }).map(function (r) {
    return '  ' + r.date + '  ' + (r.time || '--:--') + '   ' +
      (r.totalAmount >= 0 ? '+' : '') + r.totalAmount.toLocaleString('vi-VN') + ' ₫';
  });

  MailApp.sendEmail(mailTo(), '📊 Báo cáo giấc ngủ 7 ngày qua',
    'Đã ghi nhận: ' + week.length + '/7 đêm\n' +
    'Số đêm bị phạt: ' + late + '\n' +
    'Tổng biến động: ' + (total >= 0 ? '+' : '') + total.toLocaleString('vi-VN') + ' ₫\n' +
    'Chuỗi hiện tại: ' + currentStreak() + ' ngày\n\n' +
    'Chi tiết:\n' + lines.join('\n') + '\n\n' +
    (late === 0 ? 'Tuần hoàn hảo. Giữ nhịp này!' : 'Tuần tới cố gắng giảm số đêm trễ nhé.'));
}

function currentStreak() {
  var sheet = getSheet();
  var last = sheet.getLastRow();
  if (last < 2) return 0;
  return Number(sheet.getRange(last, COL.STREAK).getValue()) || 0;
}

/* ===================== KIỂM TRA NHANH ===================== */

/** Chọn hàm này rồi bấm Run để thử ghi 1 dòng mẫu. */
function testWrite() {
  var res = upsert({
    date: todayIso(-1), time: '22:15', totalAmount: 30000, baseAmount: 30000,
    resultingStreak: 1, msg: 'Dòng thử nghiệm', streakMsg: '',
    wake: '06:00', duration: 7.75, routineDone: 5, routineBonus: 0
  });
  Logger.log(res ? 'Ghi thành công' : 'Ghi thất bại');
}
