package com.stardew.craft.gingerisland;

import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.GameRules;
import java.util.Optional;
import java.util.UUID;

/** Ownership is part of the stack, so vanilla item merging cannot mix two islands' rewards. */
public final class WalnutDebris {
    public static final String FARM_ID = "stardewcraft_ginger_farm_instance";
    private WalnutDebris() {}

    public static Optional<UUID> owner(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return data.hasUUID(FARM_ID) ? Optional.of(data.getUUID(FARM_ID)) : Optional.empty();
    }

    public static void drop(ServerLevel level, BlockPos pos, int count) {
        if (count < 1 || !level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) return;
        ItemStack stack = new ItemStack(ModItems.GOLDEN_WALNUT.get(), count);
        IslandContext.farmInstance(level, pos).ifPresent(id -> CustomData.update(DataComponents.CUSTOM_DATA, stack,
                data -> data.putUUID(FARM_ID, id)));
        ItemEntity entity = new ItemEntity(level, pos.getX() + level.random.nextFloat() * .5 + .25,
                pos.getY() + level.random.nextFloat() * .5 + .25,
                pos.getZ() + level.random.nextFloat() * .5 + .25, stack);
        entity.setDefaultPickUpDelay();
        level.addFreshEntity(entity);
    }
}
