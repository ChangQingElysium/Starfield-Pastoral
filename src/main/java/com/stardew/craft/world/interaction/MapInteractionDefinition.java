package com.stardew.craft.world.interaction;

import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.mapinteraction.StardewMapInteractionAction;
import com.stardew.craft.api.v1.world.StardewLocations;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Set;

/** Immutable, server-only definition loaded from map_interactions data. */
public record MapInteractionDefinition(
        ResourceLocation id,
        int priority,
        ResourceLocation dimension,
        ResourceLocation location,
        List<Box> boxes,
        Set<ResourceLocation> blocks,
        Set<ResourceLocation> blockTags,
        List<Branch> branches,
        Hint hint,
        Source source
) {
    public MapInteractionDefinition {
        boxes = List.copyOf(boxes);
        blocks = Set.copyOf(blocks);
        blockTags = Set.copyOf(blockTags);
        branches = List.copyOf(branches);
        hint = hint == null ? Hint.AUTO : hint;
    }

    public boolean matches(ServerLevel level, BlockPos pos) {
        if (dimension != null
                && !dimension.equals(level.dimension().location())) {
            return false;
        }
        if (location != null) {
            ResourceLocation actual = StardewLocations.find(level, pos)
                    .map(value -> value.id())
                    .orElse(null);
            if (actual == null
                    || !StardewLocations.isWithin(actual, location)) {
                return false;
            }
        }
        if (boxes.stream().noneMatch(box -> box.contains(pos))) {
            return false;
        }
        if (blocks.isEmpty() && blockTags.isEmpty()) {
            return true;
        }
        var state = level.getBlockState(pos);
        ResourceLocation blockId =
                BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (blocks.contains(blockId)) {
            return true;
        }
        return blockTags.stream().anyMatch(tag ->
                state.is(TagKey.create(
                        net.minecraft.core.registries.Registries.BLOCK,
                        tag)));
    }

    public record Box(BlockPos min, BlockPos max) {
        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX()
                    && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY()
                    && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ()
                    && pos.getZ() <= max.getZ();
        }
    }

    public record Branch(
            String id,
            List<StardewCondition> conditions,
            List<StardewAction> effects,
            List<Message> messages,
            StardewMapInteractionAction action
    ) {
        public Branch {
            conditions = List.copyOf(conditions);
            effects = List.copyOf(effects);
            messages = List.copyOf(messages);
        }
    }

    public record Message(
            String translationKey,
            String fallback,
            String literal
    ) {
        public Component component() {
            if (literal != null) {
                return Component.literal(literal);
            }
            return fallback == null || fallback.isEmpty()
                    ? Component.translatable(translationKey)
                    : Component.translatableWithFallback(
                            translationKey, fallback);
        }
    }

    public boolean showsReadHint(Branch branch) {
        return switch (hint) {
            case READ -> true;
            case NONE -> false;
            case AUTO -> branch != null
                    && !branch.messages().isEmpty();
        };
    }

    public enum Hint {
        AUTO,
        READ,
        NONE
    }

    public record Source(
            String vanillaVersion,
            String map,
            String tileAction,
            String code
    ) {
        public static Source empty() {
            return new Source("", "", "", "");
        }
    }
}
