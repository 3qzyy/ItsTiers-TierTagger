package dev.itstiers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Mod-local translations, independent from the game language. */
public final class L {
    public static final String[] CODES = {"en", "pl", "de"};
    private static Map<String, String> cur = new HashMap<>();
    private static Map<String, String> en = new HashMap<>();

    private static Map<String, String> read(String code) {
        Map<String, String> m = new HashMap<>();
        try (InputStream in = L.class.getResourceAsStream("/itstiers_lang/" + code + ".json")) {
            if (in == null) return m;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (String k : o.keySet()) m.put(k, o.get(k).getAsString());
        } catch (Exception ignored) {}
        return m;
    }

    public static void load(String code) {
        if (en.isEmpty()) en = read("en");
        cur = "en".equals(code) ? en : read(code);
    }

    public static String t(String key) {
        String v = cur.get(key);
        if (v == null) v = en.get(key);
        return v == null ? key : v;
    }

    public static String t(String key, Object... args) {
        String s = t(key);
        for (int i = 0; i < args.length; i++) s = s.replace("{" + i + "}", String.valueOf(args[i]));
        return s;
    }
}
