package com.meridiansafe.mobile;

import android.content.Context;
import android.content.SharedPreferences;

public final class BotState {
    private static final String PREF = "bot_state";
    private BotState() {}

    public static SharedPreferences p(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
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
        String current = log(c);
        String next = line + "\n" + current;
        if (next.length() > 7000) next = next.substring(0, 7000);
        p(c).edit().putString("log", next).apply();
    }
    public static void reset(Context c) {
        p(c).edit().putFloat("balance",1.0f).putInt("trades",0).putInt("wins",0).putInt("losses",0).putString("log","Paper account reset.\n").apply();
    }
}
