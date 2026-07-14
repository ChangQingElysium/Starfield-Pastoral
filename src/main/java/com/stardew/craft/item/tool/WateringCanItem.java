package com.stardew.craft.item.tool;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.block.crop.DeadCropBlock;
import com.stardew.craft.block.crop.StardewCropBlock;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.context.UseOnContext;
import javax.annotation.Nonnull;

public class WateringCanItem extends Item implements IStardewItem {

    // 0.35s @ 20tps
    private static final int WATERING_COOLDOWN_TICKS = 7;
    private static final int REFILL_TICKS = 20;
    private static final String TAG_ACTION = "StardewAction";
    private static final int ACTION_WATER = 1;
    private static final int ACTION_REFILL = 2;

    public enum Tier {
        STARTER(40, 0),
        COPPER(55, 1),
        STEEL(70, 2),
        GOLD(85, 3),
        IRIDIUM(100, 4);

        final int capacity;
        final int maxChargeLevel;

        Tier(int capacity, int maxChargeLevel) {
            this.capacity = capacity;
            this.maxChargeLevel = maxChargeLevel;
        }

        public int getCapacity() {
            return capacity;
        }

        public int getMaxChargeLevel() {
            return maxChargeLevel;
        }
    }

    private final Tier tier;

    public WateringCanItem(Tier tier, Properties properties) {
        // 设置最大耐久度为容量
        super(properties.durability(tier.capacity).setNoRepair());
        this.tier = tier;
    }
    
    @Override
    @SuppressWarnings("null")
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null) {
            if (player.getCooldowns().isOnCooldown(this)) {
                return InteractionResult.FAIL;
            }
            @Nonnull Level level = context.getLevel();
            @Nonnull ItemStack stack = context.getItemInHand();
            @Nonnull InteractionHand hand = context.getHand();

            // 对着水/水炼药锅右键：优先进入“汲水蓄力”
            // 注意：UseOnContext 的 clickedPos 可能会“穿透流体”命中后方方块，因此这里用 POV 射线重新确认。
            BlockHitResult povHit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
            if (povHit.getType() == HitResult.Type.BLOCK) {
                @Nonnull BlockPos pos = povHit.getBlockPos();
                if (isWaterSource(level, pos) || level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
                    if (getWater(stack) < tier.capacity) {
                        setAction(stack, ACTION_REFILL);
                        player.startUsingItem(hand);
                        return InteractionResult.CONSUME;
                    }
                    return InteractionResult.FAIL;
                }
            }

            // 兼容：如果确实点到了水炼药锅方块本体（射线没命中到它），也允许汲水
            @Nonnull BlockPos clickedPos = context.getClickedPos();
            if (level.getBlockState(clickedPos).is(Blocks.WATER_CAULDRON)) {
                if (getWater(stack) < tier.capacity) {
                    setAction(stack, ACTION_REFILL);
                    player.startUsingItem(hand);
                    return InteractionResult.CONSUME;
                }
                return InteractionResult.FAIL;
            }

            // 其他情况：开始蓄力洒水
            if (getWater(stack) <= 0 && !player.isCreative()) {
                if (!level.isClientSide) {
                    @Nonnull Component message = Component.translatable("stardewcraft.message.tool.empty");
                    player.displayClientMessage(message, true);
                }
                return InteractionResult.FAIL;
            }

            setAction(stack, ACTION_WATER);
            player.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    public Tier getTier() {

        return tier;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.tool";
    }

    @Override
    public boolean isEnchantable(@Nonnull ItemStack stack) {
        return stack.getMaxStackSize() == 1;
    }

    @Override
    public int getEnchantmentValue() {
        return Math.max(1, (tier.getMaxChargeLevel() + 1) * 5);
    }

    // ================= 使用逻辑 =================

    @Override
    public int getUseDuration(@Nonnull ItemStack stack, @Nonnull LivingEntity entity) {
        return 72000; // 让物品可以一直按着
    }

    @Override
    public UseAnim getUseAnimation(@Nonnull ItemStack stack) {
        return UseAnim.BOW; // 使用弓的动作（举起手）
    }

    @Override
    @SuppressWarnings("null")
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        @Nonnull ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        
        // 1) 对着水源：优先进入“汲水蓄力”，不允许秒加满。
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hitResult.getBlockPos();
            if (isWaterSource(level, pos) || level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
                if (getWater(stack) < tier.capacity) {
                    setAction(stack, ACTION_REFILL);
                    player.startUsingItem(hand);
                    return InteractionResultHolder.consume(stack);
                }
                return InteractionResultHolder.fail(stack);
            }
        }

        // 2) 没水不能洒水

        if (getWater(stack) <= 0 && !player.isCreative()) {
            if (!level.isClientSide) {
                @Nonnull Component message = Component.translatable("stardewcraft.message.tool.empty");
                player.displayClientMessage(message, true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 3) 开始蓄力洒水
        setAction(stack, ACTION_WATER);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    @SuppressWarnings("null")
    public void releaseUsing(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull LivingEntity entity, int timeCharged) {
        if (!(entity instanceof Player player)) return;

        int action = getAction(stack);
        // 汲水是“按住 1 秒自动完成”，不依赖 release。
        if (action == ACTION_REFILL) {
            clearAction(stack);
            return;
        }

        int useDuration = this.getUseDuration(stack, entity) - timeCharged;
        int chargeLevel = getChargeLevel(stack, useDuration);

        // 能量为 0 时：拦截耗能动作（仅星露谷维度）。
        if (!level.isClientSide
            && player instanceof ServerPlayer serverPlayer
            && !player.isCreative()
            && player.level().dimension() == ModDimensions.STARDEW_VALLEY) {
            if (PlayerStardewDataAPI.getEnergy(serverPlayer) <= 0.0f) {
                @Nonnull Component message = Component.translatable("stardewcraft.message.player.exhausted");
                player.displayClientMessage(message, true);
                return;
            }
        }

        // 获取作用范围
        BlockHitResult hitResult = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            @Nonnull BlockPos hitPos = hitResult.getBlockPos();
            hitPos = resolveWateringTarget(level, hitPos);

            List<BlockPos> targetPositions = getAffectedBlocks(level, hitPos, player, chargeLevel);

            // 农场保护：在别人农场上无权操作（温室内部豁免，温室是合法种植区域）。
            // 蓄力流程由 Item.use(air) → startUsingItem 启动，完全绕过 RightClickBlock 事件，
            // 因此在这里按每个目标格过滤，若全部被过滤则提示并返回。
            if (!level.isClientSide && player instanceof ServerPlayer sp
                    && level.dimension() == ModDimensions.STARDEW_VALLEY
                    && !sp.isCreative()) {
                int before = targetPositions.size();
                targetPositions = new java.util.ArrayList<>(targetPositions);
                targetPositions.removeIf(pos -> {
                    if (level instanceof net.minecraft.server.level.ServerLevel sl
                            && com.stardew.craft.greenhouse.GreenhouseManager.isInGreenhouseInterior(sl, pos)) {
                        return !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyGreenhouseAt(sp, sl, pos);
                    }
                    return !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(sp, pos);
                });
                if (targetPositions.isEmpty() && before > 0) {
                    sp.displayClientMessage(
                            Component.translatable("stardewcraft.farm.build_farm_only"), true);
                    return;
                }
            }

            boolean wateredAny = false;

            // 原版(星露谷)手感：每“浇到一格”消耗 1 点水；蓄力只是改变浇到的格子数量。
            // 如果水不够，则只浇前面的若干格（而不是一次性扣一大段）。
            boolean bottomless = isBottomless(stack);
            int waterLeft = (player.isCreative() || bottomless) ? Integer.MAX_VALUE : getWater(stack);
            for (BlockPos pos : targetPositions) {
                if (!player.isCreative() && !bottomless && waterLeft <= 0) {
                    break;
                }

                if (waterTile(level, pos)) {
                    wateredAny = true;
                    if (!player.isCreative() && !bottomless) {
                        waterLeft -= 1;
                    }

                    // 粒子效果
                    if (level.isClientSide) {
                        for (int i = 0; i < 5; ++i) {
                            @Nonnull ParticleOptions splash = ParticleTypes.SPLASH;
                            level.addParticle(splash, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 0, 0, 0);
                        }
                    }
                }
            }

            if (wateredAny) {
                // 扣水
                if (!player.isCreative() && !bottomless) {
                    setWater(stack, Math.max(0, waterLeft));
                    
                    // 扣除能量 (Stamina)
                    if (!level.isClientSide && player instanceof ServerPlayer serverPlayer
                            && !StardewEnchantments.has(stack, StardewEnchantments.EFFICIENT)) {
                        int farmingLevel = PlayerStardewDataAPI.getSkillLevel(serverPlayer, SkillType.FARMING);
                        // 原版公式: Stamina -= (2 * (power + 1)) - (FarmingLevel * 0.1)
                        float staminaCost = (2.0f * (chargeLevel + 1)) - (farmingLevel * 0.1f);
                        PlayerStardewDataAPI.consumeEnergy(serverPlayer, staminaCost);
                    }
                }
                
                // 播放浇水音效
                @Nonnull SoundEvent splashSound = SoundEvents.GENERIC_SPLASH;
                level.playSound(null, player.blockPosition(), splashSound, SoundSource.PLAYERS, 0.5f, 1.5f);

                // 洒水后增加一个很短的冷却，期间不得再次洒水
                player.getCooldowns().addCooldown(this, WATERING_COOLDOWN_TICKS);
            }
        }

        clearAction(stack);
    }
    

    // ================= 核心逻辑 =================

    @Override
    @SuppressWarnings("null")
    public void onUseTick(@Nonnull Level level, @Nonnull LivingEntity livingEntity, @Nonnull ItemStack stack, int remainingUseDuration) {
        if (!(livingEntity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack, livingEntity) - remainingUseDuration;

        int action = getAction(stack);
        if (action == ACTION_REFILL) {
            // 汲水：按住约 1 秒后才装满；中途松开即打断不生效
            if (!level.isClientSide && usedTicks >= REFILL_TICKS) {
                BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);
                if (hit.getType() == HitResult.Type.BLOCK) {
                    @Nonnull BlockPos pos = hit.getBlockPos();
                    if (isWaterSource(level, pos) || level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) {
                        if (getWater(stack) < tier.capacity) {
                            setWater(stack, tier.capacity);
                            @Nonnull SoundEvent fillSound = SoundEvents.BUCKET_FILL;
                            level.playSound(null, player.getX(), player.getY(), player.getZ(), fillSound, SoundSource.PLAYERS, 1.0F, 1.0F);
                        }
                    }
                }

                clearAction(stack);
                player.stopUsingItem();
            }
            return;
        }
        
        // 每 20 ticks (1秒) 提升一级
        // 星露谷原版大约是 600ms - 800ms，这里调整为 15 ticks (0.75s) 让手感更紧凑
        int ticksPerLevel = StardewEnchantments.has(stack, StardewEnchantments.SWIFT) ? 10 : 15;
        
        if (usedTicks > 0 && usedTicks % ticksPerLevel == 0) {
            int currentLevel = usedTicks / ticksPerLevel;
            
            // 只有在未达到最高等级时才播放音效，或者每级都播？
            // 星露谷里每升一级都有音效，直到最高级
            int maxChargeLevel = getEffectiveMaxChargeLevel(stack);
            if (currentLevel <= maxChargeLevel) {
                 // 使用音符盒声音模拟 'Ding'
                 // 音调随等级升高
                 float pitch = 0.8f + (currentLevel * 0.2f);
                 @Nonnull SoundEvent chargeSound = SoundEvents.NOTE_BLOCK_CHIME.value();
                 level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                     chargeSound, SoundSource.PLAYERS, 0.5f, pitch);
            }
        }
    }

    /**
     * 计算当前的蓄力等级 (0 - Max)
     * 根据按住的时间长短
     */
    public int getChargeLevel(int ticksUsed) {
        return getChargeLevel(ItemStack.EMPTY, ticksUsed);
    }

    public int getChargeLevel(ItemStack stack, int ticksUsed) {
        // 与 onUseTick 保持一致
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
     * 执行浇水逻辑
     */
    @SuppressWarnings("null")
    private boolean waterTile(@Nonnull Level level, @Nonnull BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        
        // 1. 耕地
        if (state.getBlock() instanceof FarmBlock) {
            int moisture = state.getValue(FarmBlock.MOISTURE);
            if (moisture < 7) {
                if (!level.isClientSide) {
                    level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, 7), 3);
                }
                return true;
            }
            // 已经是湿的也可以视为“浇到了”，为了视觉反馈一致性
            return true; 
        }
        
        // 2. 将泥土变成耕地？ 不，那是锄头的功能。
        // 水壶只能让耕地变湿。
        
        return false;
    }

    /**
     * 根据蓄力等级和朝向计算受影响的方块列表
     */
    @SuppressWarnings("null")
    public List<BlockPos> getAffectedBlocks(@Nonnull Level level, @Nonnull BlockPos startPos,
                                            @Nonnull Player player, int chargeLevel) {
        startPos = resolveWateringTarget(level, startPos);

        List<BlockPos> list = new ArrayList<>();
        Direction facing = player.getDirection();
        
        // 0级 (1x1)
        if (chargeLevel == 0) {
            list.add(startPos);
        } else if (chargeLevel == 1) { // 铜 (1x3 直线)
            for (int i = 0; i < 3; i++) {
                list.add(startPos.relative(facing, i));
            }
        } else if (chargeLevel == 2) { // 铁 (1x5 直线)
            for (int i = 0; i < 5; i++) {
                list.add(startPos.relative(facing, i));
            }
        } else if (chargeLevel == 3) { // 金 (3x3 区域)
            // 中心是 startPos 往前方偏移 1 格
            BlockPos center = startPos.relative(facing, 1);
            // 遍历 3x3
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    // 只添加同一Y平面上的方块
                    BlockPos target = center.offset(x, 0, z);
                    if (target.getY() == startPos.getY()) {
                        list.add(target);
                    }
                }
            }
        } else if (chargeLevel == 4) { // 铱 (3x6 区域)
            // 3x6 = 3宽 6长
            // 宽度方向
            Direction right = facing.getClockWise();
            Direction left = facing.getCounterClockWise();
            
            for (int forward = 0; forward < 6; forward++) {
                BlockPos base = startPos.relative(facing, forward);
                
                // 确保base在同一Y平面 (relative通常不会改变Y，除非facing是上下，但玩家通常水平操作)
                if (base.getY() != startPos.getY()) continue;
                
                list.add(base); // 中间
                
                BlockPos rPos = base.relative(right);
                if (rPos.getY() == startPos.getY()) list.add(rPos);
                
                BlockPos lPos = base.relative(left);
                if (lPos.getY() == startPos.getY()) list.add(lPos);
            }
        } else if (chargeLevel >= 5) { // Expansive (5x5，以瞄准方块为中心)
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    list.add(startPos.offset(dx, 0, dz));
                }
            }
        } else {
            // 默认 fallback
            list.add(startPos);
        }
        
        // 过滤被阻挡的方块
        // 规则：如果耕地上方有方块且有碰撞体积（如石头、栅栏），则不能浇水
        // 排除：作物（stardew crop）和空气没有碰撞体积，可以浇水
        list.removeIf(pos -> {
             BlockPos abovePos = pos.above();
             BlockState aboveState = level.getBlockState(abovePos);
               // 作物长在耕地上时不应当阻挡洒水/预览
               if (aboveState.getBlock() instanceof com.stardew.craft.block.crop.StardewCropBlock) return false;
               if (aboveState.getBlock() instanceof com.stardew.craft.block.crop.DeadCropBlock) return false;
             return !aboveState.getCollisionShape(level, abovePos).isEmpty();
        });

        return list;
    }

    private static BlockPos resolveWateringTarget(Level level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof FarmBlock) {
            return pos;
        }

        BlockPos below = pos.below();
        if (level.getBlockState(below).getBlock() instanceof FarmBlock) {
            return below;
        }

        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof StardewCropBlock || state.getBlock() instanceof DeadCropBlock) {
            BlockPos cursor = below;
            for (int i = 0; i < 3; i++) {
                BlockState cursorState = level.getBlockState(cursor);
                if (cursorState.getBlock() instanceof FarmBlock) {
                    return cursor;
                }
                if (!(cursorState.getBlock() instanceof StardewCropBlock)
                        && !(cursorState.getBlock() instanceof DeadCropBlock)) {
                    break;
                }
                cursor = cursor.below();
            }
        }

        return pos;
    }

    // ================= 水量/耐久显示 =================

    public int getWater(ItemStack stack) {
        if (isBottomless(stack)) {
            return tier.capacity;
        }
        // 由于耐久度是反着来的 (Damage 0 = Full, Damage Max = Empty)
        // Water = MaxDamage - Damage
        return stack.getMaxDamage() - stack.getDamageValue();
    }

    public void setWater(ItemStack stack, int amount) {
        if (isBottomless(stack)) {
            stack.setDamageValue(0);
            return;
        }
        // Damage = MaxDamage - Water
        int damage = stack.getMaxDamage() - amount;
        stack.setDamageValue(damage);
    }

    public boolean isBottomless(ItemStack stack) {
        return StardewEnchantments.has(stack, StardewEnchantments.BOTTOMLESS);
    }

    @Override
    public boolean isBarVisible(@Nonnull ItemStack stack) {
        // 即使是满水也要显示蓄水条
        return true;
    }
    
    @Override
    public int getBarColor(@Nonnull ItemStack stack) {
        if (isBottomless(stack)) {
            return 0xC060FF;
        }
        return 0x44AAFF; 
    }

    @Override
    public int getBarWidth(@Nonnull ItemStack stack) {
        if (isBottomless(stack)) {
            return 13;
        }
        return super.getBarWidth(stack);
    }
    
    // 即使是满的也始终显示条，或者仅在不满时显示？原版逻辑是 stack.isDamaged() 才显示。
    // 这意味着满水时不显示条，用了一次显示一点点空。符合逻辑。

    @Override
    @SuppressWarnings("null")
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
                                @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    @SuppressWarnings("null")
    private static boolean isWaterSource(@Nonnull Level level, @Nonnull BlockPos pos) {
        // 允许从任意水(含流动水)汲水，符合星露谷手感
        var fluidState = level.getFluidState(pos);
        if (fluidState.is(FluidTags.WATER)) return true;
        // 蓄水池方块也视为水源
        return level.getBlockState(pos).getBlock() instanceof com.stardew.craft.block.decor.ReservoirBlock;
    }

    @SuppressWarnings("null")
    private static int getAction(@Nonnull ItemStack stack) {
        return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY)
                .copyTag()
                .getInt(TAG_ACTION);
    }

    @SuppressWarnings("null")
    private static void setAction(@Nonnull ItemStack stack, int action) {
        var current = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        current.putInt(TAG_ACTION, action);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(current));
    }

    @SuppressWarnings("null")
    private static void clearAction(@Nonnull ItemStack stack) {
        var current = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        current.remove(TAG_ACTION);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(current));
    }
}
