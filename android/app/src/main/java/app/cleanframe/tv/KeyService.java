package app.cleanframe.tv;

import android.accessibilityservice.AccessibilityService;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Remote buttons that Xiaomi's partnercustomizer and the stock launcher used to handle:
 * Settings (KEYCODE_SETTINGS) opens the TV settings, since partnercustomizer would send it to the
 * Google TV quick panel, which is gone with the stock launcher disabled. YouTube (BUTTON_3) opens
 * YouTube when it is installed. Apps (ALL_APPS) opens CleanFrame's full app list. Xiaomi TV+ (BUTTON_5), Netflix (BUTTON_6) and Prime Video (BUTTON_7)
 * do nothing: their apps are disabled, and TV+ would otherwise reopen Live TV on the last HDMI input.
 * Home pressed goes Home; held, it shows the quick menu over whatever is open (held again, it closes).
 * While the menu shows, Back, OK and the arrows close it without reaching the app.
 * Every other key passes through untouched.
 */
public class KeyService extends AccessibilityService {
    private static final String YOUTUBE = "com.google.android.youtube.tv";
    /** Home held this long opens the quick menu instead of going Home. */
    private static final long HOLD_MS = 200;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private QuickMenu quick;
    /** Whether this Home press, decided on its first down, is kept from the system, and whether it became a hold. */
    private boolean homeKept, homeHeld;
    private final Runnable homeHold = () -> {
        homeHeld = true;
        if (quick.isShown()) quick.hide(); else quick.show();
    };

    @Override
    protected void onServiceConnected() { quick = new QuickMenu(this); quick.prepare(); }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        Intent intent;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_HOME) return onHome(event);
        if (quick != null && quick.isShown() && closesQuick(code)) {
            if (event.getAction() == KeyEvent.ACTION_UP) quick.hide();
            return true;
        }
        switch (code) {
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

    /**
     * A press goes Home through the system's own action; held past HOLD_MS it toggles the menu. Timed
     * here because held keys are not repeated to an accessibility service. Over the screensaver Home
     * passes through, so it simply wakes.
     */
    private boolean onHome(KeyEvent event) {
        if (quick == null) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() > 0) return homeKept;
            homeKept = !PhotoDream.dreaming;
            homeHeld = false;
            if (homeKept) handler.postDelayed(homeHold, HOLD_MS); else quick.hide();
            return homeKept;
        }
        if (event.getAction() == KeyEvent.ACTION_UP && homeKept) {
            homeKept = false;
            handler.removeCallbacks(homeHold);
            if (!homeHeld && !event.isCanceled()) { quick.hide(); performGlobalAction(GLOBAL_ACTION_HOME); }
            return true;
        }
        return false;
    }

    private static boolean closesQuick(int code) {
        switch (code) {
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(homeHold);
        if (quick != null) quick.release();
        super.onDestroy();
    }
}
