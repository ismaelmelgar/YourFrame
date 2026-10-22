package io.yourframe.tv.infrastructure;

import android.content.Context;
import android.util.Log;

import io.yourframe.tv.domain.Artwork;
import io.yourframe.tv.domain.Catalogue;
import io.yourframe.tv.domain.ImageRef;
import io.yourframe.tv.domain.Museum;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The Catalogue from assets/artworks.json, made on a computer by tools/make_artlist.py from the museums' open
 * collections (one adapter per museum there; the TV never searches any API). Read once per process.
 * Each work is an array: [museum, id, category, title, artist, date, medium, ratio, image, width, crop, colour].
 */
public final class CatalogueFile {
    private CatalogueFile() {}

    public static final String TAG = "YourFrame";
    private static Catalogue catalogue;

    public static synchronized Catalogue get(Context c) {
        if (catalogue != null) return catalogue;
        List<Museum> museums = new ArrayList<>();
        List<Artwork> list = new ArrayList<>();
        try (InputStream in = c.getAssets().open("artworks.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject doc = new JSONObject(out.toString("UTF-8"));
            JSONArray ms = doc.getJSONArray("museums");
            String[] kinds = new String[ms.length()], prefixes = new String[ms.length()];
            for (int i = 0; i < ms.length(); i++) {
                JSONObject m = ms.getJSONObject(i);
                museums.add(new Museum(m.getString("id"), m.getString("name"), m.optString("short", m.getString("name")),
                        m.optString("license")));
                kinds[i] = m.optString("kind");
                prefixes[i] = m.optString("prefix");
            }
            JSONArray works = doc.getJSONArray("works");
            for (int i = 0; i < works.length(); i++) {
                JSONArray w = works.getJSONArray(i);
                int m = w.getInt(0);
                if (m < 0 || m >= museums.size()) continue;
                list.add(artwork(w, museums.get(m).id, "iiif".equals(kinds[m]), prefixes[m]));
            }
        } catch (Exception e) {
            Log.w(TAG, "artworks.json: " + e);
        }
        catalogue = new Catalogue(museums, list);
        return catalogue;
    }

    private static Artwork artwork(JSONArray w, String museum, boolean iiif, String prefix) {
        String source = w.optString(1), path = w.optString(8);
        String url = path.startsWith("http") ? path : prefix + path;
        ImageRef image = iiif ? ImageRef.iiif(url, w.optInt(9))
                : ImageRef.fixed(url, url.contains("/original/") ? url.replace("/original/", "/web-large/")
                        : url.replace("_print.jpg", "_web.jpg"));
        JSONArray k = w.optJSONArray(10);
        float[] crop = k == null || k.length() != 4 ? null
                : new float[] {(float) k.optDouble(0), (float) k.optDouble(1), (float) k.optDouble(2), (float) k.optDouble(3)};
        int color;
        try {
            color = 0xFF000000 | Integer.parseInt(w.optString(11, "808080"), 16);
        } catch (RuntimeException e) {
            color = 0xFF808080;
        }
        // A stable id across list updates: the disk cache and the Exhibition's memory are keyed by it.
        return new Artwork((museum + ":" + source).hashCode(), museum, source, w.optString(2), w.optString(3),
                w.optString(4), w.optString(5), w.optString(6), image, (float) w.optDouble(7, 1.5), crop, color);
    }
}
