package io.yourframe.tv;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The settings, in the screensaver's own newspaper look (paper, serif type, rules), driven with the D-pad: up and down
 * move between rows, left and right (or OK) change a value. On the right, the screensaver itself, live, reflecting
 * every change. "Preview" opens it full screen; "Set as screensaver" makes it the TV's (see Activation).
 */
public class SettingsActivity extends Activity {
    private static final int PAPER = 0xFFF1EDE4, INK = 0xFF1E1C19, MUTED = 0xFF6B665C, RULE = 0xFFCFC9BC;

    private SharedPreferences prefs;
    private ArtView preview;
    private TextView status, count;
    private float dp;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Fonts.init(this);
        prefs = Prefs.get(this);
        dp = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(px(48), px(30), px(48), px(24));
        root.addView(columns, new FrameLayout.LayoutParams(-1, -1));

        // Left: the masthead and the settings.
        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        columns.addView(left, new LinearLayout.LayoutParams(0, -1, 0.42f));

        TextView title = text(getString(R.string.app_name), Fonts.displayBold, 38, INK);
        left.addView(title);
        TextView tag = text(getString(R.string.tagline).toUpperCase(), Fonts.textBold, 9.5f, MUTED);
        tag.setLetterSpacing(0.2f);
        left.addView(tag);
        left.addView(new DoubleRule(this), new LinearLayout.LayoutParams(-1, px(9)));
        space(left, 6);

        String[] catNames = names(Prefs.CATEGORY_NAMES);
        String[] everyNames = new String[Prefs.EVERY.length];
        for (int i = 0; i < everyNames.length; i++) {
            int s = Prefs.EVERY[i];
            everyNames[i] = s < 60 ? getString(R.string.every_s, s) : getString(R.string.every_min, s / 60);
        }
        String[] onOff = {getString(R.string.on), getString(R.string.off)};

        left.addView(new Row(this, R.string.set_category, catNames, () -> Prefs.index(Prefs.CATEGORIES, prefs.getString("category", "all")),
                i -> prefs.edit().putString("category", Prefs.CATEGORIES[i]).apply(), null));
        left.addView(new Row(this, R.string.set_frame, names(Prefs.FRAME_NAMES), () -> Prefs.index(Prefs.FRAMES, prefs.getString("frame", "none")),
                i -> prefs.edit().putString("frame", Prefs.FRAMES[i]).apply(), null));
        left.addView(new Row(this, R.string.set_mat, names(Prefs.MAT_NAMES), () -> Prefs.index(Prefs.MATS, prefs.getString("mat", "paper")),
                i -> prefs.edit().putString("mat", Prefs.MATS[i]).apply(), Prefs.MAT_COLORS));
        left.addView(new Row(this, R.string.set_every, everyNames, () -> new Prefs.Snapshot(prefs).every,
                i -> prefs.edit().putInt("every", Prefs.EVERY[i]).apply(), null));
        left.addView(new Row(this, R.string.set_info, onOff, () -> prefs.getBoolean("info", true) ? 0 : 1,
                i -> prefs.edit().putBoolean("info", i == 0).apply(), null));
        left.addView(new Row(this, R.string.set_clock, onOff, () -> prefs.getBoolean("clock", true) ? 0 : 1,
                i -> prefs.edit().putBoolean("clock", i == 0).apply(), null));
        left.addView(new Row(this, R.string.set_night, names(Prefs.NIGHT_NAMES), () -> Prefs.index(Prefs.NIGHTS, prefs.getString("night", "22")),
                i -> prefs.edit().putString("night", Prefs.NIGHTS[i]).apply(), null));
        space(left, 8);
        Row previewRow = new Row(this, R.string.set_preview, () -> startActivity(new Intent(this, PreviewActivity.class)));
        left.addView(previewRow);
        left.addView(new Row(this, R.string.set_activate, () -> Activation.activate(this, (t, done) -> {
            status.setText(t);
            if (done) refreshStatus(false);
        })));
        space(left, 10);
        status = text("", Fonts.text, 10.5f, MUTED);
        status.setLineSpacing(0, 1.25f);
        left.addView(status);

        // Right: the live preview, like a print on the page, and its credit line.
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setPadding(px(40), 0, 0, 0);
        columns.addView(right, new LinearLayout.LayoutParams(0, -1, 0.58f));
        TextView sec = text(getString(R.string.set_preview).toUpperCase(), Fonts.textBold, 9.5f, MUTED);
        sec.setLetterSpacing(0.26f);
        right.addView(sec);
        space(right, 6);
        FrameLayout card = new FrameLayout(this) {
            @Override protected void onMeasure(int wSpec, int hSpec) {
                int w = MeasureSpec.getSize(wSpec);
                super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.round(w * 9f / 16f), MeasureSpec.EXACTLY));
            }
        };
        card.setBackgroundColor(INK);
        card.setPadding(1, 1, 1, 1);
        card.setElevation(px(10));
        preview = new ArtView(this, true);
        card.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        right.addView(card, new LinearLayout.LayoutParams(-1, -2));
        space(right, 10);
        count = text("", Fonts.textItalic, 10.5f, MUTED);
        right.addView(count);
        TextView credit = text("Images: The Metropolitan Museum of Art, Open Access (CC0). Type: Playfair Display, "
                + "Libre Caslon Text (SIL OFL). Built on Free My TV (MIT).", Fonts.text, 9, MUTED);
        credit.setPadding(0, px(4), 0, 0);
        right.addView(credit);

        setContentView(root);
        left.getChildAt(4).requestFocus();   // the first row (after title, tagline, rule and space)
        refreshStatus(true);
        updateCount();
    }

    @Override protected void onStart() {
        super.onStart();
        preview.start();
        refreshStatus(false);
    }

    @Override protected void onStop() {
        preview.stop();
        super.onStop();
    }

    private void changed() {
        preview.settingsChanged();
        updateCount();
    }

    private void updateCount() {
        Prefs.Snapshot s = new Prefs.Snapshot(prefs);
        count.setText(getString(Prefs.CATEGORY_NAMES[s.category]) + " · "
                + getString(R.string.works_count, Works.in(this, s.categoryId()).size()) + " · The Met");
    }

    private void refreshStatus(boolean withCommand) {
        Boolean on = Activation.isActive(this);
        String line = on == null ? "" : getString(on ? R.string.saver_is_on : R.string.saver_is_off);
        if (on == null || !on) line += (line.isEmpty() ? "" : "\n") + getString(R.string.act_computer, Activation.adbCommand(this));
        if (withCommand || on != null) status.setText(line);
    }

    // MARK: - Views

    private int px(float v) {
        return Math.round(v * dp);
    }

    private void space(LinearLayout l, int h) {
        l.addView(new View(this), new LinearLayout.LayoutParams(1, px(h)));
    }

    private String[] names(int[] ids) {
        String[] out = new String[ids.length];
        for (int i = 0; i < ids.length; i++) out[i] = getString(ids[i]);
        return out;
    }

    private TextView text(String s, android.graphics.Typeface t, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTypeface(t);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp * 1.5f);
        v.setTextColor(color);
        v.setIncludeFontPadding(false);
        return v;
    }

    interface Getter { int get(); }
    interface Setter { void set(int i); }

    /** The double rule under the masthead: 2 px, a gap, 1 px. */
    private final class DoubleRule extends View {
        private final Paint p = new Paint();

        DoubleRule(Context c) {
            super(c);
            p.setColor(INK);
            p.setAlpha(150);
        }

        @Override protected void onDraw(Canvas c) {
            c.drawRect(0, px(3), getWidth(), px(3) + Math.max(2, px(1.2f)), p);
            c.drawRect(0, px(6.5f), getWidth(), px(6.5f) + Math.max(1, px(0.6f)), p);
        }
    }

    /** A setting (label left, value right, ◀ ▶ while focused) or an action (OK runs it). Focused: ink on paper, inverted. */
    private final class Row extends LinearLayout {
        private final TextView label, value;
        private final String[] options;
        private final Getter getter;
        private final Setter setter;
        private final Runnable action;
        private final int[] swatches;
        private final GradientDrawable swatch = new GradientDrawable();

        Row(Context c, int labelId, String[] options, Getter getter, Setter setter, int[] swatches) {
            this(c, labelId, options, getter, setter, swatches, null);
        }

        Row(Context c, int labelId, Runnable action) {
            this(c, labelId, null, null, null, null, action);
        }

        private Row(Context c, int labelId, String[] options, Getter getter, Setter setter, int[] swatches, Runnable action) {
            super(c);
            this.options = options;
            this.getter = getter;
            this.setter = setter;
            this.action = action;
            this.swatches = swatches;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setFocusable(true);
            setClickable(true);
            setPadding(px(12), px(7), px(12), px(7));
            label = text(getString(labelId).toUpperCase(), Fonts.textBold, 10, INK);
            label.setLetterSpacing(0.16f);
            addView(label, new LayoutParams(0, -2, 1));
            value = text("", action != null ? Fonts.displayBold : Fonts.displayItalic, 13, INK);
            value.setSingleLine();
            value.setEllipsize(TextUtils.TruncateAt.END);
            value.setGravity(Gravity.END);
            addView(value, new LayoutParams(-2, -2));
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setSize(px(13), px(13));
            setOnClickListener(v -> next(1));
            setOnFocusChangeListener((v, f) -> refresh());
            refresh();
            ViewGroup.MarginLayoutParams lp = new LayoutParams(-1, -2);
            setLayoutParams(lp);
        }

        void refresh() {
            boolean f = hasFocus();
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(px(3));
            bg.setColor(f ? INK : 0x00000000);
            if (!f) bg.setStroke(0, 0);
            setBackground(bg);
            int fg = f ? PAPER : INK;
            label.setTextColor(fg);
            value.setTextColor(fg);
            if (options != null) {
                String v = options[Math.max(0, Math.min(options.length - 1, getter.get()))];
                value.setText(f ? "‹  " + v + "  ›" : v);
                if (swatches != null) {
                    swatch.setColor(swatches[getter.get()]);
                    swatch.setStroke(Math.max(1, px(1)), f ? PAPER : 0xFFA9A294);
                    value.setCompoundDrawablesRelative(swatch, null, null, null);
                    value.setCompoundDrawablePadding(px(8));
                }
            } else {
                value.setText(f ? "OK  ›" : "›");
            }
        }

        void next(int step) {
            if (action != null) {
                action.run();
                return;
            }
            int n = options.length;
            setter.set(((getter.get() + step) % n + n) % n);
            refresh();
            changed();
        }

        @Override public boolean onKeyDown(int keyCode, KeyEvent e) {
            if (options != null && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                next(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
            return super.onKeyDown(keyCode, e);
        }
    }
}
