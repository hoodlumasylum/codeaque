package com.codeaque.launcher;

/** Called on the UI thread when a background built-in listening load finishes. */
public interface NovaAsrLoadCb {
    void onAsrLoad(boolean ok, String error);
}
