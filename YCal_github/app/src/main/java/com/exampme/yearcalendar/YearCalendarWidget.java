package com.exampme.yearcalendar;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.RemoteViews;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Calendar;

public class YearCalendarWidget extends AppWidgetProvider {

    static final String[] MON = {
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };

    static final String PREFS        = "ycal_prefs";
    static final String KEY_ODD      = "odd_week_start_ms";
    static final String KEY_WEEKDAY  = "color_weekday";   // RGB без альфы
    static final String KEY_WEEKEND  = "color_weekend";
    static final String KEY_BG_RGB   = "bg_rgb";
    static final String KEY_BG_ALPHA = "bg_alpha";        // 0–255

    static final int DEF_WEEKDAY  = 0xFFFFFF;
    static final int DEF_WEEKEND  = 0xFF4444;
    static final int DEF_BG_RGB   = 0x000000;
    static final int DEF_BG_ALPHA = 0;

    // ── AppWidgetProvider ───────────────────────────────────────────────────

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) update(ctx, mgr, id);
    }

    static void update(Context ctx, AppWidgetManager mgr, int id) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        int W = dm.widthPixels, H = dm.heightPixels;

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.TRANSPARENT);

        Calendar today = Calendar.getInstance();
        int yr     = today.get(Calendar.YEAR);
        int todayM = today.get(Calendar.MONTH);
        int todayD = today.get(Calendar.DAY_OF_MONTH);

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long oddRefMs   = prefs.getLong(KEY_ODD, -1);
        int weekdayRgb  = prefs.getInt(KEY_WEEKDAY, DEF_WEEKDAY);
        int weekendRgb  = prefs.getInt(KEY_WEEKEND, DEF_WEEKEND);
        int bgRgb       = prefs.getInt(KEY_BG_RGB,  DEF_BG_RGB);
        int bgAlpha     = prefs.getInt(KEY_BG_ALPHA, DEF_BG_ALPHA);

        int weekdayColor = 0xFF000000 | weekdayRgb;
        int weekendColor = 0xFF000000 | weekendRgb;
        int bgColor      = (bgAlpha << 24) | (bgRgb & 0xFFFFFF);

        float padX  = W * 0.01f, padY = H * 0.008f;
        float cellW = (W - padX * 2) / 3f;
        float cellH = (H - padY - H * 0.01f) / 4f;

        for (int m = 0; m < 12; m++) {
            float x0 = padX + (m % 3) * cellW;
            float y0 = padY + (m / 3) * cellH;
            drawMonth(c, x0, y0, cellW, cellH, yr, m, todayM, todayD, oddRefMs,
                      weekdayColor, weekendColor, bgColor);
        }

        RemoteViews views = new RemoteViews(ctx.getPackageName(), R.layout.w);
        views.setImageViewBitmap(R.id.i, bmp);

        Intent intent = new Intent(ctx, SettingsActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.i, pi);

        mgr.updateAppWidget(id, views);
    }

    // ── Odd-week logic ──────────────────────────────────────────────────────

    static boolean isOddWeek(long oddRefMs, int yr, int m, int col, int firstDow) {
        if (oddRefMs < 0) return false;

        java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");

        // oddRefMs хранится как UTC-полдень — читаем дату прямо в UTC
        Calendar ref = Calendar.getInstance(utc);
        ref.setTimeInMillis(oddRefMs);
        int refDow = (ref.get(Calendar.DAY_OF_WEEK) + 5) % 7; // 0=Пн … 6=Вс
        ref.add(Calendar.DAY_OF_YEAR, -refDow);

        Calendar colMon = Calendar.getInstance(utc);
        colMon.set(yr, m, 1, 12, 0, 0);
        colMon.set(Calendar.MILLISECOND, 0);
        colMon.add(Calendar.DAY_OF_YEAR, -firstDow + col * 7);

        long diffMs    = colMon.getTimeInMillis() - ref.getTimeInMillis();
        long diffWeeks = diffMs / (7L * 24 * 3600 * 1000);
        return ((diffWeeks % 2) + 2) % 2 == 0;
    }

    // ── Drawing ─────────────────────────────────────────────────────────────

    static void drawMonth(Canvas c, float x0, float y0, float cW, float cH,
                          int yr, int m, int todayM, int todayD, long oddRefMs,
                          int weekdayColor, int weekendColor, int bgColor) {
        float pH = cW * 0.015f, pV = cH * 0.01f;
        float iX = x0+pH, iY = y0+pV, iW = cW-pH*2, iH = cH-pV*2;

        // Фон ячейки
        if (Color.alpha(bgColor) > 0) {
            Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            bgPaint.setColor(bgColor);
            c.drawRoundRect(new RectF(iX, iY, iX+iW, iY+iH), 12, 12, bgPaint);
        }

        float fs = iH * 0.055f;
        Paint mp = new Paint(Paint.ANTI_ALIAS_FLAG);
        mp.setColor(0xFFFFFFFF);
        mp.setTextSize(fs);
        mp.setTypeface(Typeface.DEFAULT_BOLD);
        mp.setTextAlign(Paint.Align.CENTER);
        mp.setShadowLayer(4, 0, 2, 0xCC000000);
        c.drawText(MON[m], iX+iW/2, iY+fs*1.05f, mp);

        Calendar cal = Calendar.getInstance();
        cal.set(yr, m, 1);
        int totalDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int firstDow  = (cal.get(Calendar.DAY_OF_WEEK)+5)%7;
        int numCols   = (firstDow + totalDays + 6) / 7;

        float daysTop = iY + fs * 1.2f;
        float daysH   = iH - fs * 1.2f;
        float colW    = iW / numCols;
        float rowH    = daysH / 7f;
        float dayFs   = Math.min(rowH * 0.85f, colW * 0.78f);

        Paint oddBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        oddBg.setColor(0x44FFFFFF);
        for (int col = 0; col < numCols; col++) {
            if (!isOddWeek(oddRefMs, yr, m, col, firstDow)) continue;
            int firstRow = -1, lastRow = -1;
            for (int day = 1; day <= totalDays; day++) {
                int idx = firstDow + day - 1;
                if (idx / 7 == col) {
                    int row = idx % 7;
                    if (firstRow < 0) firstRow = row;
                    lastRow = row;
                }
            }
            if (firstRow < 0) continue;
            float bx   = iX + col * colW + 1;
            float by   = daysTop + firstRow * rowH;
            float bbot = daysTop + (lastRow + 1) * rowH;
            c.drawRoundRect(new RectF(bx, by, bx + colW - 2, bbot), 6, 6, oddBg);
        }

        Paint dp = new Paint(Paint.ANTI_ALIAS_FLAG);
        dp.setTextSize(dayFs);
        dp.setTextAlign(Paint.Align.CENTER);
        dp.setTypeface(Typeface.DEFAULT_BOLD);
        dp.setShadowLayer(3, 0, 1, 0xAA000000);

        Paint todayBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        todayBg.setColor(0xEEFFD23C);

        for (int day = 1; day <= totalDays; day++) {
            int idx  = firstDow + day - 1;
            int col  = idx / 7;
            int row  = idx % 7;
            float cx = iX + col*colW + colW/2f;
            float cy = daysTop + row*rowH + dayFs*0.88f;
            boolean isToday = m==todayM && day==todayD;
            boolean isWe    = row >= 5;
            if (isToday) {
                float r = Math.min(colW, rowH) * 0.46f;
                c.drawCircle(cx, cy - dayFs*0.3f, r, todayBg);
                dp.setColor(0xFF111111);
            } else {
                dp.setColor(isWe ? weekendColor : weekdayColor);
            }
            c.drawText(String.valueOf(day), cx, cy, dp);
        }
    }

    // ── SettingsActivity ────────────────────────────────────────────────────

    public static class SettingsActivity extends Activity {

        private DatePicker      datePicker;
        private ColorPickerView cpWeekday, cpWeekend, cpBg;
        private SeekBar         alphaBar;
        private View            alphaPreview;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            final SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            ScrollView scroll = new ScrollView(this);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(px(20), px(24), px(20), px(32));
            root.setBackgroundColor(0xFF1A1A2E);
            scroll.addView(root);

            // ── Нечётная неделя ──────────────────────────────────────────────
            addSection(root, "Нечётная неделя");

            TextView sub = new TextView(this);
            sub.setText("Выбери любой день нечётной недели:");
            sub.setTextSize(14);
            sub.setTextColor(0xFFAAAAAA);
            sub.setPadding(0, 0, 0, px(8));
            root.addView(sub);

            datePicker = new DatePicker(this);
            datePicker.setCalendarViewShown(false);
            long savedOdd = prefs.getLong(KEY_ODD, -1);
            if (savedOdd > 0) {
                Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                cal.setTimeInMillis(savedOdd);
                datePicker.updateDate(cal.get(Calendar.YEAR),
                                      cal.get(Calendar.MONTH),
                                      cal.get(Calendar.DAY_OF_MONTH));
            }
            root.addView(datePicker);

            // ── Цвет будних дней ─────────────────────────────────────────────
            addSection(root, "Цвет будних дней");
            cpWeekday = new ColorPickerView(this, prefs.getInt(KEY_WEEKDAY, DEF_WEEKDAY));
            root.addView(cpWeekday);

            // ── Цвет выходных ────────────────────────────────────────────────
            addSection(root, "Цвет выходных");
            cpWeekend = new ColorPickerView(this, prefs.getInt(KEY_WEEKEND, DEF_WEEKEND));
            root.addView(cpWeekend);

            // ── Фон ячейки ───────────────────────────────────────────────────
            addSection(root, "Фон ячейки");
            cpBg = new ColorPickerView(this, prefs.getInt(KEY_BG_RGB, DEF_BG_RGB));
            root.addView(cpBg);

            TextView alphaLabel = new TextView(this);
            alphaLabel.setText("Прозрачность  (0 = прозрачный  /  255 = непрозрачный)");
            alphaLabel.setTextSize(13);
            alphaLabel.setTextColor(0xFFCCCCCC);
            alphaLabel.setPadding(0, px(12), 0, px(4));
            root.addView(alphaLabel);

            // Полоска предпросмотра цвета+альфы
            alphaPreview = new View(this);
            LinearLayout.LayoutParams apLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, px(28));
            apLp.bottomMargin = px(6);
            alphaPreview.setLayoutParams(apLp);
            int initAlpha  = prefs.getInt(KEY_BG_ALPHA, DEF_BG_ALPHA);
            int initBgRgb  = prefs.getInt(KEY_BG_RGB,   DEF_BG_RGB);
            updateAlphaPreview(initAlpha, initBgRgb);
            root.addView(alphaPreview);

            alphaBar = new SeekBar(this);
            alphaBar.setMax(255);
            alphaBar.setProgress(initAlpha);
            alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int v, boolean u) {
                    updateAlphaPreview(v, cpBg.getRgb());
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });
            root.addView(alphaBar);

            // Синхронизируем предпросмотр при смене цвета фона
            cpBg.setOnColorChangedListener(new ColorPickerView.OnColorChangedListener() {
                @Override public void onColorChanged(int rgb) {
                    updateAlphaPreview(alphaBar.getProgress(), rgb);
                }
            });

            // ── Кнопка Сохранить ─────────────────────────────────────────────
            Button btn = new Button(this);
            btn.setText("Сохранить");
            btn.setBackgroundColor(0xFF4A90D9);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(16);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            btnLp.topMargin = px(24);
            btn.setLayoutParams(btnLp);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    Calendar sel = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                    sel.set(datePicker.getYear(), datePicker.getMonth(),
                            datePicker.getDayOfMonth(), 12, 0, 0);
                    sel.set(Calendar.MILLISECOND, 0);

                    prefs.edit()
                         .putLong(KEY_ODD,      sel.getTimeInMillis())
                         .putInt(KEY_WEEKDAY,   cpWeekday.getRgb())
                         .putInt(KEY_WEEKEND,   cpWeekend.getRgb())
                         .putInt(KEY_BG_RGB,    cpBg.getRgb())
                         .putInt(KEY_BG_ALPHA,  alphaBar.getProgress())
                         .apply();

                    AppWidgetManager mgr = AppWidgetManager.getInstance(SettingsActivity.this);
                    int[] ids = mgr.getAppWidgetIds(
                            new ComponentName(SettingsActivity.this, YearCalendarWidget.class));
                    for (int wid : ids) update(SettingsActivity.this, mgr, wid);
                    finish();
                }
            });
            root.addView(btn);

            setContentView(scroll);
        }

        // ── Helpers ───────────────────────────────────────────────────────────

        private void updateAlphaPreview(int alpha, int rgb) {
            alphaPreview.setBackgroundColor((alpha << 24) | (rgb & 0xFFFFFF));
        }

        private void addSection(LinearLayout parent, String title) {
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tvLp.topMargin = px(20);

            TextView tv = new TextView(this);
            tv.setText(title);
            tv.setTextSize(15);
            tv.setTextColor(0xFFFFFFFF);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setLayoutParams(tvLp);
            parent.addView(tv);

            View divider = new View(this);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1);
            dlp.topMargin    = px(4);
            dlp.bottomMargin = px(10);
            divider.setLayoutParams(dlp);
            divider.setBackgroundColor(0x55FFFFFF);
            parent.addView(divider);
        }

        private int px(int dp) {
            return Math.round(dp * getResources().getDisplayMetrics().density);
        }
    }

    // ── ColorPickerView ─────────────────────────────────────────────────────

    static class ColorPickerView extends LinearLayout {

        interface OnColorChangedListener { void onColorChanged(int rgb); }

        private int rgb;
        private View    preview;
        private SeekBar rBar, gBar, bBar;
        private OnColorChangedListener listener;

        ColorPickerView(Context ctx, int initialRgb) {
            super(ctx);
            this.rgb = initialRgb & 0xFFFFFF;
            setOrientation(VERTICAL);

            // Полоска предпросмотра
            preview = new View(ctx);
            LayoutParams pvLp = new LayoutParams(LayoutParams.MATCH_PARENT, px(ctx, 36));
            pvLp.bottomMargin = px(ctx, 10);
            preview.setLayoutParams(pvLp);
            preview.setBackgroundColor(0xFF000000 | rgb);
            addView(preview);

            // Инициализируем ползунки из начального цвета
            rBar = makeSeekBar(ctx, Color.red(0xFF000000   | rgb));
            gBar = makeSeekBar(ctx, Color.green(0xFF000000 | rgb));
            bBar = makeSeekBar(ctx, Color.blue(0xFF000000  | rgb));

            addColoredRow(ctx, "R", rBar, 0xFFFF5555);
            addColoredRow(ctx, "G", gBar, 0xFF55CC55);
            addColoredRow(ctx, "B", bBar, 0xFF5599FF);

            SeekBar.OnSeekBarChangeListener watcher = new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int v, boolean u) {
                    rgb = (rBar.getProgress() << 16)
                        | (gBar.getProgress() << 8)
                        |  bBar.getProgress();
                    preview.setBackgroundColor(0xFF000000 | rgb);
                    if (listener != null) listener.onColorChanged(rgb);
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            };
            rBar.setOnSeekBarChangeListener(watcher);
            gBar.setOnSeekBarChangeListener(watcher);
            bBar.setOnSeekBarChangeListener(watcher);
        }

        void setOnColorChangedListener(OnColorChangedListener l) { listener = l; }
        int getRgb() { return rgb; }

        private SeekBar makeSeekBar(Context ctx, int progress) {
            SeekBar sb = new SeekBar(ctx);
            sb.setMax(255);
            sb.setProgress(progress);
            return sb;
        }

        private void addColoredRow(Context ctx, String label, SeekBar bar, int labelColor) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(HORIZONTAL);
            LayoutParams rowLp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = px(ctx, 5);
            row.setLayoutParams(rowLp);

            TextView tv = new TextView(ctx);
            tv.setText(label);
            tv.setTextColor(labelColor);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setTextSize(13);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            tv.setLayoutParams(new LayoutParams(px(ctx, 22), LayoutParams.MATCH_PARENT));
            row.addView(tv);

            bar.setLayoutParams(new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            row.addView(bar);

            addView(row);
        }

        private static int px(Context ctx, int dp) {
            return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
        }
    }
}
