package com.codeaque.launcher;

/** Optional "Block unknown callers" (Android 10+). This is NOT the default phone app.
 *
 *  Honest limit: without some role from Android, CodeAque cannot stop the real phone app ringing for an unknown
 *  number, and it cannot filter incoming texts. Becoming the default dialer would be a heavier takeover and is
 *  left as future work (it can break ordinary calls). What this build can do, if a parent grants the separate
 *  call-screening role, is ask Android to reject incoming calls whose number is not a safe contact. The role is
 *  missing on some phones. Emergency calls are not in the safe list; this service only runs when the role is held.
 *  UNTESTED on a real CodeAque phone. */
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
