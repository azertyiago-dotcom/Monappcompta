package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import com.monentreprise.macompta.PdfGenerator;

/**
 * Adaptateur qui redirige la sortie d'un PrintDocumentAdapter vers un fichier PDF
 * physiquement présent sur le disque.
 *
 * ⚠️ DOIT être dans le package "android.print" pour pouvoir accéder au
 * constructeur package-private de WriteResultCallback.
 */
public class PrintToFileAdapter extends PrintDocumentAdapter {

    /** Callback invoqué quand le fichier PDF est prêt sur le disque. */
    public interface OnFileReady {
        void run();
    }

    private final PrintDocumentAdapter delegate;
    private final File target;
    private final OnFileReady onFileReady;
    private final PdfGenerator.Callback cb;
    private ParcelFileDescriptor tempFd;
    private File tempFile;

    public PrintToFileAdapter(PrintDocumentAdapter delegate,
                              File target,
                              OnFileReady onFileReady,
                              PdfGenerator.Callback cb) {
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
    public void onLayout(PrintAttributes oldAttrs,
                         PrintAttributes newAttrs,
                         CancellationSignal signal,
                         LayoutResultCallback callback,
                         Bundle extras) {
        delegate.onLayout(oldAttrs, newAttrs, signal, new LayoutResultCallback() {
            @Override
            public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                callback.onLayoutFinished(info, changed);
            }

            @Override
            public void onLayoutFailed(CharSequence error) {
                callback.onLayoutFailed(error);
            }

            @Override
            public void onLayoutCancelled() {
                callback.onLayoutCancelled();
            }
        }, extras);
    }

    @Override
    public void onWrite(PageRange[] pages,
                        ParcelFileDescriptor destination,
                        CancellationSignal signal,
                        WriteResultCallback callback) {
        try {
            tempFile = File.createTempFile("fact", ".pdf", target.getParentFile());
            tempFd = ParcelFileDescriptor.open(
                    tempFile,
                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_CREATE
            );

            delegate.onWrite(pages, tempFd, signal, new WriteResultCallback() {
                @Override
                public void onWriteFinished(PageRange[] pages) {
                    try {
                        tempFd.close();
                        copy(tempFile, target);
                        //noinspection ResultOfMethodCallIgnored
                        tempFile.delete();
                        callback.onWriteFinished(pages);
                        onFileReady.run();
                    } catch (Exception e) {
                        cb.onError(e.getMessage());
                        callback.onWriteFailed(e.getMessage());
                    }
                }

                @Override
                public void onWriteFailed(CharSequence error) {
                    callback.onWriteFailed(error);
                    cb.onError(String.valueOf(error));
                }

                @Override
                public void onWriteCancelled() {
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
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }
}
