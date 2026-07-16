
# StarfieldPastoral | 星野牧歌

StarfieldPastoral（星野牧歌） is a non-commercial fan project that recreates the feel and core progression of Stardew Valley inside Minecraft on NeoForge.

## Community | 官方社区

- Official Discord server: [https://discord.gg/cnG3eE58Au](https://discord.gg/cnG3eE58Au)
- 官方 QQ 群：`961767762`

## Current Status

The project is in active alpha development. It already contains a broad playable foundation, but it is not yet a full 1:1 parity implementation.

Current development focus is on expanding feature coverage while tightening Stardew Valley behavioral parity across existing systems.

## Platform

- Project name: StarfieldPastoral | 星野牧歌
- Minecraft 1.21.1
- NeoForge 21.1.217
- Java 21
- Mod version: 0.5.1fix2
- Mod id: stardewcraft

## Implemented or In-Progress Systems

- core farm flow, farmland, crops, growth management, and related item content;
- animal systems including barns, coops, feed troughs, hay flow, pasture grass eating, and animal interaction UI;
- fishing data, fish location rules, treasure tables, and fishing session runtime;
- NPC runtime systems including schedules, dialogue, mail, event triggers, and friendship-related content;
- cutscenes, overnight settlement flow, time management, pass-out handling, and player data systems;
- interior and subspace systems for farm buildings and special locations;
- artisan and utility blocks such as kegs, casks, looms, furnaces, smokers, worm bins, and similar facilities;
- community center, shop, minecart, warp, weather, mining, and desert-related content foundations.

## Repository Scope

This repository is intended to keep the actual mod project itself:

- source code and gameplay resources under src;
- Gradle build files and wrapper files;
- required local dependency jars under libs;
- essential repository metadata such as README, license, and workflow files.

Local planning notes, reverse-engineering reference material, scratch assets, and temporary working files are intentionally kept out of version control.

## Development

Common local validation commands:

- ./gradlew classes
- ./gradlew build

The 0.5 addon/data-pack API guide is in `docs/0.5-addon-api.md`. Runnable examples are under `examples/stardewcraft-data-pack` and `examples/stardewcraft-addon`.

If local dependency resolution gets out of sync, refresh Gradle dependencies and reload the project in your IDE.

## Pregen World Data

The Stardew Valley dimension relies on prebuilt region data instead of runtime chunk-by-chunk generation.

Expected resource locations include:

- src/main/resources/data/stardewcraft/structures/stardew_valley/main.schem
- src/main/resources/data/stardewcraft/structures/mine/main.schem
- src/main/resources/pregen/stardew_valley/region_manifest.txt
- src/main/resources/pregen/stardew_valley/region/*.mca

If the required pregen data is missing or invalid, travel into the Stardew Valley world space is blocked.

## Localization Credits

- Russian localization incorporates translation work by Annelo and the translators of
  [annel0/Starfield-Pastoral](https://github.com/annel0/Starfield-Pastoral), with official Stardew Valley localized text taking precedence where available.

## License and Asset Notice

This repository is distributed as a non-commercial fan project.

Source code and project-original content belong to the StarfieldPastoral project team and contributors. Any included third-party or original-game-derived assets remain subject to their original rightsholders and are not relicensed by this repository.

See LICENSE.md for the current project license terms.
