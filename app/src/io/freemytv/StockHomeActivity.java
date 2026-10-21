package io.freemytv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;

/** Foreground bridge in the main process: shares HomeMode's lock with mode jobs, and never launches after leaving. */
public class StockHomeActivity extends Activity {
    private AlertDialog dialog;
    private boolean active;
    private Intent ready;
    private boolean completed, failed;
    private final String visitId = Long.toString(android.os.SystemClock.elapsedRealtimeNanos());

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(Ui.tr("Início original", "Stock home"))
                .setMessage(Ui.tr("Abrindo o início original…", "Opening stock home…"))
                .setNegativeButton(android.R.string.cancel, (d, i) -> finish())
                .setOnCancelListener(d -> finish()).show();
        new Thread(() -> {
            Intent result = null;
            try { result = HomeMode.prepareStockHome(getApplicationContext(), visitId); }
            catch (Exception e) { AppLog.w("stock home shortcut: " + e); }
            final Intent intent = result;
            runOnUiThread(() -> {
                ready = intent;
                completed = true;
                if (isFinishing() || isDestroyed())
                    ModeJob.schedule(getApplicationContext(), ModeJob.STOCK_RETURN, visitId, 0);
                else if (active) open();
            });
        }, "stock-home").start();
    }

    @Override protected void onResume() {
        super.onResume();
        active = true;
        if (completed && !failed) open();
    }

    @Override protected void onPause() {
        active = false;
        super.onPause();
        // Home/Back while enabling cancels the visit; a late result must not steal focus.
        finish();
    }

    private void open() {
        if (ready != null) {
            try { startActivity(ready); finish(); return; }
            catch (RuntimeException e) { AppLog.w("stock home launch: " + e); }
        }
        failed = true;
        dialog.setMessage(Ui.tr("Não foi possível abrir o início original. Se estiver desativado, mantenha a depuração USB ligada e autorizada para o Free My TV.",
                "Could not open stock home. If it is disabled, keep USB debugging enabled and authorized for Free My TV."));
    }

    @Override protected void onDestroy() {
        if (dialog != null) dialog.dismiss();
        super.onDestroy();
    }
}
