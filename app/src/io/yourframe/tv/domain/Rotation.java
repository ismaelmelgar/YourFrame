package io.yourframe.tv.domain;

/**
 * The timing of the Exhibition on screen, as on The Frame: the first work fades in from black, each next one comes
 * in with a slow 2 s crossfade once its time is up, and the Caption leaves 10 s after a work arrives.
 */
public final class Rotation {
    private Rotation() {}

    public static final long FIRST_MS = 1600, FADE_MS = 2000, CAPTION_MS = 10_000, CAPTION_FADE_MS = 1200;

    /** Smoothstep from 0 to 1 over `duration`. */
    public static float ease(long elapsed, long duration) {
        float t = Math.max(0, Math.min(1, elapsed / (float) duration));
        return t * t * (3 - 2 * t);
    }

    /** Whether the next work comes in now: when the current one's time is up, or at once (a placeholder, a change). */
    public static boolean due(long shownAt, long now, long everyMs, boolean immediate) {
        return immediate || now >= shownAt + everyMs;
    }

    /** The Caption's opacity, `elapsed` ms after its work arrived. */
    public static float captionAlpha(long elapsed) {
        return elapsed < CAPTION_MS ? 1 : 1 - ease(elapsed - CAPTION_MS, CAPTION_FADE_MS);
    }

    public static boolean captionFading(long elapsed) {
        return elapsed >= CAPTION_MS && elapsed < CAPTION_MS + CAPTION_FADE_MS + 50;
    }
}
