package io.yourframe.tv.infrastructure;

import android.content.Context;
import android.content.SharedPreferences;

import io.yourframe.tv.domain.Category;
import io.yourframe.tv.domain.Exhibition;
import io.yourframe.tv.domain.Frame;
import io.yourframe.tv.domain.Mat;
import io.yourframe.tv.domain.Night;
import io.yourframe.tv.domain.Settings;

/** Settings and the Exhibition's memory in SharedPreferences, shared by the settings screen and the Screensaver. */
public final class SettingsStore {
    private SettingsStore() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    public static Settings read(Context c) {
        SharedPreferences p = prefs(c);
        Settings d = Settings.DEFAULT;
        return new Settings(Category.byId(p.getString("category", d.category.id)), Frame.byId(p.getString("frame", d.frame.id)),
                Mat.byId(p.getString("mat", d.mat.id)), p.getInt("every", d.everySeconds), p.getBoolean("info", d.info),
                p.getBoolean("clock", d.clock), Night.byId(p.getString("night", d.night.id)),
                museums(p.getString("museumsOff", "")));
    }

    private static java.util.Set<String> museums(String saved) {
        java.util.Set<String> out = new java.util.TreeSet<>();
        for (String m : saved.split(",")) if (!m.trim().isEmpty()) out.add(m.trim());
        return out;
    }

    public static void save(Context c, Settings s) {
        prefs(c).edit().putString("category", s.category.id).putString("frame", s.frame.id).putString("mat", s.mat.id)
                .putInt("every", s.everySeconds).putBoolean("info", s.info).putBoolean("clock", s.clock)
                .putString("night", s.night.id).putString("museumsOff", android.text.TextUtils.join(",", s.museumsOff)).apply();
    }

    public static Exhibition exhibition(Context c) {
        return new Exhibition(prefs(c).getString("recent", ""));
    }

    public static void save(Context c, Exhibition e) {
        prefs(c).edit().putString("recent", e.saved()).apply();
    }
}
