package com.codeaque.launcher;

import java.util.ArrayList;
import java.util.List;

/** 0.10.22 Safe contacts: the names and numbers a parent allows. Stored only on the device (CodeAque's own settings),
 *  one "name<TAB>number" per line. Plain Java so it is unit-tested on a PC. */
public final class SafeContacts {
    private SafeContacts() { }

    public static final class Contact {
        public final String name, number;
        public Contact(String name, String number) { this.name = name; this.number = number; }
    }

    /** Keeps a leading + and the digits only; null if fewer than 3 digits. */
    public static String clean(String number) {
        if (number == null) return null;
        String t = number.trim(), d = t.replaceAll("[^0-9]", "");
        if (d.length() < 3 || d.length() > 15) return null;
        return (t.startsWith("+") ? "+" : "") + d;
    }

    /** Same number? Compares the last 9 digits (so 07700 900123 matches +44 7700 900123); exact for short numbers. */
    public static boolean same(String a, String b) {
        String x = clean(a), y = clean(b);
        if (x == null || y == null) return false;
        x = x.replace("+", ""); y = y.replace("+", "");
        if (x.length() < 9 || y.length() < 9) return x.equals(y);
        return x.substring(x.length() - 9).equals(y.substring(y.length() - 9));
    }

    public static boolean allowed(String number, List<Contact> list) {
        for (Contact c : list) if (same(number, c.number)) return true;
        return false;
    }

    public static List<Contact> parse(String s) {
        List<Contact> out = new ArrayList<>();
        if (s == null) return out;
        for (String line : s.split("\n")) {
            int i = line.indexOf('\t'); if (i <= 0) continue;
            String n = line.substring(0, i).trim(), num = clean(line.substring(i + 1));
            if (!n.isEmpty() && num != null) out.add(new Contact(n, num));
        }
        return out;
    }

    public static String write(List<Contact> list) {
        StringBuilder b = new StringBuilder();
        for (Contact c : list) b.append(c.name.replace('\t', ' ').replace('\n', ' ').trim()).append('\t').append(c.number).append('\n');
        return b.toString();
    }

    /** Adds (or renames) a contact; false if the number is not valid. */
    public static boolean add(List<Contact> list, String name, String number) {
        String num = clean(number), n = name == null ? "" : name.replace('\t', ' ').replace('\n', ' ').trim();
        if (num == null) return false;
        if (n.isEmpty()) n = num;
        for (int i = 0; i < list.size(); i++) if (same(list.get(i).number, num)) { list.set(i, new Contact(n, num)); return true; }
        list.add(new Contact(n, num));
        return true;
    }

    /** 0.10.29: change the name or number of the contact at index. False if the index or number is no good,
     *  or another contact already uses that number. */
    public static boolean update(List<Contact> list, int index, String name, String number) {
        if (list == null || index < 0 || index >= list.size()) return false;
        String num = clean(number), n = name == null ? "" : name.replace('\t', ' ').replace('\n', ' ').trim();
        if (num == null) return false;
        if (n.isEmpty()) n = num;
        for (int i = 0; i < list.size(); i++) if (i != index && same(list.get(i).number, num)) return false;
        list.set(index, new Contact(n, num));
        return true;
    }
}
