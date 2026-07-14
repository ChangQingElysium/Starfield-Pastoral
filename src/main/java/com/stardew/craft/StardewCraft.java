package com.stardew.craft;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.capability.UtilityAutomationCapabilities;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.fluid.ModFluids;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.catalog.StardewCatalogTab;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.network.PacketHandler;
import com.stardew.craft.event.WildTreeChopEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModContainer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(StardewCraft.MODID)
public class StardewCraft {
    // Define mod id in a common place for everything to reference
    public static final String MODID = "stardewcraft";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();

    /** pregen .mca 文件在本次启动中被覆盖（版本升级），需要在 level 加载后 reset 管理器。 */
    public static boolean pregenJustInstalled = false;
    
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "stardewcraft" namespace
    @SuppressWarnings("null")
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // Stardew Valley catalog tabs. Keep stardew_tab as the first, farming-oriented tab for compatibility.
    public static final java.util.Map<StardewCatalogTab, DeferredHolder<CreativeModeTab, CreativeModeTab>> STARDEW_CATALOG_TABS = registerCatalogTabs();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> STARDEW_TAB =
            STARDEW_CATALOG_TABS.get(StardewCatalogTab.FARMING_FORAGING);

    private static java.util.Map<StardewCatalogTab, DeferredHolder<CreativeModeTab, CreativeModeTab>> registerCatalogTabs() {
        java.util.EnumMap<StardewCatalogTab, DeferredHolder<CreativeModeTab, CreativeModeTab>> tabs =
                new java.util.EnumMap<>(StardewCatalogTab.class);
        StardewCatalogTab[] catalogTabs = StardewCatalogTab.values();
        for (StardewCatalogTab tab : catalogTabs) {
            tabs.put(tab, CREATIVE_MODE_TABS.register(tab.registryName(), () -> {
                CreativeModeTab.Builder builder = CreativeModeTab.builder()
                        .title(Component.translatable(tab.translationKey()))
                        .icon(() -> tab.iconItem().getDefaultInstance())
                        .displayItems((parameters, output) -> StardewItemCatalog.acceptTab(tab, output));
                return builder.build();
            }));
        }
        return java.util.Collections.unmodifiableMap(tabs);
    }

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public StardewCraft(IEventBus modEventBus, ModContainer modContainer) {
        com.stardew.craft.api.v1.internal.BuiltinApiTypes.bootstrap();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(PacketHandler::register);
        modEventBus.addListener(UtilityAutomationCapabilities::registerCapabilities);
        modEventBus.addListener(ModEntities::onEntityAttributeCreation);
        modEventBus.addListener(com.stardew.craft.data.StardewDataMaps::registerDataMaps);

        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        
        // 注册作物系统的物品和方块
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModFluids.FLUID_TYPES.register(modEventBus);
        ModFluids.FLUIDS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
		ModEntities.ENTITY_TYPES.register(modEventBus);
		ModSounds.SOUND_EVENTS.register(modEventBus);

		// 注册自定义 Buff（状态效果）
		ModMobEffects.MOB_EFFECTS.register(modEventBus);
		
		// 注册自定义粒子
		com.stardew.craft.weather.ModParticles.PARTICLES.register(modEventBus);
		
		// 注册菜单类型
		com.stardew.craft.menu.ModMenuTypes.MENU_TYPES.register(modEventBus);

		// 注册自定义 GameRules（睡眠比例、AFK 超时等）
		com.stardew.craft.core.ModGameRules.init();


        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (StardewCraft) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);
        
        // 手动注册事件（确保事件被正确注册）
        NeoForge.EVENT_BUS.register(com.stardew.craft.event.MinePickaxeEvents.class);
        NeoForge.EVENT_BUS.register(WildTreeChopEvents.class);
        NeoForge.EVENT_BUS.register(com.stardew.craft.event.ResourceClumpEvents.class);
        // WeaponCombatEvents 已有 @EventBusSubscriber 自动注册，不需要手动注册

        if (FMLEnvironment.dist == Dist.CLIENT) {
            NeoForge.EVENT_BUS.register(com.stardew.craft.client.ModClientEvents.class);
            if (ModList.get().isLoaded("appleskin")) {
                NeoForge.EVENT_BUS.register(com.stardew.craft.compat.AppleSkinCompat.class);
            }
        }

        // Register our mod's ModConfigSpecs so that FML can create and load the config files for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);

        // Sync max stack size from config → StackSizeHolder whenever config loads/reloads
        modContainer.getEventBus().addListener((net.neoforged.fml.event.config.ModConfigEvent event) -> {
            if (event.getConfig().getSpec() == Config.SPEC) {
                com.stardew.craft.config.StackSizeHolder.set(Config.MAX_STACK_SIZE.get());
                LOGGER.info("Max stack size set to {}", com.stardew.craft.config.StackSizeHolder.get());
            }
        });
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("Initializing StardewCraft systems...");
        
        // 初始化维度和作物系统
        event.enqueueWork(() -> {
            com.stardew.craft.core.ModDimensions.register();

            // 注入晕倒/死亡系统
            com.stardew.craft.player.StardewDamageHooks.setKnockoutHandler(
                    com.stardew.craft.player.PassOutService::onCombatDeath);
        });

        // Curios 可选兼容：如果安装了 Curios，注册戒指/靴子到 Curios 槽位
        event.enqueueWork(() -> {
            if (com.stardew.craft.compat.CuriosCompatBridge.isCuriosLoaded()) {
                LOGGER.info("[Curios] Curios detected, registering ring/boots items as curio-compatible");
                com.stardew.craft.item.ModItems.ITEMS.getEntries().forEach(entry -> {
                    net.minecraft.world.item.Item item = entry.get();
                    if (item instanceof com.stardew.craft.item.equipment.StardewRingItem
                            || item instanceof com.stardew.craft.item.equipment.StardewBootsItem) {
                        com.stardew.craft.compat.CuriosCompatBridge.registerItem(item);
                    }
                });
            }
        });
    }

    /**
     * 在 createLevels() 之前安装预烘焙区域文件。
     * ServerAboutToStartEvent 在 initServer() 早期触发，早于 LevelEvent.Load，
     * 确保 .mca 文件在维度首次加载前就位。
     */
    @SuppressWarnings("null")
    @SubscribeEvent
    public void onServerAboutToStart(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event) {
        var server = event.getServer();
        LOGGER.info("[VALLEY_MAP] Startup: trying prebuilt region install (ServerAboutToStart)");
        var result = com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller.installIfAvailable(server);
        if (result.installedOrUpgraded()) {
            pregenJustInstalled = true;
            LOGGER.info("[VALLEY_MAP] Prebuilt regions {}. Will reset managers on level load.", result);
        } else if (result == com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller.InstallResult.ALREADY_PRESENT) {
            LOGGER.info("[VALLEY_MAP] Prebuilt regions ready (ALREADY_PRESENT).");
        } else {
            LOGGER.error("[VALLEY_MAP] Prebuilt region package missing or invalid. Stardew Valley travel will be blocked until fixed.");
        }
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SuppressWarnings("null")
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");

        // 加载任务数据
        com.stardew.craft.quest.QuestDataLoader.load();

        // 注册温室季节豁免规则
        com.stardew.craft.greenhouse.GreenhouseManager.registerSeasonRule();

        // 注册祝尼魔温室符文季节豁免规则
        com.stardew.craft.manager.JunimoGreenhouseRuneManager.registerSeasonRule();

        // 注册跨季宽限期季节豁免规则
        com.stardew.craft.farming.SeasonLocationRules.registerGracePeriodRule();

        var server = event.getServer();
        // markAsPreGenerated 需要在 level 可用之后执行
        if (com.stardew.craft.dimension.StardewValleyPrebuiltRegionInstaller.hasInstalledPrebuilt(server)) {
            var stardewLevel = server.getLevel(com.stardew.craft.core.ModDimensions.STARDEW_VALLEY);
            if (stardewLevel != null) {
                com.stardew.craft.dimension.StardewValleyMapBootstrap.markAsPreGenerated(stardewLevel);

                // pregen version 刚升级 → .mca 已在 ServerAboutToStart 被覆盖。
                // 此时 level 已加载，执行所有管理器 reset，确保 ensurePlaced 会重新放置。
                if (pregenJustInstalled) {
                    pregenJustInstalled = false;
                    com.stardew.craft.dimension.StardewBiomePatcher.schedulePregenBiomeMigration(
                        stardewLevel, "pregen_region_reinstalled_startup");
                    com.stardew.craft.interior.InteriorSubspaceManager.replaceAllPortalsIfReady(
                        stardewLevel, "pregen_region_reinstalled_startup");
                    LOGGER.info("[VALLEY_PREGEN] Pregen just installed — portal replacement done (startup path)");
                }

                // 每次服务器启动都强制重置农场入口/采石场/下水道/矿车站点的放置版本号。
                // 这样首个玩家进入星露谷时 ensurePlaced() 一定会重新放置所有方块，
                // 避免老存档 / 模组更新后方块丢失但 SavedData 版本号仍为最新导致跳过。
                // 各 manager 内部是幂等的 setBlock 操作，重复放置不会有副作用。
                com.stardew.craft.farm.FarmEntryBarrierManager.get(stardewLevel).resetForMigration();
                com.stardew.craft.communitycenter.quarry.QuarryAccessManager.get(stardewLevel).resetForMigration();
                com.stardew.craft.sewer.SewerAccessManager.get(stardewLevel).resetForMigration();
                com.stardew.craft.minecart.MinecartStationManager.get(stardewLevel).resetForMigration();
                com.stardew.craft.manager.QuarrySpawnService.resetInitialSpawn(stardewLevel);
                com.stardew.craft.mastery.MasterySiteInstaller.get(stardewLevel).resetForMigration();
                com.stardew.craft.statue.UncertaintyStatueInstaller.get(stardewLevel).resetForMigration();
                com.stardew.craft.specialorder.SpecialOrderBoardInstaller.get(stardewLevel).resetForMigration();
                LOGGER.info("[VALLEY_INIT] Reset all manager SavedData versions — ensurePlaced will re-run on first player entry");
            } else {
                LOGGER.info("[VALLEY_MAP] Stardew level not loaded at startup, will mark pre-generated on first travel.");
            }
        }
    }
}
