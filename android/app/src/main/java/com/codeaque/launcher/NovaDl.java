package com.codeaque.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Plain HTTP file download with optional resume. Progress is reported in percent.
 * The callback is invoked on the worker thread; the caller posts to the UI.
 */
public final class NovaDl {
    public interface Cb {
        void onProgress(int percent, long got, long total);
        void onDone(boolean ok, String error);
    }

    private NovaDl() {}

    public static boolean download(String url, File dest, Cb cb) {
        if (url == null || dest == null) {
            if (cb != null) cb.onDone(false, "Missing download.");
            return false;
        }
        File part = new File(dest.getAbsolutePath() + ".part");
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            if (cb != null) cb.onDone(false, "Couldn't create the folder.");
            return false;
        }
        HttpURLConnection c = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            long already = part.isFile() ? part.length() : 0L;
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setInstanceFollowRedirects(true);
            c.setRequestProperty("User-Agent", "CodeAque/0.10.78");
            if (already > 0) c.setRequestProperty("Range", "bytes=" + already + "-");
            c.connect();
            int code = c.getResponseCode();
            boolean append = already > 0 && code == 206;
            if (code == 200) { already = 0; append = false; }
            else if (code != 206 && !(already == 0 && code >= 200 && code < 300)) {
                if (cb != null) cb.onDone(false, "Download failed (" + code + ").");
                return false;
            }
            long total = already;
            long cl = c.getContentLength();
            if (cl > 0) total = already + cl;
            in = c.getInputStream();
            out = new FileOutputStream(part, append);
            byte[] buf = new byte[64 * 1024];
            long got = already;
            int n;
            int lastPct = -1;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                got += n;
                if (cb != null && total > 0) {
                    int pct = (int) Math.min(100, (got * 100) / total);
                    if (pct != lastPct) { lastPct = pct; cb.onProgress(pct, got, total); }
                }
            }
            out.flush();
            out.close(); out = null;
            if (dest.exists()) dest.delete();
            if (!part.renameTo(dest)) {
                if (cb != null) cb.onDone(false, "Couldn't finish the file.");
                return false;
            }
            if (cb != null) cb.onProgress(100, dest.length(), dest.length());
            if (cb != null) cb.onDone(true, null);
            return true;
        } catch (Exception e) {
            if (cb != null) cb.onDone(false, "The download stopped. You can try again.");
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) { }
            try { if (out != null) out.close(); } catch (Exception ignored) { }
            if (c != null) c.disconnect();
        }
    }
}
