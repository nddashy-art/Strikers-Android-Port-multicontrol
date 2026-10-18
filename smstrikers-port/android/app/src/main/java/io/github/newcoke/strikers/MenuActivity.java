package io.github.newcoke.strikers;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * A normal Android window for settings. Do not draw configuration controls on
 * top of the SDL/Vulkan surface: some Android devices keep the game surface
 * above child views added to SDLActivity, making the menu invisible or unstable.
 */
public final class MenuActivity extends Activity {
    static final String EXTRA_SECTION = "io.github.newcoke.strikers.MENU_SECTION";
    static final String EXTRA_WIFI_IP = "io.github.newcoke.strikers.MENU_WIFI_IP";
    static final String EXTRA_WIFI_PIN = "io.github.newcoke.strikers.MENU_WIFI_PIN";
    static final String EXTRA_WIFI_STATUS = "io.github.newcoke.strikers.MENU_WIFI_STATUS";

    private boolean menuShown;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Give the Activity a real content view before showing the full-screen dialog.
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF101722);
        setContentView(root);
        hideSystemBars();

        int section = getIntent() != null
                ? getIntent().getIntExtra(EXTRA_SECTION, 0) : 0;
        try {
            menuShown = true;
            MenuConfig.abrir(this, new Preferencias(this), () -> {
                setResult(RESULT_OK);
                finish();
            }, section);
        } catch (RuntimeException error) {
            menuShown = false;
            android.util.Log.e("strikers", "No se pudo crear la pantalla de ajustes", error);
            android.widget.Toast.makeText(this,
                    "No se pudo abrir ajustes. El juego sigue abierto.",
                    android.widget.Toast.LENGTH_LONG).show();
            finish();
        }
    }

    String[] informacionControlWifi() {
        android.content.Intent intent = getIntent();
        return new String[] {
            intent != null ? intent.getStringExtra(EXTRA_WIFI_IP) : null,
            intent != null ? intent.getStringExtra(EXTRA_WIFI_PIN) : null,
            intent != null ? intent.getStringExtra(EXTRA_WIFI_STATUS) : null
        };
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}
