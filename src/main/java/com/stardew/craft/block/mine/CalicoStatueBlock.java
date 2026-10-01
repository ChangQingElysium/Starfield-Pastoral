package com.stardew.craft.block.mine;

import com.stardew.craft.block.mastery.TallMasteryBlock;
import com.stardew.craft.festival.desert.DesertFestivalMineService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

@SuppressWarnings("null")
public class CalicoStatueBlock extends TallMasteryBlock {
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");

    public CalicoStatueBlock(Properties properties) {
        super(properties, "stardewcraft:block/utility/totem_pole_desert", Direction.NORTH);
        registerDefaultState(defaultBlockState().setValue(ACTIVATED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVATED);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) {
            pos = TallMasteryBlock.getMainPos(pos, state);
            state = level.getBlockState(pos);
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(ACTIVATED)) {
            return InteractionResult.SUCCESS;
        }
        if (!DesertFestivalMineService.activateCalicoStatue(serverPlayer, serverLevel, pos, serverLevel.getRandom())) {
            return InteractionResult.SUCCESS;
        }
        setActivated(level, pos, state, true);
        return InteractionResult.SUCCESS;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = useWithoutItem(state, level, pos, player, hit);
        return switch (result) {
            case SUCCESS -> ItemInteractionResult.sidedSuccess(level.isClientSide());
            case CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.CONSUME;
            case FAIL -> ItemInteractionResult.FAIL;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    public static void setActivated(Level level, BlockPos pos, BlockState state, boolean activated) {
        level.setBlock(pos, state.setValue(ACTIVATED, activated), 3);
        BlockPos extensionPos = pos.above();
        BlockState extensionState = level.getBlockState(extensionPos);
        if (extensionState.getBlock() instanceof CalicoStatueBlock && extensionState.hasProperty(ACTIVATED)) {
            level.setBlock(extensionPos, extensionState.setValue(ACTIVATED, activated), 3);
        }
    }
}
