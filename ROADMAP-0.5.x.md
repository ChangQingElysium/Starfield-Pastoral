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
- connect mail, friendship, NPC schedules/dialogue, rewards, locations, and unlocks into
  coherent save progression;
- prefer source-first parity research before implementation;
- avoid speculative rewrites of already-working 0.4.x festival systems.

Unknown on purpose:

- exact first 0.5.x feature batch;
- final PR split;
- which vanilla quest/event systems are in scope for the first public 0.5.x release;
- which content should be deferred past 0.5.x.

## Planning Lanes

Use these lanes to sort research before creating implementation PRs.

### 1. Quest Runtime

Questions to answer:

- Which vanilla quest categories already exist in StardewCraft?
- Which quest states need persistence, sync, UI presentation, reward handling, and turn-in logic?
- Which existing StardewCraft systems should own quest progress instead of adding parallel state?

Initial output should be a gap map, not code.

### 2. Event and Cutscene Progression

Questions to answer:

- Which vanilla event triggers are already represented?
- Which triggers depend on friendship, mail, location, time, weather, season, year, or previous events?
- Which existing cutscene/event runtime can be extended safely?

Initial output should identify the smallest reusable trigger contract.

### 3. NPC Story Flow

Questions to answer:

- Which NPCs need first-pass progression coverage?
- Which dialogue, mail, schedule, gift, and friendship hooks are already available?
- Which NPC story beats are blocked by missing locations or items?

Initial output should rank NPCs by dependency risk.

### 4. Mail, Rewards, and Unlocks

Questions to answer:

- Which vanilla progression beats are delivered through mail?
- Which rewards/unlocks already exist as StardewCraft items, blocks, shops, recipes, or flags?
- Which missing rewards need placeholder policy versus full implementation?

Initial output should separate data-entry work from new runtime work.

### 5. Release Shape

Questions to answer:

- What is the smallest 0.5.0 that feels meaningfully more complete than 0.4.x?
- Which work can ship as 0.5.1, 0.5.2, and later patch lines?
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
- [one concrete quest/event/NPC/mail/unlock slice]

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
