package io.yourframe.tv.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** The order works hang in: at random, never one of the last 50 shown (remembered across Screensaver sessions). */
public final class Exhibition {
    public static final int REMEMBER = 50;
    private final ArrayDeque<Integer> recent = new ArrayDeque<>();

    /** From what saved() returned ("" for a new one); unreadable entries are skipped. */
    public Exhibition(String saved) {
        for (String s : saved.split(",")) {
            try {
                if (!s.isEmpty()) recent.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        while (recent.size() > REMEMBER) recent.removeFirst();
    }

    /**
     * A random work of `pool` that isn't in `skip` (on screen, or failed) nor among the recent ones; once every work
     * is recent, any not in `skip`. Null when there's none.
     */
    public Artwork next(List<Artwork> pool, Set<Integer> skip, Random random) {
        List<Artwork> fresh = new ArrayList<>();
        for (Artwork a : pool) if (!recent.contains(a.id) && !skip.contains(a.id)) fresh.add(a);
        if (fresh.isEmpty()) for (Artwork a : pool) if (!skip.contains(a.id)) fresh.add(a);
        return fresh.isEmpty() ? null : fresh.get(random.nextInt(fresh.size()));
    }

    public void shown(int id) {
        recent.remove(id);
        recent.addLast(id);
        while (recent.size() > REMEMBER) recent.removeFirst();
    }

    public String saved() {
        StringBuilder sb = new StringBuilder();
        for (Integer i : recent) sb.append(sb.length() == 0 ? "" : ",").append(i);
        return sb.toString();
    }
}
