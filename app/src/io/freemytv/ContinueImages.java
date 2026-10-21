package io.freemytv;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Only visible cards are queued, never from onDraw. Failed art is a normal placeholder. */
final class ContinueImages {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private final Context context;
    private final Runnable changed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newFixedThreadPool(2);
    private final Set<String> pending = new HashSet<>(), failed = new LinkedHashSet<>();
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap value) { return value.getAllocationByteCount(); }
    };
    private boolean closed;
    ContinueImages(Context context, Runnable changed) { this.context = context.getApplicationContext(); this.changed = changed; }
    Bitmap get(String uri) { return cache.get(uri); }
    void request(String uri) {
        if (closed || uri.isEmpty() || cache.get(uri) != null || pending.contains(uri) || failed.contains(uri) || pending.size() >= 12) return;
        pending.add(uri);
        worker.execute(() -> {
            Bitmap bitmap = load(uri);
            main.post(() -> {
                pending.remove(uri);
                if (closed) { if (bitmap != null) bitmap.recycle(); return; }
                if (bitmap != null) cache.put(uri, bitmap);
                else {
                    failed.add(uri);
                    if (failed.size() > 64) failed.remove(failed.iterator().next());
                }
                changed.run();
            });
        });
    }
    void close() { closed = true; worker.shutdownNow(); cache.evictAll(); }
    private Bitmap load(String value) {
        HttpURLConnection connection = null;
        try {
            Uri uri = Uri.parse(value);
            InputStream stream;
            if ("content".equals(uri.getScheme())) stream = context.getContentResolver().openInputStream(uri);
            else if ("https".equals(uri.getScheme())) {
                // Handle redirects ourselves so an HTTPS origin cannot redirect to cleartext HTTP.
                URL url = new URL(value);
                for (int redirects = 0; ; redirects++) {
                    if (!"https".equalsIgnoreCase(url.getProtocol()) || redirects > 4) return null;
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setConnectTimeout(5000);
                    connection.setReadTimeout(5000);
                    int code = connection.getResponseCode();
                    if (code >= 300 && code < 400) {
                        String location = connection.getHeaderField("Location");
                        if (location == null) return null;
                        url = new URL(url, location);
                        connection.disconnect();
                    } else { if (code != 200) return null; break; }
                }
                if (connection.getContentLengthLong() > MAX_BYTES) return null;
                stream = connection.getInputStream();
            } else return null;
            if (stream == null) return null;
            byte[] bytes;
            try (InputStream in = stream; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted() || out.size() + count > MAX_BYTES) return null;
                    out.write(buffer, 0, count);
                }
                bytes = out.toByteArray();
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = 1;
            while (options.outWidth / options.inSampleSize > 840 || options.outHeight / options.inSampleSize > 480)
                options.inSampleSize *= 2;
            options.inJustDecodeBounds = false;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
        } catch (Exception | OutOfMemoryError e) { return null; }
        finally { if (connection != null) connection.disconnect(); }
    }
}
