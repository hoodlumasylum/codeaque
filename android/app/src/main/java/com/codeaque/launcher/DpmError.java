package com.codeaque.launcher;

/** Turns messy dpm/su output (Java stack traces) into a short reason a parent can read. */
public final class DpmError {
    private DpmError() { }

    /** First useful line, skipping blanks and stack frames (`at com.android...`). */
    public static String firstLine(String text) {
        if (text == null) return "";
        for (String raw : text.replace('\r', '\n').split("\n")) {
            String l = raw.trim();
            if (l.isEmpty() || isStackFrame(l)) continue;
            return stripExceptionClass(l);
        }
        return "";
    }

    /** Plain-English summary for the top of the failure dialog. Never includes a stack trace. */
    public static String plainReason(String text) {
        String first = firstLine(text);
        String low = first.toLowerCase(java.util.Locale.ROOT);
        if (low.contains("account") || low.contains("already some accounts"))
            return "A Google (or other) account is already on the phone, so Android will not make CodeAque the device owner.";
        if (low.contains("provision") || (low.contains("already") && (low.contains("owner") || low.contains("set up") || low.contains("being set"))))
            return "This phone is already set up as a device owner (or already provisioned), so Android will not set it again.";
        if (low.contains("not allowed") || low.contains("cannot be set") || low.contains("not permitted"))
            return "Android would not make CodeAque the device owner.";
        if (low.contains("failed to exec") || low.contains("no such file"))
            return "The device-owner command could not be started.";
        if (!first.isEmpty()) return first;
        return "Could not set device owner.";
    }

    static boolean isStackFrame(String l) {
        return l.startsWith("at ") || l.startsWith("Caused by:") || l.startsWith("...") || l.startsWith("Suppressed:");
    }

    /** "java.lang.IllegalStateException: Not allowed..." -> "Not allowed..." */
    static String stripExceptionClass(String l) {
        if (l == null || l.isEmpty()) return "";
        int colon = l.indexOf(": ");
        if (colon <= 0) return l;
        String head = l.substring(0, colon);
        if (head.startsWith("java.") || head.startsWith("android.") || head.startsWith("javax.") || head.startsWith("com.")
                || head.endsWith("Exception") || head.endsWith("Error"))
            return l.substring(colon + 2).trim();
        return l;
    }
}
