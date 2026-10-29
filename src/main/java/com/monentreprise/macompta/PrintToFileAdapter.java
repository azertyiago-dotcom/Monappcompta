package com.monentreprise.macompta;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.print.PageRange;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintDocumentInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Redirige la sortie d'un PrintDocumentAdapter vers un fichier. */
public class PrintToFileAdapter extends PrintDocumentAdapter {

    public interface OnFileReady { void run(); }

    private final PrintDocumentAdapter delegate;
    private final File target;
    private final OnFileReady onFileReady;
    private final PdfGenerator.Callback cb;
    private ParcelFileDescriptor tempFd;
    private File tempFile;

    public PrintToFileAdapter(PrintDocumentAdapter delegate, File target,
                              OnFileReady onFileReady, PdfGenerator.Callback cb) {
        this.delegate = delegate;
        this.target = target;
        this.onFileReady = onFileReady;
        this.cb = cb;
    }

    @Override
    public void onStart() {
        delegate.onStart();
    }

    @Override
    public void onLayout(PrintAttributes oldAttrs, PrintAttributes newAttrs,
                         CancellationSignal signal, LayoutResultCallback callback,
                         Bundle extras) {
        delegate.onLayout(oldAttrs, newAttrs, signal, new LayoutResultCallback() {
            @Override public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                // On accepte l'attribut proposé mais on impose notre taille de page A4
                callback.onLayoutFinished(info, changed);
            }
            @Override public void onLayoutFailed(CharSequence error) {
                callback.onLayoutFailed(error);
            }
            @Override public void onLayoutCancelled() {
                callback.onLayoutCancelled();
            }
        }, extras);
    }

    @Override
    public void onWrite(PageRange[] pages, ParcelFileDescriptor destination,
                        CancellationSignal signal, WriteResultCallback callback) {
        try {
            tempFile = File.createTempFile("fact", ".pdf", target.getParentFile());
            tempFd = ParcelFileDescriptor.open(tempFile,
                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE);

            delegate.onWrite(pages, tempFd, signal, new WriteResultCallback() {
                @Override public void onWriteFinished(PageRange[] pages) {
                    try {
                        tempFd.close();
                        copy(tempFile, target);
                        tempFile.delete();
                        callback.onWriteFinished(pages);
                        onFileReady.run();
                    } catch (Exception e) {
                        cb.onError(e.getMessage());
                        callback.onWriteFailed(e.getMessage());
                    }
                }
                @Override public void onWriteFailed(CharSequence error) {
                    callback.onWriteFailed(error);
                    cb.onError(String.valueOf(error));
                }
                @Override public void onWriteCancelled() {
                    callback.onWriteCancelled();
                    cb.onError("Annulé");
                }
            });
        } catch (Exception e) {
            cb.onError(e.getMessage());
            callback.onWriteFailed(e.getMessage());
        }
    }

    private void copy(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }
}
