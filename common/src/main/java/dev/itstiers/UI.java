package dev.itstiers;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** The whole ItsTiers screen: layout, liquid-glass drawing, spring animations and input. Version independent. */
public final class UI {
    // ---- themes -------------------------------------------------------------------------------
    private static final class Theme {
        int bgTop, bgBot, win, winSolid, glass, solid, hi, line, text, muted, acc, acc2, ht, lt, on, grid, dim;
    }
    private static final Theme DARK = new Theme(), LIGHT = new Theme();
    static {
        DARK.bgTop = 0x0A2A44; DARK.bgBot = 0x04121F; DARK.win = 0x700A2C46; DARK.winSolid = 0xFF082338;
        DARK.glass = 0x2E6CC6EA; DARK.solid = 0xFF0E3350; DARK.hi = 0x30FFFFFF; DARK.line = 0x8C8CE1FF;
        DARK.text = 0xFFE6F8FF; DARK.muted = 0xFF7FB9D0; DARK.acc = 0xFF35D6E6; DARK.acc2 = 0xFF6AF0C8;
        DARK.ht = 0xFFFFCB6B; DARK.lt = 0xFF7FDCFF; DARK.on = 0xFF03202B; DARK.grid = 0x308CDCFF; DARK.dim = 0x2204121F;
        LIGHT.bgTop = 0xEAFAFF; LIGHT.bgBot = 0xBFE9F5; LIGHT.win = 0x90EAF9FF; LIGHT.winSolid = 0xFFD6EFF7;
        LIGHT.glass = 0x80FFFFFF; LIGHT.solid = 0xFFEEF9FD; LIGHT.hi = 0x80FFFFFF; LIGHT.line = 0xF0FFFFFF;
        LIGHT.text = 0xFF06324A; LIGHT.muted = 0xFF3D7C96; LIGHT.acc = 0xFF0892B4; LIGHT.acc2 = 0xFF0AA98A;
        LIGHT.ht = 0xFFB86E00; LIGHT.lt = 0xFF0779A0; LIGHT.on = 0xFFFFFFFF; LIGHT.grid = 0x38086E96; LIGHT.dim = 0x18FFFFFF;
    }

    private static final Identifier LOGO = Icons.id("textures/logo.png");
    private static final int TABS = 5, T_RANK = 0, T_MODES = 1, T_LTM = 2, T_PROFILE = 3, T_SETTINGS = 4;
    private static final String[] TAB_KEYS = {"tab.ranking", "tab.modes", "tab.ltm", "tab.profile", "tab.settings"};

    // ---- state --------------------------------------------------------------------------------
    private Theme T = DARK;
    private boolean perf, glass;
    private float A = 1f;                 // global alpha multiplier
    private final float[] aStack = new float[16];
    private int aTop;
    private long last = System.nanoTime();
    private float dt, time, openT, tabT;
    private int tab, tabDir = 1;
    private String query;                 // profile nick
    private TierData.Player egg;
    private int selMode = 5, selLtm = 0;
    private final float[] scrollTarget = new float[TABS];
    private final Map<String, float[]> springs = new HashMap<>();
    private final List<float[]> hitRects = new ArrayList<>();
    private final List<Runnable> hitActions = new ArrayList<>();
    private int mx, my;
    private int clipX0, clipY0, clipX1, clipY1;
    private boolean clipOn;
    private final float[][] bubbles = new float[26][];
    private Gfx g;

    public UI(String user) {
        Random r = new Random(787);
        for (int i = 0; i < bubbles.length; i++) bubbles[i] = new float[]{r.nextFloat(), r.nextFloat(), 0.35f + r.nextFloat() * 1.4f, r.nextFloat() * 6.28f};
        if (user != null && !user.isBlank()) { query = user; tab = T_PROFILE; }
        else {
            try { query = Minecraft.getInstance().getUser().getName(); } catch (Throwable t) { query = null; }
            tab = T_RANK;
        }
    }

    public boolean wantsBlur() { return Config.I.glass && !Config.I.performance; }

    public void debugTab(int t) { setTab(t); }
    public void debugProfile(String name) { openProfile(name); }

    // ---- animation helpers ---------------------------------------------------------------------
    private static float clamp(float v, float a, float b) { return v < a ? a : Math.min(v, b); }

    /** Under-damped step response: overshoots and settles, the "liquid" bounce used everywhere. */
    private static float bounce(float t) {
        if (t <= 0) return 0;
        return (float) (1 - Math.exp(-6.5 * t) * Math.cos(10.5 * t));
    }

    private float spring(String key, float target, float k, float c) {
        float[] s = springs.get(key);
        if (s == null) { s = new float[]{target, 0, time}; springs.put(key, s); return target; }
        if (s[2] != time) {
            s[2] = time;
            if (perf) { s[0] += (target - s[0]) * Math.min(1, dt * 18); s[1] = 0; }
            else {
                float h = dt / 4f;
                for (int i = 0; i < 4; i++) { s[1] += (k * (target - s[0]) - c * s[1]) * h; s[0] += s[1] * h; }
            }
        }
        return s[0];
    }

    private void kick(String key, float v) {
        float[] s = springs.get(key);
        if (s != null) s[1] += v;
    }

    private void seed(String key, float value) { springs.put(key, new float[]{value, 0, time}); }

    // ---- colour / primitive helpers -------------------------------------------------------------
    private int a(int c) {
        int al = (int) (((c >>> 24) & 255) * A);
        return (Math.max(0, Math.min(255, al)) << 24) | (c & 0xFFFFFF);
    }
    private static int alpha(int c, float f) { return ((int) clamp(((c >>> 24) & 255) * f, 0, 255) << 24) | (c & 0xFFFFFF); }
    private static int rgb(int c, int al) { return (al << 24) | (c & 0xFFFFFF); }

    private void pushA(float mul) { aStack[aTop++] = A; A *= mul; }
    private void popA() { A = aStack[--aTop]; }

    private void fill(int x0, int y0, int x1, int y1, int c) { if (((a(c) >>> 24) & 255) > 2) g.fill(x0, y0, x1, y1, a(c)); }

    /** Rounded rectangle built from pixel rows. */
    private void rr(int x, int y, int w, int h, int r, int fill, int border) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (perf) r = Math.min(r, 2);
        int[] ins = new int[r];
        for (int i = 0; i < r; i++) { double d = r - i - 0.5; ins[i] = (int) Math.round(r - Math.sqrt(r * r - d * d)); }
        if ((fill >>> 24) != 0) {
            for (int i = 0; i < r; i++) {
                fill(x + ins[i], y + i, x + w - ins[i], y + i + 1, fill);
                fill(x + ins[i], y + h - i - 1, x + w - ins[i], y + h - i, fill);
            }
            fill(x, y + r, x + w, y + h - r, fill);
        }
        if ((border >>> 24) != 0) {
            int top = r > 0 ? ins[0] : 0;
            fill(x + top, y, x + w - top, y + 1, border);
            fill(x + top, y + h - 1, x + w - top, y + h, border);
            for (int i = 1; i < r; i++) {
                int a0 = ins[i], a1 = Math.max(ins[i] + 1, ins[i - 1]);
                fill(x + a0, y + i, x + a1, y + i + 1, border);
                fill(x + w - a1, y + i, x + w - a0, y + i + 1, border);
                fill(x + a0, y + h - i - 1, x + a1, y + h - i, border);
                fill(x + w - a1, y + h - i - 1, x + w - a0, y + h - i, border);
            }
            fill(x, y + Math.max(r, 1), x + 1, y + h - Math.max(r, 1), border);
            fill(x + w - 1, y + Math.max(r, 1), x + w, y + h - Math.max(r, 1), border);
        }
    }

    /** A liquid-glass (or flat, in performance mode) panel. */
    private void panel(int x, int y, int w, int h, int r) {
        rr(x, y, w, h, r, glass ? T.glass : T.solid, T.line);
        if (glass && h > 6) {
            int hh = Math.min(h / 2, 9);
            g.grad(x + r, y + 1, x + w - r, y + 1 + hh, a(T.hi), a(T.hi & 0xFFFFFF));
            fill(x + r, y + h - 2, x + w - r, y + h - 1, T.dim);
        }
    }

    private void line(float x1, float y1, float x2, float y2, int col) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.5f || ((a(col) >>> 24) & 255) <= 2) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x1, y1);
        p.rotate((float) Math.atan2(dy, dx));
        g.fill(0, 0, Math.max(1, Math.round(len)), 1, a(col));
        p.popMatrix();
    }

    private void dot(float x, float y, int size, int col) {
        if (((a(col) >>> 24) & 255) <= 2) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x - size / 2f, y - size / 2f);
        g.fill(0, 0, size, size, a(col));
        p.popMatrix();
    }

    private void text(String s, int x, int y, int col, boolean bold) { if (((a(col) >>> 24) & 255) > 6) g.text(s, x, y, a(col), bold); }
    private void textR(String s, int xRight, int y, int col, boolean bold) { text(s, xRight - g.textW(s, bold), y, col, bold); }
    private void textC(String s, int cx, int y, int col, boolean bold) { text(s, cx - g.textW(s, bold) / 2, y, col, bold); }

    private void textS(String s, float x, float y, float sc, int col, boolean bold) {
        if (((a(col) >>> 24) & 255) <= 6) return;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(sc, sc);
        g.text(s, 0, 0, a(col), bold);
        p.popMatrix();
    }

    private String fit(String s, int maxW, boolean bold) {
        if (g.textW(s, bold) <= maxW) return s;
        while (s.length() > 1 && g.textW(s + "..", bold) > maxW) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    private void icon(Identifier id, float cx, float cy, int size, float spin, int tint) {
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(cx, cy);
        float sx = (float) Math.abs(Math.cos(spin));
        p.scale(Math.max(0.04f, sx), 1f);
        g.tex(id, -size / 2, -size / 2, size, size, a(tint));
        p.popMatrix();
    }

    private void clip(int x0, int y0, int x1, int y1) { g.clip(x0, y0, x1, y1); clipOn = true; clipX0 = x0; clipY0 = y0; clipX1 = x1; clipY1 = y1; }
    private void unclip() { g.unclip(); clipOn = false; }

    private boolean hov(int x, int y, int w, int h) {
        if (openT < 0.25f) return false;
        if (clipOn && (mx < clipX0 || mx >= clipX1 || my < clipY0 || my >= clipY1)) return false;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void hit(int x, int y, int w, int h, Runnable r) {
        int x0 = x, y0 = y, x1 = x + w, y1 = y + h;
        if (clipOn) { x0 = Math.max(x0, clipX0); y0 = Math.max(y0, clipY0); x1 = Math.min(x1, clipX1); y1 = Math.min(y1, clipY1); }
        if (x1 <= x0 || y1 <= y0) return;
        hitRects.add(new float[]{x0, y0, x1, y1});
        hitActions.add(r);
    }

    /** Starts a popped-in, hover-reactive element. Returns false (and draws nothing) while it is still waiting for its turn. */
    private boolean begin(String key, int x, int y, int w, int h, float delay, boolean interactive) {
        float t = tabT - delay;
        if (!perf && t <= 0) return false;
        float b = perf ? 1 : bounce(t * 1.25f);
        float hv = interactive ? spring("h:" + key, hov(x, y, w, h) ? 1 : 0, 300, 15) : 0;
        float press = spring("p:" + key, 0, 380, 13);
        float sx = b * (1 + 0.045f * hv) * (1 + press), sy = (b > 1 ? 1 - (b - 1) * 0.7f : b) * (1 + 0.045f * hv) * (1 - press * 0.6f);
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        float cx = x + w / 2f, cy = y + h / 2f;
        p.translate(cx, cy - 1.5f * hv);
        p.scale(Math.max(0.01f, sx), Math.max(0.01f, sy));
        p.translate(-cx, -cy);
        pushA(perf ? 1 : clamp(t * 6, 0, 1));
        return true;
    }

    private void end() { popA(); g.pose().popMatrix(); }

    private void press(String key) { kick("p:" + key, -1.6f); }

    // ---- frame --------------------------------------------------------------------------------
    public void render(Gfx gfx, int mouseX, int mouseY) {
        g = gfx; mx = mouseX; my = mouseY;
        long now = System.nanoTime();
        dt = clamp((now - last) / 1e9f, 0.0005f, 0.05f);
        last = now;
        time += dt; openT += dt; tabT += dt;
        T = Config.I.dark ? DARK : LIGHT;
        perf = Config.I.performance;
        glass = Config.I.glass && !perf;
        A = 1; aTop = 0; clipOn = false;
        hitRects.clear(); hitActions.clear();

        int sw = g.width(), sh = g.height();
        backdrop(sw, sh);

        int W = Math.min(sw - 12, 480), H = Math.min(sh - 10, 300);
        int x0 = (sw - W) / 2, y0 = (sh - H) / 2;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        if (!perf) {
            float bx = bounce(openT * 1.2f), by = bounce(openT * 1.2f - 0.06f);
            p.translate(sw / 2f, sh / 2f);
            p.scale(0.72f + 0.28f * bx, 0.6f + 0.4f * by);
            p.translate(-sw / 2f, -sh / 2f);
            pushA(clamp(openT * 5, 0, 1));
        } else pushA(1);

        window(x0, y0, W, H);
        header(x0, y0, W);
        tabs(x0 + 8, y0 + 34, W - 16);
        content(x0 + 8, y0 + 56, W - 16, H - 64);

        popA();
        p.popMatrix();
    }

    private void backdrop(int sw, int sh) {
        int al = glass ? (Config.I.dark ? 0xB4 : 0x96) : 0xF4;
        g.grad(0, 0, sw, sh, rgb(T.bgTop, al), rgb(T.bgBot, al));
        if (perf) return;
        float px = (mx - sw / 2f) / sw, py = (my - sh / 2f) / sh;
        // perspective grid floor
        float hor = sh * 0.56f - py * 10, vx = sw / 2f - px * 30;
        float phase = (time * 0.35f) % 1f;
        for (int i = 0; i < 9; i++) {
            float z = (i + phase) / 9f;
            float yy = hor + (sh - hor) * z * z;
            fill(0, (int) yy, sw, (int) yy + 1, alpha(T.grid, 0.25f + z));
        }
        for (int k = -9; k <= 9; k++) line(vx + k * sw * 0.035f, hor, vx + k * sw * 0.22f, sh, alpha(T.grid, 0.8f));
        // scan band
        float sy = ((time * 0.16f) % 1.3f) * sh - sh * 0.15f;
        g.grad(0, (int) sy, sw, (int) sy + 26, a(T.grid & 0xFFFFFF), a(alpha(T.grid, 0.9f)));
        g.grad(0, (int) sy + 26, sw, (int) sy + 52, a(alpha(T.grid, 0.9f)), a(T.grid & 0xFFFFFF));
        // bubbles floating in depth
        for (float[] b : bubbles) {
            float z = b[2];
            float yy = ((b[1] - time * 0.035f / z) % 1f + 1f) % 1f;
            float xx = b[0] + (float) Math.sin(time * 0.6f + b[3]) * 0.012f;
            int size = Math.max(2, Math.round(11 / z));
            int bx = (int) (xx * sw - px * 26 / z), by = (int) (yy * (sh + 40) - 20 - py * 18 / z);
            int ca = (int) clamp(70 / z, 14, 90);
            rr(bx, by, size, size, size / 2, rgb(Config.I.dark ? 0x9FE9FF : 0xFFFFFF, ca / 3), rgb(Config.I.dark ? 0x9FE9FF : 0xFFFFFF, ca));
            if (size > 5) fill(bx + size / 4, by + size / 4, bx + size / 4 + 2, by + size / 4 + 1, rgb(0xFFFFFF, ca + 40));
        }
    }

    private void window(int x0, int y0, int W, int H) {
        rr(x0, y0, W, H, 9, glass ? T.win : T.winSolid, T.line);
        if (glass) g.grad(x0 + 9, y0 + 1, x0 + W - 9, y0 + 22, a(T.hi), a(T.hi & 0xFFFFFF));
        // HUD corner brackets
        int c = T.acc;
        fill(x0 + 9, y0 - 2, x0 + 30, y0 - 1, c); fill(x0 - 2, y0 + 9, x0 - 1, y0 + 30, c);
        fill(x0 + W - 30, y0 + H + 1, x0 + W - 9, y0 + H + 2, c); fill(x0 + W + 1, y0 + H - 30, x0 + W + 2, y0 + H - 9, c);
        fill(x0 + W - 30, y0 - 2, x0 + W - 9, y0 - 1, alpha(c, 0.5f)); fill(x0 + 9, y0 + H + 1, x0 + 30, y0 + H + 2, alpha(c, 0.5f));
        if (glass) {
            // travelling sheen
            float t = (time * 0.22f) % 1.6f;
            int sx = x0 - 60 + (int) (t * (W + 120));
            clip(x0 + 1, y0 + 1, x0 + W - 1, y0 + H - 1);
            for (int row = 0; row < H; row += 6) {
                int xx = sx - row / 3;
                fill(xx, y0 + row, xx + 22, y0 + row + 6, rgb(0xFFFFFF, Config.I.dark ? 0x0C : 0x26));
            }
            unclip();
        }
    }

    private int pill(int xRight, int y, String s, int dot) {
        int w = g.textW(s, false) + 12 + (dot != 0 ? 8 : 0);
        int x = xRight - w;
        panel(x, y, w, 14, 7);
        int tx = x + 6;
        if (dot != 0) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(time * 5);
            rr(x + 5, y + 5, 5, 5, 2, alpha(dot, pulse), 0);
            tx += 8;
        }
        text(s, tx, y + 3, T.text, false);
        return w;
    }

    private void header(int x0, int y0, int W) {
        float bob = perf ? 0 : (float) Math.sin(time * 2.1f) * 1.2f;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(0, bob);
        g.tex(LOGO, x0 + 10, y0 + 6, 69, 23, a(0xFFFFFFFF));
        p.popMatrix();

        int xr = x0 + W - 10;
        // close
        boolean ch = hov(xr - 14, y0 + 9, 14, 14);
        float cs = spring("close", ch ? 1 : 0, 300, 14);
        p.pushMatrix();
        p.translate(xr - 7, y0 + 16);
        p.scale(1 + 0.15f * cs, 1 + 0.15f * cs);
        p.translate(-(xr - 7), -(y0 + 16));
        panel(xr - 14, y0 + 9, 14, 14, 7);
        line(xr - 10, y0 + 13, xr - 4, y0 + 19, T.text);
        line(xr - 10, y0 + 19, xr - 4, y0 + 13, T.text);
        p.popMatrix();
        hit(xr - 14, y0 + 9, 14, 14, () -> ScreenAccess.set(null));
        xr -= 18;

        TierData.Snapshot s = TierData.get();
        String live = s.ranking.isEmpty() ? L.t("status.loading") : (s.live ? L.t("status.live", s.ranking.size()) : L.t("status.cached", s.ranking.size()));
        xr -= pill(xr, y0 + 9, live, s.live ? T.acc2 : T.ht) + 4;
        StringBuilder act = new StringBuilder();
        for (TierData.Ltm l : s.ltms) if (l.active) { if (act.length() > 0) act.append(", "); act.append(l.name); }
        if (act.length() > 0 && xr - x0 > 200) pill(xr, y0 + 9, L.t("status.ltm", act), 0);
    }

    private void setTab(int t) {
        if (t == tab) return;
        tabDir = t > tab ? 1 : -1;
        tab = t;
        tabT = 0;
        kick("tabpill", (float) tabDir * 60);
    }

    private void tabs(int x, int y, int w) {
        panel(x, y, w, 16, 8);
        float tw = (w - 4) / (float) TABS;
        float px = spring("tabpill", x + 2 + tab * tw, 210, 15);
        float[] sp = springs.get("tabpill");
        float stretch = perf || sp == null ? 0 : Math.min(8, Math.abs(sp[1]) * 0.012f);
        int pw = Math.round(tw + stretch), ph = Math.round(12 - stretch * 0.25f);
        rr(Math.round(px - stretch / 2), y + 2 + (12 - ph) / 2, pw, ph, 6, T.acc, 0);
        if (glass) g.grad(Math.round(px) + 6, y + 3, Math.round(px) + pw - 6, y + 8, a(0x50FFFFFF), a(0x00FFFFFF));
        for (int i = 0; i < TABS; i++) {
            int tx = Math.round(x + 2 + i * tw);
            boolean h = hov(tx, y, Math.round(tw), 16);
            float hs = spring("tab" + i, h ? 1 : 0, 300, 14);
            String label = L.t(TAB_KEYS[i]);
            int col = i == tab ? T.on : (h ? T.text : T.muted);
            textS(label, tx + tw / 2f - g.textW(label, true) / 2f, y + 4 - hs, 1f, col, true);
            final int idx = i;
            hit(tx, y, Math.round(tw), 16, () -> setTab(idx));
        }
    }

    private void content(int x, int y, int w, int h) {
        String sk = "scroll" + tab;
        float sc = spring(sk, scrollTarget[tab], 170, 21);
        float slide = perf ? 0 : (1 - bounce(tabT * 1.5f)) * 26 * tabDir;
        clip(x, y, x + w, y + h);
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(slide, 0);
        pushA(perf ? 1 : clamp(tabT * 7, 0, 1));
        int top = y - Math.round(sc);
        int used;
        switch (tab) {
            case T_RANK -> used = ranking(x, top, w, y, y + h);
            case T_MODES -> used = modeBoard(x, top, w, false);
            case T_LTM -> used = modeBoard(x, top, w, true);
            case T_PROFILE -> used = profile(x, top, w, h);
            default -> used = settings(x, top, w);
        }
        popA();
        p.popMatrix();
        float maxScroll = Math.max(0, used - h);
        if (scrollTarget[tab] > maxScroll) scrollTarget[tab] = maxScroll;
        if (scrollTarget[tab] < 0) scrollTarget[tab] = 0;
        if (maxScroll > 0) {
            int bh = Math.max(14, Math.round(h * (h / (float) used)));
            int by = y + Math.round((h - bh) * clamp(sc / maxScroll, 0, 1));
            rr(x + w - 2, by, 2, bh, 1, alpha(T.acc, 0.7f), 0);
        }
        unclip();
    }

    // ---- shared bits ---------------------------------------------------------------------------
    private int resCol(TierData.Result r) { return r.retired ? (Config.I.dark ? 0xFFB3A6D9 : 0xFF6A5A99) : tierCol(r.shown()); }

    private int tierCol(String tier) { return tier != null && tier.startsWith("HT") ? T.ht : T.lt; }

    private void bar(int x, int y, int w, int h, float frac, float t) {
        rr(x, y, w, h, h / 2, T.grid, 0);
        float f = clamp(frac * (perf ? 1 : bounce(t)), 0, 1.06f);
        int bw = Math.round(w * Math.min(1f, f));
        if (bw > 0) {
            rr(x, y, bw, h, h / 2, T.acc, 0);
            if (bw > 4) rr(x + bw / 2, y, bw - bw / 2, h, h / 2, T.acc2, 0);
        }
    }

    private void section(String title, String right, int x, int y, int w) {
        fill(x, y + 3, x + 2, y + 9, T.acc);
        text(title, x + 6, y + 2, T.text, true);
        if (right != null) textR(right, x + w, y + 2, T.muted, false);
    }

    private TierData.Player current() {
        if (query == null) return null;
        if (query.equalsIgnoreCase("Tqopl")) {
            if (egg == null || egg.tiers.size() != TierData.MODES.length + TierData.get().ltms.size()) egg = TierData.easterEgg();
            return egg;
        }
        return TierData.find(query);
    }

    private void openProfile(String name) {
        query = name;
        scrollTarget[T_PROFILE] = 0;
        if (tab == T_PROFILE) tabT = 0; else setTab(T_PROFILE);
    }

    // ---- profile -------------------------------------------------------------------------------
    private int profile(int x, int y, int w, int viewH) {
        TierData.Player pl = current();
        TierData.Snapshot snap = TierData.get();
        if (pl == null) {
            if (begin("nf", x, y + 20, w, 60, 0, false)) {
                panel(x, y + 20, w, 60, 8);
                String msg = snap.ranking.isEmpty() ? L.t("status.loading") : (query == null ? L.t("profile.none") : L.t("profile.notfound", query));
                textC(msg, x + w / 2, y + 38, T.text, true);
                textC(L.t("profile.hint"), x + w / 2, y + 52, T.muted, false);
                end();
            }
            return 90;
        }
        int lw = Math.round(w * 0.6f) - 2, rw = w - lw - 4, ch = 82;
        // --- identity card ---
        if (begin("pc", x, y, lw, ch, 0, false)) {
            panel(x, y, lw, ch, 8);
            int bx = x + 8, by = y + 9, bw = 58, bh = 64;
            rr(bx, by, bw, bh, 7, pl.easterEgg ? 0xFF160305 : alpha(T.glass, 1.3f), pl.easterEgg ? 0xFFFF3040 : T.line);
            if (!perf) rings(bx + bw / 2f, by + bh / 2f, false);
            if (pl.easterEgg) monster(bx, by, bw, bh);
            else skin(pl.name, bx, by, bw, bh);
            if (!perf) rings(bx + bw / 2f, by + bh / 2f, true);

            int ix = x + 74, iw = lw - 74 - 8;
            String nm = fit(pl.name, (int) (iw / 1.5f) - 4, true);
            textS(nm, ix, y + 8, 1.5f, pl.easterEgg ? 0xFFFF4A5A : T.text, true);
            int nx = ix + Math.round(g.textW(nm, true) * 1.5f) + 5;
            String sub = pl.easterEgg ? "??? · #?" : (pl.region.isEmpty() ? "" : pl.region + " · ") + "#" + pl.rank + " " + L.t("profile.overall");
            if (nx + g.textW(sub, false) < x + lw - 6) text(sub, nx, y + 12, T.muted, false);

            int ti = TierData.titleIndex(pl.points);
            float frac = 1;
            String next = L.t("profile.maxrank");
            if (pl.easterEgg) next = L.t("egg.sub");
            else if (ti > 0) {
                int lo = TierData.TITLE_MIN[ti], hi = TierData.TITLE_MIN[ti - 1];
                frac = (pl.points - lo) / (float) (hi - lo);
                next = L.t("profile.next", TierData.TITLE_NAME[ti - 1], hi - pl.points);
            }
            String title = pl.easterEgg ? "Clubstep Monster" : TierData.TITLE_NAME[ti];
            if (!pl.easterEgg) icon(Icons.id("textures/rank/" + TierData.TITLE_ID[ti] + ".png"), ix + 6, y + 30, 12, perf ? 0 : (1 - bounce(tabT - 0.2f)) * 6.28f, 0xFFFFFFFF);
            int tx = ix + (pl.easterEgg ? 0 : 15);
            text(title, tx, y + 26, pl.easterEgg ? 0xFFFF4A5A : T.ht, true);
            int nw = x + lw - 8 - (tx + g.textW(title, true) + 6);
            if (nw > 30) text(fit(next, nw, false), tx + g.textW(title, true) + 6, y + 26, T.muted, false);
            bar(ix, y + 39, iw, 4, pl.easterEgg ? 1 : frac, tabT - 0.25f);

            int ranked = 0, ltmRanked = 0, best = 99;
            for (Map.Entry<String, TierData.Result> e : pl.tiers.entrySet()) {
                int i = TierData.tierIndex(e.getValue().shown());
                if (i < 0) continue;
                if (e.getKey().startsWith("ltm:")) ltmRanked++; else ranked++;
                best = Math.min(best, i);
            }
            String[] labels = {L.t("stat.points"), L.t("stat.best"), L.t("stat.ranked"), L.t("stat.ltm")};
            String[] values = {pl.easterEgg ? "—" : String.valueOf(pl.points), best < 99 ? TierData.TIER_ORDER[best] : "—",
                    ranked + " / " + TierData.MODES.length, String.valueOf(ltmRanked)};
            int cw = iw / 4;
            for (int i = 0; i < 4; i++) {
                text(labels[i], ix + i * cw, y + 50, T.muted, false);
                float pb = perf ? 1 : bounce((tabT - 0.2f - i * 0.07f) * 1.3f);
                textS(values[i], ix + i * cw, y + 61 + (1 - pb) * 6, 1.25f, i == 1 && best < 99 ? tierCol(values[1]) : T.text, true);
            }
            end();
        }
        // --- radar card ---
        int rx = x + lw + 4;
        if (begin("rc", rx, y, rw, ch, 0.08f, false)) {
            panel(rx, y, rw, ch, 8);
            int R = Math.min(32, (rw - 70) / 2);
            if (R >= 16) radar(pl, rx + 8 + R, y + ch / 2f, R);
            int tx = rx + (R >= 16 ? 16 + R * 2 + 6 : 8);
            int best = 99, sum = 0, n = 0;
            String strongest = "—";
            for (String m : TierData.MODES) {
                TierData.Result r = pl.get(m);
                int i = r == null ? -1 : TierData.tierIndex(r.shown());
                if (i < 0) continue;
                sum += i; n++;
                if (i < best) { best = i; strongest = TierData.modeName(m); }
            }
            text(L.t("radar.title"), tx, y + 8, T.muted, false);
            text(L.t("radar.peak"), tx, y + 22, T.text, false);
            String pk = best < 99 ? TierData.TIER_ORDER[best] : "—";
            text(pk, tx + g.textW(L.t("radar.peak"), false) + 4, y + 22, tierCol(pk), true);
            text(L.t("radar.avg"), tx, y + 33, T.text, false);
            String av = n > 0 ? TierData.TIER_ORDER[Math.round(sum / (float) n)] : "—";
            text(av, tx + g.textW(L.t("radar.avg"), false) + 4, y + 33, tierCol(av), true);
            text(L.t("radar.strongest"), tx, y + 50, T.muted, false);
            text(fit(strongest, rx + rw - tx - 4, true), tx, y + 61, T.text, true);
            end();
        }
        int cy = y + ch + 6;
        // --- standard modes ---
        int rankedN = 0;
        for (String m : TierData.MODES) { TierData.Result r = pl.get(m); if (r != null && r.shown() != null) rankedN++; }
        section(L.t("profile.modes"), L.t("profile.count", rankedN, TierData.MODES.length - rankedN), x, cy, w);
        cy += 14;
        int cols = 5, gap = 4, cwid = (w - gap * (cols - 1)) / cols;
        for (int i = 0; i < TierData.MODES.length; i++) {
            final int mi = i;
            modeCard(TierData.MODES[i], pl, x + (i % cols) * (cwid + gap), cy + (i / cols) * 34, cwid, 30, 0.12f + i * 0.045f,
                    () -> { selMode = mi; setTab(T_MODES); });
        }
        cy += ((TierData.MODES.length + cols - 1) / cols) * 34 + 2;
        // --- LTMs ---
        section(L.t("profile.ltms"), L.t("profile.autosync"), x, cy, w);
        cy += 14;
        List<TierData.Ltm> ltms = snap.ltms;
        if (ltms.isEmpty()) { text(L.t("ltm.none"), x + 6, cy + 2, T.muted, false); cy += 16; }
        for (int i = 0; i < ltms.size(); i++) {
            final int li = i;
            modeCard(ltms.get(i).key(), pl, x + (i % cols) * (cwid + gap), cy + (i / cols) * 34, cwid, 30, 0.5f + i * 0.045f,
                    () -> { selLtm = li; setTab(T_LTM); });
        }
        cy += ((ltms.size() + cols - 1) / cols) * 34;
        return cy - y + 4;
    }

    private void modeCard(String key, TierData.Player pl, int x, int y, int w, int h, float delay, Runnable click) {
        String id = "mc:" + key;
        if (!begin(id, x, y, w, h, delay, true)) return;
        TierData.Result r = pl.get(key);
        String shown = r == null ? null : r.shown();
        if (shown == null) pushA(0.5f);
        panel(x, y, w, h, 6);
        float[] hs = springs.get("h:" + id);
        float hv = hs == null ? 0 : hs[0];
        float spin = perf ? 0 : hv * 6.2832f + (1 - bounce((tabT - delay) * 1.2f)) * 3.1416f;
        icon(Icons.tex(key), x + 13, y + 12, 16, spin, 0xFFFFFFFF);
        text(fit(TierData.modeName(key), w - 28, false), x + 25, y + 4, T.text, false);
        if (shown != null) {
            String lab = Tags.label(r);
            text(lab, x + 25, y + 14, resCol(r), true);
            if (r.retired && w > 70) textR(L.t("tier.retired"), x + w - 5, y + 14, resCol(r), false);
            else if (r.peak != null && !r.peak.equals(shown) && w > 70) textR("^" + r.peak, x + w - 5, y + 14, T.muted, false);
            bar(x + 5, y + h - 5, w - 10, 2, (10 - TierData.tierIndex(shown)) / 10f, (tabT - delay - 0.1f) * 1.2f);
        } else {
            text("—", x + 25, y + 14, T.muted, true);
            bar(x + 5, y + h - 5, w - 10, 2, 0, 1);
        }
        if (shown == null) popA();
        end();
        hit(x, y, w, h, () -> { press(id); click.run(); });
    }

    private void skin(String name, int bx, int by, int bw, int bh) {
        Heads.Entry e = Heads.get(name);
        if (e == null || e.id == null) {
            for (int i = 0; i < 3; i++) {
                float ph = (float) Math.sin(time * 6 - i * 0.9f);
                dot(bx + bw / 2f - 8 + i * 8, by + bh / 2f - Math.max(0, ph) * 4, 3, alpha(T.acc, 0.5f + 0.5f * Math.max(0, ph)));
            }
            return;
        }
        String sk = "skin:" + name;
        if (!springs.containsKey(sk)) seed(sk, 0.3f);
        float s = spring(sk, 1, 200, 12);
        float sc = Math.min((bw - 6) / (float) e.w, (bh - 6) / (float) e.h);
        int dw = Math.round(e.w * sc), dh = Math.round(e.h * sc);
        float bob = perf ? 0 : (float) Math.sin(time * 2.4f) * 1.5f;
        float par = perf ? 0 : (mx - (bx + bw / 2f)) * 0.01f;
        Matrix3x2fStack p = g.pose();
        p.pushMatrix();
        p.translate(bx + bw / 2f + clamp(par, -2, 2), by + bh / 2f + bob);
        p.scale(s, s > 1 ? 2 - s : s);
        g.tex(e.id, -dw / 2, -dh / 2, dw, dh, a(0xFFFFFFFF));
        p.popMatrix();
    }

    /** Two rings of dots orbiting in 3D around the skin; drawn in a back pass and a front pass. */
    private void rings(float cx, float cy, boolean front) {
        for (int ring = 0; ring < 2; ring++) {
            float R = ring == 0 ? 36 : 31;
            double tilt = ring == 0 ? 1.15 : -0.9, spin = time * (ring == 0 ? 0.9 : -1.3);
            double roll = ring == 0 ? 0.35 : -0.5;
            int col = ring == 0 ? T.acc : T.acc2;
            for (int i = 0; i < 30; i++) {
                double an = i / 30.0 * Math.PI * 2 + spin;
                double x = Math.cos(an) * R, y = Math.sin(an) * R, z;
                double y2 = y * Math.cos(tilt);
                z = y * Math.sin(tilt);
                double x3 = x * Math.cos(roll) - y2 * Math.sin(roll), y3 = x * Math.sin(roll) + y2 * Math.cos(roll);
                if ((z > 0) != front) continue;
                float depth = (float) (z / R);
                float persp = 1 + depth * 0.18f;
                int size = depth > 0.4f ? 2 : 1;
                float trail = ((i % 10) / 10f);
                dot(cx + (float) x3 * persp, cy + (float) y3 * persp, size, alpha(col, (0.35f + 0.5f * (depth * 0.5f + 0.5f)) * (0.4f + 0.6f * trail)));
            }
        }
    }

    private void radar(TierData.Player pl, float cx, float cy, int R) {
        int n = TierData.MODES.length;
        float grow = perf ? 1 : bounce((tabT - 0.2f) * 1.1f);
        float rot = perf ? 0 : (float) Math.sin(time * 0.5f) * 0.04f;
        for (int ringI = 1; ringI <= 3; ringI++) {
            float rr = R * ringI / 3f;
            for (int i = 0; i < n; i++) {
                double a0 = Math.PI * 2 * i / n - Math.PI / 2 + rot, a1 = Math.PI * 2 * (i + 1) / n - Math.PI / 2 + rot;
                line(cx + (float) Math.cos(a0) * rr, cy + (float) Math.sin(a0) * rr, cx + (float) Math.cos(a1) * rr, cy + (float) Math.sin(a1) * rr, alpha(T.line, ringI == 3 ? 0.7f : 0.3f));
            }
        }
        float[] vx = new float[n], vy = new float[n];
        for (int i = 0; i < n; i++) {
            double an = Math.PI * 2 * i / n - Math.PI / 2 + rot;
            line(cx, cy, cx + (float) Math.cos(an) * R, cy + (float) Math.sin(an) * R, alpha(T.line, 0.3f));
            TierData.Result r = pl.get(TierData.MODES[i]);
            int ti = r == null ? -1 : TierData.tierIndex(r.shown());
            float v = (ti < 0 ? 0.08f : (10 - ti) / 10f) * grow;
            vx[i] = cx + (float) Math.cos(an) * R * v;
            vy[i] = cy + (float) Math.sin(an) * R * v;
        }
        for (int k = 1; k <= 4; k++) {
            float f = k / 4f;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                line(cx + (vx[i] - cx) * f, cy + (vy[i] - cy) * f, cx + (vx[j] - cx) * f, cy + (vy[j] - cy) * f, alpha(k == 4 ? T.acc : T.acc2, k == 4 ? 1f : 0.22f));
            }
        }
        for (int i = 0; i < n; i++) dot(vx[i], vy[i], 3, T.acc2);
    }

    private void tri(float cx, float baseY, float halfW, float height, int col) {
        int rows = Math.max(1, Math.round(Math.abs(height)));
        for (int i = 0; i < rows; i++) {
            float f = 1 - (i + 0.5f) / rows;
            int yy = Math.round(height > 0 ? baseY - i - 1 : baseY + i);
            int hw = Math.max(0, Math.round(halfW * f));
            if (hw > 0) fill(Math.round(cx) - hw, yy, Math.round(cx) + hw, yy + 1, col);
        }
    }

    /** The Clubstep Monster, drawn procedurally for the Tqopl easter egg. */
    private void monster(int bx, int by, int bw, int bh) {
        float jit = perf ? 0 : (float) Math.sin(time * 9) * 0.8f;
        int cx = bx + bw / 2;
        int hx = bx + 10, hy = by + 17 + Math.round(jit), hw = bw - 20, hh = 26;
        int white = 0xFFFFFFFF, red = 0xFFFF2233;
        // side spikes
        for (int i = 0; i < 4; i++) {
            int yy = hy + 3 + i * 6;
            for (int k = 0; k < 6; k++) {
                fill(hx - 1 - k, yy + k / 2, hx - k, yy + 5 - k / 2, alpha(red, 1 - k * 0.12f));
                fill(hx + hw + k, yy + k / 2, hx + hw + 1 + k, yy + 5 - k / 2, alpha(red, 0.45f - k * 0.05f));
            }
        }
        // horns and top block
        tri(hx + 6, hy, 6, 11, white); tri(hx + 6, hy, 4, 8, 0xFF050505);
        tri(hx + hw - 6, hy, 6, 11, white); tri(hx + hw - 6, hy, 4, 8, 0xFF050505);
        fill(cx - 8, hy - 9, cx + 8, hy, white); fill(cx - 7, hy - 9, cx + 7, hy, 0xFF050505);
        // head
        fill(hx - 1, hy - 1, hx + hw + 1, hy + hh + 1, white);
        fill(hx, hy, hx + hw, hy + hh, 0xFF050505);
        // eyes
        for (int e = 0; e < 2; e++) {
            int ex = e == 0 ? hx + 6 : hx + hw - 16, ey = hy + 8;
            fill(ex - 1, ey - 1, ex + 11, ey + 11, white);
            fill(ex, ey, ex + 10, ey + 10, 0xFF2A0508);
            float ecx = ex + 5, ecy = ey + 5;
            for (int b = 0; b < 5; b++) {
                double an = time * 5 * (e == 0 ? 1 : -1) + b * Math.PI * 2 / 5;
                line(ecx, ecy, ecx + (float) Math.cos(an) * 5, ecy + (float) Math.sin(an) * 5, red);
            }
            rr(ex + 2, ey + 2, 6, 6, 3, 0xFFF06CF0, 0);
            rr(ex + 3, ey + 3, 4, 4, 2, 0xFF2B7BFF, 0);
        }
        // teeth
        float chomp = perf ? 0 : Math.abs((float) Math.sin(time * 4)) * 3;
        for (int i = 0; i < 4; i++) {
            float tx = hx + 5 + i * (hw - 10) / 3f;
            tri(tx, hy + hh + 1, 4, -(8 + chomp), white);
            tri(tx, hy + hh + 1, 2.5f, -(6 + chomp), 0xFF050505);
        }
        for (int i = 0; i < 3; i++) {
            float tx = hx + 5 + (i + 0.5f) * (hw - 10) / 3f;
            tri(tx, by + bh - 2, 3, 6 + chomp * 0.6f, alpha(red, 0.9f));
        }
    }

    // ---- ranking -------------------------------------------------------------------------------
    private int ranking(int x, int y, int w, int viewTop, int viewBot) {
        List<TierData.Player> list = TierData.get().ranking;
        if (list.isEmpty()) { textC(L.t("status.loading"), x + w / 2, y + 30, T.muted, true); return 60; }
        int rh = 20, step = 23, vis = 0;
        for (int i = 0; i < list.size(); i++) {
            int ry = y + i * step;
            if (ry + rh < viewTop - 6 || ry > viewBot + 6) continue;
            TierData.Player pl = list.get(i);
            String id = "rk:" + pl.name;
            if (!begin(id, x, ry, w - 4, rh, Math.min(vis++, 12) * 0.035f, true)) continue;
            panel(x, ry, w - 4, rh, 6);
            int medal = i == 0 ? 0xFFFFC94A : i == 1 ? 0xFFC9D6E2 : i == 2 ? 0xFFE0975A : 0;
            if (medal != 0) fill(x + 1, ry + 5, x + 3, ry + rh - 5, medal);
            text("#" + pl.rank, x + 7, ry + 6, medal != 0 ? (Config.I.dark ? medal : T.ht) : T.muted, true);
            icon(Icons.id("textures/rank/" + TierData.TITLE_ID[TierData.titleIndex(pl.points)] + ".png"), x + 38, ry + 10, 12, 0, 0xFFFFFFFF);
            String nm = fit(pl.name, 92, true);
            text(nm, x + 48, ry + 6, T.text, true);
            int nx = x + 48 + g.textW(nm, true) + 5;
            if (!pl.region.isEmpty()) { text(pl.region, nx, ry + 6, T.muted, false); nx += g.textW(pl.region, false) + 8; }
            String pts = L.t("rank.pts", pl.points);
            int pr = x + w - 4 - 7;
            textR(pts, pr, ry + 6, T.acc, true);
            int limit = pr - g.textW(pts, true) - 8;
            // best tiers as chips
            List<Map.Entry<String, TierData.Result>> rs = new ArrayList<>(pl.tiers.entrySet());
            rs.removeIf(e -> e.getValue().shown() == null);
            rs.sort((a, b) -> TierData.tierIndex(a.getValue().shown()) - TierData.tierIndex(b.getValue().shown()));
            int cx = Math.max(nx, x + 172);
            for (Map.Entry<String, TierData.Result> e : rs) {
                String lab = Tags.label(e.getValue());
                int cw = 12 + g.textW(lab, true) + 5;
                if (cx + cw > limit) break;
                icon(Icons.tex(e.getKey()), cx + 5, ry + 10, 10, 0, 0xFFFFFFFF);
                text(lab, cx + 12, ry + 6, resCol(e.getValue()), true);
                cx += cw;
            }
            end();
            hit(x, ry, w - 4, rh, () -> { press(id); openProfile(pl.name); });
        }
        return list.size() * step;
    }

    // ---- modes / LTM boards --------------------------------------------------------------------
    private int modeBoard(int x, int y, int w, boolean ltm) {
        TierData.Snapshot snap = TierData.get();
        int n = ltm ? snap.ltms.size() : TierData.MODES.length;
        if (ltm && n == 0) {
            if (begin("noltm", x, y + 16, w, 40, 0, false)) { panel(x, y + 16, w, 40, 8); textC(L.t("ltm.none"), x + w / 2, y + 32, T.muted, true); end(); }
            return 70;
        }
        int sel = ltm ? (selLtm = Math.min(selLtm, n - 1)) : selMode;
        int cols = 5, gap = 4, cw = (w - gap * (cols - 1)) / cols;
        for (int i = 0; i < n; i++) {
            String key = ltm ? snap.ltms.get(i).key() : TierData.MODES[i];
            String id = (ltm ? "lc:" : "sc:") + key;
            int cx = x + (i % cols) * (cw + gap), cy = y + (i / cols) * 20;
            if (!begin(id, cx, cy, cw, 16, i * 0.03f, true)) continue;
            boolean on = i == sel;
            if (on) { rr(cx, cy, cw, 16, 8, T.acc, 0); if (glass) g.grad(cx + 8, cy + 1, cx + cw - 8, cy + 7, a(0x50FFFFFF), a(0x00FFFFFF)); }
            else panel(cx, cy, cw, 16, 8);
            icon(Icons.tex(key), cx + 10, cy + 8, 10, on && !perf ? time * 2.2f : 0, 0xFFFFFFFF);
            text(fit(TierData.modeName(key), cw - 22 - (ltm && snap.ltms.get(i).active ? 8 : 0), true), cx + 18, cy + 4, on ? T.on : T.text, true);
            if (ltm && snap.ltms.get(i).active) rr(cx + cw - 9, cy + 6, 4, 4, 2, alpha(on ? T.on : T.acc2, 0.6f + 0.4f * (float) Math.sin(time * 5)), 0);
            end();
            final int idx = i;
            hit(cx, cy, cw, 16, () -> { press(id); if (ltm) selLtm = idx; else selMode = idx; tabT = 0.12f; });
        }
        int cy = y + ((n + cols - 1) / cols) * 20 + 4;
        String key = ltm ? snap.ltms.get(sel).key() : TierData.MODES[sel];
        @SuppressWarnings("unchecked") List<TierData.Player>[] byTier = new List[5];
        int total = 0;
        for (int i = 0; i < 5; i++) byTier[i] = new ArrayList<>();
        for (TierData.Player pl : snap.ranking) {
            TierData.Result r = pl.get(key);
            int ti = r == null ? -1 : TierData.tierIndex(r.shown());
            if (ti >= 0) { byTier[ti / 2].add(pl); total++; }
        }
        String head = TierData.modeName(key) + (ltm && snap.ltms.get(sel).active ? "  ·  " + L.t("ltm.active") : "");
        section(head, L.t("board.count", total), x, cy, w);
        cy += 14;
        int maxH = 0;
        for (int t = 0; t < 5; t++) {
            List<TierData.Player> col = byTier[t];
            col.sort((a, b) -> {
                int ia = TierData.tierIndex(a.get(key).shown()), ib = TierData.tierIndex(b.get(key).shown());
                return ia != ib ? ia - ib : a.name.compareToIgnoreCase(b.name);
            });
            int px = x + t * (cw + gap), ph = 20 + Math.max(1, col.size()) * 11 + 4;
            maxH = Math.max(maxH, ph);
            if (!begin("col" + t, px, cy, cw, ph, 0.1f + t * 0.06f, false)) continue;
            panel(px, cy, cw, ph, 7);
            String th = L.t("board.tier", t + 1);
            text(th, px + 7, cy + 5, t == 0 ? T.ht : T.text, true);
            textR(String.valueOf(col.size()), px + cw - 7, cy + 5, T.muted, false);
            fill(px + 6, cy + 16, px + cw - 6, cy + 17, alpha(T.line, 0.5f));
            if (col.isEmpty()) text("—", px + 7, cy + 21, T.muted, false);
            for (int i = 0; i < col.size(); i++) {
                TierData.Player pl = col.get(i);
                TierData.Result r = pl.get(key);
                int ey = cy + 20 + i * 11;
                boolean h = hov(px + 3, ey - 1, cw - 6, 11);
                if (h) rr(px + 3, ey - 1, cw - 6, 11, 4, alpha(T.acc, 0.25f), 0);
                String lab = Tags.label(r).replaceAll("[0-9]", "");
                text(lab, px + 7, ey, resCol(r), true);
                text(fit(pl.name, cw - 14 - g.textW(lab, true) - 4, false), px + 7 + g.textW(lab, true) + 4, ey, h ? T.acc : T.text, false);
                hit(px + 3, ey - 1, cw - 6, 11, () -> openProfile(pl.name));
            }
            end();
        }
        return cy + maxH - y + 4;
    }

    // ---- settings ------------------------------------------------------------------------------
    private int settings(int x, int y, int w) {
        Config c = Config.I;
        int gap = 4, cw = (w - gap) / 2, rh = 24, row = 0;
        int[] pos = new int[2];
        Object[][] rows = {
                {"set.nametag", 0}, {"set.tablist", 1}, {"set.icons", 2}, {"set.side", 3}, {"set.mode", 4},
                {"set.theme", 5}, {"set.glass", 6}, {"set.perf", 7}, {"set.lang", 8}};
        for (Object[] r : rows) {
            int idx = (Integer) r[1];
            boolean wide = idx == 4 || idx == 8;
            if (wide && pos[0] == 1) { pos[0] = 0; row++; }
            int px = x + pos[0] * (cw + gap), py = y + row * (rh + gap), pw = wide ? w : cw;
            String id = "set" + idx;
            if (begin(id, px, py, pw, rh, (row * 2 + pos[0]) * 0.04f, false)) {
                panel(px, py, pw, rh, 7);
                text(L.t((String) r[0]), px + 9, py + 8, T.text, true);
                int cr = px + pw - 8;
                switch (idx) {
                    case 0 -> toggle(id, cr, py + 6, c.nametag, () -> c.nametag = !c.nametag);
                    case 1 -> toggle(id, cr, py + 6, c.tablist, () -> c.tablist = !c.tablist);
                    case 2 -> toggle(id, cr, py + 6, c.icons, () -> c.icons = !c.icons);
                    case 3 -> seg(id, cr, py + 5, new String[]{L.t("set.left"), L.t("set.right")}, "right".equals(c.side) ? 1 : 0, i -> c.side = i == 1 ? "right" : "left");
                    case 4 -> cycler(id, cr, py + 5);
                    case 5 -> seg(id, cr, py + 5, new String[]{L.t("set.dark"), L.t("set.light")}, c.dark ? 0 : 1, i -> c.dark = i == 0);
                    case 6 -> toggle(id, cr, py + 6, c.glass && !c.performance, () -> { if (!c.performance) c.glass = !c.glass; });
                    case 7 -> toggle(id, cr, py + 6, c.performance, () -> c.performance = !c.performance);
                    default -> seg(id, cr, py + 5, new String[]{"English", "Polski", "Deutsch"}, langIndex(), i -> { c.language = L.CODES[i]; L.load(c.language); });
                }
                end();
            }
            if (wide) { pos[0] = 0; row++; } else if (++pos[0] == 2) { pos[0] = 0; row++; }
        }
        if (pos[0] == 1) row++;
        int fy = y + row * (rh + gap) + 2;
        text(L.t("set.hint"), x + 4, fy, T.muted, false);
        return fy - y + 14;
    }

    private int langIndex() {
        for (int i = 0; i < L.CODES.length; i++) if (L.CODES[i].equals(Config.I.language)) return i;
        return 0;
    }

    private void toggle(String id, int xRight, int y, boolean on, Runnable flip) {
        int w = 24, h = 12, x = xRight - w;
        float k = spring("tg:" + id, on ? 1 : 0, 260, 13);
        float kc = clamp(k, 0, 1);
        rr(x, y, w, h, 6, on ? T.acc : alpha(T.muted, 0.45f), T.line);
        float sq = perf ? 0 : Math.min(3, Math.abs(springs.get("tg:" + id)[1]) * 0.5f);
        int kw = Math.round(8 + sq);
        int kx = Math.round(x + 2 + k * (w - 4 - 8) - (kc > 0.5f ? sq : 0));
        rr(kx, y + 2, kw, 8, 4, 0xFFFFFFFF, 0);
        hit(x - 4, y - 4, w + 8, h + 8, () -> { flip.run(); Config.save(); });
    }

    private interface IntAction { void run(int i); }

    private void seg(String id, int xRight, int y, String[] opts, int sel, IntAction pick) {
        int ow = 0;
        for (String o : opts) ow = Math.max(ow, g.textW(o, true) + 12);
        int w = ow * opts.length + 4, x = xRight - w;
        rr(x, y, w, 14, 7, alpha(T.muted, 0.25f), T.line);
        float px = spring("sg:" + id, x + 2 + sel * ow, 230, 15);
        float st = perf ? 0 : Math.min(6, Math.abs(springs.get("sg:" + id)[1]) * 0.012f);
        rr(Math.round(px - st / 2), y + 2, Math.round(ow + st), 10, 5, T.acc, 0);
        for (int i = 0; i < opts.length; i++) {
            textC(opts[i], x + 2 + i * ow + ow / 2, y + 3, i == sel ? T.on : T.muted, true);
            final int idx = i;
            hit(x + 2 + i * ow, y - 3, ow, 20, () -> { pick.run(idx); Config.save(); });
        }
    }

    private List<String> modeChoices() {
        List<String> l = new ArrayList<>();
        l.add("best");
        for (String m : TierData.MODES) l.add(m);
        for (TierData.Ltm t : TierData.get().ltms) l.add(t.key());
        return l;
    }

    private void cycler(String id, int xRight, int y) {
        List<String> ch = modeChoices();
        int cur = Math.max(0, ch.indexOf(Config.I.displayMode));
        String key = ch.get(cur);
        String label = key.equals("best") ? L.t("set.best") : TierData.modeName(key);
        int w = 132, x = xRight - w;
        float wob = spring("cy:" + id, 0, 320, 12);
        rr(x, y, w, 14, 7, alpha(T.muted, 0.25f), T.line);
        text("<", x + 6, y + 3, hov(x, y, 16, 14) ? T.acc : T.text, true);
        textR(">", x + w - 6, y + 3, hov(x + w - 16, y, 16, 14) ? T.acc : T.text, true);
        int lw = g.textW(label, true) + (key.equals("best") ? 0 : 13);
        int lx = x + w / 2 - lw / 2 + Math.round(wob);
        if (!key.equals("best")) { icon(Icons.tex(key), lx + 5, y + 7, 10, 0, 0xFFFFFFFF); lx += 13; }
        text(label, lx, y + 3, T.acc, true);
        hit(x, y - 3, w / 2, 20, () -> { Config.I.displayMode = ch.get((cur - 1 + ch.size()) % ch.size()); kick("cy:" + id, -90); Config.save(); });
        hit(x + w / 2, y - 3, w / 2, 20, () -> { Config.I.displayMode = ch.get((cur + 1) % ch.size()); kick("cy:" + id, 90); Config.save(); });
    }

    // ---- input ---------------------------------------------------------------------------------
    public boolean click(double x, double y, int button) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) return false;
        for (int i = hitRects.size() - 1; i >= 0; i--) {
            float[] r = hitRects.get(i);
            if (x >= r[0] && x < r[2] && y >= r[1] && y < r[3]) { hitActions.get(i).run(); return true; }
        }
        return false;
    }

    public boolean scroll(double dy) {
        scrollTarget[tab] -= (float) dy * 26;
        if (scrollTarget[tab] < 0) scrollTarget[tab] = 0;
        return true;
    }

    public boolean key(int key) {
        if (key == InputConstants.KEY_RIGHT || key == InputConstants.KEY_TAB) { setTab((tab + 1) % TABS); return true; }      // right / tab
        if (key == InputConstants.KEY_LEFT) { setTab((tab + TABS - 1) % TABS); return true; }             // left
        return false;
    }

    @Override public String toString() { return String.format(Locale.ROOT, "ItsTiersUI[tab=%d]", tab); }
}
