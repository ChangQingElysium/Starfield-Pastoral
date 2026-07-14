# StardewCraft 0.5.x Source Audit

This document is the working audit table for 0.5.x gameplay-flow completeness.
It separates runtime foundations from content coverage and vanilla-semantic gaps.

Vanilla evidence was inspected from the local source archive in the sibling main
worktree:

`/Users/jiayuhan/游戏制作/StardewCraft/源文件`

StardewCraft implementation evidence is from this planning worktree:

`/Users/jiayuhan/游戏制作/StardewCraft-0.5-planning`

## Status Labels

- `Formed`: the core StardewCraft runtime exists and is usable.
- `Partial coverage`: the runtime exists, but content/key coverage is incomplete.
- `Semantic audit`: an implementation exists, but vanilla behavior/key semantics need line-by-line checking.
- `Missing system`: no real runtime/state path was found, beyond assets, flags, or adjacent systems.
- `Not audited`: evidence exists, but this pass did not inspect deeply enough to call parity.

## Source Scale Snapshot

| Area | Vanilla source scale | StardewCraft scale observed | Reading |
| --- | ---: | ---: | --- |
| Static quests | `Content/Data/Quests.json`: 66 entries | `data/stardewcraft/quests.json`: 14 entries | Runtime exists; static story content is sparse. |
| Daily billboard quests | Generated in source, not only JSON | `DailyQuestGenerator` exists | Needs formula/pool audit, not raw count comparison. |
| Special orders | `Content/Data/SpecialOrders.json`: 33 entries | 16 hardcoded normal-board orders | Normal board is mostly covered; Qi/desert/schema remain. |
| Secret notes | `Content/Data/SecretNotes.json`: 38 entries | no state/runtime found | Missing system. |
| Museum lost books | `Strings/Notes`: 21 lost-book notes plus `Missing` | no `LostBooksFound` / `lb_` state found | Missing system, distinct from 1.6 books. |
| Museum rewards | `Content/Data/MuseumRewards.json`: 31 entries | hand-authored reward registry, about 22 concrete rules | Formed, but not fully data-parity. |
| Mail content | `Content/Data/mail.json`: 179 keys | 131 mail entries, 41 direct vanilla-key overlaps | Runtime is strong; key mapping needs audit. |
| Events | 45 base event files, 258 event entries | 35 cutscene JSON files, 61 command classes | Runtime exists; content coverage is the large gap. |
| Monster slayer goals | vanilla data-driven Gil goals | `MonsterSlayerGoalRegistry`: 12 implemented goals | Formed for spawned monsters; reward parity needs audit. |

## System Audit Table

| System | Vanilla anchors | StardewCraft anchors | Status | What is actually left |
| --- | --- | --- | --- | --- |
| Quest runtime and quest log | `StardewValley.Quests/Quest.cs`, `QuestLog.cs` | `StardewQuest`, `QuestManager`, quest payloads, quest UI | `Formed` | Keep runtime; do not rewrite. Audit edge cases only when adding missing quest types. |
| Static story quests | `Content/Data/Quests.json` | `data/stardewcraft/quests.json` | `Partial coverage` | Add a source-ledger row for all 66 vanilla quests. Then add missing entries in dependency order, not numeric order. |
| Daily billboard quests | `Utility.getQuestOfTheDay`, `ItemDeliveryQuest`, `ResourceCollectionQuest` | `DailyQuestGenerator`, `BillboardScreen` | `Semantic audit` | Compare probability gates, NPC/item pools, rewards, acceptance day, and prize-ticket behavior against source. |
| Lost item quests | `LostItemQuest.cs`, quest IDs 100/102/107 | quest 102 exists; `LostItem` parses as Basic | `Partial coverage` | Implement a real `LostItemQuest` state: spawned/retrievable world item, NPC turn-in, thank-you dialogue, item cleanup. |
| Secret lost item quests | `SecretLostItemQuest.cs`, quest IDs 128/129 | parsed as Basic | `Missing system` | Needs friendship-gated secret item turn-in, mutual exclusion, reward friendship, and source-keyed dialogue. |
| Quest story auto-triggering | source new-day/mail/event hooks | `QuestManager`, `MailService`, cutscene commands, fixed interior region events | `Partial coverage` | Early quest flow now uses mail/event/location gates for 13-17; expand only after each selected trigger is source-mapped. |
| Mail runtime | `Farmer.mailReceived`, `mailForTomorrow`, `mailbox`, `Game1.addMail*` | `MailService`, `MailRegistry`, `PlayerStardewData` mail fields | `Formed` | Preserve. Main risk is not runtime; it is ID semantics and delivery triggers. |
| Mail content and key semantics | `Content/Data/mail.json` | `data/stardewcraft/mail/*.json` | `Semantic audit` | Build a map: vanilla key, current equivalent key, trigger source, readable/no-letter, attachment/effect. Do not import blindly. |
| Cutscene runtime | `Event.cs` `DefaultCommands`, `Data/Events/*` | `EventData`, `EventCommandFactory`, runtime/server evaluators | `Formed` | Runtime is good enough for hand-authored parity events. Maintain a command coverage matrix before adding large batches. |
| Event content coverage | 258 base event entries | 35 cutscene JSON files | `Partial coverage` | Prioritize progression trunk events first, then NPC heart-event batches. Avoid treating festival cutscenes as 0.5 blockers. |
| Event preconditions | event key syntax, `Preconditions.cs`, `GameStateQuery.cs` | client/server `PreconditionEvaluator` | `Semantic audit` | Add missing condition types only when a selected source event requires them. Unknown conditions should not silently pass in new parity work. |
| Special orders normal board | `SpecialOrders.json` Willy through Gunther normal orders | `SpecialOrderDefinitions` first 16 orders | `Formed` | Audit unlock day, refresh seed, repeatability, required tags, item tags, reward mail, and dropbox coordinates. |
| Special orders advanced/Qi/desert | `SpecialOrders.json` Caroline/Willy2/Qi/desert orders | current schema lacks several objective/reward/duration types | `Partial coverage` | Add `OneDay`, `ThreeDays`, `Gift`, `JKScore`, `ReachMineFloor`, `Custom`, `Gems`, `Object`, and then content. |
| Community Center and Junimo Notes | `CommunityCenter.cs`, Town event 611439, Wizard event 112 | `communitycenter/*`, `JunimoNoteBlock`, bundle UI, restore services | `Formed` | Treat as flow-audit work: verify gates, flags, area completion, Joja branch, and final celebration, not a rebuild. |
| Museum donation | `LibraryMuseum.cs`, donation state, museum layout | `MuseumDonationData`, exhibit stands, Gunther service | `Formed` | Preserve current per-player design unless a source conflict is found. Audit placement/rearrange/reward UX separately. |
| Museum rewards | `Content/Data/MuseumRewards.json` | `MuseumRewardRegistry` | `Partial coverage` | Fill missing specific rewards and `museumComplete`; consider data-driven parsing only if manual parity becomes brittle. |
| Museum lost books | `LibraryMuseum.totalNotes`, `LostBooksFound`, `readNote`, `lb_` flags | no matching state/runtime found | `Missing system` | Implement separately from 1.6 books: lost book find, counter, library note markers, `Strings/Notes`, `lb_` flags. |
| Secret notes | `secretNotesSeen`, `HasMagnifyingGlass`, `(O)79`, `(O)842`, `SecretNotes.json` | only power/menu/object-data traces found | `Missing system` | Add state, drop sources, note item, reading UI, collection page, and note-specific rewards/quests. |
| 1.6 books and bookseller | 1.6 book/power systems | `BookDefinition`, `BookService`, bookseller UI/data | `Formed` | Keep separate from museum lost books. Only audit if 0.5 chooses to touch book-power progression. |
| Monster slayer and Gil goals | `MonsterSlayerQuests` source/data | kill counts, `MonsterSlayerGoalRegistry`, `MarlonService`, Gil screen | `Partial coverage` | Runtime exists for spawned monster tags. Audit missing vanilla goals/rewards, hats/dialogue-only rewards, and Adventurer Guild unlock. |
| Marlon item recovery/passout | death/passout mail and Marlon recovery | `PassOutService`, `MarlonService`, `Book_Marlon` discount | `Semantic audit` | Compare source billing, mail text, item loss/recovery, and event timing when 0.5 touches death flow. |
| NPC friendship/dialogue/schedules | NPC data, events, mail, schedules | `npc/*`, friendship runtime, dialogue data, schedule runtime | `Not audited` | Must become its own audit before NPC heart-event batches. Do not infer completeness from data volume. |
| Collections/game menu progression | collections pages, secret notes collection, powers | `StardewGameMenuScreen`, powers tab, book/power flags | `Partial coverage` | Add Secret Notes/Lost Books collection surfaces only after state systems exist. |

## Player-Facing System Map

This is the planning layer the 0.5.x branch should use. The technical table above is
evidence; this section is how work should be split. A system here means something a
player would recognize as a Stardew Valley feature, not an internal Java service.

| System | Vanilla anchors to audit | Current reading | Planning note |
| --- | --- | --- | --- |
| Quest system | `Content/Data/Quests.json`, `Quest.cs`, quest subclasses, `Utility.getQuestOfTheDay` | Runtime exists; static content and lost-item semantics are incomplete. | First recommended focus. Finish one quest family at a time. |
| NPC friendship and story | `Content/Data/Characters.json`, `Characters/Dialogue`, `Characters/schedules`, `Content/Data/Events/*` | Runtime/data exist but not audited as a full player system. | Do after quest foundations, because many events depend on quest/mail flags. |
| Community Center and Joja route | `CommunityCenter.cs`, `Bundles.json`, Town/CC events, Joja completion flow | Runtime is formed, but flow parity needs audit. | Treat as its own system, not just an event batch. |
| Museum and library | `LibraryMuseum.cs`, `MuseumRewards.json`, `Strings/Notes` | Donation/reward runtime exists; lost books are missing. | Split into donation rewards and lost-book reading if PRs get large. |
| Secret notes | `SecretNotes.json`, note item behavior, Magnifying Glass unlock, note reward code | Missing runtime/state. | Needs source-ledger first because many notes are unique one-off behaviors. |
| Wizard, witch, and magic buildings | Wizard events/quests, Witch Swamp flow, farm building data, obelisks/Junimo Hut/Gold Clock | Not audited in this pass. | Player-facing system, likely mid/late 0.5 after CC/Joja dependencies are clear. |
| Mines and Adventurer's Guild | Mine progression, Marlon/Gil, monster slayer goals, item recovery | Runtime exists in pieces. | Audit as a system when quest flow reaches mine/Marlon dependencies. |
| Desert, bus, Skull Cavern, and casino | Bus repair, desert locations, Sandy, Skull Cavern unlock, casino questline | Not audited in this pass. | Large enough to be its own stage; likely not part of first 0.5 slice unless chosen deliberately. |
| Sewer, Krobus, Dwarf, and special access NPCs | Rusty Key, sewer location, Krobus shop, Dwarf translation/shops | Not audited in this pass. | Cross-links museum, mines, NPC, and wizard systems; keep as a separate system group. |
| Farm buildings and Robin construction | `Buildings.json`, Robin shop/construction menus, upgrade flow | Not audited in this pass. | Separate normal farm construction from wizard magic buildings. |
| Collections and books/powers | collection tabs, powers, 1.6 books, bookseller | Partially formed for books/powers. | Keep 1.6 books separate from museum lost books. |
| Late-game island/Qi systems | Island locations, walnuts, Qi room, advanced orders | Not audited in this pass. | Probably defer beyond first 0.5 unless the release scope changes. |
| Movie theater | Abandoned Joja/Movie Theater events, concessions, NPC invites | Not audited in this pass. | Post-CC/Joja system; likely later than quest/NPC/museum basics. |
| Festival-independent shops and world events | Traveling cart, night market-like shops, trains, meteorites, earthquake, bathhouse | Not audited in this pass. | Some are 0.4.x/festival-adjacent; include only when they affect 0.5 progression. |

## Development Path

0.5.x should move system by system. Pick one player-facing system, finish the
source ledger and implementation path for that system, then move to the next. Mail,
events, rewards, flags, items, and UI are support work inside the selected system.

Every system stage should follow the same loop:

1. Source audit: vanilla source files, Wiki cross-check, data keys, and player-facing behavior.
2. StardewCraft audit: existing runtime, data, UI, state, and content coverage.
3. System ledger: row per vanilla behavior or key, with current status and proposed PR.
4. Implementation PRs: narrow slices that complete that one system.
5. Regression pass: new-save or focused test plan proving the system works end to end.

### Stage 1: Quest System

Goal: make vanilla quests feel like a real StardewCraft system before expanding sideways.

In scope:

- all 66 static vanilla quest rows;
- current StardewCraft quest entries and quest log behavior;
- daily billboard quest generation;
- lost item and secret lost item quest behavior;
- quest rewards, turn-ins, progress tracking, and chain triggers;
- quest dependencies on mail, NPCs, items, mine access, and Community Center state.

Suggested PR sequence:

- `docs: add vanilla quest system ledger`
- `quest: audit daily billboard generation`
- `quest: implement lost item quest behavior`
- `quest: implement secret lost item quest behavior`
- `quest: expand vanilla quest content batch 1`

Exit criteria:

- every vanilla quest has a ledger row;
- every implemented quest has source evidence, trigger, progress path, turn-in path, reward path, and save state;
- missing quests are marked blocked or deferred with a concrete dependency.

Current quest-batch rule:

| IDs | Source-backed original flow | StardewCraft status | Missing/change needed | Story/event collaboration |
| --- | --- | --- | --- | --- |
| `18` | `MineShaft.cs` completes quest 18 on floor 120 reward chest and gives SpecialItem(4). | Implemented as `Location`/`MineFloor:120`; floor 120 chest already gives Skull Key. | Focused runtime test: reach floor 120, complete 18, receive Skull Key. | No cutscene. |
| `19` | `SpecialItem.cs` case 4 sets Skull Key and adds quest 19; `GameLocation.cs` SkullDoor completes 19 and queues `skullCave`. | Implemented: Skull Key item accepts 19; desert Skull Cavern entrance completes 19 and queues `skullCave`. | Focused runtime test on new and old saves with Skull Key. | No cutscene; only object dialogue. |
| `20` | `mail.json` `skullCave` attaches quest 20; `MineShaft.CheckForQiChallengeCompletion` completes at deepest mine level 145 and queues `QiChallengeComplete`. | Mechanically implemented as `Location`/`MineFloor:145`; `skullCave` mail accepts 20; completion queues reward mail with 10000g. This is not content-complete because Qi as a character/system is not implemented. | Focused runtime test: read mail, reach Skull Cavern level 25, receive reward letter. Separate dependency: Qi identity, later Qi questline/casino/desert-system presentation. | No cutscene in this vanilla quest, but Qi system still needs its own planning/co-write pass. |
| `21` | Farm event 91: Marnie morning visit adds quest 21; AnimalShop event 92 consumes Cave Carrot, removes quest, gives Marnie friendship. | Not implemented. | Needs wake-up event, AnimalShop completion event, item/time/event preconditions, `remove_item`, and friendship command coverage check. | Yes. Need co-write Marnie visit/completion event and MC staging. |
| `22` | Farm event 93: Jodi morning visit adds quest 22; SamHouse events 94/95 consume Largemouth Bass and remove quest, with year 1/year 2 variants. | Not implemented. | Needs wake-up event, SamHouse completion events, item/time/year/event preconditions, `remove_item`, and staging for family dinner. | Yes. Need co-write Jodi invitation/dinner event and MC staging. |

### Stage 2: NPC Friendship and Story System

Goal: treat NPC progression as its own system instead of scattering heart events through unrelated PRs.

In scope:

- friendship points/hearts;
- daily dialogue and schedule dependencies;
- gift taste and birthday behavior if not already complete;
- heart-event trigger conditions;
- event seen flags and event rewards;
- NPC mail and post-event follow-ups.

Suggested PR sequence:

- `docs: add npc story system ledger`
- `npc: audit friendship gift and dialogue parity`
- `events: add first npc heart-event batch`
- `mail: add npc progression mail batch`

Exit criteria:

- first NPC batch can be played from meet -> friendship gain -> event trigger -> reward/follow-up;
- missing trigger/precondition types are listed, not silently ignored.

### Stage 3: Community Center and Joja Route System

Goal: verify the largest vanilla progression branch as a whole player system.

In scope:

- Junimo note discovery and translation;
- bundle donation, room completion, and reward flow;
- town repair unlocks such as bridge, minecarts, bus, greenhouse, and quarry;
- Joja membership, purchase route, and route exclusivity;
- final completion/celebration state.

Suggested PR sequence:

- `docs: add community center and joja flow ledger`
- `communitycenter: audit bundle completion and room rewards`
- `joja: audit membership and purchase route`
- `progression: fix cc joja route blockers`

Exit criteria:

- a new save can enter the route, make progress, receive repairs/rewards, and reach a coherent completed or deferred state.

### Stage 4: Museum and Library System

Goal: finish Gunther/library-facing progression as a system.

In scope:

- artifact/mineral donation;
- museum display and rearrange behavior;
- Gunther rewards from `MuseumRewards.json`;
- lost book discovery, `LostBooksFound`, `lb_<id>` flags, and library reading;
- rusty key or other museum-linked unlocks if confirmed by source audit.

Suggested PR sequence:

- `docs: add museum and library system ledger`
- `museum: complete reward parity`
- `museum: add lost book discovery and reading`
- `museum: audit Gunther linked unlocks`

Exit criteria:

- donation/reward flow and lost-book flow are both playable and visibly separated from 1.6 book powers.

### Stage 5: Secret Notes System

Goal: add the Magnifying Glass and secret-note loop as its own gameplay system.

In scope:

- Magnifying Glass unlock;
- secret note item/drop sources;
- read behavior and seen-note state;
- `SecretNotes.json` content;
- note-specific quests, map clues, rewards, and collection UI.

Suggested PR sequence:

- `docs: add secret notes system ledger`
- `secretnotes: add note item state and reading UI`
- `secretnotes: add source note rewards batch 1`
- `secretnotes: add collection tab integration`

Exit criteria:

- notes can drop, be read once, persist as seen, and produce their source-backed effects.

### Stage 6: Wizard, Witch, and Magic Buildings System

Goal: handle the Wizard line as a coherent late-progression system.

In scope:

- Wizard introduction and tower access;
- Dark Talisman / sewer / Krobus dependency;
- Witch Swamp, Goblin Problem, magic ink;
- Wizard building shop;
- obelisks, Junimo Hut, Gold Clock, and any accepted placeholders.

Suggested PR sequence:

- `docs: add wizard magic building system ledger`
- `events: audit wizard witch swamp questline`
- `building: add wizard construction flow`
- `building: add magic building batch 1`

Exit criteria:

- dependencies are explicit and the first magic-building release has a clear complete/deferred boundary.

### Stage 7: Mines and Adventurer's Guild System

Goal: audit the mine/Marlon/Gil loop as a player progression system.

In scope:

- mine access and floor progression;
- Marlon intro and Adventurer's Guild entry;
- monster slayer goals and Gil rewards;
- item recovery/passout services;
- mine-linked quests and mail.

Suggested PR sequence:

- `docs: add mines and adventurer guild system ledger`
- `mines: audit floor access and Marlon intro`
- `adventurer: complete monster slayer reward parity`
- `marlon: audit item recovery and passout flow`

Exit criteria:

- the player can unlock, progress, and collect source-backed guild rewards without hidden dead ends.

### Stage 8: Desert, Bus, Skull Cavern, and Casino System

Goal: handle the desert branch only when its prerequisites are ready.

In scope:

- bus repair and desert access;
- Sandy and desert shop behavior;
- Skull Cavern entry and quest dependencies;
- casino membership questline;
- desert-linked special orders if included.

Suggested PR sequence:

- `docs: add desert skull cavern casino ledger`
- `desert: audit bus repair and sandy shop`
- `skullcavern: audit access and quest dependencies`
- `casino: add membership questline`

Exit criteria:

- desert access is not just a location teleport; it has the source-backed unlock and follow-up systems around it.

### Stage 9: Late-Game Island, Qi, Movie Theater, and Other Deferred Systems

Goal: keep large late-game systems visible without letting them destabilize early 0.5.

Potential systems:

- Ginger Island and Golden Walnuts;
- Qi room, Qi gems, and advanced orders;
- Movie Theater;
- Sewer/Krobus/Dwarf if not finished in earlier dependency work;
- normal farm construction if it becomes progression-critical;
- special world events and non-festival shops.

Suggested PR sequence:

- create one source ledger per selected system;
- do not merge broad late-game runtime rewrites until the system's release boundary is chosen.

Exit criteria:

- each deferred system has an explicit owner stage, not a vague "later" bucket.

### Integration Pass After Each Stage

After every system stage:

- play or simulate the relevant source-backed flow;
- verify source evidence, trigger path, state path, UI/feedback, and reward path;
- update the system ledger before moving to the next system.

## Suggested Milestones

| Milestone | Release shape | Must include | Should not include |
| --- | --- | --- | --- |
| `0.5.0-alpha` | first player-facing system foundation | quest system ledger, daily quest audit, LostItem/SecretLostItem plan or implementation | broad NPC heart-event flood |
| `0.5.0-beta` | quest system playable slice | source-backed quest batches with working progress, turn-in, rewards, and dependencies | secret notes/museum/wizard work unless intentionally selected |
| `0.5.0` | one coherent completed system | quest system reaches accepted 0.5 boundary and has a regression checklist | every vanilla system |
| `0.5.1` | next selected system | likely NPC friendship/story or Community Center/Joja, chosen after 0.5.0 | unplanned late-game branches |
| `0.5.2+` | system-by-system expansion | museum/library, secret notes, wizard buildings, mines/guild, desert/casino as chosen | unrelated festival churn |

## Working Rules for Execution Windows

- Start from a player-facing system and its source-ledger row, not a vague feature name.
- Confirm the vanilla source path before editing.
- Reuse existing runtime first.
- If a missing dependency is discovered, either add it as a small foundation PR or mark the
  row blocked. Do not bury it inside a large content PR.
- Every PR should name its vanilla source evidence and its affected progression state.
