package dev.itstiers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenAccess {
    public static void set(Screen s) { Minecraft.getInstance().setScreen(s); }
    public static Screen get() { return Minecraft.getInstance().screen; }
    public static boolean overlayActive() { return Minecraft.getInstance().getOverlay() != null; }
    public static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, c -> {});
    }
}