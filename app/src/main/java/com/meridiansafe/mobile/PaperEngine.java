package com.meridiansafe.mobile;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public final class PaperEngine {
    private final Context context;
    private final Random random = new Random();
    public PaperEngine(Context c) { context = c.getApplicationContext(); }

    public void cycle() {
        float balance = BotState.balance(context);
        float maxCapital = BotState.p(context).getFloat("maxCapital", 0.05f);
        String risk = BotState.p(context).getString("risk", "Aman");

        double entryChance = risk.equals("Agresif") ? 0.50 : risk.equals("Seimbang") ? 0.36 : 0.24;
        if (random.nextDouble() > entryChance) {
            log("SCAN: tidak ada setup yang lolos filter (simulasi)");
            return;
        }

        float position = Math.min(maxCapital, Math.max(0.005f, balance * (risk.equals("Agresif") ? 0.12f : risk.equals("Seimbang") ? 0.075f : 0.04f)));
        double grossMove = random.nextGaussian() * (risk.equals("Agresif") ? 0.055 : risk.equals("Seimbang") ? 0.035 : 0.020);
        double fee = 0.0015 + random.nextDouble() * 0.0015;
        double netMove = grossMove - fee;
        float pnl = (float)(position * netMove);
        float next = Math.max(0f, balance + pnl);

        int t = BotState.trades(context) + 1;
        int w = BotState.wins(context);
        int l = BotState.losses(context);
        if (pnl >= 0) w++; else l++;
        BotState.setBalance(context, next);
        BotState.setStats(context, t, w, l);

        log(String.format(Locale.US,
                "PAPER #%d: size %.4f SOL | net %+.2f%% | PnL %+.5f SOL",
                t, position, netMove * 100.0, pnl));

        // Simple beginner kill-switch: stop when drawdown reaches 10% from the 1 SOL paper start.
        if (next <= 0.90f) {
            BotState.setRunning(context, false);
            log("KILL SWITCH: paper balance turun 10%. Bot dihentikan otomatis.");
        }
    }

    private void log(String s) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        BotState.appendLog(context, ts + "  " + s);
    }
}
