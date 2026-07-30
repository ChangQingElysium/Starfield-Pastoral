package com.stardew.craft.item.tool;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.secretnote.SecretNoteBuriedTreasureService;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ItemAbility;
import net.neoforged.neoforge.common.ItemAbilities;

import java.util.ArrayList;
import java.util.List;

public class HoeItem extends Item implements IStardewItem {

    // 0.35s @ 20tps (与喷壶保持一致)
    private static final int HOE_COOLDOWN_TICKS = 7;
    // 用于区分“点按右键”和“开始长按蓄力”的阈值（ticks）
    public static final int TAP_THRESHOLD_TICKS = 4;
    // p0->p1 抬手时长（ticks）
    public static final int RAISE_TO_P1_TICKS = 6;

    // 客户端第一人称动画标记（仅用于渲染，不影响服务端判定）
    public static final String NBT_STRIKE_START_TICK = "StardewHoeStrikeStartTick";
    public static final String NBT_STRIKE_DURATION_TICKS = "StardewHoeStrikeDurationTicks";
    public static final String NBT_STRIKE_FROM_P1 = "StardewHoeStrikeFromP1";
    public static final int STRIKE_TOTAL_TICKS = 7;

    public enum Tier {
        STARTER(0),
        COPPER(1),
        STEEL(2),
        GOLD(3),
        IRIDIUM(4);

        final int maxChargeLevel;

        Tier(int maxChargeLevel) {
            this.maxChargeLevel = maxChargeLevel;
        }

        public int getMaxChargeLevel() {
            return maxChargeLevel;
        }
    }

    private final Tier tier;

    public HoeItem(Tier tier, Properties properties) {
        super(properties.setNoRepair());
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public boolean canPerformAction(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") ItemAbility ability) {
        // 关键：让方块的 getToolModifiedState(ctx, HOE_TILL, ...) 能识别这是“锄头能力”。
        return ItemAbilities.DEFAULT_HOE_ACTIONS.contains(ability);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.tool";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        // 星露谷工具不可出售
        return -1;
    }

    @Override
    public boolean isEnchantable(@SuppressWarnings("null") ItemStack stack) {
        return stack.getMaxStackSize() == 1;
    }

    @Override
    public int getEnchantmentValue() {
        return Math.max(1, (tier.getMaxChargeLevel() + 1) * 5);
    }

    // ================= 使用逻辑（按水壶风格） =================

    @Override
    public int getUseDuration(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(@SuppressWarnings("null") ItemStack stack) {
        return UseAnim.BOW;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResult.FAIL;
        }

        // 普通锄头：不支持蓄力，右键就直接翻一格（星露谷手感：利落、不进入长按动作）
        if (!supportsCharging()) {
            Level level = context.getLevel();
            InteractionHand hand = context.getHand();
            BlockPos pos = context.getClickedPos();

            if (!canTill(level, player, hand, pos)) {
                return InteractionResult.PASS;
            }

            // 能量为 0 时：拦截耗能动作（仅星露谷维度）。
            if (!level.isClientSide
                && player instanceof ServerPlayer serverPlayer
                && !player.isCreative()
                && player.level().dimension() == ModDimensions.STARDEW_VALLEY) {
                if (PlayerStardewDataAPI.getEnergy(serverPlayer) <= 0.0f) {
                    player.displayClientMessage(Component.translatable("stardewcraft.message.player.exhausted"), true);
                    return InteractionResult.FAIL;
                }
            }

            if (level.isClientSide) {
                startClientStrike(stackFromContext(context), player, false);
            }

            if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
                BlockState preTillState = level.getBlockState(pos);
                boolean secretTreasureDug = player instanceof ServerPlayer serverPlayer
                        && SecretNoteBuriedTreasureService.tryDig(
                                serverLevel, serverPlayer, pos, preTillState);
                boolean tilled = secretTreasureDug || tillTile(serverLevel, player, hand, pos);
                if (tilled) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        com.stardew.craft.player.PlayerDataManager.getPlayerData(serverPlayer)
                                .incrementStat("dirtHoed", 1);
                    }
                    if (!secretTreasureDug) {
                        rollBuriedDrops(serverLevel, pos, preTillState,
                                player instanceof ServerPlayer sp ? sp : null, stackFromContext(context));
                    }
                    applyStaminaAndCooldown(player, stackFromContext(context), 0);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // 可蓄力锄头：开始使用；在 releaseUsing 根据按住时长决定 0级 / 蓄力等级
        player.startUsingItem(context.getHand());
        return InteractionResult.CONSUME;
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResultHolder<ItemStack> use(@SuppressWarnings("null") Level level, @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand) {
        @SuppressWarnings("null")
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        // 普通锄头：右键空气不进入蓄力流程（避免抬手/拉弓动作）
        if (!supportsCharging()) {
            return InteractionResultHolder.pass(stack);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @SuppressWarnings("null")
    @Override
    public void onUseTick(@SuppressWarnings("null") Level level, @SuppressWarnings("null") LivingEntity livingEntity, @SuppressWarnings("null") ItemStack stack, int remainingUseDuration) {
        if (!(livingEntity instanceof Player player)) {
            return;
        }

        int usedTicks = getUseDuration(stack, livingEntity) - remainingUseDuration;

        // 与喷壶保持一致：每级蓄力播放提示音
        int ticksPerLevel = StardewEnchantments.has(stack, StardewEnchantments.SWIFT) ? 10 : 15;
        if (usedTicks > 0 && usedTicks % ticksPerLevel == 0) {
            int currentLevel = usedTicks / ticksPerLevel;
            int maxChargeLevel = getEffectiveMaxChargeLevel(stack);
            if (currentLevel <= maxChargeLevel) {
                float pitch = 0.8f + (currentLevel * 0.2f);
                level.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.5f, pitch);
            }
        }
    }

    @SuppressWarnings("null")
    @Override
    public void releaseUsing(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") Level level, @SuppressWarnings("null") LivingEntity entity, int timeCharged) {
        if (!(entity instanceof Player player)) {
            return;
        }

        int activeTicks = stack.getUseDuration(entity) - timeCharged;
        // 点按：强制当作 0 级（不算“蓄力锄”）
        int chargeLevel;
        if (activeTicks < TAP_THRESHOLD_TICKS) {
            chargeLevel = 0;
        } else {
            chargeLevel = getChargeLevel(stack, activeTicks);
        }

        // 获取目标方块（以视线命中为准）
        @SuppressWarnings("null")
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos startPos = hitResult.getBlockPos();
        InteractionHand usedHand = resolveHand(player, stack);
        List<BlockPos> targets = getAffectedBlocks(level, startPos, player, usedHand, chargeLevel);

        // 农场区域保护：在星露谷维度，非创造玩家不能在"自己农场以外"锄地。
        // 蓄力流程由 Item.use(air) → startUsingItem 启动，完全绕过 RightClickBlock 事件，
        // 因此直接在这里按目标格过滤，并在全部被过滤后给出提示。
        if (!level.isClientSide
            && player instanceof ServerPlayer sp
            && !sp.isCreative()
            && level.dimension() == ModDimensions.STARDEW_VALLEY) {
            int before = targets.size();
            targets.removeIf(pos -> {
                // 温室内部按 owner/farm 权限放行，不再一刀切豁免。
                if (level instanceof ServerLevel sl
                        && com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(sl, pos)) {
                    return !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyGreenhouseAt(sp, sl, pos);
                }
                // 远古斑点（含沙漠变体）：在小镇/沙漠等公共区域也允许锄取古物
                BlockState stateHere = level.getBlockState(pos);
                if (stateHere.is(com.stardew.craft.block.ModBlocks.ARTIFACT_SPOT_DIRT.get())
                        || stateHere.is(com.stardew.craft.block.ModBlocks.DESERT_ARTIFACT_SPOT.get())
                        || stateHere.is(com.stardew.craft.block.ModBlocks.BEACH_ARTIFACT_SPOT.get())) {
                    return false;
                }
                return !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(sp, pos);
            });
            if (targets.isEmpty() && before > 0) {
                sp.displayClientMessage(
                        Component.translatable("stardewcraft.farm.build_farm_only"), true);
                return;
            }
        }

        // 能量为 0 时：拦截耗能动作（仅星露谷维度）。
        if (!level.isClientSide
            && player instanceof ServerPlayer serverPlayer
            && !player.isCreative()
            && player.level().dimension() == ModDimensions.STARDEW_VALLEY) {
            if (PlayerStardewDataAPI.getEnergy(serverPlayer) <= 0.0f) {
                player.displayClientMessage(Component.translatable("stardewcraft.message.player.exhausted"), true);
                return;
            }
        }

        if (level.isClientSide) {
            // 蓄力锄：长按期间会到 p1；松手时从 p1 -> p2，更有“啪一下”的力度。
            // 点按：按 direct 流程（p0->p1->p2->p0）。
            // 注意：第一人称“抬手蓄力”会在 TAP_THRESHOLD 之后才开始，所以从 p1 起砸下去也需要加上该偏移。
            boolean fromP1 = supportsCharging() && activeTicks >= (TAP_THRESHOLD_TICKS + RAISE_TO_P1_TICKS);
            if (!targets.isEmpty()) {
                startClientStrike(stack, player, fromP1);
            }
        }

        if (!level.isClientSide) {
            boolean tilledAny = false;
            int tilledCount = 0;
            for (BlockPos pos : targets) {
                BlockState preTillState = level.getBlockState(pos);
                boolean secretTreasureDug = player instanceof ServerPlayer serverPlayer
                        && SecretNoteBuriedTreasureService.tryDig(
                                (ServerLevel) level, serverPlayer, pos, preTillState);
                if (secretTreasureDug || tillTile((ServerLevel) level, player, usedHand, pos)) {
                    tilledAny = true;
                    tilledCount++;
                    if (!secretTreasureDug) {
                        rollBuriedDrops((ServerLevel) level, pos, preTillState,
                                player instanceof ServerPlayer sp ? sp : null, stack);
                    }
                }
            }

            if (tilledAny) {
                if (player instanceof ServerPlayer serverPlayer) {
                    com.stardew.craft.player.PlayerDataManager.getPlayerData(serverPlayer)
                            .incrementStat("dirtHoed", tilledCount);
                }
                applyStaminaAndCooldown(player, stack, chargeLevel);
            }
        } else {
            // 客户端仅做一点粒子反馈（避免完全无反馈）
            if (chargeLevel > 0) {
                for (int i = 0; i < 4; i++) {
                    level.addParticle(ParticleTypes.CRIT, player.getX(), player.getY() + 1.0, player.getZ(), 0, 0, 0);
                }
            }
        }
    }

    private boolean supportsCharging() {
        return tier.maxChargeLevel > 0;
    }

    private static ItemStack stackFromContext(UseOnContext context) {
        return context.getItemInHand();
    }

    private static void startClientStrike(ItemStack stack, Player player, boolean fromP1) {
        // 纯客户端视觉：用 CustomData 记录开始 tick 与时长，mixin 在 render 时计算 progress。
        CompoundTag tag = getCustomTag(stack);
        tag.putInt(NBT_STRIKE_START_TICK, player.tickCount);
        tag.putInt(NBT_STRIKE_DURATION_TICKS, STRIKE_TOTAL_TICKS);
        tag.putBoolean(NBT_STRIKE_FROM_P1, fromP1);
        setCustomTag(stack, tag);
    }

    private static CompoundTag getCustomTag(ItemStack stack) {
        @SuppressWarnings("null")
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return new CompoundTag();
        }
        return data.copyTag();
    }

    @SuppressWarnings("null")
    private static void setCustomTag(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private void applyStaminaAndCooldown(Player player, ItemStack stack, int chargeLevel) {
        // 扣除能量 (Stamina)
        if (!player.isCreative() && player instanceof ServerPlayer serverPlayer
                && !StardewEnchantments.has(stack, StardewEnchantments.EFFICIENT)) {
            int farmingLevel = PlayerStardewDataAPI.getSkillLevel(serverPlayer, SkillType.FARMING);
            float staminaCost = (2.0f * (chargeLevel + 1)) - (farmingLevel * 0.1f);
            PlayerStardewDataAPI.consumeEnergy(serverPlayer, staminaCost);
        }

        // 冷却
        player.getCooldowns().addCooldown(this, HOE_COOLDOWN_TICKS);
    }

    /**
     * 与喷壶保持一致：每 15 ticks 升一级。
     */
    public int getChargeLevel(int ticksUsed) {
        return getChargeLevel(ItemStack.EMPTY, ticksUsed);
    }

    public int getChargeLevel(ItemStack stack, int ticksUsed) {
        int ticksPerLevel = StardewEnchantments.has(stack, StardewEnchantments.SWIFT) ? 10 : 15;
        int level = ticksUsed / ticksPerLevel;
        int maxChargeLevel = getEffectiveMaxChargeLevel(stack);
        if (level > maxChargeLevel) {
            level = maxChargeLevel;
        }
        return level;
    }

    public int getEffectiveMaxChargeLevel(ItemStack stack) {
        int maxChargeLevel = tier.maxChargeLevel;
        if (StardewEnchantments.has(stack, StardewEnchantments.EXPANSIVE)) {
            maxChargeLevel = Math.min(5, maxChargeLevel + 1);
        }
        return maxChargeLevel;
    }

    /**
     * 范围形状：与星露谷(以及 WateringCanItem)一致：
     * 0: 1x1
     * 1: 1x3
     * 2: 1x5
     * 3: 3x3
     * 4: 3x6
     * 5: 5x5 (预留：原版 Expansive/更高上限)
     */
    public List<BlockPos> getAffectedBlocks(Level level, BlockPos startPos, Player player, int chargeLevel) {
        // 默认按主手计算；客户端预览/实际执行建议用带 hand 的重载
        return getAffectedBlocks(level, startPos, player, InteractionHand.MAIN_HAND, chargeLevel);
    }

    @SuppressWarnings("null")
    public List<BlockPos> getAffectedBlocks(Level level, BlockPos startPos, Player player, InteractionHand hand, int chargeLevel) {
        List<BlockPos> list = collectAffectedBlockPattern(startPos, player, chargeLevel);

        // 只保留“看起来能锄”的格子：上方必须无碰撞体积（空气/作物等），且方块支持 HOE_TILL
        list.removeIf(pos -> !canTill(level, player, hand, pos));

        return list;
    }

    public List<BlockPos> getPreviewBlocks(Level level, BlockPos startPos, Player player, int chargeLevel) {
        List<BlockPos> list = collectAffectedBlockPattern(startPos, player, chargeLevel);
        list.removeIf(pos -> !hasClearHoeSpace(level, pos));
        return list;
    }

    private List<BlockPos> collectAffectedBlockPattern(BlockPos startPos, Player player, int chargeLevel) {
        List<BlockPos> list = new ArrayList<>();
        Direction facing = player.getDirection();

        if (chargeLevel == 0) {
            list.add(startPos);
        } else if (chargeLevel == 1) {
            for (int i = 0; i < 3; i++) {
                list.add(startPos.relative(facing, i));
            }
        } else if (chargeLevel == 2) {
            for (int i = 0; i < 5; i++) {
                list.add(startPos.relative(facing, i));
            }
        } else if (chargeLevel == 3) {
            @SuppressWarnings("null")
            BlockPos center = startPos.relative(facing, 1);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    list.add(center.offset(x, 0, z));
                }
            }
        } else if (chargeLevel == 4) {
            Direction right = facing.getClockWise();
            Direction left = facing.getCounterClockWise();

            for (int forward = 0; forward < 6; forward++) {
                BlockPos base = startPos.relative(facing, forward);
                list.add(base);
                list.add(base.relative(right));
                list.add(base.relative(left));
            }
        } else {
            // 5x5: 以 startPos 为中心（更符合“大片耕作”直觉）
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    list.add(startPos.offset(dx, 0, dz));
                }
            }
        }

        int baseY = startPos.getY();
        list.removeIf(p -> p.getY() != baseY);
        return list;
    }

    // 由于 getAffectedBlocks 需要在客户端预览也可用，这里避免直接用 server-only API。
    @SuppressWarnings("null")
    private boolean canTill(Level level, Player player, InteractionHand hand, BlockPos pos) {
        if (!hasClearHoeSpace(level, pos)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (SecretNoteBuriedTreasureService.isConfiguredDigSurface(level, pos, state)) {
            return level.isClientSide
                    || player instanceof ServerPlayer serverPlayer
                    && SecretNoteBuriedTreasureService.canDig(serverPlayer, pos, state);
        }
        if (isPublicYellowDirt(level, pos, state)) {
            return false;
        }
        @SuppressWarnings("null")
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        @SuppressWarnings("null")
        UseOnContext ctx = new UseOnContext(player, hand, hit);

        @SuppressWarnings("null")
        BlockState modified = state.getToolModifiedState(ctx, ItemAbilities.HOE_TILL, false);
        return modified != null && modified != state;
    }

    private static boolean hasClearHoeSpace(Level level, BlockPos pos) {
        return level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    @SuppressWarnings("null")
    private boolean tillTile(ServerLevel level, Player player, InteractionHand hand, BlockPos pos) {
        // 与 vanilla hoe 一致：必须有空间
        if (!level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (isPublicYellowDirt(level, pos, state)) {
            return false;
        }
        @SuppressWarnings("null")
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        @SuppressWarnings("null")
        UseOnContext ctx = new UseOnContext(player, hand, hit);

        @SuppressWarnings("null")
        BlockState modified = state.getToolModifiedState(ctx, ItemAbilities.HOE_TILL, false);
        if (modified == null || modified == state) {
            return false;
        }

        level.setBlock(pos, modified, 11);

        // 公共区域耕地才需要登记次日复原；远古斑点恢复基础地块时不登记。
        if (modified.is(Blocks.FARMLAND)
                && com.stardew.craft.core.FarmAreaResolver.isInStardewButNotFarm(level, pos)
                && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos)) {
            com.stardew.craft.manager.CropGrowthManager.get(level).trackPublicTilledChunk(level, pos);
        }

        level.playSound(null, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0f, 1.0f);
        level.levelEvent(2001, pos, Block.getId(state));
        return true;
    }

    /**
     * SDV-parity：
     * - 远古斑点方块 → ArtifactDropService（古物/矿石/粘土等）
     * - 普通黄土/泥土 → 仅粘土 3% / 混合种子 1%
     */
    @SuppressWarnings("null")
    private void rollBuriedDrops(ServerLevel level, BlockPos tilledPos, BlockState preTillState, ServerPlayer player, ItemStack tool) {
        if (preTillState.is(com.stardew.craft.block.ModBlocks.ARTIFACT_SPOT_DIRT.get())
                || preTillState.is(com.stardew.craft.block.ModBlocks.DESERT_ARTIFACT_SPOT.get())
                || preTillState.is(com.stardew.craft.block.ModBlocks.BEACH_ARTIFACT_SPOT.get())) {
            // 远古斑点：完整古物掉落表（SDV ContinueOnDrop 可产出多个物品）
            List<ItemStack> drops = com.stardew.craft.manager.ArtifactDropService.rollAllDrops(level, tilledPos, player);
            if (StardewEnchantments.has(tool, StardewEnchantments.GENEROUS)) {
                drops = new ArrayList<>(drops);
                drops.addAll(com.stardew.craft.manager.ArtifactDropService.rollAllDrops(level, tilledPos, player));
            }
            com.stardew.craft.book.BookAcquisitionService.recordArtifactSpotDugAndMaybeAddDefenseBook(
                    player, drops, level.random);
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    Block.popResource(level, tilledPos.above(), drop);
                }
            }
            return;
        }
        double archaeologistMul = StardewEnchantments.has(tool, StardewEnchantments.ARCHAEOLOGIST) ? 2.0 : 1.0;
        int generousCount = StardewEnchantments.has(tool, StardewEnchantments.GENEROUS) ? 2 : 1;
        if (shouldRollWinterBuriedForage(level, tilledPos)
                && level.random.nextDouble() < 0.08 * archaeologistMul) {
            ItemStack drop = level.random.nextBoolean()
                    ? new ItemStack(ModItems.VANILLA_CATEGORY_ITEMS.get("winter_root").get())
                    : new ItemStack(ModItems.VANILLA_CATEGORY_ITEMS.get("snow_yam").get());
            for (int i = 0; i < generousCount; i++) {
                Block.popResource(level, tilledPos.above(), drop.copy());
            }
            return;
        }
        // 普通锄地：少量概率出粘土/混合种子
        if (level.random.nextDouble() < 0.03 * archaeologistMul) {
            for (int i = 0; i < generousCount; i++) {
                Block.popResource(level, tilledPos.above(), new ItemStack(ModItems.CLAY.get()));
            }
            return;
        }
        if (level.random.nextDouble() < 0.01 * archaeologistMul) {
            for (int i = 0; i < generousCount; i++) {
                Block.popResource(level, tilledPos.above(), new ItemStack(ModItems.MIXED_SEEDS.get()));
            }
        }
    }

    private static boolean shouldRollWinterBuriedForage(ServerLevel level, BlockPos pos) {
        return level.dimension() == ModDimensions.STARDEW_VALLEY
                && StardewTimeManager.get().getCurrentSeason() == 3
                && level.canSeeSky(pos.above())
                && com.stardew.craft.core.FarmAreaResolver.isInAnyFarm(level, pos)
                && !com.stardew.craft.desert.DesertConstants.isInDesertRegion(pos)
                && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos);
    }

    private static boolean isPublicYellowDirt(Level level, BlockPos pos, BlockState state) {
        return state.is(com.stardew.craft.block.ModBlocks.YELLOW_DIRT.get())
                && com.stardew.craft.core.FarmAreaResolver.isInStardewButNotFarm(level, pos)
                && !com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(level, pos);
    }

    @SuppressWarnings("null")
    private static InteractionHand resolveHand(Player player, ItemStack stack) {
        if (ItemStack.isSameItemSameComponents(player.getOffhandItem(), stack)) {
            return InteractionHand.OFF_HAND;
        }
        return InteractionHand.MAIN_HAND;
    }

    @SuppressWarnings("null")
    @Override
    public void appendHoverText(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") TooltipContext context, @SuppressWarnings("null") List<Component> tooltipComponents, @SuppressWarnings("null") TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
