package com.codeaque.launcher;

/** Colour themes. Plain Java so it can be unit-tested. "neon" is the default (charcoal, white logo, cyan accent);
 *  "powerup" (orange & blue energy) and "catch" (red, white & yellow) are original optional themes. */
public final class Palette {
    public final String id, name;
    public final int gold, goldLight, goldDark, accent, accentLight, accentMid, glowRgb, bgCenter,
            tileTop, tileSelTop, tileBottom, border, borderDark, text, dim, sub, prompt, webBadge, getBadge;
    public final int[] waves;
    public final boolean aura;
    public boolean burst;          // Catch: cheerful sunburst + confetti, spinning star on the selected tile
    public String selectSound;
    public float[] tint;           // 4x5 colour matrix recolouring the emblem (null = the original art)     // sound for launch / select, null = the normal one

    Palette(String id, String name, int gold, int goldLight, int goldDark, int accent, int accentLight, int accentMid,
            int glowRgb, int bgCenter, int tileTop, int tileSelTop, int tileBottom, int border, int borderDark,
            int text, int dim, int sub, int prompt, int webBadge, int getBadge, int[] waves, boolean aura) {
        this.id = id; this.name = name; this.gold = gold; this.goldLight = goldLight; this.goldDark = goldDark;
        this.accent = accent; this.accentLight = accentLight; this.accentMid = accentMid; this.glowRgb = glowRgb;
        this.bgCenter = bgCenter; this.tileTop = tileTop; this.tileSelTop = tileSelTop; this.tileBottom = tileBottom;
        this.border = border; this.borderDark = borderDark; this.text = text; this.dim = dim; this.sub = sub;
        this.prompt = prompt; this.webBadge = webBadge; this.getBadge = getBadge; this.waves = waves; this.aura = aura;
    }

    public static final Palette NEON = new Palette("neon", "Neon",
            0xFF3FD4FF, 0xFFD8F6FF, 0xFF1C8FC0, 0xFF7FE4FF, 0xFFB8F0FF, 0xFF4FB8E0,
            0x2FC8FF, 0xFF141A22, 0xF0141920, 0xFF1A3444, 0xF0080A0E, 0xFF2C5A70, 0xFF1C3240,
            0xFFF2F6FA, 0xFF8E9AA8, 0xFF9AAAB8, 0xFF0C1016, 0xCC14607A, 0xCC2C4A90,
            new int[]{0x553FD4FF, 0x332F8CFF, 0x335CE8FF, 0x66B8F0FF, 0x223FD4FF}, false);

    public static final Palette POWERUP = new Palette("powerup", "Power Up",
            0xFFFF9A1F, 0xFFFFE0A0, 0xFFC2500A, 0xFF5CC8FF, 0xFF9FE0FF, 0xFF3AA0F0,
            0x2E9BFF, 0xFF0A1C3A, 0xF00A1630, 0xFF14305C, 0xF003070F, 0xFF2F6FB8, 0xFF1A3D6E,
            0xFFF2F6FF, 0xFF93A6C4, 0xFFA8B8D4, 0xFF060E1E, 0xCC1450A0, 0xCCA04A0A,
            new int[]{0x66FF8A1A, 0x553AA0FF, 0x44FFC040, 0x8860C8FF, 0x44FF6A00}, true);
    static { POWERUP.selectSound = "powerup"; }

    public static final Palette CATCH = new Palette("catch", "Catch",
            0xFFFFD23F, 0xFFFFF4B8, 0xFFE0A000, 0xFFFFFFFF, 0xFFFFF4B8, 0xFFFF5A5A,
            0xFF3B3B, 0xFF5A0C10, 0xF03A0A0E, 0xFF7A1018, 0xF0120304, 0xFFE8E8E8, 0xFFB03038,
            0xFFFFFFFF, 0xFFE8C8C8, 0xFFFFE08A, 0xFF2A0608, 0xCCB01E28, 0xCCC08A00,
            new int[]{0x55FFFFFF, 0x55FFD23F, 0x44FF5050, 0x66FFFFFF, 0x33FFD23F}, false);
    static { CATCH.burst = true; CATCH.selectSound = "chime"; }

    public static final Palette[] ALL = {NEON, POWERUP, CATCH};

    public static Palette byId(String id) {
        for (Palette p : ALL) if (p.id.equals(id)) return p;
        return NEON;
    }

    public static String next(String id) {
        for (int i = 0; i < ALL.length; i++) if (ALL[i].id.equals(id)) return ALL[(i + 1) % ALL.length].id;
        return NEON.id;
    }
}
