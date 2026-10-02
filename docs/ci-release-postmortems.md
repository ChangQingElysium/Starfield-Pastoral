# Release CI Postmortems

This file is the durable record for release-related CI failures. A release is not considered complete until the exact candidate commit passes the full GitHub workflow from a clean, tracked-only checkout and the pushed GitHub Actions run finishes successfully.

### 0.6.3 candidate — seated torso joint misclassified as a limb (2026-10-02)

The local clean-checkout `build check` gate rejected Gil with `Missing continuous bridge gil`. The compiled model contains the complete `person/body` waist surface, with one shirt owner and weights from 0 through 1 in eight subdivisions. The checker identified a waist only when its upper bone was literally named `root`, incorrectly treating Gil's seated `person` parent as a two-owner limb cut. Classify the compiler's torso bindings by their lower bone (`body` or `chest_breath`), retaining every interior-weight, seam, bend-angle, finite-vertex and rigid-endpoint assertion and the two-owner requirement for actual limb cuts. No production model, compiler, animation or gameplay logic is changed.

### 0.6.3 candidate — stale charged-hoe and fiber-harvest fixtures (2026-10-02)

The local tracked-only suite completed 642 GameTests with three required failures. Two were verified fixture mismatches: the charged-hoe fixture prepared a square centered on the hit block instead of the forward-shifted charge-5 pattern, and the fiber fixture discarded the complete harvest callback while asserting total yield on the deliberately single-item primary return. Prepare the forward-facing 5x5 soil and assert its exact 25 coordinates; collect every harvest callback and assert the complete fiber count and quality separately from the primary return. Keep marker targeting, both crop halves clearing and all existing behavior assertions. No production hoe or crop logic is changed. Include the new Ginger Island namespace in the default release suite so its production interactions are actually exercised.

### 0.6.3 candidate — GameTest footprint and entity isolation (2026-10-02)

Enabling the Ginger Island namespace expanded the tracked-only suite to 653 tests and exposed two additional failures: the fireplace and tropical bed wrote their multi-cell footprints outside a 1x1x1 template, where adjacent test structures could occupy those cells. Reuse the tracked pure-air 32x12x32 dog-house fixture under the enabled Ginger Island namespace, with a deterministic gzip generator, for all nine world-writing or delayed cases. Preserve all original coordinates, placement, interaction, light, cleanup and harvest assertions; the two read-only/data-only cases retain their small template. The next complete run passed every Ginger Island test.

Wizard placement was independently traced to the preceding legacy-livestock fixture's `Old friend` and `Legacy hatch` entities, whose `blocksBuilding=true` bounding boxes intersected the hut at farm offset (12,2,12). All block, survival, owner and farm checks passed; the native entity collision correctly refused placement. Move both Wizard fixtures to isolated farm offset (24,2,24), retaining the complete six-building/four-facing, obstruction, ownership and edge coverage. Remove the temporary long diagnostic, which also exceeded the GameTest report-book string limit. No production collision, building permission, furniture or animal logic is changed; the revised candidate must still pass the complete clean-checkout suite.

### 0.6.3 candidate — greenhouse schematic shape-normalization oracle (2026-10-02)

The urgent greenhouse soil regression expanded the clean NeoForge suite to 657 tests; one new assertion rejected an authored upper `long_potted_plant` extension at schematic-local `(5,2,1)`. The saved test world confirms that the lower main and side extensions remain, while the old upper extensions become air. A complete schematic audit finds four planters, each with three obsolete upper extensions, for twelve cells total; all four mains and eight valid lower extensions remain. The unchanged model occupies only the lower row, and the existing schematic loader calls neighbor-shape normalization, which already removes unsupported decorative extensions. The loader, model and decorative-block implementation are unchanged from the preceding candidate. Make the test oracle use that existing, narrowly defined invalid-extension cleanup rule while retaining identity checks for every valid decorative and soil cell; do not change production geometry, skip all decoration checks or change the soil migration to satisfy the fixture. The other three new tests passed actual hoe/seed use, moisture/crop/container preservation, boundary guards and all four exterior facings.

### 0.6.3 candidate — Ostrich Incubator contradicts the approved home data (2026-10-02)

The clean Forge suite completed 694 tests with three required failures: the new greenhouse oracle above and two external-automation cases that could not validate their Ostrich barn fixture. This exposed a shared production contradiction, not merely a stale fixture. The tracked Ostrich definition explicitly records the project decision `ostrich lives in Coop`; both new-incubator placement and the block entity nevertheless required `BARN`, then the normal animal-home validator rejected the same barn because its family differs from the configured Ostrich family. Resolve both machine-specific family gates through `LivestockSpecies.OSTRICH.family()` without changing animal data, tier requirements, input rules, timing, ownership or receipts. Exercise a real valid tier-two coop with placement and egg insertion, retain rejection in a real barn, and make the Forge capability fixtures use the same approved home. Keep every multi-cell, side, invalidation, single-owner and cleanup assertion. This repair is applied identically to both versions; it is not a new port exemption.

### 0.6.3 NeoForge candidate — regression cleanup bounding-box API (2026-10-02)

The clean candidate stopped at `compileJava` because the new incubation regression used the two-`BlockPos` `AABB` constructor retained by 1.20.1 but removed in 1.21.1. Construct the identical cleanup bounds from the two lower-corner `Vec3` points on NeoForge. Preserve the same 48x24x48 fixture, world assertions and cleanup scope; the Forge constructor remains valid. No production logic or release gate is changed. Repeat the complete exact-candidate workflow after this test-only API correction.

### 0.6.3 candidate — mine-cache seed search escaped its fixture (2026-10-02)

The complete NeoForge suite ran 658 tests and rejected the existing breaker-attribution case because its cache extension could not be placed. The saved world proves that `findGemSlot` chose local `(4,3,17)` outside the tracked 17x5x17 `ring_utilities` fixture, where the upper cell is the GameTest boundary barrier. The test searched an unbounded 80x80 area; production correctly rejected the nonreplaceable extension. Only this world-writing case reuses the tracked 48x24x48 building air fixture. Restrict its seed search to the actual world-coordinate bounds with a four-cell horizontal margin and require both cells to be inside and replaceable before executing the unchanged reward RNG and gem-branch draw sequence. Preserve every actual-breaker, nearby-player, weapon, bomb, creative-mode, loot and duplicate-payment assertion. No production mining, geometry, reward or RNG rule is changed.

### 0.6.3 candidate — unstable automation handler wrappers (2026-10-02)

The complete Forge suite ran 695 tests and rejected Auto-Grabber main/extension handler identity. Its default `getAutomationItemHandler()` allocated a new stateless `UtilityItemHandler` on every query; the extension's same-side query caused the Forge provider to invalidate the main `LazyOptional` it had just returned. Cache one handler in Auto-Grabber, following the existing machine pattern, and apply this allocation-only correction identically to both versions. The 36 inventory slots, collection, output, persistence and permissions are unchanged. Strengthen the retained multi-machine/four-facing/seven-side regression with repeated-query identity and optional-liveness assertions. The same review found three ordinary/shared chest registrations allocating `InvWrapper`; the Forge bridge may reuse only exact standard wrappers around the same Container, while still resolving each query and invalidating on null, source replacement or explicit owner change. Do not unconditionally retain capabilities or treat matching wrapper class names as proof of matching inventory owners.

## Recurring structural cause

### 0.6.3 candidate — concurrent palette reads during ordinary mine placement (2026-10-02)

The clean Forge candidate `92ecab95` aborted before GameTest completion on a skylight worker: `MissingPaletteEntryException` at world `(-4,68,12219)` while placing ordinary mine floor 61. The strict verifier rejected the incomplete run. The placement path is vanilla `StructureTemplate.placeInWorld` / `ServerLevel.setBlock` / guarded `LevelChunkSection.getAndSet`, not the farm schematic fast path. The saved section contains a valid 14-entry palette with zero invalid indices, ruling out a persistently malformed template or chunk at that location. Both versions' official `PalettedContainer.get` take one Data snapshot, but read its mutable palette/storage without participating in the writer's synchronization. A standalone diagnostic using the actual mapped classes, public `set/get` only and registered integer values reproduces the same exception on Forge/JDK17 in 13 ms (index 2) and NeoForge/JDK21 in 12 ms (index 9). No unchecked writes, reflection mutations or local assets are involved in that reproduction.

Apply the same container-level reentrant lock in both packages to single-slot reads, slot mutations, palette resize and copy. Preserve the original `ThreadingDetector` acquire/release calls and their order; existing checked read/write/pack sections inherit the same lock, while unchecked slot mutations use the private-operation guard. Always unlock in `finally`, including detector failures. Do not swallow the exception, substitute air, disable lighting, change mine placement order or alter any block states, seeds, rewards or gameplay timing. Do not claim arbitrary user callbacks in `getAll/count/maybeHas` have been made thread-safe. Maintain an actual-class regression proving a reader waits behind an acquired writer, then stress palette growth and verify final values, copies and network serialization. Runtime verifiers must reject a palette failure even if another task appears to complete. The revised exact commits must pass the full clean workflow and remote Actions before release.


### 0.6.3 NeoForge candidate — entity visibility is not synchronous block loading (2026-10-02)

GitHub run `36972384365` on `14b23a67` completed all 658 tests but rejected only `liveProjectionRejectsStaleEntitiesAndReplayedFloorProducts` at its immediate migrated-animal lookup. Both clean local suites passed. The fixture operates on a separately allocated farm and only synchronously loads its block chunks. The official server implementation does not make this an entity-tracking guarantee: `getChunk(FULL)` uses a short-lived level-33 ticket; entity sections default to hidden, `addFreshEntity` can register an entity without adding it to visible lookup, and `ServerLevel.getEntity(UUID)` reads only that visible lookup. Even adding a forced ticket does not synchronously complete its full-status callback. The remote log does not capture the exact visibility state, but the source audit proves that the fixture lacks this required precondition. Hold only its own home-chunk tickets and wait, within a bounded tick budget, for the actual entity-loading and entity-ticking predicates before invoking migration/projection once. Entity-ticking readiness is a sufficient observable fixture condition, not a new production tracking requirement. Preserve every stale-entity, sold-animal, floor-product receipt, disk-save and replay assertion; release only tickets owned by the fixture on success, failure or timeout. No production projection, animal rules, persistence or chunk-loading policy is changed. The revised exact candidates must repeat the complete clean workflow and pass remote CI before publication.


### 0.6.1 candidate — local clean-checkout gate (2026-09-14)

Before pushing, `build check` rejected the API maturity manifest: its documentation evidence had been generated with local, ignored audit notes present. The clean checkout correctly reported `StardewGiantCrops.doc_ref=no` where the manifest claimed `yes`. Restrict evidence collection to Git-indexed files and regenerate the conservative experimental classification from the published tree. Local modelling/audit directories remain excluded; they are not added to satisfy a gate. This failure occurred locally before GitHub publication.

Several release failures shared the same structural cause: validation was performed in a developer working tree that contained local source assets or locally updated tests, while GitHub Actions built only the committed repository. `./gradlew classes` also hid test and downstream compatibility failures because the workflow actually runs `./gradlew build` and additional verification stages.

The permanent prevention rule is therefore not “fix the next missing file.” It is to validate the exact candidate commit in a clean worktree before pushing it.

## Incident history

| Date (UTC) | Release / commit | Actions run | Verified failure | Classification |
| --- | --- | --- | --- | --- |
| 2026-07-22 | `0.5.2` / `73c171a9` | `29942226058` | Three resource tests read files absent from the GitHub checkout and failed with `NoSuchFileException`. | Local-only dependency |
| 2026-07-30 | `0.5.4` / `4717a0b5` | `30543498768` | Eleven tests depended on repository-external original assets or source files; the clean runner could not find them. | Local-only dependency |
| 2026-08-02 | `0.5.4fix1` / `901e21fd` | `30734828626` | Weapon source-contract tests depended on local Stardew source data absent from GitHub. | Local-only dependency |
| 2026-08-02 | CI follow-up / `7cd290f9` | `30735203382` | The pinned addon canary no longer compiled against the changed public API. | Full workflow not validated before push |
| 2026-08-06 | `0.5.5` / `0f62b6bb` | `31125704695` | GitHub assigned no runner (`runner_id=0`); the check annotation says the hosted job was not acquired after repeated attempts. The run occurred during [GitHub's critical Actions incident](https://stspg.io/rcz3fcm83sff) from 15:22 UTC on August 6 to 02:04 UTC on August 7. | External platform outage |
| 2026-08-07 audit | `0.5.5` / `0f62b6bb` clean checkout | Local reproduction | The exact committed tree fails `compileTestJava` with 12 errors because production construction APIs changed while stale tracked tests remained in the release commit. The original dirty working tree already contained corresponding test edits, but they were deliberately excluded from the release. | Latent tracked-test mismatch |

## 0.5.5 root-cause chain

1. The release was declared ready after `./gradlew classes --offline`, which compiles production code but not test sources and does not execute the rest of the GitHub workflow.
2. Updated local test files were excluded from the release while their older versions remained tracked in GitHub.
3. The push landed during a confirmed GitHub Actions outage. GitHub never started a runner, so this external failure masked the repository's own `compileTestJava` failure.
4. A clean detached checkout of `0f62b6bb` reproduced 12 compiler errors in `AnimalBuildingLifecycleTest`, `AnimalBuildingAutomationCheckpointTest`, and `AnimalBuildingTierDefinitionsTest`.

## Required release procedure

1. Finish and review the intended release diff, including the final policy decision for every tracked test changed by the production code.
2. Create the candidate commit locally, but do not push it yet.
3. Create a clean detached worktree at that commit. Confirm that `git status --short` is empty there.
4. Run the commands in `.github/workflows/build.yml` in order, including Gradle build, compatibility verifiers, GameTest runtime verification, example addon, example data pack, and addon canary.
5. Check that no test, script, or build task requires ignored/untracked paths such as `tmp/`, `源文件/`, or machine-specific absolute paths.
6. Push only after the clean worktree passes. Then monitor the remote Actions run until it reports success.
7. If GitHub itself is degraded, record the check annotation and status incident, wait for recovery, and re-run the same commit. Do not use an empty follow-up commit to disguise an infrastructure retry.

## Test publication policy

The repository must use one consistent state:

- If `src/test` remains tracked, production API changes must include the matching test updates and the clean release commit must compile and pass them.
- If tests are intentionally development-only, remove the whole development-only test set from the Git index and ignore it locally. Do not leave stale tracked copies on GitHub while newer local copies are omitted.

The invalid mixed state was normalized locally on 2026-08-07: all 506
previously tracked files under `src/test` were removed from the Git index,
`/src/test/` was added to `.gitignore`, and the 519 local test files remained
on disk. The API maturity verifier was made independent of local Java tests and
its evidence manifest was regenerated without claiming unpublished test
coverage.

The tracked-only candidate then passed Gradle `build`, 18 compatibility
verifier tests, all 31 required GameTests, runtime shutdown verification, the
example addon build, validation of 66 example data-pack JSON documents, the
pinned addon compilation, and the pinned addon's 45-mixin compatibility audit.

### 0.6.1 candidate — cloth continuity sampling (2026-09-14)

The clean `check` gate rejected Evelyn at the coarse 1/120 versus 1/240 sampling ratio (maximum displacements 0.14621837 / 0.092136994). Denser 1/480 and 1/960 sampling of the unchanged production surface gives 0.04757029 / 0.024577953, with midpoint error falling from 0.019293508 to 0.002320573: the discrepancy is resolved continuous contact acceleration, not a fixed positional jump. Increase continuity sampling density for all garments; retain the existing midpoint, half-step ratio, seam, attachment and penetration limits. No runtime animation or model is changed for this gate correction.

### 0.6.1 candidate — stale GameTest fixtures (2026-09-14)

The full clean-checkout suite reported nine failures against contracts revised during development: three crop checks assumed tall selection in stages whose final models are shorter; template checks assumed one material slot and shift replacement instead of the current removal flow; a farm permission fixture assumed its freed slot was first in the shared FIFO; a pond event fixture used vanilla cod without a configured population request; and the mine reward fixture still expected placeholder swords after slingshots were restored. Update fixtures to the current shipped resources and interaction rules. Crop checks now compare both selection parts to stage geometry, composites exercise both material slots, and farm reuse still verifies the exact recycled slot. Keep inventory, permissions, collision, event-order and lighting assertions. No game rules are changed to satisfy these fixtures.

### 0.6.1 candidate — external SVE is outside the release scope (2026-09-14)

The pinned SVE checkout failed its own macOS dependency verification, then failed compilation against retired wild-tree and dirt-backed artifact-spot internals. The owner explicitly directed this release not to maintain SVE compatibility. Remove the external SVE checkout/build/audit from the mandatory Build workflow and revert the provisional SVE-only production bridges and dependency-metadata supplement. Keep the core build/check, maintained API checks, GameTests, runtime shutdown verification, example addon and data-pack validation. This is a release-scope decision, not evidence that the old SVE version is compatible with 0.6.1. Earlier release procedure entries above describe the historical gate.

### 0.6.1 — undeclared Pillow build dependency (2026-09-14)

GitHub run `34826617201` on `615b98a85` failed at `compileNativeFurnitureModels`: `compile_sebastian_computer.py` imports `PIL.Image`, but the Ubuntu runner had no Pillow installation. The tracked-only macOS checkout passed because its Python environment already contained Pillow 11.3.0; clean source isolation did not isolate interpreter packages. Add a pinned `requirements-build.txt`, explicitly set up Python and install those requirements before Gradle in CI, document local setup, and repeat the clean-checkout workflow inside a fresh virtual environment. Keep the furniture compiler and generated particle texture checks enabled.

### 0.6.1 — case-sensitive object catalog resource (2026-09-14)

GitHub run `34827394644` on `2a3404789` passed build/check but failed ten GameTests in fish tanks, ponds, pet gifts, artifact probabilities and provider catalogs. The common cause was a tracked `npc/vanilla/data/Objects.json` while all seven readers request lowercase `objects.json`; the runner explicitly logged the missing fish-pond object resource. The macOS filesystem hid the mismatch, but Linux and JAR entries are case-sensitive. Rename the resource through an intermediate filename so Git records the case-only change. Add a portable test comparing each reader's resource path against exact directory-entry names and checking representative fish/pet gift records. Verify the built JAR contains only the lowercase entry; retain all ten gameplay assertions.

### 0.6.1fix2 candidate — stale multi-part and soil fixtures (2026-09-17)

The local tracked-only gate started 586 GameTests, then reported a missing Farm Computer model variant and crashed while reading a crop stage from air. Both failures were stale fixtures after production contracts changed: the Farm Computer now has explicit `main` and `extension` blockstate parts, while Stardew crops intentionally accept the mod's authored farmland rather than vanilla farmland. Query the Farm Computer's `facing=...,part=main` variant and plant chunk-boundary test crops on `stardewcraft:farmland`. Keep both tests enabled so model rotation, collision and deferred chunk-load synchronization remain covered.

### 0.6.1fix2 candidate — stale terrain hierarchy fixture (2026-09-17)

The second local tracked-only GameTest run completed all 586 tests but rejected `cliffConnectionsCoverEveryFaceAndFold` because its legacy material-rank array still expected sand immediately above cliff. The release adds hard soil between cliff and sand, and the new hard-soil suite already verifies that production hierarchy. Add hard soil to the older cliff fixture and keep the full face, fold, corner and inset-gap coverage intact.

### 0.6.2 candidate — shell Java runtime discovery (2026-09-19)

The first local tracked-only `build check` attempt stopped before Gradle configuration because the non-login release shell resolved macOS `/usr/bin/java` without a configured runtime. A Java 21 JDK was already installed in Gradle's managed JDK directory; exporting that JDK as `JAVA_HOME` made the exact candidate complete `build check`. This was a local release-shell setup failure, not a source or CI runner failure. Keep the clean-checkout gate, and explicitly select Java 21 when reproducing the workflow outside GitHub Actions.

### 0.6.2 candidate — stale GameTest assumptions after farm and construction changes (2026-09-19)

The first local tracked-only GameTest run completed all 628 tests but reported three required failures. One addon API fixture counted only its three registered initialization steps even though seven maintained core farm steps now run through the same public registry. The construction progress fixture tried to keep construction and an upgrade active on one farm after Robin work became intentionally serialized. The terrain paste fixture reflected an obsolete private method signature after the bulk placement path added full-block ordering and a preloaded chunk grid. Update the tests to assert addon call order and balanced reports alongside core steps, exercise construction and upgrade snapshots sequentially, and invoke the current placement contract. The next run exposed one further fixture omission: the sequential construction had not marked its scaffold ready before completion, so restore that required lifecycle step. Keep all three behavior tests enabled.
