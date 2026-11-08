package com.codeaque.launcher;

/** 0.10.21: device admin receiver, only active if a parent runs
 *  adb shell dpm set-device-owner com.codeaque.launcher/.CodeAqueAdmin  (optional full kiosk for Lock mode). */
public class CodeAqueAdmin extends android.app.admin.DeviceAdminReceiver { }
