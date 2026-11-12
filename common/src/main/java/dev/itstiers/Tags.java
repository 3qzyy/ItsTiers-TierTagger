package dev.itstiers;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Map;

/** Builds the tier tag that is put next to player names. */
public final class Tags {
    private static final int[] TIER_COLORS = {0xFFCB6B, 0xD6E4F0, 0xE8A35C, 0x6FD3E8, 0x8FA7B8};

    public static final int RETIRED_COLOR = 0x9A8FB8;

    public static int tierColor(String tier) {
        int i = TierData.tierIndex(tier);
        if (i < 0) return 0xAAAAAA;
        int c = TIER_COLORS[i / 2];
        if (i % 2 == 1) {
            int r = (int) (((c >> 16) & 255) * 0.82f), g = (int) (((c >> 8) & 255) * 0.82f), b = (int) ((c & 255) * 0.82f);
            c = r << 16 | g << 8 | b;
        }
        return c;
    }

    /** Which mode key is shown for this player under the given setting, or null. */
    public static String pick(TierData.Player p, String mode) {
        if (!"best".equals(mode)) {
            TierData.Result r = p.get(mode);
            return r != null && r.shown() != null ? mode : null;
        }
        String best = null;
        int bi = 99;
        for (Map.Entry<String, TierData.Result> e : p.tiers.entrySet()) {
            int i = TierData.tierIndex(e.getValue().shown());
            if (i >= 0 && i < bi) { bi = i; best = e.getKey(); }
        }
        return best;
    }

    public static String label(TierData.Result r) {
        String s = r.shown();
        return s == null ? null : (r.retired ? "R" + s : s);
    }

    public static Component decorate(Component original, String name, boolean tab) {
        try {
            Config c = Config.I;
            if (original == null || name == null || (tab ? !c.tablist : !c.nametag)) return null;
            TierData.Player p = TierData.find(name);
            if (p == null) return null;
            String key = pick(p, c.displayMode);
            if (key == null) return null;
            TierData.Result r = p.get(key);
            MutableComponent tier = Component.literal(label(r)).setStyle(Style.EMPTY.withColor(r.retired ? RETIRED_COLOR : tierColor(r.shown())).withBold(true).withStrikethrough(false));
            MutableComponent sep = Component.literal(" | ").setStyle(Style.EMPTY.withColor(0x7F8C99));
            MutableComponent out = Component.empty();
            if ("right".equals(c.side)) {
                out.append(original).append(sep).append(tier);
                if (c.icons) out.append(" ").append(Icons.glyph(key));
            } else {
                if (c.icons) out.append(Icons.glyph(key)).append(" ");
                out.append(tier).append(sep).append(original);
            }
            return out;
        } catch (Throwable t) {
            return null;
        }
    }
}