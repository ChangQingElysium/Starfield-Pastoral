package com.stardew.craft.block.utility;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.MailboxBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.mail.MailService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 信箱方块 — SDV 邮件系统的物理入口。
 * 继承 MapUtilityStaticBlock 获得自适应碰撞箱 + PART/FACING/Extension 系统。
 * 每个信箱有主人（ownerUUID via MailboxBlockEntity）。
 */
@SuppressWarnings("null")
public class MailboxBlock extends MapUtilityStaticBlock implements EntityBlock {
    public static final BooleanProperty HAS_MAIL = BooleanProperty.create("has_mail");

    public MailboxBlock(Properties properties) {
        super(properties, "stardewcraft:block/utility/mailbox", true);
        registerDefaultState(defaultBlockState().setValue(HAS_MAIL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HAS_MAIL);
    }

    @SuppressWarnings("null")
    @Override
    public List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) return List.of();
        return List.of(new ItemStack(ModBlocks.MAILBOX.get()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) return null;
        return new MailboxBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockEntityType<T> type) {
        if (state.getValue(PART) == Part.EXTENSION) return null;
        if (type != ModBlockEntities.MAILBOX.get()) return null;
        if (level.isClientSide) return null;
        return (lvl, pos, st, be) -> ((MailboxBlockEntity) be).serverTick();
    }

    @SuppressWarnings("null")
    @Override
    public void setPlacedBy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos,
                            @SuppressWarnings("null") BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer,
                            @SuppressWarnings("null") ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) return;
        // Set owner
        if (placer instanceof ServerPlayer sp) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MailboxBlockEntity mailbox) {
                mailbox.setOwner(sp);
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state,
                                               @SuppressWarnings("null") Level level,
                                               @SuppressWarnings("null") BlockPos pos,
                                               @SuppressWarnings("null") Player player,
                                               @SuppressWarnings("null") BlockHitResult hit) {
        // Redirect extension clicks to main
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) {
                BlockState mainState = level.getBlockState(mainPos);
                return useWithoutItem(mainState, level, mainPos, player, hit);
            }
            return InteractionResult.PASS;
        }

        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (player instanceof ServerPlayer sp) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MailboxBlockEntity mailbox) {
                if (mailbox.hasOwner() && !mailbox.isOwner(sp)) {
                    // 共用农场成员也可使用邮箱
                    var registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
                    if (mailbox.getOwnerUUID() == null
                            || !registry.areFarmmates(sp.getUUID(), mailbox.getOwnerUUID())) {
                        sp.sendSystemMessage(Component.translatable("stardewcraft.mailbox.not_yours"));
                        return InteractionResult.CONSUME;
                    }
                }
                if (!mailbox.hasOwner()) {
                    mailbox.setOwner(sp);
                }
            }
            MailService.openNextMail(sp);
        }
        return InteractionResult.CONSUME;
    }

    @SuppressWarnings("null")
    @Override
    public void playerWillDestroy(@SuppressWarnings("null") Level level,
                                        @SuppressWarnings("null") BlockPos pos,
                                        @SuppressWarnings("null") BlockState state,
                                        @SuppressWarnings("null") Player player) {
        // System mailbox cannot be destroyed
        if (!level.isClientSide) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos != null) {
                BlockEntity be = level.getBlockEntity(mainPos);
                if (be instanceof MailboxBlockEntity mailbox && mailbox.isSystemBlock()) {
                    player.sendSystemMessage(Component.translatable("stardewcraft.mailbox.system_block"));
                    return;
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
