package com.codeaque.launcher;

/**
 * 0.10.51: one-row scroll physics, kept out of HomeView so it can be unit-tested without a device.
 * Units are tiles (1 = one tile + gap). The finger owns the position while down; a fling coasts
 * after lift; only then does the row ease to the nearest centred tile. Snap never runs against a
 * live drag (that fight was the main touch jitter in 0.10.49–0.10.50).
 */
public final class HomeScroll {
    public static final float FLING_FRICTION = 2.4f;
    public static final float FLING_START = 0.15f;
    public static final float FLING_STOP = 0.08f;
    public static final float SNAP_RATE = 8.0f;
    public static final float FOCUS_RATE = 9.0f;
    public static final float SCALE_RATE = 12.0f;

    public boolean dragging;
    public boolean flinging;
    public float scroll;
    public float vel;
    public int tile;
    public float focusScale = 1f;

    public void reset(float pos, int t) {
        dragging = false; flinging = false; vel = 0; scroll = pos; tile = Math.max(0, t); focusScale = 1f;
    }

    public void pointerDown() {
        dragging = true;
        flinging = false;
        vel = 0;
    }

    /** delta is in tile units, same sign as "finger left, later tiles". */
    public void pointerMove(float deltaTiles, float maxScroll) {
        if (!dragging) return;
        scroll = clamp(scroll + deltaTiles, 0, maxScroll);
        vel = 0;
        flinging = false;
    }

    public void pointerUp(float velTiles, float vis, float maxScroll, int count) {
        dragging = false;
        scroll = clamp(scroll, 0, maxScroll);
        if (Math.abs(velTiles) > FLING_START && maxScroll > 0) {
            flinging = true;
            vel = velTiles;
        } else {
            flinging = false;
            vel = 0;
            tile = nearest(vis, count);
        }
    }

    public void cancelPointer(float vis, float maxScroll, int count) {
        if (dragging) pointerUp(0, vis, maxScroll, count);
    }

    /** Controller / D-pad: stop a fling and ease to this tile. */
    public void focusTile(int t, int count) {
        dragging = false;
        flinging = false;
        vel = 0;
        tile = Math.max(0, Math.min(Math.max(0, count - 1), t));
    }

    /**
     * Advance one animation frame. While the finger is down this only clamps — it does not snap.
     * @return true if the row still needs another vsync
     */
    public boolean step(float dt, float vis, float maxScroll, int count, boolean wantFocusScale) {
        if (dt < 0) dt = 0;
        if (dt > 0.1f) dt = 0.1f;
        if (count < 0) count = 0;
        scroll = clamp(scroll, 0, maxScroll);

        boolean settling;
        if (dragging) {
            settling = true;
        } else if (flinging) {
            scroll += vel * dt;
            vel *= (float) Math.exp(-FLING_FRICTION * dt);
            if (scroll < 0) { scroll = 0; vel = 0; }
            if (scroll > maxScroll) { scroll = maxScroll; vel = 0; }
            if (Math.abs(vel) < FLING_STOP) {
                flinging = false;
                vel = 0;
                tile = nearest(vis, count);
            }
            settling = true;
        } else {
            float target = focusTarget(vis, maxScroll);
            float dist = target - scroll;
            float settle = 1f - (float) Math.exp(-SNAP_RATE * dt);
            if (settle > 1f) settle = 1f;
            scroll += dist * settle;
            if (Math.abs(target - scroll) < 0.0015f) scroll = target;
            settling = Math.abs(target - scroll) > 0.004f;
        }

        float want = wantFocusScale ? 1.05f : 1f;
        float sk = 1f - (float) Math.exp(-SCALE_RATE * dt);
        focusScale += (want - focusScale) * sk;
        if (Math.abs(want - focusScale) < 0.001f) focusScale = want;
        else settling = true;
        return settling || dragging || flinging;
    }

    public float focusTarget(float vis, float maxScroll) {
        return clamp(tile - (vis - 1f) / 2f, 0, maxScroll);
    }

    public int nearest(float vis, int count) {
        if (count <= 0) return 0;
        int t = Math.round(scroll + (vis - 1f) / 2f);
        if (t < 0) t = 0;
        if (t > count - 1) t = count - 1;
        return t;
    }

    /** Tile to highlight: nearest while the finger/fling owns the row, otherwise the committed tile. */
    public int highlight(float vis, int count) {
        return (dragging || flinging) ? nearest(vis, count) : tile;
    }

    public boolean busy() { return dragging || flinging; }

    public static float clamp(float v, float lo, float hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    /** 60 Hz physics bench: drag, fling, and snap to rest. Not a GPU measurement. */
    public static Bench benchFling(int tiles, float vis, float flingVel, float dt) {
        HomeScroll s = new HomeScroll();
        s.reset(0, 0);
        float max = Math.max(0, tiles - vis);
        s.pointerDown();
        s.pointerMove(0.4f, max);
        if (s.tile != 0) throw new IllegalStateException("drag must not commit the tile");
        boolean snappedDuringDrag = false;
        for (int i = 0; i < 8; i++) {
            float before = s.scroll;
            s.step(dt, vis, max, tiles, true);
            if (!s.dragging || Math.abs(s.scroll - before) > 0.0001f) snappedDuringDrag = true;
        }
        s.pointerUp(flingVel, vis, max, tiles);
        int frames = 0;
        float last = s.scroll;
        boolean reversedEarly = false;
        long t0 = System.nanoTime();
        while (s.step(dt, vis, max, tiles, true) && frames < 600) {
            frames++;
            if (s.flinging && flingVel > 0 && s.scroll + 0.0001f < last) reversedEarly = true;
            last = s.scroll;
        }
        long ns = System.nanoTime() - t0;
        if (s.flinging || s.dragging) throw new IllegalStateException("did not settle");
        Bench b = new Bench();
        b.frames = frames;
        b.simMs = frames * dt * 1000f;
        b.cpuNs = ns;
        b.snappedDuringDrag = snappedDuringDrag;
        b.reversedDuringFling = reversedEarly;
        b.finalTile = s.tile;
        b.finalScroll = s.scroll;
        return b;
    }

    public static final class Bench {
        public int frames, finalTile;
        public float simMs, finalScroll;
        public long cpuNs;
        public boolean snappedDuringDrag, reversedDuringFling;
    }
}
