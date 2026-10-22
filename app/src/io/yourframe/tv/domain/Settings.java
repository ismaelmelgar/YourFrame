package io.yourframe.tv.domain;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** What the viewer chose. Immutable: a change is a new Settings, compared with the old one. */
public final class Settings {
    /** Seconds between works ("manual" makes no sense in a screensaver). */
    public static final int[] EVERY = {30, 60, 300, 900};

    public enum Change { NONE, TIMING, LOOK, CATEGORY }

    public final Category category;
    public final Frame frame;
    public final Mat mat;
    public final int everySeconds;
    public final boolean info, clock;
    public final Night night;
    /** The museums turned off (so a museum added later is on). */
    public final Set<String> museumsOff;

    public Settings(Category category, Frame frame, Mat mat, int everySeconds, boolean info, boolean clock, Night night,
                    Set<String> museumsOff) {
        this.category = category;
        this.frame = frame;
        this.mat = mat;
        int e = 60;
        for (int s : EVERY) if (s == everySeconds) e = s;
        this.everySeconds = e;
        this.info = info;
        this.clock = clock;
        this.night = night;
        this.museumsOff = Collections.unmodifiableSet(new TreeSet<>(museumsOff));
    }

    public static final Settings DEFAULT = new Settings(Category.ALL, Frame.MAT_ONLY, Mat.PAPER, 60, true, true,
            Night.FROM_22, Collections.<String>emptySet());

    public boolean bleed() {
        return frame == Frame.BLEED;
    }

    public long everyMs() {
        return everySeconds * 1000L;
    }

    public boolean shows(String museum) {
        return !museumsOff.contains(museum);
    }

    /** With a museum turned on or off; turning off the last one that's on changes nothing (one always stays on). */
    public Settings withMuseum(String museum, boolean on, Collection<String> all) {
        Set<String> off = new TreeSet<>(museumsOff);
        if (on) off.remove(museum);
        else off.add(museum);
        boolean anyOn = false;
        for (String m : all) anyOn |= !off.contains(m);
        if (!anyOn) return this;
        return new Settings(category, frame, mat, everySeconds, info, clock, night, off);
    }

    /** What a change means for the work on screen: other works to choose from, a repaint, or only the timing. */
    public Change comparedWith(Settings old) {
        if (old == null || old.category != category || !old.museumsOff.equals(museumsOff)) return Change.CATEGORY;
        if (old.frame != frame || old.mat != mat || old.info != info || old.clock != clock) return Change.LOOK;
        if (old.everySeconds != everySeconds || old.night != night) return Change.TIMING;
        return Change.NONE;
    }
}
