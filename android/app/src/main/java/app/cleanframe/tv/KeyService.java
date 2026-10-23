package app.cleanframe.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remote buttons that Xiaomi's partnercustomizer and the stock launcher used to handle:
 * Settings (KEYCODE_SETTINGS) opens the TV settings, since partnercustomizer would send it to the
 * Google TV quick panel, which is gone with the stock launcher disabled. YouTube (BUTTON_3) opens
 * YouTube when it is installed. Apps (ALL_APPS) opens CleanFrame's full app list. Xiaomi TV+ (BUTTON_5), Netflix (BUTTON_6) and Prime Video (BUTTON_7)
 * do nothing: their apps are disabled, and TV+ would otherwise reopen Live TV on the last HDMI input.
 * Every other key passes through untouched.
 */
public class KeyService extends AccessibilityService {
    private static final String YOUTUBE = "com.google.android.youtube.tv";

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Intent intent;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_BUTTON_5:
            case KeyEvent.KEYCODE_BUTTON_6:
            case KeyEvent.KEYCODE_BUTTON_7:
                return true;
            case KeyEvent.KEYCODE_SETTINGS:
                intent = new Intent(Settings.ACTION_SETTINGS);
                break;
            case KeyEvent.KEYCODE_ALL_APPS:
                intent = new Intent(this, HomeActivity.class).putExtra(HomeActivity.EXTRA_OPEN, "apps");
                break;
            case KeyEvent.KEYCODE_BUTTON_3:
                intent = getPackageManager().getLeanbackLaunchIntentForPackage(YOUTUBE);
                if (intent == null) return true;   // YouTube disabled or missing: the button does nothing
                break;
            default:
                return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return true;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
