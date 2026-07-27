# Compatibility baselines

This directory contains reviewed compatibility contracts for addon authors.

## `public-api-v1.txt`

`public-api-v1.txt` is generated from compiled bytecode under
`com.stardew.craft.api.v1`, excluding the explicitly internal
`com.stardew.craft.api.v1.internal` package and the top-level candidates listed
in `experimental-api-v1.txt`. It records stable public and protected types and
members together with their JVM descriptors.

`experimental-api-v1.txt` is a review gate, not a second compatibility
baseline. Types stay there while their model is being exercised by multiple
classes of addon. Promoting a type requires removing it from the experimental
list, regenerating the stable baseline, and reviewing the resulting API diff.
Adding an already stable type to the experimental list is a compatibility
break and must not be used to bypass the check.

`api-maturity-review-v1.tsv` records one decision for every type in the
experimental manifest. Its evidence columns are deliberately narrow: they
show direct symbol references from core runtime code, the independent sample,
tests and documentation. Missing evidence keeps that type experimental. Even
when all four columns are present, promotion still requires the release-grade
runtime and second-consumer evidence described by the roadmap.

The normal `check` lifecycle runs:

```bash
./gradlew checkPublicApiCompatibility
./gradlew checkApiMaturityReview
```

The check permits additions but fails if an existing baseline type or member
is removed or its signature changes. Keep an old forwarding overload or bridge
whenever possible.

Shared Java extension points accept registrations during mod initialization and
freeze at the end of normal server startup. Duplicate IDs and registrations
attempted after that boundary are rejected and retained in
`/stardew debug extensions explain <extension-point>`. GameTest servers keep
their registries open so each isolated synthetic test can create its own
providers.

Regenerate the baseline only after deliberately reviewing the compatibility
impact:

```bash
./gradlew generatePublicApiBaseline
git diff -- compatibility/public-api-v1.txt
```

A baseline update must not be used to hide an accidental break.

## Addon canaries

`addon-canaries.json` pins each external addon to an exact source commit and
records the compatibility inputs reviewed by this repository. Canaries verify
real-world compatibility after platform capabilities are designed; they are
not the source specification for new APIs. A pin update must be a deliberate
change, not an unreviewed checkout of a moving branch.

The generic Mixin-addon verifier checks:

- the checked-out Git commit;
- the complete configured Mixin set;
- every Mixin target class in the current StardewCraft build;
- exact method selectors, including JVM descriptors and generated lambda names;
- compiled `@Inject` and `@WrapMethod` handler descriptors against target
  arguments and return types;
- fields used through Mixin accessors.

Run it after compiling StardewCraft and the canary addon:

```bash
./gradlew classes
python3 compatibility/verify_addon_canary.py <addon-id> <addon-checkout> \
  --addon-classes <addon-checkout>/build/classes/java/main
```

CI additionally builds each pinned addon source against
`build/compatibility/stardewcraft-canary.jar`. The canary source and resulting
addon artifact are test inputs only and are not redistributed by StardewCraft.

CI also runs the dedicated NeoForge GameTest server. The runtime contract suite
includes an addon managed-animal round trip covering type registration,
compatible-building movement, entity-type projection, namespaced addon state,
and `AnimalWorldData` save/load.

## Runtime smoke evidence

Compilation and title-screen startup do not prove that a world can load. After
a client-world release smoke test, verify the captured log with:

```bash
python3 compatibility/verify_runtime_smoke.py run/logs/latest.log \
  --scenario client-world
```

The verifier requires resource reload, a real player login, Stardew player-data
load, a stable session interval, orderly server shutdown, world saving and the
final all-dimensions-saved marker. It also rejects JVM linkage and Mixin
application failures even when shutdown later appears clean.

For a captured GameTest log use `--scenario game-test`. Run verifier unit tests
with:

```bash
python3 -m unittest discover -s compatibility -p 'test_*.py'
```

Old-world release fixtures must be tested from disposable copies, never from
the only copy of a player's save. Point the development server at a prepared
temporary run directory with:

```bash
./gradlew runServer -PserverRunDir=/tmp/stardewcraft-old-world-smoke.example
python3 compatibility/verify_runtime_smoke.py \
  /tmp/stardewcraft-old-world-smoke.example/logs/latest.log \
  --scenario dedicated-world
```

The temporary directory must contain `eula.txt`, `server.properties`, and the
copied save as `world/`. The dedicated-world scenario requires recipe reload,
world preparation, a completed server start, at least five stable seconds, and
a shutdown that saves every dimension.

Use `--scenario network-world` on the dedicated-server log after a real client
joins. This additionally requires capability negotiation, player login,
Stardew player-data load, at least ten stable seconds, and complete saving.
