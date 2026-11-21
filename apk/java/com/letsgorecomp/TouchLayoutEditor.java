package com.letsgorecomp;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

/**
 * Lightweight editor for the five gameplay-control groups in GamepadOverlay.
 * It only changes View transforms/visibility; all existing input listeners and
 * native input paths remain untouched.
 */
final class TouchLayoutEditor {
    private final GamepadOverlay overlay;
    private final Activity activity;
    private final View[] controls;
    private final SharedPreferences prefs;
    private final LinearLayout toolbar;
    private final Button editButton;
    private final Button doneButton;
    private final Button minusButton;
    private final Button plusButton;
    private final Button hideButton;
    private final Button restoreButton;

    private boolean editing;
    private boolean toolbarGesture;
    private int selected = -1;
    private float downX;
    private float downY;
    private float startTranslationX;
    private float startTranslationY;
    private float pinchStartDistance;
    private float pinchStartScale = 1.0f;

    TouchLayoutEditor(GamepadOverlay overlay, Activity activity, int controlCount) {
        this.overlay = overlay;
        this.activity = activity;
        this.prefs = activity.getSharedPreferences("touch_layout_editor", Activity.MODE_PRIVATE);

        int count = Math.min(controlCount, overlay.getChildCount());
        controls = new View[count];
        for (int i = 0; i < count; i++) controls[i] = overlay.getChildAt(i);

        toolbar = new LinearLayout(activity);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(3), dp(3), dp(3), dp(3));
        toolbar.setBackgroundColor(Color.argb(210, 20, 20, 24));

        editButton = makeButton("✎");
        doneButton = makeButton("OK");
        minusButton = makeButton("−");
        plusButton = makeButton("+");
        hideButton = makeButton("Esconder");
        restoreButton = makeButton("Restaurar");

        addToolbarButton(editButton);
        addToolbarButton(doneButton);
        addToolbarButton(minusButton);
        addToolbarButton(plusButton);
        addToolbarButton(hideButton);
        addToolbarButton(restoreButton);

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.TOP | Gravity.LEFT;
        barLp.leftMargin = dp(8);
        barLp.topMargin = dp(54);
        overlay.addView(toolbar, barLp);

        editButton.setOnClickListener(v -> setEditing(true));
        doneButton.setOnClickListener(v -> setEditing(false));
        minusButton.setOnClickListener(v -> adjustScale(-0.1f));
        plusButton.setOnClickListener(v -> adjustScale(0.1f));
        hideButton.setOnClickListener(v -> hideSelected());
        restoreButton.setOnClickListener(v -> restoreDefaults());

        setEditing(false);
        loadLayout();
    }

    boolean onTouchEvent(MotionEvent event) {
        if (!editing) return false;

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            toolbarGesture = isInsideToolbar(event.getX(), event.getY());
            if (toolbarGesture) return false;
            selected = findControl(event.getX(), event.getY());
            downX = event.getX();
            downY = event.getY();
            if (selected >= 0) {
                View v = controls[selected];
                startTranslationX = v.getTranslationX();
                startTranslationY = v.getTranslationY();
            }
            updateSelectionButtons();
            return true;
        }

        if (toolbarGesture) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                toolbarGesture = false;
            }
            return false;
        }

        if (action == MotionEvent.ACTION_POINTER_DOWN && selected >= 0 && event.getPointerCount() >= 2) {
            pinchStartDistance = pointerDistance(event);
            pinchStartScale = controls[selected].getScaleX();
            return true;
        }

        if (action == MotionEvent.ACTION_MOVE && selected >= 0) {
            View v = controls[selected];
            if (event.getPointerCount() >= 2 && pinchStartDistance > 0.0f) {
                float ratio = pointerDistance(event) / pinchStartDistance;
                float scale = clamp(pinchStartScale * ratio, 0.5f, 2.5f);
                v.setScaleX(scale);
                v.setScaleY(scale);
            } else if (event.getPointerCount() == 1) {
                v.setTranslationX(startTranslationX + event.getX() - downX);
                v.setTranslationY(startTranslationY + event.getY() - downY);
            }
            return true;
        }

        if (action == MotionEvent.ACTION_POINTER_UP) {
            pinchStartDistance = 0.0f;
            if (event.getPointerCount() > 1) {
                downX = event.getX(0);
                downY = event.getY(0);
                startTranslationX = controls[selected].getTranslationX();
                startTranslationY = controls[selected].getTranslationY();
            }
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            saveLayout();
            pinchStartDistance = 0.0f;
            return true;
        }
        return true;
    }

    private void setEditing(boolean value) {
        editing = value;
        editButton.setVisibility(value ? View.GONE : View.VISIBLE);
        doneButton.setVisibility(value ? View.VISIBLE : View.GONE);
        minusButton.setVisibility(value ? View.VISIBLE : View.GONE);
        plusButton.setVisibility(value ? View.VISIBLE : View.GONE);
        hideButton.setVisibility(value ? View.VISIBLE : View.GONE);
        restoreButton.setVisibility(value ? View.VISIBLE : View.GONE);
        selected = -1;
        updateSelectionButtons();
        if (value) {
            Toast.makeText(activity, "Geser kontrol untuk memindahkan, cubit untuk mengubah ukuran", Toast.LENGTH_LONG).show();
        }
    }

    private void updateSelectionButtons() {
        boolean hasSelection = selected >= 0;
        minusButton.setEnabled(hasSelection);
        plusButton.setEnabled(hasSelection);
        hideButton.setEnabled(hasSelection && selected != 2 && selected != 3);
    }

    private void adjustScale(float delta) {
        if (selected < 0) return;
        View v = controls[selected];
        float scale = clamp(v.getScaleX() + delta, 0.5f, 2.5f);
        v.setScaleX(scale);
        v.setScaleY(scale);
        saveLayout();
    }

    private void hideSelected() {
        if (selected < 0 || selected == 2 || selected == 3) return;
        controls[selected].setVisibility(View.GONE);
        saveLayout();
        selected = -1;
        updateSelectionButtons();
    }

    private void restoreDefaults() {
        for (View v : controls) {
            v.setTranslationX(0.0f);
            v.setTranslationY(0.0f);
            v.setScaleX(1.0f);
            v.setScaleY(1.0f);
            v.setVisibility(View.VISIBLE);
        }
        prefs.edit().clear().apply();
        selected = -1;
        updateSelectionButtons();
    }

    private void loadLayout() {
        for (int i = 0; i < controls.length; i++) {
            View v = controls[i];
            v.setTranslationX(prefs.getFloat("x_" + i, 0.0f));
            v.setTranslationY(prefs.getFloat("y_" + i, 0.0f));
            float scale = clamp(prefs.getFloat("s_" + i, 1.0f), 0.5f, 2.5f);
            v.setScaleX(scale);
            v.setScaleY(scale);
            boolean canHide = i != 2 && i != 3;
            if (canHide && prefs.getBoolean("hidden_" + i, false)) v.setVisibility(View.GONE);
        }
    }

    private void saveLayout() {
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < controls.length; i++) {
            View v = controls[i];
            e.putFloat("x_" + i, v.getTranslationX());
            e.putFloat("y_" + i, v.getTranslationY());
            e.putFloat("s_" + i, v.getScaleX());
            e.putBoolean("hidden_" + i, v.getVisibility() == View.GONE);
        }
        e.apply();
    }

    private int findControl(float x, float y) {
        for (int i = controls.length - 1; i >= 0; i--) {
            View v = controls[i];
            if (v.getVisibility() != View.VISIBLE) continue;
            float left = v.getLeft() + v.getTranslationX();
            float top = v.getTop() + v.getTranslationY();
            float width = v.getWidth() * v.getScaleX();
            float height = v.getHeight() * v.getScaleY();
            if (x >= left && x <= left + width && y >= top && y <= top + height) return i;
        }
        return -1;
    }

    private boolean isInsideToolbar(float x, float y) {
        float left = toolbar.getLeft();
        float top = toolbar.getTop();
        return x >= left && x <= left + toolbar.getWidth()
            && y >= top && y <= top + toolbar.getHeight();
    }

    private float pointerDistance(MotionEvent e) {
        if (e.getPointerCount() < 2) return 0.0f;
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.hypot(dx, dy);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private Button makeButton(String label) {
        Button b = new Button(activity);
        b.setText(label);
        b.setTextSize(11);
        b.setMinWidth(dp(42));
        b.setMinHeight(dp(36));
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private void addToolbarButton(Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
        lp.setMargins(dp(2), 0, dp(2), 0);
        toolbar.addView(b, lp);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
