package com.codeaque.launcher;

/** Optional "Only call safe numbers" (Android 10+, the call-redirection role, which a parent grants).
 *  While Lock mode is on, outgoing calls to numbers not in Safe contacts are cancelled, from any dialer.
 *  Android does not send emergency calls through this service, so they are never stopped.
 *  The role is missing on some phones. UNTESTED on a real CodeAque phone. */
public class SafeCallRedirection extends android.telecom.CallRedirectionService {
    @Override public void onPlaceCall(android.net.Uri handle, android.telecom.PhoneAccountHandle acct, boolean interactive) {
        android.content.SharedPreferences p = getSharedPreferences("codeaque", MODE_PRIVATE);
        boolean on = p.getBoolean("safeOnly", false) && p.getBoolean("lockMode", false);
        String num = handle == null ? null : handle.getSchemeSpecificPart();
        if (on && !SafeContacts.allowed(num, SafeContacts.parse(p.getString("safeContacts", "")))) cancelCall();
        else placeCallUnmodified();
    }
}
