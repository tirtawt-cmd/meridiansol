package com.meridiansafe.mobile;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Paper engine driven by real Solana market observations. No real orders are sent. */
public final class PaperEngine {
    private final Context context;
    private final SolanaScanner scanner = new SolanaScanner();
    public PaperEngine(Context c) { context = c.getApplicationContext(); }

    public void cycle() {
        String risk = BotState.p(context).getString("risk", "Aman");
        try {
            if (BotState.hasOpenPosition(context)) {
                monitorOpen(risk);
            }
            SolanaScanner.ScanResult r = scanner.scan(risk);
            BotState.p(context).edit()
                    .putString("scannerSummary", SolanaScanner.summary(r))
                    .putLong("lastScanAt", System.currentTimeMillis())
                    .putInt("scanDiscovered", r.discovered)
                    .putInt("scanSignal", r.signalPass).apply();
            log(String.format(Locale.US,
                    "SCAN LIVE: %d pool Solana | liq %d | vol %d | activity %d | signal %d",
                    r.discovered, r.liquidityPass, r.volumePass, r.activityPass, r.signalPass));

            if (!BotState.hasOpenPosition(context)) {
                SolanaScanner.Candidate best = SolanaScanner.bestSignal(r);
                if (best != null) openPaper(best, risk);
                else log("WAIT: belum ada token yang lolos seluruh filter.");
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log("SCAN ERROR: " + msg + " — koneksi/API akan dicoba lagi pada siklus berikutnya.");
        }
    }

    private void openPaper(SolanaScanner.Candidate c, String risk) {
        float balance = BotState.balance(context);
        float maxCapital = BotState.p(context).getFloat("maxCapital", 0.05f);
        float fraction = risk.equals("Agresif") ? 0.12f : risk.equals("Seimbang") ? 0.075f : 0.04f;
        float size = Math.min(maxCapital, Math.max(0.005f, balance * fraction));
        if (c.priceUsd <= 0 || c.poolAddress.isEmpty()) return;
        BotState.openPosition(context, c.poolAddress, c.tokenAddress, c.symbol, c.priceUsd, size);
        log(String.format(Locale.US,
                "PAPER ENTRY: %s | size %.4f SOL | entry $%.10f | score %d | liq $%.0f",
                c.symbol, size, c.priceUsd, c.score, c.liquidityUsd));
    }

    private void monitorOpen(String risk) {
        String pair = BotState.p(context).getString("openPair", "");
        String symbol = BotState.p(context).getString("openSymbol", "?");
        double entry = Double.longBitsToDouble(BotState.p(context).getLong("openEntryBits", Double.doubleToLongBits(0)));
        float size = BotState.p(context).getFloat("openSize", 0f);
        long openedAt = BotState.p(context).getLong("openAt", System.currentTimeMillis());
        if (entry <= 0 || size <= 0) { BotState.clearPosition(context); return; }

        SolanaScanner.PairMark m = scanner.markPair(pair);
        if (!m.ok) { log("POSITION " + symbol + ": harga belum terbaca (" + m.error + ")"); return; }
        double rawPct = (m.priceUsd / entry - 1.0) * 100.0;
        // Paper cost allowance for both sides: 0.60%, approximating fees/slippage without pretending exact execution.
        double netPct = rawPct - 0.60;
        long heldMin = Math.max(0, (System.currentTimeMillis() - openedAt) / 60000L);
        double tp = risk.equals("Agresif") ? 12.0 : risk.equals("Seimbang") ? 7.0 : 4.0;
        double sl = risk.equals("Agresif") ? -6.0 : risk.equals("Seimbang") ? -3.5 : -2.0;
        long maxHold = risk.equals("Agresif") ? 60 : risk.equals("Seimbang") ? 45 : 30;

        BotState.p(context).edit().putFloat("openNetPct", (float)netPct).apply();
        log(String.format(Locale.US,
                "POSITION %s: %+.2f%% net | $%.10f | held %dm | liq $%.0f",
                symbol, netPct, m.priceUsd, heldMin, m.liquidityUsd));

        if (netPct >= tp) closePaper(symbol, size, netPct, "TAKE PROFIT");
        else if (netPct <= sl) closePaper(symbol, size, netPct, "STOP LOSS");
        else if (heldMin >= maxHold) closePaper(symbol, size, netPct, "TIME EXIT");
        else if (m.liquidityUsd > 0 && m.liquidityUsd < 5000) closePaper(symbol, size, netPct, "LIQUIDITY EXIT");
    }

    private void closePaper(String symbol, float size, double netPct, String why) {
        float pnl = (float)(size * netPct / 100.0);
        float next = Math.max(0f, BotState.balance(context) + pnl);
        int t = BotState.trades(context) + 1;
        int w = BotState.wins(context), l = BotState.losses(context);
        if (pnl >= 0) w++; else l++;
        BotState.setBalance(context, next);
        BotState.setStats(context, t, w, l);
        BotState.clearPosition(context);
        log(String.format(Locale.US,
                "PAPER EXIT #%d %s: %s | net %+.2f%% | PnL %+.5f SOL",
                t, symbol, why, netPct, pnl));
        if (next <= 0.90f) {
            BotState.setRunning(context, false);
            log("KILL SWITCH: paper balance turun 10% dari modal awal. Bot dihentikan.");
        }
    }

    private void log(String s) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        BotState.appendLog(context, ts + "  " + s);
    }
}
