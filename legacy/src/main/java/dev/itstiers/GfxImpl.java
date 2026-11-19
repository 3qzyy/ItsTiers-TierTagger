package dev.itstiers;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

public final class GfxImpl implements Gfx {
    private static final Style SEMI = Style.EMPTY.withFont(new FontDescription.Resource(Icons.id("ui_semibold")));
    private static final Style BOLD = Style.EMPTY.withFont(new FontDescription.Resource(Icons.id("ui_bold")));
    private final Map<String, Component> semi = new HashMap<>(), bold = new HashMap<>();
    private GuiGraphics g;
    private Font font;

    public void set(GuiGraphics g, Font font) { this.g = g; this.font = font; }

    private Component comp(String s, boolean b) {
        Map<String, Component> m = b ? bold : semi;
        Component c = m.get(s);
        if (c == null) {
            if (m.size() > 3000) m.clear();
            c = Component.literal(s).setStyle(b ? BOLD : SEMI);
            m.put(s, c);
        }
        return c;
    }

    @Override public int width() { return g.guiWidth(); }
    @Override public int height() { return g.guiHeight(); }
    @Override public void fill(int x0, int y0, int x1, int y1, int argb) { g.fill(x0, y0, x1, y1, argb); }
    @Override public void grad(int x0, int y0, int x1, int y1, int top, int bottom) { g.fillGradient(x0, y0, x1, y1, top, bottom); }
    @Override public void text(String s, int x, int y, int argb, boolean b) { g.drawString(font, comp(s, b), x, y, argb, false); }
    @Override public int textW(String s, boolean b) { return font.width(comp(s, b)); }
    @Override public void tex(Identifier id, int x, int y, int w, int h, int argb) { g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, w, h, argb); }
    @Override public Matrix3x2fStack pose() { return g.pose(); }
    @Override public void clip(int x0, int y0, int x1, int y1) { g.enableScissor(x0, y0, x1, y1); }
    @Override public void unclip() { g.disableScissor(); }
}