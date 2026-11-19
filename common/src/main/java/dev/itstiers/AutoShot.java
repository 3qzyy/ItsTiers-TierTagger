package dev.itstiers;

import net.minecraft.client.Minecraft;

/** Dev-only: -Ditstiers.autoshot=true opens every page, takes screenshots and quits. */
final class AutoShot {
    private static final boolean ON = System.getProperty("itstiers.autoshot") != null;
    private static int n = -1;
    private static UI ui;

    static void tick(Minecraft mc) {
        if (!ON) return;
        if (n < 0) { if (ScreenAccess.get() != null && !ScreenAccess.overlayActive()) n = 0; else return; }
        n++;
        Config c = Config.I;
        String top = TierData.get().ranking.isEmpty() ? "Notch" : TierData.get().ranking.get(0).name;
        switch (n) {
            case 60 -> { c.dark = true; c.glass = true; c.performance = false; ui = new UI(top); ScreenAccess.set(new ItsScreen(ui)); }
            case 140 -> ScreenAccess.shot("its_1_profile_dark.png");
            case 145 -> ui.debugTab(0);
            case 185 -> ScreenAccess.shot("its_2_ranking.png");
            case 190 -> ui.debugTab(1);
            case 230 -> ScreenAccess.shot("its_3_modes.png");
            case 235 -> ui.debugTab(2);
            case 275 -> ScreenAccess.shot("its_4_ltm.png");
            case 280 -> ui.debugTab(4);
            case 320 -> ScreenAccess.shot("its_5_settings.png");
            case 325 -> { c.dark = false; ui.debugProfile(top); }
            case 385 -> ScreenAccess.shot("its_6_profile_light.png");
            case 390 -> { c.dark = true; ui.debugProfile("Tqopl"); }
            case 440 -> ScreenAccess.shot("its_7_egg.png");
            case 445 -> { c.performance = true; ui.debugProfile(top); }
            case 500 -> ScreenAccess.shot("its_8_perf.png");
            case 520 -> { LOG_DONE(); mc.stop(); }
            default -> {}
        }
    }

    private static void LOG_DONE() { ItsTiersClient.LOG.info("ItsTiers autoshot finished"); }
}