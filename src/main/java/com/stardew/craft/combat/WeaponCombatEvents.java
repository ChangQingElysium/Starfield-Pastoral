package com.stardew.craft.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.combat.skill.handler.HolySmiteSkillHandler;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.SilverSaberFoldbackState;
import com.stardew.craft.combat.skill.SteelSpineFuryState;
import com.stardew.craft.combat.skill.DragonBreathTracker;
import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.combat.skill.InsectEyeStanceTracker;
import com.stardew.craft.combat.skill.SilverSaberSkillHelper;
import com.stardew.craft.combat.skill.ObsidianResonanceTracker;
import com.stardew.craft.combat.skill.DarkSwordBloodDebtTracker;
import com.stardew.craft.combat.skill.DarkSwordBloodMoonTracker;
import com.stardew.craft.combat.skill.DarkSwordEffects;
import com.stardew.craft.combat.skill.DragontoothShivBreathTracker;
import com.stardew.craft.combat.skill.CrystalDaggerLayerTracker;
import com.stardew.craft.combat.skill.OssifiedMarkTracker;
import com.stardew.craft.combat.skill.GalaxyDaggerMarkTracker;
import com.stardew.craft.combat.skill.InfinityDaggerMarkTracker;
import com.stardew.craft.combat.skill.ElfBladeTracker;
import com.stardew.craft.combat.skill.HolyBladeDodgeTracker;
import com.stardew.craft.combat.skill.HolyBladeEffects;
import com.stardew.craft.combat.skill.TemperedQuenchTracker;
import com.stardew.craft.combat.skill.TideMarkTracker;
import com.stardew.craft.combat.skill.handler.TemperedQuenchSkillHandler;
import com.stardew.craft.combat.skill.YetiToothMarkTracker;
import com.stardew.craft.combat.skill.YetiToothEffects;
import com.stardew.craft.combat.skill.IridiumNeedleCritTracker;
import com.stardew.craft.combat.skill.IridiumNeedleFrenzyTracker;
import com.stardew.craft.combat.network.BurglarShankLootPayload;
import com.stardew.craft.combat.network.DamageNumberPayload;
import com.stardew.craft.combat.network.CrystalDaggerBurstPayload;
import com.stardew.craft.combat.network.SteelSpineFuryStrikePayload;
import com.stardew.craft.enchantment.StardewEnchantments;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponRegistry;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.minecraft.world.phys.Vec3;
import java.util.Objects;
import java.util.UUID;


@EventBusSubscriber(modid = StardewCraft.MODID)
public class WeaponCombatEvents {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        long nowTick = player.level().getGameTime();
        if (WeaponSkillAnimationLock.isLocked(player, nowTick)
            && !WeaponSkillContextStore.hasPending(player, nowTick)) {
            event.setCanceled(true);
            return;
        }

        // 别人的农场：仅访问权限不可攻击实体
        if (!player.level().isClientSide
                && player instanceof net.minecraft.server.level.ServerPlayer sp
                && !sp.isCreative()
                && sp.level().dimension() == com.stardew.craft.core.ModDimensions.STARDEW_VALLEY) {
            net.minecraft.core.BlockPos targetPos = event.getTarget().blockPosition();
            if (com.stardew.craft.event.FarmAreaProtectionEvents.isOnProtectedFarm(sp, targetPos)) {
                event.setCanceled(true);
                sp.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable("stardewcraft.farm.build_farm_only"), true);
                return;
            }
        }

        if (player.level().isClientSide) {
            return;
        }

        if (!(event.getTarget() instanceof LivingEntity target)) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IStardewWeapon weaponItem)) {
            return;
        }

        AttackTargetTracker.record(player, target, nowTick);

        if (!SilverSaberFoldbackState.isActive(player, nowTick)) {
            return;
        }

        WeaponData data = weaponItem.getWeaponData();
        if (data == null || data.getSkill1() == null) {
            return;
        }

        WeaponSkillData skill = data.getSkill1();
        if (!"silver_foldback".equals(skill.getId())) {
            return;
        }

        event.setCanceled(true);

        // 折返中左键：攻击 + 不返�?+ 进入冷却
        SilverSaberSkillHelper.executeStayStrike(player, target, weaponItem.getWeaponId(), skill, nowTick);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return;
        }

        if (!SilverSaberFoldbackState.isActiveRaw(player)) {
            long nowTick = player.level().getGameTime();
            return;
        }

        long nowTick = player.level().getGameTime();
        long endTick = SilverSaberFoldbackState.getEndTick(player);
        if (nowTick <= endTick) {
            return;
        }

        // 折返状态超时：清除状�?+ 进入冷却
        String weaponId = SilverSaberFoldbackState.getWeaponId(player);
        if (weaponId == null || weaponId.isEmpty()) {
            SilverSaberSkillHelper.exitFoldbackState(player);
            return;
        }
        WeaponData data = WeaponRegistry.get(weaponId);
        if (data == null || data.getSkill1() == null) {
            SilverSaberSkillHelper.exitFoldbackState(player);
            return;
        }
        WeaponSkillData skill = data.getSkill1();
        if (!"silver_foldback".equals(skill.getId())) {
            SilverSaberSkillHelper.exitFoldbackState(player);
            return;
        }
        SilverSaberSkillHelper.handleTimeout(player, weaponId, skill, nowTick);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        long nowTick = player.level().getGameTime();
        if (WeaponSkillAnimationLock.isLocked(player, nowTick)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        long nowTick = player.level().getGameTime();
        if (WeaponSkillAnimationLock.isLocked(player, nowTick)) {
            event.setNewSpeed(0.0F);
        }
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onLivingHurt(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;

        long nowTick = target.level().getGameTime();
        WeaponSkillContextStore.PendingHit pendingHit =
                WeaponSkillContextStore.consumePending(player, nowTick);
        WeaponDamageSnapshot releaseWeapon = pendingHit == null
                ? null
                : pendingHit.weaponSnapshot().orElse(null);
        ItemStack weapon = releaseWeapon != null
                ? releaseWeapon.weapon()
                : player.getMainHandItem();
        if (!(weapon.getItem() instanceof IStardewWeapon)) return;
        boolean isSweepSource = isSweepDamageSource(event.getSource());

        SkillContext skillContext = pendingHit != null
                ? pendingHit.skillContext()
                : SkillContext.normalAttack();
        WeaponDamageSnapshot damageWeaponSnapshot = releaseWeapon != null
                ? releaseWeapon
                : WeaponDamageSnapshot.capture(
                        net.minecraft.resources.ResourceLocation
                                .fromNamespaceAndPath(
                                        StardewCraft.MODID,
                                        ((IStardewWeapon) weapon.getItem())
                                                .getWeaponId()
                                ),
                        weapon
                );

        if ("normal".equals(skillContext.getSkillId()) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            SkillContext stanceContext = InsectEyeStanceTracker.getSkillContext(serverPlayer, nowTick);
            if (stanceContext != null) {
                skillContext = stanceContext;
            }
        }

        SteelSpineFuryState.AttackBoost spineBoost = null;
        if ("normal".equals(skillContext.getSkillId()) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            spineBoost = SteelSpineFuryState.consumeAttack(serverPlayer, nowTick);
            if (spineBoost != null) {
                String boostSkillId = spineBoost.strong() ? "steel_spine_fury" : "steel_spine_fury_weak";
                skillContext = SkillContext.builder()
                    .skillId(boostSkillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(spineBoost.damageMultiplier())
                    .build();
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && weapon.getItem() instanceof IStardewWeapon IStardewWeapon
                && "crystal_dagger".equals(IStardewWeapon.getWeaponId())) {
            int stacks = CrystalDaggerLayerTracker.getStacks(serverPlayer, nowTick);
            if (stacks > 0) {
                float bonus = stacks * 0.02f;
                skillContext = SkillContext.builder()
                    .skillId(skillContext.getSkillId())
                    .tier(skillContext.getTier())
                    .damageMultiplier(skillContext.getDamageMultiplier())
                    .ignoreDefense(skillContext.isIgnoreDefense())
                    .guaranteedCrit(skillContext.isGuaranteedCrit())
                    .critChanceBonus(skillContext.getCritChanceBonus() + bonus)
                    .build();
            }
        }

        if ("normal".equals(skillContext.getSkillId()) && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            if (weapon.getItem() instanceof IStardewWeapon IStardewWeapon
                && "dragontooth_cutlass".equals(IStardewWeapon.getWeaponId())) {
                int stacks = DragonBreathTracker.getStacks(serverPlayer);
                if (stacks > 0) {
                    float multiplier = 1.0f + (stacks * 0.01f);
                    skillContext = SkillContext.builder()
                        .skillId("normal")
                        .tier(SkillContext.SkillTier.NORMAL)
                        .damageMultiplier(multiplier)
                        .build();
                }
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && weapon.getItem() instanceof IStardewWeapon IStardewWeapon
                && "dragontooth_shiv".equals(IStardewWeapon.getWeaponId())) {
            boolean stanceActive = DragontoothShivBreathTracker.isActive(serverPlayer, nowTick);
            boolean backstab = isBackstab(player, target);
            if (stanceActive || backstab) {
                String newSkillId = skillContext.getSkillId();
                if (stanceActive && "normal".equals(newSkillId)) {
                    newSkillId = "dragontooth_shiv_breath";
                }
                skillContext = SkillContext.builder()
                    .skillId(newSkillId)
                    .tier(skillContext.getTier())
                    .damageMultiplier(skillContext.getDamageMultiplier())
                    .ignoreDefense(skillContext.isIgnoreDefense())
                    .guaranteedCrit(true)
                    .critChanceBonus(skillContext.getCritChanceBonus())
                    .build();
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && weapon.getItem() instanceof IStardewWeapon IStardewWeapon
                && "iridium_needle".equals(IStardewWeapon.getWeaponId())) {
            boolean frenzyActive = IridiumNeedleFrenzyTracker.isActive(serverPlayer, nowTick);
            boolean forceCrit = IridiumNeedleCritTracker.shouldGuaranteeCrit(serverPlayer);
            if (frenzyActive || forceCrit) {
                String nextSkillId = skillContext.getSkillId();
                if (frenzyActive && "normal".equals(nextSkillId)) {
                    nextSkillId = "iridium_needle_frenzy";
                }
                float critBonus = skillContext.getCritChanceBonus()
                        + (frenzyActive ? IridiumNeedleFrenzyTracker.CRIT_CHANCE_BONUS : 0.0F);
                skillContext = SkillContext.builder()
                    .skillId(nextSkillId)
                    .tier(skillContext.getTier())
                    .damageMultiplier(skillContext.getDamageMultiplier())
                    .ignoreDefense(skillContext.isIgnoreDefense())
                    .guaranteedCrit(skillContext.isGuaranteedCrit() || forceCrit)
                    .critChanceBonus(critBonus)
                    .build();
            }
        }

        if ("normal".equals(skillContext.getSkillId())
                && WeaponSkillAnimationLock.isLocked(player, nowTick)) {
            event.setNewDamage(0);
            return;
        }

        com.stardew.craft.combat.equipment.EquipmentStats equipStats = null;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            equipStats = com.stardew.craft.combat.equipment.EquipmentResolver.getMergedStats(sp);
        }

        boolean isPrimaryTarget = AttackTargetTracker.isPrimaryTarget(player, target, nowTick);
        boolean isNormalAttack = "normal".equals(skillContext.getSkillId());
        boolean isSweepTarget = isSweepSource || (isNormalAttack && !isPrimaryTarget);
        WeaponStats sweepStats = WeaponStats.fromItemStack(weapon);
        WeaponType sweepWeaponType = WeaponType.SWORD;
        if (weapon.getItem() instanceof IStardewWeapon IStardewWeapon) {
            WeaponData weaponData = IStardewWeapon.getWeaponData();
            if (weaponData != null) {
                sweepWeaponType = weaponData.getWeaponType();
            } else if (sweepStats != null) {
                sweepWeaponType = sweepStats.getWeaponType();
            }
        }

        if (isSweepSource && sweepWeaponType == WeaponType.DAGGER) {
            event.setNewDamage(0);
            return;
        }

        if (isSweepTarget && sweepWeaponType == WeaponType.DAGGER) {
            event.setNewDamage(0);
            return;
        }

        if (isSweepTarget && sweepWeaponType != WeaponType.SWORD && sweepWeaponType != WeaponType.CLUB) {
            event.setNewDamage(0);
            return;
        }

        DamageRequest.Builder damageRequest = DamageCalculator.createPlayerDamageRequest(
                player,
                target,
                weapon,
                skillContext,
                equipStats
        ).toBuilder();
        if (spineBoost != null) {
            damageRequest.addPostDefenseAdjustment(
                    DamageAdjustment.add("steel_spine_bonus", spineBoost.bonusDamage())
            );
        }

        // MC 原版蓄力冷却惩罚 — SDV 武器跳过此机制
        // 服务器上 attackStrengthTicker 与客户端不同步，
        // 导致 getAttackStrengthScale() 返回 0 → 0.2F + 0*0*0.8F = 0.2 = 恰好 1/5 伤害。
        // SDV 有自己的武器速度系统（speed 属性），不需要 MC 的蓄力惩罚。
        if (isNormalAttack) {
            float attackStrength = player.getAttackStrengthScale(0.5F);
            if (attackStrength < 0.85F) {
                // 自愈：服务端蓄力值不可靠，强制视为满蓄力
                attackStrength = 1.0F;
            }
            float strengthMultiplier = 0.2F + attackStrength * attackStrength * 0.8F;
            damageRequest.addPostDefenseAdjustment(
                    DamageAdjustment.multiply("attack_strength", strengthMultiplier)
            );
        }

        if (isSweepTarget && sweepWeaponType == WeaponType.SWORD) {
            int sweepingLevel = getItemEnchantmentLevel(
                    player,
                    weapon,
                    Enchantments.SWEEPING_EDGE
            );
            float sweepRatio = sweepingLevel > 0
                ? 1.0f - (1.0f / (sweepingLevel + 1.0f))
                : 0.10f;
            damageRequest.addPostDefenseAdjustment(
                    DamageAdjustment.replaceWithBaseMultiplier("sword_sweep", sweepRatio)
            );
        }

        boolean bloodMoonActive = player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && DarkSwordBloodMoonTracker.isActive(serverPlayer, nowTick);
        if (bloodMoonActive) {
            damageRequest.addPostDefenseAdjustment(DamageAdjustment.multiply(
                    "dark_sword_blood_moon",
                    DarkSwordBloodMoonTracker.getDamageBonusMultiplier(
                            (net.minecraft.server.level.ServerPlayer) player,
                            nowTick
                    )
            ));
        }

        if (StardewEnchantments.has(weapon, StardewEnchantments.BUG_KILLER)
                && StardewEnchantments.isBugKillerTarget(target)) {
            damageRequest.addPreDefenseAdjustment(
                    DamageAdjustment.multiplyFloor("enchantment_bug_killer", 2.0f)
            );
        }
        if (StardewEnchantments.has(weapon, StardewEnchantments.CRUSADER)
                && StardewEnchantments.isCrusaderTarget(target)) {
            damageRequest.addPreDefenseAdjustment(
                    DamageAdjustment.multiplyFloor("enchantment_crusader", 1.5f)
            );
        }
        if (hasDragonToothBonus(weapon, "slime_slayer")
                && isDragonToothSlimeSlayerTarget(target)) {
            damageRequest
                    .addPreDefenseAdjustment(DamageAdjustment.multiply("dragontooth_slime_slayer", 1.33f))
                    .addPreDefenseAdjustment(DamageAdjustment.addFloor("dragontooth_slime_slayer_flat", 1.0f));
        }
        DamageOutcome result = DamagePipeline.evaluate(damageRequest.build());
        CombatDamageHistory.record(player, nowTick, result);
        float finalDamage = result.getFinalDamage();
        event.setNewDamage(finalDamage);


        if (spineBoost != null && !result.isDodged() && finalDamage > 0 && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new SteelSpineFuryStrikePayload(spineBoost.strong()));
        }

        if ("holy_smite".equals(skillContext.getSkillId())
                && !result.isDodged()
                && finalDamage > 0
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            HolyBladeEffects.playSmiteHit((ServerLevel) target.level(), target);
            HolyBladeEffects.playHeal(serverPlayer, HolySmiteSkillHandler.HEAL_AMOUNT);
            HolyBladeDodgeTracker.start(
                    serverPlayer,
                    nowTick,
                    HolySmiteSkillHandler.DODGE_DURATION_TICKS,
                    HolySmiteSkillHandler.DODGE_CHANCE
            );
        }

        if ("tempered_quench".equals(skillContext.getSkillId())
                && !result.isDodged()
                && finalDamage > 0
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            TemperedQuenchTracker.start(
                    serverPlayer,
                    target,
                    nowTick,
                    TemperedQuenchSkillHandler.BLAST_DELAY_TICKS,
                    damageWeaponSnapshot
            );
        }

        if (!result.isDodged()
                && finalDamage > 0
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            float debtRatio = DarkSwordBloodDebtTracker.getLifestealRatio(serverPlayer, nowTick);
            float moonRatio = DarkSwordBloodMoonTracker.getLifestealRatio(serverPlayer, nowTick);
            float ratio = Math.max(debtRatio, moonRatio);
            if (ratio > 0.0f) {
                int heal = Math.max(1, Math.round(finalDamage * ratio));
                float actualHeal = CombatHealing.heal(serverPlayer, heal);
                if (actualHeal > 0.0F) {
                    DarkSwordBloodMoonTracker.recordLifeSteal(serverPlayer, nowTick, actualHeal);
                    DarkSwordEffects.playLifeSteal(serverPlayer);
                }
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && weapon.getItem() instanceof IStardewWeapon IStardewWeapon
                && "iridium_needle".equals(IStardewWeapon.getWeaponId())
                && !result.isDodged()
                && finalDamage > 0) {
            IridiumNeedleCritTracker.recordHit(serverPlayer);
            if (IridiumNeedleFrenzyTracker.isActive(serverPlayer, nowTick) && result.isCrit()) {
                CombatHealing.heal(serverPlayer, IridiumNeedleFrenzyTracker.CRITICAL_HEAL_AMOUNT);
                PlayerStardewDataAPI.restoreEnergy(
                        serverPlayer,
                        IridiumNeedleFrenzyTracker.CRITICAL_ENERGY_RESTORE
                );
                @SuppressWarnings("null")
                MobEffect vulnerable = Objects.requireNonNull(ModMobEffects.VULNERABLE.get(), "vulnerable");
                @SuppressWarnings("null")
                Holder<MobEffect> vulnerableHolder = Holder.direct(vulnerable);
                @SuppressWarnings("null")
                MobEffectInstance vulnerableInstance = new MobEffectInstance(
                        vulnerableHolder,
                        IridiumNeedleFrenzyTracker.CRITICAL_VULNERABLE_DURATION_TICKS,
                        IridiumNeedleFrenzyTracker.CRITICAL_VULNERABLE_AMPLIFIER,
                        false,
                        true,
                        true
                );
                target.addEffect(vulnerableInstance);
            }
        }

        // 击退（重量）：命中且未闪避时生效
        if (!result.isDodged() && result.getFinalDamage() > 0) {
            WeaponStats weaponStats = WeaponStats.fromItemStack(weapon);
            float strength = calculateKnockbackStrength(weaponStats);
            // SDV BuffEffects.KnockbackMultiplier scales the weapon's knockback.
            if (equipStats != null) {
                strength *= 1.0f + equipStats.getKnockbackBonus();
            }
            if (strength > 0.0f) {
                double dx = player.getX() - target.getX();
                double dz = player.getZ() - target.getZ();
                if (dx * dx + dz * dz > 0.0001) {
                    target.knockback(strength, dx, dz);
                }
            }
        }

        String skillId = result.getSkillId();
        if (bloodMoonActive && (skillId == null || "normal".equals(skillId))) {
            skillId = "dark_sword_blood_moon";
        } else if (skillId != null && "normal".equals(skillId)) {
            skillId = null;
        }
        if (skillId == null
                && result.isCrit()
                && weapon.getItem() instanceof IStardewWeapon IStardewWeapon
                && "iridium_needle".equals(IStardewWeapon.getWeaponId())) {
            skillId = "iridium_needle_thrust";
        }
        boolean displayCrit = result.isCrit() && !isSweepTarget;
        DamageNumberContextStore.set(
                player,
                skillId,
                displayCrit,
                damageWeaponSnapshot,
                nowTick + 2
        );

        if (!"elf_blade_leaf".equals(skillContext.getSkillId())
            && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && !result.isDodged()
            && finalDamage > 0) {
            ElfBladeTracker.fireLeafAtTarget(serverPlayer, target, nowTick);
        }

        if ("normal".equals(skillContext.getSkillId())
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && !result.isDodged()
                && finalDamage > 0) {
            if (ObsidianResonanceTracker.isCharged(serverPlayer, nowTick)) {
                ObsidianResonanceTracker.consumeAndStrike(
                        serverPlayer,
                        target,
                        nowTick,
                        result.isCrit(),
                        damageWeaponSnapshot
                );
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && !result.isDodged()
                && finalDamage > 0
                && weapon.getItem() instanceof IStardewWeapon IStardewWeapon) {
            String weaponId = IStardewWeapon.getWeaponId();
            if ("dragontooth_cutlass".equals(weaponId)) {
                if ("normal".equals(skillContext.getSkillId())) {
                    DragonBreathTracker.addStacks(serverPlayer, result.isCrit() ? 3 : 1);
                }
            } else if ("galaxy_sword".equals(weaponId)) {
                StartrailTracker.addStacks(serverPlayer, result.isCrit() ? 3 : 1);
            } else if ("infinity_blade".equals(weaponId)) {
                SingularityTracker.addStacks(serverPlayer, result.isCrit() ? 3 : 1);
            } else if ("crystal_dagger".equals(weaponId)) {
                if ("normal".equals(skillContext.getSkillId())) {
                    CrystalDaggerLayerTracker.addStack(serverPlayer, nowTick);
                }
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && weapon.getItem() instanceof IStardewWeapon IStardewWeapon
                && "crystal_dagger".equals(IStardewWeapon.getWeaponId())
                && !result.isDodged()
                && finalDamage > 0) {
            if (CrystalDaggerLayerTracker.shouldBurst(serverPlayer, nowTick)) {
                CrystalDaggerLayerTracker.consumeBurst(serverPlayer);
                SkillContext burstContext = SkillContext.builder()
                    .skillId("crystal_dagger_burst")
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(0.80f)
                    .build();
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                WeaponSkillDamage.apply(
                        player,
                        target,
                        burstContext,
                        damageWeaponSnapshot,
                        nowTick + 5
                );
                PacketDistributor.sendToPlayer(serverPlayer, new CrystalDaggerBurstPayload());
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && weapon.getItem() instanceof IStardewWeapon IStardewWeapon
                && "normal".equals(skillContext.getSkillId())
                && !result.isDodged()
                && finalDamage > 0) {
            if ("infinity_blade".equals(IStardewWeapon.getWeaponId())
                && SingularityTracker.isEvolved(serverPlayer)) {
                SkillContext followContext = SkillContext.builder()
                    .skillId("singularity_followup")
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(0.30f)
                    .build();
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                WeaponSkillDamage.apply(
                        player,
                        target,
                        followContext,
                        damageWeaponSnapshot,
                        nowTick + 5
                );
            }
        }

        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && !result.isDodged()
                && finalDamage > 0) {
            com.stardew.craft.item.trinket.TrinketEffectHandler.onDamageMonster(
                    serverPlayer, target, Math.max(1, Math.round(finalDamage)), result.isCrit());
        }
    }


    @SuppressWarnings("null")
    private static boolean isBackstab(Player player, LivingEntity target) {
        Vec3 toAttacker = player.position().subtract(target.position());
        Vec3 targetLook = target.getLookAngle();
        Vec3 toFlat = new Vec3(toAttacker.x, 0.0, toAttacker.z);
        Vec3 lookFlat = new Vec3(targetLook.x, 0.0, targetLook.z);
        if (toFlat.lengthSqr() < 1.0E-4 || lookFlat.lengthSqr() < 1.0E-4) {
            return false;
        }
        double dot = toFlat.normalize().dot(lookFlat.normalize());
        return dot < -0.35;
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;

        long nowTick = target.level().getGameTime();
        DamageNumberContextStore.Meta meta = DamageNumberContextStore.peek(player, nowTick);
        String skillId = meta != null ? meta.skillId() : null;
        boolean crit = meta != null && meta.crit();
        ItemStack damageWeapon = meta != null
                ? meta.weaponSnapshot()
                        .map(WeaponDamageSnapshot::weapon)
                        .orElseGet(player::getMainHandItem)
                : player.getMainHandItem();
        WeaponDamageSnapshot damageWeaponSnapshot = meta != null
                ? meta.weaponSnapshot().orElse(null)
                : null;
        if (damageWeaponSnapshot == null
                && damageWeapon.getItem() instanceof IStardewWeapon weaponItem) {
            damageWeaponSnapshot = WeaponDamageSnapshot.capture(
                    net.minecraft.resources.ResourceLocation
                            .fromNamespaceAndPath(
                                    StardewCraft.MODID,
                                    weaponItem.getWeaponId()
                            ),
                    damageWeapon
            );
        }

        if ("burglar_shank".equals(skillId)
            && event.getNewDamage() > 0.0f
            && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            boolean killed = (!MineMonsterSpawnHandler.isCollapsedMummy(target))
                    && (!target.isAlive() || target.getHealth() <= 0.0f);
            if (killed) {
                BurglarLootHooks.fireBurglarKill(target, serverPlayer);
                if (DimensionDamageMapper.isInStardewDimension(target)) {
                    PlayerStardewDataAPI.addMoney(serverPlayer, 10);
                    PacketDistributor.sendToPlayer(serverPlayer, new BurglarShankLootPayload());
                }
            } else {
                target.addEffect(new net.minecraft.world.effect.MobEffectInstance(ModMobEffects.WEAK_POINT, 60, 1, false, true, true));
            }
        }

        boolean killedByPlayer = (!MineMonsterSpawnHandler.isCollapsedMummy(target))
                && (!target.isAlive() || target.getHealth() <= 0.0f);
        boolean dealtPositiveDamage = event.getNewDamage() > 0.0F;
        if (dealtPositiveDamage
                && killedByPlayer
                && StardewEnchantments.has(damageWeapon, StardewEnchantments.VAMPIRIC)
                && player.getRandom().nextFloat() < 0.09f
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            CombatHealing.healFraction(serverPlayer, 0.10F, 1.0F);
        }

        if (!dealtPositiveDamage) {
            return;
        }

        boolean inStardewDimension =
                DimensionDamageMapper.isInStardewDimension(target);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            boolean killed = killedByPlayer;
            if (killed) {
                if (hasDragonToothBonus(damageWeapon, "slime_gatherer") && isDragonToothSlimeGathererTarget(target)) {
                    dropDragonToothSlimeGatherer(target, serverPlayer);
                }
                if (inStardewDimension) {
                    PlayerStardewDataAPI.addExperience(
                            serverPlayer,
                            SkillType.COMBAT,
                            getCombatExperienceOnKill(target)
                    );
                }
            }
        }

        if (inStardewDimension) {
            double headTop = target.getY() + target.getBbHeight();
            double baseYOffset = Math.max(0.20, target.getBbHeight() * 0.25);
            double baseY = headTop + baseYOffset;
            if ("tide_mark_bonus".equals(skillId)) {
                baseY += target.getBbHeight() * 0.12;
            } else if ("tide_anchor".equals(skillId)) {
                baseY += target.getBbHeight() * 0.06;
            } else if ("tide_reel".equals(skillId)) {
                baseY += target.getBbHeight() * 0.08;
            } else if ("templar_judgement_share".equals(skillId)) {
                baseY += target.getBbHeight() * 0.10;
            } else if ("templar_judgement".equals(skillId)) {
                baseY += target.getBbHeight() * 0.08;
            }
            int damage = Math.max(0, Math.round(event.getNewDamage()));
            DamageNumberPayload payload = new DamageNumberPayload(
                    (float) target.getX(),
                    (float) baseY,
                    (float) target.getZ(),
                    damage,
                    crit,
                    skillId
            );
            if (target.level() instanceof ServerLevel serverLevel) {
                PacketDistributor.sendToPlayersInDimension(
                        serverLevel,
                        payload
                );
            } else {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(
                        target,
                        payload
                );
            }
        }

        // === 潮汐印记追加伤害：命中反�?===
        if ("tide_mark_bonus".equals(skillId) && target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                x, y, z,
                10, 0.35, 0.2, 0.35, 0.03);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z,
                6, 0.25, 0.15, 0.25, 0.05);
            serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.55f, 1.1f);
        }

        // === 潮汐锚：命中反馈 ===
        if ("tide_anchor".equals(skillId) && target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                x, y, z,
                8, 0.35, 0.2, 0.35, 0.03);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z,
                5, 0.25, 0.15, 0.25, 0.05);
            serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, 0.95f);
        }

        if ("fishcatch_thrust".equals(skillId) && target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                x, y, z,
                10, 0.35, 0.2, 0.35, 0.03);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                x, y, z,
                8, 0.35, 0.2, 0.35, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z,
                5, 0.25, 0.15, 0.25, 0.05);
            serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.TRIDENT_HIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.45f, 1.1f);
        }

        if ("tide_reel".equals(skillId) && target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH,
                x, y, z,
                14, 0.45, 0.25, 0.45, 0.04);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,
                x, y, z,
                10, 0.45, 0.25, 0.45, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z,
                8, 0.35, 0.2, 0.35, 0.06);
            serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 0.55f, 1.1f);
        }

        if ("crystal_dagger_burst".equals(skillId) && target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                x, y, z,
                18, 0.45, 0.35, 0.45, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                x, y, z,
                14, 0.45, 0.35, 0.45, 0.05);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z,
                10, 0.35, 0.25, 0.35, 0.06);
            serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.15f);
        }

        if (("shadow_dagger_execute".equals(skillId) || "shadow_dagger_execute_bonus".equals(skillId))
            && target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                x, y, z,
                16, 0.35, 0.25, 0.35, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                x, y, z,
                10, 0.25, 0.2, 0.25, 0.01);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                x, y, z,
                10, 0.35, 0.25, 0.35, 0.04);
            serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1.4f);

            boolean killed = !target.isAlive() || target.getHealth() <= 0.0f;
            if (killed) {
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    x, y + 0.15, z,
                    20, 0.45, 0.35, 0.45, 0.03);
                serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    x, y, z,
                    16, 0.35, 0.25, 0.35, 0.08);
                serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.45f, 1.2f);
                serverLevel.playSound(null, target.blockPosition(),
                    net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.2f);
            }
        }

        // === 昆虫头部：复眼架势命中反�?+ 易伤II ===
        if ("insect_eye_stance".equals(skillId) && event.getNewDamage() > 0.0f && target.level() instanceof ServerLevel serverLevel) {
            @SuppressWarnings("null")
            MobEffect vulnerable = Objects.requireNonNull(ModMobEffects.VULNERABLE.get(), "vulnerable");
            @SuppressWarnings("null")
            Holder<MobEffect> vulnerableHolder = Holder.direct(vulnerable);
            @SuppressWarnings("null")
            MobEffectInstance vulnerableInstance = new MobEffectInstance(vulnerableHolder, 60, 1, false, true, true);
            target.addEffect(vulnerableInstance);

            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.55;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                x, y, z,
                10, 0.35, 0.2, 0.35, 0.02);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z,
                6, 0.25, 0.15, 0.25, 0.05);
            serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.BEEHIVE_WORK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 1.4f);
        }

        // === 昆虫头部：甲翼疾掠命中反�?===
        if ("insect_dash".equals(skillId) && event.getNewDamage() > 0.0f && target.level() instanceof ServerLevel serverLevel) {
            double x = target.getX();
            double y = target.getY() + target.getBbHeight() * 0.6;
            double z = target.getZ();
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                x, y, z,
                1, 0.0, 0.0, 0.0, 0.0);
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                x, y, z,
                8, 0.3, 0.18, 0.3, 0.06);
            serverLevel.playSound(null, target.blockPosition(),
                net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.15f);
        }

        // === 雪怪之牙：冻牙刻印命中后施加印记与减�?===
        if (YetiToothMarkTracker.SKILL_ID.equals(skillId)
            && event.getNewDamage() > 0.0f
            && attacker instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && target.level() instanceof ServerLevel) {
            YetiToothMarkTracker.apply(
                    target,
                    serverPlayer,
                    nowTick,
                    YetiToothMarkTracker.MARK_DURATION_TICKS
            );
            YetiToothEffects.applySlow(
                    target,
                    YetiToothMarkTracker.SLOW_DURATION_TICKS,
                    YetiToothMarkTracker.SLOW_AMPLIFIER
            );
        }

        // === 熔岩武士刀：熔铸刻印命中后施加熔印 ===
        if ("lava_katana_brand".equals(skillId)
            && event.getNewDamage() > 0.0f
            && attacker instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && target.level() instanceof ServerLevel) {
            com.stardew.craft.combat.skill.LavaKatanaMarkTracker.apply(
                    target,
                    serverPlayer,
                    nowTick,
                    com.stardew.craft.combat.skill.LavaKatanaMarkTracker.MARK_DURATION_TICKS
            );
        }

        boolean isOssifiedExtra = meta != null
            && ("ossified_mark_bonus".equals(meta.skillId()) || "ossified_execution_dot".equals(meta.skillId()));

        if (!isOssifiedExtra
            && event.getNewDamage() > 0.0f
            && meta != null
            && meta.crit()) {
            if (damageWeaponSnapshot != null
                && damageWeapon.getItem() instanceof IStardewWeapon weaponItem
                && "ossified_blade".equals(weaponItem.getWeaponId())
                && OssifiedMarkTracker.consumeBonusIfEligible(target, player, nowTick)) {
                SkillContext bonusContext = SkillContext.builder()
                    .skillId("ossified_mark_bonus")
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(1.0f)
                    .build();
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                WeaponSkillDamage.apply(
                        player,
                        target,
                        bonusContext,
                        damageWeaponSnapshot,
                        nowTick + 5
                );

                if (target.level() instanceof ServerLevel serverLevel) {
                    double x = target.getX();
                    double y = target.getY() + target.getBbHeight() * 0.6;
                    double z = target.getZ();
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ASH,
                        x, y, z,
                        8, 0.25, 0.18, 0.25, 0.02);
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                        x, y, z,
                        6, 0.2, 0.12, 0.2, 0.05);
                    serverLevel.playSound(null, target.blockPosition(),
                        net.minecraft.sounds.SoundEvents.BONE_BLOCK_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.2f);
                }
            }
        }

        // === 雪怪之牙：印记触发冻结（下一次命中） ===
        if (event.getNewDamage() > 0.0f
            && attacker instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && target.level() instanceof ServerLevel serverLevel) {
            if (YetiToothMarkTracker.isEligibleFollowupSkill(skillId)
                && YetiToothMarkTracker.consumeIfEligible(target, serverPlayer, nowTick)) {
                YetiToothEffects.applyFreeze(
                        serverLevel,
                        target,
                        YetiToothMarkTracker.FREEZE_DURATION_TICKS
                );
            }
        }

        // === 熔岩武士刀：命中叠加热�?===
        if (event.getNewDamage() > 0.0f
            && attacker instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            ItemStack weapon = damageWeapon;
            if (weapon.getItem() instanceof IStardewWeapon weaponItem
                && "lava_katana".equals(weaponItem.getWeaponId())) {
                boolean isBurn = "lava_katana_burn".equals(skillId);
                boolean isFinisher = "lava_katana_finisher".equals(skillId);
                boolean isBrand = "lava_katana_brand".equals(skillId);
                if (!isBurn && !isFinisher && !isBrand
                    && com.stardew.craft.combat.skill.LavaKatanaMarkTracker.isMarkedBy(target, serverPlayer, nowTick)) {
                    com.stardew.craft.combat.skill.LavaKatanaMarkTracker.addHeatIfEligible(target, serverPlayer, nowTick, 1);
                }
            }
        }

        boolean isGalaxyBonus = meta != null && "galaxy_dagger_mark_bonus".equals(meta.skillId());
        boolean isInfinityBonus = meta != null && "infinity_dagger_mark_bonus".equals(meta.skillId());

        if (event.getNewDamage() > 0.0f
            && attacker instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && !isGalaxyBonus
            && damageWeaponSnapshot != null) {
            if (GalaxyDaggerMarkTracker.consumeIfEligible(target, serverPlayer, nowTick)) {
                SkillContext bonusContext = SkillContext.builder()
                    .skillId("galaxy_dagger_mark_bonus")
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(0.80f)
                    .build();
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                WeaponSkillDamage.apply(
                        player,
                        target,
                        bonusContext,
                        damageWeaponSnapshot,
                        nowTick + 5
                );

                if (target.level() instanceof ServerLevel serverLevel) {
                    double x = target.getX();
                    double y = target.getY() + target.getBbHeight() * 0.6;
                    double z = target.getZ();
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        x, y, z,
                        14, 0.35, 0.2, 0.35, 0.04);
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                        x, y, z,
                        12, 0.35, 0.2, 0.35, 0.05);
                    serverLevel.playSound(null, target.blockPosition(),
                        net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.6f, 1.35f);
                    serverLevel.playSound(null, target.blockPosition(),
                        net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, 1.2f);
                }
            }
        }

        boolean skipInfinityMark = "infinity_dagger_singularity_backstab".equals(skillId);
        if (event.getNewDamage() > 0.0f
            && attacker instanceof net.minecraft.server.level.ServerPlayer serverPlayer
            && !isInfinityBonus
            && !skipInfinityMark
            && damageWeaponSnapshot != null) {
            if (InfinityDaggerMarkTracker.consumeIfEligible(target, serverPlayer, nowTick)) {
                SkillContext bonusContext = SkillContext.builder()
                    .skillId("infinity_dagger_mark_bonus")
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(1.20f)
                    .build();
                target.invulnerableTime = 0;
                target.hurtTime = 0;
                WeaponSkillDamage.apply(
                        player,
                        target,
                        bonusContext,
                        damageWeaponSnapshot,
                        nowTick + 5
                );

                if (target.level() instanceof ServerLevel serverLevel) {
                    double x = target.getX();
                    double y = target.getY() + target.getBbHeight() * 0.6;
                    double z = target.getZ();
                    double radius = 0.32;
                    for (int i = 0; i < 12; i++) {
                        double angle = (Math.PI * 2.0 * i) / 12.0;
                        double px = x + Math.cos(angle) * radius;
                        double pz = z + Math.sin(angle) * radius;
                        double vx = (x - px) * 0.08;
                        double vz = (z - pz) * 0.08;
                        serverLevel.addParticle(net.minecraft.core.particles.ParticleTypes.PORTAL,
                            px, y, pz,
                            vx, 0.0, vz);
                    }
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                        x, y, z,
                        10, 0.25, 0.18, 0.25, 0.02);
                    serverLevel.playSound(null, target.blockPosition(),
                        net.minecraft.sounds.SoundEvents.END_PORTAL_FRAME_FILL,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.5f, 0.75f);
                    serverLevel.playSound(null, target.blockPosition(),
                        net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.35f, 0.75f);
                }
            }
        }

        // === 海王星大剑：潮汐印记追加伤害 ===
        if (meta != null && TideMarkTracker.BONUS_SKILL_ID.equals(meta.skillId())) {
            return;
        }

        if (event.getNewDamage() <= 0.0f) {
            return;
        }

        if (damageWeaponSnapshot == null
                || !(damageWeapon.getItem() instanceof IStardewWeapon weaponItem)) {
            return;
        }

        if (!"neptunes_glaive".equals(weaponItem.getWeaponId())) {
            return;
        }

        if (!TideMarkTracker.isMarkedBy(target, player, nowTick)) {
            return;
        }

        target.invulnerableTime = 0;
        target.hurtTime = 0;
        WeaponSkillDamage.apply(
                player,
                target,
                TideMarkTracker.createBonusContext(),
                damageWeaponSnapshot,
                nowTick + TideMarkTracker.HIT_CONTEXT_LIFETIME_TICKS
        );
    }

    private static int getCombatExperienceOnKill(LivingEntity target) {
        var tags = target.getTags();

        if (tags.contains("sd_mob_slime")) {
            if (tags.contains("sd_tier_5")) return 20;
            if (tags.contains("sd_tier_4")) return 10;
            if (tags.contains("sd_tier_3")) return 6;
            if (tags.contains("sd_tier_2")) return 5;
            return 3;
        }
        if (tags.contains("sd_mob_bat")) {
            if (tags.contains("sd_tier_4")) return 15;
            if (tags.contains("sd_tier_3")) return 10;
            if (tags.contains("sd_tier_2")) return 7;
            return 5;
        }
        if (tags.contains("sd_mob_fly")) return 3;
        if (tags.contains("sd_mob_grub")) return 2;
        if (tags.contains("sd_mob_bug")) return tags.contains("sd_tier_2") ? 10 : 5;
        if (tags.contains("sd_mob_dust_sprite")) return 3;
        if (tags.contains("sd_mob_skeleton")) return tags.contains("sd_tier_3") ? 20 : 15;
        if (tags.contains("sd_mob_ghost")) return tags.contains("sd_tier_2") ? 20 : 15;
        if (tags.contains("sd_mob_mummy")) return 20;
        if (tags.contains("sd_mob_serpent")) return 10;
        if (tags.contains("sd_mob_crab")) {
            if (tags.contains("sd_tier_3")) return 12;
            if (tags.contains("sd_tier_2")) return 8;
            return 5;
        }
        if (tags.contains("sd_mob_golem")) return tags.contains("sd_tier_2") ? 15 : 10;
        if (tags.contains("sd_mob_shadow")) return tags.contains("sd_tier_2") ? 15 : 12;
        if (tags.contains("sd_mob_duggy")) return 5;
        if (tags.contains("sd_mob_metal_head")) return 15;
        if (tags.contains("sd_mob_squid")) return 10;

        // 非标签怪：按实体类型回退，匹配数据包 COMBAT_SYSTEM.md 的默认对应关系。
        @SuppressWarnings("null")
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getPath();
        return switch (path) {
            case "slime" -> 3;
            case "phantom", "bat" -> 5;
            case "endermite" -> 2;
            case "spider", "cave_spider", "silverfish" -> 5;
            case "skeleton", "stray", "wither_skeleton", "zombie", "drowned", "blaze" -> 15;
            case "vex" -> 10;
            default -> 3;
        };
    }

    private static boolean hasDragonToothBonus(ItemStack weapon, String kind) {
        return WeaponForgeData.dragonToothBonuses(WeaponForgeData.read(weapon).dragonToothEnchantment()).stream()
                .anyMatch(bonus -> kind.equals(bonus.kind()));
    }

    private static boolean isDragonToothSlimeSlayerTarget(LivingEntity target) {
        return target.getTags().contains("sd_mob_slime");
    }

    private static boolean isDragonToothSlimeGathererTarget(LivingEntity target) {
        var tags = target.getTags();
        return tags.contains("sd_mob_slime") || tags.contains("sd_mob_bigslime_skull");
    }

    private static void dropDragonToothSlimeGatherer(LivingEntity target, net.minecraft.server.level.ServerPlayer player) {
        int bound = Math.max(1, (int) Math.ceil(Math.sqrt(target.getMaxHealth()) / 3.0));
        int toDrop = 1 + player.getRandom().nextInt(bound);
        ItemEntity itemEntity = new ItemEntity(target.level(), target.getX(), target.getY(), target.getZ(),
                new ItemStack(ModItems.SLIME_ITEM.get(), toDrop));
        itemEntity.setDefaultPickUpDelay();
        target.level().addFreshEntity(itemEntity);
    }

    private static float calculateKnockbackStrength(WeaponStats stats) {
        float base = switch (stats.getWeaponType()) {
            case SWORD -> 0.4f;
            case DAGGER -> 0.1f;
            case CLUB -> 0.8f;
            default -> 0.3f;
        };
        float extra = stats.getKnockback() * 0.10f;
        return Math.max(0.0f, base + extra);
    }


    @SuppressWarnings({ "null", "deprecation" })
    private static int getItemEnchantmentLevel(Player player, ItemStack stack,
                                               net.minecraft.resources.ResourceKey<Enchantment> enchantmentKey) {
        var lookup = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var holder = lookup.getOrThrow(enchantmentKey);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }


    private static boolean isSweepDamageSource(DamageSource source) {
        if (source == null) {
            return false;
        }
        String msgId = source.getMsgId();
        if (msgId == null) {
            return false;
        }
        return msgId.contains("sweep") || "playerSweep".equals(msgId) || "player_sweep".equals(msgId);
    }

    /** Clean up state when a player logs out to prevent memory leaks. */
    public static void removePlayer(UUID playerId) {
        CombatDamageHistory.remove(playerId);
    }
}
