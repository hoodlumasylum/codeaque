package com.codeaque.launcher;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;

/**
 * 0.10.46: bounded parallel art loader. Play icons, Roblox thumbnails and Steam covers share one
 * queue (default 3 workers) so a fast scroll enqueues work instead of dropping it or spawning a
 * thread per tile. Retries use exponential backoff. Disk files are the cache; a miss is only
 * written for a confirmed "no art" result, not a network blip. Keys are package / place / app id,
 * never a view index.
 */
public final class ArtLoader {
    public static final int PARALLEL = 3;
    public static final int MAX_TRIES = 3;
    public static final int OK = 1, NONE = 0, RETRY = -1;

    public static int backoffMs(int failureIndex) {
        if (failureIndex <= 0) return 400;
        if (failureIndex == 1) return 1200;
        return 3600;
    }

    public interface Backend {
        int play(String pkg, File dir);
        int roblox(String place, File dir);
        int steam(String appid, File dir);
    }

    public interface Listener { void artUpdated(String key); }

    static final int PLAY = 1, ROBLOX = 2, STEAM = 3;

    static final class Job {
        final int kind; final String id; final File dir; int tries;
        Job(int kind, String id, File dir) { this.kind = kind; this.id = id; this.dir = dir; }
        String key() {
            if (kind == PLAY) return "play:" + id;
            if (kind == ROBLOX) return "roblox:" + id;
            return "steam:" + id;
        }
    }

    private final ArrayDeque<Job> queue = new ArrayDeque<Job>();
    private final HashMap<String, Job> pending = new HashMap<String, Job>();
    private final HashSet<String> running = new HashSet<String>();
    private final Object lock = new Object();
    private Backend backend;
    private Listener listener;
    private boolean live;
    private int inflight;

    public ArtLoader() { this(null, null); }
    public ArtLoader(Backend backend, Listener listener) {
        this.backend = backend != null ? backend : new DefaultBackend();
        this.listener = listener;
    }
    public void setListener(Listener l) { this.listener = l; }
    public void setBackend(Backend b) { if (b != null) this.backend = b; }

    /** Start the worker threads once. Safe to call again. */
    public void start() {
        synchronized (lock) {
            if (live) return;
            live = true;
            for (int i = 0; i < PARALLEL; i++) {
                Thread t = new Thread(new ArtLoaderWorker(this), "art-loader-" + i);
                t.setDaemon(true);
                t.start();
            }
        }
    }

    public void requestPlay(String pkg, File dir) { enqueue(PLAY, pkg, dir); }
    public void requestRoblox(String place, File dir) { enqueue(ROBLOX, place, dir); }
    public void requestSteam(String appid, File dir) { enqueue(STEAM, appid, dir); }

    public boolean isQueuedOrRunning(String key) {
        synchronized (lock) { return pending.containsKey(key) || running.contains(key); }
    }
    public int queued() { synchronized (lock) { return queue.size(); } }
    public int inflight() { synchronized (lock) { return inflight; } }

    void enqueue(int kind, String id, File dir) {
        if (id == null || id.isEmpty() || dir == null) return;
        Job j = new Job(kind, id, dir);
        String key = j.key();
        synchronized (lock) {
            if (pending.containsKey(key) || running.contains(key)) return; // already in the queue — never drop it
            pending.put(key, j);
            queue.addLast(j);
            lock.notifyAll();
        }
    }

    /** Test hook: run one job on the calling thread. Returns false if the queue was empty. */
    boolean step() {
        Job j;
        synchronized (lock) {
            j = queue.pollFirst();
            if (j == null) return false;
            pending.remove(j.key());
            running.add(j.key());
            inflight++;
        }
        finish(j, exec(j));
        return true;
    }

    void loop() {
        while (true) {
            Job j;
            synchronized (lock) {
                if (!live) return;
                while (live && queue.isEmpty()) {
                    try { lock.wait(); } catch (InterruptedException e) { return; }
                }
                if (!live) return;
                j = queue.pollFirst();
                if (j == null) continue;
                pending.remove(j.key());
                running.add(j.key());
                inflight++;
            }
            int r = exec(j);
            if (r == RETRY && j.tries < MAX_TRIES) {
                try { Thread.sleep(backoffMs(j.tries)); } catch (InterruptedException e) { return; }
                j.tries++;
                synchronized (lock) {
                    running.remove(j.key());
                    inflight--;
                    if (!pending.containsKey(j.key())) {
                        pending.put(j.key(), j);
                        queue.addLast(j);
                    }
                    lock.notifyAll();
                }
                continue;
            }
            finish(j, r);
        }
    }

    private int exec(Job j) {
        try {
            if (j.kind == PLAY) return backend.play(j.id, j.dir);
            if (j.kind == ROBLOX) return backend.roblox(j.id, j.dir);
            return backend.steam(j.id, j.dir);
        } catch (Exception e) { return RETRY; }
    }

    private void finish(Job j, int result) {
        synchronized (lock) {
            running.remove(j.key());
            inflight--;
        }
        Listener l = listener;
        if (l != null && result == OK) {
            try { l.artUpdated(j.key()); } catch (Exception ignored) { }
        }
    }

    static final class DefaultBackend implements Backend {
        public int play(String pkg, File dir) { return PlayArt.fetchStatus(PlayArt.PLAY, pkg, dir); }
        public int roblox(String place, File dir) {
            try {
                if (StoreArt.fetchRoblox(place, dir)) return OK;
                if (place == null || !place.matches("\\d+")) return NONE;
                return RETRY;
            } catch (Exception e) { return RETRY; }
        }
        public int steam(String appid, File dir) {
            try {
                if (StoreArt.fetchSteam(appid, dir)) return OK;
                if (appid == null || !appid.matches("\\d+")) return NONE;
                return RETRY;
            } catch (Exception e) { return RETRY; }
        }
    }
}

final class ArtLoaderWorker implements Runnable {
    private final ArtLoader loader;
    ArtLoaderWorker(ArtLoader loader) { this.loader = loader; }
    public void run() { loader.loop(); }
}
