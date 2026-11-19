package dev.itstiers.mixin;

import dev.itstiers.DynPackSource;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Shadow @Final @Mutable private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void itstiers$addSource(RepositorySource[] initial, CallbackInfo ci) {
        boolean client = false;
        for (RepositorySource s : this.sources) if (s instanceof ClientPackSource) { client = true; break; }
        if (!client) return;
        Set<RepositorySource> copy = new LinkedHashSet<>(this.sources);
        copy.add(new DynPackSource());
        this.sources = copy;
    }
}