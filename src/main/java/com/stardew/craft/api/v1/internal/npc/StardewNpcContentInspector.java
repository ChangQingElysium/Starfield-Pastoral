package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.npc.StardewNpcContentSnapshot;
import com.stardew.craft.api.v1.npc.StardewNpcEntities;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.api.v1.npc.StardewNpcRuntimeSnapshot;
import com.stardew.craft.api.v1.shop.StardewShopBinding;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.shop.ShopDataLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Internal projection joining NPC and shop reload snapshots by canonical ID. */
public final class StardewNpcContentInspector {
    private StardewNpcContentInspector() {
    }

    public static StardewNpcContentSnapshot inspect(ResourceLocation npcId) {
        Objects.requireNonNull(npcId, "npcId");
        String key = storageKey(npcId);
        boolean hasProfile = StardewNpcProfileRegistry.resolve(npcId) != null;
        boolean hasDialogue = NpcDataRegistry.dialogues().containsKey(key);
        boolean hasSchedule = NpcDataRegistry.schedules().containsKey(key);
        boolean hasGiftTastes = NpcDataRegistry.tastes().containsKey(key);
        ArrayList<ResourceLocation> bindingIds = new ArrayList<>();
        LinkedHashSet<ResourceLocation> shopIds = new LinkedHashSet<>();
        ArrayList<String> issues = new ArrayList<>();

        for (var entry : ShopDataLoader.bindingSnapshot()
                .definitions().entrySet()) {
            StardewShopBinding binding = entry.getValue();
            ResourceLocation boundNpc = binding.npc()
                    .map(StardewNpcInteractions::normalizeNpcId)
                    .orElse(null);
            if (!npcId.equals(boundNpc)) {
                continue;
            }
            bindingIds.add(entry.getKey());
            ResourceLocation shopId = canonicalShopId(binding.shop());
            if (shopId == null) {
                issues.add("missing_shop:" + binding.shop());
            } else {
                shopIds.add(shopId);
            }
        }
        if (!hasProfile && (hasDialogue || hasSchedule || hasGiftTastes
                || !bindingIds.isEmpty())) {
            issues.add("missing_profile");
        }
        return new StardewNpcContentSnapshot(
                npcId,
                hasProfile,
                hasDialogue,
                hasSchedule,
                hasGiftTastes,
                bindingIds,
                List.copyOf(shopIds),
                issues);
    }

    public static StardewNpcRuntimeSnapshot inspect(
            ServerLevel level,
            ResourceLocation npcId
    ) {
        Objects.requireNonNull(level, "level");
        var entity = StardewNpcEntities.resolve(level, npcId);
        return new StardewNpcRuntimeSnapshot(
                inspect(npcId),
                level.dimension().location(),
                entity.map(value -> value.getUUID()));
    }

    public static List<ResourceLocation> ids() {
        TreeSet<ResourceLocation> ids = new TreeSet<>(
                StardewNpcProfileRegistry.ids());
        addDataIds(ids, NpcDataRegistry.dialogues().keySet());
        addDataIds(ids, NpcDataRegistry.schedules().keySet());
        addDataIds(ids, NpcDataRegistry.tastes().keySet());
        for (StardewShopBinding binding : ShopDataLoader.bindingSnapshot()
                .definitions().values()) {
            binding.npc()
                    .map(StardewNpcInteractions::normalizeNpcId)
                    .ifPresent(ids::add);
        }
        return List.copyOf(ids);
    }

    private static void addDataIds(
            TreeSet<ResourceLocation> output,
            Iterable<String> rawIds
    ) {
        for (String rawId : rawIds) {
            ResourceLocation id = StardewNpcInteractions.normalizeNpcId(rawId);
            if (id != null && !"universal".equals(id.getPath())) {
                output.add(id);
            }
        }
    }

    private static String storageKey(ResourceLocation npcId) {
        return StardewCraft.MODID.equals(npcId.getNamespace())
                ? npcId.getPath()
                : npcId.toString();
    }

    private static ResourceLocation canonicalShopId(String rawShopId) {
        ResourceLocation direct = ResourceLocation.tryParse(rawShopId);
        if (direct != null
                && ShopDataLoader.getDefinition(direct) != null) {
            return direct;
        }
        return ShopDataLoader.snapshot().definitions().entrySet().stream()
                .filter(entry -> entry.getValue().legacyId()
                        .equals(rawShopId))
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }
}
