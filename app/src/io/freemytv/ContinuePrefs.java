package io.freemytv;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Used only by the :home process. Does not bump Prefs.version or recreate HomeView. */
final class ContinuePrefs {
    private final SharedPreferences prefs;
    ContinuePrefs(Context c) { prefs = c.getSharedPreferences("continue_watching", Context.MODE_PRIVATE); }
    Set<String> excluded() { return new HashSet<>(prefs.getStringSet("excluded", new HashSet<>())); }
    Set<String> hiddenKeys() { return hidden().keySet(); }
    Map<String, String> hidden() { return entries("hidden:"); }
    Map<String, String> sources() { return entries("source:"); }
    private Map<String, String> entries(String prefix) {
        Map<String, String> out = new TreeMap<>();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet())
            if (e.getKey().startsWith(prefix) && e.getValue() instanceof String)
                out.put(e.getKey().substring(prefix.length()), (String) e.getValue());
        return out;
    }
    void remember(Map<String, String> sources) {
        SharedPreferences.Editor edit = prefs.edit();
        for (Map.Entry<String, String> e : sources.entrySet()) edit.putString("source:" + e.getKey(), e.getValue());
        edit.apply();
    }
    void enabled(String pkg, boolean enabled) {
        Set<String> excluded = excluded();
        if (enabled) excluded.remove(pkg); else excluded.add(pkg);
        prefs.edit().putStringSet("excluded", excluded).apply();
    }
    void hide(ContinueModel.Item item) {
        prefs.edit().putString("hidden:" + item.key, item.title + " · " + item.source + " " + ContinueText.episode(item)).apply();
    }
    void restore(String key) { prefs.edit().remove("hidden:" + key).apply(); }
    void restoreAll() {
        SharedPreferences.Editor edit = prefs.edit();
        for (String key : hiddenKeys()) edit.remove("hidden:" + key);
        edit.apply();
    }
}
