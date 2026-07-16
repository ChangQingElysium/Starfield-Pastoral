package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.fishing.data.SpawnFishRule;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.SecretNoteItem;
import com.stardew.craft.item.SpecificBaitItem;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.client.gui.WorkbenchScreen;
import com.stardew.craft.client.gui.menu.StardewGameMenuScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * JEI 插件 - 钓鱼信息展示
 * 这是可选依赖，当JEI不存在时不会加载
 */
@JeiPlugin
public class StardewJeiPlugin implements IModPlugin {
    public static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "jei_plugin");
    private static volatile IJeiRuntime runtime;
    private static volatile List<FishingInfoCategory.DisplayEntry> publishedFishingRecipes = List.of();
    private static volatile Map<ResourceLocation, List<ArtisanJeiRecipe>> publishedArtisanRecipes = Map.of();
    private static volatile List<StardewCraftingCategory.DisplayRecipe> publishedCraftingRecipes = List.of();
    private static volatile List<CookingRecipeCategory.DisplayRecipe> publishedCookingRecipes = List.of();
    private static volatile List<ShopInfoCategory.DisplayEntry> publishedShopRecipes = List.of();
    private static volatile List<GeodeProcessingCategory.DisplayEntry> publishedGeodeRecipes = List.of();
    private static volatile List<FishPondInfoCategory.DisplayEntry> publishedFishPondRecipes = List.of();

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(@SuppressWarnings("null") IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        JeiPortraitCache.preload(guiHelper, JeiPortraitCache.SHOP_NPC_IDS);
        registration.addRecipeCategories(new FishingInfoCategory(guiHelper));

        // Register a stable category for every supported machine before server data arrives.
        for (MachineJeiRegistry.Machine machine : MachineJeiRegistry.all()) {
            ItemStack machineIcon = ArtisanRecipeCategory.itemStack(machine.itemId().toString());
            if (machineIcon.isEmpty()) {
                StardewCraft.LOGGER.error("Cannot register JEI category for missing machine item {}", machine.itemId());
                continue;
            }
            registration.addRecipeCategories(new ArtisanRecipeCategory(guiHelper, machine, machineIcon));
        }

        registration.addRecipeCategories(new ShopInfoCategory(guiHelper));
        registration.addRecipeCategories(new GeodeProcessingCategory(guiHelper));
        registration.addRecipeCategories(new FishPondInfoCategory(guiHelper));
        registration.addRecipeCategories(new StardewCraftingCategory(guiHelper));
        registration.addRecipeCategories(new CookingRecipeCategory(guiHelper));
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        IIngredientManager ingredientManager = registration.getJeiHelpers().getIngredientManager();

        // The V-menu is a real container now, but only its inventory and crafting tabs
        // should reserve space for JEI. Other tabs use the full screen for non-item UI.
        registration.addGuiScreenHandler(StardewGameMenuScreen.class, screen -> {
            if (!screen.shouldShowJei() || screen.jeiGuiWidth() <= 0 || screen.jeiGuiHeight() <= 0) {
                return null;
            }
            return properties(screen, screen.jeiGuiLeft(), screen.jeiGuiTop(),
                    screen.jeiGuiWidth(), screen.jeiGuiHeight());
        });
        registration.addGuiContainerHandler(StardewGameMenuScreen.class,
                new IGuiContainerHandler<StardewGameMenuScreen>() {
                    @Override
                    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(
                            StardewGameMenuScreen screen, double mouseX, double mouseY) {
                        StardewGameMenuScreen.ClickableItem target = screen.jeiIngredientAt(mouseX, mouseY);
                        return target == null ? Optional.empty() : clickableIngredient(ingredientManager,
                                target.stack(), target.x(), target.y(), target.width(), target.height());
                    }
                });

        // Workbenches intentionally have no inventory slots, so JEI needs an explicit
        // ordinary-Screen bridge as well as clickable output ingredients.
        registration.addGuiScreenHandler(WorkbenchScreen.class, screen -> {
            if (screen.jeiGuiWidth() <= 0 || screen.jeiGuiHeight() <= 0) {
                return null;
            }
            return properties(screen, screen.jeiGuiLeft(), screen.jeiGuiTop(),
                    screen.jeiGuiWidth(), screen.jeiGuiHeight());
        });
        registration.addGlobalGuiHandler(new mezz.jei.api.gui.handlers.IGlobalGuiHandler() {
            @Override
            public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(double mouseX, double mouseY) {
                net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
                if (!(minecraft.screen instanceof WorkbenchScreen screen)) {
                    return Optional.empty();
                }
                WorkbenchScreen.ClickableItem target = screen.jeiIngredientAt(mouseX, mouseY);
                return target == null ? Optional.empty() : clickableIngredient(ingredientManager,
                        target.stack(), target.x(), target.y(), target.width(), target.height());
            }
        });
    }

    private static Optional<IClickableIngredient<?>> clickableIngredient(
            IIngredientManager ingredientManager, ItemStack stack, int x, int y, int width, int height) {
        return ingredientManager.createClickableIngredient(stack, new Rect2i(x, y, width, height), true)
                .map(ingredient -> (IClickableIngredient<?>) ingredient);
    }

    private static IGuiProperties properties(Screen screen, int x, int y, int width, int height) {
        return new ScreenProperties(screen.getClass(), x, y, width, height, screen.width, screen.height);
    }

    private record ScreenProperties(Class<? extends Screen> screenClass, int guiLeft, int guiTop,
                                    int guiXSize, int guiYSize, int screenWidth, int screenHeight)
            implements IGuiProperties {
    }

    @Override
    @SuppressWarnings("null")
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        // Quality and flower colour are presentation data, not recipe identity.
        // Leaving them without subtype interpreters makes every quality share U/R lookups.
        registration.registerSubtypeInterpreter(ModItems.JELLY.get(), new PreserveSubtypeInterpreter());
        registration.registerSubtypeInterpreter(ModItems.PICKLES.get(), new PreserveSubtypeInterpreter());
        registration.registerSubtypeInterpreter(ModItems.ROE.get(), new PreserveSubtypeInterpreter());
        registration.registerSubtypeInterpreter(ModItems.AGED_ROE.get(), new PreserveSubtypeInterpreter());
        registration.registerSubtypeInterpreter(ModItems.DRIED_FRUIT.get(), new PreserveSubtypeInterpreter());
        registration.registerSubtypeInterpreter(ModItems.DRIED_MUSHROOMS.get(), new PreserveSubtypeInterpreter());
        registration.registerSubtypeInterpreter(ModItems.TARGETED_BAIT.get(), new SpecificBaitSubtypeInterpreter());
        registration.registerSubtypeInterpreter(ModItems.SECRET_NOTE.get(), new SecretNoteSubtypeInterpreter());
    }

    @Override
    @SuppressWarnings("null")
    public void registerExtraIngredients(@SuppressWarnings("null") IExtraIngredientRegistration registration) {
        registration.addExtraItemStacks(StardewItemCatalog.jeiExtraIngredientStacks());
    }

    @SuppressWarnings("null")
    @Override
    public void registerRecipes(@SuppressWarnings("null") IRecipeRegistration registration) {
        // Fishing info
        List<FishingInfoCategory.DisplayEntry> fishingRecipes = buildFishingEntries();
        
        if (!fishingRecipes.isEmpty()) {
            registration.addRecipes(FishingInfoCategory.RECIPE_TYPE, fishingRecipes);
            StardewCraft.LOGGER.info("Registered {} one-per-item fishing information pages for JEI",
                    fishingRecipes.size());
        } else {
            StardewCraft.LOGGER.warn("No fishing rules loaded when registering JEI recipes!");
        }
        publishedFishingRecipes = List.copyOf(fishingRecipes);

        // Artisan machine recipes — per machine
        int totalArtisan = 0;
        Map<ResourceLocation, List<ArtisanJeiRecipe>> artisanRecipes = new LinkedHashMap<>();
        for (MachineJeiRegistry.Machine machine : MachineJeiRegistry.all()) {
            List<ArtisanJeiRecipe> recipes = ArtisanJeiRecipeFactory.build(machine);
            artisanRecipes.put(machine.id(), recipes);
            if (!recipes.isEmpty()) {
                registration.addRecipes(machine.recipeType(), recipes);
                totalArtisan += recipes.size();
            }
        }
        if (totalArtisan > 0) {
            StardewCraft.LOGGER.info("Registered {} artisan machine recipes across {} machines for JEI",
                    totalArtisan, MachineJeiRegistry.all().size());
        }
        publishedArtisanRecipes = Map.copyOf(artisanRecipes);

        // Shop info
        var shopEntries = ShopInfoCategory.buildAllEntries();
        if (!shopEntries.isEmpty()) {
            registration.addRecipes(ShopInfoCategory.RECIPE_TYPE, shopEntries);
            StardewCraft.LOGGER.info("Registered {} shop info entries for JEI", shopEntries.size());
        }
        publishedShopRecipes = List.copyOf(shopEntries);

        // Geode processing
        var geodeEntries = GeodeProcessingCategory.buildAllEntries();
        if (!geodeEntries.isEmpty()) {
            registration.addRecipes(GeodeProcessingCategory.RECIPE_TYPE, geodeEntries);
            StardewCraft.LOGGER.info("Registered {} geode processing entries for JEI", geodeEntries.size());
        }
        publishedGeodeRecipes = List.copyOf(geodeEntries);

        // Fish pond products, including source-flavoured roe.
        var fishPondEntries = FishPondInfoCategory.buildAllEntries();
        if (!fishPondEntries.isEmpty()) {
            registration.addRecipes(FishPondInfoCategory.RECIPE_TYPE, fishPondEntries);
            StardewCraft.LOGGER.info("Registered {} fish pond product entries for JEI", fishPondEntries.size());
        }
        publishedFishPondRecipes = List.copyOf(fishPondEntries);

        // Stardew crafting
        var craftingRecipes = StardewCraftingCategory.buildAllRecipes();
        if (!craftingRecipes.isEmpty()) {
            registration.addRecipes(StardewCraftingCategory.RECIPE_TYPE, craftingRecipes);
            StardewCraft.LOGGER.info("Registered {} stardew crafting recipes for JEI", craftingRecipes.size());
        }
        publishedCraftingRecipes = List.copyOf(craftingRecipes);

        // Cooking
        var cookingRecipes = CookingRecipeCategory.buildAllRecipes();
        if (!cookingRecipes.isEmpty()) {
            registration.addRecipes(CookingRecipeCategory.RECIPE_TYPE, cookingRecipes);
            StardewCraft.LOGGER.info("Registered {} cooking recipes for JEI", cookingRecipes.size());
        }
        publishedCookingRecipes = List.copyOf(cookingRecipes);

        // Hide items tagged stardewcraft:hidden
        hideTaggedItems(registration);
        hideUnboundSecretNote(registration);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        refreshSyncedRecipes();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Refreshes the JEI categories backed by registries received in {@code DataRegistrySyncPayload}. */
    public static void refreshSyncedRecipes() {
        IJeiRuntime currentRuntime = runtime;
        if (currentRuntime == null) {
            return;
        }

        IRecipeManager recipeManager = currentRuntime.getRecipeManager();
        List<FishingInfoCategory.DisplayEntry> fishingRecipes = buildFishingEntries();
        refreshRecipeTypeBySignature(recipeManager, FishingInfoCategory.RECIPE_TYPE,
                publishedFishingRecipes, fishingRecipes,
                FishingInfoCategory.DisplayEntry::contentSignature);
        publishedFishingRecipes = List.copyOf(fishingRecipes);

        Map<ResourceLocation, List<ArtisanJeiRecipe>> artisanRecipes = new LinkedHashMap<>();
        for (MachineJeiRegistry.Machine machine : MachineJeiRegistry.all()) {
            List<ArtisanJeiRecipe> recipes = ArtisanJeiRecipeFactory.build(machine);
            artisanRecipes.put(machine.id(), recipes);
            refreshArtisanRecipeType(recipeManager, machine.recipeType(),
                    publishedArtisanRecipes.getOrDefault(machine.id(), List.of()), recipes);
        }
        publishedArtisanRecipes = Map.copyOf(artisanRecipes);

        List<StardewCraftingCategory.DisplayRecipe> craftingRecipes =
                List.copyOf(StardewCraftingCategory.buildAllRecipes());
        refreshRecipeTypeBySignature(recipeManager, StardewCraftingCategory.RECIPE_TYPE,
                publishedCraftingRecipes, craftingRecipes,
                StardewCraftingCategory.DisplayRecipe::contentSignature);
        publishedCraftingRecipes = craftingRecipes;

        List<CookingRecipeCategory.DisplayRecipe> cookingRecipes =
                List.copyOf(CookingRecipeCategory.buildAllRecipes());
        refreshRecipeTypeBySignature(recipeManager, CookingRecipeCategory.RECIPE_TYPE,
                publishedCookingRecipes, cookingRecipes,
                CookingRecipeCategory.DisplayRecipe::contentSignature);
        publishedCookingRecipes = cookingRecipes;

        boolean hasServerCatalog = com.stardew.craft.client.ClientJeiCatalog.isSynced();
        List<ShopInfoCategory.DisplayEntry> shopRecipes = hasServerCatalog
                ? com.stardew.craft.client.ClientJeiCatalog.shops().stream()
                            .map(entry -> new ShopInfoCategory.DisplayEntry(
                                    entry.item(), entry.shopId(), entry.ownerNpcId(), entry.price(), entry.stock(),
                                    entry.tradeItem(), entry.tradeItemCount(), entry.purchaseStack(),
                                    entry.seasons(), entry.minYear(), entry.minMineLevel(),
                                    entry.mailRequired(), entry.dayOfWeek(), entry.dayOfMonthParity(),
                                    entry.conditional(), entry.conditionTokens(), entry.recipe()))
                            .toList()
                : ShopInfoCategory.buildAllEntries();
        refreshRecipeTypeBySignature(recipeManager, ShopInfoCategory.RECIPE_TYPE,
                publishedShopRecipes, shopRecipes, ShopInfoCategory.DisplayEntry::contentSignature);
        publishedShopRecipes = List.copyOf(shopRecipes);

        List<GeodeProcessingCategory.DisplayEntry> geodeRecipes = new ArrayList<>(
                GeodeProcessingCategory.buildAllEntries());
        if (hasServerCatalog) {
            geodeRecipes.addAll(com.stardew.craft.client.ClientJeiCatalog.geodes().stream()
                    .map(entry -> new GeodeProcessingCategory.DisplayEntry(entry.geode(), entry.output()))
                    .toList());
        }
        refreshRecipeTypeBySignature(recipeManager, GeodeProcessingCategory.RECIPE_TYPE,
                publishedGeodeRecipes, geodeRecipes,
                GeodeProcessingCategory.DisplayEntry::contentSignature);
        publishedGeodeRecipes = List.copyOf(geodeRecipes);

        List<FishPondInfoCategory.DisplayEntry> fishPondRecipes = hasServerCatalog
                ? com.stardew.craft.client.ClientJeiCatalog.fishPonds().stream()
                            .map(entry -> new FishPondInfoCategory.DisplayEntry(
                                    entry.fish(), entry.output(), entry.requiredPopulation(),
                                    entry.outputChance(), entry.dailyMinChance(), entry.dailyMaxChance(),
                                    entry.minCount(), entry.maxCount(), entry.bonusCountPossible()))
                            .toList()
                : FishPondInfoCategory.buildAllEntries();
        refreshRecipeTypeBySignature(recipeManager, FishPondInfoCategory.RECIPE_TYPE,
                publishedFishPondRecipes, fishPondRecipes,
                FishPondInfoCategory.DisplayEntry::contentSignature);
        publishedFishPondRecipes = List.copyOf(fishPondRecipes);

        StardewCraft.LOGGER.info("Refreshed JEI from synced server content: {} fishing, {} artisan, {} crafting, {} cooking, {} shop, {} geode, {} fish pond recipes",
                fishingRecipes.size(), artisanRecipes.values().stream().mapToInt(List::size).sum(),
                craftingRecipes.size(), cookingRecipes.size(), publishedShopRecipes.size(),
                publishedGeodeRecipes.size(), publishedFishPondRecipes.size());
    }

    static List<SpawnFishRule> buildFishingRecipes() {
        return FishingInfoCategory.deduplicateRules(FishingDataManager.get().getAllFishRules());
    }

    static List<FishingInfoCategory.DisplayEntry> buildFishingEntries() {
        return FishingInfoCategory.buildEntries(FishingDataManager.get().getAllFishRules());
    }

    private static <T> void refreshRecipeTypeBySignature(
            IRecipeManager recipeManager,
            RecipeType<T> recipeType,
            List<T> previous,
            List<T> replacement,
            java.util.function.Function<T, String> signature
    ) {
        List<String> previousSignatures = previous.stream().map(signature).toList();
        List<String> replacementSignatures = replacement.stream().map(signature).toList();
        if (previousSignatures.equals(replacementSignatures)) return;
        if (!previous.isEmpty()) recipeManager.hideRecipes(recipeType, previous);
        if (!replacement.isEmpty()) recipeManager.addRecipes(recipeType, replacement);
    }

    private static void refreshArtisanRecipeType(
            IRecipeManager recipeManager,
            RecipeType<ArtisanJeiRecipe> recipeType,
            List<ArtisanJeiRecipe> previous,
            List<ArtisanJeiRecipe> replacement
    ) {
        List<String> previousSignatures = previous.stream()
                .map(ArtisanJeiRecipe::contentSignature)
                .toList();
        List<String> replacementSignatures = replacement.stream()
                .map(ArtisanJeiRecipe::contentSignature)
                .toList();
        if (previousSignatures.equals(replacementSignatures)) {
            return;
        }
        if (!previous.isEmpty()) {
            recipeManager.hideRecipes(recipeType, previous);
        }
        if (!replacement.isEmpty()) {
            recipeManager.addRecipes(recipeType, replacement);
        }
    }

    @SuppressWarnings("null")
    @Override
    public void registerRecipeCatalysts(@SuppressWarnings("null") IRecipeCatalystRegistration registration) {
        // Fishing rods → fishing info
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.FISHING_ROD.get()), FishingInfoCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.TRAINING_ROD.get()), FishingInfoCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.FIBERGLASS_ROD.get()), FishingInfoCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.IRIDIUM_ROD.get()), FishingInfoCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.ADVANCED_IRIDIUM_ROD.get()), FishingInfoCategory.RECIPE_TYPE);

        // Artisan machines → per-machine categories
        for (MachineJeiRegistry.Machine machine : MachineJeiRegistry.all()) {
            ItemStack machineStack = ArtisanRecipeCategory.itemStack(machine.itemId().toString());
            if (!machineStack.isEmpty()) {
                registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, machineStack, machine.recipeType());
            }
        }

        // Geode crusher → geode processing. Geodes themselves are real INPUT slots.
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.GEODE_CRUSHER.get()), GeodeProcessingCategory.RECIPE_TYPE);

        // Fish pond manager → fish pond products.
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.FISH_POND_MANAGER.get()), FishPondInfoCategory.RECIPE_TYPE);

        // Cooking pot → cooking
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.COOKING_POT.get()), CookingRecipeCategory.RECIPE_TYPE);
    }

    private static final class PreserveSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
        @Override
        public Object getSubtypeData(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") UidContext context) {
            return PreservesItem.getSubtypeKey(stack);
        }

        @Override
        public String getLegacyStringSubtypeInfo(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") UidContext context) {
            return PreservesItem.getSubtypeKey(stack);
        }
    }

    private static final class SpecificBaitSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
        @Override
        public Object getSubtypeData(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") UidContext context) {
            String fishId = SpecificBaitItem.getTargetFishId(stack);
            return fishId == null || fishId.isBlank() ? "target=none" : "target=" + fishId;
        }

        @Override
        public String getLegacyStringSubtypeInfo(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") UidContext context) {
            String fishId = SpecificBaitItem.getTargetFishId(stack);
            return fishId == null || fishId.isBlank() ? "target=none" : "target=" + fishId;
        }
    }

    private static final class SecretNoteSubtypeInterpreter implements ISubtypeInterpreter<ItemStack> {
        @Override
        public Object getSubtypeData(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") UidContext context) {
            return SecretNoteItem.getVariantKey(stack);
        }

        @Override
        public String getLegacyStringSubtypeInfo(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") UidContext context) {
            return SecretNoteItem.getVariantKey(stack);
        }
    }

    @SuppressWarnings("null")
    private static void hideUnboundSecretNote(IRecipeRegistration registration) {
        registration.getIngredientManager().removeIngredientsAtRuntime(
                VanillaTypes.ITEM_STACK,
                List.of(new ItemStack(ModItems.SECRET_NOTE.get())));
    }

    /**
     * Hide items tagged with stardewcraft:hidden from JEI item list.
     */
    @SuppressWarnings("null")
    private static void hideTaggedItems(IRecipeRegistration registration) {
        List<ItemStack> toHide = new ArrayList<>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(ModTags.Items.HIDDEN)) {
            toHide.add(new ItemStack(holder.value()));
        }
        if (!toHide.isEmpty()) {
            var ingredientManager = registration.getIngredientManager();
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
            StardewCraft.LOGGER.info("Hidden {} items from JEI (tag stardewcraft:hidden)", toHide.size());
        }
    }
}
