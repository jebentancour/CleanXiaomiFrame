package app.cleanframe.tv;

import android.content.Context;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.webkit.WebView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Properties;

/**
 * The remote's microphone button talks to Home Assistant's Assist. A press records the remote's
 * microphone and streams it to HA's preferred pipeline (assist_pipeline/run over the WebSocket API,
 * speech-to-text through to text-to-speech); HA decides when the speech has ended. What was heard
 * and the answer show on a panel like the quick menu's (src/main/dream/assist.html), and the answer
 * is spoken. A press while it runs, or Back while the panel shows, stops it.
 *
 * The HA address and a long-lived access token come from assist.properties in the app's external
 * files folder, pushed over ADB (docs/android-xiaomi.md): url=http://homeassistant:8123, token=...
 * An optional device_id names the TV's HA device, whose area Assist then uses for the question.
 */
final class Assist {
    static final String CONFIG = "assist.properties";
    private static final int RATE = 16000;
    /** Recording stops here even if HA has not heard the end of the speech. */
    private static final long LISTEN_MS = 10000;
    /** How long the panel stays after the answer, or after an error. */
    private static final long LINGER_MS = 5000;
    /** A new question within this long continues the same conversation. */
    private static final long FOLLOW_UP_MS = 5 * 60 * 1000;

    private final Context context;
    private final WindowManager windows;
    private final AudioManager audio;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hide = this::hide;
    private WebView web;
    private boolean shown, loaded;
    private String state = "listening", text = "";
    private Run run;
    private MediaPlayer player;
    private AudioFocusRequest focus;
    private String conversationId;
    private long conversationAt;

    Assist(Context context) {
        this.context = context;
        windows = context.getSystemService(WindowManager.class);
        audio = context.getSystemService(AudioManager.class);
    }

    boolean isShown() { return shown; }

    /** The microphone button: ask, or stop the question in progress. */
    void toggle() {
        if (run != null || player != null) { hide(); return; }
        handler.removeCallbacks(hide);
        stopPlayer();
        show("listening", "");
        requestFocus();
        run = new Run();
        run.start();
    }

    void hide() {
        handler.removeCallbacks(hide);
        if (run != null) { run.cancel(); run = null; }
        stopPlayer();
        abandonFocus();
        if (!shown) return;
        shown = false;
        try { windows.removeView(web); } catch (RuntimeException ignored) { }
        web.onPause();
    }

    void release() {
        hide();
        if (web != null) { web.destroy(); web = null; }
    }

    // Panel

    private void show(String state, String text) {
        this.state = state;
        this.text = text;
        if (web == null) {
            web = new WebView(context);
            WebAssets.configure(web);
            web.setWebViewClient(new WebAssets(context) {
                @Override
                public void onPageFinished(WebView view, String url) { loaded = true; render(); }
            });
            web.setBackgroundColor(0);
            web.loadUrl(WebAssets.ORIGIN + "assist.html");
        }
        if (!shown) {
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            try {
                windows.addView(web, params);
                shown = true;
                web.onResume();
            } catch (RuntimeException e) {
                return;
            }
        }
        render();
    }

    private void render() {
        if (web != null && loaded) {
            web.evaluateJavascript("window.CleanFrameAssist && CleanFrameAssist.set(" + JSONObject.quote(state) + ","
                    + JSONObject.quote(text) + ")", null);
        }
    }

    /** From the worker thread: update the panel if this run is still the current one. */
    private void post(Run from, String state, String text) {
        handler.post(() -> { if (run == from) show(state, text); });
    }

    /** From the worker thread: the run is over; the panel lingers unless an answer is still playing. */
    private void finished(Run from) {
        handler.post(() -> {
            if (run != from) return;
            run = null;
            if (player == null) { abandonFocus(); handler.postDelayed(hide, LINGER_MS); }
        });
    }

    // Spoken answer

    private void play(Run from, String url) {
        handler.post(() -> {
            if (run != from) return;
            stopPlayer();
            MediaPlayer p = new MediaPlayer();
            player = p;
            p.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            p.setOnPreparedListener(MediaPlayer::start);
            p.setOnCompletionListener(done -> spoken(p));
            p.setOnErrorListener((failed, what, extra) -> { spoken(p); return true; });
            try {
                p.setDataSource(url);
                p.prepareAsync();
            } catch (IOException | RuntimeException e) {
                spoken(p);
            }
        });
    }

    private void spoken(MediaPlayer p) {
        if (player != p) return;
        stopPlayer();
        if (run == null) { abandonFocus(); handler.postDelayed(hide, LINGER_MS); }
    }

    private void stopPlayer() {
        if (player == null) return;
        try { player.release(); } catch (RuntimeException ignored) { }
        player = null;
    }

    /** Pauses or ducks what is playing while the TV listens and answers. */
    private void requestFocus() {
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).build())
                    .build();
        }
        audio.requestAudioFocus(focus);
    }

    private void abandonFocus() {
        if (focus != null) audio.abandonAudioFocusRequest(focus);
    }

    private Properties config() {
        File file = new File(context.getExternalFilesDir(null), CONFIG);
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException e) {
            return null;
        }
        return p.getProperty("url", "").isEmpty() || p.getProperty("token", "").isEmpty() ? null : p;
    }

    /** One question: connect, authenticate, run the pipeline, stream the microphone until HA stops listening. */
    private final class Run extends Thread {
        private volatile boolean cancelled, recording;
        private volatile HaSocket socket;

        void cancel() {
            cancelled = true;
            recording = false;
            HaSocket s = socket;
            if (s != null) s.close();   // unblocks the reads
        }

        @Override
        public void run() {
            try {
                Properties config = config();
                if (config == null) { post(this, "error", "Assist no está configurado"); return; }
                URI base = URI.create(config.getProperty("url").replaceAll("/+$", "") + "/");
                socket = new HaSocket(base);
                if (cancelled) return;
                JSONObject m = new JSONObject(socket.receive());
                if ("auth_required".equals(m.optString("type"))) {
                    socket.sendText(new JSONObject().put("type", "auth").put("access_token", config.getProperty("token")).toString());
                    m = new JSONObject(socket.receive());
                }
                if (!"auth_ok".equals(m.optString("type"))) { post(this, "error", "Home Assistant rechazó el token"); return; }

                JSONObject request = new JSONObject().put("id", 1).put("type", "assist_pipeline/run")
                        .put("start_stage", "stt").put("end_stage", "tts")
                        .put("input", new JSONObject().put("sample_rate", RATE));
                // The TV's HA device, so "turn off the lights" acts on the TV's area
                String deviceId = config.getProperty("device_id", "");
                if (!deviceId.isEmpty()) request.put("device_id", deviceId);
                if (conversationId != null && System.currentTimeMillis() - conversationAt < FOLLOW_UP_MS) {
                    request.put("conversation_id", conversationId);
                }
                socket.sendText(request.toString());

                while (!cancelled) {
                    m = new JSONObject(socket.receive());
                    if ("result".equals(m.optString("type"))) {
                        if (!m.optBoolean("success")) { error(m.optJSONObject("error")); return; }
                        continue;
                    }
                    JSONObject event = m.optJSONObject("event");
                    if (event == null) continue;
                    JSONObject data = event.optJSONObject("data");
                    if (data == null) data = new JSONObject();
                    switch (event.optString("type")) {
                        case "run-start":
                            JSONObject runner = data.optJSONObject("runner_data");
                            if (runner != null) record(runner.optInt("stt_binary_handler_id", -1));
                            break;
                        case "stt-vad-end":
                            recording = false;
                            post(this, "thinking", "");
                            break;
                        case "stt-end":
                            recording = false;
                            JSONObject stt = data.optJSONObject("stt_output");
                            post(this, "thinking", stt == null ? "" : stt.optString("text"));
                            break;
                        case "intent-end":
                            JSONObject intent = data.optJSONObject("intent_output");
                            if (intent == null) break;
                            if (!intent.isNull("conversation_id")) {
                                conversationId = intent.optString("conversation_id");
                                conversationAt = System.currentTimeMillis();
                            }
                            post(this, "answer", speech(intent));
                            break;
                        case "tts-end":
                            JSONObject tts = data.optJSONObject("tts_output");
                            String url = tts == null ? "" : tts.optString("url");
                            if (!url.isEmpty()) play(this, base.resolve(url).toString());
                            break;
                        case "error":
                            error(data);
                            return;
                        case "run-end":
                            return;
                        default:
                            break;
                    }
                }
            } catch (IOException | JSONException | IllegalArgumentException e) {
                if (!cancelled) post(this, "error", "Sin conexión con Home Assistant");
            } finally {
                recording = false;
                HaSocket s = socket;
                if (s != null) s.close();
                finished(this);
            }
        }

        private void error(JSONObject error) {
            String code = error == null ? "" : error.optString("code");
            String message;
            switch (code) {
                case "stt-no-text-recognized": message = "No te entendí"; break;
                case "stt-stream-failed": message = "No llegó el audio"; break;
                default: message = error == null ? "Algo falló en Assist" : error.optString("message", "Algo falló en Assist");
            }
            post(this, "error", message);
        }

        private String speech(JSONObject intent) {
            JSONObject response = intent.optJSONObject("response");
            JSONObject speech = response == null ? null : response.optJSONObject("speech");
            JSONObject plain = speech == null ? null : speech.optJSONObject("plain");
            return plain == null ? "" : plain.optString("speech");
        }

        /** Streams 16 kHz mono PCM, each frame prefixed with HA's handler id; an empty frame ends the audio. */
        private void record(int id) {
            if (id < 0) return;
            recording = true;
            Thread mic = new Thread(() -> {
                int size = Math.max(AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096);
                AudioRecord recorder;
                try {
                    recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size);
                } catch (RuntimeException e) {
                    recorder = null;
                }
                if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    if (recorder != null) recorder.release();
                    post(Run.this, "error", "Sin permiso para el micrófono");
                    cancel();
                    return;
                }
                byte[] chunk = new byte[2048];
                long until = System.currentTimeMillis() + LISTEN_MS;
                try {
                    recorder.startRecording();
                    while (recording && System.currentTimeMillis() < until) {
                        int n = recorder.read(chunk, 0, chunk.length);
                        if (n <= 0) continue;
                        byte[] frame = new byte[n + 1];
                        frame[0] = (byte) id;
                        System.arraycopy(chunk, 0, frame, 1, n);
                        socket.sendBinary(frame);
                    }
                    if (!cancelled) socket.sendBinary(new byte[] { (byte) id });
                } catch (IOException | RuntimeException ignored) {
                    // the socket closed: the run reports it
                } finally {
                    recorder.release();
                }
            }, "assist-mic");
            mic.start();
        }
    }
}
