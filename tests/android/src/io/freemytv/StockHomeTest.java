package io.freemytv;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

final class StockHomeTest {
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static ActivityInfo home(String pkg, boolean system) {
        ActivityInfo info = new ActivityInfo();
        info.packageName = pkg;
        info.name = pkg + ".MainActivity";
        info.exported = true;
        info.applicationInfo = new ApplicationInfo();
        info.applicationInfo.flags = system ? ApplicationInfo.FLAG_SYSTEM : 0;
        return info;
    }
    static int run() throws Exception {
        checks = 0;
        ActivityInfo stock = home("com.google.android.tvlauncher", true);
        ActivityInfo vendor = home("vendor.portal", true);
        ActivityInfo free = home("io.freemytv", false);
        ComponentName chosen = StockHome.select(Arrays.asList(free, vendor, stock), "");
        check(chosen.getPackageName().equals(stock.packageName), "find factory HOME when Free My TV was chosen in system settings");
        check(StockHome.select(Arrays.asList(stock, vendor), "vendor.portal/.MainActivity").getPackageName().equals("vendor.portal"), "prefer recorded original even with multiple HOME apps");
        check(StockHome.select(Arrays.asList(free, stock), "io.freemytv/.MainActivity").equals(chosen), "never recurse into Free My TV");
        check(StockHome.select(Collections.emptyList(), "") == null, "missing factory launcher does not fall back to implicit HOME");
        check(StockHome.select(Arrays.asList(free, home("third.party", false)), "") == null, "third-party launchers are not factory home");
        ActivityInfo setup = home("com.google.android.tungsten.setupwraith", true);
        check(StockHome.select(Arrays.asList(free, setup), "") == null, "never open setup wizard");
        check(StockHome.select(Arrays.asList(home("com.android.tv.settings", true)), "") == null, "missing stock home must not open system FallbackHome");
        vendor.exported = false;
        check(StockHome.select(Arrays.asList(vendor, stock), "vendor.portal/.MainActivity").equals(chosen), "reject non-exported activity");
        vendor.exported = true;
        check(StockHome.select(Arrays.asList(vendor, home("other.vendor", true)), "") == null, "do not guess between unknown system homes");
        check(StockHome.select(Arrays.asList(stock, stock), "invalid").equals(chosen), "duplicate query entries are deduplicated; invalid saved component ignored");
        // Disabled apps remain candidates so HomeMode can enable them before launch.
        stock.enabled = false;
        stock.applicationInfo.enabled = false;
        check(StockHome.select(Arrays.asList(stock), "").equals(chosen), "disabled factory launcher remains discoverable");
        Intent intent = StockHome.launch(chosen);
        check(chosen.equals(intent.getComponent()) && Intent.ACTION_MAIN.equals(intent.getAction())
                && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && !intent.hasCategory(Intent.CATEGORY_HOME),
                "visit exact factory activity as a regular task so Home returns to the default launcher");
        check((intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) != 0
                && (intent.getFlags() & Intent.FLAG_ACTIVITY_CLEAR_TASK) == 0, "open independent task without clearing tasks");
        Set<String> disabled = new HashSet<>(Arrays.asList(stock.packageName, "com.google.android.tvrecommendations", "vendor.promo"));
        disabled.removeAll(StockHome.keptPackages(chosen.flattenToString()));
        check(disabled.equals(Collections.singleton("vendor.promo")), "temporary visit touches stock launcher and recommendation service only");
        check(StockHome.keptPackages("").isEmpty() && StockHome.keptPackages("io.freemytv/.HomeActivity").isEmpty(), "no exception before shortcut activation or for our own home");
        check(StockHome.keptPackages("vendor.portal/.MainActivity").equals(Collections.singleton("vendor.portal")), "vendor HOME keeps only its own package");
        check(StockHome.interceptsHome("free", false, true, true), "temporary Home interception allowed only for our active default");
        check(!StockHome.interceptsHome("original", false, true, true), "Original never intercepts Home");
        check(!StockHome.interceptsHome("free", true, true, true), "watchdog restoration never intercepts Home");
        check(!StockHome.interceptsHome("free", false, false, true), "no Home interception outside a visit");
        check(!StockHome.interceptsHome("free", false, true, false), "respect a different default chosen in system settings");
        org.json.JSONObject visit = new org.json.JSONObject().put("stockHomeVisit", new org.json.JSONArray()).put("stockHomeVisitId", "new");
        check(StockHome.isCurrentVisit(visit, "new"), "current visit can be restored after return or cancellation");
        check(!StockHome.isCurrentVisit(visit, "old"), "late cancelled preparation cannot close a newer visit");
        visit.remove("stockHomeVisit");
        check(!StockHome.isCurrentVisit(visit, "new"), "completed visit cannot run cleanup twice");
        return checks;
    }
}
