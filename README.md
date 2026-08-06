<p align="center">
  <img src="https://raw.githubusercontent.com/ChangQingElysium/Starfield-Pastoral/main/.github/assets/starfield-pastoral-hero.png" alt="A farm, river, and mine under a starry sky" width="100%">
</p>

<h1 align="center">Starfield Pastoral · 星野牧歌</h1>

<p align="center">
  <strong>Farm, fish, raise animals, meet the villagers, and explore the mines in Minecraft 1.21.1.</strong>
  <br>
  A non-commercial NeoForge fan project based on the farming, town life, and progression of Stardew Valley.
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/starfield-pastoral"><img src="https://img.shields.io/curseforge/dt/1525680?style=flat-square&logo=curseforge&label=Downloads&color=F16436" alt="CurseForge downloads"></a>
  <a href="https://modrinth.com/mod/starfield-pastoral"><img src="https://img.shields.io/modrinth/dt/starfield-pastoral?style=flat-square&logo=modrinth&label=Downloads&color=00AF5C" alt="Modrinth downloads"></a>
  <a href="https://github.com/ChangQingElysium/Starfield-Pastoral/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/ChangQingElysium/Starfield-Pastoral/build.yml?branch=main&style=flat-square&logo=github&label=Build" alt="Build status"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.21.1-62B47A?style=flat-square" alt="Minecraft 1.21.1">
  <img src="https://img.shields.io/badge/NeoForge-21.1.217-E78A2F?style=flat-square" alt="NeoForge 21.1.217">
  <img src="https://img.shields.io/badge/Status-Alpha-F4C542?style=flat-square" alt="Alpha status">
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/starfield-pastoral">Download on CurseForge</a>
  ·
  <a href="https://modrinth.com/mod/starfield-pastoral">Download on Modrinth</a>
  ·
  <a href="https://discord.gg/cnG3eE58Au">Join Discord</a>
  ·
  <a href="https://www.mcmod.cn/class/26689.html">MCMod.cn (Chinese)</a>
  ·
  <a href="https://github.com/ChangQingElysium/Starfield-Pastoral/discussions/categories/ideas">Share feedback</a>
  ·
  <a href="https://github.com/ChangQingElysium/Starfield-Pastoral/issues">Report a bug</a>
</p>

## About

Starfield Pastoral is a NeoForge mod for Minecraft 1.21.1. A new game begins in the Overworld. Find the Wizard's Tower, complete the Wizard's request, enter the Stardew Valley dimension, and take over your grandfather's farm.

Current builds include farming, animals, fishing, NPC schedules, dialogue, mail, shops, mines, time progression, and overnight settlement.

> [!WARNING]
> Starfield Pastoral is in alpha. Updates may change systems and world data. Back up your world before installing a new version.

## Features

- 🌱 **Farming:** Grow seasonal crops, use fertilizer and sprinklers, and process harvests in kegs, preserves jars, and other machines.
- 🐄 **Animals:** Build coops and barns, manage hay and feed, care for animals, and collect eggs, milk, wool, and other produce.
- 🎣 **Fishing:** Catch fish based on location, season, and weather. Fishing includes a minigame, bait, tackle, and treasure.
- 🏘️ **Villagers:** Follow NPC schedules, talk to residents, receive mail, build friendships, visit shops, and trigger events.
- ⛏️ **Exploration:** Fight and gather ore in the mines, restore the Community Center, and unlock minecarts, warps, and new areas.
- 🤝 **Multiplayer:** Run a farm together in a shared Stardew Valley world.

## Getting started

1. Use the **Wizard Tower Compass** in the Overworld to find the nearest Wizard's Tower.
2. Talk to the Wizard and accept his opening quest.
3. Give the Wizard an **Eye of Ender** to stabilize the dimensional route.
4. Enter the Stardew Valley dimension and travel to the farm.

## Download and requirements

| Requirement | Version |
| --- | --- |
| Minecraft | 1.21.1 |
| Mod loader | NeoForge 21.1.217 or a compatible newer build |
| Java | 21 |
| Environment | Client and server |
| Source version | 0.5.5 |
| Official downloads | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/starfield-pastoral) · [Modrinth](https://modrinth.com/mod/starfield-pastoral) |

JEI is optional and adds information for fishing and other content. Read the release notes before updating an existing world.

## Community

- **Discord:** [Join the official server](https://discord.gg/cnG3eE58Au)
- **QQ group:** `961767762`
- **Ideas and player feedback:** [GitHub Discussions](https://github.com/ChangQingElysium/Starfield-Pastoral/discussions/categories/ideas)
- **Bug reports:** [GitHub Issues](https://github.com/ChangQingElysium/Starfield-Pastoral/issues)

## Add-ons and development

Data packs and NeoForge add-ons can use the 0.5 API to extend supported farm layouts, buildings, locations, and other content.

- Read the [0.5 add-on and data-pack API guide](https://github.com/ChangQingElysium/Starfield-Pastoral/blob/main/docs/0.5-addon-api.md).
- Review the [example data pack](https://github.com/ChangQingElysium/Starfield-Pastoral/tree/main/examples/stardewcraft-data-pack) and [example NeoForge add-on](https://github.com/ChangQingElysium/Starfield-Pastoral/tree/main/examples/stardewcraft-addon).
- Check player-facing changes in the [changelog](https://github.com/ChangQingElysium/Starfield-Pastoral/blob/main/CHANGELOG.md).

Common local validation commands:

```shell
./gradlew classes
./gradlew build
```

<details>
<summary><strong>Prebuilt world data for development</strong></summary>

The Stardew Valley dimension loads prebuilt region data. Development checkouts need the following files:

```text
src/main/resources/data/stardewcraft/structures/stardew_valley/main.schem
src/main/resources/data/stardewcraft/structures/mine/main.schem
src/main/resources/pregen/stardew_valley/region_manifest.txt
src/main/resources/pregen/stardew_valley/region/*.mca
```

The mod blocks entry to the Stardew Valley dimension when these files are missing or fail validation.

</details>

## Credits and license

Russian localization includes work by Annelo and contributors to [annel0/Starfield-Pastoral](https://github.com/annel0/Starfield-Pastoral). Existing official localization text takes priority.

Starfield Pastoral uses a [custom non-commercial fan project license](https://github.com/ChangQingElysium/Starfield-Pastoral/blob/main/LICENSE.md). Original project code and content belong to the project team and contributors. Third-party and game-derived materials remain the property of their respective rightsholders. The project license does not cover those materials.

Starfield Pastoral has no affiliation with ConcernedApe, Mojang, or Microsoft.

<details>
<summary><strong>简体中文简介</strong></summary>

星野牧歌是适用于 Minecraft 1.21.1 的 NeoForge 非商业同人模组。玩家从主世界出发，找到法师塔，完成任务后进入星露谷维度，并在爷爷留下的农场开始生活。

目前可体验种植、养殖、钓鱼、居民互动、矿洞、商店、社区中心和多人联机等内容。项目仍处于 Alpha 开发阶段，更新前请备份存档。

- [前往 CurseForge 下载](https://www.curseforge.com/minecraft/mc-mods/starfield-pastoral) · [前往 Modrinth 下载](https://modrinth.com/mod/starfield-pastoral)
- [加入 Discord](https://discord.gg/cnG3eE58Au)
- QQ 群：`961767762`

</details>
