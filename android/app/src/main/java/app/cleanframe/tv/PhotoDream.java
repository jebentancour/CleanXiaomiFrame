package app.cleanframe.tv;

import android.service.dreams.DreamService;
import android.view.ViewGroup;
import android.webkit.WebView;

/** Screensaver: CleanFrame's wallpapers with the clock and date (src/main/dream/dream.html). */
public class PhotoDream extends DreamService {
    /** True while the screensaver shows: Home then wakes it as usual instead of opening the quick menu. */
    static volatile boolean dreaming;
    private WebView web;

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);
        setFullscreen(true);
        setScreenBright(true);
        web = new WebView(this);
        WebAssets.configure(web);
        web.setWebViewClient(new WebAssets(this));
        setContentView(web);
        web.loadUrl(WebAssets.ORIGIN + "dream.html");
    }

    @Override
    public void onDreamingStarted() { super.onDreamingStarted(); dreaming = true; }

    @Override
    public void onDreamingStopped() { dreaming = false; super.onDreamingStopped(); }

    /**
     * Android's dream manager keeps each finished screensaver's binder, and through it this service and its window,
     * until its own garbage collection. Taking the WebView out of the window first keeps it out of that.
     */
    @Override
    public void onDetachedFromWindow() {
        if (web != null) {
            ViewGroup parent = (ViewGroup) web.getParent();
            if (parent != null) parent.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDetachedFromWindow();
    }
}
