package com.stardew.craft.event;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.mine.MineBarrelBlock;
import com.stardew.craft.block.mine.MineIceDebrisBlock;
import com.stardew.craft.block.mine.MineGroundWeedsBlock;
import com.stardew.craft.combat.StardewWeaponAreaAttack;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.WeaponType;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.item.weapon.IStardewWeapon;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative, one-hit mine cache and ice breaking. Cache cells resolve to one loot owner. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MineBarrelBreakHandler {
    private static final Map<ServerPlayer, Double> READY_TICKS = new WeakHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) trySwing(player, null);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !canSwing(player)
                || !(player.level().getBlockState(event.getPos()).getBlock() instanceof MineBarrelBlock
                || player.level().getBlockState(event.getPos()).getBlock() instanceof MineIceDebrisBlock
                || player.level().getBlockState(event.getPos()).getBlock() instanceof MineGroundWeedsBlock)) return;
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START)
            trySwing(player, event.getPos());
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        // Vanilla's server animation packet updates these fields even for a left-click miss.
        // Do not add a client-controlled radius/position or mutate skill target queries.
        if (event.getEntity() instanceof ServerPlayer player && player.swinging
                && player.swingingArm == InteractionHand.MAIN_HAND && player.swingTime == 1)
            trySwing(player, null);
    }

    private static boolean canSwing(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()
                || WeaponSkillAnimationLock.isLocked(player, player.level().getGameTime())) return false;
        Item item = player.getMainHandItem().getItem();
        if (item instanceof IStardewWeapon) return WeaponStats.fromItemStack(player.getMainHandItem()).getWeaponType() != WeaponType.SLINGSHOT;
        return item instanceof SwordItem || item instanceof AxeItem || item instanceof PickaxeItem || item instanceof HoeItem;
    }

    private static void trySwing(ServerPlayer player, BlockPos forcedPos) {
        if (!canSwing(player)) return;
        long now = player.level().getGameTime();
        if (now < READY_TICKS.getOrDefault(player, Double.NEGATIVE_INFINITY)) return;
        double interval = Math.max(1, player.getCurrentItemAttackStrengthDelay());
        if (player.getMainHandItem().getItem() instanceof IStardewWeapon) {
            var stats = WeaponStats.fromItemStack(player.getMainHandItem());
            float speed = com.stardew.craft.combat.equipment.EquipmentResolver.getMergedStats(player).getWeaponSpeedMultiplier();
            interval = com.stardew.craft.combat.StardewWeaponSpeedRules.repeatMillisecondsFromRawSpeed(
                    stats.getWeaponType(), stats.getRawSpeed(), speed + stats.getWeaponSpeedMultiplier()) / 50.0;
        }
        READY_TICKS.put(player, now + Math.max(1, interval));
        breakNearbyBarrels(player.serverLevel(), player, forcedPos);
    }

    public static void breakNearbyBarrels(ServerLevel level, ServerPlayer player, BlockPos forcedPos) {
        var type = player.getMainHandItem().getItem() instanceof IStardewWeapon
                ? WeaponStats.fromItemStack(player.getMainHandItem()).getWeaponType() : WeaponType.SWORD;
        Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 origin = player.getBoundingBox().getCenter();
        breakInVolume(player, player.getBoundingBox().inflate(2.5), player.getEyePosition(), center -> {
            if (forcedPos != null && BlockPos.containing(center).equals(forcedPos)
                    && player.canInteractWithBlock(forcedPos, 0)) return true;
            Vec3 offset = center.subtract(origin);
            return StardewWeaponAreaAttack.contains(type, offset.dot(forward), offset.dot(right), offset.y);
        });
    }

    /** Call only at an authored skill's damage frame, with that strike's actual volume. */
    public static void breakInVolume(ServerPlayer player, AABB bounds, Vec3 origin, Predicate<Vec3> contains) {
        if (!player.isAlive() || player.isSpectator()) return;
        if (com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime.deferIfPreparing(
                () -> breakInVolume(player, bounds, origin, contains))) return;
        ServerLevel level = player.serverLevel();
        // The same authored melee area can intercept source projectiles, including swings into air.
        if (player.getMainHandItem().getItem() instanceof IStardewWeapon
                || player.getMainHandItem().getItem() instanceof SwordItem) {
            for (var bone : level.getEntitiesOfClass(com.stardew.craft.entity.projectile.SkeletonBoneEntity.class, bounds)) {
                Vec3 center = bone.getBoundingBox().getCenter();
                if (contains.test(center) && level.clip(new ClipContext(origin, center,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS)
                    bone.breakByWeapon();
            }            for (var bone : level.getEntitiesOfClass(com.stardew.craft.entity.projectile.ShamanCurseEntity.class, bounds)) {
                Vec3 center = bone.getBoundingBox().getCenter();
                if (contains.test(center) && level.clip(new ClipContext(origin, center,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS)
                    bone.breakByWeapon();
            }            for (var bone : level.getEntitiesOfClass(com.stardew.craft.entity.projectile.SquidFireballEntity.class, bounds)) {
                Vec3 center = bone.getBoundingBox().getCenter();
                if (contains.test(center) && level.clip(new ClipContext(origin, center,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.MISS)
                    bone.breakByWeapon();
            }
        }
        var mains = new LinkedHashSet<BlockPos>();
        for (BlockPos cell : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            var state = level.getBlockState(cell);
            if (!(state.getBlock() instanceof MineBarrelBlock) && !(state.getBlock() instanceof MineIceDebrisBlock)
                    && !(state.getBlock() instanceof MineGroundWeedsBlock)) continue;
            Vec3 center = Vec3.atCenterOf(cell);
            if (!contains.test(center)) continue;
            BlockPos main = state.getBlock() instanceof MineBarrelBlock block ? block.findMainPos(level, cell, state) : cell;
            if (main == null) continue;
            if (!player.isCreative() && level.dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                    && !com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(player, main)) continue;
            var hit = level.clip(new ClipContext(origin, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                var hitState = level.getBlockState(hit.getBlockPos());
                BlockPos hitMain = hitState.getBlock() instanceof MineBarrelBlock hitBlock
                        ? hitBlock.findMainPos(level, hit.getBlockPos(), hitState)
                        : (hitState.getBlock() instanceof MineIceDebrisBlock || hitState.getBlock() instanceof MineGroundWeedsBlock) ? hit.getBlockPos() : null;
                if (!main.equals(hitMain)) continue;
            }
            mains.add(main.immutable());
        }
        for (BlockPos main : mains) {
            if (level.getBlockState(main).getBlock() instanceof MineIceDebrisBlock) MineIceDebrisBlock.breakBy(level, main, player);
            else if (level.getBlockState(main).getBlock() instanceof MineGroundWeedsBlock) MineGroundWeedsBlock.breakBy(level, main, player, true);
            else MineBarrelBlock.breakBy(level, main, player);
        }
    }

    public static void breakInArc(ServerPlayer player, double range, double minimumDot) {
        Vec3 origin = player.getEyePosition(), look = player.getLookAngle().normalize();
        breakInVolume(player, player.getBoundingBox().inflate(range, range * .75, range), origin, center -> {
            Vec3 to = center.subtract(origin);
            return to.lengthSqr() <= range * range && to.normalize().dot(look) >= minimumDot;
        });
    }
}
