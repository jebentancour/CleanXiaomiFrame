package app.cleanframe.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.SharedPreferences;
import android.net.Uri;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/** window.CleanFrameHost: what device.js asks of Android instead of Tizen. */
final class Host {
    private final Activity activity;

    Host(Activity activity) { this.activity = activity; }

    /** Installed TV apps as JSON [{appId, name, icon}], without this launcher. */
    @JavascriptInterface
    public String apps() {
        PackageManager pm = activity.getPackageManager();
        JSONArray out = new JSONArray();
        Set<String> seen = new HashSet<>();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo r : pm.queryIntentActivities(query, 0)) {
            String pkg = r.activityInfo.packageName;
            if (pkg.equals(activity.getPackageName()) || !seen.add(pkg)) continue;
            try {
                out.put(new JSONObject().put("appId", pkg).put("name", String.valueOf(r.loadLabel(pm))).put("icon", "/appicon/" + pkg + ".png"));
            } catch (JSONException ignored) {
            }
        }
        return out.toString();
    }

    @JavascriptInterface
    public boolean launch(String pkg) {
        PackageManager pm = activity.getPackageManager();
        Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
        return intent != null && start(intent);
    }

    /**
     * Devices the TV knows over HDMI-CEC, as JSON [{appId, name, port}]. appId is "hdmi<port>.<name slug>":
     * openDevice follows the name to whatever port the device is on now, and falls back to the port.
     */
    @JavascriptInterface
    public String devices() {
        JSONArray out = new JSONArray();
        List<String> ports = hdmiPorts();
        for (TvInputInfo info : cecDevices()) {
            String name = deviceName(info);
            int port = ports.indexOf(info.getParentId()) + 1;
            if (name.isEmpty() || port < 1) continue;
            try {
                out.put(new JSONObject().put("appId", "hdmi" + port + "." + slug(name)).put("name", name).put("port", port));
            } catch (JSONException ignored) {
            }
        }
        return out.toString();
    }

    @JavascriptInterface
    public boolean openDevice(String appId) {
        int dot = appId.indexOf('.');
        if (!appId.startsWith("hdmi") || dot < 0) return false;
        String wanted = appId.substring(dot + 1), input = null;
        for (TvInputInfo info : cecDevices())
            if (slug(deviceName(info)).equals(wanted)) input = info.getParentId();
        if (input == null) {   // device off or unplugged: try the port it was last seen on
            List<String> ports = hdmiPorts();
            int port;
            try { port = Integer.parseInt(appId.substring(4, dot)); } catch (NumberFormatException e) { return false; }
            if (port < 1 || port > ports.size()) return false;
            input = ports.get(port - 1);
        }
        // Live TV, the TV's own input switcher, tunes passthrough channels.
        return start(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input)));
    }

    /** Physical HDMI ports in port order (HW4, HW5, HW6 on this Xiaomi). */
    private List<String> hdmiPorts() {
        List<String> ports = new ArrayList<>();
        TvInputManager tv = activity.getSystemService(TvInputManager.class);
        if (tv == null) return ports;
        for (TvInputInfo info : tv.getTvInputList())
            if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() == null) ports.add(info.getId());
        Collections.sort(ports);
        return ports;
    }

    /** CEC devices appear as child inputs of the port they are plugged into. */
    private List<TvInputInfo> cecDevices() {
        List<TvInputInfo> list = new ArrayList<>();
        TvInputManager tv = activity.getSystemService(TvInputManager.class);
        if (tv == null) return list;
        for (TvInputInfo info : tv.getTvInputList())
            if (info.getType() == TvInputInfo.TYPE_HDMI && info.getParentId() != null) list.add(info);
        return list;
    }

    /** The name the device announces over CEC (HdmiDeviceInfo itself is a system API). */
    private String deviceName(TvInputInfo info) {
        CharSequence label = info.loadLabel(activity);
        return label == null ? "" : label.toString().trim();
    }

    private static String slug(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    /**
     * Apps used in the last six hours, most recent first, as a JSON array of package names.
     * Needs usage access (granted over ADB: appops set app.cleanframe.tv GET_USAGE_STATS allow);
     * without it the list is empty. An app closed here stays off the list until it is used again.
     */
    @JavascriptInterface
    public String recent() {
        UsageStatsManager usage = activity.getSystemService(UsageStatsManager.class);
        JSONArray out = new JSONArray();
        if (usage == null) return out.toString();
        long now = System.currentTimeMillis();
        Map<String, Long> last = new HashMap<>();
        UsageEvents events = usage.queryEvents(now - 6 * 3600_000L, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events != null && events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) last.put(e.getPackageName(), e.getTimeStamp());
        }
        SharedPreferences closed = closedPrefs();
        List<Map.Entry<String, Long>> list = new ArrayList<>(last.entrySet());
        list.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Long> entry : list) {
            String pkg = entry.getKey();
            if (pkg.equals(activity.getPackageName()) || closed.getLong(pkg, 0) >= entry.getValue()) continue;
            out.put(pkg);
        }
        return out.toString();
    }

    /** Closes an app that is in the background (Force stop needs a system permission; this does not). */
    @JavascriptInterface
    public boolean close(String pkg) {
        ActivityManager am = activity.getSystemService(ActivityManager.class);
        if (am == null) return false;
        am.killBackgroundProcesses(pkg);
        closedPrefs().edit().putLong(pkg, System.currentTimeMillis()).apply();
        return true;
    }

    /** The app's page in TV settings, with Force stop and Clear data. */
    @JavascriptInterface
    public boolean appInfo(String pkg) {
        return start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null)));
    }

    private SharedPreferences closedPrefs() { return activity.getSharedPreferences("closed", Activity.MODE_PRIVATE); }

    /**
     * The TV's own navigation sounds, as in its menus: "left", "right", "up", "down" when focus moves,
     * "select" for OK and Back. Android plays them only while system sounds are on (Settings → Sound).
     */
    @JavascriptInterface
    public void sound(String kind) {
        AudioManager audio = activity.getSystemService(AudioManager.class);
        if (audio == null) return;
        int effect;
        switch (kind) {
            case "left": effect = AudioManager.FX_FOCUS_NAVIGATION_LEFT; break;
            case "right": effect = AudioManager.FX_FOCUS_NAVIGATION_RIGHT; break;
            case "up": effect = AudioManager.FX_FOCUS_NAVIGATION_UP; break;
            case "down": effect = AudioManager.FX_FOCUS_NAVIGATION_DOWN; break;
            default: effect = AudioManager.FX_KEY_CLICK;
        }
        audio.playSoundEffect(effect);
    }

    @JavascriptInterface
    public boolean tvSettings() { return start(new Intent(Settings.ACTION_SETTINGS)); }

    private boolean start(Intent intent) {
        try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
