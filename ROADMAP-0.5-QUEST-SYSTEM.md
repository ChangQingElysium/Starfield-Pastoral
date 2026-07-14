# StardewCraft 0.5.x Quest System Focus

This is the focused planning document for the 0.5.x quest-system stage.

The goal is not "add some quest content". The goal is to make quests a complete
player-facing StardewCraft system: quest acquisition, quest log, progress hooks,
completion, rewards, follow-up quests, daily help-wanted quests, and source-backed
special quest behavior.

Vanilla evidence comes from:

- `/Users/jiayuhan/游戏制作/StardewCraft/源文件/Content/Data/Quests.json`
- `/Users/jiayuhan/游戏制作/StardewCraft/源文件/StardewValley.Quests/*.cs`
- `/Users/jiayuhan/游戏制作/StardewCraft/源文件/StardewValley/Utility.cs`
- `/Users/jiayuhan/游戏制作/StardewCraft/源文件/StardewValley.Menus/Billboard.cs`
- `/Users/jiayuhan/游戏制作/StardewCraft/源文件/StardewValley.Menus/QuestLog.cs`

StardewCraft evidence comes from:

- `src/main/resources/data/stardewcraft/quests.json`
- `src/main/java/com/stardew/craft/quest/*`
- `src/main/java/com/stardew/craft/client/gui/quest/*`
- `src/main/java/com/stardew/craft/npc/runtime/NpcInteractionService.java`
- `src/main/java/com/stardew/craft/mail/MailService.java`

## System Boundary

In scope for the quest-system stage:

- static vanilla quest definitions from `Content/Data/Quests.json`;
- quest log persistence, sync, and reward claiming;
- daily help-wanted billboard generation and acceptance;
- quest progress hooks: item received, item offered to NPC, recipe crafted, NPC socialized,
  monster slain, fish caught, warped, building exists, mine floor reached;
- `LostItemQuest` and `SecretLostItemQuest` behavior;
- quest chain behavior through `nextQuests`;
- quest acquisition from mail, cutscenes/events, world interactions, and day-start triggers.

Out of scope unless required by a selected quest:

- full NPC heart-event coverage;
- the complete secret-notes system;
- the complete wizard-building system;
- special orders as a separate board/system;
- Community Center/Joja as a full route system.

Those systems can provide dependencies, but this stage should not turn into all of 0.5.x.

## Vanilla Facts

### Static Quest Data

`Content/Data/Quests.json` has 66 entries.

Type count:

| Type | Count |
| --- | ---: |
| `Basic` | 24 |
| `ItemDelivery` | 24 |
| `LostItem` | 3 |
| `Location` | 3 |
| `ItemHarvest` | 3 |
| `SecretLostItem` | 2 |
| `Crafting` | 2 |
| `Building` | 2 |
| `Social` | 1 |
| `Monster` | 1 |
| `Fishing` | 1 |

`Quest.getQuestFromId` parses the slash-delimited data and constructs typed quest
classes. Important source anchors:

- `Quest.cs:282-465`: type dispatch and data parsing;
- `Quest.cs:351-366`: `ItemDelivery`;
- `Quest.cs:368-387`: `Monster`;
- `Quest.cs:400-411`: `ItemHarvest`;
- `Quest.cs:413-424`: `LostItem`;
- `Quest.cs:426-437`: `SecretLostItem`;
- `Quest.cs:446-458`: `nextQuests`.

### Completion Semantics

`Quest.questComplete` is not just a boolean flip.

Source anchors:

- `Quest.cs:580-638`: completion flow;
- daily quests increment `BillboardQuestsDone`;
- every third completed daily quest gives a Prize Ticket;
- `nextQuests` are added immediately on completion;
- quests with no money/reward text are removed from the quest log;
- `questComplete_<id>` dialogue event is generated.

### Lost Item Semantics

`LostItemQuest` is a real type, not an item-delivery alias.

Source anchors:

- `LostItemQuest.cs:51-63`: stores NPC, item id, source location, tile X/Y;
- `LostItemQuest.cs:77-92`: when the player enters the source location, the quest item
  is placed as an overlay object;
- `LostItemQuest.cs:104-125`: picking up the item marks `itemFound` and changes the
  objective to return to the NPC;
- `LostItemQuest.cs:128-145`: talking to the NPC completes the quest, removes the item,
  shows thank-you dialogue, and gives +250 friendship.

Vanilla `LostItem` rows:

| ID | Title | Conditions |
| --- | --- | --- |
| `100` | Robin's Lost Axe | `Robin (O)788 Forest 110 81` |
| `102` | Mayor's "Shorts" | `Lewis (O)789 AnimalShop 13 7` |
| `107` | Blackberry Basket | `Linus (O)790 Backwoods 27 27` |

### Secret Lost Item Semantics

`SecretLostItemQuest` is also a real type. It is related to secret-note outcomes, but
the quest runtime itself is still part of the quest system.

Source anchors:

- `SecretLostItemQuest.cs:38-45`: stores NPC, item id, friendship reward, and exclusive quest id;
- `SecretLostItemQuest.cs:62-75`: marks item found when the required item enters inventory;
- `SecretLostItemQuest.cs:78-95`: talking to the NPC completes the quest, removes the item,
  shows thank-you dialogue, and gives configured friendship;
- `SecretLostItemQuest.cs:98-114`: completion removes this quest and marks the exclusive
  paired quest for destruction.

Vanilla `SecretLostItem` rows:

| ID | Title | Conditions |
| --- | --- | --- |
| `128` | `...` | `Abigail (O)191 100 129` |
| `129` | `...` | `Caroline (O)191 50 128` |

### Daily Help-Wanted Quest Semantics

`Utility.getQuestOfTheDay` generates the daily billboard quest.

Source anchors:

- `Utility.cs:3195-3246`: probability table;
- first day returns no quest;
- `d < 0.08`: `ResourceCollectionQuest`;
- `d < 0.20` and mine has been entered and days played > 5: `SlayMonsterQuest`;
- `d < 0.50`: no quest;
- `d < 0.60`: `FishingQuest`;
- `d < 0.66` and Monday and no active `SocializeQuest`: `SocializeQuest`;
- else: `ItemDeliveryQuest`.

`Billboard.cs:366-371` marks the generated quest as daily, sets it accepted, and adds it
to the player's quest log.

## StardewCraft Facts

### What Already Exists

The quest system has a real formed runtime:

- `QuestDataLoader` loads `data/stardewcraft/quests.json` in SDV slash-delimited format.
- `StardewQuest` persists id/type/title/description/objective/reward/accepted/completed/
  daily/show-new/cancel/destroy/days-left/next-quests.
- `QuestManager` owns quest log, completed quest ids, daily quest, billboard stats, day-start
  triggers, cleanup, sync, and NBT persistence.
- `StardewQuestEvents` is the facade for progress hooks.
- Implemented quest subclasses: `CraftingQuest`, `ItemDeliveryQuest`, `ResourceCollectionQuest`,
  `ItemHarvestQuest`, `GoSomewhereQuest`, `HaveBuildingQuest`, `FishingQuest`,
  `SlayMonsterQuest`, `SocializeQuest`.
- `DailyQuestGenerator` already follows the broad vanilla probability table.
- `BillboardScreen`, `QuestLogScreen`, `AcceptQuestPayload`, and `ClaimRewardPayload` provide
  player-facing UI and reward claiming.
- `NpcInteractionService` intercepts matching item-delivery quests before normal gifting.
- `MailService` can accept a quest from a mail entry.
- Cutscene/server actions can call `QuestManager.acceptQuest` and remove active quests.

### Main Gaps

These are the quest-system gaps that matter before adding large content batches:

1. Static quest data coverage is small: StardewCraft has 14 static entries versus vanilla's 66.
2. `QuestDataLoader` maps `LostItem` and `SecretLostItem` to `TYPE_BASIC`, so those vanilla
   semantics are missing even if entries are copied.
3. Current quest `102` is implemented as `ItemDelivery`, while vanilla quest `102` is
   `LostItem` with source location/tile behavior.
4. `StardewQuest.createByType` has no type ids/classes for `LostItemQuest` or
   `SecretLostItemQuest`.
5. Some vanilla `Basic` quests are not self-completing; they are completed/advanced by
   mail, locations, events, NPC interactions, or object interactions. They need a trigger
   ledger before content import.
6. Daily quest generation uses hand-authored NPC/item/fish/resource pools. It matches the
   broad probability shape, but not yet source/Wiki pool parity.
7. `ItemDeliveryQuest`, `FishingQuest`, `ResourceCollectionQuest`, and `SlayMonsterQuest`
   have report-to-NPC behavior, but each should be audited against source before declaring
   parity.
8. Quest acquisition is scattered across day-start triggers, mail, cutscenes, fixed interior
   regions, and world interactions. The first implementation batch should document triggers
   before adding rows.

## Current Static Quest Coverage

The full row-by-row table lives in
[ROADMAP-0.5-QUEST-LEDGER.md](ROADMAP-0.5-QUEST-LEDGER.md). The short summary below
is kept only as a quick orientation.

| ID | Vanilla type/title | StardewCraft status |
| --- | --- | --- |
| `1` | `Location` / Meet The Wizard | Present, but currently `Basic`; needs source trigger audit. |
| `6` | `ItemHarvest` / Getting Started | Present. |
| `7` | `Building` / Raising Animals | Present. |
| `8` | `Crafting` / Advancement | Present; uses StardewCraft `scarecrow` recipe id. |
| `9` | `Social` / Introductions | Present. |
| `10` | `Basic` / Copper Ore | Vanilla data exists, but source main flow appears to skip it; current flow goes `copperFound` -> Clint furnace event -> `11`. |
| `11` | `Crafting` / Forging Ahead | Present; added by Clint furnace event and uses StardewCraft `furnace` recipe id. |
| `12` | `ItemHarvest` / Smelting | Present; machine harvest now reports item-received progress. |
| `13` | `Basic` / To The Beach | Present; `spring_2_1` mail attaches quest, Willy beach event removes it and sets `NOQUEST_13`. |
| `14` | `Basic` / Explore The Mine | Present as `Location`/mine-floor behavior; Marlon mine intro adds it, floor 5 queues `guildQuest`. |
| `15` | `Monster` / Initiation | Present; `guildQuest` mail attaches it and completion chains to `16`. |
| `16` | `Location` / Initiation | Present; fixed adventurer guild interior entry fires the vanilla `AdventureGuild` location key. |
| `17` | `Basic` / Deeper In The Mine | Present as `Location`/mine-floor behavior and remains chained from `14`. |
| `18` | `Basic` / To The Bottom? | Present as `Location`/mine-floor behavior; floor 120 completes it and the existing reward chest gives Skull Key. |
| `19` | `Basic` / The Skull Key | Present; Skull Key item acquisition adds it, and first desert Skull Cavern entrance completes it and queues `skullCave`. |
| `20` | `Basic` / Qi's Challenge | Mechanically present as `Location`/`MineFloor:145`; `skullCave` mail attaches it and completion queues `QiChallengeComplete`, but Qi as a character/system is not complete, so this is not a full content-complete row. |
| `21` | `Basic` / Marnie's Request | Missing; requires Marnie farm visit event 91 and AnimalShop completion event 92. |
| `22` | `Basic` / Fish Casserole | Missing; requires Jodi farm visit event 93 and SamHouse completion events 94/95. |
| `23` | `Location` / Archaeology | Missing. |
| `24` | `Basic` / Archaeology | Missing. |
| `25` | `Basic` / How To Win Friends | Missing. |
| `26` | `Basic` / Rat Problem | Missing. |
| `27` | `Basic` / Goblin Problem | Missing. |
| `28` | `Basic` / Dark Talisman | Missing. |
| `29` | `Basic` / Strange Note | Missing; depends on secret-note flow. |
| `30` | `Basic` / Cryptic Note | Missing; depends on secret-note flow. |
| `31` | `Basic` / A Winter Mystery | Missing; depends on Magnifying Glass/secret notes. |
| `100` | `LostItem` / Robin's Lost Axe | Missing; needs `LostItemQuest`. |
| `101` | `ItemDelivery` / Jodi's Request | Missing. |
| `102` | `LostItem` / Mayor's "Shorts" | Present as `ItemDelivery`; should migrate or explicitly justify divergence. |
| `103`-`126` | NPC `ItemDelivery` requests | Missing except local special handling around `102`; add after item/NPC availability audit. |
| `127` | `Basic` / Haley's Cake-Walk | Missing. |
| `128`-`129` | `SecretLostItem` pair | Missing; needs `SecretLostItemQuest` plus secret-note dependency. |
| `130` | `Basic` / The Pirate's Wife | Missing; late-game Ginger Island dependency. |
| `131` | `Fishing` / Willy's Challenge | Missing; special late quest. |
| `132` | `ItemHarvest` / Getting Started | Missing; alternate/newer quest row, needs source trigger audit. |
| `133` | `Building` / Feeding Animals | Missing. |
| `134` | `Basic` / The Giant Stump | Missing; 1.6 giant stump dependency. |

## Recommended Development Path

### PR 1: Quest System Ledger

Purpose: make the work executable without guessing.

Deliverables:

- full 66-row quest ledger;
- columns: id, vanilla type, title, conditions, next quest, money, cancelable, current
  StardewCraft equivalent, trigger owner, dependency, implementation status;
- split static quests into early-game, NPC request, secret-note-dependent, wizard/magic,
  desert/Qi/island, and 1.6/deferred groups.

Verification:

- no code verification needed;
- compare ledger count against `Quests.json` count of 66.

### PR 2: LostItemQuest Runtime

Purpose: implement the three vanilla `LostItem` quests correctly.

Deliverables:

- add `LostItemQuest` class;
- add type id/load/save support;
- update `QuestDataLoader` parsing for `LostItem`;
- add a MC equivalent for source location/tile placement or an accepted world-anchor
  abstraction;
- migrate quest `102` away from plain `ItemDelivery` if the runtime supports it;
- add quest rows `100`, `102`, and `107` when their item/location anchors are available.

Open design question:

- MC has blocks/entities rather than SDV overlay objects. The implementation needs a chosen
  equivalent: spawned pickup entity, invisible marker plus item pickup, or location-specific
  world event. Do not hardcode only Mayor's Shorts if the runtime can support all three.

Verification:

- accept quest;
- enter source location;
- see/retrieve item once;
- quest objective changes to return-to-NPC;
- NPC turn-in consumes item, gives money/friendship, and completes/removes quest correctly.

### PR 3: SecretLostItemQuest Runtime

Purpose: support quest ids `128` and `129` without pretending the whole secret-note system
is done.

Deliverables:

- add `SecretLostItemQuest` class;
- add type id/load/save support;
- update `QuestDataLoader` parsing for `SecretLostItem`;
- support `friendshipReward` and `exclusiveQuestId`;
- ensure completion removes/destroys the paired quest;
- leave acquisition blocked behind the future Secret Notes system if needed.

Verification:

- item entering inventory marks found;
- talking to Abigail/Caroline with item completes the right quest;
- the exclusive paired quest is removed or marked destroyed;
- item is consumed and friendship reward is applied.

### PR 4: Early Static Quest Batch

Purpose: improve the new-save quest flow after the missing quest types are real.

Candidate scope:

- `8` Advancement;
- `13` To The Beach; done in the first early static batch;
- `16` Adventurer's Guild location follow-up; done in the first early static batch;
- `23`/`24` Archaeology if museum basics are ready;
- `25` How To Win Friends if friendship reward/trigger is ready.

Do not include:

- `27`/`28` wizard late chain;
- `29`/`30`/`31` secret-note chain;
- `19`/`20` as content-complete desert/Qi work; their mechanics may be connected early, but Qi
  identity and broader Qi/desert presentation belong to a later system pass;
- `130` island;
- `134` giant stump.

Verification:

- new save can receive, progress, complete, and chain the selected early quests without
  broken dependencies.

### PR 5: Daily Billboard Audit

Purpose: make daily help-wanted quests source-backed rather than only broadly shaped.

Deliverables:

- compare `DailyQuestGenerator` with `Utility.getQuestOfTheDay`;
- audit NPC pool, fish/item/resource pools, rewards, social quest uniqueness, mine gate,
  first-day null behavior, and festival-day suppression;
- decide whether StardewCraft should keep MC-specific item pools where vanilla items do
  not exist yet, with explicit placeholders.

Verification:

- deterministic day generation;
- no quest on day 1;
- no quest for the `d < 0.50` null range;
- daily quest acceptance, timeout, completion, prize ticket, and calendar marker still work.

### PR 6: NPC Request Content Batch

Purpose: add the 24 vanilla `ItemDelivery` request rows only after item/NPC availability is
clear.

Deliverables:

- item availability audit for ids `101` through `126`;
- target NPC availability audit;
- per-row decision: implement now, placeholder, or defer;
- add source-backed target messages.

Verification:

- delivery confirmation appears before gift flow;
- correct item count is consumed;
- money and friendship rewards apply;
- thank-you dialogue uses source-backed target message.

## First Decision

Recommended next action: do PR 1, the 66-row quest-system ledger. It is the lowest-risk
step and will stop the implementation stage from mixing early quests, NPC requests,
secret-note quests, and late-game systems into one branch.
