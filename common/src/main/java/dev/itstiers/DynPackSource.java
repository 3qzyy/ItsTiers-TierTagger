package dev.itstiers;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;

import java.util.Optional;
import java.util.function.Consumer;

/** Always-on resource pack backed by config/itstiers/pack (downloaded LTM icons). */
public class DynPackSource implements RepositorySource {
    @Override
    public void loadPacks(Consumer<Pack> consumer) {
        try {
            PackLocationInfo info = new PackLocationInfo("itstiers_dynamic", Component.literal("ItsTiers LTM icons"), PackSource.BUILT_IN, Optional.empty());
            Pack pack = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(Icons.packDir()), PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) {
                consumer.accept(pack);
                Icons.onRepoReload();
            } else ItsTiersClient.LOG.warn("ItsTiers dynamic icon pack could not be read");
        } catch (Throwable t) {
            ItsTiersClient.LOG.warn("ItsTiers dynamic icon pack failed", t);
        }
    }
}