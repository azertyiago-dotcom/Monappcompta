package com.monentreprise.macompta;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();

        // JavaScript OBLIGATOIRE
        settings.setJavaScriptEnabled(true);

        // Stockage DOM OBLIGATOIRE pour localStorage
        // Sans ceci, localStorage peut être null dans la WebView
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // Autoriser l'accès aux fichiers locaux (assets)
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        // Sécurité : ne pas autoriser l'accès file:// depuis d'autres origines
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);

        // Viewport mobile
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        // Activer Payment Request API pour Google Pay (WebView 137+)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PAYMENT_REQUEST)) {
            WebSettingsCompat.setPaymentRequestEnabled(settings, true);
        }

        // Debug JS uniquement en debug
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
        }

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
