package com.codeaque.launcher;

import android.content.pm.PackageManager;
import android.os.Build;
import android.view.WindowManager;
import android.widget.Toast;

/** Named Host for the overlay bubble. d8 8.2 NPEs on an anonymous Host in the service. */
public final class NovaOverlayHost implements NovaView.Host {
    final NovaOverlayService svc;
    final WindowManager.LayoutParams lp;

    public NovaOverlayHost(NovaOverlayService svc, WindowManager.LayoutParams lp) {
        this.svc = svc;
        this.lp = lp;
    }

    @Override public void onNovaExpandChanged(boolean expanded) {
        // Take focus only while the help panel is open so a game keeps its input otherwise.
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | (expanded ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        try {
            if (svc.added && svc.wm != null && svc.bubble != null) svc.wm.updateViewLayout(svc.bubble, lp);
        } catch (Exception ignored) { }
    }

    @Override public boolean novaMicAllowed() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return svc.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void novaRequestMic() {
        Toast.makeText(svc, "To use the mic, open CodeAque and allow it when Chat asks.", Toast.LENGTH_LONG).show();
        if (svc.bubble != null) svc.bubble.onMicPermission(false);
    }
}
