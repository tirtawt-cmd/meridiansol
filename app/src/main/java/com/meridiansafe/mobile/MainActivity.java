package com.meridiansafe.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView status, balance, pnl, trades, logText, scannerText, positionText, candidateText, lastScanText;
    private Spinner riskSpinner;
    private EditText maxCapital;
    private CheckBox autoRestart;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() { render(); handler.postDelayed(this, 1500); }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);

        status = findViewById(R.id.status);
        balance = findViewById(R.id.balance);
        pnl = findViewById(R.id.pnl);
        trades = findViewById(R.id.trades);
        logText = findViewById(R.id.logText);
        scannerText = findViewById(R.id.scannerText);
        candidateText = findViewById(R.id.candidateText);
        lastScanText = findViewById(R.id.lastScanText);
        positionText = findViewById(R.id.positionText);
        riskSpinner = findViewById(R.id.riskSpinner);
        maxCapital = findViewById(R.id.maxCapital);
        autoRestart = findViewById(R.id.autoRestart);

        String[] risks = {"Aman", "Seimbang", "Agresif"};
        ArrayAdapter<String> a = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, risks);
        riskSpinner.setAdapter(a);
        String savedRisk = BotState.p(this).getString("risk", "Aman");
        for (int i=0;i<risks.length;i++) if (risks[i].equals(savedRisk)) riskSpinner.setSelection(i);
        maxCapital.setText(String.format(Locale.US,"%.3f", BotState.p(this).getFloat("maxCapital",0.05f)));
        autoRestart.setChecked(BotState.autoRestart(this));

        findViewById(R.id.startButton).setOnClickListener(v -> startBot());
        findViewById(R.id.stopButton).setOnClickListener(v -> send(BotService.ACTION_STOP));
        findViewById(R.id.emergencyButton).setOnClickListener(v -> send(BotService.ACTION_EMERGENCY));
        findViewById(R.id.solscanButton).setOnClickListener(v -> openTop("https://solscan.io/token/", "topMint"));
        findViewById(R.id.dexButton).setOnClickListener(v -> openTop("https://dexscreener.com/solana/", "topPool"));
        autoRestart.setOnCheckedChangeListener((b1, checked) -> BotState.setAutoRestart(this, checked));
        render();
    }

    private void openTop(String base, String key) {
        String id = BotState.p(this).getString(key, "");
        if (id == null || id.isEmpty()) {
            Toast.makeText(this, "Belum ada kandidat untuk dibuka.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(base + id)));
    }

    private void startBot() {
        float cap = 0.05f;
        try { cap = Float.parseFloat(maxCapital.getText().toString()); } catch (Exception ignored) {}
        cap = Math.max(0.005f, Math.min(cap, 0.25f));
        BotState.p(this).edit().putFloat("maxCapital",cap).putString("risk", String.valueOf(riskSpinner.getSelectedItem())).apply();
        Intent s = new Intent(this, BotService.class).setAction(BotService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s); else startService(s);
    }

    private void send(String action) {
        Intent s = new Intent(this, BotService.class).setAction(action);
        startService(s);
    }

    private void render() {
        boolean r = BotState.running(this);
        status.setText(r ? "STATUS: RUNNING • PAPER" : "STATUS: STOPPED");
        status.setTextColor(Color.parseColor(r ? "#166534" : "#991B1B"));
        float b = BotState.balance(this);
        balance.setText(String.format(Locale.US,"Paper balance: %.4f SOL", b));
        pnl.setText(String.format(Locale.US,"PnL: %+.4f SOL (%+.2f%%)", b-1f, (b-1f)*100f));
        trades.setText(String.format(Locale.US,"Trades: %d • Win: %d • Loss: %d", BotState.trades(this), BotState.wins(this), BotState.losses(this)));
        logText.setText(BotState.log(this));
        scannerText.setText(BotState.p(this).getString("scannerSummary", "Belum ada scan. Tekan START PAPER BOT."));
        candidateText.setText(BotState.p(this).getString("topCandidateDetail", "Belum ada kandidat."));
        long at = BotState.p(this).getLong("lastScanAt", 0L);
        if (at > 0) lastScanText.setText("Last scan: " + new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date(at)) + " • polling ±60 detik");
        else lastScanText.setText("Last scan: belum ada");
        if (BotState.hasOpenPosition(this)) {
            String sym = BotState.p(this).getString("openSymbol", "?");
            float size = BotState.p(this).getFloat("openSize", 0f);
            float net = BotState.p(this).getFloat("openNetPct", 0f);
            positionText.setText(String.format(Locale.US,"OPEN PAPER: %s • %.4f SOL • mark %+.2f%%", sym, size, net));
        } else positionText.setText("OPEN PAPER: tidak ada posisi");
    }

    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
}
