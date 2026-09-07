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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only Solana market scanner.
 * Discovery: GeckoTerminal new pools.
 * Enrichment/mark: DEX Screener pair endpoint.
 * No wallet, signing, swap or blockchain write exists in this class.
 */
public final class SolanaScanner {
    private static final String NEW_POOLS =
            "https://api.geckoterminal.com/api/v2/networks/solana/new_pools?include=base_token,quote_token&page=1";
    private static final String PAIR = "https://api.dexscreener.com/latest/dex/pairs/solana/";

    private static final Set<String> TRUSTED_QUOTES = new HashSet<>();
    static {
        Collections.addAll(TRUSTED_QUOTES,
                "SOL", "WSOL", "USDC", "USDT");
    }

    public static final class Candidate {
        public String poolAddress = "";
        public String tokenAddress = "";
        public String quoteAddress = "";
        public String symbol = "?";
        public String quoteSymbol = "?";
        public String name = "?";
        public String dexId = "?";
        public double priceUsd;
        public double liquidityUsd;
        public double volume1h;
        public double volume24h;
        public int buys1h;
        public int sells1h;
        public double ageMinutes;
        public double fdv;
        public double marketCap;
        public double change5m;
        public double change1h;
        public int score;
        public String decision = "REJECT";
        public String reason = "";
        public String stage = "DISCOVERY";
        public boolean enriched;
        public boolean safetyPass;
    }

    public static final class ScanResult {
        public final List<Candidate> all = new ArrayList<>();
        public int discovered;
        public int liquidityPass;
        public int volumePass;
        public int activityPass;
        public int safetyPass;
        public int signalPass;
        public int enrichedCount;
        public String source = "GeckoTerminal + DEX Screener";
    }

    public static final class PairMark {
        public boolean ok;
        public double priceUsd;
        public double liquidityUsd;
        public double priceChange5m;
        public double priceChange1h;
        public double fdv;
        public double marketCap;
        public String dexId = "?";
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
            String baseId = relationshipId(rel, "base_token");
            String quoteId = relationshipId(rel, "quote_token");
            JSONObject ba = tokenAttrs.get(baseId);
            JSONObject qa = tokenAttrs.get(quoteId);
            if (ba != null) {
                c.symbol = ba.optString("symbol", firstSymbol(c.name));
                c.tokenAddress = ba.optString("address", stripNetworkPrefix(baseId));
                String n = ba.optString("name", "");
                if (!n.isEmpty()) c.name = n;
            } else {
                c.symbol = firstSymbol(c.name);
                c.tokenAddress = stripNetworkPrefix(baseId);
            }
            if (qa != null) {
                c.quoteSymbol = qa.optString("symbol", "?");
                c.quoteAddress = qa.optString("address", stripNetworkPrefix(quoteId));
            } else {
                c.quoteAddress = stripNetworkPrefix(quoteId);
            }

            evaluateBase(c, risk, out);
            // Enrich only candidates that already reached ACTIVITY. This limits API calls.
            if ("ACTIVITY".equals(c.stage) && !c.poolAddress.isEmpty()) {
                PairMark mark = markPair(c.poolAddress);
                if (mark.ok) {
                    out.enrichedCount++;
                    c.enriched = true;
                    c.dexId = mark.dexId;
                    c.fdv = mark.fdv;
                    c.marketCap = mark.marketCap;
                    c.change5m = mark.priceChange5m;
                    c.change1h = mark.priceChange1h;
                    if (mark.priceUsd > 0) c.priceUsd = mark.priceUsd;
                    if (mark.liquidityUsd > 0) c.liquidityUsd = mark.liquidityUsd;
                    evaluateSafetyAndSignal(c, risk, out);
                } else {
                    c.reason = "safety data belum tersedia: " + safe(mark.error);
                    c.stage = "SAFETY";
                }
            }
            out.all.add(c);
        }

        Collections.sort(out.all, (a, b) -> {
            int byScore = Integer.compare(b.score, a.score);
            if (byScore != 0) return byScore;
            return Double.compare(b.liquidityUsd, a.liquidityUsd);
        });
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
            m.dexId = p.optString("dexId", "?");
            JSONObject liq = p.optJSONObject("liquidity");
            if (liq != null) m.liquidityUsd = liq.optDouble("usd", 0);
            JSONObject pc = p.optJSONObject("priceChange");
            if (pc != null) {
                m.priceChange5m = pc.optDouble("m5", 0);
                m.priceChange1h = pc.optDouble("h1", 0);
            }
            m.fdv = p.optDouble("fdv", 0);
            m.marketCap = p.optDouble("marketCap", 0);
            m.ok = m.priceUsd > 0;
        } catch (Exception e) {
            m.error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        return m;
    }

    private void evaluateBase(Candidate c, String risk, ScanResult out) {
        double minLiq = risk.equals("Agresif") ? 12000 : risk.equals("Seimbang") ? 25000 : 50000;
        double minVol1h = risk.equals("Agresif") ? 4000 : risk.equals("Seimbang") ? 9000 : 18000;
        int minTx = risk.equals("Agresif") ? 12 : risk.equals("Seimbang") ? 20 : 35;
        double minAge = risk.equals("Agresif") ? 2 : risk.equals("Seimbang") ? 5 : 10;
        double maxAge = risk.equals("Agresif") ? 72 * 60 : risk.equals("Seimbang") ? 48 * 60 : 24 * 60;

        boolean liq = c.liquidityUsd >= minLiq;
        if (liq) out.liquidityPass++;
        boolean vol = liq && c.volume1h >= minVol1h;
        if (vol) out.volumePass++;
        int tx = c.buys1h + c.sells1h;
        boolean activity = vol && tx >= minTx && c.buys1h >= Math.max(3, (int)Math.ceil(c.sells1h * 0.75));
        if (activity) out.activityPass++;
        boolean age = c.ageMinutes >= minAge && c.ageMinutes <= maxAge;
        boolean price = c.priceUsd > 0;
        boolean token = c.tokenAddress != null && c.tokenAddress.length() >= 32;

        c.score = 0;
        if (liq) c.score += 25;
        if (vol) c.score += 20;
        if (activity) c.score += 20;
        if (age) c.score += 10;
        if (price) c.score += 5;
        if (token) c.score += 5;

        if (!liq) { c.stage = "LIQUIDITY"; c.reason = "liquidity < $" + compact(minLiq); }
        else if (!vol) { c.stage = "VOLUME"; c.reason = "volume 1h < $" + compact(minVol1h); }
        else if (!activity) { c.stage = "ACTIVITY"; c.reason = "aktivitas buy/sell belum cukup"; }
        else if (!age) { c.stage = "AGE"; c.reason = "umur pool " + ageText(c.ageMinutes) + " di luar filter"; }
        else if (!price) { c.stage = "PRICE"; c.reason = "harga belum tersedia"; }
        else if (!token) { c.stage = "TOKEN"; c.reason = "mint address tidak valid/tersedia"; }
        else { c.stage = "ACTIVITY"; c.reason = "lolos filter dasar; cek safety"; }
    }

    private void evaluateSafetyAndSignal(Candidate c, String risk, ScanResult out) {
        double maxPump5m = risk.equals("Agresif") ? 85 : risk.equals("Seimbang") ? 55 : 35;
        double maxPump1h = risk.equals("Agresif") ? 300 : risk.equals("Seimbang") ? 180 : 120;
        double maxDump5m = risk.equals("Agresif") ? -45 : risk.equals("Seimbang") ? -35 : -25;
        double minBuyRatio = risk.equals("Agresif") ? 0.45 : risk.equals("Seimbang") ? 0.50 : 0.55;
        int total = c.buys1h + c.sells1h;
        double buyRatio = total <= 0 ? 0 : c.buys1h / (double)total;
        boolean quoteTrusted = TRUSTED_QUOTES.contains(c.quoteSymbol.toUpperCase(Locale.US));
        boolean momentumOkay = c.change5m <= maxPump5m && c.change1h <= maxPump1h && c.change5m >= maxDump5m;
        boolean flowOkay = buyRatio >= minBuyRatio;
        boolean valuationOkay = c.fdv <= 0 || c.fdv >= c.liquidityUsd * 2.0;
        boolean dataOkay = c.enriched && c.priceUsd > 0 && c.liquidityUsd > 0;

        if (quoteTrusted) c.score += 5;
        if (momentumOkay) c.score += 5;
        if (flowOkay) c.score += 5;
        if (valuationOkay) c.score += 3;
        if (dataOkay) c.score += 2;

        c.score = Math.min(100, c.score);
        c.safetyPass = quoteTrusted && momentumOkay && flowOkay && valuationOkay && dataOkay;
        if (c.safetyPass) out.safetyPass++;

        int minScore = risk.equals("Agresif") ? 78 : risk.equals("Seimbang") ? 82 : 86;
        if (!quoteTrusted) { c.stage = "SAFETY"; c.reason = "quote token " + c.quoteSymbol + " bukan SOL/WSOL/USDC/USDT"; }
        else if (!momentumOkay) { c.stage = "SAFETY"; c.reason = String.format(Locale.US,"momentum ekstrem 5m %+.1f%% / 1h %+.1f%%",c.change5m,c.change1h); }
        else if (!flowOkay) { c.stage = "SAFETY"; c.reason = String.format(Locale.US,"buy ratio %.0f%% terlalu rendah",buyRatio*100); }
        else if (!valuationOkay) { c.stage = "SAFETY"; c.reason = "FDV terlalu dekat/di bawah liquidity; data anomali"; }
        else if (!dataOkay) { c.stage = "SAFETY"; c.reason = "data harga/liquidity enrichment belum lengkap"; }
        else if (c.score < minScore) { c.stage = "SCORE"; c.reason = "score " + c.score + " < minimum " + minScore; }
        else {
            c.stage = "SIGNAL";
            c.decision = "PAPER BUY";
            c.reason = "lolos liquidity + volume + activity + age + safety";
            out.signalPass++;
        }
    }

    public static String summary(ScanResult s) {
        StringBuilder b = new StringBuilder();
        b.append(String.format(Locale.US,
                "DISCOVERY %d → LIQ %d → VOL %d → ACTIVITY %d → SAFE %d → SIGNAL %d\n",
                s.discovered, s.liquidityPass, s.volumePass, s.activityPass, s.safetyPass, s.signalPass));
        b.append("Data: ").append(s.source).append(" • enriched ").append(s.enrichedCount).append("\n\n");
        int n = Math.min(12, s.all.size());
        for (int i=0;i<n;i++) {
            Candidate c = s.all.get(i);
            b.append(String.format(Locale.US,
                    "%d. %s/%s | score %d | %s\n",
                    i+1, c.symbol, c.quoteSymbol, c.score, c.decision));
            b.append(String.format(Locale.US,
                    "   liq $%s • vol1h $%s • B/S %d/%d • age %s\n",
                    compact(c.liquidityUsd), compact(c.volume1h), c.buys1h, c.sells1h, ageText(c.ageMinutes)));
            if (c.enriched) {
                b.append(String.format(Locale.US,
                        "   %s • 5m %+.1f%% • 1h %+.1f%% • MC $%s • FDV $%s\n",
                        c.dexId, c.change5m, c.change1h, compact(c.marketCap), compact(c.fdv)));
            }
            b.append("   ").append(c.stage).append(": ").append(c.reason).append("\n");
            b.append("   mint ").append(shortId(c.tokenAddress)).append(" • pool ").append(shortId(c.poolAddress)).append("\n\n");
        }
        return b.toString();
    }

    public static String detail(Candidate c) {
        if (c == null) return "Belum ada kandidat.";
        int total = c.buys1h + c.sells1h;
        double br = total == 0 ? 0 : c.buys1h * 100.0 / total;
        return String.format(Locale.US,
                "%s (%s/%s)\n%s • score %d • %s\nHarga $%.10f\nLiquidity $%s • Vol 1h $%s • Vol 24h $%s\nBuy/Sell %d/%d • buy ratio %.0f%%\nUmur %s • DEX %s\n5m %+.2f%% • 1h %+.2f%%\nMarket cap $%s • FDV $%s\nMint: %s\nPool: %s\nAlasan: %s",
                c.name, c.symbol, c.quoteSymbol, c.decision, c.score, c.stage,
                c.priceUsd, compact(c.liquidityUsd), compact(c.volume1h), compact(c.volume24h),
                c.buys1h, c.sells1h, br, ageText(c.ageMinutes), c.dexId,
                c.change5m, c.change1h, compact(c.marketCap), compact(c.fdv),
                c.tokenAddress, c.poolAddress, c.reason);
    }

    public static Candidate bestSignal(ScanResult s) {
        for (Candidate c : s.all) if ("PAPER BUY".equals(c.decision)) return c;
        return null;
    }

    public static Candidate bestCandidate(ScanResult s) {
        return s == null || s.all.isEmpty() ? null : s.all.get(0);
    }

    private static JSONObject getJson(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestMethod("GET");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "MeridianSafeMobile/0.3 paper-scanner");
        int code = c.getResponseCode();
        BufferedReader br = new BufferedReader(new InputStreamReader(
                code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        c.disconnect();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code);
        return new JSONObject(sb.toString());
    }

    private static String relationshipId(JSONObject rel, String key) {
        if (rel == null) return "";
        JSONObject x = rel.optJSONObject(key);
        JSONObject d = x == null ? null : x.optJSONObject("data");
        return d == null ? "" : d.optString("id", "");
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
        if (v <= 0) return "0";
        if (v >= 1_000_000_000) return String.format(Locale.US,"%.2fB",v/1_000_000_000d);
        if (v >= 1_000_000) return String.format(Locale.US,"%.1fM",v/1_000_000d);
        if (v >= 1_000) return String.format(Locale.US,"%.1fK",v/1_000d);
        return String.format(Locale.US,"%.0f",v);
    }
    private static String ageText(double min) {
        if (min >= 1440) return String.format(Locale.US,"%.1fd",min/1440d);
        if (min >= 60) return String.format(Locale.US,"%.1fh",min/60d);
        return String.format(Locale.US,"%.0fm",min);
    }
    private static String shortId(String s) {
        if (s == null || s.length() < 12) return s == null ? "" : s;
        return s.substring(0,6) + "…" + s.substring(s.length()-5);
    }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "unknown" : s; }
}
