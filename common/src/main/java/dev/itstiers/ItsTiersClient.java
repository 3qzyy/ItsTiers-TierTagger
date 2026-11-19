package dev.itstiers;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.TreeSet;

public class ItsTiersClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("ItsTiers");
    private static KeyMapping key;
    private static volatile boolean pendingOpen;
    private static volatile String pendingUser;
    private static int ticks;

    @Override
    public void onInitializeClient() {
        Config.load();
        Icons.init();
        TierData.refreshAsync(true);

        key = Platform.registerKey(new KeyMapping("key.itstiers.open", InputConstants.KEY_I,
                KeyMapping.Category.register(Icons.id("main"))));

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
                LiteralArgumentBuilder.<FabricClientCommandSource>literal("itstiers")
                        .executes(c -> { pendingUser = null; pendingOpen = true; return 1; })
                        .then(RequiredArgumentBuilder.<FabricClientCommandSource, String>argument("user", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    String rem = b.getRemaining().toLowerCase(Locale.ROOT);
                                    TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
                                    for (TierData.Player p : TierData.get().ranking) names.add(p.name);
                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.getConnection() != null) for (PlayerInfo i : mc.getConnection().getOnlinePlayers()) names.add(i.getProfile().name());
                                    for (String n : names) if (n.toLowerCase(Locale.ROOT).startsWith(rem)) b.suggest(n);
                                    return b.buildFuture();
                                })
                                .executes(c -> { pendingUser = StringArgumentType.getString(c, "user"); pendingOpen = true; return 1; }))));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ticks++;
            if (ticks % 1200 == 0) TierData.refreshAsync(false);
            if (Icons.needsReload && !ScreenAccess.overlayActive()) {
                Icons.needsReload = false;
                mc.reloadResourcePacks();
            }
            while (key.consumeClick()) { pendingUser = null; pendingOpen = true; }
            if (pendingOpen) {
                pendingOpen = false;
                TierData.refreshAsync(false);
                ScreenAccess.set(new ItsScreen(new UI(pendingUser)));
            }
            AutoShot.tick(mc);
        });
    }
}