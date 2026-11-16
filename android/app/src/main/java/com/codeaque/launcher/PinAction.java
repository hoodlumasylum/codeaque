package com.codeaque.launcher;

/** 0.10.37: one rule for every parent PIN prompt. Plain Java so checks can run on a PC.
 *  Only ACCEPTED means the caller may proceed. Empty, invalid and wrong PINs never grant access,
 *  and a Cancel / Back / outside tap is simply "not accepted" (the caller must not run onSuccess). */
public final class PinAction {
    private PinAction() { }

    public static final int ACCEPTED = 0, WRONG = 1, EMPTY = 2, INVALID = 3;

    /** Check a typed PIN against the stored salt:hash. Never returns ACCEPTED for null, empty, or a mismatch. */
    public static int verify(String typed, String storedHash) {
        if (typed == null || typed.isEmpty()) return EMPTY;
        if (!LockPin.validPin(typed)) return INVALID;
        if (storedHash == null || storedHash.isEmpty() || !LockPin.check(typed, storedHash)) return WRONG;
        return ACCEPTED;
    }

    public static boolean granted(int result) { return result == ACCEPTED; }

    /** A new PIN must be 4-6 digits and typed the same twice. A blank or a mismatch is never accepted. */
    public static int verifyNew(String a, String b) {
        if (a == null || a.isEmpty() || b == null || b.isEmpty()) return EMPTY;
        if (!LockPin.validPin(a)) return INVALID;
        if (!a.equals(b)) return WRONG;
        return ACCEPTED;
    }
}
