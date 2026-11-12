package dev.itstiers.mixin;

import dev.itstiers.Tags;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void itstiers$tag(CallbackInfoReturnable<Component> cir) {
        if ((Object) this instanceof AbstractClientPlayer p) {
            Component c = Tags.decorate(cir.getReturnValue(), p.getScoreboardName(), false);
            if (c != null) cir.setReturnValue(c);
        }
    }
}