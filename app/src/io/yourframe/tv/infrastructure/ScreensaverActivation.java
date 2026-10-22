package io.yourframe.tv.infrastructure;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;

import io.yourframe.tv.ArtDream;
import io.yourframe.tv.R;

import java.io.IOException;

/**
 * "Set as screensaver". On plain Android TV the system's screensaver settings list YourFrame: open them. Google TV
 * hides third-party screensavers there, so the app sets itself through the TV's own ADB (as Free My TV does):
 * `settings put secure screensaver_components <this app>/.ArtDream`. The TV asks once "Allow USB debugging?".
 * The same command from a computer is shown as the fallback.
 */
public final class ScreensaverActivation {
    private ScreensaverActivation() {}

    public interface Listener { void status(String text, boolean done); }

    public static String component(Context c) {
        return new ComponentName(c, ArtDream.class).flattenToShortString();
    }

    public static String adbCommand(Context c) {
        return "adb shell settings put secure screensaver_components " + component(c);
    }

    /** Whether the TV's screensaver is YourFrame now (null when the TV doesn't let apps read it). */
    public static Boolean isActive(Context c) {
        try {
            String v = Settings.Secure.getString(c.getContentResolver(), "screensaver_components");
            if (v == null) return null;
            String mine = component(c), full = new ComponentName(c, ArtDream.class).flattenToString();
            for (String s : v.split(",")) if (s.trim().equals(mine) || s.trim().equals(full)) return true;
            return false;
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static boolean isGoogleTv(Context c) {
        PackageManager pm = c.getPackageManager();
        if (pm.hasSystemFeature("com.google.android.feature.GOOGLE_EXPERIENCE")
                && pm.hasSystemFeature("com.google.android.tv.installed")) return true;
        try {
            pm.getPackageInfo("com.google.android.apps.tv.launcherx", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public static void activate(final Context c, final Listener l) {
        if (!isGoogleTv(c)) {
            try {
                c.startActivity(new Intent(Settings.ACTION_DREAM_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                l.status(c.getString(R.string.act_opened), false);
                return;
            } catch (ActivityNotFoundException | SecurityException e) {
                // No screensaver settings on this TV: try the ADB way.
            }
        }
        final Handler main = new Handler(Looper.getMainLooper());
        final Context app = c.getApplicationContext();
        l.status(c.getString(R.string.act_working), false);
        new Thread(() -> {
            String result;
            boolean ok = false;
            try (Adb adb = Adb.connect(app, 60_000, () -> main.post(() -> l.status(app.getString(R.string.act_allow), false)))) {
                String comp = component(app);
                adb.shell("settings put secure screensaver_enabled 1");
                adb.shell("settings put secure screensaver_components " + comp);
                String now = adb.shell("settings get secure screensaver_components").trim();
                ok = now.contains(comp);
                result = ok ? app.getString(R.string.act_done)
                        : app.getString(R.string.act_failed, now) + "\n" + app.getString(R.string.act_computer, adbCommand(app));
            } catch (IOException | RuntimeException e) {
                Log.w(CatalogueFile.TAG, "activation: " + e.getMessage());
                String m = String.valueOf(e.getMessage());
                String why = m.contains("port 5555") ? app.getString(R.string.act_debug_off)
                        : m.contains("accepted") || m.contains("rejected") ? app.getString(R.string.act_rejected)
                        : m.contains("respond") ? app.getString(R.string.act_no_reply) : m;
                result = app.getString(R.string.act_failed, why) + "\n" + app.getString(R.string.act_computer, adbCommand(app));
            }
            final String text = result;
            final boolean done = ok;
            main.post(() -> l.status(text, done));
        }, "activation").start();
    }
}
