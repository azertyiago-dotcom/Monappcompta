package com.monentreprise.macompta;

import android.print.PrintToFileAdapter;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.File;

/** Génère un PDF depuis un HTML de facture via PrintManager. */
public class PdfGenerator {

    public interface Callback {
        void onSuccess(File pdf);
        void onError(String message);
    }

    private static WebView printWebView; // garde une référence pour éviter le GC

    public static void generate(Context context, String html, String filename, Callback cb) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            cb.onError("API trop ancienne pour PrintManager");
            return;
        }

        final Context appCtx = context.getApplicationContext();
        final File outDir = new File(appCtx.getExternalFilesDir(null), "factures");
        if (!outDir.exists()) outDir.mkdirs();
        final File outFile = new File(outDir, filename);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                printWebView = new WebView(appCtx);
                printWebView.getSettings().setJavaScriptEnabled(false);
                printWebView.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView view, String url) {
                        try {
                            PrintManager pm = (PrintManager) appCtx
                                    .getSystemService(Context.PRINT_SERVICE);
                            String jobName = "Facture_" + filename;

                            PrintDocumentAdapter adapter = view.createPrintDocumentAdapter(jobName);
                            PrintAttributes attrs = new PrintAttributes.Builder()
                                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                                    .setResolution(new PrintAttributes.Resolution("pdf", "pdf", 600, 600))
                                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                                    .build();

                            // On redirige la sortie vers un fichier via un adapter custom
                            PrintToFileAdapter fileAdapter =
                                    new PrintToFileAdapter(adapter, outFile, () -> {
                                        // Nettoyage
                                        if (printWebView != null) {
                                            printWebView.destroy();
                                            printWebView = null;
                                        }
                                        if (outFile.exists() && outFile.length() > 0) {
                                            cb.onSuccess(outFile);
                                        } else {
                                            cb.onError("PDF vide");
                                        }
                                    }, cb);

                            pm.print(jobName, fileAdapter, attrs);
                        } catch (Exception e) {
                            cb.onError(e.getMessage());
                        }
                    }
                });
                printWebView.loadDataWithBaseURL(null, html, "text/HTML", "UTF-8", null);
            } catch (Exception e) {
                cb.onError(e.getMessage());
            }
        });
    }
}
