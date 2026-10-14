package com.alya.assistant;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class AlyaOverlayService extends Service {
    private WindowManager windowManager;
    private LinearLayout orb;
    private boolean showingScreenText;
    private WindowManager.LayoutParams overlayParams;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        windowManager = getSystemService(WindowManager.class);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(16, 12, 16, 12);
        panel.setBackgroundColor(0xFF315644);
        TextView label = new TextView(this);
        label.setText("Alya · tap to inspect screen · tap again to close");
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(14);
        label.setPadding(4, 4, 4, 8);
        label.setOnClickListener(view -> {
            if (showingScreenText) {
                stopSelf();
                return;
            }
            AlyaAccessibilityService accessibility = AlyaAccessibilityService.getInstance();
            if (accessibility == null) {
                label.setText("Enable Alya screen actions in app settings first.");
                return;
            }
            String screenText = accessibility.readVisibleText();
            TextView preview = (TextView) panel.getChildAt(1);
            preview.setText(screenText.length() > 500 ? screenText.substring(0, 500) + "…" : screenText);
            preview.setVisibility(android.view.View.VISIBLE);
            panel.getChildAt(2).setVisibility(android.view.View.VISIBLE);
            panel.getChildAt(3).setVisibility(android.view.View.VISIBLE);
            showingScreenText = true;
            overlayParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(panel, overlayParams);
        });
        panel.addView(label);
        TextView preview = new TextView(this);
        preview.setTextColor(0xFFFFFFFF);
        preview.setTextSize(12);
        preview.setMaxLines(8);
        preview.setVisibility(android.view.View.GONE);
        panel.addView(preview);
        EditText targetText = new EditText(this);
        targetText.setSingleLine(true);
        targetText.setHint("Exact visible control text");
        targetText.setVisibility(android.view.View.GONE);
        panel.addView(targetText);
        Button click = new Button(this);
        click.setText("Click matching control");
        click.setAllCaps(false);
        click.setVisibility(android.view.View.GONE);
        click.setOnClickListener(view -> {
            AlyaAccessibilityService accessibility = AlyaAccessibilityService.getInstance();
            String requested = targetText.getText().toString().trim();
            if (accessibility == null || requested.isEmpty()) {
                preview.setText("Enter the exact visible control text.");
                return;
            }
            boolean clicked = accessibility.clickVisibleText(requested);
            preview.setText(clicked ? "Matching control clicked." : "No matching clickable control was found.");
        });
        panel.addView(click);
        orb = panel;
        int size = dp(56);
        overlayParams = new WindowManager.LayoutParams(
                dp(300),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.END;
        overlayParams.y = size * 2;
        windowManager.addView(orb, overlayParams);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroy() {
        if (orb != null && windowManager != null) windowManager.removeView(orb);
        orb = null;
        super.onDestroy();
    }
}