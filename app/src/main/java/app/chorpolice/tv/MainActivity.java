package app.chorpolice.tv;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Full-screen launcher for the Chor Police TV board. The game server runs on the laptop; this app just shows its /tv page. */
public class MainActivity extends Activity {
    private static final int PORT = 3000;
    private WebView web;
    private View setup;
    private TextView status;
    private EditText addr;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean showingGame = false;
    private Runnable retry;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("chor", MODE_PRIVATE);
        web = findViewById(R.id.web);
        setup = findViewById(R.id.setup);
        status = findViewById(R.id.status);
        addr = findViewById(R.id.addr);
        Button find = findViewById(R.id.find);
        Button connect = findViewById(R.id.connect);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        web.setBackgroundColor(Color.parseColor("#CDE0B4"));
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) lost("Can't reach the game. Is it running on the laptop?");
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                load(); return true;
            }
        });

        find.setOnClickListener(v -> discover());
        connect.setOnClickListener(v -> connectTyped());
        addr.setOnEditorActionListener((v, id, e) -> { if (id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE) { connectTyped(); return true; } return false; });

        String saved = prefs.getString("server", null);
        if (saved != null) { addr.setText(saved); load(); } else { showSetup("Start the game on your laptop, then press Find my game."); find.requestFocus(); }
    }

    private String base() { return prefs.getString("server", null); }

    private void load() {
        String host = base();
        if (host == null) { showSetup("Start the game on your laptop, then press Find my game."); return; }
        showingGame = true;
        setup.setVisibility(View.GONE);
        web.setVisibility(View.VISIBLE);
        web.requestFocus();
        web.loadUrl("http://" + host + "/tv?app=1");
    }

    private void lost(String msg) {
        showSetup(msg + " Trying again…");
        if (retry != null) ui.removeCallbacks(retry);
        retry = () -> { if (!showingGame) load(); };
        ui.postDelayed(retry, 4000);
    }

    private void showSetup(String msg) {
        showingGame = false;
        web.setVisibility(View.GONE);
        setup.setVisibility(View.VISIBLE);
        status.setText(msg);
    }

    private void connectTyped() {
        String t = addr.getText().toString().trim().replace("http://", "").replace("https://", "");
        int slash = t.indexOf('/');
        if (slash >= 0) t = t.substring(0, slash);
        if (t.isEmpty()) { status.setText("Type the laptop address first."); return; }
        if (!t.contains(":")) t = t + ":" + PORT;
        prefs.edit().putString("server", t).apply();
        load();
    }

    /** Scan the TV's own Wi-Fi subnet for a Chor Police server on port 3000. */
    private void discover() {
        status.setText("Looking for the game on your Wi-Fi…");
        new Thread(() -> {
            String prefix = null;
            try {
                for (Enumeration<NetworkInterface> n = NetworkInterface.getNetworkInterfaces(); n.hasMoreElements(); ) {
                    NetworkInterface ni = n.nextElement();
                    if (!ni.isUp() || ni.isLoopback()) continue;
                    for (Enumeration<InetAddress> a = ni.getInetAddresses(); a.hasMoreElements(); ) {
                        InetAddress ia = a.nextElement();
                        if (ia instanceof Inet4Address && ia.isSiteLocalAddress()) {
                            String ip = ia.getHostAddress();
                            prefix = ip.substring(0, ip.lastIndexOf('.') + 1);
                        }
                    }
                }
            } catch (Exception ignored) { }
            if (prefix == null) { ui.post(() -> status.setText("The TV isn't on Wi-Fi/Ethernet. Connect it, then try again.")); return; }
            final AtomicReference<String> found = new AtomicReference<>();
            ExecutorService pool = Executors.newFixedThreadPool(48);
            for (int i = 1; i < 255 && found.get() == null; i++) {
                final String host = prefix + i;
                pool.execute(() -> {
                    if (found.get() != null) return;
                    try {
                        HttpURLConnection c = (HttpURLConnection) new URL("http://" + host + ":" + PORT + "/api/info").openConnection();
                        c.setConnectTimeout(350); c.setReadTimeout(700);
                        if (c.getResponseCode() == 200) {
                            java.io.InputStream in = c.getInputStream();
                            byte[] buf = new byte[400]; int len = in.read(buf);
                            if (len > 0 && new String(buf, 0, len).contains("\"urls\"")) found.compareAndSet(null, host + ":" + PORT);
                        }
                        c.disconnect();
                    } catch (Exception ignored) { }
                });
            }
            pool.shutdown();
            try { pool.awaitTermination(20, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            final String hit = found.get();
            ui.post(() -> {
                if (hit == null) { status.setText("Couldn't find it. Check the laptop is running the game on the same Wi-Fi, or type its address below."); }
                else { addr.setText(hit); prefs.edit().putString("server", hit).apply(); load(); }
            });
        }).start();
    }

    /** Back or Menu on the remote opens the setup screen; Back again returns to the game (or exits if nothing is saved). */
    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_MENU) {
            if (showingGame) { showSetup("Change the game address, or press Back to return to the game."); return true; }
            if (base() != null && code == KeyEvent.KEYCODE_BACK) { load(); return true; }
        }
        return super.onKeyDown(code, e);
    }

    @Override protected void onResume() { super.onResume(); web.onResume(); hideBars(); }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override public void onWindowFocusChanged(boolean f) { super.onWindowFocusChanged(f); if (f) hideBars(); }
    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }
}
