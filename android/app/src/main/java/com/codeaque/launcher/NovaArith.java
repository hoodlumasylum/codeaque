package com.codeaque.launcher;

/**
 * Tiny spoken-arithmetic helper: "eight times eight", "what's 5 plus 3".
 */
public final class NovaArith {
    private NovaArith() {}

    public static String answer(String q) {
        long[] ev = eval(q);
        if (ev == null) return null;
        long a = ev[0], b = ev[2], op = ev[1], r = ev[3];
        String as = Long.toString(a), bs = Long.toString(b), rs = Long.toString(r);
        if (op == 2 && b == 0) return "I can't divide by zero.";
        String word = op == 1 ? "plus" : op == 2 ? "divided by" : op == 3 ? "minus" : "times";
        return as + " " + word + " " + bs + " is " + rs + ".";
    }

    /** [a, op, b, result] or null. op: 0 times, 1 plus, 2 divide, 3 minus. */
    public static long[] eval(String q) {
        if (q == null) return null;
        String n = NovaAsrText.tidy(q);
        n = n.replace('×', 'x').replace('÷', ' ');
        n = n.replaceAll("(?i)multiplied by", " times ");
        n = n.replaceAll("(?i)divided by", " divided ");
        n = n.replaceAll("(?i)\\bover\\b", " divided ");
        n = n.replace('+', ' ').replace('-', ' ');
        String[] w = NovaIntent.norm(n).split(" ");
        int opAt = -1, op = -1;
        for (int i = 0; i < w.length; i++) {
            int o = opOf(w[i]);
            if (o >= 0) { opAt = i; op = o; break; }
        }
        if (opAt <= 0 || opAt >= w.length - 1) return null;
        Long a = parseNum(w[opAt - 1]);
        Long b = parseNum(w[opAt + 1]);
        if (a == null || b == null) return null;
        long r;
        if (op == 0) r = a * b;
        else if (op == 1) r = a + b;
        else if (op == 2) {
            if (b == 0) return new long[] { a, op, b, 0 };
            if (a % b != 0) return new long[] { a, op, b, a / b }; // integer
            r = a / b;
        } else r = a - b;
        return new long[] { a, op, b, r };
    }

    public static boolean looksLike(String q) { return eval(q) != null; }

    static int opOf(String w) {
        if (w == null) return -1;
        if (w.equals("times") || w.equals("x") || w.equals("multiplied")) return 0;
        if (w.equals("plus") || w.equals("add")) return 1;
        if (w.equals("divided") || w.equals("divide")) return 2;
        if (w.equals("minus") || w.equals("subtract") || w.equals("less")) return 3;
        return -1;
    }

    static Long parseNum(String w) {
        if (w == null || w.isEmpty()) return null;
        try { return Long.parseLong(w); } catch (NumberFormatException ignored) { }
        return wordNum(w);
    }

    static Long wordNum(String w) {
        if (w.equals("zero") || w.equals("nought")) return 0L;
        if (w.equals("one")) return 1L;
        if (w.equals("two")) return 2L;
        if (w.equals("three")) return 3L;
        if (w.equals("four")) return 4L;
        if (w.equals("five")) return 5L;
        if (w.equals("six")) return 6L;
        if (w.equals("seven")) return 7L;
        if (w.equals("eight")) return 8L;
        if (w.equals("nine")) return 9L;
        if (w.equals("ten")) return 10L;
        if (w.equals("eleven")) return 11L;
        if (w.equals("twelve")) return 12L;
        if (w.equals("thirteen")) return 13L;
        if (w.equals("fourteen")) return 14L;
        if (w.equals("fifteen")) return 15L;
        if (w.equals("sixteen")) return 16L;
        if (w.equals("seventeen")) return 17L;
        if (w.equals("eighteen")) return 18L;
        if (w.equals("nineteen")) return 19L;
        if (w.equals("twenty")) return 20L;
        if (w.equals("thirty")) return 30L;
        if (w.equals("forty")) return 40L;
        if (w.equals("fifty")) return 50L;
        if (w.equals("sixty")) return 60L;
        if (w.equals("seventy")) return 70L;
        if (w.equals("eighty")) return 80L;
        if (w.equals("ninety")) return 90L;
        if (w.equals("hundred")) return 100L;
        return null;
    }
}
