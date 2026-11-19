package dev.itstiers.mixin;

import dev.itstiers.Tags;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class TabMixin {
    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void itstiers$tag(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        Component c = Tags.decorate(cir.getReturnValue(), info.getProfile().name(), true);
        if (c != null) cir.setReturnValue(c);
    }
}