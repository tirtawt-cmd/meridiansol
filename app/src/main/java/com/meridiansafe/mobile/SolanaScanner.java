package com.meridiansafe.mobile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only Solana market scanner.
 * Discovery: GeckoTerminal /networks/solana/new_pools (real market data).
 * Position mark: DEX Screener pair endpoint.
 * No wallet, private key, signing or blockchain transaction code exists here.
 */
public final class SolanaScanner {
    private static final String NEW_POOLS =
            "https://api.geckoterminal.com/api/v2/networks/solana/new_pools?include=base_token,quote_token&page=1";
    private static final String PAIR = "https://api.dexscreener.com/latest/dex/pairs/solana/";

    public static final class Candidate {
        public String poolAddress = "";
        public String tokenAddress = "";
        public String symbol = "?";
        public String name = "?";
        public double priceUsd;
        public double liquidityUsd;
        public double volume1h;
        public double volume24h;
        public int buys1h;
        public int sells1h;
        public double ageMinutes;
        public int score;
        public String decision = "REJECT";
        public String reason = "";
    }

    public static final class ScanResult {
        public final List<Candidate> all = new ArrayList<>();
        public int discovered;
        public int liquidityPass;
        public int volumePass;
        public int activityPass;
        public int signalPass;
        public String source = "GeckoTerminal";
    }

    public static final class PairMark {
        public boolean ok;
        public double priceUsd;
        public double liquidityUsd;
        public double priceChange5m;
        public double priceChange1h;
        public String error = "";
    }

    public ScanResult scan(String risk) throws Exception {
        JSONObject root = getJson(NEW_POOLS);
        JSONArray data = root.optJSONArray("data");
        JSONArray included = root.optJSONArray("included");
        Map<String, JSONObject> tokenAttrs = new HashMap<>();
        if (included != null) {
            for (int i = 0; i < included.length(); i++) {
                JSONObject item = included.optJSONObject(i);
                if (item != null) tokenAttrs.put(item.optString("id", ""), item.optJSONObject("attributes"));
            }
        }

        ScanResult out = new ScanResult();
        if (data == null) return out;
        out.discovered = data.length();

        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null) continue;
            JSONObject a = item.optJSONObject("attributes");
            if (a == null) continue;

            Candidate c = new Candidate();
            c.poolAddress = normalizePoolAddress(a.optString("address", item.optString("id", "")));
            c.name = a.optString("name", "Unknown pool");
            c.priceUsd = d(a, "base_token_price_usd");
            c.liquidityUsd = d(a, "reserve_in_usd");
            c.volume1h = nestedDouble(a, "volume_usd", "h1");
            c.volume24h = nestedDouble(a, "volume_usd", "h24");
            c.buys1h = nestedInt2(a, "transactions", "h1", "buys");
            c.sells1h = nestedInt2(a, "transactions", "h1", "sells");
            c.ageMinutes = ageMinutes(a.optString("pool_created_at", ""));

            JSONObject rel = item.optJSONObject("relationships");
            String baseId = "";
            if (rel != null) {
                JSONObject base = rel.optJSONObject("base_token");
                if (base != null && base.optJSONObject("data") != null)
                    baseId = base.optJSONObject("data").optString("id", "");
            }
            JSONObject ba = tokenAttrs.get(baseId);
            if (ba != null) {
                c.symbol = ba.optString("symbol", firstSymbol(c.name));
                c.tokenAddress = ba.optString("address", stripNetworkPrefix(baseId));
                String n = ba.optString("name", "");
                if (!n.isEmpty()) c.name = n;
            } else {
                c.symbol = firstSymbol(c.name);
                c.tokenAddress = stripNetworkPrefix(baseId);
            }

            evaluate(c, risk, out);
            out.all.add(c);
        }
        Collections.sort(out.all, Comparator.comparingInt((Candidate c) -> c.score).reversed());
        return out;
    }

    public PairMark markPair(String pairAddress) {
        PairMark m = new PairMark();
        if (pairAddress == null || pairAddress.isEmpty()) { m.error = "pair kosong"; return m; }
        try {
            JSONObject root = getJson(PAIR + pairAddress);
            JSONArray pairs = root.optJSONArray("pairs");
            if (pairs == null || pairs.length() == 0) { m.error = "pair tidak ditemukan"; return m; }
            JSONObject p = pairs.optJSONObject(0);
            m.priceUsd = parse(p.optString("priceUsd", "0"));
            JSONObject liq = p.optJSONObject("liquidity");
            if (liq != null) m.liquidityUsd = liq.optDouble("usd", 0);
            JSONObject pc = p.optJSONObject("priceChange");
            if (pc != null) {
                m.priceChange5m = pc.optDouble("m5", 0);
                m.priceChange1h = pc.optDouble("h1", 0);
            }
            m.ok = m.priceUsd > 0;
        } catch (Exception e) { m.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
        return m;
    }

    private void evaluate(Candidate c, String risk, ScanResult out) {
        double minLiq = risk.equals("Agresif") ? 12000 : risk.equals("Seimbang") ? 25000 : 50000;
        double minVol1h = risk.equals("Agresif") ? 4000 : risk.equals("Seimbang") ? 9000 : 18000;
        int minTx = risk.equals("Agresif") ? 12 : risk.equals("Seimbang") ? 20 : 35;
        double minAge = risk.equals("Agresif") ? 2 : risk.equals("Seimbang") ? 5 : 10;

        boolean liq = c.liquidityUsd >= minLiq;
        if (liq) out.liquidityPass++;
        boolean vol = liq && c.volume1h >= minVol1h;
        if (vol) out.volumePass++;
        int tx = c.buys1h + c.sells1h;
        boolean activity = vol && tx >= minTx && c.buys1h >= Math.max(3, c.sells1h * 0.75);
        if (activity) out.activityPass++;
        boolean age = c.ageMinutes >= minAge && c.ageMinutes <= 72 * 60;
        boolean price = c.priceUsd > 0;

        c.score = 0;
        if (liq) c.score += 30;
        if (vol) c.score += 25;
        if (activity) c.score += 25;
        if (age) c.score += 10;
        if (price) c.score += 10;

        if (!liq) c.reason = "liquidity < $" + compact(minLiq);
        else if (!vol) c.reason = "volume 1h < $" + compact(minVol1h);
        else if (!activity) c.reason = "aktivitas buy/sell belum cukup";
        else if (!age) c.reason = "umur pool di luar filter";
        else if (!price) c.reason = "harga belum tersedia";
        else {
            c.decision = "PAPER BUY";
            c.reason = "lolos liquidity + volume + activity + age";
            out.signalPass++;
        }
    }

    public static String summary(ScanResult s) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.US,
                "DISCOVERY %d → LIQ %d → VOL %d → ACTIVITY %d → SIGNAL %d\n",
                s.discovered, s.liquidityPass, s.volumePass, s.activityPass, s.signalPass));
        int n = Math.min(10, s.all.size());
        for (int i=0;i<n;i++) {
            Candidate c = s.all.get(i);
            b.append(String.format(Locale.US,
                    "%d. %s | score %d | liq $%s | vol1h $%s | B/S %d/%d | %s\n   %s\n",
                    i+1, c.symbol, c.score, compact(c.liquidityUsd), compact(c.volume1h),
                    c.buys1h, c.sells1h, c.decision, c.reason));
        }
        return b.toString();
    }

    public static Candidate bestSignal(ScanResult s) {
        for (Candidate c : s.all) if ("PAPER BUY".equals(c.decision)) return c;
        return null;
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(15000); c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "MeridianSafeMobile/0.2 paper-scanner");
        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close(); c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return new JSONObject(sb.toString());
    }

    private static double d(JSONObject o, String k) { return parse(o.optString(k, "0")); }
    private static double nestedDouble(JSONObject o, String k1, String k2) {
        JSONObject x = o.optJSONObject(k1); return x == null ? 0 : parse(String.valueOf(x.opt(k2)));
    }
    private static int nestedInt2(JSONObject o, String k1, String k2, String k3) {
        JSONObject x = o.optJSONObject(k1); if (x == null) return 0;
        JSONObject y = x.optJSONObject(k2); return y == null ? 0 : y.optInt(k3, 0);
    }
    private static double parse(String s) { try { return Double.parseDouble(s); } catch (Exception e) { return 0; } }
    private static double ageMinutes(String iso) {
        try { return Math.max(0, (System.currentTimeMillis() - Instant.parse(iso).toEpochMilli()) / 60000.0); }
        catch (Exception e) { return 0; }
    }
    private static String firstSymbol(String poolName) {
        if (poolName == null) return "?";
        String x = poolName.split("/")[0].trim();
        return x.length() > 12 ? x.substring(0,12) : x;
    }
    private static String stripNetworkPrefix(String id) {
        if (id == null) return "";
        int p = id.indexOf('_'); return p >= 0 ? id.substring(p+1) : id;
    }
    private static String normalizePoolAddress(String id) { return stripNetworkPrefix(id); }
    private static String compact(double v) {
        if (v >= 1_000_000) return String.format(Locale.US,"%.1fM",v/1_000_000d);
        if (v >= 1_000) return String.format(Locale.US,"%.1fK",v/1_000d);
        return String.format(Locale.US,"%.0f",v);
    }
}
