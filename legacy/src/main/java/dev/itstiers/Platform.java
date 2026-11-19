package dev.itstiers;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class Platform {
    public static KeyMapping registerKey(KeyMapping k) { return KeyBindingHelper.registerKeyBinding(k); }
}