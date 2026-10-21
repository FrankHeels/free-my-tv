package io.freemytv;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.util.concurrent.CountDownLatch;

/** In-memory fixture, never writes android.media.tv. */
public class FixtureProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://local.codex.continuetests.provider/programs");
    static volatile boolean fail;
    static volatile int count = 5, reads;
    static volatile CountDownLatch entered, release;
    @Override public boolean onCreate() { return true; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        reads++;
        CountDownLatch wait = release;
        if (wait != null) {
            if (entered != null) entered.countDown();
            try { wait.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (fail) throw new IllegalStateException("fixture provider failure");
        MatrixCursor c = new MatrixCursor(new String[]{"package_name", "internal_provider_id", "title", "watch_next_type",
            "last_playback_position_millis", "duration_millis", "last_engagement_time_utc_millis", "intent_uri"});
        for (int i = 0; i < count; i++) c.addRow(new Object[]{getContext().getPackageName(), "film" + i, "Fixture " + i,
            0, 25L, 100L, 1000L + i, "continuetest://film/" + i});
        return c;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(new java.io.File(getContext().getFilesDir(), uri.getLastPathSegment()), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
