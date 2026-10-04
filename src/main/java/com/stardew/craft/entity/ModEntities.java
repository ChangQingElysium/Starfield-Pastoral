package com.stardew.craft.entity;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.stardew.craft.entity.projectile.MeowmereProjectileEntity;
import com.stardew.craft.entity.projectile.ElfBladeLeafEntity;
import com.stardew.craft.entity.projectile.TideAnchorProjectileEntity;
import com.stardew.craft.entity.projectile.TemperedBilletProjectileEntity;
import com.stardew.craft.entity.bomb.StardewBombEntity;
import com.stardew.craft.entity.effect.IceSpineEffectEntity;
import com.stardew.craft.entity.festival.MoonlightJellyEntity;
import com.stardew.craft.entity.mastery.PrismaticButterflyEntity;
import com.stardew.craft.entity.monster.LuckyPurpleShortsMonsterEntity;
import com.stardew.craft.entity.trinket.FairyCompanionEntity;
import com.stardew.craft.entity.animal.BaseCoopAnimalEntity;
import com.stardew.craft.entity.animal.CowEntity;
import com.stardew.craft.entity.animal.DinosaurEntity;
import com.stardew.craft.entity.animal.DuckEntity;
import com.stardew.craft.entity.animal.GoatEntity;
import com.stardew.craft.entity.animal.GoldenChickenEntity;
import com.stardew.craft.entity.animal.OstrichEntity;
import com.stardew.craft.entity.animal.PigEntity;
import com.stardew.craft.entity.animal.RabbitEntity;
import com.stardew.craft.entity.animal.SheepEntity;
import com.stardew.craft.entity.animal.VoidChickenEntity;
import com.stardew.craft.entity.animal.WhiteChickenEntity;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.cutscene.runtime.EventActorEntity;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.entity.seat.SofaSeatEntity;
import com.stardew.craft.entity.seat.CushionEntity;
import com.stardew.craft.entity.decor.CarpetEntity;
import com.stardew.craft.entity.minecart.MinecartStationEntity;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

public final class ModEntities {
	private ModEntities() {
	}

	@SuppressWarnings("null")
	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, StardewCraft.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineSquidKidEntity>> SQUID_KID = ENTITY_TYPES.register(
            "squid_kid", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineSquidKidEntity>of(
                    com.stardew.craft.entity.monster.MineSquidKidEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineSquidKidEntity.WIDTH, com.stardew.craft.entity.monster.MineSquidKidEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("squid_kid"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.projectile.RexBreathEntity>> REX_BREATH = ENTITY_TYPES.register("rex_breath", () -> EntityType.Builder.<com.stardew.craft.entity.projectile.RexBreathEntity>of(com.stardew.craft.entity.projectile.RexBreathEntity::new, MobCategory.MISC).sized(29F/64,29F/64).clientTrackingRange(8).updateInterval(1).build("rex_breath"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.projectile.SquidFireballEntity>> SQUID_FIREBALL = ENTITY_TYPES.register("squid_fireball", () -> EntityType.Builder.<com.stardew.craft.entity.projectile.SquidFireballEntity>of(com.stardew.craft.entity.projectile.SquidFireballEntity::new, MobCategory.MISC).sized(21F/64,21F/64).clientTrackingRange(8).updateInterval(1).build("squid_fireball"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineShadowShamanEntity>> SHADOW_SHAMAN = ENTITY_TYPES.register(
            "shadow_shaman", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineShadowShamanEntity>of(
                    com.stardew.craft.entity.monster.MineShadowShamanEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineShadowShamanEntity.WIDTH, com.stardew.craft.entity.monster.MineShadowShamanEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("shadow_shaman"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.projectile.ShamanCurseEntity>> SHAMAN_CURSE = ENTITY_TYPES.register("shaman_curse", () -> EntityType.Builder.<com.stardew.craft.entity.projectile.ShamanCurseEntity>of(com.stardew.craft.entity.projectile.ShamanCurseEntity::new, MobCategory.MISC).sized(21F/64,21F/64).clientTrackingRange(8).updateInterval(1).build("shaman_curse"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineShadowBruteEntity>> SHADOW_BRUTE = ENTITY_TYPES.register(
            "shadow_brute", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineShadowBruteEntity>of(
                    com.stardew.craft.entity.monster.MineShadowBruteEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineShadowBruteEntity.WIDTH, com.stardew.craft.entity.monster.MineShadowBruteEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("shadow_brute"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineMetalHeadEntity>> METAL_HEAD = ENTITY_TYPES.register(
            "metal_head", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineMetalHeadEntity>of(
                    com.stardew.craft.entity.monster.MineMetalHeadEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineMetalHeadEntity.WIDTH, com.stardew.craft.entity.monster.MineMetalHeadEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("metal_head"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineRockGolemEntity>> ROCK_GOLEM = ENTITY_TYPES.register(
            "rock_golem", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineRockGolemEntity>of(
                    com.stardew.craft.entity.monster.MineRockGolemEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineRockGolemEntity.WIDTH, com.stardew.craft.entity.monster.MineRockGolemEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("rock_golem"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineRockGolemEntity>> WILDERNESS_GOLEM = ENTITY_TYPES.register(
            "wilderness_golem", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineRockGolemEntity>of(
                    (type, level) -> new com.stardew.craft.entity.monster.MineRockGolemEntity(type, level, com.stardew.craft.entity.monster.MineRockGolemEntity.Variant.WILDERNESS), MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineRockGolemEntity.WIDTH, com.stardew.craft.entity.monster.MineRockGolemEntity.FARM_HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("wilderness_golem"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineRockGolemEntity>> IRIDIUM_GOLEM = ENTITY_TYPES.register(
            "iridium_golem", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineRockGolemEntity>of(
                    (type, level) -> new com.stardew.craft.entity.monster.MineRockGolemEntity(type, level, com.stardew.craft.entity.monster.MineRockGolemEntity.Variant.IRIDIUM), MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineRockGolemEntity.WIDTH, com.stardew.craft.entity.monster.MineRockGolemEntity.FARM_HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("iridium_golem"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineSkeletonEntity>> SKELETON = ENTITY_TYPES.register(
            "skeleton", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineSkeletonEntity>of(
                    com.stardew.craft.entity.monster.MineSkeletonEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineSkeletonEntity.WIDTH, com.stardew.craft.entity.monster.MineSkeletonEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("skeleton"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.projectile.SkeletonBoneEntity>> SKELETON_BONE = ENTITY_TYPES.register(
            "skeleton_bone", () -> EntityType.Builder.<com.stardew.craft.entity.projectile.SkeletonBoneEntity>of(
                    com.stardew.craft.entity.projectile.SkeletonBoneEntity::new, MobCategory.MISC)
                    .sized(com.stardew.craft.entity.projectile.SkeletonBoneEntity.WIDTH, com.stardew.craft.entity.projectile.SkeletonBoneEntity.WIDTH)
                    .clientTrackingRange(8).updateInterval(1).build("skeleton_bone"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineGhostEntity>> GHOST = ENTITY_TYPES.register(
            "ghost", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineGhostEntity>of(
                    (type, level) -> new com.stardew.craft.entity.monster.MineGhostEntity(type, level, false), MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineGhostEntity.WIDTH, com.stardew.craft.entity.monster.MineGhostEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("ghost"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineGhostEntity>> CARBON_GHOST = ENTITY_TYPES.register(
            "carbon_ghost", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineGhostEntity>of(
                    (type, level) -> new com.stardew.craft.entity.monster.MineGhostEntity(type, level, true), MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineGhostEntity.WIDTH, com.stardew.craft.entity.monster.MineGhostEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("carbon_ghost"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineDustSpiritEntity>> DUST_SPIRIT = ENTITY_TYPES.register(
            "dust_sprite", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineDustSpiritEntity>of(
                    com.stardew.craft.entity.monster.MineDustSpiritEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineDustSpiritEntity.WIDTH, com.stardew.craft.entity.monster.MineDustSpiritEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("dust_sprite"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineDuggyEntity>> DUGGY = ENTITY_TYPES.register(
            "duggy", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineDuggyEntity>of(
                    com.stardew.craft.entity.monster.MineDuggyEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineDuggyEntity.WIDTH, com.stardew.craft.entity.monster.MineDuggyEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("duggy"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineGrubEntity>> GRUB = ENTITY_TYPES.register(
            "grub", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineGrubEntity>of(
                    com.stardew.craft.entity.monster.MineGrubEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineGrubEntity.WIDTH, com.stardew.craft.entity.monster.MineGrubEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("grub"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineFlyEntity>> FLY = ENTITY_TYPES.register(
            "fly", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineFlyEntity>of(
                    com.stardew.craft.entity.monster.MineFlyEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineFlyEntity.WIDTH, com.stardew.craft.entity.monster.MineFlyEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("fly"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineSerpentEntity>> SERPENT = ENTITY_TYPES.register(
            "serpent", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineSerpentEntity>of(com.stardew.craft.entity.monster.MineSerpentEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineSerpentEntity.WIDTH, com.stardew.craft.entity.monster.MineSerpentEntity.HEIGHT).clientTrackingRange(8).updateInterval(1).build("serpent"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MinePepperRexEntity>> PEPPER_REX = ENTITY_TYPES.register(
            "pepper_rex", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MinePepperRexEntity>of(com.stardew.craft.entity.monster.MinePepperRexEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MinePepperRexEntity.WIDTH, com.stardew.craft.entity.monster.MinePepperRexEntity.HEIGHT).clientTrackingRange(8).updateInterval(1).build("pepper_rex"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineMummyEntity>> MUMMY = ENTITY_TYPES.register(
            "mummy", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineMummyEntity>of(com.stardew.craft.entity.monster.MineMummyEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineMummyEntity.WIDTH, com.stardew.craft.entity.monster.MineMummyEntity.HEIGHT).clientTrackingRange(8).updateInterval(1).build("mummy"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineBigSlimeEntity>> BIG_SLIME = ENTITY_TYPES.register(
            "big_slime", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineBigSlimeEntity>of(
                    com.stardew.craft.entity.monster.MineBigSlimeEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineBigSlimeEntity.WIDTH, com.stardew.craft.entity.monster.MineBigSlimeEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("big_slime"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineBugEntity>> BUG = ENTITY_TYPES.register(
            "bug", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineBugEntity>of(
                    com.stardew.craft.entity.monster.MineBugEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineBugEntity.WIDTH, com.stardew.craft.entity.monster.MineBugEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("bug"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineBugEntity>> ARMORED_BUG = ENTITY_TYPES.register(
            "armored_bug", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineBugEntity>of(
                    (type, level) -> new com.stardew.craft.entity.monster.MineBugEntity(type, level, true), MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineBugEntity.WIDTH, com.stardew.craft.entity.monster.MineBugEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("armored_bug"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.RockCrabEntity>> ROCK_CRAB = ENTITY_TYPES.register(
            "rock_crab", () -> EntityType.Builder.<com.stardew.craft.entity.monster.RockCrabEntity>of(
                    com.stardew.craft.entity.monster.RockCrabEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.RockCrabEntity.WIDTH, com.stardew.craft.entity.monster.RockCrabEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("rock_crab"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.RockCrabEntity>> LAVA_CRAB = ENTITY_TYPES.register(
            "lava_crab", () -> EntityType.Builder.<com.stardew.craft.entity.monster.RockCrabEntity>of(
                    (type, level) -> new com.stardew.craft.entity.monster.RockCrabEntity(type, level, "lava_crab"), MobCategory.MONSTER)
                    .sized(1.0F, 0.95F).clientTrackingRange(8).updateInterval(1).build("lava_crab"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.RockCrabEntity>> IRIDIUM_CRAB = ENTITY_TYPES.register(
            "iridium_crab", () -> EntityType.Builder.<com.stardew.craft.entity.monster.RockCrabEntity>of(
                    (type, level) -> new com.stardew.craft.entity.monster.RockCrabEntity(type, level, "iridium_crab"), MobCategory.MONSTER)
                    .sized(1.0F, 1.2F).clientTrackingRange(8).updateInterval(1).build("iridium_crab"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineBatEntity>> BAT = ENTITY_TYPES.register(
            "bat", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineBatEntity>of(
                    com.stardew.craft.entity.monster.MineBatEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineBatEntity.WIDTH, com.stardew.craft.entity.monster.MineBatEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("bat"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineBatEntity>> FROST_BAT = ENTITY_TYPES.register(
            "frost_bat", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineBatEntity>of(
                    com.stardew.craft.entity.monster.MineBatEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineBatEntity.WIDTH, com.stardew.craft.entity.monster.MineBatEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("frost_bat"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineBatEntity>> LAVA_BAT = ENTITY_TYPES.register(
            "lava_bat", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineBatEntity>of(
                    com.stardew.craft.entity.monster.MineBatEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineBatEntity.WIDTH, com.stardew.craft.entity.monster.MineBatEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("lava_bat"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.MineBatEntity>> IRIDIUM_BAT = ENTITY_TYPES.register(
            "iridium_bat", () -> EntityType.Builder.<com.stardew.craft.entity.monster.MineBatEntity>of(
                    com.stardew.craft.entity.monster.MineBatEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.MineBatEntity.WIDTH, com.stardew.craft.entity.monster.MineBatEntity.HEIGHT)
                    .clientTrackingRange(8).updateInterval(1).build("iridium_bat"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.GreenSlimeEntity>> GREEN_SLIME = ENTITY_TYPES.register(
            "green_slime", () -> EntityType.Builder.<com.stardew.craft.entity.monster.GreenSlimeEntity>of(
                    com.stardew.craft.entity.monster.GreenSlimeEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_WIDTH,
                            com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_BODY_HEIGHT
                                    + com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_TOP_MARGIN)
                    .clientTrackingRange(8).updateInterval(1).build("green_slime"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.GreenSlimeEntity>> FROST_JELLY = ENTITY_TYPES.register(
            "frost_jelly", () -> EntityType.Builder.<com.stardew.craft.entity.monster.GreenSlimeEntity>of(
                    com.stardew.craft.entity.monster.GreenSlimeEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_WIDTH,
                            com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_BODY_HEIGHT
                                    + com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_TOP_MARGIN)
                    .clientTrackingRange(8).updateInterval(1).build("frost_jelly"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.monster.GreenSlimeEntity>> SLUDGE = ENTITY_TYPES.register(
            "sludge", () -> EntityType.Builder.<com.stardew.craft.entity.monster.GreenSlimeEntity>of(
                    com.stardew.craft.entity.monster.GreenSlimeEntity::new, MobCategory.MONSTER)
                    .sized(com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_WIDTH,
                            com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_BODY_HEIGHT
                                    + com.stardew.craft.entity.monster.GreenSlimeRules.COLLISION_TOP_MARGIN)
                    .clientTrackingRange(8).updateInterval(1).build("sludge"));

	public static final DeferredHolder<EntityType<?>, EntityType<MeowmereProjectileEntity>> MEOWMERE_PROJECTILE = ENTITY_TYPES.register(
			"meowmere_projectile",
			() -> EntityType.Builder.<MeowmereProjectileEntity>of(MeowmereProjectileEntity::new, MobCategory.MISC)
					.sized(0.3F, 0.3F) // 猫头大小
					.clientTrackingRange(4)
					.updateInterval(1)
					.build("meowmere_projectile")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<TideAnchorProjectileEntity>> TIDE_ANCHOR_PROJECTILE = ENTITY_TYPES.register(
			"tide_anchor_projectile",
			() -> EntityType.Builder.<TideAnchorProjectileEntity>of(TideAnchorProjectileEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.clientTrackingRange(4)
					.updateInterval(1)
					.build("tide_anchor_projectile")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<TemperedBilletProjectileEntity>> TEMPERED_BILLET_PROJECTILE = ENTITY_TYPES.register(
			"tempered_billet_projectile",
			() -> EntityType.Builder.<TemperedBilletProjectileEntity>of(TemperedBilletProjectileEntity::new, MobCategory.MISC)
					.sized(0.35F, 0.35F)
					.clientTrackingRange(4)
					.updateInterval(1)
					.build("tempered_billet_projectile")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<ElfBladeLeafEntity>> ELF_BLADE_LEAF = ENTITY_TYPES.register(
			"elf_blade_leaf",
			() -> EntityType.Builder.<ElfBladeLeafEntity>of(ElfBladeLeafEntity::new, MobCategory.MISC)
					.sized(0.35F, 0.35F)
					.clientTrackingRange(4)
					.updateInterval(1)
					.build("elf_blade_leaf")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<IceSpineEffectEntity>> ICE_SPINE_EFFECT = ENTITY_TYPES.register(
			"ice_spine_effect",
			() -> EntityType.Builder.<IceSpineEffectEntity>of(IceSpineEffectEntity::new, MobCategory.MISC)
					.sized(0.8F, 0.8F)
					.clientTrackingRange(32)
					.updateInterval(1)
					.build("ice_spine_effect")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<PrismaticButterflyEntity>> PRISMATIC_BUTTERFLY = ENTITY_TYPES.register(
			"prismatic_butterfly",
			() -> EntityType.Builder.<PrismaticButterflyEntity>of(PrismaticButterflyEntity::new, MobCategory.MISC)
					.sized(0.6F, 0.6F)
					.clientTrackingRange(64)
					.updateInterval(1)
					.build("prismatic_butterfly")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<MoonlightJellyEntity>> MOONLIGHT_JELLY = ENTITY_TYPES.register(
			"moonlight_jelly",
			() -> EntityType.Builder.<MoonlightJellyEntity>of(MoonlightJellyEntity::new, MobCategory.MISC)
					.sized(1.2F, 1.2F)
					.clientTrackingRange(64)
					.updateInterval(1)
					.build("moonlight_jelly")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<FairyCompanionEntity>> FAIRY_COMPANION = ENTITY_TYPES.register(
			"fairy_companion",
			() -> EntityType.Builder.<FairyCompanionEntity>of(FairyCompanionEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.clientTrackingRange(64)
					.updateInterval(1)
					.build("fairy_companion")
	);

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.projectile.SlingshotProjectile>> SLINGSHOT_PROJECTILE = ENTITY_TYPES.register("slingshot_projectile",
            () -> EntityType.Builder.<com.stardew.craft.entity.projectile.SlingshotProjectile>of(com.stardew.craft.entity.projectile.SlingshotProjectile::new, MobCategory.MISC)
                    .sized(29/64f, 29/64f).clientTrackingRange(8).updateInterval(1).build("slingshot_projectile"));

	public static final DeferredHolder<EntityType<?>, EntityType<StardewBombEntity>> STARDEW_BOMB = ENTITY_TYPES.register(
			"stardew_bomb",
			() -> EntityType.Builder.<StardewBombEntity>of(StardewBombEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build("stardew_bomb")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<FallenPrefabTreeEntity>> FALLEN_PREFAB_TREE = ENTITY_TYPES.register(
			"fallen_prefab_tree",
			() -> EntityType.Builder.<FallenPrefabTreeEntity>of(FallenPrefabTreeEntity::new, MobCategory.MISC)
					// Prefab trees can be up to 7x7 and tall; when they fall sideways the reach grows,
					// so use a generous bounding size to keep frustum culling from hiding the tip.
					.sized(16.0F, 16.0F)
					.clientTrackingRange(64)
					.updateInterval(1)
					.build("fallen_prefab_tree")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<DuckEntity>> DUCK = ENTITY_TYPES.register(
			"duck",
			() -> EntityType.Builder.<DuckEntity>of(DuckEntity::new, MobCategory.CREATURE)
					.sized(0.7F, 0.8F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("duck")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<WhiteChickenEntity>> WHITE_CHICKEN = ENTITY_TYPES.register(
			"white_chicken",
			() -> EntityType.Builder.<WhiteChickenEntity>of(WhiteChickenEntity::new, MobCategory.CREATURE)
					.sized(0.7F, 0.8F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("white_chicken")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<GoldenChickenEntity>> GOLDEN_CHICKEN = ENTITY_TYPES.register(
			"golden_chicken",
			() -> EntityType.Builder.<GoldenChickenEntity>of(GoldenChickenEntity::new, MobCategory.CREATURE)
					.sized(0.7F, 0.8F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("golden_chicken")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<VoidChickenEntity>> VOID_CHICKEN = ENTITY_TYPES.register(
			"void_chicken",
			() -> EntityType.Builder.<VoidChickenEntity>of(VoidChickenEntity::new, MobCategory.CREATURE)
					.sized(0.7F, 0.8F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("void_chicken")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<RabbitEntity>> RABBIT = ENTITY_TYPES.register(
			"rabbit",
			() -> EntityType.Builder.<RabbitEntity>of(RabbitEntity::new, MobCategory.CREATURE)
					.sized(0.7F, 0.8F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("rabbit")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<OstrichEntity>> OSTRICH = ENTITY_TYPES.register(
			"ostrich",
			() -> EntityType.Builder.<OstrichEntity>of(OstrichEntity::new, MobCategory.CREATURE)
					.sized(0.9F, 1.5F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("ostrich")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<DinosaurEntity>> DINOSAUR = ENTITY_TYPES.register(
			"dinosaur",
			() -> EntityType.Builder.<DinosaurEntity>of(DinosaurEntity::new, MobCategory.CREATURE)
					.sized(0.9F, 1.2F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("dinosaur")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<CowEntity>> COW = ENTITY_TYPES.register(
			"cow",
			() -> EntityType.Builder.<CowEntity>of(CowEntity::new, MobCategory.CREATURE)
					.sized(0.9F, 1.3F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("cow")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<GoatEntity>> GOAT = ENTITY_TYPES.register(
			"goat",
			() -> EntityType.Builder.<GoatEntity>of(GoatEntity::new, MobCategory.CREATURE)
					.sized(0.9F, 1.3F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("goat")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<SheepEntity>> SHEEP = ENTITY_TYPES.register(
			"sheep",
			() -> EntityType.Builder.<SheepEntity>of(SheepEntity::new, MobCategory.CREATURE)
					.sized(0.9F, 1.3F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("sheep")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<PigEntity>> PIG = ENTITY_TYPES.register(
			"pig",
			() -> EntityType.Builder.<PigEntity>of(PigEntity::new, MobCategory.CREATURE)
					.sized(0.9F, 1.3F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("pig")
	);

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.pet.PetEntity>> PET = ENTITY_TYPES.register("pet", () -> EntityType.Builder.of(com.stardew.craft.pet.PetEntity::new, MobCategory.CREATURE).sized(.7F, 1.05F).clientTrackingRange(8).updateInterval(2).build("stardewcraft:pet"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.animal.runtime.LivestockEntity>> LIVESTOCK_ANIMAL = ENTITY_TYPES.register(
            "livestock_animal", () -> EntityType.Builder.<com.stardew.craft.animal.runtime.LivestockEntity>of(com.stardew.craft.animal.runtime.LivestockEntity::new, MobCategory.CREATURE).sized(0.65f, 0.8f).clientTrackingRange(8).build("livestock_animal"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.animal.runtime.LivestockProductEntity>> LIVESTOCK_PRODUCT = ENTITY_TYPES.register(
            "livestock_product", () -> EntityType.Builder.<com.stardew.craft.animal.runtime.LivestockProductEntity>of(com.stardew.craft.animal.runtime.LivestockProductEntity::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(6).build("livestock_product"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.building.runtime.RobinConstructionEntity>> ROBIN_CONSTRUCTION = ENTITY_TYPES.register(
            "robin_construction", () -> EntityType.Builder.<com.stardew.craft.building.runtime.RobinConstructionEntity>of(
                    com.stardew.craft.building.runtime.RobinConstructionEntity::new, MobCategory.MISC)
                    // Keep the stationary worker's facing and animation smooth on clients.
                    .sized(0.6f, 1.9f).clientTrackingRange(8).updateInterval(1).build("robin_construction"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.gingerisland.GiantTurtleEntity>> GINGER_GIANT_TURTLE = ENTITY_TYPES.register(
            "ginger_giant_turtle", () -> EntityType.Builder.<com.stardew.craft.gingerisland.GiantTurtleEntity>of(
                    com.stardew.craft.gingerisland.GiantTurtleEntity::new, MobCategory.MISC)
                    .sized(com.stardew.craft.gingerisland.GiantTurtleEntity.WIDTH, com.stardew.craft.gingerisland.GiantTurtleEntity.HEIGHT)
                    .clientTrackingRange(12).updateInterval(2).build("ginger_giant_turtle"));

	public static final DeferredHolder<EntityType<?>, EntityType<StardewNpcEntity>> STARDEW_NPC = ENTITY_TYPES.register(
			"stardew_npc",
			() -> EntityType.Builder.<StardewNpcEntity>of(StardewNpcEntity::new, MobCategory.CREATURE)
					.sized(0.6F, 1.8F)
					.clientTrackingRange(16)
					.updateInterval(2)
					.build("stardew_npc")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<EventActorEntity>> EVENT_ACTOR = ENTITY_TYPES.register(
			"event_actor",
			() -> EntityType.Builder.<EventActorEntity>of(EventActorEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.clientTrackingRange(16)
					.updateInterval(2)
					.noSave()
					.build("event_actor")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<EventPlayerActorEntity>> EVENT_PLAYER_ACTOR = ENTITY_TYPES.register(
			"event_player_actor",
			() -> EntityType.Builder.<EventPlayerActorEntity>of(EventPlayerActorEntity::new, MobCategory.MISC)
					.sized(0.6F, 1.8F)
					.clientTrackingRange(16)
					.updateInterval(2)
					.noSave()
					.build("event_player_actor")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<JunimoEntity>> JUNIMO = ENTITY_TYPES.register(
			"junimo",
			() -> EntityType.Builder.<JunimoEntity>of(JunimoEntity::new, MobCategory.CREATURE)
					.sized(0.5F, 0.7F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("junimo")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.npc.BooksellerEntity>> BOOKSELLER = ENTITY_TYPES.register(
			"bookseller",
			() -> EntityType.Builder.<com.stardew.craft.entity.npc.BooksellerEntity>of(
							com.stardew.craft.entity.npc.BooksellerEntity::new, MobCategory.MISC)
					.sized(0.7F, 2.1F)
					.clientTrackingRange(10)
					.updateInterval(20)
					.build("bookseller")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.npc.CamelMerchantEntity>> CAMEL_MERCHANT = ENTITY_TYPES.register(
			"camel_merchant",
			() -> EntityType.Builder.<com.stardew.craft.entity.npc.CamelMerchantEntity>of(
							com.stardew.craft.entity.npc.CamelMerchantEntity::new, MobCategory.MISC)
					.sized(0.7F, 2.1F)
					.clientTrackingRange(10)
					.updateInterval(20)
					.build("camel_merchant")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.npc.TravelingCartEntity>> TRAVELING_CART = ENTITY_TYPES.register(
			"traveling_cart",
			() -> EntityType.Builder.<com.stardew.craft.entity.npc.TravelingCartEntity>of(
							com.stardew.craft.entity.npc.TravelingCartEntity::new, MobCategory.MISC)
					.sized(3.8F, 3.2F)
					.clientTrackingRange(10)
					.updateInterval(20)
					.build("traveling_cart")
	);

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.seat.BirdSpringRiderSeatEntity>> BIRD_SPRING_RIDER_SEAT = ENTITY_TYPES.register(
            "bird_spring_rider_seat", () -> EntityType.Builder.<com.stardew.craft.entity.seat.BirdSpringRiderSeatEntity>of(
                    com.stardew.craft.entity.seat.BirdSpringRiderSeatEntity::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F).clientTrackingRange(10).updateInterval(1).build("bird_spring_rider_seat"));

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.seat.DoubleSwingSeatEntity>> DOUBLE_SWING_SEAT = ENTITY_TYPES.register(
            "double_swing_seat", () -> EntityType.Builder.<com.stardew.craft.entity.seat.DoubleSwingSeatEntity>of(
                    com.stardew.craft.entity.seat.DoubleSwingSeatEntity::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F).clientTrackingRange(10).updateInterval(1).build("double_swing_seat"));

	public static final DeferredHolder<EntityType<?>, EntityType<SofaSeatEntity>> SOFA_SEAT = ENTITY_TYPES.register(
			"sofa_seat",
			() -> EntityType.Builder.<SofaSeatEntity>of(SofaSeatEntity::new, MobCategory.MISC)
					.sized(0.01F, 0.01F)
					.clientTrackingRange(8)
					.updateInterval(1)
					.build("sofa_seat")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<CushionEntity>> CUSHION = ENTITY_TYPES.register(
			"cushion",
			() -> EntityType.Builder.<CushionEntity>of(CushionEntity::new, MobCategory.MISC)
					.sized(1.0F, 0.25F)
					.passengerAttachments(0.25F)
					.clientTrackingRange(10)
					.updateInterval(Integer.MAX_VALUE)
					.setShouldReceiveVelocityUpdates(false)
					.build("cushion")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<CarpetEntity>> CARPET = ENTITY_TYPES.register(
			"carpet",
			() -> EntityType.Builder.<CarpetEntity>of(CarpetEntity::new, MobCategory.MISC)
					.sized(1.0F, 0.1F)
					.clientTrackingRange(32)
					.updateInterval(20)
					.build("carpet")
	);

    public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.minecart.CoalMinecartEntity>> COAL_MINECART = ENTITY_TYPES.register(
            "coal_minecart", () -> EntityType.Builder.<com.stardew.craft.entity.minecart.CoalMinecartEntity>of(
                    com.stardew.craft.entity.minecart.CoalMinecartEntity::new, MobCategory.MISC)
                    .sized(1.0F, 1.1875F).clientTrackingRange(10).updateInterval(40).build("coal_minecart"));

	public static final DeferredHolder<EntityType<?>, EntityType<MinecartStationEntity>> MINECART_STATION = ENTITY_TYPES.register(
			"minecart_station",
			() -> EntityType.Builder.<MinecartStationEntity>of(MinecartStationEntity::new, MobCategory.MISC)
					.sized(1.0F, 1.1875F)
					.clientTrackingRange(8)
					.updateInterval(40)
					.build("minecart_station")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<com.stardew.craft.entity.passive.CrowEntity>> CROW = ENTITY_TYPES.register(
			"crow",
			() -> EntityType.Builder.<com.stardew.craft.entity.passive.CrowEntity>of(com.stardew.craft.entity.passive.CrowEntity::new, MobCategory.CREATURE)
					.sized(0.4F, 0.5F)
					.clientTrackingRange(8)
					.updateInterval(3)
					.build("crow")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<LuckyPurpleShortsMonsterEntity>> LUCKY_PURPLE_SHORTS_MONSTER = ENTITY_TYPES.register(
			"lucky_purple_shorts_monster",
			() -> EntityType.Builder.<LuckyPurpleShortsMonsterEntity>of(LuckyPurpleShortsMonsterEntity::new, MobCategory.MONSTER)
					.sized(0.6F, 0.9F)
					.clientTrackingRange(16)
					.updateInterval(2)
					.noSave()
					.build("lucky_purple_shorts_monster")
	);

	@SuppressWarnings("null")
	public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(SQUID_KID.get(), com.stardew.craft.entity.monster.MineSquidKidEntity.createAttributes().build());
        event.put(SHADOW_SHAMAN.get(), com.stardew.craft.entity.monster.MineShadowShamanEntity.createAttributes().build());
        event.put(SHADOW_BRUTE.get(), com.stardew.craft.entity.monster.MineShadowBruteEntity.createAttributes().build());
        event.put(METAL_HEAD.get(), com.stardew.craft.entity.monster.MineMetalHeadEntity.createAttributes().build());
        event.put(ROCK_GOLEM.get(), com.stardew.craft.entity.monster.MineRockGolemEntity.createAttributes().build());
        event.put(WILDERNESS_GOLEM.get(), com.stardew.craft.entity.monster.MineRockGolemEntity.createAttributes().build());
        event.put(IRIDIUM_GOLEM.get(), com.stardew.craft.entity.monster.MineRockGolemEntity.createAttributes().build());
        event.put(SKELETON.get(), com.stardew.craft.entity.monster.MineSkeletonEntity.createAttributes().build());
        event.put(GHOST.get(), com.stardew.craft.entity.monster.MineGhostEntity.createAttributes().build());
        event.put(CARBON_GHOST.get(), com.stardew.craft.entity.monster.MineGhostEntity.createAttributes().build());
        event.put(DUST_SPIRIT.get(), com.stardew.craft.entity.monster.MineDustSpiritEntity.createAttributes().build());
        event.put(DUGGY.get(), com.stardew.craft.entity.monster.MineDuggyEntity.createAttributes().build());
        event.put(GRUB.get(), com.stardew.craft.entity.monster.MineGrubEntity.createAttributes().build());
        event.put(FLY.get(), com.stardew.craft.entity.monster.MineFlyEntity.createAttributes().build());
        event.put(SERPENT.get(), com.stardew.craft.entity.monster.MineSerpentEntity.createAttributes().build());
        event.put(PEPPER_REX.get(), com.stardew.craft.entity.monster.MinePepperRexEntity.createAttributes().build());
        event.put(MUMMY.get(), com.stardew.craft.entity.monster.MineMummyEntity.createAttributes().build());
        event.put(BIG_SLIME.get(), com.stardew.craft.entity.monster.MineBigSlimeEntity.createAttributes().build());
        event.put(ARMORED_BUG.get(), com.stardew.craft.entity.monster.MineBugEntity.createAttributes().build());
        event.put(BUG.get(), com.stardew.craft.entity.monster.MineBugEntity.createAttributes().build());
        event.put(LAVA_CRAB.get(), com.stardew.craft.entity.monster.RockCrabEntity.createAttributes().build());
        event.put(IRIDIUM_CRAB.get(), com.stardew.craft.entity.monster.RockCrabEntity.createAttributes().build());
        event.put(ROCK_CRAB.get(), com.stardew.craft.entity.monster.RockCrabEntity.createAttributes().build());
        event.put(BAT.get(), com.stardew.craft.entity.monster.MineBatEntity.createAttributes().build());
        event.put(FROST_BAT.get(), com.stardew.craft.entity.monster.MineBatEntity.createAttributes().build());
        event.put(LAVA_BAT.get(), com.stardew.craft.entity.monster.MineBatEntity.createAttributes().build());
        event.put(IRIDIUM_BAT.get(), com.stardew.craft.entity.monster.MineBatEntity.createAttributes().build());
        event.put(GREEN_SLIME.get(), com.stardew.craft.entity.monster.GreenSlimeEntity.createAttributes().build());
        event.put(FROST_JELLY.get(), com.stardew.craft.entity.monster.GreenSlimeEntity.createAttributes().build());
        event.put(SLUDGE.get(), com.stardew.craft.entity.monster.GreenSlimeEntity.createAttributes().build());
		event.put(DUCK.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(PET.get(), com.stardew.craft.pet.PetEntity.attributes().build());
		event.put(LIVESTOCK_ANIMAL.get(), com.stardew.craft.animal.runtime.LivestockEntity.attributes().build());
		event.put(WHITE_CHICKEN.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(GOLDEN_CHICKEN.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(VOID_CHICKEN.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(RABBIT.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(OSTRICH.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(DINOSAUR.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(COW.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(GOAT.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(SHEEP.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(PIG.get(), BaseCoopAnimalEntity.createAttributes().build());
		event.put(STARDEW_NPC.get(), StardewNpcEntity.createAttributes().build());
		event.put(EVENT_ACTOR.get(), EventActorEntity.createAttributes().build());
		event.put(EVENT_PLAYER_ACTOR.get(), EventPlayerActorEntity.createAttributes().build());
		event.put(JUNIMO.get(), JunimoEntity.createAttributes().build());
		event.put(BOOKSELLER.get(), com.stardew.craft.entity.npc.BooksellerEntity.createAttributes().build());
		event.put(CAMEL_MERCHANT.get(), com.stardew.craft.entity.npc.CamelMerchantEntity.createAttributes().build());
		event.put(TRAVELING_CART.get(), com.stardew.craft.entity.npc.TravelingCartEntity.createAttributes().build());
		event.put(CROW.get(), com.stardew.craft.entity.passive.CrowEntity.createAttributes().build());
		event.put(LUCKY_PURPLE_SHORTS_MONSTER.get(), LuckyPurpleShortsMonsterEntity.createAttributes().build());
	}

}
