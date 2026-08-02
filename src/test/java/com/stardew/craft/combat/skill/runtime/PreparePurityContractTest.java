package com.stardew.craft.combat.skill.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreparePurityContractTest {
    /*
     * Exact, reviewed signatures used while building a skill execution plan.
     * Any new call or constructor fails closed until deliberately added here.
     */
    private static final Set<String> ALLOWED_PREPARE_OPERATIONS = signatures("""
            com.stardew.craft.combat.CombatHealing#currentHealth(net.minecraft.server.level.ServerPlayer)
            com.stardew.craft.combat.WeaponStats#fromItemStack(net.minecraft.world.item.ItemStack)
            com.stardew.craft.combat.WeaponStats#getAverageDamage()
            com.stardew.craft.combat.skill.BrokenTridentCatchTracker#consumeForBegin(net.minecraft.server.level.ServerPlayer,long)
            com.stardew.craft.combat.skill.BrokenTridentCatchTracker#hasFishInInventory(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.DragonBreathTracker#consumeForMajor(net.minecraft.server.level.ServerPlayer)
            com.stardew.craft.combat.skill.GalaxyDaggerMarkTracker#consumeDuringBegin(com.stardew.craft.combat.skill.runtime.SkillInstance,net.minecraft.world.entity.LivingEntity,net.minecraft.world.entity.player.Player,long)
            com.stardew.craft.combat.skill.InfinityDaggerMarkTracker#consumeDuringBegin(com.stardew.craft.combat.skill.runtime.SkillInstance,net.minecraft.world.entity.LivingEntity,net.minecraft.world.entity.player.Player,long)
            com.stardew.craft.combat.skill.InfinityDaggerMarkTracker#isMarkedBy(net.minecraft.world.entity.LivingEntity,net.minecraft.world.entity.player.Player,long)
            com.stardew.craft.combat.skill.InsectDashChainState#getNextStage(net.minecraft.server.level.ServerPlayer,long)
            com.stardew.craft.combat.skill.LavaKatanaMarkTracker#isMarkedBy(net.minecraft.world.entity.LivingEntity,net.minecraft.world.entity.player.Player,long)
            com.stardew.craft.combat.skill.OssifiedMarkTracker#isMarkedBy(net.minecraft.world.entity.LivingEntity,net.minecraft.world.entity.player.Player,long)
            com.stardew.craft.combat.skill.SilverSaberFoldbackState#getOrigin(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.SilverSaberFoldbackState#isActive(net.minecraft.world.entity.player.Player,long)
            com.stardew.craft.combat.skill.SingularityTracker#consumeAll(net.minecraft.server.level.ServerPlayer)
            com.stardew.craft.combat.skill.SingularityTracker#getStacks(net.minecraft.server.level.ServerPlayer)
            com.stardew.craft.combat.skill.SkillContext#builder()
            com.stardew.craft.combat.skill.SkillContext.Builder#build()
            com.stardew.craft.combat.skill.SkillContext.Builder#damageMultiplier(float)
            com.stardew.craft.combat.skill.SkillContext.Builder#guaranteedCrit(boolean)
            com.stardew.craft.combat.skill.SkillContext.Builder#skillId(java.lang.String)
            com.stardew.craft.combat.skill.SkillContext.Builder#tier(com.stardew.craft.combat.skill.SkillContext.SkillTier)
            com.stardew.craft.combat.skill.StartrailTracker#consumeAll(net.minecraft.server.level.ServerPlayer)
            com.stardew.craft.combat.skill.StartrailTracker#getStacks(net.minecraft.server.level.ServerPlayer)
            com.stardew.craft.combat.skill.TemperedFireRingTracker#beginBilletCastDuringBegin(com.stardew.craft.combat.skill.runtime.SkillInstance,net.minecraft.server.level.ServerPlayer,long,int,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher#sendSkillAnim(net.minecraft.server.level.ServerPlayer,java.lang.String,java.lang.String,int)
            com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher#sendSkillAnim(net.minecraft.server.level.ServerPlayer,java.lang.String,java.lang.String,int,int,int)
            com.stardew.craft.combat.skill.WeaponSkillAnimationLock#setLock(net.minecraft.world.entity.player.Player,long,int)
            com.stardew.craft.combat.skill.handler.CarvingThrustExecutionState#<init>(long,java.util.UUID)
            com.stardew.craft.combat.skill.handler.CarvingThrustSkillHandler#findInitialTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.ClaymoreFoldbackExecutionState#<init>(long,int,java.util.UUID)
            com.stardew.craft.combat.skill.handler.CrescentSlashSkillHandler.State#<init>(long,long)
            com.stardew.craft.combat.skill.handler.DarkSwordBloodDebtExecutionState#<init>(long,int)
            com.stardew.craft.combat.skill.handler.DarkSwordBloodDebtSkillHandler#healthCost(float)
            com.stardew.craft.combat.skill.handler.DarkSwordBloodMoonExecutionState#<init>(long,int,int,net.minecraft.resources.ResourceKey,float,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.DarkSwordBloodMoonSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.DarkSwordBloodMoonSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.DesperatePlunderExecutionState#<init>(java.util.UUID)
            com.stardew.craft.combat.skill.handler.DragonBreathJudgementExecutionState#<init>()
            com.stardew.craft.combat.skill.handler.DragonBreathJudgementSkillHandler#criticalChanceBonus(int)
            com.stardew.craft.combat.skill.handler.DragonBreathJudgementSkillHandler#extraStacks(int)
            com.stardew.craft.combat.skill.handler.DragonBreathThrustExecutionState#<init>(long,int)
            com.stardew.craft.combat.skill.handler.DragonBreathThrustSkillHandler#distancePointToSegment2D(double,double,double,double,double,double)
            com.stardew.craft.combat.skill.handler.DragonBreathThrustSkillHandler#findTargetsAlongPath(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,double)
            com.stardew.craft.combat.skill.handler.DragonBreathThrustSkillHandler#horizontalLook(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.handler.DragonBreathThrustSkillHandler#isPathClear(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.DragonBreathThrustSkillHandler#isSafePosition(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.DragonBreathThrustSkillHandler#resolveSafeDashEnd(net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.handler.DragontoothShivBreathExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int)
            com.stardew.craft.combat.skill.handler.DragontoothShivBreathSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.DragontoothShivBreathSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.DragontoothShivStabSkillHandler#findTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.DwarfDaggerRushExecutionState#<init>(long,int)
            com.stardew.craft.combat.skill.handler.DwarfDaggerThrustExecutionState#<init>(net.minecraft.server.level.ServerPlayer,long,net.minecraft.world.phys.Vec3,int)
            com.stardew.craft.combat.skill.handler.DwarfDaggerThrustSkillHandler#computeDashEnd(net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.handler.DwarfDaggerThrustSkillHandler#findSafePosition(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.DwarfDaggerThrustSkillHandler#horizontalLook(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.handler.DwarfDaggerThrustSkillHandler#requireMovementUnlocked(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,java.lang.String)
            com.stardew.craft.combat.skill.handler.DwarfFortressExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.DwarfFortressSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.DwarfFortressSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.DwarfRuneGuardExecutionState#<init>(java.util.UUID)
            com.stardew.craft.combat.skill.handler.ElfBladeLeafExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown)
            com.stardew.craft.combat.skill.handler.EternalCollapseExecutionState#<init>(net.minecraft.world.phys.Vec3,long,int,int,double,float,float,boolean,float,java.lang.String,net.minecraft.resources.ResourceKey)
            com.stardew.craft.combat.skill.handler.EternalCollapseSkillHandler#chooseCollapseCenter(net.minecraft.world.phys.Vec3,java.util.List)
            com.stardew.craft.combat.skill.handler.EternalCollapseSkillHandler#criticalChanceBonusForStacks(int)
            com.stardew.craft.combat.skill.handler.EternalCollapseSkillHandler#extraStrikesForStacks(int)
            com.stardew.craft.combat.skill.handler.EternalCollapseSkillHandler#hasFinalStrikeForStacks(int)
            com.stardew.craft.combat.skill.handler.EternalCollapseSkillHandler#snapshotTargetsInRadius(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,double)
            com.stardew.craft.combat.skill.handler.FemurSlamExecutionState#<init>(long,int,com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown)
            com.stardew.craft.combat.skill.handler.FishcatchThrustExecutionState#<init>(long,java.util.UUID)
            com.stardew.craft.combat.skill.handler.FishcatchThrustSkillHandler#findInitialTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.ForestBlessingSkillHandler.State#<init>(long,int)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#behindCandidates(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,double,double)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#findSafeBehindPosition(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.LivingEntity,double)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#findSafePosition(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#isUsableTarget(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.LivingEntity)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#resolveCast(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#rotate(net.minecraft.world.phys.Vec3,double)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler#targetOffsetPosition(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler.CastPlan#<init>(net.minecraft.world.entity.LivingEntity,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarleapSkillHandler.CastPlan#target()
            com.stardew.craft.combat.skill.handler.GalaxyDaggerStarstabSkillHandler#findInitialTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.GalaxyDaggerThrustExecutionState#<init>(long,java.util.UUID)
            com.stardew.craft.combat.skill.handler.GalaxyJudgementExecutionState#<init>(long,int,int,double,float,java.lang.String,net.minecraft.resources.ResourceKey,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.GalaxyJudgementSkillHandler#createMainSlashContext(com.stardew.craft.item.weapon.WeaponSkillData,boolean)
            com.stardew.craft.combat.skill.handler.GalaxyJudgementSkillHandler#extraHitsForStacks(int)
            com.stardew.craft.combat.skill.handler.GalaxyJudgementSkillHandler#findMainSlashTargets(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.HolyDomainExecutionState#<init>(long,int,float)
            com.stardew.craft.combat.skill.handler.HolyDomainSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.HolyDomainSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#behindCandidates(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,double,double)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#findSafeBehindPosition(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.LivingEntity,double)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#findSafePosition(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#isCastContextValid(boolean,boolean,boolean)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#resolveCast(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#rotate(net.minecraft.world.phys.Vec3,double)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler#targetOffsetPosition(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler.CastPlan#<init>(net.minecraft.world.entity.LivingEntity,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabSkillHandler.CastPlan#target()
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityBackstabExecutionState#<init>(java.util.UUID)
            com.stardew.craft.combat.skill.handler.InfinityDaggerSingularityStabSkillHandler#findInitialTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.InfinityDaggerThrustExecutionState#<init>(long,java.util.UUID)
            com.stardew.craft.combat.skill.handler.InsectDashExecutionState#<init>(int,java.util.Collection,com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,int)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#canPayEnergy(float,boolean,boolean,int)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#distanceToSegmentSqr(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#energyCostForStage(int)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#findSafePosition(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#findTargetsAlongPath(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,double)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#horizontalLook(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#isPathClear(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#resolveSafeDashEnd(net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.handler.InsectDashSkillHandler#startCooldown(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,com.stardew.craft.combat.skill.runtime.SkillInstance)
            com.stardew.craft.combat.skill.handler.InsectEyeStanceExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,java.lang.String,com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown)
            com.stardew.craft.combat.skill.handler.IridiumNeedleFrenzyExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int)
            com.stardew.craft.combat.skill.handler.IridiumNeedleFrenzySkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.IridiumNeedleFrenzySkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.IridiumNeedleThrustExecutionState#<init>(long,java.util.UUID)
            com.stardew.craft.combat.skill.handler.IridiumNeedleThrustSkillHandler#findInitialTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.IronDirkThrustSkillHandler#findNearestTargetEntityInFront(net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.handler.IronDirkThrustSkillHandler#findRotatedSafePosition(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.LivingEntity,net.minecraft.world.phys.Vec3,double)
            com.stardew.craft.combat.skill.handler.IronDirkThrustSkillHandler#findSafePosition(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.IronDirkThrustSkillHandler#findSafePositionAroundTarget(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.LivingEntity,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.IronDirkThrustSkillHandler#getBehindPosition(net.minecraft.world.entity.LivingEntity,net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.handler.IronDirkThrustSkillHandler#getFrontPosition(net.minecraft.world.entity.LivingEntity,net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.handler.IronDirkThrustSkillHandler#rotateVector(net.minecraft.world.phys.Vec3,double)
            com.stardew.craft.combat.skill.handler.LavaKatanaBrandSkillHandler#findTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int)
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler#findMarkedTargetsInRange(net.minecraft.server.level.ServerLevel,net.minecraft.world.entity.player.Player,long,double)
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler#hasCastTarget(int,boolean)
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler#resolveCastPlan(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler.CastPlan#<init>(java.util.List,net.minecraft.world.entity.LivingEntity)
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler.CastPlan#fallbackTarget()
            com.stardew.craft.combat.skill.handler.LavaKatanaReverbSkillHandler.CastPlan#markedTargets()
            com.stardew.craft.combat.skill.handler.LightCounterExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,java.lang.String,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.MeowmereShotSkillHandler#projectileDamage(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.MeowmereShotSkillHandler#projectileDamage(com.stardew.craft.item.weapon.WeaponData)
            com.stardew.craft.combat.skill.handler.MeowmereShotSkillHandler.State#<init>(com.stardew.craft.entity.projectile.MeowmereProjectileEntity,long)
            com.stardew.craft.combat.skill.handler.MeowmereSymphonySkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.MeowmereSymphonySkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.MeowmereSymphonySkillHandler#createProjectiles(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,float)
            com.stardew.craft.combat.skill.handler.MeowmereSymphonySkillHandler#projectileDamage(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.MeowmereSymphonySkillHandler#projectileDamage(com.stardew.craft.item.weapon.WeaponData,int)
            com.stardew.craft.combat.skill.handler.MeowmereSymphonySkillHandler#yawOffsetDegrees(int)
            com.stardew.craft.combat.skill.handler.MeowmereSymphonySkillHandler.State#<init>(java.util.List,long)
            com.stardew.craft.combat.skill.handler.ObsidianCrackExecutionState#<init>(long,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.ObsidianCrackSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.ObsidianCrackSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.ObsidianCrackSkillHandler#createCrackLine(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.ObsidianCrackSkillHandler#horizontalLook(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.handler.ObsidianCrackSkillHandler.CrackLine#<init>(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,float,float)
            com.stardew.craft.combat.skill.handler.ObsidianCrackSkillHandler.CrackLine#end()
            com.stardew.craft.combat.skill.handler.ObsidianCrackSkillHandler.CrackLine#start()
            com.stardew.craft.combat.skill.handler.OssifiedExecutionSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.ShadowDaggerExecuteSkillHandler.State#<init>(java.util.UUID,boolean)
            com.stardew.craft.combat.skill.handler.OssifiedExecutionSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.OssifiedExecutionSkillHandler#findMarkedTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.OssifiedExecutionState#<init>(net.minecraft.world.phys.Vec3,float,net.minecraft.resources.ResourceKey,long,int)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#findSafePosition(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#findTargetFrontPosition(net.minecraft.world.entity.player.Player,net.minecraft.world.entity.LivingEntity)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#horizontalLook(net.minecraft.world.entity.player.Player)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#isPathClear(net.minecraft.world.entity.player.Player,net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#modeFor(boolean,boolean)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#resolvePlan(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#resolveSafeDashEnd(net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#rotate(net.minecraft.world.phys.Vec3,double)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler#targetOffsetPosition(net.minecraft.world.entity.LivingEntity,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler.CastPlan#<init>(com.stardew.craft.combat.skill.handler.SilverFoldbackSkillHandler.CastMode,net.minecraft.world.entity.LivingEntity,net.minecraft.world.phys.Vec3)
            com.stardew.craft.combat.skill.handler.SingularityEvolveExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,java.lang.String,boolean,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.SingularityEvolveSkillHandler#evolvedForStacks(int)
            com.stardew.craft.combat.skill.handler.StartrailRiftExecutionState#<init>(java.util.Collection)
            com.stardew.craft.combat.skill.handler.StartrailRiftSkillHandler#isBoostedForStacks(int)
            com.stardew.craft.combat.skill.handler.SteelFalchionLineExecutionState#<init>(net.minecraft.resources.ResourceKey,long,net.minecraft.world.phys.Vec3,float,float,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.SteelFalchionLineSkillHandler#findTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.SteelFalchionTraceExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,net.minecraft.world.phys.Vec3,float,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.SteelFalchionTraceSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.SteelFalchionTraceSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.SteelSpineFuryExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown)
            com.stardew.craft.combat.skill.handler.TemperedBilletSkillHandler#assignedTargetIndex(int,int)
            com.stardew.craft.combat.skill.handler.TemperedBilletSkillHandler#createProjectiles(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,java.util.List,float,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.TemperedBilletSkillHandler#findTargetsInRadius(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,double)
            com.stardew.craft.combat.skill.handler.TemperedQuenchExecutionState#<init>(net.minecraft.resources.ResourceKey)
            com.stardew.craft.combat.skill.handler.TemplarJudgementExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,java.util.List,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.combat.skill.handler.TemplarJudgementSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.TemplarJudgementSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.TemplarJudgementSkillHandler#findTargets(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.TemplarVowExecutionState#<init>(net.minecraft.resources.ResourceKey,long,int,com.stardew.craft.combat.skill.WeaponDamageSnapshot,com.stardew.craft.combat.skill.runtime.DeferredSkillCooldown)
            com.stardew.craft.combat.skill.handler.TideAnchorSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.TideAnchorSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.handler.TideAnchorSkillHandler.State#<init>(net.minecraft.resources.ResourceKey,com.stardew.craft.entity.projectile.TideAnchorProjectileEntity,long)
            com.stardew.craft.combat.skill.handler.TideMarkSkillHandler#findTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.TideReelSkillHandler.State#<init>(java.util.UUID,boolean)
            com.stardew.craft.combat.skill.handler.TideReelSkillHandler#appliedCooldownTicks(int,boolean)
            com.stardew.craft.combat.skill.handler.TideReelSkillHandler#findTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.WickedKrisNestBurstSkillHandler#findTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.WickedKrisVenomRippleSkillHandler.State#<init>()
            com.stardew.craft.combat.skill.handler.WickedKrisVenomRippleSkillHandler#findTargets(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.WindSpireThrustSkillHandler#findNearestTargetInFront(net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.handler.YetiToothMarkSkillHandler#findTarget(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.YetiToothSpineExecutionState#<init>(net.minecraft.resources.ResourceKey)
            com.stardew.craft.combat.skill.handler.YetiToothSpineSkillHandler#canPayEnergy(com.stardew.craft.combat.skill.runtime.SkillExecutionContext)
            com.stardew.craft.combat.skill.handler.YetiToothSpineSkillHandler#canPayEnergy(float,boolean,boolean)
            com.stardew.craft.combat.skill.runtime.SkillExecutionContext#hand()
            com.stardew.craft.combat.skill.runtime.SkillExecutionContext#nowTick()
            com.stardew.craft.combat.skill.runtime.SkillExecutionContext#player()
            com.stardew.craft.combat.skill.runtime.SkillExecutionContext#skillData()
            com.stardew.craft.combat.skill.runtime.SkillExecutionContext#weapon()
            com.stardew.craft.combat.skill.runtime.SkillExecutionContext#weaponId()
            com.stardew.craft.combat.skill.runtime.SkillExecutionContext#weaponSnapshot()
            com.stardew.craft.combat.skill.runtime.SkillInstance#initializeExecutionState(com.stardew.craft.combat.skill.runtime.SkillInstance.ExecutionState)
            com.stardew.craft.combat.skill.runtime.SkillInstance#registerBeginFailureCleanup(java.lang.Runnable)
            com.stardew.craft.combat.skill.runtime.SkillInstance#registerCommittedEffect(java.lang.Runnable)
            com.stardew.craft.combat.skill.runtime.SkillInstance#setTargetEntityIds(java.util.List)
            com.stardew.craft.combat.skill.runtime.SkillTargeting#findNearestTargetInFront(net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.runtime.SkillTargeting#findTargetEntity(net.minecraft.world.entity.player.Player,double)
            com.stardew.craft.combat.skill.runtime.SkillTargeting#findTargetsInArc(net.minecraft.world.entity.player.Player,double,double)
            com.stardew.craft.combat.skill.runtime.WeaponSkillMovementControl#isLocked(net.minecraft.world.entity.LivingEntity,long)
            com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime#commitCooldown(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,com.stardew.craft.combat.skill.runtime.SkillInstance,int)
            com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime#consumeEnergyDuringBegin(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,com.stardew.craft.combat.skill.runtime.SkillInstance,float)
            com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime#deferCooldown(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,com.stardew.craft.combat.skill.runtime.SkillInstance,int)
            com.stardew.craft.combat.skill.runtime.WeaponSkillRuntime#spendHealthDuringBegin(com.stardew.craft.combat.skill.runtime.SkillExecutionContext,com.stardew.craft.combat.skill.runtime.SkillInstance,float,float)
            com.stardew.craft.entity.projectile.MeowmereProjectileEntity#<init>(net.minecraft.world.level.Level,net.minecraft.world.entity.LivingEntity,float,int,java.lang.String,com.stardew.craft.combat.skill.SkillContext.SkillTier,float,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.entity.projectile.TemperedBilletProjectileEntity#<init>(net.minecraft.world.level.Level,net.minecraft.world.entity.LivingEntity,float,java.lang.String,net.minecraft.world.entity.LivingEntity,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.entity.projectile.TideAnchorProjectileEntity#<init>(net.minecraft.world.level.Level,net.minecraft.world.entity.LivingEntity,java.lang.String,float,com.stardew.craft.combat.skill.WeaponDamageSnapshot)
            com.stardew.craft.item.weapon.IStardewWeapon#getWeaponData()
            com.stardew.craft.item.weapon.WeaponData#getAverageDamage()
            com.stardew.craft.item.weapon.WeaponSkillData#getCooldown()
            com.stardew.craft.item.weapon.WeaponSkillData#getDamagePercent()
            com.stardew.craft.item.weapon.WeaponSkillData#getId()
            com.stardew.craft.player.PlayerStardewDataAPI#getEnergy(net.minecraft.server.level.ServerPlayer)
            java.lang.Double#compare(double,double)
            java.lang.IllegalStateException#<init>(java.lang.String)
            java.lang.Math#atan2(double,double)
            java.lang.Math#ceil(double)
            java.lang.Math#cos(double)
            java.lang.Math#max(double,double)
            java.lang.Math#max(float,float)
            java.lang.Math#max(int,int)
            java.lang.Math#min(double,double)
            java.lang.Math#min(int,int)
            java.lang.Math#sin(double)
            java.lang.Math#sqrt(double)
            java.lang.Math#toRadians(double)
            java.util.ArrayList#<init>(int)
            java.util.Collection#removeIf(java.util.function.Predicate)
            java.util.Collection#stream()
            java.util.Collections#shuffle(java.util.List,java.util.Random)
            java.util.List#add(java.lang.Object)
            java.util.List#copyOf(java.util.Collection)
            java.util.List#get(int)
            java.util.List#isEmpty()
            java.util.List#of()
            java.util.List#of(java.lang.Object)
            java.util.List#size()
            java.util.List#sort(java.util.Comparator)
            java.util.Objects#requireNonNull(java.lang.Object,java.lang.String)
            java.util.Optional#ifPresent(java.util.function.Consumer)
            java.util.Optional#isPresent()
            java.util.Random#<init>(long)
            java.util.stream.Stream#limit(long)
            java.util.stream.Stream#map(java.util.function.Function)
            java.util.stream.Stream#toList()
            net.minecraft.core.BlockPos#containing(net.minecraft.core.Position)
            net.minecraft.core.Holder#direct(java.lang.Object)
            net.minecraft.resources.ResourceLocation#getPath()
            net.minecraft.server.level.ServerPlayer#serverLevel()
            net.minecraft.util.Mth#clamp(double,double,double)
            net.minecraft.util.RandomSource#nextFloat()
            net.minecraft.util.RandomSource#nextLong()
            net.minecraft.world.entity.Entity#distanceToSqr(double,double,double)
            net.minecraft.world.entity.Entity#distanceToSqr(net.minecraft.world.entity.Entity)
            net.minecraft.world.entity.Entity#getBbHeight()
            net.minecraft.world.entity.Entity#getBbWidth()
            net.minecraft.world.entity.Entity#getBoundingBox()
            net.minecraft.world.entity.Entity#getEyePosition()
            net.minecraft.world.entity.Entity#getId()
            net.minecraft.world.entity.Entity#getLookAngle()
            net.minecraft.world.entity.Entity#getUUID()
            net.minecraft.world.entity.Entity#getX()
            net.minecraft.world.entity.Entity#getXRot()
            net.minecraft.world.entity.Entity#getY()
            net.minecraft.world.entity.Entity#getYRot()
            net.minecraft.world.entity.Entity#getZ()
            net.minecraft.world.entity.Entity#isRemoved()
            net.minecraft.world.entity.Entity#level()
            net.minecraft.world.entity.Entity#position()
            net.minecraft.world.entity.LivingEntity#getHealth()
            net.minecraft.world.entity.LivingEntity#getMaxHealth()
            net.minecraft.world.entity.LivingEntity#hasEffect(net.minecraft.core.Holder)
            net.minecraft.world.entity.LivingEntity#isAlive()
            net.minecraft.world.entity.LivingEntity#isPickable()
            net.minecraft.world.entity.LivingEntity#startUsingItem(net.minecraft.world.InteractionHand)
            net.minecraft.world.entity.player.Player#getAbilities()
            net.minecraft.world.entity.projectile.Projectile#shootFromRotation(net.minecraft.world.entity.Entity,float,float,float,float,float)
            net.minecraft.world.item.ItemStack#getItem()
            net.minecraft.world.level.BlockGetter#clip(net.minecraft.world.level.ClipContext)
            net.minecraft.world.level.ClipContext#<init>(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3,net.minecraft.world.level.ClipContext.Block,net.minecraft.world.level.ClipContext.Fluid,net.minecraft.world.entity.Entity)
            net.minecraft.world.level.CollisionGetter#noCollision(net.minecraft.world.entity.Entity,net.minecraft.world.phys.AABB)
            net.minecraft.world.level.EntityGetter#getEntitiesOfClass(java.lang.Class,net.minecraft.world.phys.AABB,java.util.function.Predicate)
            net.minecraft.world.level.Level#dimension()
            net.minecraft.world.level.Level#getWorldBorder()
            net.minecraft.world.level.border.WorldBorder#isWithinBounds(net.minecraft.core.BlockPos)
            net.minecraft.world.phys.AABB#<init>(double,double,double,double,double,double)
            net.minecraft.world.phys.AABB#<init>(net.minecraft.world.phys.Vec3,net.minecraft.world.phys.Vec3)
            net.minecraft.world.phys.AABB#expandTowards(net.minecraft.world.phys.Vec3)
            net.minecraft.world.phys.AABB#inflate(double,double,double)
            net.minecraft.world.phys.AABB#move(double,double,double)
            net.minecraft.world.phys.HitResult#getLocation()
            net.minecraft.world.phys.HitResult#getType()
            net.minecraft.world.phys.Vec3#<init>(double,double,double)
            net.minecraft.world.phys.Vec3#add(double,double,double)
            net.minecraft.world.phys.Vec3#add(net.minecraft.world.phys.Vec3)
            net.minecraft.world.phys.Vec3#distanceToSqr(net.minecraft.world.phys.Vec3)
            net.minecraft.world.phys.Vec3#dot(net.minecraft.world.phys.Vec3)
            net.minecraft.world.phys.Vec3#horizontalDistance()
            net.minecraft.world.phys.Vec3#horizontalDistanceSqr()
            net.minecraft.world.phys.Vec3#lengthSqr()
            net.minecraft.world.phys.Vec3#normalize()
            net.minecraft.world.phys.Vec3#scale(double)
            net.minecraft.world.phys.Vec3#subtract(net.minecraft.world.phys.Vec3)
            net.neoforged.neoforge.registries.DeferredHolder#get()
            """);

    @Test
    void runtimeHandlerPreparationUsesOnlyAuditedOperations()
            throws IOException {
        PreparePurityAuditor.AuditResult result =
                PreparePurityAuditor.auditPaths(
                        handlerSources(),
                        ALLOWED_PREPARE_OPERATIONS
                );
        assertTrue(
                result.valid(),
                () -> "Prepare purity violations:\n"
                        + String.join("\n", result.violations())
                        + "\nActual signatures:\n"
                        + result.actualSignatures().stream()
                                .sorted()
                                .collect(Collectors.joining("\n"))
        );
        assertTrue(
                ALLOWED_PREPARE_OPERATIONS.containsAll(
                        result.actualSignatures()
                )
        );
        assertTrue(
                result.actualSignatures().containsAll(
                        ALLOWED_PREPARE_OPERATIONS
                ),
                () -> "Remove stale prepare-operation allowlist entries:\n"
                        + ALLOWED_PREPARE_OPERATIONS.stream()
                                .filter(signature -> !result.actualSignatures()
                                        .contains(signature))
                                .sorted()
                                .collect(Collectors.joining("\n"))
        );
    }

    @Test
    void unknownAndTransitivelyImpureHelpersFailClosed() throws IOException {
        PreparePurityAuditor.AuditResult result = auditSynthetic(
                """
                package sample;
                final class Sample {
                    void begin(Context context, Instance instance) {
                        helper(context);
                    }
                    void helper(Context context) { context.damage(); }
                }
                final class Context { void damage() {} }
                final class Instance {
                    void registerCommittedEffect(Runnable effect) {}
                    void registerBeginFailureCleanup(Runnable cleanup) {}
                }
                """,
                Set.of("sample.Sample#helper(sample.Context)")
        );
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(message ->
                message.contains("sample.Context#damage()")
        ));
    }

    @Test
    void inlineCommittedAndCleanupCallbacksAreDeferredBoundaries()
            throws IOException {
        PreparePurityAuditor.AuditResult result = auditSynthetic(
                """
                package sample;
                final class Sample {
                    void begin(Context context, Instance instance) {
                        instance.registerCommittedEffect(
                                () -> context.damage()
                        );
                        instance.registerBeginFailureCleanup(
                                context::restore
                        );
                    }
                }
                final class Context {
                    void damage() {}
                    void restore() {}
                }
                final class Instance {
                    void registerCommittedEffect(Runnable effect) {}
                    void registerBeginFailureCleanup(Runnable cleanup) {}
                }
                """,
                Set.of(
                        "sample.Instance#registerCommittedEffect(java.lang.Runnable)",
                        "sample.Instance#registerBeginFailureCleanup(java.lang.Runnable)"
                )
        );
        assertTrue(result.valid(), result.violations().toString());
    }

    @Test
    void nonCommittedMemberReferencesFailClosed() throws IOException {
        PreparePurityAuditor.AuditResult result = auditSynthetic(
                """
                package sample;
                final class Sample {
                    void begin(Context context, Instance instance) {
                        Runnable effect = context::damage;
                    }
                }
                final class Context { void damage() {} }
                final class Instance {}
                """,
                Set.of()
        );
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(message ->
                message.contains("sample.Context#damage()")
        ));
    }

    @Test
    void sameHandlerMemberReferencesAreAuditedTransitively()
            throws IOException {
        PreparePurityAuditor.AuditResult result = auditSynthetic(
                """
                package sample;
                final class Sample {
                    private final Context context = new Context();
                    void begin(Context ignored, Instance instance) {
                        Runnable effect = this::helper;
                    }
                    void helper() { context.damage(); }
                }
                final class Context { void damage() {} }
                final class Instance {}
                """,
                Set.of("sample.Sample#helper()")
        );
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(message ->
                message.contains("sample.Context#damage()")
        ));
    }

    @Test
    void eagerCallbackFactoriesAndMemberWritesFailClosed()
            throws IOException {
        PreparePurityAuditor.AuditResult result = auditSynthetic(
                """
                package sample;
                final class Sample {
                    void begin(Context context, Instance instance) {
                        context.value = 2;
                        instance.registerCommittedEffect(build(context));
                    }
                    Runnable build(Context context) {
                        return context::damage;
                    }
                }
                final class Context {
                    int value;
                    void damage() {}
                }
                final class Instance {
                    void registerCommittedEffect(Runnable effect) {}
                    void registerBeginFailureCleanup(Runnable cleanup) {}
                }
                """,
                Set.of(
                        "sample.Instance#registerCommittedEffect(java.lang.Runnable)"
                )
        );
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(message ->
                message.contains("non-local write context.value")
        ));
        assertTrue(result.violations().stream().anyMatch(message ->
                message.contains("sample.Sample#build(sample.Context)")
        ));
    }

    private static PreparePurityAuditor.AuditResult auditSynthetic(
            String source,
            Set<String> allowed
    ) throws IOException {
        return PreparePurityAuditor.auditSource(
                "sample.Sample",
                source,
                allowed
        );
    }

    private static Set<String> signatures(String values) {
        return values.lines()
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static List<Path> handlerSources() throws IOException {
        Path root = mainJavaRoot().resolve(Path.of(
                "com", "stardew", "craft", "combat", "skill", "handler"
        ));
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(path -> path.getFileName().toString()
                            .endsWith("SkillHandler.java"))
                    .sorted()
                    .toList();
        }
    }

    private static Path mainJavaRoot() throws IOException {
        Path relativeRoot = Path.of("src", "main", "java");
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path root = current.resolve(relativeRoot);
            if (Files.isDirectory(root)) {
                return root;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate " + relativeRoot);
    }
}
