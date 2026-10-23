# Fabric 1.21.1 event closure audit

Audit scope: the current StardewCraft consumers, their native producers and startup registration paths. This is source/classfile evidence plus focused behavioral tests, not a claim that every gameplay trigger has been exercised by a player.

## Automatic routing correction

FML **4.0.42** `AutomaticEventSubscriber.java:60–106` classifies each annotated method by its **declared event parameter** and `IModBusEvent.isAssignableFrom(...)`. Its `EventBusSubscriber.bus` field is ignored. The Fabric index previously used that legacy field, which routed these four live registration handlers to GAME instead of MOD:

| Consumer | Handlers corrected |
| --- | ---: |
| `StardewSkyEffects$Registration.registerDimensionEffects` | 1 |
| `TemplateClientEvents.modifyModels/registerBlockColors/registerItemColors` | 3 |

The central correction introduces `ModBusEvent` with the actual NeoForge 21.1.217 event hierarchy, classfile-only hierarchy classification in `SubscriberIndexGenerator`, and filtered registration on `EventBus`. Explicit `register`/`addListener` retain the caller's chosen bus. The index schema is now **2**, keyed by `(className, bus)`, so a mixed class can contribute separate, disjoint entries. Physical-side filtering precedes resolving a subscriber class. Old schema indexes and malformed automatic handlers fail closed.

`ModelEvent` itself stays GAME; only its four registration/baking nested classes carry the marker. `SetupEvent`, renderer/color event parents and their inherited child types carry the MOD marker as in NeoForge.

Independent bytecode generation against the actual compiled business classes yields **403 subscriber classes, 816 handlers: MOD 168, GAME 648**. The original NeoForge/FML interface graph independently gives the same totals. Only the four functions above change route. No current production subscriber class is mixed; regression fixtures cover that case.

Focused verification: 31 source files compile against the real native/FAPI/PL classpath; **20/20 tests pass** (7 subscriber-index, 2 event-hierarchy, 11 existing bus behavior tests). Evidence lives under `fabric/build/research/event-closure-audit/auto-routing/` and is reproducible development evidence, not a required release input. The root task then completed the unified gate: **755 platform tests, zero failures/errors**, `auditClientMixins` exit 0, and successful `remapJar`. The installation audit separately verifies the actual final JAR and schema-2 index; see [artifact audit](fabric-install-artifact-audit.md).

## All 22 original handlers outside the automatic index

The original 838 `@SubscribeEvent` handlers minus the 816 automatic handlers reconcile as follows:

| Original handlers | Count | Real route |
| --- | ---: | --- |
| `StardewCraft` server lifecycle instance methods | 3 | Common overlay `GAME.register(this)`, line 125 |
| `WildTreeChopEvents` and `ResourceClumpEvents` | 2 + 5 | Common overlay explicit GAME class registration, lines 128–129 |
| `TVScreenOverlayRenderer` | 1 | Client overlay explicit GAME registration, line 74 |
| `DebugKeybindsTick` | 1 | Client overlay explicit GAME registration under the original development guard, lines 76–77 |
| `AppleSkinCompat` HUD handlers | 4 | Client overlay loaded-mod guard, lines 66–71; `FabricAppleSkinEvents:18–21` registers the four real external callbacks |
| `PacketHandler.register` | 1 | Common overlay explicit MOD listener, line 88 |
| Retired animal event adapters | 2 + 3 | Intentionally unregistered in the original source; see `AnimalBuildingStructureEvents:24` and `AnimalDuplicateGuardEvents:30`. No automatic class annotation or explicit registration exists. |
| **Total** | **22** | |

The platform's additional `MobSpawnHooks.blockCancelledSpawn` is the **839th** annotated method across compiled original + platform code; its own `initialize()` explicitly registers the class on GAME. It is not an omitted original subscriber.

Programmatic listeners also close independently of annotations: six common constructor MOD listeners, five `TownDoorRuntime` listeners, and two `TownDoorClient` listeners. These **13** registrations use explicit event classes in generated Fabric source. The fourteenth original method reference is `ConfigMigration`, registered with FCAP's real `Consumer<ModConfig>` and all three configuration scopes rather than the project event bus.

## Native producer coverage and the client-world correction

The 149 lexical original parameter spellings reduce to **132 distinct compiled event parameter classes**: 128 project event types and four external AppleSkin types. Classfile construction + ancestry inspection finds a production constructor path for every consumed project type. Base `ClientChatReceivedEvent`, `EntityTeleportEvent`, and `Event` are intentionally represented by concrete subclasses. Constructor presence alone does not establish side, injection timing, cancellation or complete gameplay coverage.

That additional check found a real missing client path: only server world/chunk callbacks published `LevelEvent.Load/Unload` and `ChunkEvent.Load/Unload`. `ClientSurfaceFloors` could retain its dimension-independent chunk cache after changing dimensions; `TownDoorClient` and `ColoredLightEngine` cleanup consumers were likewise unreachable on the physical client.

The correction uses three client-only Mixins and `FabricClientWorldEvents`:

- ClientLevel constructor RETURN publishes Load after native setup, before the new world is assigned.
- Minecraft `setLevel` HEAD and disconnect after `updateScreenAndTick` publish old-level Unload at the NeoForge boundaries. `clearClientLevel` does not gain an extra event.
- ClientChunkCache successful `replaceWithPacketData` RETURN publishes Load with `newChunk=false`, including a repeated full packet for an existing chunk. The native `drop` branch publishes Unload before storage removal. FAPI's wider unload cases (view-radius shrink/slot replacement) are deliberately not added because NeoForge does not post at those sites.

Six behavioral/native-descriptor/side-isolation tests pass. The root gate after this correction passed **748 platform tests** and actual CLIENT transformation with **222 targets / 199 transformed / 23 optional absent / zero failures**. These counts predate the later central routing gate.

Client entity lifecycle coverage already existed: `ClientEntityLifecycleMixin` wraps `ClientLevel.addEntity` before ID replacement and the `Entity.tick` invocation inside `tickNonPassenger`; `ClientEntityLeaveMixin` posts at `EntityCallbacks.onTrackingEnd` TAIL. The server paths exist independently. Passenger `rideTick` is not given a new tick event: actual NeoForge also does not post EntityTick there. This preserves native event boundaries and keeps the existing client `YetiToothVisuals` consumer reachable.

## Dispatch semantics and preserved boundary

`EventBus` dispatches an immutable subscription snapshot, inherited event types, priority HIGHEST→LOWEST, and stable registration order. Cancellation skips ordinary later handlers; `receiveCanceled` handlers can observe or restore dispatch. Class + method identity prevents an explicit re-registration from duplicating an already automatic handler. Explicit object registration remains instance-only. Handler failures propagate.

A known original API omission is preserved: NeoForge's native fluid-placement hook consumes `FluidPlaceBlockEvent.getNewState()` but ignores its cancellation flag. The port applies state replacement and does not claim that the original cancellation-only handler protects fluid placement. This is not reported as a Fabric cancellation implementation success.

## Known open render-stage boundary: AFTER_LEVEL

**Status: source-confirmed, not closed.** In NeoForge **21.1.217**, the userdev patch `patches/net/minecraft/client/renderer/GameRenderer.java.patch` dispatches `RenderLevelStageEvent.Stage.AFTER_LEVEL` immediately after the outer `LevelRenderer.renderLevel(...)` call returns, before the hand-rendering phase. It passes a null pose stack; the constructor in `net/neoforged/neoforge/client/event/RenderLevelStageEvent.java` creates an identity `PoseStack`. A direct nested `LevelRenderer.renderLevel(...)` call, such as `TownDoorRenderer.renderContent`, does not traverse that GameRenderer dispatch site.

The current Fabric bridge in `FabricLevelRenderEvents.install` instead maps FAPI `WorldRenderEvents.END` to `AFTER_LEVEL` for every normal `LevelRenderer.renderLevel` return, including the nested portal draw. Its `FrameScope.event` supplies that invocation's native local pose stack. The portal frame-scope correction restores the outer frame on normal and exceptional exits, but does not align this event's publication boundary, count or identity-pose contract.

No current production source consumer references `AFTER_LEVEL`; no current business impact was identified by this source audit. Aligning the producer with NeoForge and adding the corresponding focused regression remain open work, outside the current portal-crash hotfix. This is a static contract finding, not runtime acceptance or a claim of full render-event equivalence.

No additional current-consumer event without a production constructor path was identified after these two corrections. Full functional acceptance still requires actual gameplay triggers, especially optional integrations and render consumers; static closure and headless event tests are narrower evidence.
