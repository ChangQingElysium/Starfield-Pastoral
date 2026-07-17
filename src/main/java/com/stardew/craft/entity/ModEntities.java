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

	public static final DeferredHolder<EntityType<?>, EntityType<StardewBombEntity>> STARDEW_BOMB = ENTITY_TYPES.register(
			"stardew_bomb",
			() -> EntityType.Builder.<StardewBombEntity>of(StardewBombEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.clientTrackingRange(16)
					.updateInterval(1)
					.build("stardew_bomb")
	);

	public static final DeferredHolder<EntityType<?>, EntityType<FallenOakTreeEntity>> FALLEN_OAK_TREE = ENTITY_TYPES.register(
			"fallen_oak_tree",
			() -> EntityType.Builder.<FallenOakTreeEntity>of(FallenOakTreeEntity::new, MobCategory.MISC)
					// Needs a real size, otherwise frustum culling will make the entity effectively invisible.
					.sized(6.0F, 6.0F)
					.clientTrackingRange(64)
					.updateInterval(1)
					.build("fallen_oak_tree")
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

	public static final DeferredHolder<EntityType<?>, EntityType<MinecartStationEntity>> MINECART_STATION = ENTITY_TYPES.register(
			"minecart_station",
			() -> EntityType.Builder.<MinecartStationEntity>of(MinecartStationEntity::new, MobCategory.MISC)
					.sized(0.98F, 0.7F)
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
		event.put(DUCK.get(), BaseCoopAnimalEntity.createAttributes().build());
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
