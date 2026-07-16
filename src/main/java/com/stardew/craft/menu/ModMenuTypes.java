package com.stardew.craft.menu;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 菜单类型注册
 */
public class ModMenuTypes {
    
    @SuppressWarnings("null")
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = 
        DeferredRegister.create(Registries.MENU, StardewCraft.MODID);

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<StardewGameMenu>> STARDEW_GAME_MENU =
        MENU_TYPES.register("stardew_game_menu",
            () -> new MenuType<>(StardewGameMenu::new, FeatureFlags.DEFAULT_FLAGS));
    
    // 矿井出口菜单（简单的无额外数据Menu）
    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<MineExitMenu>> MINE_EXIT = 
        MENU_TYPES.register("mine_exit", 
            () -> new MenuType<>(MineExitMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // 矿井电梯菜单
    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<ElevatorMenu>> ELEVATOR =
        MENU_TYPES.register("elevator", 
            () -> new MenuType<>(ElevatorMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // 鸡舍管理器菜单
    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<CoopManagerMenu>> COOP_MANAGER =
        MENU_TYPES.register("coop_manager",
            () -> new MenuType<>(CoopManagerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // 畜棚管理器菜单
    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<BarnManagerMenu>> BARN_MANAGER =
        MENU_TYPES.register("barn_manager",
            () -> new MenuType<>(BarnManagerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // 筒仓管理器菜单
    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<SiloManagerMenu>> SILO_MANAGER =
        MENU_TYPES.register("silo_manager",
            () -> new MenuType<>(SiloManagerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<FishPondManagerMenu>> FISH_POND_MANAGER =
        MENU_TYPES.register("fish_pond_manager",
            () -> new MenuType<>(FishPondManagerMenu::new, FeatureFlags.DEFAULT_FLAGS));

    // 动物信息面板菜单
    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<AnimalQueryMenu>> ANIMAL_QUERY =
        MENU_TYPES.register("animal_query",
            () -> new MenuType<>(AnimalQueryMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<com.stardew.craft.fishing.TreasureChestMenu>> TREASURE_CHEST =
        MENU_TYPES.register("treasure_chest",
            () -> new MenuType<>(com.stardew.craft.fishing.TreasureChestMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<CookingPotMenu>> COOKING_POT =
        MENU_TYPES.register("cooking_pot",
            () -> new MenuType<>(CookingPotMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<MiniForgeMenu>> MINI_FORGE =
        MENU_TYPES.register("mini_forge",
            () -> new MenuType<>(MiniForgeMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<WoodenChestMenu>> WOODEN_CHEST =
        MENU_TYPES.register("wooden_chest",
            () -> new MenuType<>(WoodenChestMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<StoneChestMenu>> STONE_CHEST =
        MENU_TYPES.register("stone_chest",
            () -> new MenuType<>(StoneChestMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<ShippingBinMenu>> SHIPPING_BIN =
        MENU_TYPES.register("shipping_bin",
            () -> new MenuType<>(ShippingBinMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<SpecialOrderDropBoxMenu>> SPECIAL_ORDER_DROPBOX =
        MENU_TYPES.register("special_order_dropbox",
            () -> new MenuType<>(SpecialOrderDropBoxMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<FairGrangeDisplayMenu>> FAIR_GRANGE_DISPLAY =
        MENU_TYPES.register("fair_grange_display",
            () -> new MenuType<>(FairGrangeDisplayMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<com.stardew.craft.communitycenter.menu.BundleMenu>> BUNDLE =
        MENU_TYPES.register("bundle",
            () -> new MenuType<>(com.stardew.craft.communitycenter.menu.BundleMenu::new, FeatureFlags.DEFAULT_FLAGS));

    @SuppressWarnings("null")
    public static final DeferredHolder<MenuType<?>, MenuType<com.stardew.craft.communitycenter.menu.BundleRewardMenu>> BUNDLE_REWARD =
        MENU_TYPES.register("bundle_reward",
            () -> new MenuType<>(com.stardew.craft.communitycenter.menu.BundleRewardMenu::new, FeatureFlags.DEFAULT_FLAGS));
}
