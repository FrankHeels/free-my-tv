package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Native dialogs provide TV focus, scrolling and TalkBack for variable-length settings lists. */
public class ContinueSettingsActivity extends Activity {
    private ContinuePrefs prefs;
    private ContinueWatching watching;
    private AlertDialog dialog;
    private ContinueWatching.State state = ContinueWatching.State.LOADING;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        prefs = new ContinuePrefs(this);
        watching = new ContinueWatching(this, (s, items) -> { state = s; });
        menu();
    }
    @Override protected void onResume() { super.onResume(); watching.start(); }
    @Override protected void onPause() { watching.stop(); super.onPause(); }
    @Override protected void onDestroy() { watching.close(); if (dialog != null) dialog.dismiss(); super.onDestroy(); }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        watching.refresh();
    }
    private AlertDialog.Builder builder() {
        if (dialog != null) dialog.dismiss();
        return new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(ContinueText.title());
    }
    private void menu() {
        dialog = builder().setItems(new String[]{Ui.tr("Aplicativos de origem", "Source apps"),
                Ui.tr("Materiais ocultos", "Hidden materials"), Ui.tr("Acesso ao histórico", "History access")}, (d, i) -> {
                    if (i == 0) sources();
                    else if (i == 1) hidden();
                    else access();
                }).setNegativeButton(android.R.string.cancel, (d, i) -> finish()).setOnCancelListener(d -> finish()).show();
    }
    private void access() {
        AlertDialog.Builder b = builder().setMessage(state == ContinueWatching.State.READY
                ? Ui.tr("Acesso permitido", "Access allowed") : ContinueText.status(state));
        if (state == ContinueWatching.State.PERMISSION)
            b.setPositiveButton(ContinueText.action(state), (d, i) -> { menu(); ContinueLaunch.permission(this); });
        else if (state == ContinueWatching.State.ERROR)
            b.setPositiveButton(ContinueText.action(state), (d, i) -> { watching.refresh(); menu(); });
        dialog = b.setNegativeButton(android.R.string.cancel, (d, i) -> menu()).setOnCancelListener(d -> menu()).show();
    }
    private void sources() {
        Map<String, String> sources = prefs.sources();
        if (sources.isEmpty()) { access(); return; }
        List<String> keys = new ArrayList<>(sources.keySet());
        String[] labels = new String[keys.size()];
        boolean[] checked = new boolean[keys.size()];
        Set<String> excluded = prefs.excluded();
        for (int i = 0; i < keys.size(); i++) { labels[i] = sources.get(keys.get(i)); checked[i] = !excluded.contains(keys.get(i)); }
        dialog = builder().setMultiChoiceItems(labels, checked, (d, i, on) -> prefs.enabled(keys.get(i), on))
                .setPositiveButton(android.R.string.ok, (d, i) -> menu()).setOnCancelListener(d -> menu()).show();
    }
    private void hidden() {
        Map<String, String> hidden = prefs.hidden();
        List<String> keys = new ArrayList<>(hidden.keySet());
        AlertDialog.Builder b = builder().setTitle(Ui.tr("Materiais ocultos", "Hidden materials"));
        if (keys.isEmpty()) b.setMessage(Ui.tr("Nenhum material oculto", "No hidden materials"));
        else {
            String[] labels = new String[keys.size()];
            for (int i = 0; i < keys.size(); i++) labels[i] = Ui.tr("Restaurar", "Restore") + ": " + hidden.get(keys.get(i));
            b.setItems(labels, (d, i) -> { prefs.restore(keys.get(i)); hidden(); });
            b.setPositiveButton(Ui.tr("Restaurar todos", "Restore all"), (d, i) -> { prefs.restoreAll(); hidden(); });
        }
        dialog = b.setNegativeButton(android.R.string.cancel, (d, i) -> menu()).setOnCancelListener(d -> menu()).show();
    }
}
