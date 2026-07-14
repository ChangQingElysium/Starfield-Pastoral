package com.stardew.craft.block.utility.totem;

import com.stardew.craft.Config;
import com.stardew.craft.blockentity.TotemPoleBlockEntity;
import com.stardew.craft.block.utility.MapUtilityStaticBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.network.payload.OpenTotemNamingScreenPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 图腾柱方块 — Farm / Mountain / Beach / Desert 四种共享此类，由 {@link TotemType} 区分。
 */
@SuppressWarnings("null")
public class TotemPoleBlock extends MapUtilityStaticBlock implements EntityBlock {
    public static final BooleanProperty ACTIVATED = BooleanProperty.create("activated");
    private static final PlacementBounds FARM_BOUNDS = PlacementBounds.of(311, 36, 154, 103, -18, 37);
    private static final PlacementBounds MOUNTAIN_BOUNDS = PlacementBounds.of(-52, 77, -233, 140, 112, -91);
    private static final PlacementBounds BEACH_BOUNDS = PlacementBounds.of(-15, 59, 82, 174, 76, 174);
    private static final PlacementBounds DESERT_BOUNDS = PlacementBounds.of(-302, 56, -240, -156, 86, -112);

    private final TotemType totemType;

    @SuppressWarnings("null")
    public TotemPoleBlock(Properties properties, TotemType totemType, String modelId) {
        super(properties.lightLevel(state -> state.getValue(ACTIVATED) ? 15 : 0), modelId);
        this.totemType = totemType;
        registerDefaultState(defaultBlockState().setValue(ACTIVATED, false));
    }

    public TotemType getTotemType() {
        return totemType;
    }

    public static boolean isInsideBeachBounds(BlockPos pos) {
        return pos != null && BEACH_BOUNDS.contains(pos);
    }

    @SuppressWarnings("null")
    @Override
    protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ACTIVATED);
    }

    @Override
    public RenderShape getRenderShape(@SuppressWarnings("null") BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @SuppressWarnings("null")
    @Override
    protected List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return List.of();
        }
        return List.of(new ItemStack(this));
    }

    @SuppressWarnings("null")
    @Override
    @Nullable
    public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) {
            return null;
        }
        return new TotemPoleBlockEntity(pos, state, totemType);
    }

    @SuppressWarnings("null")
    @Override
    @Nullable
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            @SuppressWarnings("null") Level level,
            @SuppressWarnings("null") BlockState state,
            @SuppressWarnings("null") BlockEntityType<T> type) {
        // 图腾柱没有 tick 逻辑
        return null;
    }

    /* ---------- 放置时初始化 + 打开命名GUI ---------- */

    @SuppressWarnings("null")
    @Override
    @Nullable
    public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (Config.TOTEM_POLE_ENFORCE_PLACEMENT_RULES.get() && !canPlaceTotemHere(level, pos)) {
            Player player = context.getPlayer();
            if (player != null && level.isClientSide) {
                player.displayClientMessage(getPlacementDeniedMessage(level), true);
            }
            return null;
        }
        return super.getStateForPlacement(context);
    }

    @Override
    public void setPlacedBy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state,
                            @Nullable LivingEntity placer, @Nonnull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || state.getValue(PART) != Part.MAIN) return;

        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof TotemPoleBlockEntity pole) {
            // 分配全局ID并注册到 Tracker
            pole.initOnPlace(serverLevel);

            // 向放置者发送命名界面
            if (placer instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new OpenTotemNamingScreenPayload(
                        pos.asLong(),
                        pole.getPoleName(),
                        totemType.getId(),
                        pole.getPoleId()
                ));
            }
        }
    }

    /* ---------- 交互 ---------- */

    @SuppressWarnings("null")
    @Override
    protected ItemInteractionResult useItemOn(
            @SuppressWarnings("null") ItemStack stack,
            @SuppressWarnings("null") BlockState state,
            @SuppressWarnings("null") Level level,
            @SuppressWarnings("null") BlockPos pos,
            @SuppressWarnings("null") Player player,
            @SuppressWarnings("null") InteractionHand hand,
            @SuppressWarnings("null") BlockHitResult hit) {
        // 绑定逻辑已移至 TeleportTotemItem.useOn()，此处不做额外处理
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @SuppressWarnings("null")
    @Override
    protected InteractionResult useWithoutItem(
            @SuppressWarnings("null") BlockState state,
            @SuppressWarnings("null") Level level,
            @SuppressWarnings("null") BlockPos pos,
            @SuppressWarnings("null") Player player,
            @SuppressWarnings("null") BlockHitResult hit) {
        // 扩展块重定向到主块
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos mainPos = findMainPos(level, pos, state);
            if (mainPos == null) return InteractionResult.PASS;
            return useWithoutItem(level.getBlockState(mainPos), level, mainPos, player, hit);
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof TotemPoleBlockEntity pole)) {
            return InteractionResult.PASS;
        }

        // 系统柱不允许重命名
        if (pole.isSystemPole()) {
            return InteractionResult.PASS;
        }

        // 空手右键 → 打开命名GUI
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new OpenTotemNamingScreenPayload(
                    pos.asLong(),
                    pole.getPoleName(),
                    totemType.getId(),
                    pole.getPoleId()
            ));
        }
        return InteractionResult.CONSUME;
    }

    /* ---------- 系统柱不可破坏 ---------- */

    @SuppressWarnings("null")
    @Override
    public float getDestroyProgress(@SuppressWarnings("null") BlockState state,
                                    @SuppressWarnings("null") Player player,
                                    @SuppressWarnings("null") BlockGetter level,
                                    @SuppressWarnings("null") BlockPos pos) {
        BlockEntity be = level.getBlockEntity(resolveMainPos(level, pos, state));
        if (be instanceof TotemPoleBlockEntity pole && pole.isSystemPole()) {
            return 0.0f; // 不可破坏
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @SuppressWarnings("null")
    @Override
    public void onRemove(@SuppressWarnings("null") BlockState state,
                         @SuppressWarnings("null") Level level,
                         @SuppressWarnings("null") BlockPos pos,
                         @SuppressWarnings("null") BlockState newState,
                         boolean isMoving) {
        if (!state.is(newState.getBlock()) && !isMoving && state.getValue(PART) == Part.MAIN) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TotemPoleBlockEntity pole && !pole.isSystemPole()) {
                pole.unregisterFromTracker();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /** 解析 Extension → Main 位置，已是 Main 则返回自身 */
    public BlockPos resolveMainPos(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.EXTENSION) {
            BlockPos main = findMainPos(level, pos, state);
            return main != null ? main : pos;
        }
        return pos;
    }

    private boolean canPlaceTotemHere(Level level, BlockPos pos) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return false;
        }
        return getPlacementBounds().contains(pos);
    }

    private PlacementBounds getPlacementBounds() {
        return switch (totemType) {
            case FARM -> FARM_BOUNDS;
            case MOUNTAIN -> MOUNTAIN_BOUNDS;
            case BEACH -> BEACH_BOUNDS;
            case DESERT -> DESERT_BOUNDS;
        };
    }

    private Component getPlacementDeniedMessage(Level level) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY) {
            return Component.translatable("message.stardewcraft.totem_place_stardew_only");
        }
        return Component.translatable("message.stardewcraft.totem_place_out_of_bounds",
                Component.translatable(totemType.getDefaultNameKey()));
    }

    private record PlacementBounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        private static PlacementBounds of(int x1, int y1, int z1, int x2, int y2, int z2) {
            return new PlacementBounds(
                    Math.min(x1, x2), Math.max(x1, x2),
                    Math.min(y1, y2), Math.max(y1, y2),
                    Math.min(z1, z2), Math.max(z1, z2));
        }

        private boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                    && pos.getY() >= minY && pos.getY() <= maxY
                    && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }
}
