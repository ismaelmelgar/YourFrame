package io.yourframe.tv.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every Artwork the Screensaver can hang: only landscape ones, whatever the source list says. */
public final class Catalogue {
    private final List<Artwork> artworks;

    public Catalogue(List<Artwork> all) {
        List<Artwork> kept = new ArrayList<>();
        for (Artwork a : all) if (a.landscape()) kept.add(a);
        artworks = Collections.unmodifiableList(kept);
    }

    public List<Artwork> in(Category c) {
        List<Artwork> out = new ArrayList<>();
        for (Artwork a : artworks) if (c.includes(a)) out.add(a);
        return out;
    }

    public Artwork byId(int id) {
        for (Artwork a : artworks) if (a.id == id) return a;
        return null;
    }

    public int size() {
        return artworks.size();
    }
}
