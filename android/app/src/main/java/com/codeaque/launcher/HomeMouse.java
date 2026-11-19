package com.codeaque.launcher;

/**
 * 0.10.78: mouse helpers for the home screen, kept out of HomeView so they can
 * be unit-tested without a device. Units for wheel conversion are tiles
 * (same as HomeScroll). A short click opens a tile; a drag must not.
 */
public final class HomeMouse {
    private HomeMouse() {}

    /** Same slop as HomeView's touch gesture, in design units. */
    public static final float SLOP = 28f;

    /** Pixels per wheel notch (before dividing by the tile step). */
    public static final float WHEEL_PX = 64f;

    // MotionEvent constants, inlined so this class stays plain Java for CatalogTest.
    public static final int ACTION_SCROLL = 8;
    public static final int SOURCE_CLASS_POINTER = 0x00004000;
    public static final int SOURCE_JOYSTICK = 0x01000010;
    public static final int BUTTON_SECONDARY = 0x00000002;
    public static final int BUTTON_STYLUS_SECONDARY = 0x00000040;

    public static boolean isDrag(float dx, float dy, float s) {
        if (s < 0.01f) s = 0.01f;
        return Math.hypot(dx, dy) > SLOP * s;
    }

    /** A short press with no travel and no long-press is a click that may open a tile. */
    public static boolean clickOpens(boolean gestureChose, boolean longPressFired, float dx, float dy, float s) {
        return !gestureChose && !longPressFired && !isDrag(dx, dy, s);
    }

    /**
     * Convert a mouse-wheel tick to tile units. Prefer the horizontal axis;
     * a vertical wheel also moves the row. Positive result = later tiles
     * (same sign as HomeScroll.pointerMove: "finger left, later tiles").
     * Android AXIS_VSCROLL is positive for a wheel-up / scroll-up.
     */
    public static float wheelToTiles(float hScroll, float vScroll, float stepPx, float notchPx) {
        if (stepPx < 1f) stepPx = 1f;
        if (notchPx <= 0f) notchPx = WHEEL_PX;
        float px;
        if (Math.abs(hScroll) >= Math.abs(vScroll) && hScroll != 0f) px = hScroll * notchPx;
        else px = -vScroll * notchPx;
        return px / stepPx;
    }

    public static boolean isRightClick(int buttonState) {
        return (buttonState & (BUTTON_SECONDARY | BUTTON_STYLUS_SECONDARY)) != 0;
    }

    public static boolean isMouseWheel(int action, int source) {
        return action == ACTION_SCROLL && (source & SOURCE_CLASS_POINTER) != 0;
    }

    public static boolean isJoystick(int source) {
        return (source & SOURCE_JOYSTICK) != 0;
    }

    /** Which chrome the pointer is over: "camera", "wifi", "tab", "tile", or "". */
    public static String hoverTarget(boolean onCamera, boolean onWifi, boolean onTab, boolean onTile) {
        if (onCamera) return "camera";
        if (onWifi) return "wifi";
        if (onTab) return "tab";
        if (onTile) return "tile";
        return "";
    }
}
