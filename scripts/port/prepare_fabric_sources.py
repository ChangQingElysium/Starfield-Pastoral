#!/usr/bin/env python3
"""Prepare a complete, disposable Fabric source overlay without modifying main.

Only verified package moves are applied here. Unsupported APIs remain visible to
javac and in the report. This intentionally does not strip classes or gameplay.
"""

from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path
import re


PACKAGE_MOVES = {
    "net.minecraft.gametest.framework.GameTest":
        "com.stardew.craft.platform.fabric.gametest.GameTest",
    "net.neoforged.neoforge.gametest.GameTestHolder":
        "com.stardew.craft.platform.fabric.gametest.GameTestHolder",
    "net.neoforged.neoforge.gametest.PrefixGameTestTemplate":
        "com.stardew.craft.platform.fabric.gametest.PrefixGameTestTemplate",
    "net.neoforged.neoforge.gametest.GameTestHooks":
        "com.stardew.craft.platform.fabric.gametest.GameTestHooks",
    "net.neoforged.neoforge.common.util.FakePlayer":
        "com.stardew.craft.platform.fabric.gametest.FakePlayer",
    "net.neoforged.neoforge.common.util.FakePlayerFactory":
        "com.stardew.craft.platform.fabric.gametest.FakePlayerFactory",
    "net.neoforged.api.distmarker.Dist": "net.fabricmc.api.EnvType",
    "net.neoforged.api.distmarker.OnlyIn": "net.fabricmc.api.Environment",
    "net.neoforged.fml.ModList": "net.fabricmc.loader.api.FabricLoader",
    "net.neoforged.neoforge.server.ServerLifecycleHooks":
        "com.stardew.craft.platform.fabric.server.FabricServerLifecycle",
    "net.neoforged.neoforge.common.util.TriState":
        "com.stardew.craft.platform.fabric.util.TriState",
    "net.neoforged.neoforge.common.ItemAbility":
        "com.stardew.craft.platform.fabric.extension.ItemAbility",
    "net.neoforged.neoforge.common.ItemAbilities":
        "com.stardew.craft.platform.fabric.extension.ItemAbilities",
    "net.neoforged.neoforge.registries.DeferredRegister":
        "com.stardew.craft.platform.fabric.registry.DeferredRegister",
    "net.neoforged.neoforge.registries.DeferredHolder":
        "com.stardew.craft.platform.fabric.registry.DeferredHolder",
    "net.neoforged.neoforge.registries.DeferredBlock":
        "com.stardew.craft.platform.fabric.registry.DeferredBlock",
    "net.neoforged.neoforge.registries.DeferredItem":
        "com.stardew.craft.platform.fabric.registry.DeferredItem",
    "net.neoforged.neoforge.network.PacketDistributor":
        "com.stardew.craft.platform.fabric.network.PacketDistributor",
    "net.neoforged.neoforge.network.configuration.ICustomConfigurationTask":
        "com.stardew.craft.platform.fabric.network.ICustomConfigurationTask",
    "net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent":
        "com.stardew.craft.platform.fabric.network.RegisterConfigurationTasksEvent",
    "net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent":
        "com.stardew.craft.platform.fabric.network.RegisterPayloadHandlersEvent",
    "net.neoforged.neoforge.network.handling.IPayloadContext":
        "com.stardew.craft.platform.fabric.network.IPayloadContext",
    "net.neoforged.neoforge.network.registration.PayloadRegistrar":
        "com.stardew.craft.platform.fabric.network.PayloadRegistrar",
    "net.neoforged.neoforge.common.NeoForge":
        "com.stardew.craft.platform.fabric.event.FabricEvents",
}
for event_name in ("Event", "IEventBus", "SubscribeEvent", "EventPriority"):
    PACKAGE_MOVES[f"net.neoforged.bus.api.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.event.{event_name}")
PACKAGE_MOVES["net.neoforged.fml.common.EventBusSubscriber"] = (
    "com.stardew.craft.platform.fabric.event.EventBusSubscriber")
for event_name in ("FMLCommonSetupEvent", "FMLClientSetupEvent"):
    PACKAGE_MOVES[f"net.neoforged.fml.event.lifecycle.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.lifecycle.{event_name}")
PACKAGE_MOVES["net.neoforged.neoforge.client.event.ClientTickEvent"] = (
    "com.stardew.craft.platform.fabric.event.ClientTickEvent")
for event_name in ("ServerAboutToStartEvent", "ServerStartingEvent", "ServerStartedEvent",
                   "ServerStoppingEvent", "ServerStoppedEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.event.server.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.event.{event_name}")
for event_name in ("ServerTickEvent", "LevelTickEvent", "PlayerTickEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.event.tick.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.event.{event_name}")
for event_name in ("LevelEvent", "ChunkEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.event.level.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.event.{event_name}")
for event_name in ("OnDatapackSyncEvent", "RegisterCommandsEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.event.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.event.{event_name}")
PACKAGE_MOVES["net.neoforged.neoforge.event.level.ChunkWatchEvent"] = (
    "com.stardew.craft.platform.fabric.event.level.ChunkWatchEvent")
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player.PlayerEvent"] = (
    "com.stardew.craft.platform.fabric.event.PlayerEvent")
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player.PlayerInteractEvent"] = (
    "com.stardew.craft.platform.fabric.event.entity.player.PlayerInteractEvent")
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player.AttackEntityEvent"] = (
    "com.stardew.craft.platform.fabric.event.entity.player.AttackEntityEvent")
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent"] = (
    "com.stardew.craft.platform.fabric.event.entity.player.ItemEntityPickupEvent")
PACKAGE_MOVES["net.neoforged.neoforge.common.CommonHooks"] = (
    "com.stardew.craft.platform.fabric.extension.CommonHooks")
PACKAGE_MOVES["net.neoforged.neoforge.event.level.BlockEvent"] = (
    "com.stardew.craft.platform.fabric.event.level.BlockEvent")
PACKAGE_MOVES["net.neoforged.neoforge.common.util.BlockSnapshot"] = (
    "com.stardew.craft.platform.fabric.event.level.BlockSnapshot")
PACKAGE_MOVES["net.neoforged.neoforge.event.AddReloadListenerEvent"] = (
    "com.stardew.craft.platform.fabric.resource.AddReloadListenerEvent")
for event_name in ("EntityEvent", "EntityJoinLevelEvent", "EntityLeaveLevelEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.event.entity.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.event.entity.{event_name}")
PACKAGE_MOVES["net.neoforged.neoforge.event.tick.EntityTickEvent"] = (
    "com.stardew.craft.platform.fabric.event.entity.EntityTickEvent")
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent"] = (
    "com.stardew.craft.platform.fabric.lifecycle.EntityAttributeCreationEvent")
for event_name in ("LivingEvent", "LivingDeathEvent", "LivingIncomingDamageEvent", "LivingDamageEvent", "LivingKnockBackEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.event.entity.living.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.event.entity.living.{event_name}")
for type_name in ("DamageContainer", "IReductionFunction"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.common.damagesource.{type_name}"] = (
        f"com.stardew.craft.platform.fabric.event.entity.living.damage.{type_name}")
for type_name in ("DataMapType", "AdvancedDataMapType", "RegisterDataMapTypesEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.registries.datamaps.{type_name}"] = (
        f"com.stardew.craft.platform.fabric.datamap.{type_name}")
for event_name in ("RegisterMenuScreensEvent", "RegisterKeyMappingsEvent", "RegisterParticleProvidersEvent",
                   "RegisterColorHandlersEvent", "RegisterClientReloadListenersEvent", "EntityRenderersEvent",
                   "RenderLevelStageEvent", "ClientPlayerNetworkEvent", "RenderGuiEvent", "RenderGuiLayerEvent",
                   "TextureAtlasStitchedEvent", "InputEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.client.event.{event_name}"] = (
        f"com.stardew.craft.platform.fabric.client.event.{event_name}")
for item_name in ("IItemHandler", "IItemHandlerModifiable", "ItemStackHandler", "ItemHandlerHelper"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.items.{item_name}"] = (
        f"com.stardew.craft.platform.fabric.item.{item_name}")
for wrapper_name in ("InvWrapper", "RangedWrapper", "PlayerMainInvWrapper"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.items.wrapper.{wrapper_name}"] = (
        f"com.stardew.craft.platform.fabric.item.wrapper.{wrapper_name}")
for type_name in ("Capabilities", "RegisterCapabilitiesEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.capabilities.{type_name}"] = (
        f"com.stardew.craft.platform.fabric.capability.{type_name}")
PACKAGE_MOVES["net.neoforged.neoforge.fluids.FluidStack"] = (
    "com.stardew.craft.platform.fabric.fluid.FluidStack")
PACKAGE_MOVES["net.neoforged.neoforge.fluids.capability.IFluidHandler"] = (
    "com.stardew.craft.platform.fabric.fluid.IFluidHandler")
for type_name in ("BakedModelWrapper", "IDynamicBakedModel"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.client.model.{type_name}"] = (
        f"com.stardew.craft.platform.fabric.client.model.{type_name}")
PACKAGE_MOVES["net.neoforged.neoforge.client.ChunkRenderTypeSet"] = (
    "com.stardew.craft.platform.fabric.client.model.ChunkRenderTypeSet")
PACKAGE_MOVES["net.neoforged.neoforge.client.RenderTypeHelper"] = (
    "com.stardew.craft.platform.fabric.client.model.RenderTypeHelper")
for type_name in ("ModelData", "ModelProperty"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.client.model.data.{type_name}"] = (
        f"io.github.fabricators_of_create.porting_lib.models.data.{type_name}")
for type_name in ("IModelBuilder", "ExtraFaceData", "QuadTransformers"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.client.model.{type_name}"] = (
        f"io.github.fabricators_of_create.porting_lib.models.{type_name}")
PACKAGE_MOVES["net.neoforged.neoforge.client.model.IQuadTransformer"] = (
    "io.github.fabricators_of_create.porting_lib.models.IQuadTransformer")
PACKAGE_MOVES["net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer"] = (
    "com.stardew.craft.platform.fabric.client.model.QuadBakingVertexConsumer")
PACKAGE_MOVES["net.neoforged.neoforge.client.event.ModelEvent"] = (
    "com.stardew.craft.platform.fabric.client.model.ModelEvent")
for type_name in ("RenderTooltipEvent", "RegisterClientTooltipComponentFactoriesEvent", "RenderHandEvent", "RenderFrameEvent", "ViewportEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.client.event.{type_name}"] = (
        f"com.stardew.craft.platform.fabric.client.event.{type_name}")
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player.ItemTooltipEvent"] = (
    "com.stardew.craft.platform.fabric.client.event.tooltip.ItemTooltipEvent")
for type_name in ("IClientItemExtensions", "IClientBlockExtensions", "RegisterClientExtensionsEvent"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.client.extensions.common.{type_name}"] = (
        f"com.stardew.craft.platform.fabric.client.extension.{type_name}")
for type_name in ("IGeometryBakingContext", "IGeometryLoader", "SimpleUnbakedGeometry"):
    PACKAGE_MOVES[f"net.neoforged.neoforge.client.model.geometry.{type_name}"] = (
        f"io.github.fabricators_of_create.porting_lib.models.geometry.{type_name}")
PACKAGE_MOVES["net.neoforged.neoforge.client.model.SimpleModelState"] = (
    "io.github.fabricators_of_create.porting_lib.models.geometry.SimpleModelState")
PACKAGE_MOVES["net.neoforged.neoforge.client.gui.VanillaGuiLayers"] = (
    "com.stardew.craft.platform.fabric.client.render.VanillaGuiLayers")
NEOFORGE_REFERENCE = re.compile(r"\bnet\.neoforged(?:\.[A-Za-z_$][\w$]*)+")
NON_CODE = re.compile(r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/')
BARE_PERSISTENT_DATA_CALLERS = {
    "com/stardew/craft/entity/monster/MineBigSlimeEntity.java",
    "com/stardew/craft/monster/StardewMonsterEntity.java",
}
ITEM_ABILITY_PROVIDERS = {
    "com/stardew/craft/item/tool/HoeItem.java": "HoeItem",
    "com/stardew/craft/item/tool/FishingRodItem.java": "FishingRodItem",
    "com/stardew/craft/item/tool/StardewAxeItem.java": "StardewAxeItem",
    "com/stardew/craft/item/weapon/StardewClubItem.java": "StardewClubItem",
}
ITEM_ABILITY_CALLERS = {
    "com/stardew/craft/tree/prefab/PrefabTreeChopHandler.java",
    "com/stardew/craft/mixin/FishingHookRendererLineOriginMixin.java",
    "com/stardew/craft/manager/ArtifactSpotDigService.java",
    "com/stardew/craft/floor/SurfaceFloorEvents.java",
    "com/stardew/craft/interior/SunroomService.java",
    "com/stardew/craft/event/WildTreeChopEvents.java",
    "com/stardew/craft/block/decor/SupplyCrateBlock.java",
    "com/stardew/craft/block/decor/FarmTwigBlock.java",
    "com/stardew/craft/block/utility/TapperBlock.java",
}
TOOL_MODIFICATION_PROVIDERS = {
    "com/stardew/craft/block/terrain/TerrainGrassBlock.java": "TerrainGrassBlock",
    "com/stardew/craft/block/terrain/TerrainDirtBlock.java": "TerrainDirtBlock",
    "com/stardew/craft/block/terrain/TerrainSandBlock.java": "TerrainSandBlock",
    "com/stardew/craft/block/terrain/HardSoilBlock.java": "HardSoilBlock",
    "com/stardew/craft/block/nature/YellowDirtBlock.java": "YellowDirtBlock",
}
TOOL_MODIFICATION_CALLERS = set(TOOL_MODIFICATION_PROVIDERS) | {
    "com/stardew/craft/item/tool/HoeItem.java",
    "com/stardew/craft/entity/bomb/StardewBombEntity.java",
    *["com/stardew/craft/gametest/" + name + ".java" for name in (
        "TerrainGrassGameTests", "HardSoilGameTests", "SandySoilGameTests",
        "TerrainFarmlandGameTests", "PlantingSoilGameTests")],
}
BLOCK_ENTITY_LOAD_CLASSES = {
    *["com/stardew/craft/blockentity/" + name + ".java" for name in (
        "ShippingBinBlockEntity", "GingerIslandGemPedestalBlockEntity", "WoodenChestBlockEntity",
        "DecorBlockEntity", "StoneChestBlockEntity", "MuseumExhibitStandBlockEntity",
        "TimedProductionBlockEntity", "FeedTroughBlockEntity", "FishPondBucketBlockEntity",
        "WardrobeBlockEntity", "ScarecrowBlockEntity", "PortalTriggerBlockEntity", "AutoFeedTroughBlockEntity",
        "FridgeBlockEntity", "AquariumBlockEntity", "FishMarketCrateBlockEntity", "PlacedFishBlockEntity",
        "AnimalProduceSpotBlockEntity", "FriendshipDoorBlockEntity")],
    "com/stardew/craft/templates/TemplateBlockEntity.java",
}
DATA_MAP_CALLERS = {
    "com/stardew/craft/api/v1/item/StardewItemDataApi.java",
    "com/stardew/craft/api/v1/item/StardewFoodEffects.java",
    "com/stardew/craft/api/v1/equipment/StardewEquipmentDataApi.java",
    "com/stardew/craft/api/v1/agriculture/StardewAgricultureDataApi.java",
}
LIGHT_EMISSION_PROVIDERS = {
    "com/stardew/craft/block/utility/ToggleableDecorLightBlock.java": "ToggleableDecorLightBlock",
    "com/stardew/craft/block/decor/ToggleableCeilingLightBlock.java": "ToggleableCeilingLightBlock",
    "com/stardew/craft/block/decor/ToggleableWallLightBlock.java": "ToggleableWallLightBlock",
    "com/stardew/craft/block/decor/LargeFireplaceDecorBlock.java": "LargeFireplaceDecorBlock",
    "com/stardew/craft/gingerisland/IslandFlameBlock.java": "IslandFlameBlock",
}
EXPLICIT_BUS_METHOD_REFERENCES = {
    "com/stardew/craft/interior/door/TownDoorRuntime.java": (
        ("bus", "TownDoorNetwork::register", "com.stardew.craft.platform.fabric.network.RegisterPayloadHandlersEvent"),
        ("FabricEvents.GAME", "TownDoorRuntime::tick", "LevelTickEvent.Post"),
        ("FabricEvents.GAME", "TownDoorRuntime::interact", "PlayerInteractEvent.RightClickBlock"),
        ("FabricEvents.GAME", "TownDoorRuntime::unload", "LevelEvent.Unload"),
    ),
    "com/stardew/craft/client/interior/TownDoorClient.java": (
        ("bus", "TownDoorComposite::registerReloadListener", "com.stardew.craft.platform.fabric.client.event.RegisterClientReloadListenersEvent"),
    ),
    "com/stardew/craft/gametest/MineRockClumpGameTests.java": (
        ("com.stardew.craft.platform.fabric.event.FabricEvents.GAME", "cancel",
         "com.stardew.craft.platform.fabric.event.level.BlockEvent.BreakEvent"),
    ),
    "com/stardew/craft/gametest/GingerIslandPlacementGameTests.java": (
        ("FabricEvents.GAME", "cancel", "BlockEvent.EntityPlaceEvent"),
    ),
    "com/stardew/craft/gametest/NativeMonsterSettlementGameTests.java": (
        ("FabricEvents.GAME", "post", "LivingDamageEvent.Post"),
        ("FabricEvents.GAME", "cancel", "LivingDeathEvent"),
    ),
    "com/stardew/craft/gametest/MummyBombRegressionGameTests.java": (
        ("FabricEvents.GAME", "onDeath", "LivingDeathEvent"),
    ),
}


def convert_explicit_bus_listeners(source: str, relative: str) -> tuple[str, int]:
    count = 0
    for bus, listener, event in EXPLICIT_BUS_METHOD_REFERENCES.get(relative, ()):
        old = bus + ".addListener(" + listener + ")"
        count += source.count(old)
        source = source.replace(old, bus + ".addListener(" + event + ".class, " + listener + ")")
    if relative in ("com/stardew/craft/interior/door/TownDoorRuntime.java",
                    "com/stardew/craft/client/interior/TownDoorClient.java"):
        source, changed = re.subn(r"\bFabricEvents\.GAME\.addListener\(\(([\w.]+)\s+(\w+)\)\s*->",
                r"FabricEvents.GAME.addListener(\1.class, (\1 \2) ->", source)
        count += changed
    return source, count


def convert_data_map_queries(source: str, relative: str) -> tuple[str, int]:
    if relative not in DATA_MAP_CALLERS:
        return source, 0
    code = NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)
    receivers = (r"BuiltInRegistries\.ITEM\.wrapAsHolder\(stack\.getItem\(\)\)",
                 r"stack\.getItem\(\)\.builtInRegistryHolder\(\)",
                 r"state\.getBlock\(\)\.builtInRegistryHolder\(\)",
                 r"type\.builtInRegistryHolder\(\)")
    pattern = r"(" + "|".join(receivers) + r")\s*\.getData\((StardewDataMaps\.[A-Z_]+)\)"
    matches = list(re.finditer(pattern, code))
    for match in reversed(matches):
        source = source[:match.start()] + (
                "com.stardew.craft.platform.fabric.datamap.DataMaps.get("
                + source[match.start(1):match.end(1)] + ", " + match.group(2) + ")") + source[match.end():]
    return source, len(matches)


def convert_tool_modification(source: str, relative: str) -> tuple[str, int]:
    count = 0
    if relative in TOOL_MODIFICATION_PROVIDERS:
        name = TOOL_MODIFICATION_PROVIDERS[relative]
        pattern = r"(\bclass\s+" + re.escape(name) + r"\s+extends\s+[\w.]+)(\s*\{)"
        source, changed = re.subn(pattern, r"\1 implements com.stardew.craft.platform.fabric.extension.ToolModificationProvider\2", source)
        if changed != 1:
            raise ValueError("Review the changed tool-modification provider declaration: " + relative)
        count += changed
    if relative not in TOOL_MODIFICATION_CALLERS:
        return source, count
    code = NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)
    # Audited receivers are identifiers or dotted calls with simple arguments.
    # A new nested receiver remains unresolved until explicitly reviewed.
    pattern = r"\b([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*(?:\([^()\n]*\))?)*)\.getToolModifiedState\("
    matches = [match for match in re.finditer(pattern, code)
               if not code[:match.start()].rstrip().endswith(".")]
    for match in reversed(matches):
        receiver = source[match.start(1):match.end(1)]
        if receiver in ("super", "Blocks.DIRT"):
            replacement = "com.stardew.craft.platform.fabric.extension.BlockActions.defaultModifiedState("
        else:
            replacement = "com.stardew.craft.platform.fabric.extension.BlockActions.getModifiedState(" + receiver + ", "
        source = source[:match.start()] + replacement + source[match.end():]
    return source, count + len(matches)


def convert_item_abilities(source: str, relative: str) -> tuple[str, int]:
    count = 0
    if relative in ITEM_ABILITY_PROVIDERS:
        name = ITEM_ABILITY_PROVIDERS[relative]
        pattern = r"(\bclass\s+" + re.escape(name) + r"\s+extends\s+[^{}]+?\bimplements\s+[^{}]+?)(\s*\{)"
        source, changed = re.subn(pattern, lambda match: match.group(1).rstrip()
                + ", com.stardew.craft.platform.fabric.extension.ItemAbilityProvider" + match.group(2), source)
        if changed != 1:
            raise ValueError("Review the changed ItemAbility provider declaration: " + relative)
        count += changed
    if relative in ITEM_ABILITY_CALLERS:
        code = NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)
        matches = [match for match in re.finditer(r"\b(tool|main|stack)\.canPerformAction\(", code)
                   if not code[:match.start()].rstrip().endswith(".")]
        for match in reversed(matches):
            source = source[:match.start()] + (
                    "com.stardew.craft.platform.fabric.extension.ItemActions.canPerformAction("
                    + match.group(1) + ", ") + source[match.end():]
        count += len(matches)
    return source, count


def convert_persistent_data(source: str, relative: str) -> tuple[str, int]:
    # All 443 qualified calls in the audited tree have a single identifier as
    # receiver. Keep complex future calls unresolved rather than guessing Java
    # expression boundaries. The three inherited bare calls are path-allowlisted.
    code = NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)
    edits = []
    for match in re.finditer(r"\b([A-Za-z_$][\w$]*)\.getPersistentData\(\)", code):
        # A suffix of a longer chain (a.b.getPersistentData) is not a simple receiver.
        start = match.start()
        if code[:start].rstrip().endswith("."):
            continue
        edits.append((start, match.end(), match.group(1)))
    if relative in BARE_PERSISTENT_DATA_CALLERS:
        for match in re.finditer(r"(?<![\w$.])getPersistentData\(\)", code):
            if code[:match.start()].rstrip().endswith("."):
                continue
            edits.append((match.start(), match.end(), "this"))
    for start, end, receiver in sorted(edits, reverse=True):
        source = source[:start] + (
            "com.stardew.craft.platform.fabric.data.PersistentEntityData.get(" + receiver + ")"
        ) + source[end:]
    return source, len(edits)


def convert_saved_data_factories(source: str) -> tuple[str, int]:
    code = NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)
    patterns = [r"\bnew\s+(?:net\.minecraft\.world\.level\.saveddata\.)?SavedData\.Factory\s*<>\s*\("]
    # The inherited short name is used only in SavedData subclasses in this tree.
    # Other Factory constructors (renderers, registries, etc.) must remain intact.
    if re.search(r"\bextends\s+(?:net\.minecraft\.world\.level\.saveddata\.)?SavedData\b", code):
        patterns.append(r"\bnew\s+Factory\s*<>\s*\(")
    edits = [match.span() for pattern in patterns for match in re.finditer(pattern, code)]
    for start, end in sorted(edits, reverse=True):
        source = source[:start] + "com.stardew.craft.platform.fabric.data.SavedDataFactories.create(" + source[end:]
    return source, len(edits)


for _name in ("KeyConflictContext", "IKeyConflictContext", "KeyModifier"):
    PACKAGE_MOVES["net.neoforged.neoforge.client.settings." + _name] = (
        "com.stardew.craft.platform.fabric.client.keys." + _name)

for _original in ("net.neoforged.neoforge.common.brewing.IBrewingRecipe", "net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent"):
    PACKAGE_MOVES[_original] = "com.stardew.craft.platform.fabric.brewing." + _original.rsplit(".", 1)[-1]

PACKAGE_MOVES["net.minecraft.client.renderer.blockentity.BlockEntityRenderer"] = (
    "com.stardew.craft.platform.fabric.client.blockentity.BlockEntityRenderer")

PACKAGE_MOVES["net.neoforged.neoforge.event.entity.living.MobEffectEvent"] = (
    "com.stardew.craft.platform.fabric.event.entity.living.MobEffectEvent")

PACKAGE_MOVES["net.neoforged.neoforge.client.ClientHooks"] = "com.stardew.craft.platform.fabric.client.hook.ClientHooks"
for _event in ("ScreenEvent", "RenderPlayerEvent", "RenderLivingEvent"):
    PACKAGE_MOVES["net.neoforged.neoforge.client.event." + _event] = "com.stardew.craft.platform.fabric.client.event." + _event

PACKAGE_MOVES["net.neoforged.neoforge.event.entity.living.LivingDropsEvent"] = (
    "com.stardew.craft.platform.fabric.event.entity.living.LivingDropsEvent")

PACKAGE_MOVES["net.neoforged.neoforge.event.level.BlockDropsEvent"] = (
    "com.stardew.craft.platform.fabric.event.level.BlockDropsEvent")

for _name in ("TicketController", "RegisterTicketControllersEvent"):
    PACKAGE_MOVES["net.neoforged.neoforge.common.world.chunk." + _name] = "com.stardew.craft.platform.fabric.chunk." + _name

for _event in ("CanContinueSleepingEvent", "CanPlayerSleepEvent", "PlayerWakeUpEvent"):
    PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player." + _event] = "com.stardew.craft.platform.fabric.event.sleep." + _event
PACKAGE_MOVES["net.neoforged.neoforge.event.level.SleepFinishedTimeEvent"] = "com.stardew.craft.platform.fabric.event.sleep.SleepFinishedTimeEvent"

PACKAGE_MOVES["net.neoforged.neoforge.entity.PartEntity"] = "com.stardew.craft.platform.fabric.entity.PartEntity"
for _name in ("CriticalHitEvent", "SweepAttackEvent"):
    PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player." + _name] = "com.stardew.craft.platform.fabric.event.entity.player." + _name

PACKAGE_MOVES["net.neoforged.neoforge.event.level.ExplosionEvent"] = "com.stardew.craft.platform.fabric.event.level.ExplosionEvent"
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent"] = "com.stardew.craft.platform.fabric.event.entity.living.LivingEntityUseItemEvent"

PACKAGE_MOVES["net.neoforged.neoforge.event.level.ChunkDataEvent"] = "com.stardew.craft.platform.fabric.event.level.ChunkDataEvent"

PACKAGE_MOVES["net.neoforged.neoforge.common.Tags"] = "com.stardew.craft.platform.fabric.extension.Tags"

for _event in ("RegisterDimensionSpecialEffectsEvent", "RegisterRenderBuffersEvent", "ClientChatReceivedEvent", "RegisterItemDecorationsEvent"):
    PACKAGE_MOVES["net.neoforged.neoforge.client.event." + _event] = "com.stardew.craft.platform.fabric.client.event." + _event
PACKAGE_MOVES["net.neoforged.neoforge.client.IItemDecorator"] = "com.stardew.craft.platform.fabric.client.decoration.IItemDecorator"

PACKAGE_MOVES["net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent"] = "com.stardew.craft.platform.fabric.event.entity.living.LivingChangeTargetEvent"

PACKAGE_MOVES["net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent"] = "com.stardew.craft.platform.fabric.event.entity.EntityTravelToDimensionEvent"

PACKAGE_MOVES["net.neoforged.neoforge.event.entity.EntityTeleportEvent"] = "com.stardew.craft.platform.fabric.event.entity.EntityTeleportEvent"

PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player.ItemFishedEvent"] = "com.stardew.craft.platform.fabric.event.entity.player.ItemFishedEvent"
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.ProjectileImpactEvent"] = "com.stardew.craft.platform.fabric.event.entity.ProjectileImpactEvent"

for _original in ("net.neoforged.neoforge.event.AnvilUpdateEvent", "net.neoforged.neoforge.event.GrindstoneEvent", "net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent"):
    PACKAGE_MOVES[_original] = "com.stardew.craft.platform.fabric.event.enchant." + _original.rsplit(".", 1)[-1]

PACKAGE_MOVES["net.neoforged.neoforge.event.level.block.CropGrowEvent"] = "com.stardew.craft.platform.fabric.event.level.block.CropGrowEvent"
for _name in ("BlockGrowFeatureEvent", "PistonEvent"):
    PACKAGE_MOVES["net.neoforged.neoforge.event.level." + _name] = "com.stardew.craft.platform.fabric.event.level." + _name
PACKAGE_MOVES["net.neoforged.neoforge.event.entity.player.BonemealEvent"] = "com.stardew.craft.platform.fabric.event.entity.player.BonemealEvent"
for _name in ("MobSpawnEvent", "FinalizeSpawnEvent"):
    PACKAGE_MOVES["net.neoforged.neoforge.event.entity.living." + _name] = "com.stardew.craft.platform.fabric.event.entity.living." + _name

def java_tokens(source: str) -> tuple[list, dict[int, int]]:
    """Mask strings/comments and pair delimiters before editing call expressions."""
    code = NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)
    tokens = list(re.finditer(r"[A-Za-z_$][\w$]*|[^\s]", code))
    pairs, stack = {}, []
    for i, token in enumerate(tokens):
        value = token.group()
        if value in ("(", "[", "{"):
            stack.append(i)
        elif value in (")", "]", "}"):
            if not stack or tokens[stack[-1]].group() != {")": "(", "]": "[", "}": "{"}[value]:
                raise ValueError("Unbalanced Java delimiters in Fabric overlay")
            opening = stack.pop()
            pairs[opening], pairs[i] = i, opening
    if stack:
        raise ValueError("Unbalanced Java delimiters in Fabric overlay")
    return tokens, pairs


def receiver_start(tokens: list, pairs: dict[int, int], end: int) -> int:
    """Find a qualified Java receiver, including calls, array access and groups."""
    i = end
    if tokens[i].group() in (")", "]"):
        closing = tokens[i].group()
        i = pairs[i]
        if closing == "]":
            i = receiver_start(tokens, pairs, i - 1)
        elif i > 0 and re.fullmatch(r"[A-Za-z_$][\w$]*", tokens[i - 1].group()) \
                and tokens[i - 1].group() not in ("return", "throw", "if", "while", "switch", "synchronized"):
            i -= 1
    while i >= 2 and tokens[i - 1].group() == ".":
        i = receiver_start(tokens, pairs, i - 2)
    return i


def call_arguments(source: str, tokens: list, pairs: dict[int, int], opening: int) -> list[str]:
    end, cursor = pairs[opening], opening + 1
    if cursor == end:
        return []
    start, arguments = tokens[opening].end(), []
    while cursor < end:
        value = tokens[cursor].group()
        if value in ("(", "[", "{"):
            cursor = pairs[cursor] + 1
            continue
        if value == ",":
            arguments.append(source[start:tokens[cursor].start()].strip())
            start = tokens[cursor].end()
        cursor += 1
    arguments.append(source[start:tokens[end].start()].strip())
    return arguments


def convert_light_calls(source: str, relative: str) -> tuple[str, int]:
    count = 0
    if relative in LIGHT_EMISSION_PROVIDERS:
        name = LIGHT_EMISSION_PROVIDERS[relative]
        def provider(match):
            declaration = match.group(1).rstrip()
            return declaration + (", " if re.search(r"\bimplements\b", declaration) else " implements ") \
                    + "com.stardew.craft.platform.fabric.light.LightEmissionProvider {"
        source, count = re.subn(r"(\bclass\s+" + name + r"\s+[^{}]+)\{", provider, source)
        if count != 1:
            raise ValueError("Review the changed light emission provider: " + relative)
    if not re.search(r"\.(getLightEmission|getAuxLightManager)\s*\(", source):
        return source, count
    while True:
        tokens, pairs = java_tokens(source)
        edit = None
        for i in range(len(tokens) - 2, 1, -1):
            name = tokens[i].group()
            if name not in ("getLightEmission", "getAuxLightManager") or tokens[i - 1].group() != "." \
                    or tokens[i + 1].group() != "(":
                continue
            args = call_arguments(source, tokens, pairs, i + 1)
            if len(args) != (2 if name == "getLightEmission" else 1):
                continue
            start = receiver_start(tokens, pairs, i - 2)
            receiver = source[tokens[start].start():tokens[i - 1].start()].strip()
            target = "com.stardew.craft.platform.fabric.light." + (
                    "LightEmissionHooks.getLightEmission" if name == "getLightEmission" else "AuxiliaryLights.getManager")
            edit = tokens[start].start(), tokens[pairs[i + 1]].end(), target + "(" + ", ".join([receiver] + args) + ")"
            break
        if edit is None:
            break
        start, end, replacement = edit
        source = source[:start] + replacement + source[end:]
        count += 1
    return source, count


def convert_block_behaviors(source: str, relative: str) -> tuple[str, int]:
    providers = {
        "com/stardew/craft/block/mine/MineTimberSupportBlock.java": "MineTimberSupportBlock",
        "com/stardew/craft/block/decor/PlaygroundBlock.java": "PlaygroundBlock",
    }
    count = 0
    if relative in providers:
        def provider(match):
            declaration = match.group(1).rstrip()
            return declaration + (", " if re.search(r"\bimplements\b", declaration) else " implements ") \
                    + "com.stardew.craft.platform.fabric.blockextension.BlockBehaviorProvider {"
        source, count = re.subn(r"(\bclass\s+" + providers[relative] + r"\s+[^{}]+)\{", provider, source)
        if count != 1:
            raise ValueError("Review the changed block behavior provider: " + relative)
    helper = "com.stardew.craft.platform.fabric.blockextension.BlockBehaviors"
    arities = {"getSoundType": (3, 4), "getFriction": (3,), "getExplosionResistance": (3,), "isLadder": (4,)}
    if not re.search(r"\.(getSoundType|getFriction|getExplosionResistance|isLadder)\s*\(", source):
        return source, count
    while True:
        tokens, pairs = java_tokens(source)
        edit = None
        for i in range(len(tokens) - 2, 1, -1):
            name = tokens[i].group()
            if name not in arities or tokens[i - 1].group() != "." or tokens[i + 1].group() != "(":
                continue
            args = call_arguments(source, tokens, pairs, i + 1)
            if len(args) not in arities[name]:
                continue
            start = receiver_start(tokens, pairs, i - 2)
            receiver = source[tokens[start].start():tokens[i - 1].start()].strip()
            if receiver == helper:
                continue
            edit = tokens[start].start(), tokens[pairs[i + 1]].end(), \
                    helper + "." + name + "(" + ", ".join([receiver] + args) + ")"
            break
        if edit is None:
            return source, count
        start, end, replacement = edit
        source = source[:start] + replacement + source[end:]
        count += 1


def convert_block_lifecycle(source: str, relative: str) -> tuple[str, int]:
    providers = {"block/" + folder + "/" + name: "removal.PlayerDestroyProvider"
            for folder, name in (("utility", "WizardBuildingBlock"), ("utility", "FishPondManagerBlock"),
                ("utility", "SiloManagerBlock"), ("mine", "MineLadderBlock"), ("mine", "MineExitBlock"), ("mine", "ElevatorBlock"))}
    providers.update({"block/decor/GardenPlanterBlock": "plant.PlantSoilProvider",
        "block/terrain/TerrainGrassBlock": "plant.TreeGrowProvider", "block/mine/MineRailBlock": "rail.RailBehaviorProvider"})
    providers.update({"block/" + path: "piston.PistonPushProvider" for path in (
        "tree/WildTreeSaplingBlock", "nature/BerryBushBlock", "tree/fruit/FruitTreeSaplingBlock")})
    providers.update({"block/" + path: "clone.CloneStackProvider" for path in (
        "cooking/PlacedArtisanDrinkBlock", "decor/SupplyCrateBlock", "decor/PlacedFishBlock")})
    providers["templates/MaterialTemplateBlock"] = "face.ExternalFaceHidingProvider"
    short = relative.removeprefix("com/stardew/craft/").removesuffix(".java")
    count = 0
    if short in providers:
        def provider(match):
            declaration = match.group(1).rstrip()
            return declaration + (", " if re.search(r"\bimplements\b", declaration) else " implements ") \
                    + "com.stardew.craft.platform.fabric.blockextension." + providers[short] + " {"
        source, count = re.subn(r"(\bclass\s+" + short.rsplit("/", 1)[-1] + r"\s+[^{}]+)\{", provider, source)
        if count != 1:
            raise ValueError("Review the changed block lifecycle provider: " + relative)
        if providers[short] == "removal.PlayerDestroyProvider":
            source, changed = re.subn(r"\bsuper\.onDestroyedByPlayer\(",
                    "com.stardew.craft.platform.fabric.blockextension.removal.PlayerBlockRemoval.defaultRemove(", source)
            if changed != 1: raise ValueError("Review the changed block removal super call: " + relative)
            count += changed
    methods = {"canSustainPlant": (4, "plant.PlantHooks"), "isFertile": (2, "plant.PlantHooks"),
            "getRailDirection": (4, "rail.RailHooks")}
    if not re.search(r"\.(canSustainPlant|isFertile|getRailDirection)\s*\(", source):
        return source, count
    while True:
        tokens, pairs = java_tokens(source)
        edit = None
        for i in range(len(tokens) - 2, 1, -1):
            name = tokens[i].group()
            if name not in methods or tokens[i - 1].group() != "." or tokens[i + 1].group() != "(":
                continue
            args = call_arguments(source, tokens, pairs, i + 1)
            arity, target = methods[name]
            if len(args) != arity: continue
            start = receiver_start(tokens, pairs, i - 2)
            receiver = source[tokens[start].start():tokens[i - 1].start()].strip()
            helper = "com.stardew.craft.platform.fabric.blockextension." + target
            edit = tokens[start].start(), tokens[pairs[i + 1]].end(), \
                    helper + "." + name + "(" + ", ".join([receiver] + args) + ")"
            break
        if edit is None: return source, count
        start, end, replacement = edit
        source = source[:start] + replacement + source[end:]
        count += 1


def convert_key_and_widget_calls(source: str, relative: str) -> tuple[str, int]:
    helper = "com.stardew.craft.platform.fabric.client.keys.FabricKeyMappings"
    count = 0
    if relative in ("com/stardew/craft/client/ModKeyMappings.java", "com/stardew/craft/client/DebugKeybinds.java"):
        source, changed = re.subn(r"\bnew\s+KeyMapping\s*\(", helper + ".create(", source)
        count += changed
    if relative == "com/stardew/craft/client/DebugKeybinds.java":
        source = re.sub(r"import net\.neoforged\.fml\.loading\.FMLLoader;\s*", "", source)
        source, changed = re.subn(r"\bFMLLoader\.isProduction\(\)", helper + ".isProduction()", source)
        count += changed
    key_paths = {
        "com/stardew/craft/client/emote/EmoteWheelClient.java",
        "com/stardew/craft/client/CutsceneSkipHoldHud.java",
        "com/stardew/craft/client/ModClientEvents.java",
        "com/stardew/craft/client/weapon/FemurSlamInput.java",
    }
    if relative not in key_paths and not re.search(r"\.setTextShadow\s*\(", source):
        return source, count
    while True:
        tokens, pairs = java_tokens(source)
        edit = None
        for i in range(len(tokens) - 2, 1, -1):
            name = tokens[i].group()
            if tokens[i - 1].group() != "." or tokens[i + 1].group() != "(":
                continue
            if name != "setTextShadow" and (relative not in key_paths or name not in (
                    "getKey", "getKeyModifier", "getKeyConflictContext", "isActiveAndMatches")):
                continue
            start = receiver_start(tokens, pairs, i - 2)
            receiver = source[tokens[start].start():tokens[i - 1].start()].strip()
            if name != "setTextShadow" and not (receiver.startswith("ModKeyMappings.") or receiver == "mc.options.keyUse"
                    or relative == "com/stardew/craft/client/emote/EmoteWheelClient.java" and receiver == "mapping"):
                continue
            args = call_arguments(source, tokens, pairs, i + 1)
            target = "com.stardew.craft.platform.fabric.client.widget.EditBoxExtensions" if name == "setTextShadow" else helper
            if receiver == target:
                continue
            edit = tokens[start].start(), tokens[pairs[i + 1]].end(), \
                    target + "." + name + "(" + ", ".join([receiver] + args) + ")"
            break
        if edit is None:
            return source, count
        start, end, replacement = edit
        source = source[:start] + replacement + source[end:]
        count += 1


def convert_item_damage_provider(source: str, relative: str) -> tuple[str, int]:
    providers = {
        "item/" + name for name in ("SpecialCharmItem", "DwarvishTranslationGuideItem", "SkullKeyItem", "PowerSpecialItem",
            "WizardBuildingItem", "BearKnowledgeItem", "RustyKeyItem", "MagnifyingGlassItem")
    } | {"item/misc/" + name for name in ("GalaxySoulItem", "StardropTeaItem", "IridiumMilkItem", "AuctionPaddleItem",
            "GoldenPumpkinItem", "MagicRockCandyItem", "MoneyContractItem", "StardropItem")} | {"item/tool/WarpWandItem"}
    short = relative.removeprefix("com/stardew/craft/").removesuffix(".java")
    if short not in providers:
        return source, 0
    def provider(match):
        declaration = match.group(1).rstrip()
        return declaration + (", " if re.search(r"\bimplements\b", declaration) else " implements ") \
                + "com.stardew.craft.platform.fabric.item.damage.ItemDamageProvider {"
    source, count = re.subn(r"(\bclass\s+" + short.rsplit("/", 1)[-1] + r"\s+[^{}]+)\{", provider, source)
    if count != 1:
        raise ValueError("Review the changed item damage provider: " + relative)
    return source, count


def convert_effect_attributes(source: str, relative: str) -> tuple[str, int]:
    if relative != "com/stardew/craft/effect/ModMobEffects.java":
        return source, 0
    count = 0
    while True:
        tokens, pairs = java_tokens(source)
        edit = None
        for i in range(len(tokens) - 2, 1, -1):
            if tokens[i].group() != "addAttributeModifier" or tokens[i - 1].group() != "." or tokens[i + 1].group() != "(":
                continue
            args = call_arguments(source, tokens, pairs, i + 1)
            if len(args) != 4 or "->" not in args[-1]:
                continue
            start = receiver_start(tokens, pairs, i - 2)
            receiver = source[tokens[start].start():tokens[i - 1].start()].strip()
            replacement = "com.stardew.craft.platform.fabric.extension.MobEffectAttributeFunctions.add(" \
                    + ", ".join([receiver] + args) + ")"
            edit = tokens[start].start(), tokens[pairs[i + 1]].end(), replacement
            break
        if edit is None:
            return source, count
        start, end, replacement = edit
        source = source[:start] + replacement + source[end:]
        count += 1


def convert_model_calls(source: str, relative: str) -> tuple[str, int]:
    if not (relative.startswith("com/stardew/craft/client/")
            or relative.startswith("com/stardew/craft/templates/client/")
            or relative == "com/stardew/craft/mixin/ColoredLightModelMixin.java"):
        return source, 0
    helper = "com.stardew.craft.platform.fabric.client.model.BakedModels"
    renderer = "com.stardew.craft.platform.fabric.client.model.BlockModelRendering"
    quad = "com.stardew.craft.platform.fabric.client.model.ModelQuad"
    arities = {"getQuads": {5}, "getModelData": {0, 4}, "getParticleIcon": {1},
               "getRenderTypes": {2, 3}, "getRenderPasses": {2},
               "applyTransform": {3}, "useAmbientOcclusion": {3}, "hasAmbientOcclusion": {0},
               "renderModel": {11}, "renderSingleBlock": {5, 7}, "tesselateBlock": {12}}
    count = 0
    while True:
        tokens, pairs = java_tokens(source)
        edit = None
        for i in range(len(tokens) - 2, 1, -1):
            name = tokens[i].group()
            if name not in arities or tokens[i - 1].group() != "." or tokens[i + 1].group() != "(":
                continue
            args = call_arguments(source, tokens, pairs, i + 1)
            if len(args) not in arities[name]:
                continue
            start = receiver_start(tokens, pairs, i - 2)
            receiver = source[tokens[start].start():tokens[i - 1].start()].strip()
            if receiver in ("super", helper, quad, renderer):
                continue
            if name == "hasAmbientOcclusion":
                target = quad + ".ambientOcclusion"
            elif name in ("renderModel", "renderSingleBlock", "tesselateBlock"):
                target = renderer + "." + name
            else:
                target = helper + "." + name
            replacement = target + "(" + ", ".join([receiver] + args) + ")"
            edit = tokens[start].start(), tokens[pairs[i + 1]].end(), replacement
            break
        if edit is None:
            break
        start, end, replacement = edit
        source = source[:start] + replacement + source[end:]
        count += 1
    # Only the six-argument Neo quad constructor carries the extra AO flag.
    tokens, pairs = java_tokens(source)
    edits = []
    for i in range(1, len(tokens) - 1):
        if tokens[i].group() == "BakedQuad" and tokens[i - 1].group() == "new" \
                and tokens[i + 1].group() == "(" and len(call_arguments(source, tokens, pairs, i + 1)) == 6:
            edits.append((tokens[i - 1].start(), tokens[i].end()))
    for start, end in reversed(edits):
        source = source[:start] + quad + ".create" + source[end:]
    return source, count + len(edits)


def convert(source: str, relative: str = "") -> tuple[str, dict[str, int]]:
    changes = {}
    for original, replacement in PACKAGE_MOVES.items():
        pattern = r"\b" + r"\s*\.\s*".join(re.escape(segment) for segment in original.split(".")) + r"\b"
        source, count = re.subn(pattern, replacement, source)
        if count:
            changes[original] = count
    source, count = re.subn(r"\bimport\s+net\.neoforged\.neoforge\.gametest\.\*\s*;",
            "import com.stardew.craft.platform.fabric.gametest.GameTestHolder;\n"
            "import com.stardew.craft.platform.fabric.gametest.PrefixGameTestTemplate;", source)
    code = NON_CODE.sub(lambda match: re.sub(r"[^\n]", " ", match.group()), source)
    if re.search(r"\bimport\s+net\.minecraft\.gametest\.framework\.\*\s*;", code) and re.search(r"@GameTest\b", code):
        # An explicit import takes precedence over the native wildcard. Otherwise
        # templateNamespace would accidentally bind to vanilla's annotation.
        if not re.search(r"\bimport\s+com\.stardew\.craft\.platform\.fabric\.gametest\.GameTest\s*;", code):
            source = re.sub(r"(\bimport\s+net\.minecraft\.gametest\.framework\.\*\s*;)",
                    r"\1\nimport com.stardew.craft.platform.fabric.gametest.GameTest;", source, count=1)
            count += 1
    if count:
        changes["GameTest.wildcardMetadata"] = count
    if relative in ("com/stardew/craft/client/monsternative/NativeShamanCurseRenderer.java",
                    "com/stardew/craft/client/monsternative/NativeSquidFireballRenderer.java"):
        source, count = re.subn(r"\bimport\s+net\.neoforged\.neoforge\.client\.event\.\*\s*;",
                "import com.stardew.craft.platform.fabric.client.event.EntityRenderersEvent;\n"
                "import com.stardew.craft.platform.fabric.client.event.RegisterClientReloadListenersEvent;", source)
        changes["NativeProjectileRenderers.explicitClientEvents"] = count
    # The two loaders use different physical-side enum names. Fully qualified
    # references were already handled above; these are the imported spellings.
    for pattern, replacement in (
        (r"\bServerLifecycleHooks\.getCurrentServer\(\)", "FabricServerLifecycle.getCurrentServer()"),
        (r"\bModList\.get\(\)", "FabricLoader.getInstance()"),
        (r"\bnet\.fabricmc\.loader\.api\.FabricLoader\.get\(\)",
         "net.fabricmc.loader.api.FabricLoader.getInstance()"),
        (r"\bFabricLoader\.getInstance\(\)\.isLoaded\(", "FabricLoader.getInstance().isModLoaded("),
        (r"\bnet\.fabricmc\.loader\.api\.FabricLoader\.getInstance\(\)\.isLoaded\(",
         "net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("),
        (r"\bNeoForge\.EVENT_BUS\b", "FabricEvents.GAME"),
        (r"\bcom\.stardew\.craft\.platform\.fabric\.event\.FabricEvents\.EVENT_BUS\b",
         "com.stardew.craft.platform.fabric.event.FabricEvents.GAME"),
        (r"\bDist\.CLIENT\b", "EnvType.CLIENT"),
        (r"\bDist\.DEDICATED_SERVER\b", "EnvType.SERVER"),
        (r"\bnet\.fabricmc\.api\.EnvType\.DEDICATED_SERVER\b", "net.fabricmc.api.EnvType.SERVER"),
        (r"@OnlyIn\b", "@Environment"),
        (r"(?m)^import net\.neoforged\.fml\.loading\.FMLEnvironment;\s*\n", ""),
        (r"\b(?:net\.neoforged\.fml\.loading\.)?FMLEnvironment\.dist\.isClient\(\)",
         "(net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT)"),
        (r"\b(?:net\.neoforged\.fml\.loading\.)?FMLEnvironment\.dist\b",
         "net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType()"),
    ):
        source, count = re.subn(pattern, replacement, source)
        if count:
            changes[pattern] = count
    source, count = convert_persistent_data(source, relative)
    if count:
        changes["Entity.getPersistentData"] = count
    source, count = convert_saved_data_factories(source)
    if count:
        changes["SavedData.Factory.nullableDataFixType"] = count
    source, count = convert_item_abilities(source, relative)
    if count:
        changes["ItemAbility.stackSensitiveQueries"] = count
    source, count = convert_tool_modification(source, relative)
    if count:
        changes["BlockState.cancellableToolModification"] = count
    source, count = convert_data_map_queries(source, relative)
    if count:
        changes["Holder.ownerScopedDataMapQueries"] = count
    source, count = convert_explicit_bus_listeners(source, relative)
    if count:
        changes["EventBus.explicitListenerTypes"] = count
    source, count = convert_model_calls(source, relative)
    if count:
        changes["BakedModel.explicitExtendedDispatch"] = count
    source, count = convert_light_calls(source, relative)
    if count:
        changes["LightEmission.contextualAndSnapshotQueries"] = count
    source, count = convert_block_behaviors(source, relative)
    if count:
        changes["BlockBehavior.contextualQueries"] = count
    source, count = convert_block_lifecycle(source, relative)
    if count:
        changes["BlockLifecycle.removalPlantAndRail"] = count
    source, count = convert_key_and_widget_calls(source, relative)
    if count:
        changes["Client.nativeKeyAndWidgetExtensions"] = count
    source, count = convert_item_damage_provider(source, relative)
    if count:
        changes["ItemStack.damageProvider"] = count
    source, count = convert_effect_attributes(source, relative)
    if count:
        changes["MobEffect.amplifierFunctions"] = count
    if relative in BLOCK_ENTITY_LOAD_CLASSES:
        source, count = re.subn(r"\bextends\s+(?:net\.minecraft\.world\.level\.block\.entity\.)?BlockEntity\b",
                "extends com.stardew.craft.platform.fabric.blockentity.FabricBlockEntity", source)
        if count != 1:
            raise ValueError("Review the changed BlockEntity load declaration: " + relative)
        changes["BlockEntity.deferredOnLoad"] = count
    if relative == "com/stardew/craft/floor/SurfaceFloorData.java":
        source, count = re.subn(r"\blevel\.captureBlockSnapshots\b",
                "com.stardew.craft.platform.fabric.event.level.PlacementTransactions.isCapturing(level)", source)
        changes["SurfaceFloorData.scopedPlacementCapture"] = count
    if relative == "com/stardew/craft/gametest/SurfaceFloorGameTests.java":
        opening = r"int snapshots = level\.capturedBlockSnapshots\.size\(\);\s*level\.captureBlockSnapshots = true;\s*try\s*\{"
        source, opened = re.subn(opening,
                "try (var capture = com.stardew.craft.platform.fabric.event.level.PlacementTransactions.beginCapture(level)) {", source)
        closing = r"\}\s*finally\s*\{\s*level\.captureBlockSnapshots = false;\s*level\.capturedBlockSnapshots\.subList\(snapshots,level\.capturedBlockSnapshots\.size\(\)\)\.clear\(\);\s*\}"
        source, closed = re.subn(closing, "}", source)
        if opened != 1 or closed != 1:
            raise ValueError("Review the changed SurfaceFloor capture regression test")
        changes["SurfaceFloorGameTests.scopedPlacementCapture"] = 1
    if relative == "com/stardew/craft/config/ConfigMigration.java":
        source = source.replace("import net.neoforged.fml.event.config.ModConfigEvent;",
                                "import net.neoforged.fml.config.ModConfig;")
        source = source.replace("onConfigEvent(ModConfigEvent event)", "onConfigEvent(ModConfig config)")
        source = source.replace("event.getConfig()", "config")
        changes["ConfigMigration.FabricConfigEvent"] = 1
    if relative == "com/stardew/craft/player/ModUpdateChecker.java":
        source = source.replace("FabricLoader.getInstance().getModContainerById(",
                                "FabricLoader.getInstance().getModContainer(")
        source = source.replace("container.getModInfo().getVersion().toString()",
                                "container.getMetadata().getVersion().getFriendlyString()")
        source = source.replace(r'\"neoforge\"', r'\"fabric\"')
        changes["ModUpdateChecker.FabricVersionFilter"] = 1
    if relative == "com/stardew/craft/command/CameraDebugCommand.java":
        source = source.replace("import net.neoforged.fml.loading.FMLPaths;", "")
        source = source.replace("FMLPaths.CONFIGDIR.get()",
                                "net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()")
        changes["CameraDebugCommand.FabricConfigDir"] = 1
    if relative == "com/stardew/craft/mixin/ItemRendererWeaponTrailCaptureMixin.java":
        target = "Lnet/neoforged/neoforge/client/ClientHooks;handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemDisplayContext;Z)Lnet/minecraft/client/resources/model/BakedModel;"
        count = source.count(target)
        if count != 1:
            raise ValueError("Review the changed weapon trail transform injection")
        source = source.replace(target, "Lnet/minecraft/client/renderer/block/model/ItemTransform;apply(ZLcom/mojang/blaze3d/vertex/PoseStack;)V")
        changes["WeaponTrail.nativeTransformAfter"] = count
    if relative == "com/stardew/craft/gametest/NativeMonsterSettlementGameTests.java":
        source = source.replace("import net.neoforged.neoforge.event.entity.living.*;",
            "import com.stardew.craft.platform.fabric.event.entity.living.LivingDamageEvent;\n"
            "import com.stardew.craft.platform.fabric.event.entity.living.LivingDeathEvent;")
        changes["NativeMonsterSettlement.explicitLivingEvents"] = 1
    if relative == "com/stardew/craft/animal/runtime/LegacyLivestockMigration.java":
        barrier = "net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();"
        if source.count(barrier) != 1:
            raise ValueError("Review the changed livestock save barrier")
        source = source.replace(barrier, "// Native Fabric 1.21.1 SavedData.save writes synchronously; there is no Neo IO queue to drain.")
        source = source.replace("net.neoforged.neoforge.common.IOUtilities.writeNbtCompressed(",
            "com.stardew.craft.platform.fabric.data.AtomicNbtFiles.writeNbtCompressed(")
        changes["Livestock.checkedAtomicCheckpoint"] = 1
    if relative in {"com/stardew/craft/entity/seat/" + name + ".java" for name in ("DoubleSwingSeatEntity", "BirdSpringRiderSeatEntity", "SofaSeatEntity", "CushionEntity")}:
        name = relative.rsplit("/", 1)[-1].removesuffix(".java")
        def rider(match):
            declaration = match.group(1).rstrip()
            return declaration + (", " if re.search(r"\bimplements\b", declaration) else " implements ") + "com.stardew.craft.platform.fabric.event.sleep.riding.RiderPoseProvider {"
        source, count = re.subn(r"(\bclass\s+" + name + r"\s+[^{}]+)\{", rider, source)
        if count != 1: raise ValueError("Review changed rider provider: " + relative)
        changes["Entity.riderPoseProvider"] = count
    if relative in {"com/stardew/craft/item/weapon/" + name + ".java" for name in ("StardewWeaponItem", "StardewClubItem", "StardewDaggerItem")}:
        name = relative.rsplit("/", 1)[-1].removesuffix(".java")
        def durable(match):
            declaration = match.group(1).rstrip()
            return declaration + (", " if re.search(r"\bimplements\b", declaration) else " implements ") + "com.stardew.craft.platform.fabric.extension.ItemDurabilityProvider {"
        source, count = re.subn(r"(\bclass\s+" + name + r"\s+[^{}]+)\{", durable, source)
        if count != 1: raise ValueError("Review changed durability provider: " + relative)
        changes["ItemStack.durabilityProvider"] = count
    if relative == "com/stardew/craft/item/ExternalStardewFoodService.java":
        source, count = re.subn(r"stack\.getFoodProperties\(player\)", "stack.get(net.minecraft.core.component.DataComponents.FOOD)", source)
        changes["ExternalFood.nativeFoodComponent"] = count
    if relative in ("com/stardew/craft/client/mining/ClientMineFog.java", "com/stardew/craft/client/slingshot/SlingshotRenderer.java"):
        names = ("com.stardew.craft.platform.fabric.client.event.ClientPlayerNetworkEvent", "com.stardew.craft.platform.fabric.client.event.ViewportEvent") if relative.endswith("ClientMineFog.java") else ("com.stardew.craft.platform.fabric.client.event.RenderHandEvent", "com.stardew.craft.platform.fabric.client.event.RegisterItemDecorationsEvent", "com.stardew.craft.platform.fabric.client.model.ModelEvent", "com.stardew.craft.platform.fabric.client.event.EntityRenderersEvent")
        source = source.replace("import net.neoforged.neoforge.client.event.*;", "\n".join("import " + name + ";" for name in names))
        changes["Client.explicitWildcardEvents"] = len(names)
    if relative in ("com/stardew/craft/entity/monster/GreenSlimeEntity.java", "com/stardew/craft/entity/monster/MineBigSlimeEntity.java"):
        source, count = re.subn(r"(?<![\w$.])isAddedToLevel\(\)", "com.stardew.craft.platform.fabric.entity.EntityPresence.isAdded(this)", source)
        if count != 1: raise ValueError("Review changed entity-presence query: " + relative)
        changes["Entity.addedToLevelState"] = count
    if relative == "com/stardew/craft/compat/AppleSkinCompat.java":
        source, count = re.subn(r"event\.setCanceled\(true\)", "com.stardew.craft.platform.fabric.client.compat.FabricAppleSkinEvents.setCanceled(event, true)", source)
        if count != 1: raise ValueError("Review changed AppleSkin cancel contract")
        changes["AppleSkin.FabricCancellation"] = count
    if relative == "com/stardew/craft/entity/projectile/SlingshotProjectile.java":
        source, count = re.subn(r"net\.neoforged\.neoforge\.event\.EventHooks\.onProjectileImpact\(", "com.stardew.craft.platform.fabric.event.entity.ProjectileImpacts.onProjectileImpact(", source)
        if count != 1: raise ValueError("Review changed slingshot impact event")
        changes["Projectile.realImpactEvent"] = count
    if relative in ("com/stardew/craft/item/tool/HoeItem.java", "com/stardew/craft/item/tool/WateringCanItem.java"):
        original = "properties.setNoRepair()" if relative.endswith("/HoeItem.java") else "properties.durability(tier.capacity).setNoRepair()"
        receiver = "properties" if relative.endswith("/HoeItem.java") else "properties.durability(tier.capacity)"
        if source.count(original) != 1: raise ValueError("Review changed repair properties: " + relative)
        source = source.replace(original, "com.stardew.craft.platform.fabric.item.repair.ItemRepairs.setNoRepair(" + receiver + ")")
        changes["Item.realRepairProperties"] = 1
    if relative == "com/stardew/craft/core/ModGameRules.java":
        source, count = re.subn(r"\bGameRules\.IntegerValue\.create\(",
                "net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory.createIntRule(", source)
        source = re.sub(r"\bGameRules\.register\(", "net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry.register(", source)
        source = re.sub(r"\bGameRules\.BooleanValue\.create\(",
                "net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory.createBooleanRule(", source)
        changes["GameRules.nativeFactoryAndRegistry"] = count
    if relative == "com/stardew/craft/compat/VanillaCompatBrewingRecipes.java":
        source, count = re.subn(r"\bItemTags\.create\(", "TagKey.create(net.minecraft.core.registries.Registries.ITEM, ", source)
        if count != 1: raise ValueError("Review changed rabbit-foot item tag")
        changes["Brewing.nativeItemTagKey"] = count
    if relative == "com/stardew/craft/templates/client/TemplateClientEvents.java":
        expression = "Minecraft.getInstance().getItemColors()"
        count = source.count(expression)
        if count != 1: raise ValueError("Review changed template item color lookup")
        source = source.replace(expression, "((com.stardew.craft.platform.mixin.client.event.mixin.MinecraftColorsAccessor) Minecraft.getInstance()).stardewcraft$getItemColors()")
        changes["Template.nativeItemColors"] = count
    if relative == "com/stardew/craft/entity/ModEntities.java":
        source, count = re.subn(r"\.setShouldReceiveVelocityUpdates\(", ".alwaysUpdateVelocity(", source)
        changes["EntityType.FabricVelocityTracking"] = count
    if relative == "com/stardew/craft/npc/runtime/NpcCentralMovementService.java":
        source = source.replace("gate.openSound", "gate.type.fenceGateOpen()")
        source = source.replace("gate.closeSound", "gate.type.fenceGateClose()")
        changes["FenceGate.nativeWoodSounds"] = 2
    if relative == "com/stardew/craft/integration/jei/StardewJeiPlugin.java":
        for method, field in (("getGuiLeft", "leftPos"), ("getGuiTop", "topPos"),
                              ("getXSize", "imageWidth"), ("getYSize", "imageHeight")):
            source = re.sub(r"\." + method + r"\(\)", "." + field, source)
        changes["Jei.nativeContainerScreenBounds"] = 4
    if relative == "com/stardew/craft/communitycenter/client/BundleScreen.java":
        source, count = re.subn(r"\bthis\.getSlotUnderMouse\(\)", "this.hoveredSlot", source)
        changes["BundleScreen.nativeHoveredSlot"] = count
        declaration = "class BundleScreen extends AbstractContainerScreen<BundleMenu> {"
        if source.count(declaration) != 1: raise ValueError("Review changed bundle slot provider")
        source = source.replace(declaration, declaration[:-1] + "implements com.stardew.craft.platform.fabric.client.widget.SlotHighlightProvider {")
        source, count = re.subn(r"\bprotected void renderSlotHighlight\(", "public void stardewcraft$renderSlotHighlight(", source)
        if count != 1: raise ValueError("Review changed bundle highlight method")
        source, count = re.subn(r"\bsuper\.renderSlotHighlight\(", "com.stardew.craft.platform.fabric.client.widget.SlotHighlights.renderDefault(this, ", source)
        if count != 1: raise ValueError("Review changed bundle default highlight")
        changes["BundleScreen.realHighlightProvider"] = 1
    if relative == "com/stardew/craft/mixin/StardewInventorySlotHighlightMixin.java":
        tokens, pairs = java_tokens(source)
        methods = [i for i, token in enumerate(tokens) if token.group() == "stardewcraft$seasonalOutline"]
        if len(methods) != 1: raise ValueError("Review changed seasonal slot callback")
        index = methods[0]
        start = source.rfind("@Inject", 0, tokens[index].start())
        opening = next(i for i in range(index, len(tokens)) if tokens[i].group() == "{")
        end = tokens[pairs[opening]].end()
        if start < 0 or hashlib.sha256(source[start:end].encode()).hexdigest() != "75e3f00ab1be4cd032d21f05fbdb88e5c08bdbf307acbacf334affe0682efae0":
            raise ValueError("Review changed seasonal slot callback before migrating it")
        # The same StardewInventoryGui callback is installed on the native facade.
        source = source[:start] + source[end:]
        changes["SlotHighlight.nativeCallbackMigration"] = 1
    if relative == "com/stardew/craft/client/pet/PetSoundClient.java":
        source, count = re.subn(r"mc\.levelRenderer\.getFrustum\(\)",
                "com.stardew.craft.platform.fabric.client.render.LevelFrustums.getFrustum(mc.levelRenderer)", source)
        if count != 2: raise ValueError("Review changed pet sound frustum queries")
        changes["LevelRenderer.actualFrustum"] = count
    if relative in ("com/stardew/craft/client/weapon/BlackHolePostEffectClient.java",
                    "com/stardew/craft/client/interior/TownDoorComposite.java"):
        original = "new ShaderInstance(provider, shaderId, format)"
        if source.count(original) != 1: raise ValueError("Review changed namespaced shader constructor")
        source = source.replace(original, "com.stardew.craft.platform.fabric.client.shader.FabricShaders.create(provider, shaderId, format)")
        changes["Shader.FabricNamespacedProgram"] = 1
    if relative == "com/stardew/craft/client/interior/TownDoorRenderer.java":
        source, query = re.subn(r"target\.isStencilEnabled\(\)", "com.stardew.craft.platform.fabric.client.framebuffer.RenderTargetStencils.isStencilEnabled(target)", source)
        source, enable = re.subn(r"target\.enableStencil\(\)", "com.stardew.craft.platform.fabric.client.framebuffer.RenderTargetStencils.enableStencil(target)", source)
        if query != 1 or enable != 1: raise ValueError("Review changed portal stencil request")
        changes["RenderTarget.realStencilAttachment"] = 1
    if relative == "com/stardew/craft/client/ModRenderLayers.java":
        source, count = re.subn(r"\bItemBlockRenderTypes\.setRenderLayer\(",
                "net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap.INSTANCE.putBlock(", source)
        changes["ModRenderLayers.FabricBlockLayers"] = count
    if relative in ("com/stardew/craft/item/FishNetItem.java", "com/stardew/craft/item/artisan/SmokedFishItem.java",
                    "com/stardew/craft/block/utility/WizardBuildingBlock.java", "com/stardew/craft/block/decor/GeoFestivalDecorBlock.java"):
        source, count = re.subn(r"@Override\s+(?=public\s+void\s+initializeClient\b)", "", source)
        if count != 1:
            raise ValueError("Review the changed legacy client initializer: " + relative)
        changes["ClientExtensions.explicitLegacyInitialization"] = count
    if relative == "com/stardew/craft/weather/ModParticles.java":
        source, count = re.subn(r"\bnew\s+SimpleParticleType\(",
                "net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(", source)
        changes["SimpleParticleType.FabricParticleFactory"] = count
    if relative == "com/stardew/craft/client/model/terrain/ConnectedTopQuads.java":
        source, count = re.subn(r"\(sprite\.getPixelRGBA\(0,\s*px,\s*pz\)\s*>>>\s*24\)\s*!=\s*0",
                "!sprite.contents().isTransparent(0, px, pz)", source)
        if count != 1:
            raise ValueError("Review the changed ConnectedTopQuads sprite opacity")
        changes["TextureAtlasSprite.nativeFrameOpacity"] = count
    if relative.startswith("com/stardew/craft/client/model/"):
        source, count = re.subn(r"\bModelResourceLocation\.standalone\(",
                "com.stardew.craft.platform.fabric.client.model.BakedModels.standalone(", source)
        if count:
            changes["ModelResourceLocation.explicitStandaloneVariant"] = count
    if relative == "com/stardew/craft/floor/SurfaceFloorItem.java":
        source, count = re.subn(r"(\bclass\s+SurfaceFloorItem\s+extends\s+Item\s+implements\s+[^{}]+?)(\s*\{)",
                lambda match: match.group(1).rstrip()
                + ", com.stardew.craft.platform.fabric.event.entity.player.ItemUseFirstProvider" + match.group(2), source)
        if count != 1:
            raise ValueError("Review the changed SurfaceFloorItem early-use provider")
        changes["SurfaceFloorItem.earlyBlockInteraction"] = count
    if relative in ("com/stardew/craft/StardewCraft.java", "com/stardew/craft/templates/TemplateContent.java"):
        source, count = re.subn(r"\bCreativeModeTab\.builder\(\)",
                "net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup.builder()", source)
        changes["CreativeModeTab.FabricItemGroupBuilder"] = count
    if relative == "com/stardew/craft/mail/MailService.java":
        source, count = re.subn(r"\bplayer\.isFakePlayer\(\)",
                "(player instanceof net.fabricmc.fabric.api.entity.FakePlayer)", source)
        changes["MailService.nativeFakePlayerDetection"] = count
    if relative == "com/stardew/craft/item/weapon/SlingshotItem.java":
        source, count = re.subn(r"(\bclass\s+SlingshotItem\s+extends\s+Item\s+implements\s+[^{}]+?)(\s*\{)",
                lambda match: match.group(1).rstrip() + ", com.stardew.craft.platform.fabric.extension.ItemAttackProvider"
                + match.group(2), source)
        if count != 1:
            raise ValueError("Review the changed SlingshotItem melee gate")
        changes["SlingshotItem.consumeMeleeAttempt"] = count
    if relative in ("com/stardew/craft/mining/GoldenWalnutPickup.java", "com/stardew/craft/player/PlayerMagnetHandler.java"):
        source, count = re.subn(r"\b(entity|itemEntity|item)\.getTarget\(\)",
                r"com.stardew.craft.platform.fabric.event.entity.player.ItemPickupHooks.target(\1)", source)
        changes["ItemEntity.nativePickupTarget"] = count
    if relative == "com/stardew/craft/player/PlayerMagnetHandler.java":
        # The project's fluids use the native water/lava tag physics, including pond water.
        source, count = re.subn(r"\bitem\.getMaxHeightFluidType\(\)\.isAir\(\)",
                "(!item.isInWater() && !item.isInLava())", source)
        changes["PlayerMagnet.nativeFluidImmersion"] = count
    if relative.startswith("com/stardew/craft/gametest/") and ".getCapability(" in source:
        # Existing tests exercise exactly the same registered providers as FAPI.
        source, count = re.subn(r"\blevel\.getCapability\(([\w.]+),\s*",
                r"com.stardew.craft.platform.fabric.capability.BlockCapabilities.get(\1, level, ", source)
        changes["GameTest.FabricBlockCapabilities"] = count
    if relative in {"com/stardew/craft/gametest/" + name + ".java" for name in (
            "DedicatedLoginGameTests", "PetAddonApiGameTests", "PetInitialAdoptionGameTests",
            "PetInteractionGameTests", "PetRefinementGameTests")}:
        source = re.sub(r"(?m)^import net\.neoforged\.neoforge\.network\.connection\.ConnectionType;\s*\n", "", source)
        # Native/FAPI payload codecs use the registry buffer directly; the
        # NeoForge-only third constructor argument selects no Fabric protocol.
        source, count = re.subn(r",\s*(?:net\.neoforged\.neoforge\.network\.connection\.)?ConnectionType\.NEOFORGE\b", "", source)
        changes["GameTest.nativeRegistryCodecBuffers"] = count
    if relative == "com/stardew/craft/blockentity/StorageChestBlockEntity.java":
        source, count = re.subn(r"(?m)^\s*level\.invalidateCapabilities\(worldPosition\);", "", source)
        if count != 1:
            raise ValueError("Review the changed StorageChest capability invalidation")
        # FAPI bridge views resolve their provider on every operation, including
        # a Junimo ownership change, so the old cached-capability invalidator is unnecessary.
        changes["StorageChest.liveCapabilityViews"] = count
    if relative == "com/stardew/craft/templates/TemplateBlockEntity.java":
        source = source.replace("extends com.stardew.craft.platform.fabric.blockentity.FabricBlockEntity {",
                "extends com.stardew.craft.platform.fabric.blockentity.FabricBlockEntity implements "
                "com.stardew.craft.platform.fabric.modeldata.ModelDataProvider {")
        source, count = re.subn(r"\brequestModelDataUpdate\(\)",
                "com.stardew.craft.platform.fabric.modeldata.ModelDataUpdates.request(this)", source)
        source = source.replace("    @Override\n    public ModelData getModelData() {",
                "    @Override\n    public Object getRenderData() { return getModelData(); }\n\n"
                "    @Override\n    public ModelData getModelData() {")
        changes["TemplateBlockEntity.FabricRenderDataSnapshot"] = count
    if relative == "com/stardew/craft/client/hud/StardewPlayerHud.java":
        for side in ("left", "right"):
            source, count = re.subn(r"\bmc\.gui\." + side + r"Height\s*\+=\s*10\s*;",
                    "com.stardew.craft.platform.fabric.client.render.GuiHudLayout.add" + side.title() + "(mc.gui, 10);", source)
            if count != 1:
                raise ValueError("Review the changed StardewPlayerHud " + side + " height")
            changes["GuiHudLayout." + side] = count
    return source, changes


def prepare(root: Path, output: Path, report: Path) -> dict:
    root = root.resolve()
    source_root = root / "src/main/java"
    output = output.resolve()
    report = report.resolve()
    # Never allow the generated tree to overwrite maintained sources or another
    # arbitrary directory. A marker also makes ownership explicit after reload.
    build_root = root / "fabric/build"
    if not output.is_relative_to(build_root.resolve()):
        raise ValueError("--output must be inside this repository's fabric/build")
    if not report.is_relative_to(build_root.resolve()):
        raise ValueError("--report must be inside this repository's fabric/build")
    output.mkdir(parents=True, exist_ok=True)
    marker = output / ".stardewcraft-fabric-generated"
    if any(output.iterdir()) and not marker.exists():
        raise ValueError("Refusing to replace an unowned generated source directory")
    marker.write_text("prepare_fabric_sources.py\n", encoding="utf-8")
    paths = sorted(source_root.rglob("*.java"))
    if not paths:
        raise ValueError("The upstream source tree is empty")
    manifest = []
    remaining = Counter()
    conversions = Counter()
    expected = set()
    override_root = root / "fabric/src/overrides/java"
    overrides = {path.relative_to(override_root): path for path in override_root.rglob("*.java")}
    upstream_paths = {path.relative_to(source_root) for path in paths}
    unknown = sorted(str(path) for path in overrides.keys() - upstream_paths)
    if unknown:
        raise ValueError("Overrides must replace existing upstream paths: " + ", ".join(unknown))
    for path in paths:
        relative = path.relative_to(source_root)
        raw = path.read_bytes()
        override = overrides.get(relative)
        implementation = override.read_bytes() if override else raw
        if override:
            declared = re.search(rb"(?m)^// Upstream-SHA256: ([0-9a-f]{64})$", implementation)
            if declared is None:
                raise ValueError("Override is missing its Upstream-SHA256 baseline: " + relative.as_posix())
            if declared.group(1).decode("ascii") != hashlib.sha256(raw).hexdigest():
                raise ValueError("Upstream changed beneath a maintained override; review it first: "
                                 + relative.as_posix())
        source, changes = convert(implementation.decode("utf-8"), relative.as_posix())
        destination = output / relative
        expected.add(destination)
        destination.parent.mkdir(parents=True, exist_ok=True)
        encoded = source.encode("utf-8")
        if not destination.exists() or destination.read_bytes() != encoded:
            destination.write_bytes(encoded)
        refs = sorted(set(NEOFORGE_REFERENCE.findall(source)))
        remaining.update(refs)
        conversions.update(changes)
        manifest.append({
            "path": relative.as_posix(),
            "sha256": hashlib.sha256(raw).hexdigest(),
            "output_sha256": hashlib.sha256(encoded).hexdigest(),
            "override": override.relative_to(root).as_posix() if override else None,
            "override_sha256": hashlib.sha256(implementation).hexdigest() if override else None,
            "conversions": changes,
            "remaining_neoforge_references": refs,
        })
    # Fail preparation if an editor changed the source during generation. A
    # coherent input set is required even when the main workspace is dirty.
    after_paths = sorted(source_root.rglob("*.java"))
    if after_paths != paths or any(
            hashlib.sha256(path.read_bytes()).hexdigest() != entry["sha256"]
            for path, entry in zip(paths, manifest)):
        raise ValueError("Upstream Java changed during preparation; rerun against the current source")
    after_overrides = {path.relative_to(override_root): path for path in override_root.rglob("*.java")}
    if after_overrides.keys() != overrides.keys() or any(
            hashlib.sha256((root / entry["override"]).read_bytes()).hexdigest() != entry["override_sha256"]
            for entry in manifest if entry["override"]):
        raise ValueError("Fabric overrides changed during preparation; rerun against the current source")
    # Remove only Java outputs owned by this generator when upstream removes a
    # class; handwritten adapters live outside this generated directory.
    for stale in output.rglob("*.java"):
        if stale not in expected:
            stale.unlink()
    fingerprint = hashlib.sha256(json.dumps(
        [(entry["path"], entry["sha256"]) for entry in manifest],
        separators=(",", ":"), ensure_ascii=False,
    ).encode("utf-8")).hexdigest()
    result = {
        "schema_version": 1,
        "target": "fabric-1.21.1",
        "source_files": len(paths),
        "source_fingerprint": fingerprint,
        "converted_references": dict(sorted(conversions.items())),
        "remaining_neoforge_references": dict(sorted(remaining.items())),
        "files": manifest,
    }
    report.parent.mkdir(parents=True, exist_ok=True)
    report.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = prepare(args.root, args.output, args.report)
    except ValueError as error:
        parser.error(str(error))
    retained = set(result["remaining_neoforge_references"])
    config_api_port = {"net.neoforged.fml.config.ModConfig", "net.neoforged.neoforge.common.ModConfigSpec"}
    print(f"Prepared all {result['source_files']} upstream Java files; "
          f"{len(retained - config_api_port)} unadapted NeoForge API names; "
          f"{len(retained & config_api_port)} configuration API names supplied by Forge Config API Port.")


if __name__ == "__main__":
    main()
