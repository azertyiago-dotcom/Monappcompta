package com.monentreprise.macompta;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return !url.startsWith("file:///android_asset/");
            }
        });

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.destroy(); webView = null; }
        super.onDestroy();
    }

    // ---------- Bridge JS <-> natif ----------
    public class Bridge {

        @JavascriptInterface
        public String getPlatform() { return "android"; }

        /** Dialogue de confirmation natif, plus fiable que window.confirm(). */
        @JavascriptInterface
        public void confirm(String title, String message, String ok, String cancel,
                            final String callbackJs) {
            runOnUiThread(() -> {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(title)
                        .setMessage(message)
                        .setCancelable(false)
                        .setPositiveButton(ok, (d, w) -> evalJs(callbackJs + "(true)"))
                        .setNegativeButton(cancel, (d, w) -> evalJs(callbackJs + "(false)"))
                        .show();
            });
        }

        @JavascriptInterface
        public void toast(final String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        /** Génère un PDF depuis un HTML et le partage. */
        @JavascriptInterface
        public void exportPdfAndShare(final String html, final String filename) {
            runOnUiThread(() -> PdfGenerator.generate(
                    MainActivity.this, html, filename,
                    new PdfGenerator.Callback() {
                        @Override public void onSuccess(File pdf) {
                            shareFile(pdf);
                        }
                        @Override public void onError(String message) {
                            Toast.makeText(MainActivity.this,
                                    "Erreur PDF : " + message, Toast.LENGTH_LONG).show();
                        }
                    }));
        }

        /** Exporte le PDF puis ouvre le sélecteur de partage. */
        @JavascriptInterface
        public void exportPdfOnly(final String html, final String filename) {
            runOnUiThread(() -> PdfGenerator.generate(
                    MainActivity.this, html, filename,
                    new PdfGenerator.Callback() {
                        @Override public void onSuccess(File pdf) {
                            Toast.makeText(MainActivity.this,
                                    "PDF enregistré : " + pdf.getAbsolutePath(),
                                    Toast.LENGTH_LONG).show();
                        }
                        @Override public void onError(String m) {
                            Toast.makeText(MainActivity.this, "Erreur : " + m, Toast.LENGTH_LONG).show();
                        }
                    }));
        }
    }

    private void evalJs(String js) {
        if (webView != null) webView.evaluateJavascript(js, null);
    }

    private void shareFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "Facture " + file.getName());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Partager la facture"));
        } catch (Exception e) {
            Toast.makeText(this, "Partage impossible : " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }
}
