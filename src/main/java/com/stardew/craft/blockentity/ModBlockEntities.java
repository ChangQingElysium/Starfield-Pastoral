package com.stardew.craft.blockentity;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
	private ModBlockEntities() {
	}

	@SuppressWarnings("null")
	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
			DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, StardewCraft.MODID);

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TapperBlockEntity>> TAPPER =
			BLOCK_ENTITIES.register("tapper", () -> BlockEntityType.Builder.of(TapperBlockEntity::new, ModBlocks.TAPPER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WildWeedsBlockEntity>> WILD_WEEDS =
			BLOCK_ENTITIES.register("wild_weeds", () -> BlockEntityType.Builder.of(WildWeedsBlockEntity::new, ModBlocks.WILD_WEEDS.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FruitTreeBlockEntity>> FRUIT_TREE =
			BLOCK_ENTITIES.register("fruit_tree", () -> BlockEntityType.Builder.of(FruitTreeBlockEntity::new,
					ModBlocks.CHERRY_TREE.get(),
					ModBlocks.APRICOT_TREE.get(),
					ModBlocks.ORANGE_TREE.get(),
					ModBlocks.PEACH_TREE.get(),
					ModBlocks.POMEGRANATE_TREE.get(),
					ModBlocks.APPLE_TREE.get(),
					ModBlocks.BANANA_TREE.get(),
					ModBlocks.MANGO_TREE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NewTreePartBlockEntity>> NEW_TREE_PART =
			BLOCK_ENTITIES.register("new_tree_part", () -> BlockEntityType.Builder.of(NewTreePartBlockEntity::new,
					ModBlocks.OAK_ROOT.get(), ModBlocks.OAK_LOG.get(), ModBlocks.OAK_BRANCH.get(),
					ModBlocks.MAPLE_ROOT.get(), ModBlocks.MAPLE_LOG.get(), ModBlocks.MAPLE_BRANCH.get(),
					ModBlocks.PINE_ROOT.get(), ModBlocks.PINE_LOG.get(), ModBlocks.PINE_BRANCH.get(),
					ModBlocks.MAHOGANY_ROOT.get(), ModBlocks.MAHOGANY_LOG.get(), ModBlocks.MAHOGANY_BRANCH.get(),
					ModBlocks.MYSTIC_TREE_ROOT.get(), ModBlocks.MYSTIC_TREE_LOG.get(), ModBlocks.MYSTIC_TREE_BRANCH.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MushroomBoxBlockEntity>> MUSHROOM_BOX =
			BLOCK_ENTITIES.register("mushroom_box", () -> BlockEntityType.Builder.of(MushroomBoxBlockEntity::new, ModBlocks.MUSHROOM_BOX.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<KegBlockEntity>> KEG =
			BLOCK_ENTITIES.register("keg", () -> BlockEntityType.Builder.of(KegBlockEntity::new, ModBlocks.KEG.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PreservesJarBlockEntity>> PRESERVES_JAR =
			BLOCK_ENTITIES.register("preserves_jar", () -> BlockEntityType.Builder.of(PreservesJarBlockEntity::new, ModBlocks.PRESERVES_JAR.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DehydratorBlockEntity>> DEHYDRATOR =
			BLOCK_ENTITIES.register("dehydrator", () -> BlockEntityType.Builder.of(DehydratorBlockEntity::new, ModBlocks.DEHYDRATOR.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BaitMakerBlockEntity>> BAIT_MAKER =
			BLOCK_ENTITIES.register("bait_maker", () -> BlockEntityType.Builder.of(BaitMakerBlockEntity::new, ModBlocks.BAIT_MAKER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishSmokerBlockEntity>> FISH_SMOKER =
			BLOCK_ENTITIES.register("fish_smoker", () -> BlockEntityType.Builder.of(FishSmokerBlockEntity::new, ModBlocks.FISH_SMOKER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RecyclingMachineBlockEntity>> RECYCLING_MACHINE =
			BLOCK_ENTITIES.register("recycling_machine", () -> BlockEntityType.Builder.of(RecyclingMachineBlockEntity::new, ModBlocks.RECYCLING_MACHINE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrystalariumBlockEntity>> CRYSTALARIUM =
			BLOCK_ENTITIES.register("crystalarium", () -> BlockEntityType.Builder.of(CrystalariumBlockEntity::new, ModBlocks.CRYSTALARIUM.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeedMakerBlockEntity>> SEED_MAKER =
			BLOCK_ENTITIES.register("seed_maker", () -> BlockEntityType.Builder.of(SeedMakerBlockEntity::new, ModBlocks.SEED_MAKER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FurnaceBlockEntity>> FURNACE =
			BLOCK_ENTITIES.register("furnace", () -> BlockEntityType.Builder.of(FurnaceBlockEntity::new, ModBlocks.FURNACE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeavyFurnaceBlockEntity>> HEAVY_FURNACE =
			BLOCK_ENTITIES.register("heavy_furnace", () -> BlockEntityType.Builder.of(HeavyFurnaceBlockEntity::new, ModBlocks.HEAVY_FURNACE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnvilBlockEntity>> ANVIL =
			BLOCK_ENTITIES.register("anvil_mastery", () -> BlockEntityType.Builder.of(AnvilBlockEntity::new, ModBlocks.ANVIL_MASTERY.get()).build(null));

	public static BlockEntityType<AnvilBlockEntity> anvil() {
		return ANVIL.get();
	}

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CharcoalKilnBlockEntity>> CHARCOAL_KILN =
			BLOCK_ENTITIES.register("charcoal_kiln", () -> BlockEntityType.Builder.of(CharcoalKilnBlockEntity::new, ModBlocks.CHARCOAL_KILN.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LightningRodBlockEntity>> LIGHTNING_ROD =
			BLOCK_ENTITIES.register("lightning_rod", () -> BlockEntityType.Builder.of(LightningRodBlockEntity::new, ModBlocks.LIGHTNING_ROD.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
			BLOCK_ENTITIES.register("solar_panel", () -> BlockEntityType.Builder.of(SolarPanelBlockEntity::new, ModBlocks.SOLAR_PANEL.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeodeCrusherBlockEntity>> GEODE_CRUSHER =
			BLOCK_ENTITIES.register("geode_crusher", () -> BlockEntityType.Builder.of(GeodeCrusherBlockEntity::new, ModBlocks.GEODE_CRUSHER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoffeeMakerBlockEntity>> COFFEE_MAKER =
			BLOCK_ENTITIES.register("coffee_maker", () -> BlockEntityType.Builder.of(CoffeeMakerBlockEntity::new, ModBlocks.COFFEE_MAKER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DailyStatueBlockEntity>> DAILY_STATUE =
			BLOCK_ENTITIES.register("daily_statue", () -> BlockEntityType.Builder.of(
					DailyStatueBlockEntity::new,
					ModBlocks.STATUE_OF_ENDLESS_FORTUNE.get(),
					ModBlocks.STATUE_OF_PERFECTION.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoneMillBlockEntity>> BONE_MILL =
			BLOCK_ENTITIES.register("bone_mill", () -> BlockEntityType.Builder.of(BoneMillBlockEntity::new, ModBlocks.BONE_MILL.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CaskBlockEntity>> CASK =
			BLOCK_ENTITIES.register("cask", () -> BlockEntityType.Builder.of(CaskBlockEntity::new, ModBlocks.CASK.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CheesePressBlockEntity>> CHEESE_PRESS =
			BLOCK_ENTITIES.register("cheese_press", () -> BlockEntityType.Builder.of(CheesePressBlockEntity::new, ModBlocks.CHEESE_PRESS.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MayonnaiseMachineBlockEntity>> MAYONNAISE_MACHINE =
			BLOCK_ENTITIES.register("mayonnaise_machine", () -> BlockEntityType.Builder.of(MayonnaiseMachineBlockEntity::new, ModBlocks.MAYONNAISE_MACHINE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IncubatorBlockEntity>> INCUBATOR =
			BLOCK_ENTITIES.register("incubator", () -> BlockEntityType.Builder.of(IncubatorBlockEntity::new, ModBlocks.INCUBATOR.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OilMakerBlockEntity>> OIL_MAKER =
			BLOCK_ENTITIES.register("oil_maker", () -> BlockEntityType.Builder.of(OilMakerBlockEntity::new, ModBlocks.OIL_MAKER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LoomBlockEntity>> LOOM =
			BLOCK_ENTITIES.register("loom", () -> BlockEntityType.Builder.of(LoomBlockEntity::new, ModBlocks.LOOM.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BeeHouseBlockEntity>> BEE_HOUSE =
			BLOCK_ENTITIES.register("bee_house", () -> BlockEntityType.Builder.of(BeeHouseBlockEntity::new, ModBlocks.BEE_HOUSE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrabPotBlockEntity>> CRAB_POT =
			BLOCK_ENTITIES.register("crab_pot", () -> BlockEntityType.Builder.of(CrabPotBlockEntity::new, ModBlocks.CRAB_POT.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WaterLanternBlockEntity>> WATER_LANTERN =
			BLOCK_ENTITIES.register("water_lantern", () -> BlockEntityType.Builder.of(WaterLanternBlockEntity::new, ModBlocks.WATER_LANTERN.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MuseumExhibitStandBlockEntity>> MUSEUM_EXHIBIT_STAND =
			BLOCK_ENTITIES.register("museum_exhibit_stand", () -> BlockEntityType.Builder.of(MuseumExhibitStandBlockEntity::new, ModBlocks.MUSEUM_EXHIBIT_STAND.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WormBinBlockEntity>> WORM_BIN =
			BLOCK_ENTITIES.register("worm_bin", () -> BlockEntityType.Builder.of(WormBinBlockEntity::new, ModBlocks.WORM_BIN.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FeedTroughBlockEntity>> FEED_TROUGH =
			BLOCK_ENTITIES.register("feed_trough", () -> BlockEntityType.Builder.of(FeedTroughBlockEntity::new, ModBlocks.FEED_TROUGH.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoFeedTroughBlockEntity>> AUTOFEED_TROUGH =
			BLOCK_ENTITIES.register("autofeed_trough", () -> BlockEntityType.Builder.of(AutoFeedTroughBlockEntity::new, ModBlocks.AUTOFEED_TROUGH.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoGrabberBlockEntity>> AUTO_GRABBER =
			BLOCK_ENTITIES.register("auto_grabber", () -> BlockEntityType.Builder.of(AutoGrabberBlockEntity::new, ModBlocks.AUTO_GRABBER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoPetterBlockEntity>> AUTO_PETTER =
			BLOCK_ENTITIES.register("auto_petter", () -> BlockEntityType.Builder.of(AutoPetterBlockEntity::new, ModBlocks.AUTO_PETTER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WoodenChestBlockEntity>> WOODEN_CHEST =
			BLOCK_ENTITIES.register("wooden_chest", () -> BlockEntityType.Builder.of(WoodenChestBlockEntity::new, ModBlocks.WOODEN_CHEST.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StoneChestBlockEntity>> STONE_CHEST =
			BLOCK_ENTITIES.register("stone_chest", () -> BlockEntityType.Builder.of(StoneChestBlockEntity::new, ModBlocks.STONE_CHEST.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FridgeBlockEntity>> FRIDGE =
			BLOCK_ENTITIES.register("fridge", () -> BlockEntityType.Builder.of(FridgeBlockEntity::new, ModBlocks.FRIDGE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FurnitureStorageBlockEntity>> FURNITURE_STORAGE =
			BLOCK_ENTITIES.register("furniture_storage", () -> BlockEntityType.Builder.of(FurnitureStorageBlockEntity::new,
					ModBlocks.DRESSER_1.get(),
					ModBlocks.DRESSER_2.get(),
					ModBlocks.OAK_BEDSIDE_CABINET.get(),
					ModBlocks.OAK_DRESSER.get(),
					ModBlocks.REDWOOD_BEDSIDE_CABINET.get(),
					ModBlocks.REDWOOD_DRESSER.get(),
					ModBlocks.WALNUT_BEDSIDE_CABINET.get(),
					ModBlocks.WALNUT_DRESSER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WardrobeBlockEntity>> WARDROBE =
			BLOCK_ENTITIES.register("wardrobe", () -> BlockEntityType.Builder.of(WardrobeBlockEntity::new,
					ModBlocks.DRESSER_3.get(),
					ModBlocks.REDWOOD_WARDROBE.get(),
					ModBlocks.WALNUT_WARDROBE.get(),
					ModBlocks.OAK_WARDROBE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShippingBinBlockEntity>> SHIPPING_BIN =
			BLOCK_ENTITIES.register("shipping_bin", () -> BlockEntityType.Builder.of(ShippingBinBlockEntity::new, ModBlocks.SHIPPING_BIN.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrashBinBlockEntity>> TRASH_BIN =
			BLOCK_ENTITIES.register("trash_bin", () -> BlockEntityType.Builder.of(TrashBinBlockEntity::new, ModBlocks.TRASH_BIN.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeaterBlockEntity>> HEATER =
			BLOCK_ENTITIES.register("heater", () -> BlockEntityType.Builder.of(HeaterBlockEntity::new, ModBlocks.HEATER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HayHopperBlockEntity>> HAY_HOPPER =
			BLOCK_ENTITIES.register("hay_hopper", () -> BlockEntityType.Builder.of(HayHopperBlockEntity::new, ModBlocks.HAY_HOPPER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FriendshipDoorBlockEntity>> FRIENDSHIP_DOOR =
			BLOCK_ENTITIES.register("friendship_door", () -> BlockEntityType.Builder.of(FriendshipDoorBlockEntity::new, ModBlocks.FRIENDSHIP_DOOR.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeluxeWormBinBlockEntity>> DELUXE_WORM_BIN =
			BLOCK_ENTITIES.register("deluxe_worm_bin", () -> BlockEntityType.Builder.of(DeluxeWormBinBlockEntity::new, ModBlocks.DELUXE_WORM_BIN.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnimalProduceSpotBlockEntity>> ANIMAL_PRODUCE_SPOT =
			BLOCK_ENTITIES.register("animal_produce_spot", () -> BlockEntityType.Builder.of(AnimalProduceSpotBlockEntity::new, ModBlocks.ANIMAL_PRODUCE_SPOT.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EggFestivalEggBlockEntity>> EGG_FESTIVAL_EGG =
			BLOCK_ENTITIES.register("egg_festival_egg", () -> BlockEntityType.Builder.of(EggFestivalEggBlockEntity::new, ModBlocks.EGG_FESTIVAL_EGG.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LuckyPurpleShortsBlockEntity>> LUCKY_PURPLE_SHORTS =
			BLOCK_ENTITIES.register("lucky_purple_shorts", () -> BlockEntityType.Builder.of(LuckyPurpleShortsBlockEntity::new, ModBlocks.LUCKY_PURPLE_SHORTS.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlowerDanceDecorBlockEntity>> FLOWER_DANCE_DECOR =
			BLOCK_ENTITIES.register("flower_dance_decor", () -> BlockEntityType.Builder.of(FlowerDanceDecorBlockEntity::new,
					ModBlocks.FLOWER_CLUSTER.get(), ModBlocks.SEASONAL_DECOR.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LuauFestivalDecorBlockEntity>> LUAU_FESTIVAL_DECOR =
			BLOCK_ENTITIES.register("luau_festival_decor", () -> BlockEntityType.Builder.of(LuauFestivalDecorBlockEntity::new,
					ModBlocks.LUAU_SOUP_POT.get(), ModBlocks.LUAU_TOTEM.get(), ModBlocks.WINTER_STAR_TREE.get(),
					ModBlocks.SQUID_FEST_PROMO_POSTER.get(), ModBlocks.SQUID_FEST_REQUIREMENT_POSTER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FairStrengthTesterBlockEntity>> FAIR_STRENGTH_TESTER =
			BLOCK_ENTITIES.register("fair_strength_tester", () -> BlockEntityType.Builder.of(FairStrengthTesterBlockEntity::new,
					ModBlocks.FAIR_STRENGTH_TESTER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DecorBlockEntity>> DECOR_BLOCK =
			BLOCK_ENTITIES.register("decor_block", () -> BlockEntityType.Builder.of(DecorBlockEntity::new, ModBlocks.WALLPAPER_BLOCK.get(), ModBlocks.FLOORING_BLOCK.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LargeFireplaceBlockEntity>> LARGE_FIREPLACE =
			BLOCK_ENTITIES.register("large_fireplace", () -> BlockEntityType.Builder.of(LargeFireplaceBlockEntity::new, ModBlocks.FIREPLACE_LARGE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShrineBlockEntity>> SHRINE =
			BLOCK_ENTITIES.register("shrine", () -> BlockEntityType.Builder.of(ShrineBlockEntity::new, ModBlocks.SHRINE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScarecrowBlockEntity>> SCARECROW =
			BLOCK_ENTITIES.register("scarecrow", () -> BlockEntityType.Builder.of(ScarecrowBlockEntity::new,
					ModBlocks.SCARECROW_0.get(), ModBlocks.SCARECROW_1.get(), ModBlocks.SCARECROW_2.get(),
					ModBlocks.SCARECROW_3.get(), ModBlocks.SCARECROW_4.get(), ModBlocks.SCARECROW_5.get(),
					ModBlocks.SCARECROW_6.get(), ModBlocks.SCARECROW_7.get(), ModBlocks.SCARECROW_8.get(),
					ModBlocks.SCARECROW_9.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JunimoHutDecorBlockEntity>> JUNIMO_HUT_DECOR =
			BLOCK_ENTITIES.register("junimo_hut_decor", () -> BlockEntityType.Builder.of(JunimoHutDecorBlockEntity::new, ModBlocks.JUNIMO_HUT_DECOR.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WizardBuildingBlockEntity>> WIZARD_BUILDING =
			BLOCK_ENTITIES.register("wizard_building", () -> BlockEntityType.Builder.of(WizardBuildingBlockEntity::new,
					ModBlocks.JUNIMO_HUT.get(), ModBlocks.EARTH_OBELISK.get(), ModBlocks.WATER_OBELISK.get(),
					ModBlocks.DESERT_OBELISK.get(), ModBlocks.ISLAND_OBELISK.get(), ModBlocks.GOLD_CLOCK.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardew.craft.blockentity.GiantCropBlockEntity>> GIANT_CROP =
			BLOCK_ENTITIES.register("giant_crop", () -> BlockEntityType.Builder.of(
					com.stardew.craft.blockentity.GiantCropBlockEntity::new,
					ModBlocks.GIANT_CAULIFLOWER.get(),
					ModBlocks.GIANT_MELON.get(),
					ModBlocks.GIANT_PUMPKIN.get(),
					ModBlocks.GIANT_POWDERMELON.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BookshelfGeoBlockEntity>> BOOKSHELF_GEO =
			BLOCK_ENTITIES.register("bookshelf_geo", () -> BlockEntityType.Builder.of(BookshelfGeoBlockEntity::new,
				ModBlocks.BOOKSHELF_TALL_1.get(), ModBlocks.BOOKSHELF_TALL_2.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PillarGeoBlockEntity>> PILLAR_GEO =
			BLOCK_ENTITIES.register("pillar_geo", () -> BlockEntityType.Builder.of(PillarGeoBlockEntity::new,
				ModBlocks.PILLAR.get(), ModBlocks.GALAXY_PILLAR.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BushBlockEntity>> BUSH =
			BLOCK_ENTITIES.register("bush", () -> BlockEntityType.Builder.of(BushBlockEntity::new,
				ModBlocks.SMALL_BUSH.get(), ModBlocks.BERRY_BUSH.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishNetBlockEntity>> FISH_NET =
			BLOCK_ENTITIES.register("fish_net", () -> BlockEntityType.Builder.of(FishNetBlockEntity::new,
				ModBlocks.FISH_NET.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishPondBucketBlockEntity>> FISH_POND_BUCKET =
			BLOCK_ENTITIES.register("fish_pond_bucket", () -> BlockEntityType.Builder.of(FishPondBucketBlockEntity::new,
				ModBlocks.FISH_POND_BUCKET.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TableDisplayBlockEntity>> TABLE_DISPLAY =
			BLOCK_ENTITIES.register("table_display", () -> BlockEntityType.Builder.of(TableDisplayBlockEntity::new,
				ModBlocks.OAK_TABLE.get(), ModBlocks.SPRUCE_TABLE.get(), ModBlocks.BIRCH_TABLE.get(), ModBlocks.SPRUCE_COUNTER.get(), ModBlocks.OAK_ROUND_TABLE.get(), ModBlocks.KITCHEN_COUNTER.get(), ModBlocks.HOSPITAL_COUNTER.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CookingPlacedFoodBlockEntity>> PLACED_COOKING_FOOD =
			BLOCK_ENTITIES.register("placed_cooking_food", () -> BlockEntityType.Builder.of(
					CookingPlacedFoodBlockEntity::new,
					ModBlocks.PLACED_COOKING_FOODS.values().stream()
							.map(net.neoforged.neoforge.registries.DeferredBlock::get)
							.toArray(net.minecraft.world.level.block.Block[]::new)).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OfficeStoolBlockEntity>> OFFICE_STOOL =
			BLOCK_ENTITIES.register("office_stool", () -> BlockEntityType.Builder.of(OfficeStoolBlockEntity::new, ModBlocks.OFFICE_STOOL.get(), ModBlocks.OFFICE_CHAIR_2.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GrandfatherClockBlockEntity>> GRANDFATHER_CLOCK =
			BLOCK_ENTITIES.register("grandfather_clock", () -> BlockEntityType.Builder.of(GrandfatherClockBlockEntity::new,
				ModBlocks.GRANDFATHER_CLOCK.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TotemPoleBlockEntity>> TOTEM_POLE =
			BLOCK_ENTITIES.register("totem_pole", () -> BlockEntityType.Builder.of(TotemPoleBlockEntity::new,
				ModBlocks.TOTEM_POLE_FARM.get(), ModBlocks.TOTEM_POLE_MOUNTAIN.get(), ModBlocks.TOTEM_POLE_BEACH.get(), ModBlocks.TOTEM_POLE_DESERT.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MailboxBlockEntity>> MAILBOX =
			BLOCK_ENTITIES.register("mailbox", () -> BlockEntityType.Builder.of(MailboxBlockEntity::new,
				ModBlocks.MAILBOX.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MasteryStatueBlockEntity>> MASTERY_STATUE =
			BLOCK_ENTITIES.register("mastery_statue", () -> BlockEntityType.Builder.of(MasteryStatueBlockEntity::new,
				ModBlocks.STATUE_OF_BLESSINGS.get(), ModBlocks.STATUE_OF_DWARF_KING.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<UncertaintyStatueBlockEntity>> UNCERTAINTY_STATUE =
			BLOCK_ENTITIES.register("uncertainty_statue", () -> BlockEntityType.Builder.of(UncertaintyStatueBlockEntity::new,
				ModBlocks.UNCERTAINTY_STATUE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WizardBuildingCatalogBlockEntity>> WIZARD_BUILDING_CATALOG =
			BLOCK_ENTITIES.register("wizard_building_catalog", () -> BlockEntityType.Builder.of(
				WizardBuildingCatalogBlockEntity::new, ModBlocks.WIZARD_BUILDING_CATALOG.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MineChestBlockEntity>> MINE_CHEST =
			BLOCK_ENTITIES.register("mine_chest", () -> BlockEntityType.Builder.of(MineChestBlockEntity::new, ModBlocks.MINE_CHEST.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.stardew.craft.communitycenter.block.StarPlaqueBlockEntity>> STAR_PLAQUE =
			BLOCK_ENTITIES.register("star_plaque", () -> BlockEntityType.Builder.of(
				com.stardew.craft.communitycenter.block.StarPlaqueBlockEntity::new,
				ModBlocks.STAR_PLAQUE.get()).build(null));

	@SuppressWarnings("null")
	public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PortalTriggerBlockEntity>> PORTAL_TRIGGER =
			BLOCK_ENTITIES.register("portal_trigger", () -> BlockEntityType.Builder.of(
				PortalTriggerBlockEntity::new,
				ModBlocks.PORTAL_TRIGGER.get()).build(null));

	}
