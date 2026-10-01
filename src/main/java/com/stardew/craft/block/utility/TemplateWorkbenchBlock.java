package com.stardew.craft.block.utility;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.network.payload.OpenWorkbenchPayload;
import com.stardew.craft.workbench.TemplateWorkbenchCrafting;
import com.stardew.craft.workbench.WorkbenchType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

public class TemplateWorkbenchBlock extends WoodWorkbenchBlock {
    public TemplateWorkbenchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        open(player, pos);
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        open(player, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private void open(Player player, BlockPos pos) {
        if (player instanceof ServerPlayer serverPlayer) {
            TemplateWorkbenchCrafting.open(serverPlayer, pos);
            PacketDistributor.sendToPlayer(serverPlayer, new OpenWorkbenchPayload(WorkbenchType.TEMPLATE));
        }
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ModelVoxelShapeCache.variantShape("stardewcraft:template_workbench",
                "facing=" + state.getValue(FACING).getSerializedName());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
