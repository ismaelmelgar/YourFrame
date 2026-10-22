package io.yourframe.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/** "Preview": the screensaver full screen, as the TV will show it. Any key goes back, as in the real one. */
public class PreviewActivity extends Activity {
    private ArtView view;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        view = new ArtView(this, false);
        view.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(view);
    }

    @Override protected void onStart() {
        super.onStart();
        view.start();
    }

    @Override protected void onStop() {
        view.stop();
        super.onStop();
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }
}
