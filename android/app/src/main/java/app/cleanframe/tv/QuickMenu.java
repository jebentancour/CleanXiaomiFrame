package app.cleanframe.tv;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.webkit.WebView;

/**
 * The quick menu: Home's clock and date on a panel at the top right, drawn over whatever app is open.
 * KeyService owns it, as an accessibility overlay, so it needs no extra permission and never takes
 * focus: the app underneath keeps playing. The panel is src/main/dream/quick.html, served from the
 * launcher's own origin so it reads the Home font from the same storage. The page is loaded once and
 * kept off screen, so a held Home shows it at once instead of waiting for a WebView to start.
 */
final class QuickMenu {
    /** The menu closes on its own after this long, so it never stays over a film. */
    private static final long AUTO_HIDE_MS = 10000;

    private final Context context;
    private final WindowManager windows;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;
    private WebView web;
    private boolean shown;

    QuickMenu(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
    }

    /** Starts the WebView and loads the page ahead of the first showing. */
    void prepare() {
        if (web != null) return;
        web = new WebView(context);
        WebAssets.configure(web);
        web.setWebViewClient(new WebAssets(context));
        web.setBackgroundColor(0);   // transparent: the app shows through around the panel
        web.loadUrl(WebAssets.ORIGIN + "quick.html");
    }

    boolean isShown() { return shown; }

    void show() {
        if (shown) return;
        prepare();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        try {
            windows.addView(web, params);
        } catch (RuntimeException e) {
            return;
        }
        shown = true;
        web.onResume();
        web.evaluateJavascript("window.CleanFrameQuick && CleanFrameQuick.show()", null);
        handler.postDelayed(hide, AUTO_HIDE_MS);
    }

    void hide() {
        handler.removeCallbacks(hide);
        if (!shown) return;
        shown = false;
        try { windows.removeView(web); } catch (RuntimeException ignored) { }
        web.onPause();
    }

    /** The service is going away: drop the WebView too. */
    void release() {
        hide();
        if (web != null) { web.destroy(); web = null; }
    }
}
