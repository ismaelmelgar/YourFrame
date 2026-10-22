package io.yourframe.tv;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Typeface;

/**
 * The newspaper type, bundled in assets/fonts (SIL Open Font License, licences alongside): Playfair Display for the
 * titles and the clock, Libre Caslon Text for the text. Both are variable fonts; each style is one weight of them.
 * Without the files (never in a real build), the system serif.
 */
final class Fonts {
    private Fonts() {}

    static Typeface text, textBold, textItalic, display, displayItalic, displayBold;
    private static boolean loaded;

    static synchronized void init(Context c) {
        if (loaded) return;
        loaded = true;
        AssetManager a = c.getAssets();
        text = font(a, "fonts/LibreCaslonText.ttf", 400, Typeface.NORMAL);
        textBold = font(a, "fonts/LibreCaslonText.ttf", 700, Typeface.BOLD);
        textItalic = font(a, "fonts/LibreCaslonText-Italic.ttf", 400, Typeface.ITALIC);
        display = font(a, "fonts/PlayfairDisplay.ttf", 500, Typeface.NORMAL);
        displayItalic = font(a, "fonts/PlayfairDisplay-Italic.ttf", 500, Typeface.ITALIC);
        displayBold = font(a, "fonts/PlayfairDisplay.ttf", 700, Typeface.BOLD);
    }

    private static Typeface font(AssetManager a, String path, int weight, int fallbackStyle) {
        try {
            Typeface t = new Typeface.Builder(a, path).setFontVariationSettings("'wght' " + weight).build();
            if (t != null) return t;
        } catch (RuntimeException ignored) {
        }
        return Typeface.create(Typeface.SERIF, fallbackStyle);
    }
}
