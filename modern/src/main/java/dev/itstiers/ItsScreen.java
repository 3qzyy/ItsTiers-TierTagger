package dev.itstiers;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class ItsScreen extends Screen {
    private final UI ui;
    private final GfxImpl gfx = new GfxImpl();

    public ItsScreen(UI ui) {
        super(Component.literal("ItsTiers"));
        this.ui = ui;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        gfx.set(g, this.font);
        ui.render(gfx, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partial) {
        if (this.minecraft.level == null) this.extractPanorama(g, partial);
        if (ui.wantsBlur()) this.extractBlurredBackground(g);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        return ui.click(e.x(), e.y(), e.button()) || super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dx, double dy) { return ui.scroll(dy); }

    @Override
    public boolean keyPressed(KeyEvent e) { return ui.key(e.key()) || super.keyPressed(e); }

    @Override
    public boolean isPauseScreen() { return false; }
}