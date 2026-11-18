package com.codeaque.launcher;

/** Ends a hung native load wait and falls back. Named so d8 8.2 does not NPE. */
public final class NovaAsrLoadTimeout implements Runnable {
    @Override public void run() { NovaAsr.onLoadTimeout(); }
}
