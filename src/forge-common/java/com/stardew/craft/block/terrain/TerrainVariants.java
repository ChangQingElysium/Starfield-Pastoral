package com.stardew.craft.block.terrain;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Forge 1.20.1 adapter for the terrain visual-state contract.
 *
 * <p>The 1.21 line stores this state in {@code DataComponents.BLOCK_STATE}.
 * 1.20.1 has no data components, so the vanilla {@code BlockStateTag} is used
 * instead.  This is deliberately the vanilla key: {@link BlockItem} applies
 * it after placement, and picked-up/preconfigured block items therefore keep
 * the same round-trip semantics.</p>
 *
 * <p>The Forge terrain slices currently own the playground-sand and asphalt
 * paths. Other properties are added with their owning block families, rather
 * than being represented by a partial or inert compatibility class.</p>
 */
public final class TerrainVariants {
    public static final IntegerProperty SAND = IntegerProperty.create("variant", 0, 3);
    public static final IntegerProperty ASPHALT = IntegerProperty.create("variant", 0, 2);

    private TerrainVariants() {
    }

    @Nullable
    public static IntegerProperty property(BlockState state) {
        if (state.getBlock() instanceof PlaygroundSandBlock) {
            return SAND;
        }
        return state.getBlock() instanceof AsphaltRoadBlock ? ASPHALT : null;
    }

    public static BlockState placement(BlockState state, BlockPlaceContext context) {
        IntegerProperty property = property(state);
        if (property == null) {
            return state;
        }

        Integer fixed = fixedValue(context.getItemInHand(), property);
        if (fixed != null) {
            return state.setValue(property, fixed);
        }

        // Match the 1.21 contract: the client predicts the default state and
        // the server makes the one authoritative random choice.
        if (context.getLevel().isClientSide) {
            return state;
        }
        if (property == SAND) {
            return state.setValue(property, context.getLevel().getRandom().nextInt(4));
        }
        if (property == ASPHALT) {
            return state.setValue(property, context.getLevel().getRandom().nextInt(3));
        }
        return state;
    }

    @Nullable
    private static Integer fixedValue(ItemStack stack, IntegerProperty property) {
        CompoundTag tag = stack.getTagElement(BlockItem.BLOCK_STATE_TAG);
        if (tag == null) {
            return null;
        }
        Tag raw = tag.get(property.getName());
        if (raw == null) {
            return null;
        }
        // Vanilla BlockItem parses every state tag through Tag#getAsString,
        // including integer tags.  Keep that behavior instead of assuming a
        // particular NBT primitive type.
        return property.getValue(raw.getAsString()).orElse(null);
    }

    /** Copies the selected terrain variant into a vanilla BlockStateTag. */
    public static ItemStack fixedCopy(ItemStack original, BlockState state) {
        IntegerProperty property = property(state);
        if (property == null || original.isEmpty()) {
            return original;
        }
        ItemStack copy = original.copy();
        CompoundTag tag = copy.getOrCreateTagElement(BlockItem.BLOCK_STATE_TAG);
        tag.putString(property.getName(), property.getName(state.getValue(property)));
        return copy;
    }
}
