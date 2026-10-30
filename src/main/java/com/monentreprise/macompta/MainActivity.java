package com.monentreprise.macompta;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_SAVE = 1001;
    private static final int REQ_OPEN = 1002;
    private static final int MAX_IMPORT = 10 * 1024 * 1024; // 10 Mo
    private static final Pattern NOM_CALLBACK = Pattern.compile("[A-Za-z0-9_]+");

    private WebView webView;
    private WebView printView;      // WebView temporaire pour générer les PDF
    private String pendingBackup;   // contenu en attente d'écriture (sauvegarde)
    private String sumupResult;     // paramètres de retour de l'application SumUp

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        // Debug JS uniquement si l'APK est « debuggable » (évite la dépendance à BuildConfig)
        boolean debuggable = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        WebView.setWebContentsDebuggingEnabled(debuggable);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("file:///android_asset/");
            }
        });

        // Sans WebChromeClient, alert() et confirm() ne s'affichent pas dans une WebView
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton("OK", (d, w) -> result.confirm())
                        .setNegativeButton("Annuler", (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel())
                        .show();
                return true;
            }
        });

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");

        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
        handleSumupIntent(getIntent());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (printView != null) { printView.destroy(); printView = null; }
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }

    /** Exécute du JavaScript dans la page, depuis n'importe quel thread. */
    private void js(final String code) {
        runOnUiThread(() -> { if (webView != null) webView.evaluateJavascript(code, null); });
    }

    /** Mémorise le retour de l'application SumUp (macompta://sumup-callback?...). */
    private void handleSumupIntent(Intent i) {
        if (i == null) return;
        Uri u = i.getData();
        if (u != null && "macompta".equals(u.getScheme()) && "sumup-callback".equals(u.getHost())) {
            String q = u.getEncodedQuery();
            sumupResult = (q == null || q.isEmpty()) ? "empty=1" : q;
            i.setData(null); // évite de retraiter le même retour
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleSumupIntent(intent);
        js("(function(){try{if(window.__onSumupResult){var q=AndroidBridge.consumeSumupResult();if(q)window.__onSumupResult(q);}}catch(e){}})()");
    }

    /** Ouvre la boîte d'impression Android (« Enregistrer au format PDF », partage, imprimante). */
    private void imprimerHtml(final String html, final String nom) {
        runOnUiThread(() -> {
            if (printView != null) printView.destroy();
            final WebView w = new WebView(MainActivity.this);
            printView = w;
            final boolean[] lance = {false};
            w.setWebViewClient(new WebViewClient() {
                @Override
                public void onPageFinished(WebView v, String url) {
                    if (lance[0]) return;
                    lance[0] = true;
                    PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
                    if (pm == null) return;
                    PrintDocumentAdapter adapter = v.createPrintDocumentAdapter(nom);
                    pm.print(nom, adapter, new PrintAttributes.Builder()
                            .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
                }
            });
            w.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);

        if (req == REQ_SAVE) {
            boolean ok = false;
            if (res == RESULT_OK && data != null && data.getData() != null && pendingBackup != null) {
                try (OutputStream os = getContentResolver().openOutputStream(data.getData(), "wt")) {
                    if (os != null) {
                        os.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                        ok = true;
                    }
                } catch (Exception e) {
                    ok = false;
                }
            }
            pendingBackup = null;
            if (res == RESULT_OK) js("window.__onBackupSaved(" + ok + ")");

        } else if (req == REQ_OPEN && res == RESULT_OK && data != null && data.getData() != null) {
            try (InputStream is = getContentResolver().openInputStream(data.getData())) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while ((n = is.read(buf)) > 0) {
                    total += n;
                    if (total > MAX_IMPORT) throw new Exception("Fichier trop volumineux");
                    bos.write(buf, 0, n);
                }
                js("window.__onBackupLoaded(" + JSONObject.quote(bos.toString("UTF-8")) + ")");
            } catch (Exception e) {
                js("window.__onBackupLoaded(null)");
            }
        }
    }

    /** Pont JavaScript <-> Android. */
    public class Bridge {
        @JavascriptInterface
        public String getPlatform() { return "android"; }

        /** Rend (une seule fois) le retour SumUp en attente, ou une chaîne vide. */
        @JavascriptInterface
        public String consumeSumupResult() {
            String r = sumupResult;
            sumupResult = null;
            return r == null ? "" : r;
        }

        /** Lance un encaissement dans l'application SumUp (Payment Switch). */
        @JavascriptInterface
        public void sumupPay(final String key, final String total, final String title) {
            if (key == null || key.trim().isEmpty() || total == null || !total.matches("\\d{1,7}(\\.\\d{1,2})?")) {
                js("window.__onSumupError('param')");
                return;
            }
            runOnUiThread(() -> {
                Uri uri = new Uri.Builder()
                        .scheme("sumupmerchant").authority("pay").appendPath("1.0")
                        .appendQueryParameter("affiliate-key", key.trim())
                        .appendQueryParameter("app-id", getPackageName())
                        .appendQueryParameter("total", total)
                        .appendQueryParameter("currency", "EUR")
                        .appendQueryParameter("title", title == null ? "" : title)
                        .appendQueryParameter("callback", "macompta://sumup-callback")
                        .build();
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (ActivityNotFoundException e) {
                    js("window.__onSumupError('noapp')");
                }
            });
        }

        @JavascriptInterface
        public void toast(final String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void confirm(final String title, final String msg, final String ok,
                            final String cancel, final String cb) {
            if (cb == null || !NOM_CALLBACK.matcher(cb).matches()) return; // anti-injection
            runOnUiThread(() -> new AlertDialog.Builder(MainActivity.this)
                    .setTitle(title).setMessage(msg).setCancelable(false)
                    .setPositiveButton(ok, (d, w) -> js("window['" + cb + "'](true)"))
                    .setNegativeButton(cancel, (d, w) -> js("window['" + cb + "'](false)"))
                    .show());
        }

        @JavascriptInterface
        public void exportPdfOnly(String html, String filename) { imprimerHtml(html, filename); }

        @JavascriptInterface
        public void exportPdfAndShare(String html, String filename) { imprimerHtml(html, filename); }

        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public void saveBackup(final String filename, final String content) {
            pendingBackup = content;
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, filename);
                startActivityForResult(i, REQ_SAVE);
            });
        }

        @JavascriptInterface
        @SuppressWarnings("deprecation")
        public void pickBackup() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_OPEN);
            });
        }
    }
}
