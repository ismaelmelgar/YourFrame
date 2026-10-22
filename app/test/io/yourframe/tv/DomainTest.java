package io.yourframe.tv;

import io.yourframe.tv.application.NextArtwork;
import io.yourframe.tv.domain.*;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.*;

/**
 * Plain-JVM tests for the domain and application layers (no Android, no framework): build.sh compiles them against
 * those packages only, so an Android import there also fails the build. Exits non-zero on the first failure.
 */
public final class DomainTest {
    private static int checks;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            System.err.println("FAIL: " + what);
            System.exit(1);
        }
    }

    private static Artwork art(int id, String cat, float ratio) {
        return art(id, "met", cat, ratio);
    }

    private static Artwork art(int id, String museum, String cat, float ratio) {
        return new Artwork(id, museum, "" + id, cat, "T" + id, "A", "1900", "Oil", ImageRef.fixed("o" + id, "s" + id),
                ratio, null, 0xFF808080);
    }

    private static final List<Museum> MUSEUMS = Arrays.asList(new Museum("met", "The Metropolitan Museum of Art", "The Met", "CC0"),
            new Museum("nga", "National Gallery of Art, Washington", "NGA", "CC0"));

    private static Settings with(Category c) {
        Settings d = Settings.DEFAULT;
        return new Settings(c, d.frame, d.mat, d.everySeconds, d.info, d.clock, d.night, d.museumsOff);
    }

    public static void main(String[] args) throws Exception {
        categories();
        catalogue();
        exhibition();
        night();
        settings();
        layout();
        crop();
        rotation();
        nextArtwork();
        museums();
        images();
        System.out.println("Domain tests: " + checks + " checks passed");
    }

    static void categories() {
        Artwork eu = art(1, "eu", 1.5f), as = art(2, "as", 1.5f), ph = art(3, "ph", 1.5f);
        check(Category.ALL.includes(eu) && Category.ALL.includes(as), "All has every category...");
        check(!Category.ALL.includes(ph), "...but photography");
        check(Category.EUROPEAN.includes(eu) && !Category.EUROPEAN.includes(ph), "a category has its own works");
        check(Category.byId("nope") == Category.ALL && Category.byId("ph") == Category.PHOTOGRAPHY, "byId");
    }

    static void catalogue() {
        Catalogue c = new Catalogue(MUSEUMS, Arrays.asList(art(1, "eu", 1.5f), art(2, "eu", 1.19f), art(3, "eu", 1.2f),
                art(3, "nga", "eu", 1.5f)));
        check(c.size() == 2 && c.byId(2) == null, "portrait and square works never hang");
        check(c.byId(3).museum.equals("met"), "an id clash keeps the first work");
        check(c.in(with(Category.EUROPEAN)).size() == 2 && c.in(with(Category.ASIAN)).isEmpty(), "filter by category");
        check(c.museum("nga").name.startsWith("National Gallery") && c.museum("x") == null, "museums by id");
    }

    static void exhibition() {
        List<Artwork> pool = new ArrayList<>();
        for (int i = 0; i < 60; i++) pool.add(art(i, "eu", 1.5f));
        Exhibition e = new Exhibition("");
        Random r = new Random(7);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            Artwork a = e.next(pool, Collections.emptySet(), r);
            check(seen.add(a.id), "no repeats among the last 50");
            e.shown(a.id);
        }
        Exhibition small = new Exhibition("");
        List<Artwork> two = pool.subList(0, 2);
        small.shown(0);
        small.shown(1);
        check(small.next(two, Collections.emptySet(), r) != null, "when every work is recent, still one");
        check(small.next(two, new HashSet<>(Arrays.asList(0, 1)), r) == null, "skip (on screen, failed) is never picked");
        check(small.next(two, Collections.singleton(0), r).id == 1, "skip leaves the other");
        Exhibition again = new Exhibition(e.saved());
        check(again.saved().equals(e.saved()) && again.saved().split(",").length == 50, "remembered across sessions, 50 at most");
        for (int i = 100; i < 200; i++) again.shown(i);
        check(again.saved().split(",").length == Exhibition.REMEMBER, "the memory stays at 50");
        check(new Exhibition("1,x,,3").saved().equals("1,3"), "unreadable entries are skipped");
    }

    static void night() {
        check(Night.FROM_22.dims(22) && Night.FROM_22.dims(6) && !Night.FROM_22.dims(7) && !Night.FROM_22.dims(21), "22 to 7");
        check(Night.FROM_23.dims(23) && Night.FROM_23.dims(7) && !Night.FROM_23.dims(8) && !Night.FROM_23.dims(22), "23 to 8");
        check(!Night.OFF.dims(23) && Night.ALWAYS.dims(12), "off and always");
    }

    static void settings() {
        Settings d = Settings.DEFAULT;
        Set<String> none = Collections.emptySet();
        check(new Settings(d.category, d.frame, d.mat, 42, true, true, d.night, none).everySeconds == 60, "unknown interval falls back to 1 min");
        check(d.comparedWith(d) == Settings.Change.NONE, "same settings");
        check(new Settings(Category.ASIAN, d.frame, d.mat, 60, true, true, d.night, none).comparedWith(d) == Settings.Change.CATEGORY, "category");
        check(new Settings(d.category, Frame.GOLD, d.mat, 60, true, true, d.night, none).comparedWith(d) == Settings.Change.LOOK, "frame is a repaint");
        check(new Settings(d.category, d.frame, d.mat, 60, false, true, d.night, none).comparedWith(d) == Settings.Change.LOOK, "caption is a repaint");
        check(new Settings(d.category, d.frame, d.mat, 300, true, true, d.night, none).comparedWith(d) == Settings.Change.TIMING, "interval");
        check(d.withMuseum("met", false, Arrays.asList("met", "nga")).comparedWith(d) == Settings.Change.CATEGORY, "museums change the works");
    }

    static void layout() {
        Settings d = Settings.DEFAULT;
        Layout l = new Layout(1920, 1080, d, 1.5f);
        check(Math.abs(l.window.width() / l.window.height() - 1.5f) < 0.01f, "the window keeps the work's shape");
        check(l.window.left >= l.inner.left && l.window.right <= l.inner.right, "inside the mat");
        check(Math.abs((l.window.left - l.inner.left) - (l.inner.right - l.window.right)) <= 1, "centred");
        check(l.window.top > l.mastTop + 44 && l.captionTop > l.window.bottom, "masthead above, caption below");
        check(l.captionTop + 100 < 1080, "the caption fits on screen");
        Settings gold = new Settings(d.category, Frame.GOLD, d.mat, 60, true, true, d.night, d.museumsOff);
        check(new Layout(1920, 1080, gold, 1.5f).inner.left == 64, "the frame takes its width");
        Layout small = new Layout(1280, 720, gold, 1.5f);
        check(Math.abs(small.inner.left - 64 * 1280 / 1920f) < 0.01f, "720p UIs scale everything");
        Settings bleed = new Settings(d.category, Frame.BLEED, d.mat, 60, true, true, d.night, d.museumsOff);
        Layout b = new Layout(1920, 1080, bleed, 1.3f);
        check(b.window.left <= 0 && b.window.top <= 0 && b.window.right >= 1920 && b.window.bottom >= 1080, "full bleed covers");
        Layout wide = new Layout(1920, 1080, bleed, 2.5f);
        check(wide.window.top == 0 && wide.window.height() == 1080, "very wide works cover by height");
        int[] kept = Layout.keptSize(1.5f, 1920, 1080);
        check(kept[0] == 1920 && kept[1] == 1280, "kept wide enough to cover");
        check(Layout.keptSize(2.5f, 1920, 1080)[1] == 1080, "kept tall enough to cover");
        check(Layout.sampleSize(8000, 6000, 1920, 1280) == 4 && Layout.sampleSize(1000, 800, 1920, 1280) == 1, "sample size");
    }

    static void crop() {
        Artwork a = new Artwork(1, "met", "1", "eu", "", "", "", "", ImageRef.fixed("o", "s"), 1.5f, new float[] {0.1f, 0.05f, 0.9f, 0.95f}, 0);
        check(Arrays.equals(a.cropIn(1000, 800), new int[] {100, 40, 900, 760}), "crop fractions apply to any size");
        Artwork bad = new Artwork(1, "met", "1", "eu", "", "", "", "", ImageRef.fixed("o", "s"), 1.5f, new float[] {0.5f, 0.5f, 0.5f, 0.5f}, 0);
        check(Arrays.equals(bad.cropIn(1000, 800), new int[] {0, 0, 1000, 800}), "a degenerate crop keeps the whole image");
    }

    static void rotation() {
        check(Rotation.ease(0, 2000) == 0 && Rotation.ease(2000, 2000) == 1 && Rotation.ease(-5, 2000) == 0, "ease ends");
        check(Rotation.captionAlpha(9_999) == 1 && Rotation.captionAlpha(11_200) == 0, "the caption leaves after 10 s");
        check(Rotation.captionFading(10_500) && !Rotation.captionFading(5_000) && !Rotation.captionFading(12_000), "fading window");
        check(!Rotation.due(0, 59_999, 60_000, false) && Rotation.due(0, 60_000, 60_000, false), "due when its time is up");
        check(Rotation.due(0, 1, 60_000, true), "a placeholder or a change goes at once");
    }

    static void museums() {
        List<String> all = Arrays.asList("met", "nga");
        Settings d = Settings.DEFAULT;
        Settings noMet = d.withMuseum("met", false, all);
        check(!noMet.shows("met") && noMet.shows("nga"), "a museum off");
        check(noMet.withMuseum("nga", false, all) == noMet, "the last museum on stays on");
        check(noMet.withMuseum("met", true, all).museumsOff.isEmpty(), "and back on");
        check(d.shows("someday"), "a museum added later is on");
        Catalogue c = new Catalogue(MUSEUMS, Arrays.asList(art(1, "met", "eu", 1.5f), art(2, "nga", "eu", 1.5f)));
        check(c.in(noMet).size() == 1 && c.in(noMet).get(0).museum.equals("nga"), "the catalogue follows the selection");
        check(c.museumIds().equals(all), "museum ids in order");
    }

    static void images() {
        ImageRef iiif = ImageRef.iiif("https://api.nga.gov/iiif/abc", 5000);
        check(iiif.url(1920, 1).equals("https://api.nga.gov/iiif/abc/full/1920,/0/default.jpg"), "IIIF: exactly the screen's width");
        check(iiif.url(1920, 0.8f).equals("https://api.nga.gov/iiif/abc/full/2400,/0/default.jpg"), "IIIF: wider when a border is cropped");
        check(ImageRef.iiif("b", 1500).url(1920, 1).equals("b/full/1500,/0/default.jpg"), "IIIF: never wider than the full image");
        check(ImageRef.iiif("b", 0).url(1280, 1).equals("b/full/1280,/0/default.jpg"), "IIIF: unknown width");
        check(iiif.fallback().equals("https://api.nga.gov/iiif/abc/full/!1280,1280/0/default.jpg"), "IIIF fallback");
        ImageRef fixed = ImageRef.fixed("https://images.metmuseum.org/x/original/a.jpg", "https://images.metmuseum.org/x/web-large/a.jpg");
        check(fixed.url(1920, 0.5f).equals(fixed.url) && fixed.fallback().endsWith("web-large/a.jpg"), "a fixed URL is what it is");
        check(ImageRef.fixed("u", null).fallback() == null, "no fallback");
    }

    /** Pictures that are "kept" for some ids and downloadable unless offline or broken. */
    static final class FakePictures implements NextArtwork.Pictures<String> {
        final List<Integer> kept;
        boolean offline;
        final Set<Integer> broken = new HashSet<>();
        int downloads;

        FakePictures(Integer... kept) { this.kept = Arrays.asList(kept); }

        @Override public List<Integer> kept() { return kept; }

        @Override public String load(Artwork a, boolean online) throws IOException {
            if (kept.contains(a.id)) return "kept" + a.id;
            if (!online) throw new IOException("not kept");
            if (offline) throw new UnknownHostException("images.metmuseum.org");
            if (broken.contains(a.id)) throw new IOException("HTTP 404");
            downloads++;
            return "net" + a.id;
        }
    }

    static void nextArtwork() {
        Catalogue c = new Catalogue(MUSEUMS, Arrays.asList(art(1, "eu", 1.5f), art(2, "eu", 1.5f),
                art(3, "as", 1.5f), art(4, "as", 1.5f), art(5, "nga", "eu", 1.5f)));
        FakePictures pics = new FakePictures(3);
        NextArtwork<String> next = new NextArtwork<>(c, new Exhibition(""), pics, new Random(1));
        NextArtwork.Found<String> f = next.find(with(Category.EUROPEAN), Collections.emptySet());
        check(f != null && f.artwork.category.equals("eu") && !f.kept && f.picture.startsWith("net"), "online: a fresh download");
        check(next.kept(with(Category.EUROPEAN), Collections.emptySet()) == null, "nothing kept in this category");
        check(next.kept(with(Category.ASIAN), Collections.emptySet()).artwork.id == 3, "the first work: kept on the TV");
        pics.offline = true;
        f = next.find(with(Category.EUROPEAN), Collections.emptySet());
        check(f != null && f.kept && f.artwork.id == 3, "offline: a kept work, even of another category");
        f = next.find(with(Category.ASIAN), Collections.emptySet());
        check(f != null && f.artwork.id == 3, "offline: a kept work of the category first");
        check(next.find(with(Category.ASIAN), Collections.singleton(3)) == null, "offline with nothing else kept: nothing");
        check(next.failed.isEmpty(), "no internet is not a broken work");
        pics.offline = false;
        pics.broken.addAll(Arrays.asList(1, 2, 5));
        f = next.find(with(Category.EUROPEAN), Collections.emptySet());
        check(f != null && f.artwork.id == 3 && next.failed.containsAll(Arrays.asList(1, 2, 5)),
                "broken images are skipped and remembered; then a kept work");
        Settings noNga = with(Category.EUROPEAN).withMuseum("nga", false, c.museumIds());
        next.failed.clear();
        pics.broken.clear();
        for (int i = 0; i < 20; i++) {
            f = next.find(noNga, Collections.emptySet());
            check(f != null && f.artwork.museum.equals("met"), "a museum turned off never hangs");
        }
    }
}
