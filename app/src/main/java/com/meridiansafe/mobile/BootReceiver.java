package com.meridiansafe.mobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        if (!BotState.autoRestart(context) || !BotState.running(context)) return;
        try {
            Intent s = new Intent(context, BotService.class).setAction(BotService.ACTION_START);
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(s); else context.startService(s);
            BotState.appendLog(context, "BOOT: mencoba menghidupkan kembali paper bot.");
        } catch (Exception e) {
            BotState.setRunning(context, false);
            BotState.appendLog(context, "BOOT: Android menolak auto-start. Buka aplikasi dan tekan START.");
        }
    }
}
