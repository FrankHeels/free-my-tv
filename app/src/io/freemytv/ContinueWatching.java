package io.freemytv;

import android.content.Context;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Read-only Watch Next client. All lifecycle methods and callbacks run on the main thread. */
final class ContinueWatching {
    static final String PERMISSION = "android.permission.READ_TV_LISTINGS";
    enum State { LOADING, PERMISSION, ERROR, READY }
    interface Listener { void changed(State state, List<ContinueModel.Item> items); }
    private final Context context;
    private final android.net.Uri uri;
    private final ContinuePrefs prefs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean active, observing;
    private int generation;
    private CancellationSignal cancellation;
    private final Runnable reload = this::refresh;
    private final ContentObserver observer = new ContentObserver(main) {
        @Override public void onChange(boolean selfChange) {
            // Invalidate a running query immediately; debounce only the next query.
            invalidateQuery();
            main.removeCallbacks(reload);
            if (active) main.postDelayed(reload, 300);
        }
    };

    ContinueWatching(Context context, Listener listener) {
        this(context, listener, TvContract.WatchNextPrograms.CONTENT_URI);
    }
    // An isolated provider URI lets the Android test APK exercise lifecycle without writing TV history.
    ContinueWatching(Context context, Listener listener, android.net.Uri uri) {
        this.context = context.getApplicationContext();
        this.uri = uri;
        prefs = new ContinuePrefs(context);
        this.listener = listener;
    }
    boolean permitted() { return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED; }
    void start() { if (!active) { active = true; refresh(); } }
    void stop() {
        active = false;
        main.removeCallbacks(reload);
        invalidateQuery();
        unobserve();
    }
    void close() { stop(); worker.shutdownNow(); }
    private void invalidateQuery() {
        generation++;
        if (cancellation != null) cancellation.cancel();
        cancellation = null;
    }
    private void unobserve() {
        if (observing) context.getContentResolver().unregisterContentObserver(observer);
        observing = false;
    }
    void refresh() {
        if (!active) return;
        main.removeCallbacks(reload);
        invalidateQuery();
        if (!permitted()) {
            unobserve();
            listener.changed(State.PERMISSION, Collections.emptyList());
            return;
        }
        if (!observing) {
            try {
                context.getContentResolver().registerContentObserver(uri, true, observer);
                observing = true;
            } catch (RuntimeException e) {
                listener.changed(State.ERROR, Collections.emptyList());
                return;
            }
        }
        final int ticket = generation;
        final CancellationSignal signal = new CancellationSignal();
        cancellation = signal;
        final Set<String> excluded = prefs.excluded(), hidden = prefs.hiddenKeys();
        worker.execute(() -> {
            List<ContinueModel.Item> rows = new ArrayList<>();
            Map<String, String> sources = new HashMap<>();
            State state = State.READY;
            try (Cursor c = context.getContentResolver().query(uri,
                    null, null, null, null, signal)) {
                if (c == null) throw new IllegalStateException("No cursor");
                while (c.moveToNext()) {
                    signal.throwIfCanceled();
                    String pkg = string(c, "package_name");
                    if (pkg.isEmpty()) continue;
                    String label = sources.get(pkg);
                    if (label == null) {
                        try { label = context.getPackageManager().getApplicationLabel(
                                context.getPackageManager().getApplicationInfo(pkg, 0)).toString(); }
                        catch (PackageManager.NameNotFoundException e) { label = pkg; }
                        sources.put(pkg, label);
                    }
                    rows.add(new ContinueModel.Item(pkg, string(c, "internal_provider_id"), string(c, "title"), label,
                            string(c, "season_display_number"), string(c, "episode_display_number"), string(c, "episode_title"),
                            string(c, "poster_art_uri"), string(c, "intent_uri"), (int) number(c, "watch_next_type"),
                            number(c, "last_playback_position_millis"), number(c, "duration_millis"),
                            number(c, "last_engagement_time_utc_millis")));
                }
            } catch (android.os.OperationCanceledException e) { return; }
            catch (SecurityException e) { state = permitted() ? State.ERROR : State.PERMISSION; }
            catch (RuntimeException e) { state = State.ERROR; }
            final State resultState = state;
            final List<ContinueModel.Item> result = state == State.READY
                    ? ContinueModel.visible(rows, excluded, hidden) : Collections.emptyList();
            main.post(() -> {
                if (!active || ticket != generation) return;
                cancellation = null;
                if (!permitted()) { unobserve(); listener.changed(State.PERMISSION, Collections.emptyList()); return; }
                if (resultState == State.READY) prefs.remember(sources);
                listener.changed(resultState, result);
            });
        });
    }
    private static String string(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        return i < 0 || c.isNull(i) ? "" : c.getString(i);
    }
    private static long number(Cursor c, String name) {
        int i = c.getColumnIndex(name);
        return i < 0 || c.isNull(i) ? -1 : c.getLong(i);
    }
}
