package com.stardew.craft.block.decor;

import com.stardew.craft.blockentity.ScarecrowBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 稻草人方块（Scarecrow / Rarecrow）。
 * 走 MapDecor 的所有逻辑，1×1 占位，朝向可旋转。
 * 提供一个圆形保护半径 (basic=9, rarecrow=8)，由 ScarecrowManager 维护。
 */
@SuppressWarnings("null")
public class ScarecrowBlock extends MapDecorStaticBlock implements EntityBlock {
    private final int radius;
    private final int variantIndex;

    public ScarecrowBlock(Properties properties, String modelId, int variantIndex, int radius) {
        super(properties, modelId);
        this.variantIndex = variantIndex;
        this.radius = radius;
    }

    public int getRadius() { return radius; }
    public int getVariantIndex() { return variantIndex; }

    /** Shared XZ-plane coverage test used by crow attacks and the client range preview. */
    public static boolean protects(BlockPos scarecrowPos, BlockPos targetPos, int radius) {
        int dx = scarecrowPos.getX() - targetPos.getX();
        int dz = scarecrowPos.getZ() - targetPos.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        if (state.getValue(PART) != Part.MAIN) return null;
        return new ScarecrowBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(@Nonnull ItemStack stack, @Nonnull BlockState state,
                                              @Nonnull Level level, @Nonnull BlockPos pos,
                                              @Nonnull Player player, @Nonnull InteractionHand hand,
                                              @Nonnull BlockHitResult hit) {
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            BlockPos mainPos = findMainPos(sl, pos, state);
            if (mainPos == null) mainPos = pos;
            if (sl.getBlockEntity(mainPos) instanceof ScarecrowBlockEntity be) {
                player.displayClientMessage(
                        Component.translatable("message.scarecrow.crows_scared", be.getCrowsScared()),
                        true);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
