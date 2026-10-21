package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

final class ContinueLaunch {
    static final int PERMISSION_REQUEST = 42;
    private ContinueLaunch() {}

    static Intent material(android.content.Context a, ContinueModel.Item item) throws Exception {
        if (item.intent.isEmpty()) throw new IllegalArgumentException("Missing intent");
        Intent intent = Intent.parseUri(item.intent, Intent.URI_INTENT_SCHEME | Intent.URI_ANDROID_APP_SCHEME);
        if (intent.getSelector() != null || (intent.getPackage() != null && !item.pkg.equals(intent.getPackage()))
                || (intent.getComponent() != null && !item.pkg.equals(intent.getComponent().getPackageName())))
            throw new IllegalArgumentException("Different source");
        if (intent.getData() == null && intent.getComponent() == null) throw new IllegalArgumentException("Missing target");
        Uri playback = playbackUri(item.pkg, intent.getData());
        if (playback != intent.getData()) intent.setDataAndType(playback, intent.getType());
        intent.setPackage(item.pkg);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setClipData(null);
        ResolveInfo handler = a.getPackageManager().resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (handler == null || handler.activityInfo == null || !handler.activityInfo.exported
                || !item.pkg.equals(handler.activityInfo.packageName)) throw new IllegalArgumentException("Missing handler");
        return intent;
    }
    static Uri playbackUri(String pkg, Uri uri) {
        // Kinopoisk TV 2.270.8 supports action=play on its published episode route.
        // Verified on Xiaomi: the app resumes the supplied episode itself; no seek is injected.
        if (!"ru.kinopoisk.tv".equals(pkg) || uri == null || !uri.isHierarchical()
                || !"kpatv".equals(uri.getScheme()) || !"seasons".equals(uri.getAuthority())
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))
                || uri.getQueryParameterNames().contains("action")) return uri;
        for (String field : new String[]{"filmId", "episodeId"}) {
            java.util.List<String> values = uri.getQueryParameters(field);
            if (values.size() != 1 || values.get(0).trim().isEmpty()) return uri;
        }
        return uri.buildUpon().appendQueryParameter("action", "play").build();
    }
    static void open(Activity a, ContinueModel.Item item) {
        try { a.startActivity(material(a, item)); }
        catch (Exception e) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setTitle(ContinueText.title())
                    .setMessage(Ui.tr("Não foi possível abrir este material. O link pode estar indisponível.",
                            "This material could not be opened. Its link may be unavailable."))
                    .setPositiveButton(Ui.tr("Abrir aplicativo", "Open app"), (d, w) -> openApp(a, item.pkg))
                    .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private static void openApp(Activity a, String pkg) {
        try {
            Intent intent = a.getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = a.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) throw new IllegalArgumentException();
            a.startActivity(intent);
        } catch (RuntimeException e) {
            Toast.makeText(a, Ui.tr("Aplicativo indisponível", "App unavailable"), Toast.LENGTH_LONG).show();
        }
    }
    static void permission(Activity a) {
        android.content.SharedPreferences prefs = a.getSharedPreferences("continue_permission", 0);
        if (prefs.getBoolean("asked", false) && !a.shouldShowRequestPermissionRationale(ContinueWatching.PERMISSION)) {
            new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage(ContinueText.permission())
                    .setPositiveButton(Ui.tr("Abrir configurações", "Open settings"), (d, w) -> {
                        try { a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.getPackageName()))); }
                        catch (RuntimeException e) { Toast.makeText(a, ContinueText.permission(), Toast.LENGTH_LONG).show(); }
                    }).setNegativeButton(android.R.string.cancel, null).show();
        } else {
            prefs.edit().putBoolean("asked", true).apply();
            a.requestPermissions(new String[]{ContinueWatching.PERMISSION}, PERMISSION_REQUEST);
        }
    }
}
