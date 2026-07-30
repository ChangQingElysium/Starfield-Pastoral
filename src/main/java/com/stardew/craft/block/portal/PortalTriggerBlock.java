package com.stardew.craft.block.portal;

import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.event.InteriorPortalInteractionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;

/**
 * 隐形传送触发方块 — 替代 vanilla Interaction 实体用于室内外传送。
 * <p>
 * 特性：
 * - 完全隐形（无模型、无贴图）
 * - 无碰撞箱和选择框
 * - 不可破坏
 * - 右键触发传送（通过 BlockEntity 存储的目标 ID）
 */
@SuppressWarnings("null")
public class PortalTriggerBlock extends Block implements EntityBlock {

    public PortalTriggerBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (isMrQiSandDragon(level, pos) && !canSelectMrQiSandDragon(context)) {
            return Shapes.empty();
        }
        String targetId = portalTargetId(level, pos);
        if (isCasinoPortal(targetId) && !canSelectCasinoPortal(context, targetId)) {
            return Shapes.empty();
        }
        // 返回完整方块形状，使玩家准星能瞄准并右键交互
        return Shapes.block();
    }

    @Nullable
    private static String portalTargetId(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity portal
                ? portal.getTargetId()
                : null;
    }

    private static boolean isCasinoPortal(@Nullable String targetId) {
        return com.stardew.craft.casino.CasinoAccessService.ENTRY_TARGET_ID.equals(targetId)
                || com.stardew.craft.casino.CasinoAccessService.EXIT_TARGET_ID.equals(targetId);
    }

    private static boolean canSelectCasinoPortal(CollisionContext context, String targetId) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity instanceof ServerPlayer player) {
                if (com.stardew.craft.casino.CasinoAccessService.ENTRY_TARGET_ID.equals(targetId)) {
                    return com.stardew.craft.casino.CasinoAccessService.canUseEntrance(player);
                }
                return com.stardew.craft.casino.CasinoAccessService.isCasinoPosition(
                        player.getX(), player.getY(), player.getZ());
            }
            if (entity instanceof Player player) {
                return canLocalPlayerSelectCasinoPortal(player, targetId);
            }
        }
        return false;
    }

    private static boolean canLocalPlayerSelectCasinoPortal(Player player, String targetId) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return false;
        }
        if (com.stardew.craft.casino.CasinoAccessService.ENTRY_TARGET_ID.equals(targetId)) {
            return (com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.item.PowerSpecialItemService.CLUB_CARD_FLAG)
                    || com.stardew.craft.client.ClientPlayerDataCache.hasSpecialItem(
                    com.stardew.craft.item.PowerSpecialItemService.CLUB_CARD_ID))
                    && com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.casino.CasinoAccessService.BOUNCER_GONE_FLAG);
        }
        return com.stardew.craft.casino.CasinoAccessService.isCasinoPosition(
                player.getX(), player.getY(), player.getZ());
    }

    private static boolean isMrQiSandDragon(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity portal
                && com.stardew.craft.qi.MrQiQuestInteractionService.SAND_DRAGON_TARGET_ID
                .equals(portal.getTargetId());
    }

    private static boolean canSelectMrQiSandDragon(CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity instanceof ServerPlayer player) {
                return com.stardew.craft.qi.MrQiQuestInteractionService.hasActiveSandDragonQuest(player);
            }
            if (entity instanceof Player) {
                return FMLEnvironment.dist == Dist.CLIENT
                        && com.stardew.craft.quest.network.ClientQuestData.hasQuest(
                        com.stardew.craft.qi.MrQiQuestRules.QUEST_SAND_DRAGON);
            }
        }
        return FMLEnvironment.dist == Dist.CLIENT
                && com.stardew.craft.quest.network.ClientQuestData.hasQuest(
                com.stardew.craft.qi.MrQiQuestRules.QUEST_SAND_DRAGON);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // 无碰撞箱，玩家可穿过
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PortalTriggerBlockEntity portalBE)) return InteractionResult.PASS;

        String targetId = portalBE.getTargetId();
        if (targetId == null || targetId.isEmpty()) return InteractionResult.PASS;

        InteriorPortalInteractionEvents.handlePortalInteraction(sp, targetId);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        InteractionResult result = useWithoutItem(state, level, pos, player, hitResult);
        return switch (result) {
            case SUCCESS -> ItemInteractionResult.sidedSuccess(level.isClientSide());
            case CONSUME, CONSUME_PARTIAL -> ItemInteractionResult.CONSUME;
            case FAIL -> ItemInteractionResult.FAIL;
            default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalTriggerBlockEntity(pos, state);
    }
}
