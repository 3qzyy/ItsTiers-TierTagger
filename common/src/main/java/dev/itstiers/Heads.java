package dev.itstiers;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 3D skin renders for the profile page, fetched on demand. */
public final class Heads {
    public static final class Entry {
        public volatile Identifier id;
        public volatile int w = 1, h = 1;
    }

    private static final Map<String, Entry> MAP = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ItsTiers-skin");
        t.setDaemon(true);
        return t;
    });
    private static final String[] SOURCES = {"https://vzge.me/bust/256/%s.png", "https://mc-heads.net/body/%s/128"};

    public static Entry get(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{1,16}")) return null;
        String key = name.toLowerCase(Locale.ROOT);
        Entry e = MAP.get(key);
        if (e != null) return e;
        Entry ne = new Entry();
        MAP.put(key, ne);
        POOL.submit(() -> load(name, key, ne));
        return ne;
    }

    private static void load(String name, String key, Entry e) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
        for (String src : SOURCES) {
            try {
                String url = String.format(src, URLEncoder.encode(name, StandardCharsets.UTF_8));
                HttpResponse<byte[]> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "ItsTiersTagger").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                if (res.statusCode() != 200) continue;
                byte[] data = res.body();
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    try {
                        NativeImage img = NativeImage.read(data);
                        Identifier id = Icons.id("skin/" + key);
                        mc.getTextureManager().register(id, new DynamicTexture(() -> "itstiers skin " + key, img));
                        e.w = img.getWidth();
                        e.h = img.getHeight();
                        e.id = id;
                    } catch (Throwable t) {
                        ItsTiersClient.LOG.warn("ItsTiers skin decode failed for {}: {}", name, t.toString());
                    }
                });
                return;
            } catch (Throwable ignored) {}
        }
    }
}