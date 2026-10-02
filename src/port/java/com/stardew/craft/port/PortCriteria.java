package com.stardew.craft.port;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * PORT(1.20.1): 1.20.5+ advancement criteria the mod's block interactions fire.
 *
 * <p>1.21 {@code ServerPlayerGameMode#useItemOn} fires {@code minecraft:default_block_use} (no tool) when a block's
 * {@code useWithoutItem} consumes the click and {@code minecraft:item_used_on_block} only when {@code useItemOn} or
 * {@code Item#useOn} does. 1.20.1 has no such trigger and fires {@code item_used_on_block} for every consuming
 * {@code Block#use}. {@link #DEFAULT_BLOCK_USE} is the 1.21.1 {@code DefaultBlockInteractionTrigger}; mod blocks
 * reach it through {@link PortBlockInteraction#dispatch} and {@code PortServerPlayerGameModeDefaultUseMixin}. Vanilla
 * 1.20.1 blocks keep their 1.20.1 {@code use} and trigger.
 */
public final class PortCriteria {
    /** 1.21 {@code LootContextParamSets.BLOCK_USE}: this entity, origin and block state (no tool). */
    public static final LootContextParamSet BLOCK_USE = LootContextParamSet.builder()
            .required(LootContextParams.THIS_ENTITY)
            .required(LootContextParams.ORIGIN)
            .required(LootContextParams.BLOCK_STATE)
            .build();

    public static final DefaultBlockInteractionTrigger DEFAULT_BLOCK_USE = new DefaultBlockInteractionTrigger();

    private static boolean registered;

    private PortCriteria() {}

    /** Registers the triggers into the vanilla trigger map (main thread, before any advancement is loaded). */
    public static synchronized void register() {
        if (registered) return;
        registered = true;
        CriteriaTriggers.register(DEFAULT_BLOCK_USE);
    }

    /** 1.21.1 {@code net.minecraft.advancements.critereon.DefaultBlockInteractionTrigger}. */
    public static final class DefaultBlockInteractionTrigger
            extends SimpleCriterionTrigger<DefaultBlockInteractionTrigger.TriggerInstance> {
        static final ResourceLocation ID = new ResourceLocation("minecraft", "default_block_use");

        @Override
        public ResourceLocation getId() {
            return ID;
        }

        @Override
        protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                DeserializationContext context) {
            // 1.21: optionalFieldOf("location", ContextAwarePredicate.CODEC) — absent matches everything, present must
            // be a condition list validated against BLOCK_USE.
            JsonElement element = json.get("location");
            ContextAwarePredicate location = null;
            if (element != null) {
                location = ContextAwarePredicate.fromElement("location", context, element, BLOCK_USE);
                if (location == null) throw new JsonParseException("Failed to parse 'location' field");
            }
            return new TriggerInstance(player, location);
        }

        public void trigger(ServerPlayer player, BlockPos pos) {
            ServerLevel level = player.serverLevel();
            BlockState state = level.getBlockState(pos);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.ORIGIN, pos.getCenter())
                    .withParameter(LootContextParams.THIS_ENTITY, player)
                    .withParameter(LootContextParams.BLOCK_STATE, state)
                    .create(BLOCK_USE);
            LootContext context = new LootContext.Builder(params).create(null);
            this.trigger(player, instance -> instance.matches(context));
        }

        public static final class TriggerInstance extends AbstractCriterionTriggerInstance {
            private final ContextAwarePredicate location;

            TriggerInstance(ContextAwarePredicate player, ContextAwarePredicate location) {
                super(ID, player);
                this.location = location;
            }

            public boolean matches(LootContext context) {
                return this.location == null || this.location.matches(context);
            }

            @Override
            public JsonObject serializeToJson(SerializationContext context) {
                JsonObject json = super.serializeToJson(context);
                if (this.location != null) json.add("location", this.location.toJson(context));
                return json;
            }
        }
    }
}
