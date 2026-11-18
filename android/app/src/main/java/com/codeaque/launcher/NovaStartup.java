package com.codeaque.launcher;

/**
 * Startup rules: the home (and splash) must paint before any Nova / ASR / LLM
 * work, and the UI thread must never wait on an engine load. Plain Java for CatalogTest.
 */
public final class NovaStartup {
    private NovaStartup() {}

    /** Background engine load may run this long; a hang must not block the launcher. */
    public static final long WARMUP_TIMEOUT_MS = 30_000L;

    public static boolean attachNovaAfterFirstFrame() { return true; }

    /** isLoaded / lastError may be read from the UI thread only if they never wait on load(). */
    public static boolean uiMayWaitOnEngineLoad() { return false; }

    public static boolean loadHoldsLockDuringNative() { return false; }
}
