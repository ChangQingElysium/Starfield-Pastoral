package com.stardew.craft.block.utility;

import com.stardew.craft.blockentity.DecorBlockEntity;
import com.stardew.craft.deco.LegacyWallpaperMigration;
import com.stardew.craft.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;

import javax.annotation.Nullable;
import java.util.List;

/** Read-only compatibility shell for the pre-stable-ID wallpaper block. */
@SuppressWarnings("null")
public class LegacyWallpaperBlock extends Block implements EntityBlock {
    public static final IntegerProperty STYLE = IntegerProperty.create("style", 0, 137);
    public static final IntegerProperty SEGMENT = WallpaperBlock.SEGMENT;

    public LegacyWallpaperBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STYLE, 0).setValue(SEGMENT, 0));
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(ModItems.WALLPAPER_BLOCK.get()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DecorBlockEntity(pos, state);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        LegacyWallpaperMigration.migrateAt(level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STYLE, SEGMENT);
    }
}
