package com.meridiansafe.mobile;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** v0.3.1 adaptive WATCH/CONFIRM paper engine. No real orders are sent. */
public final class PaperEngine {
    private final Context context;
    private final SolanaScanner scanner = new SolanaScanner();
    public PaperEngine(Context c) { context = c.getApplicationContext(); }

    public void cycle() {
        String risk = BotState.p(context).getString("risk", "Aman");
        try {
            if (BotState.hasOpenPosition(context)) monitorOpen(risk);

            SolanaScanner.ScanResult r = scanner.scan(risk);
            SolanaScanner.Candidate watch = SolanaScanner.bestWatch(r);
            SolanaScanner.Candidate signal = null;

            if (!BotState.hasOpenPosition(context) && watch != null) {
                int count = BotState.observeWatch(context, watch);
                watch.confirmCount = count;
                int need = risk.equals("Aman") ? 3 : 2; // ~3 min Aman, ~2 min Seimbang/Agresif at 60s polling
                if (BotState.inCooldown(context, watch.tokenAddress)) {
                    watch.decision = "COOLDOWN";
                    watch.stage = "WAIT";
                    watch.reason = "token masih cooldown setelah trade sebelumnya";
                } else if (count >= need && confirmationHealthy(watch, risk)) {
                    watch.decision = "PAPER BUY";
                    watch.stage = "SIGNAL";
                    watch.reason = "WATCH terkonfirmasi " + count + " scan; liquidity/volume/momentum tetap sehat";
                    r.signalPass = 1;
                    signal = watch;
                } else {
                    watch.reason = "WATCH " + count + "/" + need + " • tunggu data berikutnya tetap sehat";
                }
            }

            SolanaScanner.Candidate top = signal != null ? signal : SolanaScanner.bestCandidate(r);
            BotState.p(context).edit()
                    .putString("scannerSummary", SolanaScanner.summary(r))
                    .putString("topCandidateDetail", SolanaScanner.detail(top))
                    .putString("topMint", top == null ? "" : top.tokenAddress)
                    .putString("topPool", top == null ? "" : top.poolAddress)
                    .putLong("lastScanAt", System.currentTimeMillis())
                    .putInt("scanDiscovered", r.discovered)
                    .putInt("scanSafe", r.safetyPass)
                    .putInt("scanWatch", r.watchPass)
                    .putInt("scanSignal", r.signalPass).apply();

            log(String.format(Locale.US,
                    "SCAN LIVE: %d pool | liq %d | vol %d | activity %d | safe %d | watch %d | signal %d",
                    r.discovered, r.liquidityPass, r.volumePass, r.activityPass, r.safetyPass, r.watchPass, r.signalPass));

            if (signal != null && !BotState.hasOpenPosition(context)) {
                openPaper(signal, risk);
                BotState.clearWatch(context, signal.tokenAddress);
            } else if (!BotState.hasOpenPosition(context) && watch != null) {
                log("WATCH: " + watch.symbol + " • " + watch.reason);
            } else if (!BotState.hasOpenPosition(context)) {
                log("WAIT: belum ada kandidat yang lolos menjadi WATCH.");
            }
        } catch (Exception e) {
            String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log("SCAN ERROR: " + msg + " — koneksi/API akan dicoba lagi pada siklus berikutnya.");
        }
    }

    private boolean confirmationHealthy(SolanaScanner.Candidate c, String risk) {
        if (!c.safetyPass || c.priceUsd <= 0 || c.liquidityUsd <= 0) return false;
        double minScore = risk.equals("Agresif") ? 72 : risk.equals("Seimbang") ? 76 : 78;
        double minChange5m = risk.equals("Agresif") ? -20 : risk.equals("Seimbang") ? -15 : -12;
        return c.score >= minScore && c.change5m >= minChange5m;
    }

    private void openPaper(SolanaScanner.Candidate c, String risk) {
        float balance = BotState.balance(context);
        float maxCapital = BotState.p(context).getFloat("maxCapital", 0.05f);
        float fraction = risk.equals("Agresif") ? 0.12f : risk.equals("Seimbang") ? 0.075f : 0.04f;
        float size = Math.min(maxCapital, Math.max(0.005f, balance * fraction));
        if (c.priceUsd <= 0 || c.poolAddress.isEmpty()) return;
        BotState.openPosition(context, c.poolAddress, c.tokenAddress, c.symbol, c.priceUsd, size);
        log(String.format(Locale.US,
                "PAPER ENTRY: %s | %.4f SOL | $%.10f | score %d | confirm %d | liq $%.0f | 5m %+.1f%%",
                c.symbol, size, c.priceUsd, c.score, c.confirmCount, c.liquidityUsd, c.change5m));
    }

    private void monitorOpen(String risk) {
        String pair = BotState.p(context).getString("openPair", "");
        String token = BotState.p(context).getString("openToken", "");
        String symbol = BotState.p(context).getString("openSymbol", "?");
        double entry = Double.longBitsToDouble(BotState.p(context).getLong("openEntryBits", Double.doubleToLongBits(0)));
        float size = BotState.p(context).getFloat("openSize", 0f);
        long openedAt = BotState.p(context).getLong("openAt", System.currentTimeMillis());
        if (entry <= 0 || size <= 0) { BotState.clearPosition(context); return; }

        SolanaScanner.PairMark m = scanner.markPair(pair);
        if (!m.ok) { log("POSITION " + symbol + ": harga belum terbaca (" + m.error + ")"); return; }
        double rawPct = (m.priceUsd / entry - 1.0) * 100.0;
        double costAllowance = risk.equals("Agresif") ? 0.90 : risk.equals("Seimbang") ? 0.70 : 0.55;
        double netPct = rawPct - costAllowance;
        long heldMin = Math.max(0, (System.currentTimeMillis() - openedAt) / 60000L);
        double tp = risk.equals("Agresif") ? 12.0 : risk.equals("Seimbang") ? 7.0 : 4.0;
        double sl = risk.equals("Agresif") ? -6.0 : risk.equals("Seimbang") ? -3.5 : -2.0;
        long maxHold = risk.equals("Agresif") ? 60 : risk.equals("Seimbang") ? 45 : 30;

        BotState.p(context).edit().putFloat("openNetPct", (float)netPct).apply();
        log(String.format(Locale.US,
                "POSITION %s: %+.2f%% net | $%.10f | %dm | liq $%.0f | 5m %+.1f%%",
                symbol, netPct, m.priceUsd, heldMin, m.liquidityUsd, m.priceChange5m));

        if (netPct >= tp) closePaper(symbol, token, size, netPct, "TAKE PROFIT");
        else if (netPct <= sl) closePaper(symbol, token, size, netPct, "STOP LOSS");
        else if (heldMin >= maxHold) closePaper(symbol, token, size, netPct, "TIME EXIT");
        else if (m.liquidityUsd > 0 && m.liquidityUsd < 5000) closePaper(symbol, token, size, netPct, "LIQUIDITY EXIT");
        else if (m.priceChange5m <= -35) closePaper(symbol, token, size, netPct, "MOMENTUM EXIT");
    }

    private void closePaper(String symbol, String token, float size, double netPct, String why) {
        float pnl = (float)(size * netPct / 100.0);
        float next = Math.max(0f, BotState.balance(context) + pnl);
        int t = BotState.trades(context) + 1;
        int w = BotState.wins(context), l = BotState.losses(context);
        if (pnl >= 0) w++; else l++;
        BotState.setBalance(context, next); BotState.setStats(context, t, w, l);
        BotState.setCooldown(context, token, 6 * 60 * 60 * 1000L);
        BotState.clearPosition(context);
        log(String.format(Locale.US,
                "PAPER EXIT #%d %s: %s | net %+.2f%% | PnL %+.5f SOL | cooldown 6h",
                t, symbol, why, netPct, pnl));
        if (next <= 0.90f) {
            BotState.setRunning(context, false);
            log("KILL SWITCH: paper balance turun 10% dari modal awal. Bot dihentikan.");
        }
    }

    private void log(String s) {
        String ts = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        BotState.appendLog(context, ts + " " + s);
    }
}
