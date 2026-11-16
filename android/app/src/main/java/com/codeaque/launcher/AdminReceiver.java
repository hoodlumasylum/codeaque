package com.codeaque.launcher;

/** 0.10.37: device admin receiver. On a freshly reset phone with no Google (or other) account:
 *  adb shell dpm set-device-owner com.codeaque.launcher/.AdminReceiver
 *  Then Kids mode can use a real kiosk lock (no Recents swipe). Remove it later from Settings
 *  (parent PIN) so the phone is not stuck. */
public class AdminReceiver extends android.app.admin.DeviceAdminReceiver { }
