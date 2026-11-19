package dev.itstiers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Live ItsTiers data. Everything is read from website-tiers.json, nothing is hardcoded but the standard modes. */
public final class TierData {
    public static final String URL = "https://itsmoritzzz787.github.io/ItsTiers/website-tiers.json";
    public static final String[] MODES = {"crystal", "uhc", "pot", "nethpot", "smp", "sword", "axe", "mace", "chaosmace", "spearmace"};
    public static final String[] MODE_NAMES = {"Crystal", "UHC", "Pot", "NethPot", "SMP", "Sword", "Axe", "Mace", "ChaosMace", "Spear Mace"};
    public static final String[] TIER_ORDER = {"HT1", "LT1", "HT2", "LT2", "HT3", "LT3", "HT4", "LT4", "HT5", "LT5"};
    private static final int[] POINTS = {60, 45, 30, 20, 10, 6, 4, 3, 2, 1};
    public static final int[] TITLE_MIN = {400, 250, 100, 50, 20, 10, 0};
    public static final String[] TITLE_ID = {"grandmaster", "master", "ace", "specialist", "cadet", "novice", "rookie"};
    public static final String[] TITLE_NAME = {"Combat Grandmaster", "Combat Master", "Combat Ace", "Combat Specialist", "Combat Cadet", "Combat Novice", "Rookie"};

    public static final class Result {
        public final String tier;      // current tier, may be Unranked / Retest
        public final String peak;      // best of tier and peakTier, or null
        public final boolean retired;
        public final int points;
        Result(String tier, String peakTier, boolean retired) {
            this.tier = tier;
            int a = tierIndex(tier), b = tierIndex(peakTier);
            int best = a < 0 ? b : (b < 0 ? a : Math.min(a, b));
            this.peak = best < 0 ? null : TIER_ORDER[best];
            this.retired = retired;
            this.points = best < 0 ? 0 : POINTS[best];
        }
        public boolean ranked() { return tierIndex(tier) >= 0; }
        /** Tier to show: current one, or the peak for retired players. */
        public String shown() { return retired ? (peak != null ? peak : (ranked() ? tier : null)) : (ranked() ? tier : null); }
    }

    public static final class Player {
        public String name;
        public String region = "";
        public final Map<String, Result> tiers = new LinkedHashMap<>(); // key: mode id or "ltm:<id>"
        public int points;
        public int rank;
        public boolean easterEgg;
        public Result get(String key) { return tiers.get(key); }
    }

    public static final class Ltm {
        public String id, name, icon;
        public boolean active;
        public String key() { return "ltm:" + id; }
    }

    public static final class Snapshot {
        public final List<Player> ranking = new ArrayList<>();
        public final Map<String, Player> byName = new HashMap<>();
        public final List<Ltm> ltms = new ArrayList<>();
        public boolean live;
    }

    private static volatile Snapshot snap = new Snapshot();
    private static volatile long lastFetch;
    private static volatile boolean fetching;

    public static Snapshot get() { return snap; }

    public static int tierIndex(String t) {
        if (t == null) return -1;
        for (int i = 0; i < TIER_ORDER.length; i++) if (TIER_ORDER[i].equals(t)) return i;
        return -1;
    }

    public static int titleIndex(int points) {
        for (int i = 0; i < TITLE_MIN.length; i++) if (points >= TITLE_MIN[i]) return i;
        return TITLE_MIN.length - 1;
    }

    public static String modeName(String key) {
        if (key.startsWith("ltm:")) {
            for (Ltm l : snap.ltms) if (l.key().equals(key)) return l.name;
            return key.substring(4);
        }
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(key)) return MODE_NAMES[i];
        return key;
    }

    public static Player find(String name) {
        return name == null ? null : snap.byName.get(name.toLowerCase(Locale.ROOT));
    }

    /** The Tqopl easter egg: HT1 everywhere, no rank, no points, never part of any ranking. */
    public static Player easterEgg() {
        Player p = new Player();
        p.name = "Tqopl";
        p.region = "???";
        p.easterEgg = true;
        for (String m : MODES) p.tiers.put(m, new Result("HT1", "HT1", false));
        for (Ltm l : snap.ltms) p.tiers.put(l.key(), new Result("HT1", "HT1", false));
        return p;
    }

    public static void refreshAsync(boolean force) {
        long now = System.currentTimeMillis();
        if (fetching || (!force && now - lastFetch < 5 * 60_000L)) return;
        fetching = true;
        lastFetch = now;
        Thread t = new Thread(() -> {
            try { fetch(); } finally { fetching = false; }
        }, "ItsTiers-fetch");
        t.setDaemon(true);
        t.start();
    }

    private static void fetch() {
        Path cache = Config.dir().resolve("cache.json");
        String body = null;
        boolean live = false;
        try {
            HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "?t=" + System.currentTimeMillis() / 60000))
                    .timeout(Duration.ofSeconds(15)).header("User-Agent", "ItsTiersTagger").GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 200) { body = res.body(); live = true; }
        } catch (Exception e) {
            ItsTiersClient.LOG.warn("ItsTiers fetch failed: {}", e.toString());
        }
        try {
            if (body == null && Files.exists(cache)) body = Files.readString(cache, StandardCharsets.UTF_8);
            if (body == null) return;
            Snapshot s = parse(body);
            s.live = live;
            snap = s;
            if (live) Files.writeString(cache, body, StandardCharsets.UTF_8);
            ItsTiersClient.LOG.info("ItsTiers data loaded: {} players, {} LTMs (live={})", s.ranking.size(), s.ltms.size(), live);
            Icons.sync(s.ltms);
        } catch (Exception e) {
            ItsTiersClient.LOG.warn("ItsTiers data parse failed", e);
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static Result result(JsonObject o) {
        JsonElement r = o.get("retired");
        return new Result(str(o, "tier"), str(o, "peakTier"), r != null && r.isJsonPrimitive() && r.getAsBoolean());
    }

    static Snapshot parse(String json) {
        Snapshot s = new Snapshot();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray players = root.has("players") ? root.getAsJsonArray("players") : new JsonArray();
        for (JsonElement el : players) {
            JsonObject o = el.getAsJsonObject();
            String name = str(o, "minecraft");
            if (name == null || name.isBlank()) continue;
            Player p = new Player();
            p.name = name;
            String reg = str(o, "region");
            if (reg != null) p.region = reg;
            if (o.has("tiers") && o.get("tiers").isJsonObject()) {
                JsonObject t = o.getAsJsonObject("tiers");
                for (String m : MODES) if (t.has(m) && t.get(m).isJsonObject()) p.tiers.put(m, result(t.getAsJsonObject(m)));
            }
            s.byName.put(name.toLowerCase(Locale.ROOT), p);
        }
        if (root.has("ltm") && root.get("ltm").isJsonObject()) {
            JsonObject ltm = root.getAsJsonObject("ltm");
            Map<String, Ltm> defs = new LinkedHashMap<>();
            if (ltm.has("definitions")) for (JsonElement el : ltm.getAsJsonArray("definitions")) {
                JsonObject o = el.getAsJsonObject();
                Ltm l = new Ltm();
                l.id = str(o, "id"); l.name = str(o, "name"); l.icon = str(o, "icon");
                if (l.id == null || !l.id.matches("[a-z0-9][a-z0-9-]{0,31}")) continue;
                if (l.name == null) l.name = l.id;
                defs.put(l.id, l);
            }
            long now = System.currentTimeMillis();
            List<JsonObject> act = new ArrayList<>();
            if (ltm.has("activeLtms") && ltm.get("activeLtms").isJsonArray()) for (JsonElement el : ltm.getAsJsonArray("activeLtms")) act.add(el.getAsJsonObject());
            else if (ltm.has("active") && ltm.get("active").isJsonObject()) act.add(ltm.getAsJsonObject("active"));
            for (JsonObject a : act) {
                Ltm l = defs.get(str(a, "id"));
                if (l == null) continue;
                try {
                    String ends = str(a, "endsAt");
                    l.active = ends == null || Instant.parse(ends).toEpochMilli() > now;
                } catch (Exception e) { l.active = true; }
            }
            s.ltms.addAll(defs.values());
            s.ltms.sort((a, b) -> a.active != b.active ? (a.active ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
            if (ltm.has("players")) for (JsonElement el : ltm.getAsJsonArray("players")) {
                JsonObject o = el.getAsJsonObject();
                String name = str(o, "minecraft");
                if (name == null || !o.has("results")) continue;
                Player p = s.byName.get(name.toLowerCase(Locale.ROOT));
                if (p == null) {
                    p = new Player();
                    p.name = name;
                    s.byName.put(name.toLowerCase(Locale.ROOT), p);
                }
                String reg = str(o, "region");
                if (p.region.isEmpty() && reg != null) p.region = reg;
                JsonObject r = o.getAsJsonObject("results");
                for (String id : r.keySet()) if (defs.containsKey(id) && r.get(id).isJsonObject()) p.tiers.put("ltm:" + id, result(r.getAsJsonObject(id)));
            }
        }
        for (Player p : s.byName.values()) {
            int sum = 0;
            for (Result r : p.tiers.values()) sum += r.points;
            p.points = sum;
            s.ranking.add(p);
        }
        Collections.sort(s.ranking, (a, b) -> b.points != a.points ? b.points - a.points : a.name.compareToIgnoreCase(b.name));
        for (int i = 0; i < s.ranking.size(); i++) s.ranking.get(i).rank = i + 1;
        return s;
    }
}
