package dev.itstiers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenAccess {
    public static void set(Screen s) { Minecraft.getInstance().gui.setScreen(s); }
    public static Screen get() { return Minecraft.getInstance().gui.screen(); }
    public static boolean overlayActive() { return Minecraft.getInstance().gui.overlay() != null; }
    public static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1, c -> {});
    }
}