package io.yourframe.tv;

// Derived from Free My TV's Screensaver (MIT License, Copyright (c) 2026 Free My TV contributors). See NOTICE.

import android.service.dreams.DreamService;

/**
 * The YourFrame screensaver. The system still decides when it starts and when the TV turns off; any key exits.
 * It runs in the app's default process: on Android 11 the system's DreamActivity runs there and only works with a
 * DreamService in that same process (Free My TV's ScreensaverAlone works round the same quirk).
 */
public class ArtDream extends DreamService {
    private ArtView view;

    @Override public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        view = new ArtView(this, false);
        setContentView(view);
    }

    @Override public void onDreamingStarted() {
        super.onDreamingStarted();
        if (view != null) view.start();
    }

    @Override public void onDreamingStopped() {
        if (view != null) view.stop();
        super.onDreamingStopped();
    }

    @Override public void onDetachedFromWindow() {
        if (view != null) view.stop();
        super.onDetachedFromWindow();
    }
}
