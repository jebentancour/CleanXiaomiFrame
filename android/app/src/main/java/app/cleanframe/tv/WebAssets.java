package app.cleanframe.tv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Serves the bundled web app from APK assets under one https origin, so localStorage, fetch and
 * relative paths behave as they do in the browser preview. /appicon/<package>.png renders that
 * app's launcher icon for the app list.
 */
class WebAssets extends WebViewClient {
    static final String ORIGIN = "https://cleanframe.local/";
    private static final Map<String, String> TYPES = new HashMap<>();
    static {
        TYPES.put("html", "text/html"); TYPES.put("js", "application/javascript"); TYPES.put("css", "text/css");
        TYPES.put("svg", "image/svg+xml"); TYPES.put("jpg", "image/jpeg"); TYPES.put("png", "image/png");
        TYPES.put("webp", "image/webp"); TYPES.put("woff2", "font/woff2"); TYPES.put("json", "application/json");
        TYPES.put("txt", "text/plain");
    }

    private final Context context;

    WebAssets(Context context) { this.context = context; }

    static void configure(WebView web) {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setTextZoom(100);
        s.setUseWideViewPort(true);   // honour width=device-width, so main.js scales its 1920×1080 stage to the screen
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        web.setBackgroundColor(0xff000000);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return !request.getUrl().toString().startsWith(ORIGIN);   // nothing outside the app opens in the WebView
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
        Uri url = request.getUrl();
        if (!"cleanframe.local".equals(url.getHost())) return blocked();
        String path = url.getPath() == null ? "" : url.getPath().replaceFirst("^/+", "");
        if (path.isEmpty()) path = "index.html";
        if (path.contains("..")) return blocked();
        if (path.startsWith("appicon/")) return appIcon(path.substring(8).replaceFirst("\\.png$", ""));
        try {
            InputStream in = context.getAssets().open(path);
            String ext = path.substring(path.lastIndexOf('.') + 1).toLowerCase();
            String type = TYPES.containsKey(ext) ? TYPES.get(ext) : "application/octet-stream";
            WebResourceResponse r = new WebResourceResponse(type, type.startsWith("text/") || type.endsWith("javascript") ? "utf-8" : null, in);
            r.setResponseHeaders(Collections.singletonMap("Access-Control-Allow-Origin", ORIGIN.substring(0, ORIGIN.length() - 1)));
            return r;
        } catch (IOException e) {
            return notFound();
        }
    }

    private WebResourceResponse appIcon(String pkg) {
        try {
            Drawable d = context.getPackageManager().getApplicationIcon(pkg);
            // Keep the icon's own proportions: a non-square icon (TikTok ships a 16:9 one) fills the
            // square and is cropped from the centre, never stretched.
            int size = 192, w = d.getIntrinsicWidth(), h = d.getIntrinsicHeight();
            float k = w > 0 && h > 0 ? Math.max((float) size / w, (float) size / h) : 1;
            int dw = w > 0 && h > 0 ? Math.round(w * k) : size, dh = w > 0 && h > 0 ? Math.round(h * k) : size;
            Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            d.setBounds((size - dw) / 2, (size - dh) / 2, (size + dw) / 2, (size + dh) / 2);
            d.draw(new Canvas(b));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, out);
            return new WebResourceResponse("image/png", null, new ByteArrayInputStream(out.toByteArray()));
        } catch (PackageManager.NameNotFoundException e) {
            return notFound();
        }
    }

    private static WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }

    private static WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", null, new ByteArrayInputStream(new byte[0]));
    }
}
