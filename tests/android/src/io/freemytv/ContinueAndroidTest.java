package io.freemytv;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.*;

/** No third-party test framework. Runs entirely inside its own test package/provider. */
public class ContinueAndroidTest extends Instrumentation {
    private Bundle args;
    private int checks;
    private ContinueWatching watching;
    private ContinueImages images;
    private final BlockingQueue<Snapshot> results = new LinkedBlockingQueue<>();
    private static final class Snapshot {
        final ContinueWatching.State state;
        final List<ContinueModel.Item> items;
        Snapshot(ContinueWatching.State state, List<ContinueModel.Item> items) { this.state = state; this.items = items; }
    }
    private static final class PermissionContext extends ContextWrapper {
        volatile boolean allowed = true;
        PermissionContext(Context base) { super(base); }
        @Override public Context getApplicationContext() { return this; }
        @Override public int checkSelfPermission(String permission) {
            return allowed ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED;
        }
    }
    private void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private Snapshot result() throws Exception {
        Snapshot s = results.poll(5, TimeUnit.SECONDS);
        check(s != null, "callback arrived");
        return s;
    }
    @Override public void onCreate(Bundle args) { super.onCreate(args); this.args = args; start(); }
    @Override public void onStart() {
        Bundle out = new Bundle();
        try {
            if ("persistence".equals(args.getString("phase"))) persistence(); else tests();
            out.putString("stream", "PASS: " + checks + " Android checks (" + args.getString("phase", "main") + ")\n");
            finish(-1, out);
        } catch (Throwable e) {
            out.putString("stream", "FAIL: " + android.util.Log.getStackTraceString(e));
            finish(0, out);
        } finally {
            runOnMainSync(() -> { if (watching != null) watching.close(); if (images != null) images.close(); });
        }
    }
    private ContinueModel.Item item(String intent) {
        return new ContinueModel.Item(getTargetContext().getPackageName(), "persist", "Fixture", "Tests", "", "", "", "", intent, 0, 25, 100, 1);
    }
    private void persistence() {
        ContinuePrefs prefs = new ContinuePrefs(getTargetContext());
        check(prefs.hiddenKeys().contains(item("").key), "hide survives process restart");
        check(prefs.excluded().contains("fixture.source"), "source exclusion survives process restart");
        prefs.restoreAll();
        prefs.enabled("fixture.source", true);
    }
    private void tests() throws Exception {
        Context base = getTargetContext();
        PermissionContext context = new PermissionContext(base);
        ContinuePrefs prefs = new ContinuePrefs(base);
        prefs.restoreAll();
        prefs.enabled(base.getPackageName(), true);
        runOnMainSync(() -> watching = new ContinueWatching(context, (s, items) -> results.add(new Snapshot(s, items)), FixtureProvider.URI));
        context.allowed = false;
        runOnMainSync(() -> watching.start());
        check(result().state == ContinueWatching.State.PERMISSION, "denied permission has its own state");
        context.allowed = true;
        runOnMainSync(() -> watching.refresh());
        Snapshot ready = result();
        check(ready.state == ContinueWatching.State.READY && ready.items.size() == 5, "read fixture rows");
        check(ready.items.get(0).title.equals("Fixture 4"), "provider order normalized");
        prefs.hide(ready.items.get(0));
        runOnMainSync(() -> watching.refresh());
        check(result().items.size() == 4, "persistent hide applied to read");
        // Recreated cursor contains the same identity on each query, without relying on _id.
        runOnMainSync(() -> watching.refresh());
        check(result().items.size() == 4, "recreated provider rows remain hidden");
        prefs.restore(ready.items.get(0).key);
        prefs.enabled(base.getPackageName(), false);
        runOnMainSync(() -> watching.refresh());
        check(result().items.isEmpty(), "excluded app filtered");
        prefs.enabled(base.getPackageName(), true);
        FixtureProvider.count = 0;
        runOnMainSync(() -> watching.refresh());
        Snapshot empty = result();
        check(empty.state == ContinueWatching.State.READY && empty.items.isEmpty(), "empty distinct from error");
        FixtureProvider.fail = true;
        runOnMainSync(() -> watching.refresh());
        check(result().state == ContinueWatching.State.ERROR, "provider failure");
        FixtureProvider.fail = false;
        FixtureProvider.count = 6;
        int before = FixtureProvider.reads;
        runOnMainSync(() -> {
            for (int i = 0; i < 8; i++) base.getContentResolver().notifyChange(FixtureProvider.URI, null);
        });
        check(result().items.size() == 6, "observer updates");
        check(FixtureProvider.reads == before + 1, "300ms debounce coalesces events");
        context.allowed = false;
        runOnMainSync(() -> watching.refresh());
        check(result().state == ContinueWatching.State.PERMISSION, "permission revoked while running");
        context.allowed = true;
        runOnMainSync(() -> watching.refresh());
        result();
        FixtureProvider.entered = new CountDownLatch(1);
        FixtureProvider.release = new CountDownLatch(1);
        runOnMainSync(() -> watching.refresh());
        check(FixtureProvider.entered.await(5, TimeUnit.SECONDS), "slow query entered");
        runOnMainSync(() -> watching.stop());
        FixtureProvider.release.countDown();
        FixtureProvider.release = null;
        check(results.poll(500, TimeUnit.MILLISECONDS) == null, "paused query never delivers stale rows");
        before = FixtureProvider.reads;
        base.getContentResolver().notifyChange(FixtureProvider.URI, null);
        check(results.poll(500, TimeUnit.MILLISECONDS) == null && before == FixtureProvider.reads, "observer removed while paused");
        runOnMainSync(() -> watching.start());
        check(result().items.size() == 6, "resume refreshes");
        Intent valid = ContinueLaunch.material(base, item("continuetest://film/1"));
        check(base.getPackageName().equals(valid.getPackage()), "deep link pinned to source");
        check(valid.getFlags() == Intent.FLAG_ACTIVITY_NEW_TASK, "unsafe flags not inherited");
        for (String uri : Arrays.asList("", "unknownscheme://film", "intent://film#Intent;scheme=continuetest;package=other.app;end",
                "intent://film#Intent;scheme=continuetest;component=other.app/.Main;end")) {
            boolean rejected = false;
            try { ContinueLaunch.material(base, item(uri)); } catch (Exception expected) { rejected = true; }
            check(rejected, "reject missing/mismatched handler");
        }
        boolean missing = false;
        try { ContinueLaunch.material(base, new ContinueModel.Item("local.uninstalled.source", "id", "title", "", "", "", "", "", "continuetest://film", 0, 1, 100, 1)); }
        catch (Exception expected) { missing = true; }
        check(missing, "uninstalled source rejected");
        CountDownLatch loaded = new CountDownLatch(1);
        Bitmap art = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888);
        File image = new File(base.getFilesDir(), "fixture.png");
        try (FileOutputStream file = new FileOutputStream(image)) { art.compress(Bitmap.CompressFormat.PNG, 100, file); }
        art.recycle();
        String artUri = "content://local.codex.continuetests.provider/fixture.png";
        runOnMainSync(() -> { images = new ContinueImages(base, loaded::countDown); images.request(artUri); });
        check(loaded.await(5, TimeUnit.SECONDS), "content artwork callback");
        Bitmap decoded = images.get(artUri);
        check(decoded != null && decoded.getWidth() <= 840 && decoded.getHeight() <= 480, "art downsampled");
        runOnMainSync(() -> { images.request("http://127.0.0.1/image"); images.request("content://local.codex.continuetests.provider/missing.png"); });
        Thread.sleep(300);
        check(images.get("http://127.0.0.1/image") == null, "cleartext rejected");
        check(images.get("content://local.codex.continuetests.provider/missing.png") == null, "missing artwork placeholder");
        prefs.hide(item(""));
        prefs.enabled("fixture.source", false);
        check(new ContinuePrefs(base).hiddenKeys().contains(item("").key), "new store instance retains hide");
        // SharedPreferences.apply() is flushed by ActivityThread before the instrumentation process exits.
        base.getSharedPreferences("continue_watching", 0).edit().putBoolean("testFlush", true).commit();
    }
}
