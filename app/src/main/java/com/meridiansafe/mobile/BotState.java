package com.meridiansafe.mobile;

import android.content.Context;
import android.content.SharedPreferences;

public final class BotState {
    private static final String PREF = "bot_state";
    private BotState() {}

    public static SharedPreferences p(Context c) { return c.getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    public static boolean running(Context c) { return p(c).getBoolean("running", false); }
    public static void setRunning(Context c, boolean v) { p(c).edit().putBoolean("running", v).apply(); }
    public static boolean autoRestart(Context c) { return p(c).getBoolean("autoRestart", false); }
    public static void setAutoRestart(Context c, boolean v) { p(c).edit().putBoolean("autoRestart", v).apply(); }
    public static float balance(Context c) { return p(c).getFloat("balance", 1.0f); }
    public static void setBalance(Context c, float v) { p(c).edit().putFloat("balance", v).apply(); }
    public static int trades(Context c) { return p(c).getInt("trades", 0); }
    public static int wins(Context c) { return p(c).getInt("wins", 0); }
    public static int losses(Context c) { return p(c).getInt("losses", 0); }
    public static void setStats(Context c, int t, int w, int l) { p(c).edit().putInt("trades",t).putInt("wins",w).putInt("losses",l).apply(); }
    public static String log(Context c) { return p(c).getString("log", "Belum ada aktivitas.\n"); }
    public static void appendLog(Context c, String line) {
        String next = line + "\n" + log(c);
        if (next.length() > 7000) next = next.substring(0, 7000);
        p(c).edit().putString("log", next).apply();
    }

    public static boolean hasOpenPosition(Context c) { return p(c).getBoolean("hasOpenPosition", false); }
    public static void openPosition(Context c, String pair, String token, String symbol, double entryUsd, float sizeSol) {
        p(c).edit().putBoolean("hasOpenPosition", true).putString("openPair", pair).putString("openToken", token)
                .putString("openSymbol", symbol).putLong("openEntryBits", Double.doubleToLongBits(entryUsd))
                .putFloat("openSize", sizeSol).putLong("openAt", System.currentTimeMillis()).putFloat("openNetPct", 0f).apply();
    }
    public static void clearPosition(Context c) {
        p(c).edit().putBoolean("hasOpenPosition", false).remove("openPair").remove("openToken").remove("openSymbol")
                .remove("openEntryBits").remove("openSize").remove("openAt").remove("openNetPct").apply();
    }

    public static boolean inCooldown(Context c, String token) {
        if (token == null || token.isEmpty()) return false;
        return System.currentTimeMillis() < p(c).getLong("cooldown_" + token, 0L);
    }
    public static void setCooldown(Context c, String token, long durationMs) {
        if (token == null || token.isEmpty()) return;
        p(c).edit().putLong("cooldown_" + token, System.currentTimeMillis() + Math.max(0L, durationMs)).apply();
    }

    /**
     * Observe a WATCH token across consecutive scans. Returns the confirmation streak.
     * Streak resets if the token disappears too long, liquidity collapses, volume collapses,
     * price dumps hard, or safety no longer passes.
     */
    public static int observeWatch(Context c, SolanaScanner.Candidate x) {
        if (x == null || x.tokenAddress == null || x.tokenAddress.isEmpty() || !x.watchReady || !x.safetyPass) return 0;
        SharedPreferences sp = p(c);
        String key = "watch_" + x.tokenAddress;
        long now = System.currentTimeMillis();
        long last = sp.getLong(key + "_at", 0L);
        int oldCount = sp.getInt(key + "_count", 0);
        float oldLiq = sp.getFloat(key + "_liq", 0f);
        float oldVol = sp.getFloat(key + "_vol", 0f);
        long oldPriceBits = sp.getLong(key + "_price", Double.doubleToLongBits(0));
        double oldPrice = Double.longBitsToDouble(oldPriceBits);

        boolean recent = last > 0 && now - last <= 3 * 60_000L;
        boolean liqStable = oldLiq <= 0 || x.liquidityUsd >= oldLiq * 0.80;
        boolean volStable = oldVol <= 0 || x.volume1h >= oldVol * 0.75;
        boolean priceStable = oldPrice <= 0 || x.priceUsd >= oldPrice * 0.80;
        boolean stable = recent && liqStable && volStable && priceStable && x.change5m > -25;
        int count = stable ? oldCount + 1 : 1;

        sp.edit().putLong(key + "_at", now).putInt(key + "_count", count)
                .putFloat(key + "_liq", (float)x.liquidityUsd).putFloat(key + "_vol", (float)x.volume1h)
                .putLong(key + "_price", Double.doubleToLongBits(x.priceUsd)).apply();
        return count;
    }

    public static void clearWatch(Context c, String token) {
        if (token == null || token.isEmpty()) return;
        String key = "watch_" + token;
        p(c).edit().remove(key + "_at").remove(key + "_count").remove(key + "_liq").remove(key + "_vol").remove(key + "_price").apply();
    }

    public static void reset(Context c) {
        p(c).edit().putFloat("balance",1.0f).putInt("trades",0).putInt("wins",0).putInt("losses",0)
                .putString("log","Paper account reset.\n").apply();
        clearPosition(c);
    }
}
