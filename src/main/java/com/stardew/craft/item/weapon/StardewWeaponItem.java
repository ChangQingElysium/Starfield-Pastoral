package com.stardew.craft.item.weapon;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.combat.WeaponForgeData;
import com.stardew.craft.combat.WeaponStats;
import com.stardew.craft.combat.skill.SkillContext;
import com.stardew.craft.combat.skill.WeaponSkillAnimationLock;
import com.stardew.craft.combat.skill.WeaponSkillAnimationDispatcher;
import com.stardew.craft.combat.skill.LightCounterParryState;
import com.stardew.craft.combat.skill.SilverSaberFoldbackState;
import com.stardew.craft.combat.skill.SilverSaberSkillHelper;
import com.stardew.craft.combat.skill.WeaponSkillContextStore;
import com.stardew.craft.combat.skill.WeaponSkillCooldowns;
import com.stardew.craft.combat.skill.DesperatePlunderTracker;
import com.stardew.craft.combat.skill.TideMarkTracker;
import com.stardew.craft.combat.skill.TemplarVowTracker;
import com.stardew.craft.combat.skill.TemplarJudgementTracker;
import com.stardew.craft.combat.skill.InsectEyeStanceTracker;
import com.stardew.craft.combat.skill.InsectDashChainState;
import com.stardew.craft.combat.skill.ObsidianCrackTracker;
import com.stardew.craft.combat.skill.CarvingKnifeThrustTracker;
import com.stardew.craft.combat.skill.BrokenTridentCatchTracker;
import com.stardew.craft.combat.skill.BrokenTridentThrustTracker;
import com.stardew.craft.combat.skill.StartrailTracker;
import com.stardew.craft.combat.skill.StarfallTracker;
import com.stardew.craft.combat.skill.SingularityTracker;
import com.stardew.craft.combat.skill.SingularityEvolveTracker;
import com.stardew.craft.combat.skill.EternalCollapseTracker;
import com.stardew.craft.combat.skill.WindSpireTracker;
import com.stardew.craft.combat.skill.WickedKrisPoisonTracker;
import com.stardew.craft.combat.skill.GalaxyDaggerMarkTracker;
import com.stardew.craft.combat.skill.GalaxyDaggerThrustTracker;
import com.stardew.craft.combat.skill.InfinityDaggerMarkTracker;
import com.stardew.craft.combat.skill.InfinityDaggerThrustTracker;
import com.stardew.craft.combat.skill.DwarfDaggerRushTracker;
import com.stardew.craft.combat.skill.DwarfDaggerThrustTracker;
import com.stardew.craft.combat.skill.DashMovementTracker;
import com.stardew.craft.combat.skill.DragontoothShivBreathTracker;
import com.stardew.craft.combat.skill.IridiumNeedleFrenzyTracker;
import com.stardew.craft.combat.skill.IridiumNeedleThrustTracker;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.item.IStardewItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.List;

import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import com.stardew.craft.entity.projectile.TideAnchorProjectileEntity;
import com.stardew.craft.entity.effect.IceSpineEffectEntity;
import com.stardew.craft.combat.network.WaterRingEffectPayload;

/**
 * 星露谷武器基类
 * 实现IStardewItem接口以继承模组的标准tooltip系统
 */
@SuppressWarnings("deprecation") // Tier 在 1.21+ 被弃用，但武器系统依赖它
public class StardewWeaponItem extends SwordItem implements IStardewItem, IStardewWeapon {
    
    private final String weaponId;
    private final WeaponData weaponData;
    private static final int SKILL_ANIM_TICKS = 8;
    private static final float BASE_ATTACK_RANGE = 3.0f;
    
    public StardewWeaponItem(String weaponId, Properties properties) {
        super(createTier(weaponId), properties);
        this.weaponId = weaponId;
        this.weaponData = WeaponRegistry.get(weaponId);
    }
    
    /**
     * 根据武器数据创建Tier
     */
    private static Tier createTier(String weaponId) {
        WeaponData data = WeaponRegistry.get(weaponId);
        if (data == null) {
            return new StardewWeaponTier(1, 0, 1.6f);
        }
        
        // 计算平均伤害 (MC的attackDamage会+1)
        float avgDamage = (float) ((data.getDamageMin() + data.getDamageMax()) / 2.0 - 1);
        
        // 根据武器类型设置攻速（将目标 APS 转成 MC 的 attackSpeed modifier: APS = 4.0 + modifier）
        // 星露谷 speed: 每点约 +0.1 APS
        float baseAps = data.getWeaponType().getAttackSpeed();
        float speedBonusAps = data.getSpeed() * 0.1f;
        float attackSpeed = (baseAps + speedBonusAps) - 4.0f;
        
        return new StardewWeaponTier(data.getLevel(), avgDamage, attackSpeed);
    }
    
    @SuppressWarnings("null")
    @Override
    public Component getName(@SuppressWarnings("null") ItemStack stack) {
        if (weaponData != null) {
            // 使用翻译键，颜色由稀有度决定
            return Component.translatable(this.getDescriptionId())
                    .withStyle(weaponData.getRarity().getColor());
        }
        return super.getName(stack);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        ensureWeaponStats(stack);
        return stack;
    }

    @SuppressWarnings("null")
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        if (weaponData == null) {
            return super.getDefaultAttributeModifiers();
        }

        float avgDamage = (float) ((weaponData.getDamageMin() + weaponData.getDamageMax()) / 2.0 - 1);
        float baseAps = weaponData.getWeaponType().getAttackSpeed();
        float speedBonusAps = weaponData.getSpeed() * 0.1f;
        float attackSpeed = (baseAps + speedBonusAps) - 4.0f;
        float desiredRange = weaponData.getWeaponType().getAttackRange();
        float attackRangeBonus = desiredRange - BASE_ATTACK_RANGE;

        return ItemAttributeModifiers.builder()
            .add(
                Attributes.ATTACK_DAMAGE,
                new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "weapon." + weaponId + ".attack_damage"),
                    avgDamage,
                    AttributeModifier.Operation.ADD_VALUE
                ),
                EquipmentSlotGroup.MAINHAND
            )
            .add(
                Attributes.ATTACK_SPEED,
                new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "weapon." + weaponId + ".attack_speed"),
                    attackSpeed,
                    AttributeModifier.Operation.ADD_VALUE
                ),
                EquipmentSlotGroup.MAINHAND
            )
            .add(
                Attributes.ENTITY_INTERACTION_RANGE,
                new AttributeModifier(
                    ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "weapon." + weaponId + ".attack_range"),
                    attackRangeBonus,
                    AttributeModifier.Operation.ADD_VALUE
                ),
                EquipmentSlotGroup.MAINHAND
            )
            .build();
    }

    @Override
    public boolean isDamageable(@SuppressWarnings("null") ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(@SuppressWarnings("null") ItemStack stack) {
        return false;
    }

    @Override
    public boolean hurtEnemy(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") LivingEntity target, @SuppressWarnings("null") LivingEntity attacker) {
        return true;
    }

    @Override
    public boolean mineBlock(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") LivingEntity entityLiving) {
        return true;
    }
    
    // ============== IStardewItem 接口实现 ==============
    
    @Override
    public String getItemTypeKey() {
        if (weaponData == null) return "stardewcraft.type.weapon";
        // 根据武器类型返回不同的类型键
        return switch (weaponData.getWeaponType()) {
            case SWORD -> "stardewcraft.type.weapon.sword";
            case DAGGER -> "stardewcraft.type.weapon.dagger";
            case CLUB -> "stardewcraft.type.weapon.club";
            case SLINGSHOT -> "stardewcraft.type.weapon.slingshot";
        };
    }
    
    @Override
    public int getSellPrice(ItemStack stack) {
        return weaponData == null ? -1 : weaponData.getLevel() * 50;
    }

    // ============== Tooltip ==============
    
    @Override
    public void appendHoverText(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") TooltipContext context, @SuppressWarnings("null") List<Component> tooltipComponents, @SuppressWarnings("null") TooltipFlag tooltipFlag) {
        // 注意：IStardewItem的标准tooltip（种类、售价）由ModClientEvents事件处理
        // 这里只添加武器特有的详细信息
        if (weaponData != null) {
            ensureWeaponStats(stack);
            boolean expanded = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            WeaponTooltipBuilder builder = new WeaponTooltipBuilder(stack, weaponData, expanded);
            tooltipComponents.addAll(builder.build());
        }
    }

    @SuppressWarnings("null")
    @Override
    public void inventoryTick(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") Level level, @SuppressWarnings("null") Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!level.isClientSide && entity instanceof Player) {
            ensureWeaponStats(stack);
        }
    }

    @SuppressWarnings("null")
    @Override
    public InteractionResultHolder<ItemStack> use(@SuppressWarnings("null") Level level, @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand) {
        @SuppressWarnings("null")
        ItemStack stack = player.getItemInHand(hand);
        return InteractionResultHolder.pass(stack);
    }

    @SuppressWarnings("null")
    public InteractionResultHolder<ItemStack> useSkill(Level level, Player player, InteractionHand hand, boolean majorSkill) {
        @SuppressWarnings("null")
        ItemStack stack = player.getItemInHand(hand);
        if (weaponData == null) {
            ensureWeaponStats(stack);
        }
        if (weaponData == null) {
            return InteractionResultHolder.pass(stack);
        }

        WeaponSkillData skill = majorSkill ? weaponData.getSkill2() : weaponData.getSkill1();
        if (skill == null) {
            return InteractionResultHolder.pass(stack);
        }

        long nowTick = level.getGameTime();
        String skillId = skill.getId();
        net.minecraft.resources.ResourceLocation namespacedSkillId = skillId.indexOf(':') >= 0
                ? net.minecraft.resources.ResourceLocation.tryParse(skillId)
                : net.minecraft.resources.ResourceLocation.tryBuild("stardewcraft", skillId);
        if (namespacedSkillId != null) {
            var externalHandler = com.stardew.craft.api.v1.equipment.StardewWeaponSkillHandlers
                    .get(namespacedSkillId).orElse(null);
            if (externalHandler != null) {
                return externalHandler.use(new com.stardew.craft.api.v1.equipment.StardewWeaponSkillContext(
                        level, player, hand, stack, namespacedSkillId, majorSkill));
            }
        }
        int cooldownTicks = skill.getCooldown() * 20;

        boolean isSilverFoldback = "silver_foldback".equals(skillId);
        boolean isCrescentSlash = "crescent_slash".equals(skillId);
        boolean isForestBlessing = "forest_blessing".equals(skillId);
        boolean isSteelSpineFury = "steel_spine_fury".equals(skillId);
        boolean isBoneFracture = "bone_fracture".equals(skillId);
        boolean isCarvingThrust = "carving_thrust".equals(skillId);
        boolean isIronDirkThrust = "iron_dirk_thrust".equals(skillId);
        boolean isWindSpireThrust = "wind_spire_thrust".equals(skillId);
        boolean isElfBladeLeaf = "elf_blade_leaf".equals(skillId);
        boolean isBurglarShank = "burglar_shank".equals(skillId);
        boolean isCrystalDaggerLayer = "crystal_dagger_layer".equals(skillId);
        boolean isShadowDaggerExecute = "shadow_dagger_execute".equals(skillId);
        boolean isWickedKrisVenomRipple = "wicked_kris_venom_ripple".equals(skillId);
        boolean isWickedKrisNestBurst = "wicked_kris_nest_burst".equals(skillId);
        boolean isFishcatchThrust = "fishcatch_thrust".equals(skillId);
        boolean isTideReel = "tide_reel".equals(skillId);
        boolean isClaymoreFoldback = "claymore_foldback".equals(skillId);
        boolean isTideMark = "tide_mark".equals(skillId);
        boolean isTideAnchor = "tide_anchor".equals(skillId);
        boolean isTemplarVow = "templar_vow".equals(skillId);
        boolean isTemplarJudgement = "templar_judgement".equals(skillId);
        boolean isMeowmereShot = "meowmere_shot".equals(skillId);
        boolean isMeowmereSymphony = "meowmere_symphony".equals(skillId);
        boolean isInsectEyeStance = "insect_eye_stance".equals(skillId);
        boolean isInsectDash = "insect_dash".equals(skillId);
        boolean isObsidianResonance = "obsidian_resonance".equals(skillId);
        boolean isObsidianCrack = "obsidian_crack".equals(skillId);
        boolean isOssifiedMark = "ossified_mark".equals(skillId);
        boolean isOssifiedExecution = "ossified_execution".equals(skillId);
        boolean isHolySmite = "holy_smite".equals(skillId);
        boolean isHolyDomain = "holy_domain".equals(skillId);
        boolean isTemperedQuench = "tempered_quench".equals(skillId);
        boolean isTemperedBillet = "tempered_billet".equals(skillId);
        boolean isYetiToothMark = "yeti_tooth_mark".equals(skillId);
        boolean isYetiToothSpine = "yeti_tooth_spine".equals(skillId);
        boolean isSteelFalchionLine = "steel_falchion_line".equals(skillId);
        boolean isSteelFalchionTrace = "steel_falchion_trace".equals(skillId);
        boolean isDarkSwordBloodDebt = "dark_sword_blood_debt".equals(skillId);
        boolean isDarkSwordBloodMoon = "dark_sword_blood_moon".equals(skillId);
        boolean isLavaKatanaBrand = "lava_katana_brand".equals(skillId);
        boolean isLavaKatanaReverb = "lava_katana_reverb".equals(skillId);
        boolean isDragonBreathThrust = "dragon_breath_thrust".equals(skillId);
        boolean isDragonBreathJudgement = "dragon_breath_judgement".equals(skillId);
        boolean isDragontoothShivStab = "dragontooth_shiv_stab".equals(skillId);
        boolean isDragontoothShivBreath = "dragontooth_shiv_breath".equals(skillId);
        boolean isIridiumNeedleThrust = "iridium_needle_thrust".equals(skillId);
        boolean isIridiumNeedleFrenzy = "iridium_needle_frenzy".equals(skillId);
        boolean isGalaxyDaggerStarstab = "galaxy_dagger_starstab".equals(skillId);
        boolean isGalaxyDaggerStarleap = "galaxy_dagger_starleap".equals(skillId);
        boolean isInfinityDaggerSingularityStab = "infinity_dagger_singularity_stab".equals(skillId);
        boolean isInfinityDaggerSingularityBackstab = "infinity_dagger_singularity_backstab".equals(skillId);
        boolean isDwarfRuneGuard = "dwarf_rune_guard".equals(skillId);
        boolean isDwarfFortress = "dwarf_fortress".equals(skillId);
        boolean isDwarfDaggerThrust = "dwarf_dagger_thrust".equals(skillId);
        boolean isDwarfDaggerRush = "dwarf_dagger_rush".equals(skillId);
        boolean isStartrailRift = "startrail_rift".equals(skillId);
        boolean isGalaxyJudgement = "galaxy_judgement".equals(skillId);
        boolean isSingularityEvolve = "singularity_evolve".equals(skillId);
        boolean isEternalCollapse = "eternal_collapse".equals(skillId);

        // === 银军刀特殊逻辑 ===
        if (isSilverFoldback) {
            // 客户端：检查冷却，如果冷却中直接返回失败（不播放任何效果）
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skill.getId())) {
                    return InteractionResultHolder.fail(stack);
                }
                // Foldback状态中：播放返回动画
                if (com.stardew.craft.client.weapon.SilverSaberFoldbackClientState.isActive(player)) {
                    com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skill.getId(), SilverSaberSkillHelper.SKILL_ANIM_TICKS);
                    return InteractionResultHolder.sidedSuccess(stack, true);
                }
                // 非Foldback状态：不在客户端播放动画，等服务端同步
                return InteractionResultHolder.sidedSuccess(stack, true);
            }
            
            // 服务端：检查冷却
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skill.getId(), nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }
            
            // 服务端：Foldback状态中，右键执行返回
            if (SilverSaberFoldbackState.isActive(player, nowTick)) {
                Vec3 origin = SilverSaberFoldbackState.getOrigin(player);
                LivingEntity followTarget = findNearestTargetEntityInFront(player, 5.0);
                SilverSaberSkillHelper.executeReturnStrike(
                    player, followTarget, origin, weaponId, skill, nowTick, 
                    StardewWeaponItem::teleportPlayer
                );
                return InteractionResultHolder.sidedSuccess(stack, false);
            }
            
            // 服务端：首次使用，执行突进
            LivingEntity target = findNearestTargetEntityInFront(player, 5.0);
            if (target != null) {
                // 有目标：先强制传送到目标面前，再攻击并进入Foldback状态
                Vec3 origin = player.position();
                Vec3 frontPos = getFrontPosition(target, player);
                if (frontPos != null) {
                    teleportPlayer(player, frontPos);
                    Vec3 toTarget = target.position().subtract(frontPos);
                    if (toTarget.horizontalDistanceSqr() > 0.01) {
                        float yaw = (float) (Math.atan2(-toTarget.x, toTarget.z) * (180.0 / Math.PI));
                        player.setYRot(yaw);
                        player.setYHeadRot(yaw);
                    }
                }
                SilverSaberSkillHelper.executeInitialDashAfterTeleport(
                    player, target, origin, weaponId, skill, nowTick
                );
            } else {
                // 无目标：向前冲刺3格，直接进入冷却
                SilverSaberSkillHelper.executeEmptyDash(
                    player, weaponId, skill, nowTick,
                    StardewWeaponItem::dashForward
                );
            }
            return InteractionResultHolder.sidedSuccess(stack, false);
        }
        
        // === 弯刀：弦月斩（扇形群攻） ===
        if (isCrescentSlash) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skill.getId(), nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skill.getId(), nowTick, cooldownTicks);

            if (!level.isClientSide) {
                List<LivingEntity> targets = findTargetsInArc(player, 4.5, 0.2);
                for (LivingEntity target : targets) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skill.getId())
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                }
            }

            int animTicks = 8;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skill.getId(), animTicks);
            }

            if (!level.isClientSide) {
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                if (player instanceof ServerPlayer serverPlayer) {
                    WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skill.getId(), animTicks);
                }
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 森林剑：森林赐福（命中后持续治疗） ===
        if (isForestBlessing) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skill.getId())) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.ForestBlessingClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (player instanceof ServerPlayer serverPlayer && com.stardew.craft.combat.skill.ForestBlessingTracker.isActive(serverPlayer, nowTick)) {
                if (!level.isClientSide) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skill.getId(), nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide) {
                LivingEntity target = findTargetEntity(player, 4.0);
                if (target != null) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skill.getId())
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);

                    if (player instanceof ServerPlayer serverPlayer) {
                        com.stardew.craft.combat.skill.ForestBlessingTracker.start(serverPlayer, nowTick, 80, 2, 10,
                            weaponId, skill.getId(), cooldownTicks);
                    }
                } else if (player instanceof ServerPlayer serverPlayer) {
                    com.stardew.craft.combat.skill.ForestBlessingTracker.start(serverPlayer, nowTick, 80, 1, 10,
                        weaponId, skill.getId(), cooldownTicks);
                }
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skill.getId(), animTicks);
            }

            if (!level.isClientSide) {
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                if (player instanceof ServerPlayer serverPlayer) {
                    WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skill.getId(), animTicks);
                }
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 铁刃：钢脊之怒（进入姿态，等待下次普通攻击） ===
        if (isSteelSpineFury) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skill.getId())) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.SteelSpineFuryClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (player instanceof ServerPlayer serverPlayer
                && com.stardew.craft.combat.skill.SteelSpineFuryState.isBusy(serverPlayer)) {
                if (!level.isClientSide) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skill.getId(), nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                com.stardew.craft.combat.skill.SteelSpineFuryState.start(serverPlayer, nowTick, 80, weaponId, skill.getId(), cooldownTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 骨剑：骨裂斩（弱化+减速） ===
        if (isBoneFracture) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

            if (!level.isClientSide) {
                LivingEntity target = findTargetEntity(player, 4.0);
                if (target != null) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);

                    target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 0, false, true, true));
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 0, false, true, true));

                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        com.stardew.craft.combat.skill.BoneFractureTracker.apply(serverLevel, target, nowTick, 80);
                    }
                }
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 刻刀：刻痕连刺 ===
        if (isCarvingThrust) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 2.5);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 0, false, false, true));
                CarvingKnifeThrustTracker.start(serverPlayer, nowTick, target, weaponId, skillId);
            }

            int animTicks = 18;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 残破的三叉戟：鱼获试刺 ===
        if (isFishcatchThrust) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 18;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 2.5);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 0, false, false, true));
                BrokenTridentThrustTracker.start(serverPlayer, nowTick, target, weaponId, skillId,
                    skill.getDamagePercent() / 100.0f);
            }

            int animTicks = 18;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 残破的三叉戟：渔潮回钩 ===
        if (isTideReel) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 12;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                LivingEntity target = findNearestTargetEntityInFront(player, 4.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                boolean fishCatchActive = BrokenTridentCatchTracker.isActive(serverPlayer, nowTick);
                float bonus = fishCatchActive ? 0.60f : 0.0f;
                if (fishCatchActive) {
                    BrokenTridentCatchTracker.consume(serverPlayer, nowTick);
                }

                int appliedCooldown = cooldownTicks;
                if (BrokenTridentCatchTracker.hasFishInInventory(player)) {
                    appliedCooldown = Math.max(1, cooldownTicks - 40);
                }
                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, appliedCooldown);

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f + bonus)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);

                if (fishCatchActive) {
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 0, false, true, true));
                }

                Vec3 toPlayer = player.position().subtract(target.position());
                Vec3 pullDir = new Vec3(toPlayer.x, 0.0, toPlayer.z);
                if (pullDir.lengthSqr() > 0.01) {
                    double pullStrength = fishCatchActive ? 0.55 : 0.4;
                    double lift = fishCatchActive ? 0.12 : 0.08;
                    Vec3 pull = pullDir.normalize().scale(pullStrength);
                    target.setDeltaMovement(target.getDeltaMovement().add(pull.x, lift, pull.z));
                    target.hurtMarked = true;
                }

                if (level instanceof ServerLevel serverLevel) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(serverLevel,
                        new WaterRingEffectPayload((float) target.getX(), (float) target.getY(),
                            (float) target.getZ(), 4.6f, 24));
                    serverLevel.playSound(null, target.blockPosition(),
                        SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.95f, 1.05f);
                    serverLevel.playSound(null, target.blockPosition(),
                        SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 0.85f, 1.15f);
                    serverLevel.sendParticles(ParticleTypes.SPLASH,
                        target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                        28, 0.9, 0.3, 0.9, 0.05);
                    serverLevel.sendParticles(ParticleTypes.BUBBLE,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                        20, 0.75, 0.25, 0.75, 0.03);
                    serverLevel.sendParticles(ParticleTypes.CLOUD,
                        target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(),
                        12, 0.7, 0.15, 0.7, 0.02);
                    serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.65, target.getZ(),
                        14, 0.6, 0.3, 0.6, 0.06);
                    serverLevel.sendParticles(ParticleTypes.CRIT,
                        target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                        10, 0.45, 0.25, 0.45, 0.07);
                }

                int animTicks = 12;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 铁制短剑：折影突刺 ===
        if (isIronDirkThrust) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 7.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 5, 0, false, false, true));

                Vec3 frontPos = getFrontPosition(target, player);
                if (frontPos != null) {
                    teleportPlayer(player, frontPos);
                }

                Vec3 toTarget = target.position().subtract(player.position());
                if (toTarget.horizontalDistanceSqr() > 0.01) {
                    float yaw = (float) (Math.atan2(-toTarget.x, toTarget.z) * (180.0 / Math.PI));
                    player.setYRot(yaw);
                    player.setYHeadRot(yaw);
                }

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .critChanceBonus(0.10f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);

                Vec3 behindPos = getBehindPosition(target, player, 3.0);
                if (behindPos != null) {
                    teleportPlayer(player, behindPos);
                }

                Vec3 toTargetAfter = target.position().subtract(player.position());
                if (toTargetAfter.horizontalDistanceSqr() > 0.01) {
                    float yaw = (float) (Math.atan2(-toTargetAfter.x, toTargetAfter.z) * (180.0 / Math.PI));
                    player.setYRot(yaw);
                    player.setYHeadRot(yaw);
                }
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 疾风利剑：风痕突刺 ===
        if (isWindSpireThrust) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                LivingEntity target = findNearestTargetEntityInFront(player, 6.0);
                if (target != null) {
                    Vec3 frontPos = getFrontPosition(target, player);
                    if (frontPos != null) {
                        teleportPlayer(player, frontPos);
                    }

                    Vec3 toTarget = target.position().subtract(player.position());
                    if (toTarget.horizontalDistanceSqr() > 0.01) {
                        float yaw = (float) (Math.atan2(-toTarget.x, toTarget.z) * (180.0 / Math.PI));
                        player.setYRot(yaw);
                        player.setYHeadRot(yaw);
                    }

                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);

                    player.addEffect(new MobEffectInstance(ModMobEffects.SPEED, 60, 0, false, true, true));
                    WindSpireTracker.start(serverPlayer, nowTick, 60);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.WindSpirePayload(true, 60)
                    );
                } else {
                    dashForward(player, 4.0);
                }

                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 水晶匕首：晶层刺击 ===
        if (isCrystalDaggerLayer) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                LivingEntity target = findNearestTargetEntityInFront(player, 4.0);
                if (target != null) {
                    Vec3 frontPos = getFrontPosition(target, player);
                    if (frontPos != null) {
                        teleportPlayer(player, frontPos);
                    }

                    Vec3 toTarget = target.position().subtract(player.position());
                    if (toTarget.horizontalDistanceSqr() > 0.01) {
                        float yaw = (float) (Math.atan2(-toTarget.x, toTarget.z) * (180.0 / Math.PI));
                        player.setYRot(yaw);
                        player.setYHeadRot(yaw);
                    }

                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                } else {
                    dashForward(player, 3.0);
                }

                com.stardew.craft.combat.skill.CrystalDaggerLayerTracker.addStack(serverPlayer, nowTick);

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 暗影匕首：影袭处决 ===
        if (isShadowDaggerExecute) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 4.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                boolean execute = target.getHealth() <= target.getMaxHealth() * 0.30f;
                int appliedCooldown = execute ? cooldownTicks : Math.max(1, cooldownTicks / 2);
                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, appliedCooldown);

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);

                if (execute) {
                    SkillContext bonusContext = SkillContext.builder()
                        .skillId("shadow_dagger_execute_bonus")
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(1.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, bonusContext, nowTick + 5);
                    target.invulnerableTime = 0;
                    target.hurtTime = 0;
                    player.attack(target);
                }

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 矮人匕首：符印突刺 ===
        if (isDwarfDaggerThrust) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (DwarfDaggerThrustTracker.isThrusting(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                Vec3 end = computeDashEnd(player, 8.0);
                float damageMultiplier = skill.getDamagePercent() / 100.0f;
                DwarfDaggerThrustTracker.start(serverPlayer, nowTick, end, 5, weaponId, skillId, damageMultiplier);

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 矮人匕首：地脉疾行 ===
        if (isDwarfDaggerRush) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.DwarfDaggerRushClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (DwarfDaggerRushTracker.isActive(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                DwarfDaggerRushTracker.start(serverPlayer, nowTick, 100);
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 0, false, true, true));

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 铱针：三针连斩 ===
        if (isIridiumNeedleThrust) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 18;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 2.5);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                float damageMultiplier = skill.getDamagePercent() / 100.0f;
                IridiumNeedleThrustTracker.start(serverPlayer, nowTick, target, weaponId, skillId, damageMultiplier);

                int animTicks = 18;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 铱针：铱辉狂热 ===
        if (isIridiumNeedleFrenzy) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.IridiumNeedleFrenzyClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (player instanceof ServerPlayer serverPlayer) {
                if (IridiumNeedleFrenzyTracker.isActive(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                int durationTicks = 120;
                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                IridiumNeedleFrenzyTracker.start(serverPlayer, nowTick, durationTicks);
                net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> speed =
                    net.minecraft.core.Holder.direct(ModMobEffects.SPEED.get());
                player.addEffect(new MobEffectInstance(speed, durationTicks, 0, false, true, true));
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 银河匕首：星轨裂刺 ===
        if (isGalaxyDaggerStarstab) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 6;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 3.5);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                float damageMultiplier = skill.getDamagePercent() / 100.0f;
                GalaxyDaggerThrustTracker.start(serverPlayer, nowTick, target, weaponId, skillId, damageMultiplier);

                int animTicks = 6;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 银河匕首：星跃背刺 ===
        if (isGalaxyDaggerStarleap) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 8;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                LivingEntity target = findNearestTargetEntityInFront(player, 5.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                Vec3 behindPos = getBehindPosition(target, player, 3.0);
                if (behindPos != null) {
                    teleportPlayer(player, behindPos);
                }

                Vec3 toTarget = target.position().subtract(player.position());
                if (toTarget.horizontalDistanceSqr() > 0.01) {
                    float yaw = (float) (Math.atan2(-toTarget.x, toTarget.z) * (180.0 / Math.PI));
                    player.setYRot(yaw);
                    player.setYHeadRot(yaw);
                }

                boolean marked = GalaxyDaggerMarkTracker.consumeIfEligible(target, player, nowTick);
                float damageMultiplier = skill.getDamagePercent() / 100.0f;
                if (marked) {
                    damageMultiplier += 0.30f;
                }

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(damageMultiplier)
                    .guaranteedCrit(true)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);
                com.stardew.craft.combat.skill.YetiFreezeTracker.apply(target, nowTick, 16);

                if (marked && player.level() instanceof ServerLevel serverLevel) {
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

                int animTicks = 8;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 无限匕首：奇点连刺 ===
        if (isInfinityDaggerSingularityStab) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 6;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 3.5);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                float damageMultiplier = skill.getDamagePercent() / 100.0f;
                InfinityDaggerThrustTracker.start(serverPlayer, nowTick, target, weaponId, skillId, damageMultiplier);

                int animTicks = 6;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 无限匕首：奇点背刺 ===
        if (isInfinityDaggerSingularityBackstab) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 8;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 12.0f;
            if (player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                LivingEntity target = findNearestTargetEntityInFront(player, 5.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                Vec3 behindPos = getBehindPosition(target, player, 3.0);
                if (behindPos != null) {
                    teleportPlayer(player, behindPos);
                }

                Vec3 toTarget = target.position().subtract(player.position());
                if (toTarget.horizontalDistanceSqr() > 0.01) {
                    float yaw = (float) (Math.atan2(-toTarget.x, toTarget.z) * (180.0 / Math.PI));
                    player.setYRot(yaw);
                    player.setYHeadRot(yaw);
                }

                boolean marked = InfinityDaggerMarkTracker.isMarkedBy(target, player, nowTick);
                float damageMultiplier = skill.getDamagePercent() / 100.0f;
                float markedBonus = marked ? 0.20f : 0.0f;

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(damageMultiplier)
                    .guaranteedCrit(true)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);

                if (target.isAlive()) {
                    SkillContext secondContext = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MAJOR)
                        .damageMultiplier(damageMultiplier + markedBonus)
                        .guaranteedCrit(true)
                        .build();
                    WeaponSkillContextStore.setPending(player, secondContext, nowTick + 5);
                    target.invulnerableTime = 0;
                    target.hurtTime = 0;
                    WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, 6);
                    WeaponSkillAnimationLock.setLock(player, nowTick, 6);
                    player.attack(target);
                }

                if (marked) {
                    InfinityDaggerMarkTracker.consumeIfEligible(target, player, nowTick);
                }

                if (marked && player.level() instanceof ServerLevel serverLevel) {
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

                com.stardew.craft.combat.skill.YetiFreezeTracker.apply(target, nowTick, 24);

                int animTicks = 8;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 蛇形邪剑：蛇毒涟漪 ===
        if (isWickedKrisVenomRipple) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                List<LivingEntity> targets = findTargetsInRadius(player, 4.0);
                if (targets.isEmpty()) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                for (LivingEntity target : targets) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);

                    target.invulnerableTime = 0;
                    target.hurtTime = 0;
                    player.attack(target);

                    WickedKrisPoisonTracker.applyPoison(target, serverPlayer, nowTick, 100, 5, false);
                }

                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0, false, true, true));
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 蛇形邪剑：蛇巢引爆 ===
        if (isWickedKrisNestBurst) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                float energyCost = 10.0f;
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                LivingEntity target = findNearestTargetEntityInFront(player, 4.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MAJOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);

                WickedKrisPoisonTracker.applyPoison(target, serverPlayer, nowTick, 200, 5, true);

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 飞贼之胫：盗影割袋 ===
        if (isBurglarShank) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 4.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 精灵之刃：月露萤刃 ===
        if (isElfBladeLeaf) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.ElfBladeClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
            }

            if (player instanceof ServerPlayer serverPlayer
                && com.stardew.craft.combat.skill.ElfBladeTracker.isActive(serverPlayer, nowTick)) {
                if (!level.isClientSide) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                float damageMultiplier = skill.getDamagePercent() / 100.0f;
                com.stardew.craft.combat.skill.ElfBladeTracker.start(
                    serverPlayer, nowTick, 100, damageMultiplier, weaponId, skillId, cooldownTicks
                );
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 双刃大剑：回刃折返（两段斩击） ===
        if (isClaymoreFoldback) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

            if (!level.isClientSide) {
                LivingEntity target = findTargetEntity(player, 4.0);
                if (target != null) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(0.7f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                }

                if (player instanceof ServerPlayer serverPlayer) {
                    com.stardew.craft.combat.skill.ClaymoreFoldbackTracker.start(
                        serverPlayer, nowTick, 12, target, weaponId, skillId);
                }
            }

            int animTicks = 12;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 海王星大剑：潮汐印记 ===
        if (isTideMark) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide) {
                LivingEntity target = findTargetEntity(player, 6.0);
                if (target == null) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                    }
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                TideMarkTracker.apply(target, nowTick, 100);
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 圣堂之刃：誓约反斩 ===
        if (isTemplarVow) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)
                    || com.stardew.craft.client.weapon.TemplarVowClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, 40);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (TemplarVowTracker.isActive(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }
                TemplarVowTracker.start(serverPlayer, nowTick, 40, weaponId, skillId, cooldownTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, 40);
            }
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 昆虫头部：复眼架势 ===
        if (isInsectEyeStance) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)
                    || com.stardew.craft.client.weapon.InsectEyeStanceClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, 1);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (InsectEyeStanceTracker.isActive(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }
                InsectEyeStanceTracker.start(serverPlayer, nowTick, 30, weaponId, skillId, cooldownTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, 1);
            }
            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 圣堂之刃：圣堂裁决 ===
        if (isTemplarJudgement) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                float energyCost = 10.0f;
                if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                List<LivingEntity> targets = findTargetsInRadius(player, 6.0);
                if (targets.isEmpty()) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                TemplarJudgementTracker.start(serverPlayer, nowTick, 100, targets);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 昆虫头部：甲翼疾掠 ===
        if (isInsectDash) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            int animTicks = 8;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                int stage = InsectDashChainState.getNextStage(serverPlayer, nowTick);
                float energyCost = stage == 1 ? 3.0f : (stage == 2 ? 5.0f : 7.0f);
                if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                Vec3 start = player.position();
                Vec3 end = computeDashEnd(player, 7.0);

                float damageMultiplier = stage == 1 ? 0.8f : (stage == 2 ? 1.0f : 1.2f);
                List<LivingEntity> targets = findTargetsAlongPath(player, start, end, 1.2);
                int hitCount = 0;
                for (LivingEntity target : targets) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MAJOR)
                        .damageMultiplier(damageMultiplier)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                    hitCount++;
                }

                DashMovementTracker.start(serverPlayer, nowTick, end, 5);

                if (stage >= 3) {
                    InsectDashChainState.clear(serverPlayer);
                    WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                    player.addEffect(new MobEffectInstance(ModMobEffects.SPEED, 60, 0, false, true, true));
                } else if (hitCount >= 2) {
                    InsectDashChainState.setStage(serverPlayer, nowTick, stage);
                } else {
                    InsectDashChainState.clear(serverPlayer);
                    WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                }

                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 黑曜石之刃：玄刃共鸣（被动） ===
        if (isObsidianResonance) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 黑曜石之刃：裂界一线 ===
        if (isObsidianCrack) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

            int animTicks = 12;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                Vec3 look = getHorizontalLook(player);
                Vec3 right = new Vec3(look.z, 0.0, -look.x).normalize();
                float length = 6.0f;
                @SuppressWarnings("null")
                Vec3 center = player.position().add(look.scale(3.0));
                @SuppressWarnings("null")
                Vec3 start = center.add(right.scale(-length * 0.5));
                @SuppressWarnings("null")
                Vec3 end = center.add(right.scale(length * 0.5));
                float yaw = (float) (Math.atan2(-right.x, right.z) * (180.0 / Math.PI));
                ObsidianCrackTracker.start(serverPlayer, nowTick, start, end, yaw, length, weaponId, skillId);

                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 骨化剑：断骨刻名 ===
        if (isOssifiedMark) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findTargetEntity(player, 6.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, 6 * 20);
                com.stardew.craft.combat.skill.OssifiedMarkTracker.apply(target, serverPlayer, nowTick, 60);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 骨化剑：白骨行刑 ===
        if (isOssifiedExecution) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (level.isClientSide) {
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
            }

            float energyCost = 10.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findTargetEntity(player, 6.0);
                if (target == null
                    || !com.stardew.craft.combat.skill.OssifiedMarkTracker.isMarkedBy(target, player, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                com.stardew.craft.combat.skill.OssifiedExecutionTracker.start(serverPlayer, target, nowTick, 4.0f, 60);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 淬火阔剑：回炉淬火 ===
        if (isTemperedQuench) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide) {
                LivingEntity target = findTargetEntity(player, 4.5);
                if (target == null) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                    }
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);
            }

            int animTicks = 10;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            }

            if (!level.isClientSide) {
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                if (player instanceof ServerPlayer serverPlayer) {
                    WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                }
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 淬火阔剑：熔锻飞坯 ===
        if (isTemperedBillet) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

            if (!level.isClientSide) {
                List<LivingEntity> targets = findTargetsInRadius(player, 10.0);
                if (!targets.isEmpty()) {
                    java.util.Collections.shuffle(targets, new java.util.Random(player.level().random.nextLong()));
                }
                float dmg = (float) weaponData.getAverageDamage();

                LivingEntity fallback = targets.isEmpty() ? null : targets.get(0);
                for (int i = 0; i < 3; i++) {
                    LivingEntity target = null;
                    if (i < targets.size()) {
                        target = targets.get(i);
                    } else {
                        target = fallback;
                    }

                    com.stardew.craft.entity.projectile.TemperedBilletProjectileEntity projectile =
                        new com.stardew.craft.entity.projectile.TemperedBilletProjectileEntity(level, player, dmg, skillId, target);
                    float yaw = player.getYRot() + (level.random.nextFloat() - 0.5f) * 16.0f;
                    float pitch = player.getXRot() + (level.random.nextFloat() - 0.5f) * 10.0f;
                    projectile.shootFromRotation(player, pitch, yaw, 0.0F, 1.6F, 0.2F);
                    level.addFreshEntity(projectile);
                }
            }

            int animTicks = 12;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 圣剑：圣辉惩戒 ===
        if (isHolySmite) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide) {
                LivingEntity target = findTargetEntity(player, 4.5);
                if (target == null) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                    }
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);
            }

            int animTicks = 8;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            }

            if (!level.isClientSide) {
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                if (player instanceof ServerPlayer serverPlayer) {
                    WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                }
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 圣剑：晨曦圣域 ===
        if (isHolyDomain) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                com.stardew.craft.combat.skill.HolyBladeSanctuaryTracker.start(serverPlayer, nowTick, 80, 4.0f);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 海王星大剑：潮汐锚 ===
        if (isTideAnchor) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

            if (!level.isClientSide) {
                TideAnchorProjectileEntity projectile = new TideAnchorProjectileEntity(level, player, skillId);
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.25F, 0.8F);
                level.addFreshEntity(projectile);
            }

            int animTicks = 12;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        
        // === 彩虹猫之刃：彩虹光弹 (小技能) ===
        if (isMeowmereShot) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
            
            if (!level.isClientSide) {
                float dmg = (float) weaponData.getAverageDamage();
                MeowmereProjectileEntity projectile = new MeowmereProjectileEntity(level, player, dmg, 0, skillId);
                projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.1F, 1.0F);
                level.addFreshEntity(projectile);
            }
            
            // 客户端播放挥剑动画
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, 10);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, 10);
            }
            
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 彩虹猫之刃：喵星乐章 (大技能) ===
        if (isMeowmereSymphony) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide) {
                     // Notify cooldown
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

            if (!level.isClientSide) {
                float basePitch = player.getXRot();
                float baseYaw = player.getYRot();
                float dmg = (float) (weaponData.getAverageDamage() * 0.8);
                
                // 扇形发射5个
                for (int i = -2; i <= 2; i++) {
                    MeowmereProjectileEntity p = new MeowmereProjectileEntity(level, player, dmg, 1, skillId);
                    p.shootFromRotation(player, basePitch, baseYaw + i * 8, 0.0F, 1.0F, 1.0F); // 角度间距8度
                    level.addFreshEntity(p);
                }
            }
            
             // 客户端播放挥剑动画
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, 15);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, 15);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 雪怪之牙：冻牙刻印（小技能） ===
        if (isYetiToothMark) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide) {
                LivingEntity target = findTargetEntity(player, 4.0);
                if (target == null) {
                    if (player instanceof ServerPlayer serverPlayer) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                    }
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 钢刀：疾锋刻线（小技能） ===
        if (isSteelFalchionLine) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 7.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                Vec3 center = new Vec3(target.getX(), target.getY() + 0.02, target.getZ());
                com.stardew.craft.combat.skill.SteelFalchionLineTracker.startMinorLine(
                    serverPlayer, nowTick, center, player.getYRot(), 0.30f
                );
            }

            int animTicks = 8;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 钢刀：斩迹回响（大技能） ===
        if (isSteelFalchionTrace) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 12.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                com.stardew.craft.combat.skill.SteelFalchionLineTracker.startTrace(serverPlayer, nowTick, 100, 0.50f);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 熔岩武士刀：熔铸刻印（小技能） ===
        if (isLavaKatanaBrand) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findTargetEntity(player, 5.5);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 熔岩武士刀：熔潮回鸣（大技能，无动画） ===
        if (isLavaKatanaReverb) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.LavaKatanaReverbClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (com.stardew.craft.combat.skill.LavaKatanaReverbTracker.isActive(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 12.0f;
            if (player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                if (!(serverPlayer.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
                    return InteractionResultHolder.fail(stack);
                }

                var marked = com.stardew.craft.combat.skill.LavaKatanaReverbTracker.findMarkedTargetsInRange(
                    serverLevel, serverPlayer, nowTick, 8.0
                );
                if (marked.isEmpty()) {
                    LivingEntity target = findTargetEntity(player, 8.0);
                    if (target == null) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                    com.stardew.craft.combat.skill.LavaKatanaMarkTracker.apply(target, serverPlayer, nowTick, 120);
                } else {
                    for (LivingEntity target : marked) {
                        com.stardew.craft.combat.skill.LavaKatanaMarkTracker.ensureHeatAtLeast(target, serverPlayer, nowTick, 5);
                    }
                }

                com.stardew.craft.combat.skill.LavaKatanaReverbTracker.start(serverPlayer, nowTick, 80);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new com.stardew.craft.combat.network.LavaKatanaReverbPayload(true, 80)
                );

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 龙牙弯刀：龙息突刺（小技能） ===
        if (isDragonBreathThrust) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

            if (!level.isClientSide) {
                Vec3 start = player.position();
                Vec3 look = getHorizontalLook(player).normalize();
                @SuppressWarnings("null")
                Vec3 end = start.add(look.scale(5.0));
                List<LivingEntity> targets = findTargetsOnPath(player, start, end, 0.9);
                dashForward(player, 5.0);
                for (LivingEntity target : targets) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .critChanceBonus(0.10f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);

                    target.addEffect(new MobEffectInstance(ModMobEffects.VULNERABLE, 80, 1, false, true, true));
                    com.stardew.craft.combat.skill.YetiFreezeTracker.apply(target, nowTick, 40);
                }
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 龙牙弯刀：龙息裁决（大技能，龙息释放） ===
        if (isDragonBreathJudgement) {
            if (level.isClientSide) {
                int stacks = com.stardew.craft.client.weapon.DragonBreathClientState.getStacks(player);
                if (stacks < com.stardew.craft.combat.skill.DragonBreathTracker.MAJOR_THRESHOLD) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (!com.stardew.craft.combat.skill.DragonBreathTracker.canCastMajor(serverPlayer)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                int stacks = com.stardew.craft.combat.skill.DragonBreathTracker.consumeAll(serverPlayer);
                int extra = Math.max(0, stacks - com.stardew.craft.combat.skill.DragonBreathTracker.MAJOR_THRESHOLD);
                float critBonus = extra * 0.04f;

                List<LivingEntity> targets = findTargetsInArc(player, 4.0, 0.5);
                int hitCount = 0;
                for (LivingEntity target : targets) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MAJOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .critChanceBonus(critBonus)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                    hitCount++;
                }

                int refund = Math.min(5, hitCount);
                if (refund > 0) {
                    com.stardew.craft.combat.skill.DragonBreathTracker.addStacks(serverPlayer, refund);
                }

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 龙牙小刀：龙牙穿刺（小技能） ===
        if (isDragontoothShivStab) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                LivingEntity target = findNearestTargetEntityInFront(player, 4.0);
                if (target == null) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                SkillContext context = SkillContext.builder()
                    .skillId(skillId)
                    .tier(SkillContext.SkillTier.MINOR)
                    .damageMultiplier(skill.getDamagePercent() / 100.0f)
                    .build();
                WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                player.attack(target);
                com.stardew.craft.combat.skill.YetiFreezeTracker.apply(target, nowTick, 40);

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 龙牙小刀：龙息态（大技能） ===
        if (isDragontoothShivBreath) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.DragontoothShivBreathClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (player instanceof ServerPlayer serverPlayer
                && DragontoothShivBreathTracker.isActive(serverPlayer, nowTick)) {
                boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                );
                return InteractionResultHolder.fail(stack);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                DragontoothShivBreathTracker.start(serverPlayer, nowTick, 120);
                player.addEffect(new MobEffectInstance(ModMobEffects.SPEED, 120, 0, false, true, true));
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 1, false, true, true));

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, false);
        }

        // === 矮人剑：符文回能护斩（小技能） ===
        if (isDwarfRuneGuard) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                player.addEffect(new MobEffectInstance(com.stardew.craft.effect.ModMobEffects.SHELTER, 50, 1, false, true, true));

                LivingEntity target = findTargetEntity(player, 4.5);
                if (target != null) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, true, true));
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, 6.0f);
                } else {
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, 3.0f);
                }

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 矮人剑：地脉堡垒·回震（大技能，无动画） ===
        if (isDwarfFortress) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.DwarfFortressClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (com.stardew.craft.combat.skill.DwarfFortressTracker.isActive(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                com.stardew.craft.combat.skill.DwarfFortressTracker.start(serverPlayer, nowTick, 80, skill.getDamagePercent() / 100.0f);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new com.stardew.craft.combat.network.DwarfFortressPayload(true, 80)
                );
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 银河剑：星轨裂星（小技能） ===
        if (isStartrailRift) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                int stacks = StartrailTracker.getStacks(serverPlayer);
                boolean boosted = stacks >= 6;
                float critBonus = boosted ? 0.20f : 0.0f;

                Vec3 start = player.position();
                Vec3 end = computeDashEnd(player, 4.5);
                List<LivingEntity> targets = findTargetsOnPath(player, start, end, 0.9);
                int hitCount = 0;
                for (LivingEntity target : targets) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .critChanceBonus(critBonus)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                    hitCount++;
                }

                DashMovementTracker.start(serverPlayer, nowTick, end, 5);

                Vec3 riftDir = end.subtract(start);
                double riftLen = riftDir.length();
                if (riftLen > 0.05) {
                    Vec3 riftUnit = riftDir.normalize();
                    float yaw = (float) (Math.atan2(-riftUnit.x, riftUnit.z) * (180.0 / Math.PI));
                    int segments = Mth.clamp((int) (riftLen / 0.7f), 6, 10);
                    float segLen = (float) Math.max(0.6, riftLen / segments);
                    int baseColor = com.stardew.craft.combat.VfxColors.GALAXY_PURPLE;
                    int color = baseColor;
                    if (boosted) {
                        int r = (baseColor >> 16) & 0xFF;
                        int g = (baseColor >> 8) & 0xFF;
                        int b = baseColor & 0xFF;
                        r = Math.min(255, (int) (r * 1.25f));
                        g = Math.min(255, (int) (g * 1.25f));
                        b = Math.min(255, (int) (b * 1.25f));
                        color = (r << 16) | (g << 8) | b;
                    }
                    for (int i = 0; i < segments; i++) {
                        Vec3 segPos = start.add(riftUnit.scale((i + 0.5) * segLen));
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(serverPlayer.serverLevel(),
                            new com.stardew.craft.combat.network.RiftPathPayload(
                                (float) segPos.x, (float) segPos.y, (float) segPos.z,
                                yaw, segLen, 14, color
                            ));
                    }
                }

                if (boosted) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersInDimension(serverPlayer.serverLevel(),
                        new com.stardew.craft.combat.network.ShockwaveRingPayload(
                            (float) end.x, (float) end.y, (float) end.z, 1.25f, 8,
                            com.stardew.craft.combat.VfxColors.GALAXY_PURPLE
                        ));
                }

                if (hitCount > 0) {
                    StartrailTracker.addStacks(serverPlayer, 2);
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, 6.0f);
                    int maxHp = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                    int curHp = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                    int nextHp = Math.min(maxHp, curHp + 3);
                    if (nextHp != curHp) {
                        com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, nextHp);
                    }
                }

                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 140, 0, false, true, true));

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 银河剑：银河裁决（大技能） ===
        if (isGalaxyJudgement) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 12;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                int stacks = StartrailTracker.getStacks(serverPlayer);
                boolean forceCrit = stacks >= StartrailTracker.MAX_STACKS;
                int extraHits = Math.min(3, stacks / 4);
                StartrailTracker.consumeAll(serverPlayer);

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                List<LivingEntity> targets = findTargetsInRadius(player, 4.0);
                for (LivingEntity target : targets) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MAJOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .guaranteedCrit(forceCrit)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                }

                StarfallTracker.start(serverPlayer, nowTick, 3, extraHits, 4.0, 0.70f, skillId);

                int animTicks = 12;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 无限之刃：奇点进化（小技能） ===
        if (isSingularityEvolve) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

                boolean evolved = SingularityTracker.isEvolved(serverPlayer);
                SingularityEvolveTracker.start(serverPlayer, nowTick, 20, 4.0, 1.6f, 1.2f, skillId, evolved);

                boolean hasTarget = !findTargetsInRadius(player, 4.0).isEmpty();
                if (hasTarget) {
                    SingularityTracker.addStacks(serverPlayer, 4);
                    com.stardew.craft.player.PlayerStardewDataAPI.restoreEnergy(serverPlayer, 10.0f);
                    int maxHp = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                    int curHp = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                    int nextHp = Math.min(maxHp, curHp + 5);
                    if (nextHp != curHp) {
                        com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, nextHp);
                    }
                }

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 无限之刃：永恒坍缩（大技能） ===
        if (isEternalCollapse) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = 12;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                int stacks = SingularityTracker.getStacks(serverPlayer);
                int extraStrikes = Math.min(4, stacks / 5);
                float critBonus = extraStrikes * 0.05f;
                boolean finalStrike = stacks >= 16;
                SingularityTracker.consumeAll(serverPlayer);

                WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);
                Vec3 collapseCenter = null;
                List<LivingEntity> nearby = findTargetsInRadius(player, 4.0);
                if (!nearby.isEmpty()) {
                    LivingEntity nearest = null;
                    double best = Double.MAX_VALUE;
                    Vec3 origin = player.position();
                    for (LivingEntity target : nearby) {
                        double dist = target.position().distanceToSqr(origin);
                        if (dist < best) {
                            best = dist;
                            nearest = target;
                        }
                    }
                    if (nearest != null) {
                        collapseCenter = nearest.position();
                    }
                }

                EternalCollapseTracker.start(serverPlayer, collapseCenter, nowTick, 70, 6 + extraStrikes, 4.0, 0.80f, critBonus, finalStrike, 3.0f, skillId);

                int animTicks = 12;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 黑暗剑：祭血斩（小技能） ===
        if (isDarkSwordBloodDebt) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.DarkSwordBloodDebtClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                int animTicks = SKILL_ANIM_TICKS;
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (com.stardew.craft.combat.skill.DarkSwordBloodDebtTracker.isActive(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                int max = com.stardew.craft.player.PlayerStardewDataAPI.getMaxHealth(serverPlayer);
                int current = com.stardew.craft.player.PlayerStardewDataAPI.getHealth(serverPlayer);
                int cost = Math.max(1, Math.round(current * 0.06f));
                int next = Math.max(1, current - cost);
                com.stardew.craft.player.PlayerStardewDataAPI.setHealth(serverPlayer, Math.min(max, next));

                com.stardew.craft.combat.skill.DarkSwordBloodDebtTracker.start(serverPlayer, nowTick, 100, weaponId, skillId, cooldownTicks);
                com.stardew.craft.combat.skill.DarkSwordEffects.playBloodDebtCast(serverPlayer);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new com.stardew.craft.combat.network.DarkSwordBloodDebtPayload(true, 100)
                );

                LivingEntity target = findTargetEntity(player, 4.0);
                if (target != null) {
                    SkillContext context = SkillContext.builder()
                        .skillId(skillId)
                        .tier(SkillContext.SkillTier.MINOR)
                        .damageMultiplier(skill.getDamagePercent() / 100.0f)
                        .build();
                    WeaponSkillContextStore.setPending(player, context, nowTick + 5);
                    player.attack(target);
                }

                int animTicks = SKILL_ANIM_TICKS;
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 黑暗剑：血月收割（大技能，无动画） ===
        if (isDarkSwordBloodMoon) {
            if (level.isClientSide) {
                if (com.stardew.craft.client.weapon.DarkSwordBloodMoonClientState.isActive(player)) {
                    return InteractionResultHolder.fail(stack);
                }
                if (com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponId, skillId)) {
                    return InteractionResultHolder.fail(stack);
                }
                com.stardew.craft.client.weapon.SkillEffectsClient.playSkillEffects(skillId, player);
                return InteractionResultHolder.sidedSuccess(stack, true);
            }

            if (player instanceof ServerPlayer serverPlayer) {
                if (com.stardew.craft.combat.skill.DarkSwordBloodMoonTracker.isActive(serverPlayer, nowTick)) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                    return InteractionResultHolder.fail(stack);
                }
            }

            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }

                com.stardew.craft.combat.skill.DarkSwordBloodMoonTracker.start(serverPlayer, nowTick, 80, 10, weaponId, skillId, cooldownTicks);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new com.stardew.craft.combat.network.DarkSwordBloodMoonPayload(true, 80)
                );
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 雪怪之牙：冰脊裂地（大技能） ===
        if (isYetiToothSpine) {
            if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skillId, nowTick)) {
                if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                    boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        serverPlayer,
                        new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                    );
                }
                return InteractionResultHolder.fail(stack);
            }

            float energyCost = 10.0f;
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                if (!player.getAbilities().instabuild) {
                    if (!com.stardew.craft.player.PlayerStardewDataAPI.consumeEnergy(serverPlayer, energyCost)) {
                        boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                            serverPlayer,
                            new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                        );
                        return InteractionResultHolder.fail(stack);
                    }
                }
            }

            WeaponSkillCooldowns.setCooldown(player, weaponId, skillId, nowTick, cooldownTicks);

            if (!level.isClientSide) {
                Vec3 center = player.position();
                Vec3 look = getHorizontalLook(player);
                float baseYaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
                float radius = 2.5f;
                float dmgMul = skill.getDamagePercent() / 100.0f;

                for (int i = 0; i < 5; i++) {
                    float angle = baseYaw - 60.0f + i * 30.0f;
                    double rad = Math.toRadians(angle);
                    Vec3 initialDir = new Vec3(-Math.sin(rad), 0.0, Math.cos(rad)).normalize();
                    Vec3 start = center.add(initialDir.scale(radius));
                    BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        BlockPos.containing(start.x, player.getY(), start.z));
                    Vec3 groundStart = new Vec3(start.x, surfacePos.getY() + 0.05, start.z);
                    Vec3 dir = new Vec3(groundStart.x - center.x, 0.0, groundStart.z - center.z).normalize();
                    IceSpineEffectEntity spine = new IceSpineEffectEntity(level, player,
                        groundStart,
                        dir,
                        dmgMul,
                        skillId
                    );
                    level.addFreshEntity(spine);
                }
            }

            int animTicks = SKILL_ANIM_TICKS;
            if (level.isClientSide) {
                com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skillId, animTicks);
            } else if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skillId, animTicks);
                WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            }

            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // === 其他武器的通用逻辑 ===
        if (WeaponSkillCooldowns.isOnCooldown(player, weaponId, skill.getId(), nowTick)) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                boolean mainHand = (hand == InteractionHand.MAIN_HAND);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    serverPlayer,
                    new com.stardew.craft.combat.network.SkillFailFeedbackPayload(mainHand)
                );
            }
            return InteractionResultHolder.fail(stack);
        }

        WeaponSkillCooldowns.setCooldown(player, weaponId, skill.getId(), nowTick, cooldownTicks);

        LivingEntity target = findTargetEntity(player, 4.0);
        boolean isLightCounter = "light_counter".equals(skill.getId());
        
        if (target != null && !isLightCounter) {
            SkillContext context = SkillContext.builder()
                .skillId(skill.getId())
                .tier(SkillContext.SkillTier.MINOR)
                .damageMultiplier(skill.getDamagePercent() / 100.0f)
                .build();
            WeaponSkillContextStore.setPending(player, context, nowTick + 5);
        }

        // Rusty Sword: 破伤风（+10%易伤，3秒）
        if (target != null && "tetanus_strike".equals(skill.getId())) {
            target.addEffect(new MobEffectInstance(ModMobEffects.VULNERABLE, 60, 0));
        }

        // Wooden Blade: 树木庇佑（无目标也可获得“庇护II”，2秒）
        if (!level.isClientSide && "tree_blessing".equals(skill.getId())) {
            player.addEffect(new MobEffectInstance(ModMobEffects.SHELTER, 40, 1, false, false, true));
        }

        // Steel Smallsword: 轻剑反击（短暂无伤/减伤窗口）
        if ("light_counter".equals(skill.getId())) {
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 7, 0, false, false, true));
        }
        // Pirate's Sword: 亡命掠夺（消耗2点生命值，击杀恢复4点，未击杀获得海盗之怒）
        if (!level.isClientSide && "desperate_plunder".equals(skill.getId())) {
            // 消耗2点生命值（1颗心）
            float currentHealth = player.getHealth();
            float healthCost = 2.0f;
            // 至少保留0.5血，防止自杀
            if (currentHealth > healthCost + 0.5f) {
                player.setHealth(currentHealth - healthCost);
            } else {
                player.setHealth(0.5f);
            }
            
            // 记录目标当前血量，用于判断是否击杀
            if (target != null) {
                float targetHealthBefore = target.getHealth();
                // 标记这次攻击需要检查击杀
                DesperatePlunderTracker.setPending(player, target, targetHealthBefore);
            } else {
                // 无目标时，直接给愤怒buff
                player.addEffect(new MobEffectInstance(ModMobEffects.FURY, 60, 0, false, true, true));
            }
        }
        int animTicks = isLightCounter ? LightCounterParryState.DEFAULT_WINDOW_TICKS : SKILL_ANIM_TICKS;
        if (level.isClientSide) {
            com.stardew.craft.client.weapon.WeaponSkillAnimationClient.start(weaponId, skill.getId(), animTicks);
        }

        if (!level.isClientSide) {
            if (target != null && !isLightCounter) {
                player.attack(target);
                // 检查亡命掠夺的击杀判定
                DesperatePlunderTracker.checkAndResolve(player);
            }
            WeaponSkillAnimationLock.setLock(player, nowTick, animTicks);
            if (isLightCounter) {
                LightCounterParryState.start(player, nowTick, LightCounterParryState.DEFAULT_WINDOW_TICKS, weaponId);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                WeaponSkillAnimationDispatcher.sendSkillAnim(serverPlayer, weaponId, skill.getId(), animTicks);
            }
        }

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
    
    // ============== Getters ==============
    
    public String getWeaponId() {
        return weaponId;
    }
    
    public WeaponData getWeaponData() {
        return weaponData;
    }

    @SuppressWarnings("null")
    private void ensureWeaponStats(ItemStack stack) {
        if (weaponData == null) return;
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            @SuppressWarnings("null")
            CustomData data = stack.get(DataComponents.CUSTOM_DATA);
            if (data != null && data.copyTag().contains(WeaponStats.TAG_STARDEW_WEAPON)) {
                WeaponForgeData.ensure(stack);
                return;
            }
        }

        WeaponStats.builder()
                .weaponType(weaponData.getWeaponType())
                .minDamage(weaponData.getDamageMin())
                .maxDamage(weaponData.getDamageMax())
                .critChance((float) weaponData.getCritChance())
                .bonusCritPower((float) Math.max(0, (weaponData.getCritPower() - 1.0) * 100.0))
                .speed(weaponData.getSpeed())
                .defense(weaponData.getDefense())
                .knockback((float) weaponData.getWeight())
                .build()
                .writeToItemStack(stack);
            WeaponForgeData.ensure(stack);
    }

    private static LivingEntity findTargetEntity(Player player, double range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        @SuppressWarnings("null")
        Vec3 end = eyePos.add(lookVec.scale(range));
        @SuppressWarnings("null")
        AABB box = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0D);

        @SuppressWarnings("null")
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player.level(),
                player,
                eyePos,
                end,
                box,
                entity -> entity instanceof LivingEntity && entity.isPickable() && entity != player
        );

        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }

    private static List<LivingEntity> findTargetsInArc(Player player, double range, double minDot) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB box = player.getBoundingBox().inflate(range, range * 0.75, range);
        @SuppressWarnings("null")
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity.isPickable() && entity != player
        );

        targets.removeIf(entity -> {
            @SuppressWarnings("null")
            Vec3 to = entity.getEyePosition().subtract(origin);
            if (to.lengthSqr() > range * range) {
                return true;
            }
            @SuppressWarnings("null")
            double dot = to.normalize().dot(look);
            return dot < minDot;
        });

        targets.sort((a, b) -> {
            double da = a.distanceToSqr(player);
            double db = b.distanceToSqr(player);
            return Double.compare(da, db);
        });

        return targets;
    }

    private static List<LivingEntity> findTargetsOnPath(Player player, Vec3 start, Vec3 end, double halfWidth) {
        @SuppressWarnings("null")
        AABB box = player.getBoundingBox().expandTowards(end.subtract(start))
            .inflate(halfWidth, player.getBbHeight() * 0.75, halfWidth);

        @SuppressWarnings("null")
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity.isPickable() && entity != player
        );

        targets.removeIf(entity -> {
            Vec3 pos = entity.position();
            double dist = distancePointToSegment2D(
                pos.x, pos.z,
                start.x, start.z,
                end.x, end.z
            );
            return dist > halfWidth;
        });

        targets.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
        return targets;
    }

    private static double distancePointToSegment2D(double px, double pz, double ax, double az, double bx, double bz) {
        double abx = bx - ax;
        double abz = bz - az;
        double apx = px - ax;
        double apz = pz - az;
        double abLen2 = abx * abx + abz * abz;
        if (abLen2 <= 1.0E-6) {
            double dx = px - ax;
            double dz = pz - az;
            return Math.sqrt(dx * dx + dz * dz);
        }
        double t = (apx * abx + apz * abz) / abLen2;
        t = Mth.clamp(t, 0.0, 1.0);
        double cx = ax + abx * t;
        double cz = az + abz * t;
        double dx = px - cx;
        double dz = pz - cz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @SuppressWarnings("null")
    private static List<LivingEntity> findTargetsInRadius(Player player, double range) {
        Vec3 origin = player.position();
        AABB box = player.getBoundingBox().inflate(range, range * 0.75, range);
        return player.level().getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity.isPickable() && entity != player
                && entity.distanceToSqr(origin.x, origin.y, origin.z) <= range * range
        );
    }

    @SuppressWarnings("unused")
    private static LivingEntity findNearestTargetEntityInFront(Player player, double range) {
        Vec3 origin = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        @SuppressWarnings("null")
        Vec3 end = origin.add(look.scale(range));
        @SuppressWarnings("null")
        AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0D, 1.0D, 1.0D);

        @SuppressWarnings("null")
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity.isPickable() && entity != player
        );

        LivingEntity closest = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : targets) {
            Vec3 to = entity.getEyePosition().subtract(origin);
            double dist = to.lengthSqr();
            if (dist > range * range) {
                continue;
            }
            if (dist < bestDist) {
                bestDist = dist;
                closest = entity;
            }
        }
        return closest;
    }

    private static Vec3 getFrontPosition(LivingEntity target, Player player) {
        Vec3 targetPos = target.position();
        Vec3 playerPos = player.position();
        @SuppressWarnings("null")
        Vec3 dir = playerPos.subtract(targetPos);
        
        // 如果玩家和目标几乎重叠，用目标的反方向
        if (dir.horizontalDistanceSqr() < 0.5) {
            dir = target.getLookAngle().scale(-1);
        }
        
        // 计算目标面前的位置（距离目标中心约1格）
        double offsetDist = target.getBbWidth() * 0.5 + 0.8;
        Vec3 offset = new Vec3(dir.x, 0, dir.z).normalize().scale(offsetDist);
        Vec3 desired = targetPos.add(offset.x, 0, offset.z);
        desired = new Vec3(desired.x, target.getY(), desired.z);
        
        // 尝试找到安全位置
        Vec3 safe = findSafePosition(player, desired);
        if (safe != null) {
            return safe;
        }
        
        // 如果正前方不行，尝试稍微偏移的位置
        for (int i = 1; i <= 4; i++) {
            double angle = i * 0.4; // 大约每次偏移23度
            Vec3 rotatedOffset = rotateVec(offset, angle);
            Vec3 altDesired = targetPos.add(rotatedOffset.x, 0, rotatedOffset.z);
            altDesired = new Vec3(altDesired.x, target.getY(), altDesired.z);
            safe = findSafePosition(player, altDesired);
            if (safe != null) {
                return safe;
            }
            // 反方向也试一下
            rotatedOffset = rotateVec(offset, -angle);
            altDesired = targetPos.add(rotatedOffset.x, 0, rotatedOffset.z);
            altDesired = new Vec3(altDesired.x, target.getY(), altDesired.z);
            safe = findSafePosition(player, altDesired);
            if (safe != null) {
                return safe;
            }
        }
        
        // 实在找不到就返回原始期望位置（让玩家至少移动一点）
        return desired;
    }

    @SuppressWarnings("null")
    private static Vec3 getBehindPosition(LivingEntity target, Player player, double distance) {
        Vec3 targetPos = target.position();
        Vec3 look = target.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-6) {
            Vec3 fallback = targetPos.subtract(player.position());
            dir = new Vec3(fallback.x, 0, fallback.z);
        }
        if (dir.lengthSqr() < 1.0E-6) {
            dir = new Vec3(0, 0, 1);
        }

        dir = dir.normalize();
        double offsetDist = distance + target.getBbWidth() * 0.5;
        Vec3 offset = dir.scale(-offsetDist);
        Vec3 desired = targetPos.add(offset.x, 0, offset.z);
        desired = new Vec3(desired.x, target.getY(), desired.z);

        Vec3 safe = findSafePosition(player, desired);
        if (safe != null) {
            return safe;
        }

        for (int i = 1; i <= 4; i++) {
            double angle = i * 0.4;
            Vec3 rotatedOffset = rotateVec(offset, angle);
            Vec3 altDesired = targetPos.add(rotatedOffset.x, 0, rotatedOffset.z);
            altDesired = new Vec3(altDesired.x, target.getY(), altDesired.z);
            safe = findSafePosition(player, altDesired);
            if (safe != null) {
                return safe;
            }
            rotatedOffset = rotateVec(offset, -angle);
            altDesired = targetPos.add(rotatedOffset.x, 0, rotatedOffset.z);
            altDesired = new Vec3(altDesired.x, target.getY(), altDesired.z);
            safe = findSafePosition(player, altDesired);
            if (safe != null) {
                return safe;
            }
        }

        return desired;
    }
    
    private static Vec3 rotateVec(Vec3 vec, double radians) {
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        return new Vec3(vec.x * cos - vec.z * sin, vec.y, vec.x * sin + vec.z * cos);
    }

    @SuppressWarnings("null")
    private static void dashForward(Player player, double distance) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        Vec3 end = computeDashEnd(player, distance);
        DashMovementTracker.start(serverPlayer, player.level().getGameTime(), end, 5);
    }

    @SuppressWarnings("null")
    private static Vec3 computeDashEnd(Player player, double distance) {
        Vec3 start = player.position();
        Vec3 look = getHorizontalLook(player);
        @SuppressWarnings("null")
        Vec3 end = start.add(look.scale(distance));

        @SuppressWarnings("null")
        HitResult hit = player.level().clip(new ClipContext(
            start.add(0, player.getBbHeight() * 0.5, 0),
            end.add(0, player.getBbHeight() * 0.5, 0),
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
        ));

        if (hit.getType() != HitResult.Type.MISS) {
            Vec3 hitPos = hit.getLocation();
            end = hitPos.subtract(look.scale(0.4));
        }

        Vec3 safe = findSafePosition(player, end);
        return safe != null ? safe : end;
    }

    private static Vec3 getHorizontalLook(Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            float yawRad = (float) Math.toRadians(player.getYRot());
            horizontal = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        }
        return horizontal.normalize();
    }

    private static List<LivingEntity> findTargetsAlongPath(Player player, Vec3 start, Vec3 end, double radius) {
        Vec3 min = new Vec3(Math.min(start.x, end.x), Math.min(start.y, end.y), Math.min(start.z, end.z));
        Vec3 max = new Vec3(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z));
        AABB box = new AABB(min, max).inflate(radius, radius * 0.75, radius);
        @SuppressWarnings("null")
        List<LivingEntity> targets = player.level().getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity.isPickable() && entity != player
        );

        targets.removeIf(entity -> distanceToSegmentSqr(entity.position(), start, end) > radius * radius);
        return targets;
    }

    @SuppressWarnings("null")
    private static double distanceToSegmentSqr(Vec3 point, Vec3 a, Vec3 b) {
        @SuppressWarnings("null")
        Vec3 ab = b.subtract(a);
        @SuppressWarnings("null")
        Vec3 ap = point.subtract(a);
        double abLenSqr = ab.lengthSqr();
        if (abLenSqr <= 1.0e-6) {
            return ap.lengthSqr();
        }
        double t = ap.dot(ab) / abLenSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        @SuppressWarnings("null")
        Vec3 closest = a.add(ab.scale(t));
        return point.distanceToSqr(closest);
    }

    @SuppressWarnings("null")
    private static Vec3 findSafePosition(Player player, Vec3 desired) {
        if (desired == null) return null;
        AABB box = player.getBoundingBox().move(desired.x - player.getX(), desired.y - player.getY(), desired.z - player.getZ());
        if (player.level().noCollision(player, box)) {
            return desired;
        }
        Vec3 raised = desired.add(0, 0.25, 0);
        AABB boxUp = player.getBoundingBox().move(raised.x - player.getX(), raised.y - player.getY(), raised.z - player.getZ());
        if (player.level().noCollision(player, boxUp)) {
            return raised;
        }
        return null;
    }

    private static void teleportPlayer(Player player, Vec3 pos) {
        if (pos == null) return;
        player.teleportTo(pos.x, pos.y, pos.z);
        player.setDeltaMovement(0, player.getDeltaMovement().y, 0);
    }
    
    /**
     * 自定义武器Tier
     */
    private static class StardewWeaponTier implements Tier {
        private final int level;
        private final float attackDamage;
        private final float attackSpeed;
        
        public StardewWeaponTier(int level, float attackDamage, float attackSpeed) {
            this.level = level;
            this.attackDamage = attackDamage;
            this.attackSpeed = attackSpeed;
        }
        
        @Override
        public int getUses() {
            // 星露谷武器不损坏（给一个极大耐久避免被判定为0耐久立即破坏）
            return Integer.MAX_VALUE;
        }
        
        @Override
        public float getSpeed() {
            return attackSpeed;
        }
        
        @Override
        public float getAttackDamageBonus() {
            return attackDamage;
        }
        
        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_IRON_TOOL;
        }
        
        @Override
        public int getEnchantmentValue() {
            return level * 2;
        }
        
        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    }
}
