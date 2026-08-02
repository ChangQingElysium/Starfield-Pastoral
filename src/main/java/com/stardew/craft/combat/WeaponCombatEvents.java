package com.stardew.craft.combat;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import com.stardew.craft.combat.skill.WeaponSkillDamage;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.handler.SteelSpineFurySkillHandler;
import com.stardew.craft.combat.skill.SilverSaberSkillHelper;
import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.SweepAttackEvent;
import net.neoforged.neoforge.entity.PartEntity;


@EventBusSubscriber(modid = StardewCraft.MODID)
public class WeaponCombatEvents {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onSweepAttack(SweepAttackEvent event) {
        ItemStack weapon = event.getEntity().getMainHandItem();
        if (!WeaponCombatIdentity.isWeapon(weapon)) {
            return;
        }
        event.setSweeping(false);
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
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

        Entity attackedEntity = event.getTarget();
        if (attackedEntity instanceof PartEntity<?> part) {
            attackedEntity = part.getParent();
        }
        if (!(attackedEntity instanceof LivingEntity target)) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!WeaponCombatIdentity.isWeapon(stack)) {
            return;
        }

        if (SilverSaberSkillHelper.tryHandleStayStrike(
                player,
                target,
                stack,
                nowTick
        )) {
            event.setCanceled(true);
            return;
        }

        if (player instanceof ServerPlayer serverPlayer
                && !WeaponSkillContextStore.hasPending(player, nowTick)
                && !StardewWeaponAttackRecovery.tryAcquire(
                        serverPlayer,
                        stack,
                        nowTick
                )) {
            event.setCanceled(true);
            return;
        }

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

    /** Rolls one authoritative weapon hit before native protection. */
    @SuppressWarnings("null")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (DimensionDamageMapper.isInStardewDimension(target)) return;

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) return;

        long nowTick = target.level().getGameTime();
        WeaponDamageAdmission admission = classifyWeaponDamageProvenance(
                source,
                player,
                target,
                nowTick
        );
        if (!isEligibleWeaponDamageSource(admission.provenance())) return;

        WeaponIncomingHitStore.discardExpired(nowTick);
        IncomingWeaponResolution resolution = evaluateWeaponHit(
                player,
                target,
                source,
                nowTick,
                admission,
                null
        );
        if (resolution == null) return;

        event.setAmount(resolution.authoritativeDamage());
        if (resolution.hit() != null) {
            WeaponIncomingHitStore.bind(
                    event.getContainer(),
                    resolution.hit(),
                    nowTick,
                    nowTick + 2L
            );
        }
    }

    @SubscribeEvent(
            priority = EventPriority.LOWEST,
            receiveCanceled = true
    )
    public static void onLivingIncomingDamageFinal(
            LivingIncomingDamageEvent event
    ) {
        if (event.isCanceled()) {
            WeaponIncomingHitStore.discard(event.getContainer());
        }
    }

    private static IncomingWeaponResolution evaluateWeaponHit(
            Player player,
            LivingEntity target,
            DamageSource source,
            long nowTick,
            WeaponDamageAdmission admission,
            CustomHealthProtection customProtection
    ) {
        WeaponSkillContextStore.PendingHit pendingHit =
                admission.provenance() == WeaponDamageProvenance.PROJECT_SKILL
                        ? WeaponSkillContextStore.consumePending(
                                player,
                                nowTick
                        )
                        : null;
        WeaponDamageSnapshot releaseWeapon = pendingHit == null
                ? admission.ordinaryWeaponSnapshot()
                : pendingHit.weaponSnapshot().orElse(null);
        if (releaseWeapon == null) return null;
        ItemStack weapon = releaseWeapon.weapon();
        WeaponCombatIdentity.Resolved weaponIdentity =
                WeaponCombatIdentity.resolve(weapon).orElse(null);
        if (weaponIdentity == null) return null;
        boolean isSweepSource = isSweepDamageSource(source);

        SkillContext skillContext = pendingHit != null
                ? pendingHit.skillContext()
                : SkillContext.normalAttack();
        WeaponDamageSnapshot damageWeaponSnapshot = releaseWeapon;

        WeaponHitPreparation preparation =
                DimensionDamageMapper.isInStardewDimension(target)
                        ? WeaponHitPreparation.prepare(
                                player,
                                target,
                                weapon,
                                skillContext,
                                nowTick
                        )
                        : WeaponHitPreparation.reserve(
                                player,
                                target,
                                weapon,
                                skillContext,
                                nowTick
                        );
        skillContext = preparation.skillContext();
        SteelSpineFurySkillHandler.AttackBoost spineBoost =
                preparation.steelSpineBoost();
        if ("normal".equals(skillContext.getSkillId())
                && WeaponSkillAnimationLock.isLocked(player, nowTick)) {
            preparation.reservation().release();
            return IncomingWeaponResolution.suppressed();
        }

        com.stardew.craft.combat.equipment.EquipmentStats equipStats = null;
        if (player instanceof ServerPlayer serverPlayer) {
            equipStats = com.stardew.craft.combat.equipment.EquipmentResolver
                    .getMergedStats(serverPlayer);
        }

        boolean isPrimaryTarget = admission.provenance()
                == WeaponDamageProvenance.PLAYER_ATTACK;
        boolean isNormalAttack = "normal".equals(skillContext.getSkillId());
        boolean isSweepTarget = isSweepSource
                || (isNormalAttack && !isPrimaryTarget);
        WeaponType sweepWeaponType = WeaponStats.fromItemStack(weapon)
                .getWeaponType();

        if (isSweepTarget && sweepWeaponType == WeaponType.SLINGSHOT) {
            preparation.reservation().release();
            return IncomingWeaponResolution.suppressed();
        }

        DamageRequest.Builder damageRequest = DamageCalculator
                .createPlayerDamageRequest(
                        player,
                        target,
                        weapon,
                        skillContext,
                        equipStats
                ).toBuilder();
        boolean inStardewDimension =
                DimensionDamageMapper.isInStardewDimension(target);
        if (customProtection != null) {
            damageRequest
                    .defense(customProtection.defense(), false)
                    .defenseRule(
                            DamageRequest.DefenseRule.STARDEW_PLAYER_DEFENSE
                    );
            addMultiplier(
                    damageRequest,
                    "incoming_event",
                    customProtection.incomingEventMultiplier()
            );
            addMultiplier(
                    damageRequest,
                    "shelter",
                    customProtection.shelterMultiplier()
            );
            addMultiplier(
                    damageRequest,
                    "book_bomb_resistance",
                    customProtection.bombMultiplier()
            );
            addMultiplier(
                    damageRequest,
                    "desert_festival_difficulty",
                    customProtection.difficultyMultiplier()
            );
        }
        WeaponDamageAssemblyRules.Result assemblyResult =
                WeaponDamageAssemblyRules.apply(
                        damageRequest,
                        player,
                        target,
                        weapon,
                        skillContext,
                        spineBoost,
                        isSweepTarget,
                        sweepWeaponType,
                        inStardewDimension,
                        nowTick
                );
        DamageOutcome outcome = DamagePipeline.evaluate(damageRequest.build());
        CombatDamageHistory.record(player, nowTick, outcome);
        EvaluatedWeaponHit hit = new EvaluatedWeaponHit(
                player,
                target,
                source,
                weapon,
                weaponIdentity,
                damageWeaponSnapshot,
                skillContext,
                preparation.reservation(),
                spineBoost,
                outcome,
                equipStats,
                isPrimaryTarget,
                isSweepTarget,
                inStardewDimension,
                assemblyResult.bloodMoonActive(),
                nowTick
        );
        return new IncomingWeaponResolution(hit, outcome.getFinalDamage());
    }

    /**
     * Preserves native protection outside Stardew and permanently prevents
     * native health loss for Stardew-dimension players.
     */
    @SuppressWarnings("null")
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingDamageEvent.Pre event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        Player player = attacker instanceof Player candidate
                ? candidate
                : null;
        long nowTick = target.level().getGameTime();

        if (target instanceof ServerPlayer
                && DimensionDamageMapper.isInStardewDimension(target)) {
            event.setNewDamage(0.0F);
            return;
        }
        if (player == null) return;

        EvaluatedWeaponHit hit;
        if (DimensionDamageMapper.isInStardewDimension(target)) {
            WeaponDamageAdmission admission = classifyWeaponDamageProvenance(
                    source,
                    player,
                    target,
                    nowTick
            );
            if (!isEligibleWeaponDamageSource(admission.provenance())) return;
            IncomingWeaponResolution resolution = evaluateWeaponHit(
                    player,
                    target,
                    source,
                    nowTick,
                    admission,
                    null
            );
            if (resolution == null) return;
            event.setNewDamage(resolution.authoritativeDamage());
            hit = resolution.hit();
        } else {
            hit = WeaponIncomingHitStore.consume(
                    event.getContainer(),
                    nowTick
            );
        }
        if (hit == null) return;
        hit.preparationReservation().commit();
        WeaponEvaluatedHitCoordinator.apply(hit);
    }

    public static CustomHealthWeaponResolution evaluateCustomHealthWeaponHit(
            ServerPlayer target,
            DamageSource source,
            long nowTick,
            CustomHealthProtection protection
    ) {
        Entity attacker = source.getEntity();
        if (!(attacker instanceof Player player)) {
            return CustomHealthWeaponResolution.notOwned();
        }
        WeaponDamageAdmission admission = classifyWeaponDamageProvenance(
                source,
                player,
                target,
                nowTick
        );
        if (!isEligibleWeaponDamageSource(admission.provenance())) {
            return CustomHealthWeaponResolution.notOwned();
        }

        IncomingWeaponResolution resolution = evaluateWeaponHit(
                player,
                target,
                source,
                nowTick,
                admission,
                protection
        );
        return resolution == null
                ? CustomHealthWeaponResolution.notOwned()
                : new CustomHealthWeaponResolution(true, resolution.hit());
    }

    public static void applyCustomHealthWeaponHit(
            EvaluatedWeaponHit hit,
            float appliedDamage,
            long nowTick
    ) {
        if (hit == null || appliedDamage <= 0.0F) return;

        hit.preparationReservation().commit();
        WeaponEvaluatedHitCoordinator.apply(hit);
        DamageNumberContextStore.Meta meta = DamageNumberContextStore.consume(
                hit.attacker(),
                hit.target(),
                hit.source(),
                nowTick
        );
        if (meta == null) return;

        WeaponAppliedHitCoordinator.apply(new ResolvedWeaponHit(
                hit.attacker(),
                hit.target(),
                hit.source(),
                nowTick,
                meta,
                appliedDamage
        ));
    }

    private record IncomingWeaponResolution(
            EvaluatedWeaponHit hit,
            float authoritativeDamage
    ) {
        private static IncomingWeaponResolution suppressed() {
            return new IncomingWeaponResolution(null, 0.0F);
        }
    }

    public record CustomHealthProtection(
            float incomingEventMultiplier,
            float shelterMultiplier,
            float bombMultiplier,
            float difficultyMultiplier,
            float defense
    ) {
        public CustomHealthProtection {
            requireNonNegativeFinite(
                    incomingEventMultiplier,
                    "incomingEventMultiplier"
            );
            requireNonNegativeFinite(shelterMultiplier, "shelterMultiplier");
            requireNonNegativeFinite(bombMultiplier, "bombMultiplier");
            requireNonNegativeFinite(
                    difficultyMultiplier,
                    "difficultyMultiplier"
            );
            requireNonNegativeFinite(defense, "defense");
        }
    }

    public record CustomHealthWeaponResolution(
            boolean weaponOwned,
            EvaluatedWeaponHit hit
    ) {
        private static CustomHealthWeaponResolution notOwned() {
            return new CustomHealthWeaponResolution(false, null);
        }

        public boolean suppressed() {
            return weaponOwned && hit == null;
        }
    }

    private static void addMultiplier(
            DamageRequest.Builder request,
            String id,
            float multiplier
    ) {
        if (multiplier != 1.0F) {
            request.addPreDefenseAdjustment(
                    DamageAdjustment.multiply(id, multiplier)
            );
        }
    }

    private static void requireNonNegativeFinite(float value, String field) {
        if (!Float.isFinite(value) || value < 0.0F) {
            throw new IllegalArgumentException(
                    field + " must be finite and non-negative"
            );
        }
    }

    enum WeaponDamageProvenance {
        PLAYER_ATTACK,
        PROJECT_SKILL,
        UNAUTHORED_PLAYER_ATTACK,
        THORNS,
        PROJECTILE,
        EXPLOSION,
        OTHER
    }

    static boolean isEligibleWeaponDamageSource(
            WeaponDamageProvenance provenance
    ) {
        return provenance == WeaponDamageProvenance.PLAYER_ATTACK
                || provenance == WeaponDamageProvenance.PROJECT_SKILL;
    }

    private static WeaponDamageAdmission classifyWeaponDamageProvenance(
            DamageSource source,
            Player player,
            LivingEntity target,
            long nowTick
    ) {
        if (source.is(DamageTypes.PLAYER_ATTACK)) {
            // WeaponSkillDamage emits playerAttack after binding its authored
            // context. It must stay independent from ordinary Player.attack.
            if (WeaponSkillContextStore.hasPending(player, nowTick)) {
                return admission(WeaponDamageProvenance.PROJECT_SKILL);
            }
            OrdinaryWeaponAttackFrameStore.Frame ordinaryFrame =
                    OrdinaryWeaponAttackFrameStore.claim(
                            player,
                            target,
                            source,
                            nowTick
                    );
            return ordinaryFrame == null
                    ? admission(
                            WeaponDamageProvenance.UNAUTHORED_PLAYER_ATTACK
                    )
                    : new WeaponDamageAdmission(
                            WeaponDamageProvenance.PLAYER_ATTACK,
                            ordinaryFrame.weaponSnapshot()
                    );
        }
        if (source.is(DamageTypes.THORNS)) {
            return admission(WeaponDamageProvenance.THORNS);
        }
        if (source.is(DamageTypeTags.IS_PROJECTILE)) {
            // Pending context alone is never provenance. Meowmere is the one
            // authored weapon projectile that binds its immutable release
            // snapshot synchronously around this exact hurt call.
            WeaponDamageProvenance provenance = source.getDirectEntity()
                            instanceof MeowmereProjectileEntity
                    && WeaponSkillContextStore.hasPending(player, nowTick)
                    ? WeaponDamageProvenance.PROJECT_SKILL
                    : WeaponDamageProvenance.PROJECTILE;
            return admission(provenance);
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return admission(WeaponDamageProvenance.EXPLOSION);
        }
        return admission(WeaponDamageProvenance.OTHER);
    }

    private static WeaponDamageAdmission admission(
            WeaponDamageProvenance provenance
    ) {
        return new WeaponDamageAdmission(provenance, null);
    }

    private record WeaponDamageAdmission(
            WeaponDamageProvenance provenance,
            WeaponDamageSnapshot ordinaryWeaponSnapshot
    ) {
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;

        Entity attacker = event.getSource().getEntity();
        if (!(attacker instanceof Player player)) return;

        long nowTick = target.level().getGameTime();
        DamageNumberContextStore.Meta meta = DamageNumberContextStore.consume(
                player,
                target,
                event.getSource(),
                nowTick
        );
        if (meta == null) return;

        WeaponAppliedHitCoordinator.apply(
                ResolvedWeaponHit.from(event, player, meta, nowTick)
        );
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
}
