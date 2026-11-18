package com.codeaque.launcher;

import android.app.Activity;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * 0.10.55 / 0.10.56: hide the system IME from a field, an activity, or a tap
 * outside the focused text field and any custom on-screen keyboard.
 * Named helper — no anonymous listeners.
 */
public final class ImeHide {
    private ImeHide() {}

    public static void hide(View v) {
        if (v == null) return;
        try { v.clearFocus(); } catch (Exception ignored) { }
        Context ctx = v.getContext();
        if (ctx == null) return;
        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return;
        try { imm.hideSoftInputFromWindow(v.getWindowToken(), 0); } catch (Exception ignored) { }
    }

    public static void hide(Activity a) {
        if (a == null) return;
        View focus = a.getCurrentFocus();
        View decor = a.getWindow() != null ? a.getWindow().getDecorView() : null;
        if (focus != null) hide(focus);
        if (decor != null) hide(decor);
        else if (focus == null) { /* nothing */ }
    }

    /** True if the motion event's screen coordinates land inside the view. */
    public static boolean eventInside(View v, MotionEvent ev) {
        if (v == null || ev == null) return false;
        int[] loc = new int[2];
        try { v.getLocationOnScreen(loc); } catch (Exception e) { return false; }
        float x = ev.getRawX(), y = ev.getRawY();
        return x >= loc[0] && x < loc[0] + v.getWidth() && y >= loc[1] && y < loc[1] + v.getHeight();
    }

    public static boolean focusedEdit(View v) {
        return v instanceof EditText && v.hasFocus();
    }

    /**
     * Keep the keyboard only when the down-event is on the focused field or
     * on the custom CodeAque keyboard. Anywhere else should dismiss it.
     */
    public static boolean shouldKeepKeyboard(boolean onFocusedField, boolean onCustomKeyboard) {
        return KidsSearchLayout.shouldKeepKeyboard(onFocusedField, onCustomKeyboard);
    }
}
