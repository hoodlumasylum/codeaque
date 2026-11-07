package com.codeaque.launcher;

/** 0.10.1 Settings > Force landscape for games (root only, off by default). Plain Java for the build-time checks.
 *  GammaOS 1.5 (RG353M) has no built-in forced-landscape switch; this locks the rotation with root before a game opens.
 *  UNTESTED ON A REAL DEVICE: which user_rotation value is right for a given panel is a guess, so the user picks it. */
public final class Rotation {
    private Rotation() { }

    /** Off, then the three landscape-ish fixed rotations (Surface.ROTATION_0 / 90 / 270). */
    public static final String[] MODES = {"off", "0", "1", "3"};

    public static String next(String m) {
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(m)) return MODES[(i + 1) % MODES.length];
        return MODES[1];
    }

    public static String label(String m) {
        switch (m == null ? "off" : m) {
            case "0": return "Force landscape: on (rotation 0)";
            case "1": return "Force landscape: on (rotation 90)";
            case "3": return "Force landscape: on (rotation 270)";
            default: return "Force landscape: off";
        }
    }

    /** Only the fixed values above ever reach su. */
    public static String cmd(String m) {
        if (!"0".equals(m) && !"1".equals(m) && !"3".equals(m)) return null;
        // Roblox asks for its own orientation, so Auto-rotate alone doesn't help. On Android 10+
        // "wm fixed-to-user-rotation enabled" makes every app follow the locked user rotation. The settings are kept as a fallback.
        return "settings put system accelerometer_rotation 0; settings put system user_rotation " + m
                + "; wm fixed-to-user-rotation enabled; wm user-rotation lock " + m;
    }

    /** Suggest (never switch on) for RK3566 / Anbernic handhelds such as the RG353M. */
    public static boolean suggest(String manufacturer, String model, String hardware, String board) {
        String s = (manufacturer + " " + model + " " + hardware + " " + board).toLowerCase(java.util.Locale.ROOT);
        return s.contains("rk3566") || s.contains("anbernic") || s.contains("rg353");
    }
}
