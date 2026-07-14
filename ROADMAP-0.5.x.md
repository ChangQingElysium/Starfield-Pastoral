# StarfieldPastoral 0.5.x Roadmap

0.5.x is the gameplay-flow completeness line: vanilla quests, events, story progression,
and other systems that make a StardewCraft save feel connected across days, NPCs, mail,
locations, rewards, and unlocks.

This file is the planning spine for the long-lived 0.5.x Draft PR. It is intentionally
not a final feature list yet. The first job of this branch is to keep discovery,
decisions, and execution handoffs organized while 0.4.x festival work continues to land
on `main`.

## Branch Model

- `main`: current releasable development line. 0.4.x festival PRs should merge here when ready.
- `codex/0.5-planning`: long-lived 0.5.x planning and integration branch.
- Short 0.5.x execution branches should be created from the latest `main` or from this
  planning branch only when they truly depend on accepted 0.5.x groundwork.

When a 0.4.x PR lands on `main`, merge `main` back into `codex/0.5-planning` promptly:

```bash
git checkout codex/0.5-planning
git fetch origin
git merge origin/main
```

## Current Scope

Known direction:

- improve vanilla quest coverage;
- improve vanilla story/event progression coverage;
- complete player-facing SDV systems one at a time: quests, NPC friendship/story,
  secret notes, museum/library, wizard buildings, and other progression systems;
- use technical runtime areas such as mail, flags, rewards, schedules, and unlocks as
  support layers for those player-facing systems, not as standalone planning phases;
- prefer source-first parity research before implementation;
- avoid speculative rewrites of already-working 0.4.x festival systems.

Current audit artifact:

- [ROADMAP-0.5-SOURCE-AUDIT.md](ROADMAP-0.5-SOURCE-AUDIT.md): source-backed system table,
  status labels, and proposed development path.
- [ROADMAP-0.5-QUEST-SYSTEM.md](ROADMAP-0.5-QUEST-SYSTEM.md): focused quest-system
  source audit, current StardewCraft status, and first implementation path.
- [ROADMAP-0.5-QUEST-LEDGER.md](ROADMAP-0.5-QUEST-LEDGER.md): human-readable 66-row
  vanilla quest table with current StardewCraft status and first-pass priority.

Unknown on purpose:

- exact first 0.5.x feature batch;
- final PR split;
- which vanilla quest/event systems are in scope for the first public 0.5.x release;
- which content should be deferred past 0.5.x.

## Planning Lanes

Use these lanes to sort research before creating implementation PRs. A lane should
describe a player-recognizable SDV system. Runtime work belongs inside the system
that needs it.

### 1. Quest System

Questions to answer:

- Which vanilla quest families already exist in StardewCraft?
- Which quest states need persistence, sync, UI presentation, reward handling, and turn-in logic?
- Which mail, event, NPC, and item dependencies block quest parity?

Initial output should be a gap map, not code.

### 2. NPC Friendship and Story System

Questions to answer:

- Which NPCs need first-pass friendship, dialogue, schedule, gift, and heart-event coverage?
- Which vanilla events depend on friendship, mail, location, time, weather, season, year, or previous events?
- Which story beats are blocked by missing locations, items, shops, or special systems?

Initial output should rank NPCs by dependency risk and choose a first NPC batch.

### 3. Secret Notes and Museum Library System

Questions to answer:

- How does the vanilla Magnifying Glass unlock, note drop, note read, and reward flow work?
- How are museum lost books found, counted, displayed, and remembered?
- Which UI surfaces need collection/library pages versus simple reading screens?

Initial output should separate secret notes from museum lost books; they are related
player-facing systems, but not the same runtime.

### 4. Museum Donation and Reward System

Questions to answer:

- Which donation, display, rearrange, reward, and completion behaviors already work?
- Which vanilla `MuseumRewards` entries are missing or semantically different?
- Which Gunther/library interactions belong here versus the NPC story system?

Initial output should preserve the existing donation runtime and list parity gaps.

### 5. Wizard, Magic Buildings, and Late Unlock Systems

Questions to answer:

- Which source events and quests unlock the Wizard Tower, Witch Swamp, magic ink, and buildings?
- Which buildings and obelisks need full MC equivalents, placeholders, or deferral?
- Which dependencies come from Community Center/Joja completion, Krobus, sewer, goblin, or railroad flow?

Initial output should define the boundary of the first wizard-building release.

### 6. Other SDV Progression Systems

Questions to answer:

- Which player-facing systems are large enough to become their own 0.5 stage?
- Which systems are already mostly formed and only need audit?
- Which systems are late-game enough to defer past the first 0.5 release?

Initial output should keep the system list honest as source/Wiki research expands.

### 7. Release Shape

Questions to answer:

- What is the smallest 0.5.0 that feels meaningfully more complete than 0.4.x?
- Which player-facing system is the current focus for 0.5.0, 0.5.1, 0.5.2, and later patch lines?
- Which changes must be merged early as shared foundations for later PRs?

Initial output should be a candidate milestone list, not a promise.

## PR Split Rules

- Put shared infrastructure in small PRs that can merge into `main` early.
- Keep content batches narrow: one quest family, one NPC progression slice, or one runtime contract.
- Do not hide shared 0.4.x dependencies inside the long-lived 0.5.x branch.
- Every execution PR should name its source evidence, implementation files, and minimal verification.
- If a task needs vanilla parity, first create a research handoff with source files and explicit unknowns.

## Execution Handoff Template

Copy this block into a new execution window when a 0.5.x task becomes concrete:

```text
Repo: /Users/jiayuhan/游戏制作/StardewCraft
Base branch: latest main unless this task explicitly depends on codex/0.5-planning
Version line: 0.5.x gameplay-flow completeness
Task:
- [one concrete player-facing system slice, e.g. quest system, secret notes, museum library]

Rules:
- Check git status first.
- Source-first: inspect vanilla/source data before implementing parity behavior.
- Prefer existing StardewCraft runtime paths over new parallel systems.
- Keep changes surgical and PR-sized.
- Run ./gradlew classes when possible.

Expected output:
- source evidence summary;
- changed files and intent;
- verification result;
- follow-up gaps for the 0.5.x planning window.
```

## Decision Log

- 2026-07-09: Open 0.5.x as a long-lived planning branch while 0.4.x festival PRs continue to land independently on `main`.
- 2026-07-09: Keep the first 0.5.x artifact as planning structure only. Do not invent a feature list before source research.
- 2026-07-09: Split 0.5.x planning into status classes: formed runtime, partial coverage,
  semantic audit, missing system, and not audited. Use this to avoid calling existing
  foundations "missing" just because vanilla coverage is incomplete.
- 2026-07-09: Reframed 0.5.x planning around player-facing SDV systems, not technical
  runtime lanes. Work should move system by system, with mail/events/rewards/flags treated
  as support dependencies inside the current system.
