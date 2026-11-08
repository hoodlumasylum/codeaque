package com.codeaque.launcher;

/** 0.10.22: optional "Only call safe numbers" (Android 10+, needs the call redirection role, which the parent grants).
 *  While Lock mode is on, outgoing calls to numbers not in Safe contacts are cancelled, from any dialer.
 *  Android does not send emergency calls through this service, so they are never stopped. */
public class SafeCallRedirection extends android.telecom.CallRedirectionService {
    @Override public void onPlaceCall(android.net.Uri handle, android.telecom.PhoneAccountHandle acct, boolean interactive) {
        android.content.SharedPreferences p = getSharedPreferences("codeaque", MODE_PRIVATE);
        boolean on = p.getBoolean("safeOnly", false) && p.getBoolean("lockMode", false);
        String num = handle == null ? null : handle.getSchemeSpecificPart();
        if (on && !SafeContacts.allowed(num, SafeContacts.parse(p.getString("safeContacts", "")))) cancelCall();
        else placeCallUnmodified();
    }
}
