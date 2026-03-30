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
import android.widget.TextView;
import java.util.Calendar;

public class YearCalendarWidget extends AppWidgetProvider {

    static final String[] MON = {
        "January","February","March","April","May","June",
        "July","August","September","October","November","December"
    };
    static final String PREFS = "ycal_prefs";
    static final String KEY_ODD = "odd_week_start_ms";

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
        int yr = today.get(Calendar.YEAR);
        int todayM = today.get(Calendar.MONTH);
        int todayD = today.get(Calendar.DAY_OF_MONTH);

        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long oddRefMs = prefs.getLong(KEY_ODD, -1);

        float padX = W * 0.01f, padY = H * 0.008f;
        float cellW = (W - padX * 2) / 3f;
        float cellH = (H - padY - H * 0.01f) / 4f;

        for (int m = 0; m < 12; m++) {
            float x0 = padX + (m % 3) * cellW;
            float y0 = padY + (m / 3) * cellH;
            drawMonth(c, x0, y0, cellW, cellH, yr, m, todayM, todayD, oddRefMs);
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

    static boolean isOddWeek(long oddRefMs, int yr, int m, int col, int firstDow) {
        if (oddRefMs < 0) return false;

        java.util.TimeZone utc = java.util.TimeZone.getTimeZone("UTC");

        Calendar ref = Calendar.getInstance(utc);
        ref.setTimeInMillis(oddRefMs);
        ref.set(Calendar.HOUR_OF_DAY, 12);
        ref.set(Calendar.MINUTE, 0);
        ref.set(Calendar.SECOND, 0);
        ref.set(Calendar.MILLISECOND, 0);
        int refDow = (ref.get(Calendar.DAY_OF_WEEK) + 5) % 7;
        ref.add(Calendar.DAY_OF_YEAR, -refDow);

        Calendar colMon = Calendar.getInstance(utc);
        colMon.set(yr, m, 1, 12, 0, 0);
        colMon.set(Calendar.MILLISECOND, 0);
        colMon.add(Calendar.DAY_OF_YEAR, -firstDow + col * 7);

        long diffMs = colMon.getTimeInMillis() - ref.getTimeInMillis();
        long diffWeeks = diffMs / (7L * 24 * 3600 * 1000);
        return ((diffWeeks % 2) + 2) % 2 == 0;
    }

    static void drawMonth(Canvas c, float x0, float y0, float cW, float cH,
                          int yr, int m, int todayM, int todayD, long oddRefMs) {
        float pH = cW * 0.015f, pV = cH * 0.01f;
        float iX = x0+pH, iY = y0+pV, iW = cW-pH*2, iH = cH-pV*2;

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
        int firstDow = (cal.get(Calendar.DAY_OF_WEEK)+5)%7;
        int numCols = (firstDow + totalDays + 6) / 7;

        float daysTop = iY + fs * 1.2f;
        float daysH = iH - fs * 1.2f;
        float colW = iW / numCols;
        float rowH = daysH / 7f;
        float dayFs = Math.min(rowH * 0.85f, colW * 0.78f);

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
            float bx = iX + col * colW + 1;
            float by = daysTop + firstRow * rowH;
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
            int idx = firstDow + day - 1;
            int col = idx / 7;
            int row = idx % 7;
            float cx = iX + col*colW + colW/2f;
            float cy = daysTop + row*rowH + dayFs*0.88f;
            boolean isToday = m==todayM && day==todayD;
            boolean isWe = row >= 5;
            if (isToday) {
                float r = Math.min(colW, rowH) * 0.46f;
                c.drawCircle(cx, cy - dayFs*0.3f, r, todayBg);
                dp.setColor(0xFF111111);
            } else {
                dp.setColor(isWe ? 0xFFFF4444 : 0xFFFFFFFF);
            }
            c.drawText(String.valueOf(day), cx, cy, dp);
        }
    }

    public static class SettingsActivity extends Activity {

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);

            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(48, 64, 48, 48);
            root.setBackgroundColor(0xFF1A1A2E);

            TextView title = new TextView(this);
            title.setText("Нечётная неделя");
            title.setTextSize(22);
            title.setTextColor(0xFFFFFFFF);
            title.setTypeface(null, Typeface.BOLD);
            title.setGravity(Gravity.CENTER);
            root.addView(title);

            TextView sub = new TextView(this);
            sub.setText("Выбери любой день нечётной недели:");
            sub.setTextSize(14);
            sub.setTextColor(0xFFAAAAAA);
            sub.setPadding(0, 24, 0, 16);
            sub.setGravity(Gravity.CENTER);
            root.addView(sub);

            DatePicker dp = new DatePicker(this);
            dp.setCalendarViewShown(false);
            long saved = prefs.getLong(KEY_ODD, -1);
            if (saved > 0) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(saved);
                dp.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
            }
            root.addView(dp);

            Button btn = new Button(this);
            btn.setText("Сохранить");
            btn.setBackgroundColor(0xFF4A90D9);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(16);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = 24;
            btn.setLayoutParams(lp);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Calendar sel = Calendar.getInstance();
                    sel.set(dp.getYear(), dp.getMonth(), dp.getDayOfMonth(), 0, 0, 0);
                    sel.set(Calendar.MILLISECOND, 0);
                    prefs.edit().putLong(KEY_ODD, sel.getTimeInMillis()).apply();

                    AppWidgetManager mgr = AppWidgetManager.getInstance(SettingsActivity.this);
                    int[] ids = mgr.getAppWidgetIds(
                            new ComponentName(SettingsActivity.this, YearCalendarWidget.class));
                    for (int id : ids) update(SettingsActivity.this, mgr, id);
                    finish();
                }
            });
            root.addView(btn);
            setContentView(root);
        }
    }
}
