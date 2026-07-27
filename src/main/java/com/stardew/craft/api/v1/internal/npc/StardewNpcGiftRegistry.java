package com.stardew.craft.api.v1.internal.npc;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.npc.StardewNpcGifts;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Internal gift policy and hook dispatch. */
public final class StardewNpcGiftRegistry {
    private static final Map<ResourceLocation, ConfirmationEntry> CONFIRMATION =
            new HashMap<>();
    private static final Map<ResourceLocation, BeforeEntry> BEFORE = new HashMap<>();
    private static final Map<ResourceLocation, AfterEntry> AFTER = new HashMap<>();
    private static volatile List<ConfirmationEntry> confirmationSnapshot = List.of();
    private static volatile List<BeforeEntry> beforeSnapshot = List.of();
    private static volatile List<AfterEntry> afterSnapshot = List.of();

    private StardewNpcGiftRegistry() {
    }

    public static synchronized void registerConfirmationPolicy(
            ResourceLocation id,
            int priority,
            StardewNpcGifts.ConfirmationPolicy policy
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(policy, "policy");
        if (CONFIRMATION.containsKey(id)) {
            throw new IllegalStateException(
                    "NPC gift confirmation policy already registered: " + id);
        }
        CONFIRMATION.put(id, new ConfirmationEntry(id, priority, policy));
        ArrayList<ConfirmationEntry> ordered =
                new ArrayList<>(CONFIRMATION.values());
        ordered.sort(Comparator.comparingInt(ConfirmationEntry::priority).reversed()
                .thenComparing(value -> value.id().toString()));
        confirmationSnapshot = List.copyOf(ordered);
    }

    public static synchronized void registerBeforeHook(
            ResourceLocation id,
            int priority,
            StardewNpcGifts.BeforeHook hook
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(hook, "hook");
        if (BEFORE.containsKey(id)) {
            throw new IllegalStateException(
                    "NPC gift before hook already registered: " + id);
        }
        BEFORE.put(id, new BeforeEntry(id, priority, hook));
        ArrayList<BeforeEntry> ordered = new ArrayList<>(BEFORE.values());
        ordered.sort(Comparator.comparingInt(BeforeEntry::priority).reversed()
                .thenComparing(value -> value.id().toString()));
        beforeSnapshot = List.copyOf(ordered);
    }

    public static synchronized void registerAfterHook(
            ResourceLocation id,
            int priority,
            StardewNpcGifts.AfterHook hook
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(hook, "hook");
        if (AFTER.containsKey(id)) {
            throw new IllegalStateException(
                    "NPC gift after hook already registered: " + id);
        }
        AFTER.put(id, new AfterEntry(id, priority, hook));
        ArrayList<AfterEntry> ordered = new ArrayList<>(AFTER.values());
        ordered.sort(Comparator.comparingInt(AfterEntry::priority).reversed()
                .thenComparing(value -> value.id().toString()));
        afterSnapshot = List.copyOf(ordered);
    }

    public static StardewNpcGifts.Confirmation confirmation(
            StardewNpcGifts.OfferContext context
    ) {
        for (ConfirmationEntry registered : confirmationSnapshot) {
            try {
                StardewNpcGifts.Confirmation result =
                        registered.policy().decide(context);
                if (result != null && result != StardewNpcGifts.Confirmation.PASS) {
                    return result;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "NPC gift confirmation policy {} failed for {}",
                        registered.id(), context.npcId(), exception);
            }
        }
        return StardewNpcGifts.Confirmation.REQUIRE_CONFIRMATION;
    }

    public static boolean before(StardewNpcGifts.BeforeContext context) {
        for (BeforeEntry registered : beforeSnapshot) {
            try {
                if (registered.hook().before(context)
                        == StardewNpcGifts.BeforeDecision.DENY) {
                    return false;
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "NPC gift before hook {} failed for {}",
                        registered.id(), context.npcId(), exception);
            }
        }
        return true;
    }

    public static void after(StardewNpcGifts.Result result) {
        for (AfterEntry registered : afterSnapshot) {
            try {
                registered.hook().after(result);
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "NPC gift after hook {} failed for {}",
                        registered.id(), result.npcId(), exception);
            }
        }
    }

    private record ConfirmationEntry(
            ResourceLocation id,
            int priority,
            StardewNpcGifts.ConfirmationPolicy policy
    ) {
    }

    private record BeforeEntry(
            ResourceLocation id,
            int priority,
            StardewNpcGifts.BeforeHook hook
    ) {
    }

    private record AfterEntry(
            ResourceLocation id,
            int priority,
            StardewNpcGifts.AfterHook hook
    ) {
    }
}
