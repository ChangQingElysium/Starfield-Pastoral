package com.stardew.craft.block.cooking;

import com.stardew.craft.blockentity.CookingPlacedFoodBlockEntity;
import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** A placed bottle retains its ingredient, quality and all other item components. */
public final class PlacedArtisanDrinkBlock extends CookingPlacedFoodBlock {
    private final java.util.Map<net.minecraft.core.Direction, VoxelShape> bottleShapes =
            new java.util.EnumMap<>(net.minecraft.core.Direction.class);

    public PlacedArtisanDrinkBlock(String itemId, VoxelShape bottleShape, Properties properties) {
        super(itemId, properties);
        bottleShapes.put(net.minecraft.core.Direction.NORTH, bottleShape);
        VoxelShape rotated = bottleShape;
        for (var direction : new net.minecraft.core.Direction[] {
                net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST}) {
            VoxelShape[] next = {net.minecraft.world.phys.shapes.Shapes.empty()};
            rotated.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) ->
                    next[0] = net.minecraft.world.phys.shapes.Shapes.or(next[0],
                            net.minecraft.world.phys.shapes.Shapes.box(1 - maxZ, minY, minX, 1 - minZ, maxY, maxX)));
            rotated = next[0];
            bottleShapes.put(direction, rotated);
        }
    }

    @Override
    public String getDescriptionId() {
        return "item.stardewcraft." + getItemId();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return bottleShapes.get(state.getValue(FACING));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (level.getBlockEntity(pos) instanceof CookingPlacedFoodBlockEntity food) {
            food.setStoredFood(stack);
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof CookingPlacedFoodBlockEntity food
                && !food.getStoredFood().isEmpty()) {
            return List.of(food.getStoredFood());
        }
        return List.of(defaultDrink());
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (level.getBlockEntity(pos) instanceof CookingPlacedFoodBlockEntity food
                && !food.getStoredFood().isEmpty()) {
            return food.getStoredFood();
        }
        return defaultDrink();
    }

    private ItemStack defaultDrink() {
        return new ItemStack(BuiltInRegistries.ITEM.get(
                new ResourceLocation(StardewCraft.MODID, getItemId())));
    }
}
