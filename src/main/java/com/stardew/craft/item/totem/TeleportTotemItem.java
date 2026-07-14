package com.stardew.craft.item.totem;

import com.stardew.craft.block.utility.totem.TotemPoleBlock;
import com.stardew.craft.block.utility.totem.TotemType;
import com.stardew.craft.blockentity.TotemPoleBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.totem.TotemPoleTracker;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 传送图腾（消耗品）— Farm / Mountain / Beach / Desert 四种。
 * 严格复刻 SDV Object.performUseAction + totemWarp + totemWarpForReal。
 */
@SuppressWarnings("null")
public class TeleportTotemItem extends Item implements IStardewItem {
    private static final int SAFE_SEARCH_RADIUS = 3;
    private static final int SAFE_SEARCH_VERTICAL = 3;

    private static final String TAG_BOUND_ID = "boundPoleId";
    private static final String TAG_BOUND_NAME = "boundPoleName";

    private final TotemType totemType;

    public TeleportTotemItem(TotemType totemType, Properties properties) {
        super(properties);
        this.totemType = totemType;
    }

    public TotemType getTotemType() {
        return totemType;
    }

    public boolean performFreeWarp(ServerPlayer player) {
        if (player == null || !player.isAlive()) {
            return false;
        }
        if (totemType == TotemType.FARM
                && com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID()) == null) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.totem_no_farm"), true);
            return false;
        }
        performWarp(player, new ItemStack(this), false);
        return true;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.magic";
    }

    /* ---------- 绑定 ---------- */

    /** 将此图腾绑定到指定图腾柱 */
    public static void bindToPole(ItemStack stack, int poleId, String poleName, TotemType type) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(TAG_BOUND_ID, poleId);
        tag.putString(TAG_BOUND_NAME, poleName);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getBoundPoleId(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return -1;
        CompoundTag tag = data.copyTag();
        return tag.contains(TAG_BOUND_ID) ? tag.getInt(TAG_BOUND_ID) : -1;
    }

    public static String getBoundPoleName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return "";
        return data.copyTag().getString(TAG_BOUND_NAME);
    }

    /* ---------- 使用逻辑 ---------- */

    /**
     * 右键点击图腾柱方块 → 绑定（潜行时 MC 会跳过 Block.useItemOn，所以绑定逻辑必须在此处理）。
     */
    @SuppressWarnings("null")
    @Override
    @Nonnull
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (player == null) {
            return InteractionResult.PASS;
        }

        if (!(state.getBlock() instanceof TotemPoleBlock totemBlock)) {
            return InteractionResult.PASS;
        }

        // 客户端也返回 SUCCESS 以阻止交互链继续到 use()（防止额外触发传送）
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        // 解析 Extension → Main
        BlockPos mainPos = totemBlock.resolveMainPos(level, pos, state);

        BlockEntity be = level.getBlockEntity(mainPos);
        if (!(be instanceof TotemPoleBlockEntity pole)) {
            return InteractionResult.PASS;
        }

        // 类型不匹配
        if (totemBlock.getTotemType() != totemType) {
            player.displayClientMessage(
                    Component.translatable("message.stardewcraft.totem_type_mismatch"), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        // 绑定手中全部同类图腾
        bindToPole(stack, pole.getPoleId(), pole.getPoleName(), totemType);

        // 激活图腾柱
        if (!pole.isActivated()) {
            pole.setActivated(true);
        }

        level.playSound(null, mainPos, ModSounds.SMALL_SELECT.get(), SoundSource.BLOCKS, 0.8f, 1.05f);
        Component poleName = pole.isSystemPole() && pole.getPoleName().startsWith("stardewcraft.")
                ? Component.translatable(pole.getPoleName())
                : Component.literal(pole.getPoleName());
        player.displayClientMessage(Component.translatable("message.stardewcraft.totem_bound", poleName), true);
        return InteractionResult.SUCCESS;
    }

    @SuppressWarnings("null")
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 潜行时不触发传送（潜行右键仅用于绑定图腾柱）
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        // SDV: normal_gameplay 检查 — 简化为 canMove 检查
        if (!player.isAlive()) {
            return InteractionResultHolder.pass(stack);
        }

        if (totemType == TotemType.FARM
                && com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID()) == null) {
            player.displayClientMessage(Component.translatable("message.stardewcraft.totem_no_farm"), true);
            return InteractionResultHolder.fail(stack);
        }

        if (player instanceof ServerPlayer sp) {
            performWarp(sp, stack, !player.isCreative());
        }

        return InteractionResultHolder.consume(stack);
    }

    private void performWarp(ServerPlayer player, ItemStack stack, boolean consumeStack) {
        Level level = player.level();
        level.playSound(null, player.blockPosition(), ModSounds.WARRIOR.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        if (level instanceof ServerLevel sl) {
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();
            for (int i = 0; i < 12; i++) {
                double ox = (sl.random.nextDouble() - 0.5) * 8.0;
                double oz = (sl.random.nextDouble() - 0.5) * 8.0;
                sl.sendParticles(ParticleTypes.END_ROD, px + ox, py + 1.0, pz + oz,
                        1, 0, 0.1, 0, 0.02);
            }
            for (int i = 0; i < 24; i++) {
                double ox = (sl.random.nextDouble() - 0.5) * 6.0;
                double oz = (sl.random.nextDouble() - 0.5) * 6.0;
                sl.sendParticles(ParticleTypes.FIREWORK, px + ox, py + 0.5, pz + oz,
                        1, 0, 0.3, 0, 0.05);
            }
            for (int dx = 8; dx >= -8; dx--) {
                sl.sendParticles(ParticleTypes.END_ROD,
                        px + dx, py + 0.5, pz,
                        1, 0, 0, 0, 0.01);
            }
        }

        if (consumeStack) {
            stack.shrink(1);
        }

        level.playSound(null, player.blockPosition(), ModSounds.WAND.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        player.closeContainer();
        player.stopUsingItem();

        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel != null) {
            BlockPos target = resolveTeleportDestination(player, stack, stardewLevel);
            ModTeleport.to(player, stardewLevel, target, player.getYRot(), player.getXRot());
        }
    }

    /** 解析图腾对应的锚点位置（图腾柱底座坐标） */
    private BlockPos resolveTargetAnchor(ServerPlayer player, ItemStack stack) {
        int boundId = getBoundPoleId(stack);
        ServerLevel stardewLevel = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            return getDefaultPosition(player);
        }

        TotemPoleTracker tracker = TotemPoleTracker.get(stardewLevel);

        if (boundId >= 0) {
            // 已绑定 → 查找对应柱子
            TotemPoleTracker.PoleEntry entry = tracker.getPole(boundId);
            if (entry != null) {
                return entry.pos();
            }
        }

        // FARM 类型未绑定时 → 优先使用玩家自己农场的图腾柱位置
        if (totemType == TotemType.FARM) {
            com.stardew.craft.farm.FarmInstance farm =
                    com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            if (farm != null) {
                return farm.getFarmTotemPos();
            }
        }

        // 未绑定或柱子不存在 → 使用系统柱
        TotemPoleTracker.PoleEntry defaultPole = tracker.getDefaultPole(totemType);
        if (defaultPole != null) {
            return defaultPole.pos();
        }

        // 终极 fallback
        return getDefaultPosition(player);
    }

    /** 优先传送到图腾柱周围可站立的位置，而不是柱子正上方。 */
    private BlockPos resolveTeleportDestination(ServerPlayer player, ItemStack stack, ServerLevel stardewLevel) {
        BlockPos anchor = resolveTargetAnchor(player, stack);
        BlockPos nearby = findNearbyStandablePosition(stardewLevel, anchor, false);
        if (nearby != null) {
            return nearby;
        }

        BlockPos fallback = findNearbyStandablePosition(stardewLevel, getDefaultPosition(player), true);
        if (fallback != null) {
            return fallback;
        }

        return anchor.above(2);
    }

    @Nullable
    private BlockPos findNearbyStandablePosition(ServerLevel level, BlockPos anchor, boolean allowCenterColumn) {
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;

        for (int dx = -SAFE_SEARCH_RADIUS; dx <= SAFE_SEARCH_RADIUS; dx++) {
            for (int dz = -SAFE_SEARCH_RADIUS; dz <= SAFE_SEARCH_RADIUS; dz++) {
                if (!allowCenterColumn && dx == 0 && dz == 0) {
                    continue;
                }
                int horizontalDistanceSq = dx * dx + dz * dz;
                if (horizontalDistanceSq == 0 && !allowCenterColumn) {
                    continue;
                }
                if (horizontalDistanceSq > SAFE_SEARCH_RADIUS * SAFE_SEARCH_RADIUS) {
                    continue;
                }

                for (int dy = -SAFE_SEARCH_VERTICAL; dy <= SAFE_SEARCH_VERTICAL; dy++) {
                    BlockPos groundPos = anchor.offset(dx, dy, dz);
                    if (!isStandableGround(level, groundPos)) {
                        continue;
                    }

                    int score = horizontalDistanceSq * 10 + Math.abs(dy);
                    if (score < bestScore) {
                        bestScore = score;
                        best = groundPos.above();
                    }
                }
            }
        }

        return best;
    }

    private boolean isStandableGround(ServerLevel level, BlockPos groundPos) {
        if (!level.getBlockState(groundPos).isFaceSturdy(level, groundPos, Direction.UP)) {
            return false;
        }

        BlockPos feetPos = groundPos.above();
        BlockPos headPos = feetPos.above();
        return level.getFluidState(feetPos).isEmpty()
                && level.getFluidState(headPos).isEmpty()
                && level.getBlockState(feetPos).getCollisionShape(level, feetPos).isEmpty()
                && level.getBlockState(headPos).getCollisionShape(level, headPos).isEmpty();
    }

    /** SDV 原版默认传送位置。 */
    private BlockPos getDefaultPosition(ServerPlayer player) {
        if (totemType == TotemType.FARM) {
            com.stardew.craft.farm.FarmInstance farm =
                    com.stardew.craft.farm.FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            if (farm != null) {
                return farm.getFarmTotemPos();
            }
        }
        return switch (totemType) {
            case FARM -> player.blockPosition();
            case MOUNTAIN -> new BlockPos(75, 81, -104);
            case BEACH -> new BlockPos(44, 60, 94);
            case DESERT -> new BlockPos(-203, 64, -157);
        };
    }

    /* ---------- 物品名称 ---------- */

    @SuppressWarnings("null")
    @Override
    @Nonnull
    public Component getName(@Nonnull ItemStack stack) {
        String boundName = getBoundPoleName(stack);
        Component baseName = super.getName(stack);
        if (!boundName.isEmpty()) {
            return Component.literal(baseName.getString() + " - " + boundName);
        }
        return baseName;
    }

    @SuppressWarnings("null")
    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
                                @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag flag) {
        int boundId = getBoundPoleId(stack);
        if (boundId >= 0) {
            String boundName = getBoundPoleName(stack);
            tooltipComponents.add(Component.translatable("tooltip.stardewcraft.totem_bound_to",
                    boundName.isEmpty() ? "#" + boundId : boundName)
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            tooltipComponents.add(Component.translatable("tooltip.stardewcraft.totem_unbound")
                    .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        }
    }
}
