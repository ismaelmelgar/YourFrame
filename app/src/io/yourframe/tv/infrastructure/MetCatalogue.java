package io.yourframe.tv.infrastructure;

import android.content.Context;
import android.util.Log;

import io.yourframe.tv.domain.Artwork;
import io.yourframe.tv.domain.Catalogue;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * The Catalogue from assets/artworks.json, made on a computer by tools/make_artlist.py from The Met's Open Access
 * collection (the TV never searches the API). Read once per process.
 */
public final class MetCatalogue {
    private MetCatalogue() {}

    public static final String TAG = "YourFrame";
    private static Catalogue catalogue;

    public static synchronized Catalogue get(Context c) {
        if (catalogue != null) return catalogue;
        List<Artwork> list = new ArrayList<>();
        try (InputStream in = c.getAssets().open("artworks.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            JSONObject doc = new JSONObject(out.toString("UTF-8"));
            String prefix = doc.optString("img", "https://images.metmuseum.org/CRDImages/");
            JSONArray works = doc.getJSONArray("works");
            for (int i = 0; i < works.length(); i++) list.add(artwork(works.getJSONObject(i), prefix));
        } catch (Exception e) {
            Log.w(TAG, "artworks.json: " + e);
        }
        catalogue = new Catalogue(list);
        return catalogue;
    }

    private static Artwork artwork(JSONObject o, String prefix) {
        String path = o.optString("o");
        String original = path.startsWith("http") ? path : prefix + path;
        String s = o.optString("s", "");
        String small = s.isEmpty() ? original.replace("/original/", "/web-large/") : (s.startsWith("http") ? s : prefix + s);
        JSONArray k = o.optJSONArray("k");
        float[] crop = k == null || k.length() != 4 ? null
                : new float[] {(float) k.optDouble(0), (float) k.optDouble(1), (float) k.optDouble(2), (float) k.optDouble(3)};
        int color;
        try {
            color = 0xFF000000 | Integer.parseInt(o.optString("col", "#808080").substring(1), 16);
        } catch (RuntimeException e) {
            color = 0xFF808080;
        }
        return new Artwork(o.optInt("id"), o.optString("c"), o.optString("t"), o.optString("a"), o.optString("d"),
                o.optString("m"), original, small, o.optInt("h") == 1, (float) o.optDouble("r", 1.5), crop, color);
    }
}
