# Release CI Postmortems

This file is the durable record for release-related CI failures. A release is not considered complete until the exact candidate commit passes the full GitHub workflow from a clean, tracked-only checkout and the pushed GitHub Actions run finishes successfully.

## Recurring structural cause

### 0.6.3 runtime — fresh noise-world creation deadlocks before spawn preparation (2026-10-02)

A reported Forge 1.20.1 world remained at 0%. Its log completes prebuilt-region installation (25 files, version 19) and then ends before spawn preparation. A fresh dedicated Forge world reproduces that same boundary: two thread dumps, 24 seconds apart, show every world-generation worker blocked in the custom `PalettedContainer` operation lock while the server waits for the initial spawn chunk. The client model-loading errors occur minutes earlier and do not explain this reproduced server deadlock.

The prior palette fix extended a thread-owned `ReentrantLock` over vanilla `acquire/release`. `NoiseBasedChunkGenerator.fillFromNoise` intentionally acquires sections in its caller and fills/releases them on an asynchronous worker. The caller therefore kept our lock while the worker needed it to fill the chunk. Flat GameTest worlds did not exercise that handoff; the earlier regression incorrectly required reads to wait for a manually acquired detector.

Keep vanilla `acquire/release` unchanged and guard bounded reads, slot mutations, resizing, copying, packet read/write, serialized-size inspection and codec packing individually. Public checked coordinate writes take the custom lock before the vanilla detector, matching packet read/write and pack, so these paths cannot invert lock order. Replace the incorrect detector-lifetime assertion with actual caller-acquire/worker-fill-and-release coverage, retain full-state resize/copy/packet checks, and add concurrent packet serialization and codec packing against checked writers. Apply the shared fix to both Forge and NeoForge. The Forge server smoke script must fail when startup never reaches `Done`, when saving fails, or when shutdown exceeds its bounded cleanup period; a printed zero-start count is not a passing result. Verify normal noise-world creation and reopening separately from flat GameTests. This runtime repair does not constitute a new release or remote CI result.

Development verification: both versions passed `classes` and the isolated `stardewcraft_palette_handoff` GameTest (worker handoff plus eight concurrent resize/serialization rounds). Forge 47.4.10 / Java 17 then created a fresh normal noise world, reached `Done (10.310s)`, saved every dimension, reopened the same world with `ALREADY_PRESENT`, reached `Done (4.194s)`, and saved every dimension again; the bounded smoke script exited successfully. Its intentional SIGTERM is reported by ForgeGradle as child exit 143, accepted only alongside completed startup and all-dimension saving. This is separate from the reporter's Windows / Forge 47.4.23 / Java 21 client environment, which was not run locally.


### 0.6.3 runtime — exploring new Forge chunks releases a lock on the wrong thread (2026-10-03)

The new report uses Minecraft 1.20.1 / Forge 47.4.20 / Java 17.0.12 / StardewCraft 0.6.3 and crashes near ungenerated terrain. Its exception is `IllegalMonitorStateException` in our `PalettedContainerStateAccessMixin` release handler, called from `NoiseBasedChunkGenerator`'s asynchronous completion. This is the second failure path of the detector-lifetime lock above: when chunk writes reach completion, the worker cannot unlock a thread-owned lock acquired by the generation caller. The stack establishes the defect in our handler; Connector's banner and the list of other palette mixins are not evidence that another mod caused it. Invoking the actual unchanged acquire/release handlers extracted from the published 0.6.3 Forge JAR on separate threads reproduces the same exception without requiring the reporter's modpack.

The existing shared source repair leaves vanilla acquire/release untouched. Add a completion-only handoff to the maintained palette regression before its worker-write coverage: acquire on the caller, release on a worker without any intervening slot write, require bounded completion and an unchanged slot, then reacquire on the caller. This isolates the release exception instead of allowing a blocked writer to hide it; retain all concurrent resize, copy and serialization checks. Both Forge 47.4.20 / Java 17 and NeoForge 1.21.1 pass `classes` and the isolated required palette GameTest.

A disposable normal-world Forge 47.4.20 / Java 17 server with the reporter's exact Radium `0.12.4+git.26c9d8e` generates remote overworld chunks at block coordinates `(110,-7963)` and `(16000,16000)`, confirms that both reach FULL status, saves all dimensions and exits normally. Reopening the same world loads both remote chunks, saves all dimensions and exits normally again. Startup reaches `Done (10.155s)` and `Done (4.899s)` respectively. The unmodified published Radium dependency is remapped through standard ForgeGradle `fg.deobf` for this mapped development runtime. No player save or installed modpack is modified. The full reporter modpack, including KAllFix and ModernFix, has not been executed locally; this verifies the reported release path and remote generation with the named optimization version. The published 0.6.3 installation JAR remains unchanged and still needs a separately authorized release.

### 0.6.3 runtime — old saves hang during asynchronous roof lighting (2026-10-03)

The reporter runs Minecraft 1.21.1 / NeoForge 21.1.248 / StardewCraft 0.6.3 and confirms that world loading stays stuck. The log recognizes the existing pregen (`ALREADY_PRESENT`) and ends after the three system totems, before spawn preparation. It contains no terminal exception or evidence of damaged save data. Inspection of the distributed NeoForge JAR initially identified the older palette lock, but that was not the reproduced cause: vanilla 1.21.1 acquires, fills and releases sections within the same worker. On NeoForge 21.1.249 / Java 21 the published JAR opens a preserved 0.5.6 save fixture, a copy with only its test spawn moved to ungenerated normal terrain, and that latter fixture with the reporter's exact C2ME version; all three reach `Done` and save every dimension.

Adding the reporter's other threading/lighting optimizations reproduces the hang at the same totem boundary. The diagnostic set is C2ME `0.4.0-alpha.0.122+1.21.1`, Lithium `0.15.4+mc1.21.1`, ModernFix `5.27.24+mc1.21.1`, and ScalableLux `0.3.0-alpha.0.8+1.21.1`. Two thread dumps 25 seconds apart show the same wait: the server is synchronously loading a chunk for `PrizeTicketMachineInstaller.ensurePlaced`, while a lighting worker calls `MaterialTemplateBlock.propagatesSkylightDown` through vanilla shape evaluation into `RoofTemplateBlock.getShape` / `RoofTemplateEdges.connected`. That dynamic selection shape reads neighboring block states, requesting a FULL chunk whose completion depends on the lighting work already in progress. Lithium routes the off-thread request to the server, producing a circular future wait. No custom palette lock is present in those blocked stacks. The machine installer triggers the chunk load; the invalid neighbor lookup inside lighting is the defect to repair.

The working-tree repair already replaces the call to vanilla's dynamic selection-shape path with state-local `TemplateShapeCache` geometry and the persisted `PROPAGATES_SKYLIGHT` flag. It keeps connected roof selection/rendering and material-specific light behavior. The published 0.6.3 JAR does not include this repair. The existing `TemplateLightLoadingGameTests` exercises the two affected workshop eave profiles across rotations, flipping, filling and opaque/transparent material states, using a world accessor that rejects block-state lookups and records block-entity reads. Align its explicit template namespace with its holder and maintain a fixture in that same namespace: NeoForge filters by `GameTest.templateNamespace`, so the old reference to `stardewcraft_roof_templates` caused an isolated run to register zero functions. Include `stardewcraft_template_light_loading` in the default GameTest gate so ordinary CI cannot silently skip it.

Verification: the unchanged published artifact reproduces the deadlock above on NeoForge 21.1.249. On the development NeoForge 21.1.217 / Java 21 runtime, fixed sources pass `classes` and the isolated lighting GameTest (one required test, 64 eave/material/orientation combinations). A normal-world save opens and saves across two launches. The preserved 0.5.6 save copy with its test spawn moved to ungenerated terrain reaches `Done (4.839s)` and saves all dimensions. With all four optimization versions, the fixed old-save fixture reaches `Done (2.884s)`, saves every dimension, reopens with `ALREADY_PRESENT`, reaches `Done (1.399s)`, and saves every dimension again. Tests use disposable copies and an isolated build directory; no player save or distributed JAR is modified. Intentional shutdown exit 143 is accepted only with completed startup and all-dimension saving. The reporter's exact NeoForge 21.1.248 Windows client and original save have not been executed locally; this establishes a matching reproducible defect and fixed-source coverage, not acceptance of every old world or a newly published release.


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
