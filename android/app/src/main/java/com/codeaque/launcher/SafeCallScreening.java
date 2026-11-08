package com.codeaque.launcher;

/** 0.10.22: optional "Block unknown callers" (Android 10+, needs the call screening role, which the parent grants).
 *  Incoming calls from numbers not in Safe contacts (and hidden numbers) are rejected while the setting is on. */
public class SafeCallScreening extends android.telecom.CallScreeningService {
    @Override public void onScreenCall(android.telecom.Call.Details d) {
        android.content.SharedPreferences p = getSharedPreferences("codeaque", MODE_PRIVATE);
        boolean incoming = android.os.Build.VERSION.SDK_INT < 29 || d.getCallDirection() == android.telecom.Call.Details.DIRECTION_INCOMING;
        String num = d.getHandle() == null ? null : d.getHandle().getSchemeSpecificPart();
        boolean block = incoming && p.getBoolean("blockUnknown", false) && !SafeContacts.allowed(num, SafeContacts.parse(p.getString("safeContacts", "")));
        CallResponse.Builder r = new CallResponse.Builder();
        if (block) r.setDisallowCall(true).setRejectCall(true).setSkipNotification(true).setSkipCallLog(false);
        respondToCall(d, r.build());
    }
}
