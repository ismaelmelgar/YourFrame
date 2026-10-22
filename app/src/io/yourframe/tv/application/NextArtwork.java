package io.yourframe.tv.application;

import io.yourframe.tv.domain.Artwork;
import io.yourframe.tv.domain.Catalogue;
import io.yourframe.tv.domain.Exhibition;
import io.yourframe.tv.domain.Settings;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Finds the next Artwork to hang and its picture: a fresh pick from the Exhibition, downloaded; without internet, one
 * kept on the TV (of the Category and the museums that are on, if possible). Runs on a worker thread.
 */
public final class NextArtwork<P> {
    /** Where pictures come from: the TV's own copies and the museum. The infrastructure provides it. */
    public interface Pictures<P> {
        /** Ids of the works kept on the TV. */
        List<Integer> kept();
        /** The work's picture: the kept copy, or (when `online`) downloaded. */
        P load(Artwork a, boolean online) throws IOException;
    }

    public static final class Found<P> {
        public final Artwork artwork;
        public final P picture;
        public final boolean kept;      // the TV's own copy (the first of a session, or no internet)

        Found(Artwork artwork, P picture, boolean kept) {
            this.artwork = artwork;
            this.picture = picture;
            this.kept = kept;
        }
    }

    private static final int TRIES = 4;
    private final Catalogue catalogue;
    private final Exhibition exhibition;
    private final Pictures<P> pictures;
    private final Random random;
    /** Works whose picture failed (not for lack of internet): not tried again this session. */
    public final Set<Integer> failed = Collections.synchronizedSet(new HashSet<>());

    public NextArtwork(Catalogue catalogue, Exhibition exhibition, Pictures<P> pictures, Random random) {
        this.catalogue = catalogue;
        this.exhibition = exhibition;
        this.pictures = pictures;
        this.random = random;
    }

    /** The next work, or null when there's nothing to show (offline with nothing kept). `onScreen`: not these. */
    public Found<P> find(Settings settings, Set<Integer> onScreen) {
        Set<Integer> skip = skip(onScreen);
        for (int i = 0; i < TRIES; i++) {
            Artwork a = exhibition.next(catalogue.in(settings), skip, random);
            if (a == null) break;
            try {
                return new Found<>(a, pictures.load(a, true), false);
            } catch (IOException | RuntimeException e) {
                skip.add(a.id);
                if (e instanceof UnknownHostException || e instanceof ConnectException || e instanceof SocketTimeoutException) {
                    break;      // no internet: don't wait for the other tries to time out too
                }
                failed.add(a.id);
            }
        }
        Found<P> kept = fromKept(settings, skip, true);
        return kept != null ? kept : fromKept(settings, skip, false);
    }

    /** A work of the Category kept on the TV, at once (the first of a session, while an original downloads); or null. */
    public Found<P> kept(Settings settings, Set<Integer> onScreen) {
        return fromKept(settings, skip(onScreen), true);
    }

    private Set<Integer> skip(Set<Integer> onScreen) {
        Set<Integer> skip = new HashSet<>(onScreen);
        skip.addAll(failed);
        return skip;
    }

    private Found<P> fromKept(Settings settings, Set<Integer> skip, boolean chosen) {
        List<Integer> ids = new ArrayList<>(pictures.kept());
        Collections.shuffle(ids, random);
        for (int id : ids) {
            Artwork a = catalogue.byId(id);
            if (a == null || skip.contains(id) || (chosen && !(settings.category.includes(a) && settings.shows(a.museum)))) continue;
            try {
                return new Found<>(a, pictures.load(a, false), true);
            } catch (IOException | RuntimeException e) {
                skip.add(id);
            }
        }
        return null;
    }
}
