# ItsTiers TierTagger

A client-side Fabric mod that shows [ItsTiers](https://itsmoritzzz787.github.io/ItsTiers/) tiers next to player names and
gives you a full tier browser in game.

Supported: Minecraft **1.21.11**, **26.1.x**, **26.2**, **26.3** (Fabric Loader 0.17+, Fabric API).

## Made with AI (vibe coded)
This mod is **vibe coded**: it was written almost entirely by AI through [Claude Code](https://claude.com/claude-code), with a human
steering the design and testing it in game. Models used in the session: **Claude Sonnet 5.5**, **Claude Opus 5.5** and
**Claude Fable 5.2** (Anthropic). Expect rough edges, and review the code before trusting it. It is all open source, so
issues and pull requests are welcome.
## Features
- Tier tags on nametags and in the tab list, on the left or right of the name, with the mode icon.
- `/itstiers <nick>` (or the `I` key) opens the profile page: skin render, rank (Combat Ace, ...), points, tier radar, every mode and LTM.
- Ranking, per-mode boards and an LTM tab, all from live data.
- Retired tiers are shown with an `R` prefix.
- New LTMs need **no mod update**: the mod reads the website data and downloads new LTM icons into `config/itstiers/icons`.
- Dark and light theme, liquid glass look, bouncy animations. Performance mode turns the effects off.
- English (default), Polish, German. The whole UI follows the in-game GUI scale.

## Privacy and network use
The mod only makes `GET` requests, and sends no personal data:
- `https://itsmoritzzz787.github.io/ItsTiers/website-tiers.json` (tier data, cached in `config/itstiers/cache.json`)
- icon URLs listed in that file, for LTMs the mod does not ship yet
- `https://vzge.me` and `https://mc-heads.net` for skin renders of the profile you open

## Building
```
./gradlew build
```
Jars end up in `versions/<target>/build/libs`. Needs JDK 25 (JDK 21 is enough for 1.21.11 only).

Layout: `common/` shared code and assets, `legacy/` 1.21.x specifics, `modern/` 26.x specifics.

## Credits and licenses
- Code: MIT, see `LICENSE`.
- Font: [Inter](https://rsms.me/inter/) (SIL OFL 1.1), see `common/src/main/resources/assets/itstiers/font/inter_license.txt`.
- Tier data, logo and mode icons belong to the ItsTiers tierlist and their respective owners; this is an unofficial fan project.
- [jsvg](https://github.com/weisJ/jsvg) (MIT) is bundled to read SVG icons.