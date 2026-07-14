package com.stardew.craft.api.v1.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Ordered extension point that runs before StardewCraft's built-in NPC interaction pipeline. */
public final class StardewNpcInteractions {
    private static volatile List<Entry> providers = List.of();

    private StardewNpcInteractions() {
    }

    public static synchronized void register(ResourceLocation id, int priority,
                                             StardewNpcInteractionProvider provider) {
        if (id == null || provider == null) {
            throw new IllegalArgumentException("NPC interaction provider id and provider must not be null");
        }
        if (providers.stream().anyMatch(entry -> entry.id().equals(id))) {
            throw new IllegalStateException("Duplicate NPC interaction provider: " + id);
        }
        ArrayList<Entry> next = new ArrayList<>(providers);
        next.add(new Entry(id, priority, provider));
        next.sort(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(entry -> entry.id().toString()));
        providers = List.copyOf(next);
    }

    public static InteractionResult dispatch(ServerPlayer player, StardewNpcEntity npc,
                                             InteractionHand hand) {
        ResourceLocation npcId = normalizeNpcId(npc == null ? null : npc.getNpcId());
        if (player == null || npc == null || hand == null || npcId == null) {
            return InteractionResult.PASS;
        }
        StardewNpcInteractionContext context = new StardewNpcInteractionContext(player, npc, npcId, hand);
        for (Entry entry : providers) {
            try {
                InteractionResult result = entry.provider().interact(context);
                if (result != null && result != InteractionResult.PASS) {
                    return result;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error("[NPC interaction] Provider {} failed for {}",
                        entry.id(), npcId, exception);
            }
        }
        return InteractionResult.PASS;
    }

    public static ResourceLocation normalizeNpcId(String rawId) {
        if (rawId == null || rawId.isBlank()) return null;
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(StardewCraft.MODID, normalized);
    }

    private record Entry(ResourceLocation id, int priority, StardewNpcInteractionProvider provider) {
    }
}
