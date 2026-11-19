# CLAUDE.md

Fabric client mod that shows ItsTiers tiers on nametags/tab list and in a custom GUI. Targets MC 1.21.11, 26.1.2, 26.2, 26.3.

## Layout
- `common/` shared code and assets. Everything version independent lives here.
  - `UI.java` the whole screen (layout, drawing, animations, input) on top of the `Gfx` interface.
  - `TierData.java` live data from `website-tiers.json`; `Icons.java` icons and dynamic LTM icon download; `Tags.java` nametag text.
  - `mixin/` Player display name, tab list, and the pack source for downloaded LTM icons.
- `legacy/` 1.21.x only (`GuiGraphics`, `KeyBindingHelper`). `modern/` 26.x (`GuiGraphicsExtractor`, `KeyMappingHelper`).
  `modern_a` (26.1) and `modern_b` (26.2+) only differ in `ScreenAccess`.
- `build.gradle` holds the target table (Minecraft, Fabric API, Java version). Settings are in `settings.gradle`.

## Commands
- Build all jars: `./gradlew build` (jars in `versions/<target>/build/libs`).
- Run one target: `./gradlew :mc26_3:runClient`. Add `-Pautoshot=true` to screenshot every page into `run/screenshots` and quit.

## Rules and gotchas
- Never hardcode input codes: 26.3 changed `MOUSE_BUTTON_LEFT` and the key codes. Always use `InputConstants`.
- Fonts must be TTF, not OTF (the game rejects CFF).
- New LTMs must keep working without a mod update: do not hardcode LTM ids; icons for unknown LTMs are downloaded to `config/itstiers/icons`.
- The Tqopl easter egg must never appear in rankings or have points.
- UI text goes through `itstiers_lang/{en,pl,de}.json`, English is the default and the fallback.
- Version-specific API goes in `legacy/` or `modern*/`, never in `common/`.
- Not yet verified in game: other versions than 26.3, tags on other players, downloading a brand new LTM icon.

## Disclosure
This project is vibe coded with Claude (Sonnet 5.5, Opus 5.5, Fable 5.2). Keep that stated in the README.
