package io.freemytv;

import android.accessibilityservice.AccessibilityService;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.FileObserver;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * Sees the remote's keys before any app does, so the app buttons work from inside Netflix, Disney and the rest. The
 * system waits for it on every key (up to half a second), so it runs in a process of its own and does next to nothing.
 */
public class RemoteKeyService extends AccessibilityService {
    private volatile boolean homeReturn;
    private FileObserver observer;

    @Override protected void onServiceConnected() {
        refreshHomeReturn();
        observer = new FileObserver(getFilesDir().getAbsolutePath(), FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE | FileObserver.DELETE) {
            @Override public void onEvent(int event, String path) {
                if ("home-state.json".equals(path)) refreshHomeReturn();
            }
        };
        observer.startWatching();
    }

    private void refreshHomeReturn() {
        org.json.JSONObject s = HomeMode.state(this);
        homeReturn = StockHome.interceptsHome(s.optString("mode"), s.optBoolean("homeReverted"),
                s.optBoolean("stockHomeReturn"), true);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_HOME && homeReturn && ownsHome()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                            .setComponent(new ComponentName(this, HomeActivity.class))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                } catch (RuntimeException e) {
                    AppLog.w("Home return failed: " + e);
                    return false;
                }
            }
            return true;
        }
        return RemoteKeys.onKey(this, event);
    }

    private boolean ownsHome() {
        if (Build.VERSION.SDK_INT < 29) return false;
        RoleManager roles = getSystemService(RoleManager.class);
        return roles != null && roles.isRoleHeld(RoleManager.ROLE_HOME);
    }

    @Override public void onDestroy() {
        if (observer != null) observer.stopWatching();
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override public void onInterrupt() {}
}
