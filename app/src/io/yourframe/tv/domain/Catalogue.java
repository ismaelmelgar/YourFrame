package io.yourframe.tv.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Every Artwork the Screensaver can hang, from the museums it knows: landscape only, one per id, whatever the list says. */
public final class Catalogue {
    public final List<Museum> museums;
    private final List<Artwork> artworks;

    public Catalogue(List<Museum> museums, List<Artwork> all) {
        this.museums = Collections.unmodifiableList(new ArrayList<>(museums));
        List<Artwork> kept = new ArrayList<>();
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (Artwork a : all) if (a.landscape() && ids.add(a.id)) kept.add(a);   // an id clash: the first one stays
        artworks = Collections.unmodifiableList(kept);
    }

    /** The works the Settings show: their category, from the museums that are on. */
    public List<Artwork> in(Settings s) {
        List<Artwork> out = new ArrayList<>();
        for (Artwork a : artworks) if (s.category.includes(a) && s.shows(a.museum)) out.add(a);
        return out;
    }

    public Artwork byId(int id) {
        for (Artwork a : artworks) if (a.id == id) return a;
        return null;
    }

    public Museum museum(String id) {
        for (Museum m : museums) if (m.id.equals(id)) return m;
        return null;
    }

    public List<String> museumIds() {
        List<String> ids = new ArrayList<>();
        for (Museum m : museums) ids.add(m.id);
        return ids;
    }

    public int size() {
        return artworks.size();
    }
}
