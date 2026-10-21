package io.freemytv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;

/** Resolves the factory launcher without asking Android to change its default home. */
final class StockHome {
    private StockHome() {}

    static Intent homeQuery() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    }

    static ComponentName resolve(PackageManager pm, String recorded) {
        List<ActivityInfo> candidates = new ArrayList<>();
        for (ResolveInfo info : pm.queryIntentActivities(homeQuery(), PackageManager.MATCH_DISABLED_COMPONENTS))
            if (info.activityInfo != null) candidates.add(info.activityInfo);
        return select(candidates, recorded);
    }

    static ComponentName select(List<ActivityInfo> candidates, String recorded) {
        ComponentName saved = ComponentName.unflattenFromString(recorded);
        Set<ComponentName> homes = new HashSet<>();
        for (ActivityInfo info : candidates) {
            if (!info.exported || info.applicationInfo == null
                    || (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    || "io.freemytv".equals(info.packageName)
                    || "android".equals(info.packageName) || "com.android.tv.settings".equals(info.packageName)
                    || "com.google.android.tungsten.setupwraith".equals(info.packageName)) continue;
            homes.add(new ComponentName(info.packageName, info.name));
        }
        if (homes.contains(saved)) return saved;
        // Prefer the real Android/Google TV home over a vendor's alternate content portal.
        for (String pkg : new String[]{"com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx"}) {
            ComponentName found = null;
            int count = 0;
            for (ComponentName home : homes) if (pkg.equals(home.getPackageName())) { found = home; count++; }
            if (count == 1) return found;
        }
        return homes.size() == 1 ? homes.iterator().next() : null;
    }

    static Intent launch(ComponentName home) {
        // CATEGORY_HOME creates another home task; a visit must behave like opening any other app.
        return Intent.makeMainActivity(home).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    }

    static boolean interceptsHome(String mode, boolean reverted, boolean configured, boolean ownsHome) {
        return "free".equals(mode) && !reverted && configured && ownsHome;
    }

    static boolean isCurrentVisit(JSONObject state, String token) {
        return state.has("stockHomeVisit") && !token.isEmpty() && token.equals(state.optString("stockHomeVisitId"));
    }

    /** Classic Android TV expects its recommendations service while the stock launcher is enabled. */
    static Set<String> keptPackages(String component) {
        Set<String> out = new HashSet<>();
        ComponentName home = ComponentName.unflattenFromString(component);
        if (home == null || "io.freemytv".equals(home.getPackageName())) return out;
        out.add(home.getPackageName());
        if ("com.google.android.tvlauncher".equals(home.getPackageName()))
            out.add("com.google.android.tvrecommendations");
        return out;
    }
}
