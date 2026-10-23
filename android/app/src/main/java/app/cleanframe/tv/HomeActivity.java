package app.cleanframe.tv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.webkit.WebView;

/** The Home screen: the CleanFrame web app full screen, with the remote's keys passed to it. */
public class HomeActivity extends Activity {
    /** Extra naming the view to show: "apps" for every app (the remote's Apps button), else Home. */
    static final String EXTRA_OPEN = "open";
    /** OK held this long is a hold (All apps: close or app info), not a press. */
    private static final long HOLD_MS = 450;
    private WebView web;
    private boolean okHeld;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        web = new WebView(this);
        WebAssets.configure(web);
        web.setWebViewClient(new WebAssets(this));
        web.addJavascriptInterface(new Host(this), "CleanFrameHost");
        setContentView(web);
        web.loadUrl(WebAssets.ORIGIN + "index.html" + (wantsApps(getIntent()) ? "#apps" : ""));
        web.requestFocus();
    }

    /** Home or Apps pressed while CleanFrame is running: show that view. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        web.evaluateJavascript("window.CleanFrameOpen && CleanFrameOpen('" + (wantsApps(intent) ? "apps" : "home") + "')", null);
    }

    private static boolean wantsApps(Intent intent) { return intent != null && "apps".equals(intent.getStringExtra(EXTRA_OPEN)); }

    /**
     * Back never leaves Home: the page gets it as Escape, its own Back key.
     * OK is decided here, where the remote's key repeats are visible: released early it reaches the
     * page as Enter, held past HOLD_MS it calls CleanFrameHold() instead. The WebView alone acts on
     * the first press, so a hold could never be told apart there.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode(), action = event.getAction();
        if (code == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN) sendKey("keydown", 27, "Escape", event.getRepeatCount() > 0);
            else if (action == KeyEvent.ACTION_UP) sendKey("keyup", 27, "Escape", false);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) okHeld = false;
                else if (!okHeld && event.getEventTime() - event.getDownTime() >= HOLD_MS) {
                    okHeld = true;
                    web.evaluateJavascript("window.CleanFrameHold && CleanFrameHold()", null);
                }
            } else if (action == KeyEvent.ACTION_UP) {
                if (!okHeld && !event.isCanceled()) { sendKey("keydown", 13, "Enter", false); sendKey("keyup", 13, "Enter", false); }
                okHeld = false;
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void sendKey(String type, int keyCode, String key, boolean repeat) {
        web.evaluateJavascript("document.dispatchEvent(new KeyboardEvent('" + type + "',{keyCode:" + keyCode + ",key:'" + key + "',repeat:" + repeat + ",bubbles:true}))", null);
    }

    @Override
    protected void onResume() { super.onResume(); web.onResume(); web.requestFocus(); }

    @Override
    protected void onPause() { web.onPause(); super.onPause(); }

    @Override
    protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
