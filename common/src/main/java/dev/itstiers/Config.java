package dev.itstiers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public boolean nametag = true;
    public boolean tablist = true;
    public boolean icons = true;
    public boolean glass = true;
    public boolean performance = false;
    public boolean dark = true;
    public String side = "left";
    public String displayMode = "best";
    public String language = "en";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static Config I = new Config();

    public static Path dir() {
        Path p = FabricLoader.getInstance().getConfigDir().resolve("itstiers");
        try { Files.createDirectories(p); } catch (Exception ignored) {}
        return p;
    }

    public static void load() {
        try {
            Path f = dir().resolve("config.json");
            if (Files.exists(f)) {
                Config c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) {
            ItsTiersClient.LOG.warn("Could not read config", e);
        }
        if (I.side == null) I.side = "left";
        if (I.displayMode == null) I.displayMode = "best";
        if (I.language == null) I.language = "en";
        L.load(I.language);
    }

    public static void save() {
        try {
            Files.writeString(dir().resolve("config.json"), GSON.toJson(I), StandardCharsets.UTF_8);
        } catch (Exception e) {
            ItsTiersClient.LOG.warn("Could not save config", e);
        }
    }
}
