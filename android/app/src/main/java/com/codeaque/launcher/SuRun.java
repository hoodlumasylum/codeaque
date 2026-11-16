package com.codeaque.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How to invoke a non-Magisk {@code su} (K56 {@code /system/xbin/su}) and how to read what came back.
 * Some {@code su} binaries treat {@code su -c "dpm set-device-owner ..."} as one executable path
 * ("failed to exec ...: No such file or directory"). Interactive stdin is tried first.
 * Plain Java so the parser and form order are unit-tested on a PC.
 */
public final class SuRun {
    private SuRun() { }

    /** Tried in this order for every su binary. */
    public static final String[] FORM_IDS = {
            "stdin",
            "su -c sh -c",
            "su 0 /system/bin/sh -c",
            "su root /system/bin/sh -c",
            "su -c /system/bin/sh -c",
            "su -c split-args"
    };

    public static boolean usesStdin(String form) { return "stdin".equals(form); }

    /** Plain shell lines for the interactive form: the command itself, then {@code __RC}, then {@code exit}. No {@code sh -c} wrapper. */
    public static String stdinScript(String cmd) {
        if (cmd == null) cmd = "";
        return cmd + " 2>&1; echo __RC=$?\nexit\n";
    }

    /** argv including the su binary. For stdin, just {@code {bin}}. */
    public static String[] argv(String form, String bin, String cmd) {
        if (bin == null) bin = "su";
        if (cmd == null) cmd = "";
        if ("stdin".equals(form)) return new String[] { bin };
        if ("su -c sh -c".equals(form)) return new String[] { bin, "-c", "sh -c " + singleQuote(cmd) };
        if ("su 0 /system/bin/sh -c".equals(form)) return new String[] { bin, "0", "/system/bin/sh", "-c", cmd };
        if ("su root /system/bin/sh -c".equals(form)) return new String[] { bin, "root", "/system/bin/sh", "-c", cmd };
        if ("su -c /system/bin/sh -c".equals(form)) return new String[] { bin, "-c", "/system/bin/sh", "-c", cmd };
        if ("su -c split-args".equals(form)) {
            String[] parts = splitCmd(cmd);
            String[] argv = new String[2 + parts.length];
            argv[0] = bin;
            argv[1] = "-c";
            System.arraycopy(parts, 0, argv, 2, parts.length);
            return argv;
        }
        return new String[] { bin, "-c", cmd };
    }

    public static String singleQuote(String s) {
        if (s == null) s = "";
        return "'" + s.replace("'", "'\\''") + "'";
    }

    public static String[] splitCmd(String cmd) {
        if (cmd == null) return new String[0];
        String t = cmd.trim();
        if (t.isEmpty()) return new String[0];
        return t.split("\\s+");
    }

    /** This attempt did not actually start the program (try the next form). Never treat as the final reason. */
    public static boolean isExecMiss(String out) {
        if (out == null || out.isEmpty()) return false;
        if (isDpmFailure(out)) return false;
        for (String raw : out.replace('\r', '\n').split("\n")) {
            if (isExecMissLine(raw.trim())) return true;
        }
        return false;
    }

    public static boolean isExecMissLine(String l) {
        if (l == null || l.isEmpty()) return false;
        String low = l.toLowerCase(Locale.ROOT);
        if (low.startsWith("__rc=")) return false;
        return low.contains("failed to exec")
                || low.contains("no such file")
                || low.contains("command not found")
                || low.contains("inaccessible or not found")
                || low.contains(": not found");
    }

    /** dpm (or cmd device_policy) actually ran and refused. Stop trying more forms. */
    public static boolean isDpmFailure(String out) {
        if (out == null || out.isEmpty()) return false;
        String low = out.toLowerCase(Locale.ROOT);
        if (low.contains("devicepolicymanager") || low.contains("runsetdeviceowner")) return true;
        if (low.contains("already some accounts") || low.contains("already been provisioned")) return true;
        if (low.contains("multiple users") || low.contains("more than one user")) return true;
        if (low.contains("not allowed to set the device owner") || low.contains("not allowed to set device owner")) return true;
        if (low.contains("cannot be the device owner") || low.contains("already a device owner")) return true;
        if (low.contains("illegalstateexception") && low.contains("device owner")) return true;
        return false;
    }

    static final Pattern RC_MARK = Pattern.compile("(?m)^__RC=(-?\\d+)\\s*$");

    /** Last {@code __RC=N} marker from the stdin script, or null if none. */
    public static Integer parseRcMarker(String out) {
        if (out == null) return null;
        Matcher m = RC_MARK.matcher(out);
        Integer last = null;
        while (m.find()) {
            try { last = Integer.valueOf(m.group(1)); } catch (NumberFormatException ignored) { }
        }
        return last;
    }

    public static int effectiveRc(String out, int waitFor) {
        Integer m = parseRcMarker(out);
        if (m != null) return m;
        if (isExecMiss(out) && waitFor == 0) return 127;
        return waitFor;
    }

    public static boolean isSuccess(String out, int effectiveRc) {
        return effectiveRc == 0 && !isExecMiss(out) && !isDpmFailure(out);
    }

    /** True if this attempt should be skipped in favour of the next form. */
    public static boolean shouldRetry(String out, int effectiveRc) {
        if (isSuccess(out, effectiveRc)) return false;
        if (isDpmFailure(out)) return false;
        return true;
    }

    public static String shortResult(String form, String bin, String out, int rc) {
        String who = form + (bin == null || bin.isEmpty() ? "" : " (" + bin + ")");
        if (isSuccess(out, rc)) return who + ": ok";
        String line = firstUseful(out, true);
        if (line.isEmpty()) line = "exit " + rc;
        if (line.length() > 180) line = line.substring(0, 180);
        return who + ": " + line;
    }

    /** First useful line, optionally skipping exec-miss lines so a later dpm exception wins. */
    public static String firstUseful(String text, boolean skipExecMiss) {
        if (text == null) return "";
        for (String raw : text.replace('\r', '\n').split("\n")) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("__RC=") || DpmError.isStackFrame(l)) continue;
            if (skipExecMiss && isExecMissLine(l)) continue;
            return DpmError.stripExceptionClass(l);
        }
        if (skipExecMiss) return firstUseful(text, false);
        return "";
    }

    public static String dialogMessage(Batch batch) {
        if (batch == null) return "Could not set device owner.";
        if (batch.dpmFailed()) {
            String reason = DpmError.plainReason(batch.dpmOutput());
            String first = firstUseful(batch.dpmOutput(), true);
            StringBuilder msg = new StringBuilder();
            msg.append(reason == null || reason.isEmpty() ? "Could not set device owner." : reason);
            if (first != null && !first.isEmpty() && reason != null && !reason.contains(first) && first.length() <= 240)
                msg.append("\n\n").append(first);
            msg.append("\n\nIf a Google account is on the phone, remove it or factory-reset first, then try again. You can also use a computer with adb (Show ADB steps).");
            return msg.toString();
        }
        StringBuilder msg = new StringBuilder();
        msg.append("The device-owner command could not be started with any su form.");
        String list = batch.formList();
        if (list != null && !list.isEmpty()) msg.append("\n\n").append(list);
        msg.append("\n\nIf a Google account is on the phone, remove it or factory-reset first, then try again. You can also use a computer with adb (Show ADB steps).");
        return msg.toString();
    }

    public static final class Attempt {
        public final String bin, form, output, shortLine;
        public final int rc;
        public final boolean execMiss, dpmFail, success;

        public Attempt(String bin, String form, String output, int waitFor) {
            this.bin = bin;
            this.form = form;
            this.output = output == null ? "" : output;
            this.rc = effectiveRc(this.output, waitFor);
            this.execMiss = isExecMiss(this.output);
            this.dpmFail = isDpmFailure(this.output);
            this.success = isSuccess(this.output, this.rc);
            this.shortLine = shortResult(form, bin, this.output, this.rc);
        }

        public boolean shouldRetry() { return SuRun.shouldRetry(output, rc); }
    }

    public static final class Batch {
        public final List<Attempt> attempts = new ArrayList<>();
        public int rc = -1;
        public Attempt decisive;

        public void add(Attempt a) {
            if (a == null) return;
            attempts.add(a);
            rc = a.rc;
            if (a.success || a.dpmFail) decisive = a;
        }

        public boolean success() { return decisive != null && decisive.success; }
        public boolean dpmFailed() { return decisive != null && decisive.dpmFail; }
        public String dpmOutput() { return decisive != null ? decisive.output : ""; }

        public String formList() {
            StringBuilder b = new StringBuilder();
            for (Attempt a : attempts) {
                if (b.length() > 0) b.append('\n');
                b.append(a.shortLine);
            }
            return b.toString();
        }
    }
}
