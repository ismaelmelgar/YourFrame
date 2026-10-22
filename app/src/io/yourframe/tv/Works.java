package io.yourframe.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The bundled list of works (assets/artworks.json, made by tools/make_artlist.py): public-domain, landscape works from
 * The Met's Open Access collection, with their caption, image URLs, the crop that removes the photo's border and the
 * average colour. The TV never searches the API: it picks from this list at random, without repeating the last 50.
 */
final class Works {
    static final String TAG = "YourFrame";

    static final class Work {
        final int id;
        final String category, title, artist, date, medium, original, small;
        final boolean highlight;
        final float ratio;
        final float[] crop;     // left, top, right, bottom as fractions of the image
        final int color;

        Work(JSONObject o, String prefix) {
            id = o.optInt("id");
            category = o.optString("c");
            title = o.optString("t");
            artist = o.optString("a");
            date = o.optString("d");
            medium = o.optString("m");
            String path = o.optString("o");
            original = path.startsWith("http") ? path : prefix + path;
            String s = o.optString("s", "");
            small = s.isEmpty() ? original.replace("/original/", "/web-large/") : (s.startsWith("http") ? s : prefix + s);
            highlight = o.optInt("h") == 1;
            ratio = (float) o.optDouble("r", 1.5);
            JSONArray k = o.optJSONArray("k");
            crop = k == null || k.length() != 4 ? new float[] {0, 0, 1, 1}
                    : new float[] {(float) k.optDouble(0), (float) k.optDouble(1), (float) k.optDouble(2), (float) k.optDouble(3)};
            int col;
            try {
                col = 0xFF000000 | Integer.parseInt(o.optString("col", "#808080").substring(1), 16);
            } catch (RuntimeException e) {
                col = 0xFF808080;
            }
            color = col;
        }

        boolean in(String cat) {
            return "all".equals(cat) ? highlight && !"ph".equals(category) : cat.equals(category);
        }
    }

    private static List<Work> all;
    private static final int RECENT = 50;
    private static final Random random = new Random();

    static synchronized List<Work> all(Context c) {
        if (all != null) return all;
        List<Work> list = new ArrayList<>();
        try (InputStream in = c.getAssets().open("artworks.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject doc = new JSONObject(out.toString("UTF-8"));
            String prefix = doc.optString("img", "https://images.metmuseum.org/CRDImages/");
            JSONArray works = doc.getJSONArray("works");
            for (int i = 0; i < works.length(); i++) list.add(new Work(works.getJSONObject(i), prefix));
        } catch (Exception e) {
            Log.w(TAG, "artworks.json: " + e);
        }
        all = Collections.unmodifiableList(list);
        return all;
    }

    static List<Work> in(Context c, String cat) {
        List<Work> out = new ArrayList<>();
        for (Work w : all(c)) if (w.in(cat)) out.add(w);
        return out;
    }

    static Work byId(Context c, int id) {
        for (Work w : all(c)) if (w.id == id) return w;
        return null;
    }

    /** A random work of the category, not among the last 50 shown (nor in `skip`); remembered across sessions. */
    static synchronized Work pick(Context c, String cat, java.util.Set<Integer> skip) {
        List<Work> pool = in(c, cat);
        if (pool.isEmpty()) return null;
        ArrayDeque<Integer> recent = recent(c);
        List<Work> fresh = new ArrayList<>();
        for (Work w : pool) if (!recent.contains(w.id) && !skip.contains(w.id)) fresh.add(w);
        if (fresh.isEmpty()) {
            for (Work w : pool) if (!skip.contains(w.id)) fresh.add(w);
            if (fresh.isEmpty()) return null;
        }
        return fresh.get(random.nextInt(fresh.size()));
    }

    static synchronized void shown(Context c, int id) {
        ArrayDeque<Integer> recent = recent(c);
        recent.remove(id);
        recent.addLast(id);
        while (recent.size() > RECENT) recent.removeFirst();
        StringBuilder sb = new StringBuilder();
        for (Integer i : recent) sb.append(sb.length() == 0 ? "" : ",").append(i);
        Prefs.get(c).edit().putString("recent", sb.toString()).apply();
    }

    private static ArrayDeque<Integer> recent(Context c) {
        SharedPreferences p = Prefs.get(c);
        ArrayDeque<Integer> out = new ArrayDeque<>();
        for (String s : p.getString("recent", "").split(",")) {
            try {
                if (!s.isEmpty()) out.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }
}
