package com.codeaque.launcher;

import java.security.SecureRandom;

/** 0.10.21 Lock mode: the parent PIN (4-6 digits) is kept only as "salt:hash" (PBKDF2, 16-byte random salt), on the
 *  device, never sent anywhere. Plain Java so it is unit-tested on a PC. Also which apps Lock mode hides and blocks. */
public final class LockPin {
    private LockPin() { }
    static final int ITER = 20000;

    public static boolean validPin(String pin) { return pin != null && pin.matches("[0-9]{4,6}"); }

    public static String newHash(String pin) {
        byte[] salt = new byte[16]; new SecureRandom().nextBytes(salt);
        return hex(salt) + ":" + hex(derive(pin, salt));
    }

    public static boolean check(String pin, String stored) {
        if (pin == null || stored == null) return false;
        int i = stored.indexOf(':'); if (i <= 0) return false;
        byte[] want = unhex(stored.substring(i + 1)), got = derive(pin, unhex(stored.substring(0, i)));
        return java.security.MessageDigest.isEqual(want, got);
    }

    static byte[] derive(String pin, byte[] salt) {
        try {
            javax.crypto.SecretKeyFactory f = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return f.generateSecret(new javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, ITER, 256)).getEncoded();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    /** Wait (ms) after n wrong tries in a row: none for the first 4, then 30 s, doubling, max 10 min. */
    public static long backoff(int wrong) { return wrong < 5 ? 0 : Math.min(600_000L, 30_000L << Math.min(5, wrong - 5)); }

    /** Phone, dialer, contacts, messages and Android settings apps: hidden and not opened while Lock mode is on. */
    public static boolean blocked(String pkg) {
        if (pkg == null) return false;
        String p = pkg.toLowerCase(java.util.Locale.ROOT);
        if (p.equals("com.android.settings") || p.startsWith("com.android.settings.") || p.equals("com.android.tv.settings")
                || p.equals("com.android.phone") || p.equals("com.android.server.telecom") || p.equals("com.android.mms")
                || p.equals("com.android.stk")) return true;
        return p.contains("dialer") || p.contains("contacts") || p.contains("messaging") || p.endsWith(".mms") || p.endsWith(".sms")
                || p.endsWith(".settings");
    }

    /** CodeAque actions that need the PIN while Lock mode is on (everything except Wi-Fi, Update and game shortcuts). */
    public static boolean needsPin(String action) {
        if (action == null) return false;
        return !(action.equals("wifi") || action.equals("update") || action.startsWith("goto:") || action.equals("gfn:rivals") || action.equals("calltext") || action.equals("changepin"));
    }

    static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
    static byte[] unhex(String h) { byte[] o = new byte[h.length() / 2]; for (int i = 0; i < o.length; i++) o[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16); return o; }
}
