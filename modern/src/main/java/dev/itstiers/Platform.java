package dev.itstiers;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public final class Platform {
    public static KeyMapping registerKey(KeyMapping k) { return KeyMappingHelper.registerKeyMapping(k); }
}