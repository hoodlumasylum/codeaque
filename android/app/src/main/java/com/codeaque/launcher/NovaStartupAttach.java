package com.codeaque.launcher;

import android.content.Intent;
import android.os.Bundle;

/** Named: run the rest of startup after the first frame. d8 8.2 NPEs on Nova anonymous classes. */
public final class NovaStartupAttach implements Runnable {
    final MainActivity activity;
    final Bundle state;
    final Intent launch;

    public NovaStartupAttach(MainActivity activity, Bundle state, Intent launch) {
        this.activity = activity;
        this.state = state;
        this.launch = launch;
    }

    @Override public void run() {
        if (activity != null) activity.finishStartup(state, launch);
    }
}
