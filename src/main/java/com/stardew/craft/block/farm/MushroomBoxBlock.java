package com.stardew.craft.block.farm;

import com.stardew.craft.blockentity.MushroomBoxBlockEntity;
import com.stardew.craft.blockentity.UtilityDropHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 蘑菇培养盆（Mushroom Box）— 放置在农场洞穴内，每日产蘑菇。
 *
 * <p>生存模式不可破坏（strength=-1），不受活塞影响（PushReaction.BLOCK），
 * 不在创造栏出现，无掉落。</p>
 */
public class MushroomBoxBlock extends Block implements EntityBlock {

    public static final BooleanProperty READY = BooleanProperty.create("ready");

    public MushroomBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(READY, false));
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(READY);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new MushroomBoxBlockEntity(pos, state);
    }

    @SuppressWarnings("null")
    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state, @Nonnull Level level,
                                              @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull InteractionHand hand,
                                              @Nonnull BlockHitResult hit) {
        if (harvest(level, pos, player)) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @SuppressWarnings("null")
    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos,
                                               @Nonnull Player player, @Nonnull BlockHitResult hit) {
        if (harvest(level, pos, player)) {
            return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private boolean harvest(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            BlockState state = level.getBlockState(pos);
            return state.hasProperty(READY) && state.getValue(READY);
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MushroomBoxBlockEntity box)) return false;
        if (!box.isReady()) return false;
        ItemStack out = box.harvest();
        if (out.isEmpty()) return false;
        if (!player.addItem(out)) {
            player.drop(out, false);
        }
        UtilityDropHelper.grantHarvestRewards(level, pos, player, UtilityDropHelper.STANDARD_MACHINE_VANILLA_XP);
        // Foraging +5 (SDV: MachineData ExperienceGainOnHarvest "Foraging 5")
        if (player instanceof ServerPlayer sp) {
            com.stardew.craft.player.PlayerStardewDataAPI.addExperience(
                    sp, com.stardew.craft.player.SkillType.FORAGING, 5);
        }
        return true;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            @Nonnull Level level, @Nonnull BlockState state,
            @Nonnull net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return null;
    }
}
