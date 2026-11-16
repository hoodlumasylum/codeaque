package com.codeaque.launcher;

/** Kept so an older adb command still works:
 *  adb shell dpm set-device-owner com.codeaque.launcher/.CodeAqueAdmin
 *  New setups should use {@link AdminReceiver}. */
public class CodeAqueAdmin extends AdminReceiver { }
