package dev.itstiers;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.FloatSize;
import com.github.weisj.jsvg.view.ViewBox;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Mode icons. Standard modes and known LTMs ship inside the jar; any LTM that shows up later on the
 * website gets its icon downloaded into config/itstiers/icons and served through a generated resource
 * pack, so new LTMs work without a mod update.
 */
public final class Icons {
    private static final String[] BUNDLED = {"crystal", "uhc", "pot", "nethpot", "smp", "sword", "axe", "mace", "chaosmace", "spearmace",
            "overall", "ltm", "ltm_creeper", "ltm_debuff", "ltm_diamond_op", "ltm_diamond_smp", "ltm_lightspeed_mace", "ltm_speed"};
    private static final Map<String, Integer> BUNDLED_INDEX = new HashMap<>();
    static { for (int i = 0; i < BUNDLED.length; i++) BUNDLED_INDEX.put(BUNDLED[i], i); }

    public static final Identifier FONT_ICONS = id("icons");
    public static final Identifier FONT_DYN = id("dyn");

    private static final Map<String, Integer> dynIndex = new ConcurrentHashMap<>();
    private static final Set<String> failed = ConcurrentHashMap.newKeySet();
    private static volatile Set<String> loaded = Set.of();
    public static volatile boolean needsReload;

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("itstiers", path); }

    public static Path iconDir() { return mk(Config.dir().resolve("icons")); }
    public static Path packDir() { return mk(Config.dir().resolve("pack")); }
    private static Path mk(Path p) { try { Files.createDirectories(p); } catch (Exception ignored) {} return p; }

    private static String bundledName(String key) {
        return key.startsWith("ltm:") ? "ltm_" + key.substring(4).replace('-', '_') : key;
    }

    /** Texture for a mode key ("sword", "ltm:creeper", "overall"). */
    public static Identifier tex(String key) {
        if (key.startsWith("ltm:") && loaded.contains(key.substring(4))) return id("textures/dyn/" + key.substring(4) + ".png");
        String b = bundledName(key);
        return id("textures/icon/" + (BUNDLED_INDEX.containsKey(b) ? b : "ltm") + ".png");
    }

    /** Inline text glyph for nametags and the tab list. */
    public static MutableComponent glyph(String key) {
        Identifier font = FONT_ICONS;
        int cp;
        Integer dyn = key.startsWith("ltm:") && loaded.contains(key.substring(4)) ? dynIndex.get(key.substring(4)) : null;
        if (dyn != null) { font = FONT_DYN; cp = 0xE000 + dyn; }
        else {
            Integer b = BUNDLED_INDEX.get(bundledName(key));
            cp = 0xE000 + (b != null ? b : BUNDLED_INDEX.get("ltm"));
        }
        return Component.literal(String.valueOf((char) cp)).setStyle(Style.EMPTY.withFont(new FontDescription.Resource(font)).withColor(0xFFFFFF));
    }

    // ---- dynamic pack -------------------------------------------------------------------------

    public static void init() {
        try { rebuildPack(); } catch (Exception e) { ItsTiersClient.LOG.warn("ItsTiers icon pack init failed", e); }
    }

    /** Called by the pack source right before resources (re)load. */
    public static void onRepoReload() {
        Set<String> s = new HashSet<>();
        Path tex = packDir().resolve("assets/itstiers/textures/dyn");
        if (Files.isDirectory(tex)) try (Stream<Path> st = Files.list(tex)) {
            st.forEach(p -> { String n = p.getFileName().toString(); if (n.endsWith(".png")) s.add(n.substring(0, n.length() - 4)); });
        } catch (Exception ignored) {}
        loaded = s;
    }

    private static synchronized void rebuildPack() throws Exception {
        Path pack = packDir();
        int fmt = SharedConstants.RESOURCE_PACK_FORMAT_MAJOR;
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\":{\"description\":\"ItsTiers downloaded LTM icons\",\"pack_format\":" + fmt + ",\"min_format\":" + fmt + ",\"max_format\":" + fmt + "}}",
                StandardCharsets.UTF_8);
        Path tex = mk(pack.resolve("assets/itstiers/textures/dyn"));
        Path gly = mk(pack.resolve("assets/itstiers/textures/dyn_glyph"));
        Path font = mk(pack.resolve("assets/itstiers/font"));

        Path indexFile = iconDir().resolve("index.json");
        if (dynIndex.isEmpty() && Files.exists(indexFile)) {
            try {
                JsonObject o = JsonParser.parseString(Files.readString(indexFile)).getAsJsonObject();
                for (String k : o.keySet()) dynIndex.put(k, o.get(k).getAsInt());
            } catch (Exception ignored) {}
        }
        TreeMap<String, Path> icons = new TreeMap<>();
        try (Stream<Path> st = Files.list(iconDir())) {
            st.forEach(p -> {
                String n = p.getFileName().toString();
                if (n.endsWith(".png") && n.substring(0, n.length() - 4).matches("[a-z0-9][a-z0-9-]{0,31}")) icons.put(n.substring(0, n.length() - 4), p);
            });
        }
        StringBuilder providers = new StringBuilder();
        for (Map.Entry<String, Path> e : icons.entrySet()) {
            String id = e.getKey();
            BufferedImage src;
            try { src = ImageIO.read(e.getValue().toFile()); } catch (Exception ex) { src = null; }
            if (src == null) continue;
            ImageIO.write(scale(src, 64), "png", tex.resolve(id + ".png").toFile());
            ImageIO.write(scale(src, 32), "png", gly.resolve(id + ".png").toFile());
            Integer idx = dynIndex.get(id);
            if (idx == null) {
                int next = 0;
                for (int v : dynIndex.values()) next = Math.max(next, v + 1);
                idx = next;
                dynIndex.put(id, idx);
            }
            if (providers.length() > 0) providers.append(",");
            providers.append("{\"type\":\"bitmap\",\"file\":\"itstiers:dyn_glyph/").append(id).append(".png\",\"ascent\":8,\"height\":9,\"chars\":[\"\\u")
                    .append(String.format("%04X", 0xE000 + idx)).append("\"]}");
        }
        Path fontFile = font.resolve("dyn.json");
        if (providers.length() > 0) Files.writeString(fontFile, "{\"providers\":[" + providers + "]}", StandardCharsets.UTF_8);
        else Files.deleteIfExists(fontFile);
        JsonObject o = new JsonObject();
        for (Map.Entry<String, Integer> e : dynIndex.entrySet()) o.addProperty(e.getKey(), e.getValue());
        Files.writeString(indexFile, o.toString(), StandardCharsets.UTF_8);
    }

    private static BufferedImage scale(BufferedImage src, int size) {
        BufferedImage cur = src;
        // step down in halves for a clean result from large sources
        while (cur.getWidth() > size * 2 && cur.getHeight() > size * 2) cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2, false);
        return draw(cur, size, size, true);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h, boolean fit) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (fit) {
            double s = Math.min(w / (double) src.getWidth(), h / (double) src.getHeight());
            int dw = Math.max(1, (int) Math.round(src.getWidth() * s)), dh = Math.max(1, (int) Math.round(src.getHeight() * s));
            g.drawImage(src, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        } else g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Runs on the data thread after every successful fetch: downloads icons of LTMs we have never seen. */
    public static void sync(List<TierData.Ltm> ltms) {
        boolean changed = false;
        HttpClient http = null;
        for (TierData.Ltm l : ltms) {
            if (BUNDLED_INDEX.containsKey("ltm_" + l.id.replace('-', '_'))) continue;
            Path file = iconDir().resolve(l.id + ".png");
            if (Files.exists(file) || l.icon == null || !l.icon.startsWith("https://") || failed.contains(l.id)) continue;
            try {
                if (http == null) http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
                HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(l.icon)).timeout(Duration.ofSeconds(20))
                        .header("User-Agent", "ItsTiersTagger").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
                BufferedImage img = decode(res.body());
                if (img == null) throw new IllegalStateException("unsupported image");
                ImageIO.write(scale(img, 128), "png", file.toFile());
                changed = true;
                ItsTiersClient.LOG.info("ItsTiers: downloaded icon for new LTM '{}' -> {}", l.id, file);
            } catch (Throwable t) {
                failed.add(l.id);
                ItsTiersClient.LOG.warn("ItsTiers: could not download icon for LTM '{}': {}", l.id, t.toString());
            }
        }
        if (changed) {
            try { rebuildPack(); needsReload = true; } catch (Exception e) { ItsTiersClient.LOG.warn("ItsTiers icon pack rebuild failed", e); }
        }
    }

    private static BufferedImage decode(byte[] data) throws Exception {
        String head = new String(data, 0, Math.min(data.length, 256), StandardCharsets.UTF_8).trim();
        if (head.startsWith("<")) {
            SVGDocument doc = new SVGLoader().load(new ByteArrayInputStream(data), null, LoaderContext.createDefault());
            if (doc == null) return null;
            FloatSize sz = doc.size();
            float s = 256f / Math.max(1f, Math.max(sz.width, sz.height));
            int w = Math.max(1, Math.round(sz.width * s)), h = Math.max(1, Math.round(sz.height * s));
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            doc.render(null, g, new ViewBox(0, 0, w, h));
            g.dispose();
            return img;
        }
        return ImageIO.read(new ByteArrayInputStream(data));
    }
}
