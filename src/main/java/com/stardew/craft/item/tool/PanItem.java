package com.stardew.craft.item.tool;

import com.stardew.craft.communitycenter.reward.panning.OrePanPointManager;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pan tool — 4 upgrade tiers (SDV parity).
 * <p>
 * SDV: {@code new Pan(int upgradeLevel)}; upgradeLevel values 1 (Copper) → 4
 * (Iridium). Visual: SDV always draws tile 12 regardless of tier
 * (Pan.drawInMenu hard-codes IndexOfMenuItemView = 12), so we share the
 * copper_pan.png texture across all four items.
 * <p>
 * Upgrade effects on loot (see {@link #rollLoot}):
 * <ul>
 *   <li>Main ore roll: {@code roll -= (L-1) * 0.05} — better ore tier odds.</li>
 *   <li>{@code orePieces += L - 1}.</li>
 *   <li>Inner extra-item roll: {@code roll -= (L-1) * 0.005}.</li>
 *   <li>{@code numRolls = L} (Iridium = 4 rolls → up to 4 extra item stacks).</li>
 *   <li>{@code extraChance = (L-1) * 0.04} — more continuation rolls.</li>
 * </ul>
 * DoFunction extra pan-point tick (SDV: upgradeLevel-1 additional chances to
 * spawn a new point after panning) is intentionally NOT implemented — scoped
 * out with user approval.
 */
public class PanItem extends Item implements IStardewItem {

    /** SDV Pan "reach" = 4 tiles (Reaching enchantment not modelled). */
    private static final double REACH_TILES = 4.0;

    public enum Tier {
        COPPER(1),
        STEEL(2),
        GOLD(3),
        IRIDIUM(4);

        private final int upgradeLevel;

        Tier(int upgradeLevel) { this.upgradeLevel = upgradeLevel; }
        public int upgradeLevel() { return upgradeLevel; }
    }

    private final Tier tier;

    public PanItem(Tier tier, Properties properties) {
        super(properties.stacksTo(1));
        this.tier = tier;
    }

    public Tier getTier() { return tier; }
    public int getUpgradeLevel() { return tier.upgradeLevel; }

    @Override public String getItemTypeKey() { return "stardewcraft.type.tool"; }
    @Override public int getSellPrice(ItemStack stack) { return -1; }

    @Override public boolean isEnchantable(@javax.annotation.Nonnull ItemStack stack) { return stack.getMaxStackSize() == 1; }
    @Override public int getEnchantmentValue() { return Math.max(1, tier.upgradeLevel() * 5); }

    /**
     * Use duration in ticks — 40 ticks (2 seconds) for all tiers, roughly
     * matching SDV's {@code FarmerSprite.animateOnce(303, 50f, 4)} panning
     * animation (~200ms sprite) padded to feel physical in MC.
     */
    @Override public int getUseDuration(@javax.annotation.Nonnull ItemStack stack, @javax.annotation.Nonnull LivingEntity entity) { return 40; }

    /**
     * Render the "brush" animation — closest vanilla motion to panning
     * (side-to-side wrist motion).
     */
    @Override public UseAnim getUseAnimation(@javax.annotation.Nonnull ItemStack stack) { return UseAnim.BRUSH; }

    @Override
    @SuppressWarnings("null")
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);

        // Do full validation on BOTH sides so client gets correct "swing vs no-swing" feedback.
        double reachTiles = getReachTiles(held);
        BlockHitResult hit = raycastWater(player, reachTiles + 1);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.pass(held);
        }

        BlockPos waterPos = hit.getBlockPos();
        BlockState state = level.getBlockState(waterPos);
        if (state.getFluidState().getType() != Fluids.WATER && state.getFluidState().getType() != Fluids.FLOWING_WATER) {
            return InteractionResultHolder.pass(held);
        }

        if (level.isClientSide) {
            // Client: optimistically start the use-animation; server will confirm on finish.
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(held);
        }

        if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) {
            return InteractionResultHolder.pass(held);
        }

        OrePanPointManager mgr = OrePanPointManager.get(sl);
        BlockPos activePoint = mgr.getPoint(sp.getUUID(), sl);
        if (activePoint == null) {
            sp.sendSystemMessage(Component.translatable("stardewcraft.copper_pan.no_point"));
            return InteractionResultHolder.fail(held);
        }

        double dx = Math.abs(waterPos.getX() + 0.5 - (activePoint.getX() + 0.5));
        double dz = Math.abs(waterPos.getZ() + 0.5 - (activePoint.getZ() + 0.5));
        if (dx > reachTiles || dz > reachTiles) {
            sp.sendSystemMessage(Component.translatable("stardewcraft.copper_pan.too_far"));
            return InteractionResultHolder.fail(held);
        }

        // All checks pass — start the use-animation; loot drops when it completes.
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(held);
    }

    /** Play water-ripple particles each tick while panning (visual feedback). */
    @Override
    @SuppressWarnings("null")
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseTicks) {
        if (level.isClientSide) return;
        if (!(entity instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return;
        // Emit ripple particles every 5 ticks around the player's active pan point.
        int elapsed = getUseDuration(stack, entity) - remainingUseTicks;
        if (elapsed % 5 != 0) return;

        BlockPos point = OrePanPointManager.get(sl).getPoint(sp.getUUID(), sl);
        if (point == null) return;
        sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
            point.getX() + 0.5, point.getY() + 0.9, point.getZ() + 0.5,
            6, 0.25, 0.05, 0.25, 0.02);
        if (elapsed == 5) {
            // Initial slosh sound
            sl.playSound(null, point, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
    }

    /** Finished the full use duration → perform the pan. */
    @Override
    @SuppressWarnings("null")
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (level.isClientSide) return stack;
        if (!(entity instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return stack;

        OrePanPointManager mgr = OrePanPointManager.get(sl);
        BlockPos activePoint = mgr.getPoint(sp.getUUID(), sl);
        if (activePoint == null) return stack; // point moved/cleared during use — no-op

        performPan(sp, sl, activePoint, mgr, stack, getUpgradeLevel());
        return stack;
    }

    @Override
    @SuppressWarnings("null")
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                 List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        String base = "item.stardewcraft." + tier.name().toLowerCase() + "_pan";
        tooltip.add(Component.translatable(base + ".tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(base + ".desc").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Main pan action — roll loot, give items, clear point, grant XP. */
    private static void performPan(ServerPlayer player, ServerLevel level, BlockPos point,
                                    OrePanPointManager mgr, ItemStack pan, int upgradeLevel) {
        int timesPanned = mgr.incrementTimesPanned(player.getUUID());

        int daysPlayed = totalDaysPlayed(level);
        List<ItemStack> loot = rollLoot(point, daysPlayed, player, timesPanned, upgradeLevel, pan);
        int lastMiningXp = LAST_MINING_XP.get()[0];

        // ── 物品发放：生成物品实体从淘金点弹向玩家，拾取时触发 HUD ──
        for (ItemStack stack : loot) {
            if (stack.isEmpty()) continue;
            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                level, point.getX() + 0.5, point.getY() + 1.2, point.getZ() + 0.5, stack.copy());
            double dx = player.getX() - (point.getX() + 0.5);
            double dy = player.getEyeY() - (point.getY() + 1.2);
            double dz = player.getZ() - (point.getZ() + 0.5);
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist > 0.1) {
                double speed = 0.3;
                itemEntity.setDeltaMovement(dx / dist * speed, dy / dist * speed + 0.2, dz / dist * speed);
            }
            itemEntity.setPickUpDelay(0);
            itemEntity.setThrower(player);
            level.addFreshEntity(itemEntity);
        }

        // ── 视觉 + 音效反馈 ──
        // 1. 淘金点：水花 + 气泡粒子
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
            point.getX() + 0.5, point.getY() + 0.9, point.getZ() + 0.5, 30, 0.3, 0.1, 0.3, 0.1);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,
            point.getX() + 0.5, point.getY() + 0.8, point.getZ() + 0.5, 12, 0.2, 0.1, 0.2, 0.05);
        // 2. 淘金点：泼水声
        level.playSound(null, point, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.8f, 1.4f);
        // 3. 玩家位置：收获音效（紫水晶叮 + 经验球拾取音），确保玩家一定听得到
        level.playSound(null, player, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6f, 1.2f);
        level.playSound(null, player, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.3f, 1.0f);
        // SDV Pan.getPanItems: mining XP = orePieces + extraPieces, foraging XP = item count * 7.
        int totalItems = loot.size();
        int miningXp = lastMiningXp;
        if (miningXp > 0) PlayerStardewDataAPI.addExperience(player, SkillType.MINING, miningXp);
        if (totalItems > 0) PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, totalItems * 7);

        mgr.clearPoint(player.getUUID(), level);
        mgr.syncToClient(player);
    }

    // ──────────────── Loot logic — SDV Pan.getPanItems (upgradeLevel-aware) ────────────────

    public static List<ItemStack> rollLoot(BlockPos point, int daysPlayed, ServerPlayer who, int timesPanned, int upgradeLevel) {
        return rollLoot(point, daysPlayed, who, timesPanned, upgradeLevel, ItemStack.EMPTY);
    }

    /** Mining XP (orePieces + extraPieces) of the most recent rollLoot call on this thread. */
    private static final ThreadLocal<int[]> LAST_MINING_XP = ThreadLocal.withInitial(() -> new int[1]);

    public static List<ItemStack> rollLoot(BlockPos point, int daysPlayed, ServerPlayer who, int timesPanned, int upgradeLevel, ItemStack pan) {
        List<ItemStack> items = new ArrayList<>();
        long seed = hashSeed(point.getX(), point.getZ() * 1000L, daysPlayed, (long) timesPanned * 77L);
        Random r = new Random(seed);

        int luckLevel = PlayerStardewDataAPI.getLuckLevel(who);
        double dailyLuck = PlayerStardewDataAPI.getDailyLuck(who);
        int L = Math.max(1, upgradeLevel);

        // Primary ore tier — SDV: roll -= (upgradeLevel - 1) * 0.05
        Item whichOre = ModItems.COPPER_ORE.get();
        double roll = r.nextDouble() - luckLevel * 0.001 - dailyLuck - (L - 1) * 0.05;
        if (roll < 0.01) {
            whichOre = ModItems.IRIDIUM_ORE.get();
        } else if (roll < 0.241) {
            whichOre = ModItems.GOLD_ORE.get();
        } else if (roll < 0.6) {
            whichOre = ModItems.IRON_ORE.get();
        }
        double boneChance = StardewEnchantments.has(pan, StardewEnchantments.ARCHAEOLOGIST) ? 0.20 : 0.10;
        if (whichOre != ModItems.IRIDIUM_ORE.get() && r.nextDouble() < boneChance) {
            whichOre = ModItems.BONE_FRAGMENT.get();
        }

        int orePieces = r.nextInt(2, 7) + 1 + (int) ((r.nextDouble() + 0.1 + (luckLevel / 10f) + dailyLuck) * 2.0);
        int extraPieces = r.nextInt(5) + 1 + (int) ((r.nextDouble() + 0.1 + (luckLevel / 10f)) * 2.0);
        orePieces += (L - 1);  // SDV: orePieces += upgradeLevel - 1

        // Extra item roll loop — SDV: numRolls = upgradeLevel; extraChance = (L-1)*0.04
        int numRolls = L + (StardewEnchantments.has(pan, StardewEnchantments.GENEROUS) ? 2 : 0);
        double extraChance = (L - 1) * 0.04;

        while (r.nextDouble() - dailyLuck < 0.4 + luckLevel * 0.04 + extraChance && numRolls > 0) {
            double rollExtra = r.nextDouble() - dailyLuck - (L - 1) * 0.005;
            Item whichExtra = ModItems.COAL.get();

            if (rollExtra < 0.02 + luckLevel * 0.002 && r.nextDouble() < 0.75) {
                whichExtra = ModItems.DIAMOND.get();
                extraPieces = 1;
            } else if (rollExtra < 0.1 && r.nextDouble() < 0.75) {
                Item[] gems = {
                    ModItems.EMERALD.get(), ModItems.AQUAMARINE.get(),
                    ModItems.RUBY.get(), ModItems.AMETHYST.get(), ModItems.TOPAZ.get()
                };
                whichExtra = gems[r.nextInt(gems.length)];
                extraPieces = 1;
            } else if (rollExtra < 0.36) {
                whichExtra = ModItems.OMNI_GEODE.get();
                extraPieces = Math.max(1, extraPieces / 2);
            } else if (rollExtra < 0.5) {
                Item[] fmz = {
                    ModItems.FIRE_QUARTZ.get(), ModItems.FROZEN_TEAR.get(), ModItems.EARTH_CRYSTAL.get()
                };
                whichExtra = fmz[r.nextInt(fmz.length)];
                extraPieces = 1;
            }

            // SDV mystery box 5% — golden variant after Foraging Mastery.
            com.stardew.craft.player.PlayerStardewData data = PlayerDataManager.getPlayerData(who);
            if (data.hasMailFlag("sawQiPlane") && r.nextDouble() < BookPowerEffects.applyMysteryBoxChance(data, 0.05)) {
                Item box = data.hasMastery(SkillType.FORAGING)
                        ? ModItems.GOLDEN_MYSTERY_BOX.get()
                        : ModItems.MYSTERY_BOX.get();
                items.add(new ItemStack(box));
            }

            if (whichExtra != null && whichExtra != Items.AIR) {
                items.add(new ItemStack(whichExtra, extraPieces));
            }
            if (StardewEnchantments.has(pan, StardewEnchantments.FISHER) && r.nextDouble() < 0.10) {
                items.add(new ItemStack(ModItems.SUNFISH.get()));
            }
            numRolls--;
        }

        // Artifact Trove (275) — SDV: while (r.NextDouble() < 0.05 [+0.15 Archaeologist]) amount++;
        int bonusTrove = 0;
        double bonusTroveChance = StardewEnchantments.has(pan, StardewEnchantments.ARCHAEOLOGIST) ? 0.20 : 0.05;
        while (r.nextDouble() < bonusTroveChance) bonusTrove++;
        if (bonusTrove > 0) {
            items.add(new ItemStack(ModItems.ARTIFACT_TROVE.get(), bonusTrove));
        }

        items.add(new ItemStack(whichOre, orePieces));
        LAST_MINING_XP.get()[0] = orePieces + extraPieces;
        return items;
    }

    private static long hashSeed(long a, long b, long c, long d) {
        long h = a * 0x9E3779B97F4A7C15L;
        h ^= b + 0xBF58476D1CE4E5B9L + (h << 6) + (h >>> 2);
        h ^= c + 0x94D049BB133111EBL + (h << 6) + (h >>> 2);
        h ^= d + 0x2545F4914F6CDD1DL + (h << 6) + (h >>> 2);
        return h;
    }

    private static int totalDaysPlayed(ServerLevel level) {
        com.stardew.craft.time.StardewTimeManager t = com.stardew.craft.time.StardewTimeManager.get();
        return (t.getCurrentYear() - 1) * (28 * 4) + t.getCurrentSeason() * 28 + t.getCurrentDay();
    }

    private static double getReachTiles(ItemStack stack) {
        return REACH_TILES + (StardewEnchantments.has(stack, StardewEnchantments.EXPANSIVE) ? 1.0 : 0.0);
    }

    private static BlockHitResult raycastWater(net.minecraft.world.entity.player.Player player, double reach) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle();
        Vec3 to = eye.add(look.x * reach, look.y * reach, look.z * reach);
        ClipContext ctx = new ClipContext(eye, to,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.ANY,
            player);
        return player.level().clip(ctx);
    }
}
