package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.fishing.data.FishingDataManager;
import com.stardew.craft.fishing.data.SpawnFishRule;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.SecretNoteItem;
import com.stardew.craft.item.SpecificBaitItem;
import com.stardew.craft.item.StardewQualityItem;
import com.stardew.craft.item.artisan.ArtisanDrinkItem;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.item.artisan.DehydratorIngredientHelper;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.item.artisan.SmokedFishItem;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.item.catalog.StardewItemComparator;
import com.stardew.craft.item.quality.QualityHelper;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.registration.IExtraIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JEI 插件 - 钓鱼信息展示
 * 这是可选依赖，当JEI不存在时不会加载
 */
@JeiPlugin
public class StardewJeiPlugin implements IModPlugin {
    public static final ResourceLocation PLUGIN_ID = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "jei_plugin");
    private static volatile IJeiRuntime runtime;
    private static volatile Set<String> registeredMachineKeys = Set.of();
    private static volatile List<SpawnFishRule> publishedFishingRecipes = List.of();
    private static volatile Map<String, List<ArtisanRecipeCategory.DisplayRecipe>> publishedArtisanRecipes = Map.of();
    private static volatile List<StardewCraftingCategory.DisplayRecipe> publishedCraftingRecipes = List.of();
    private static volatile List<ShopInfoCategory.DisplayEntry> publishedShopRecipes = List.of();
    private static volatile List<GeodeProcessingCategory.DisplayEntry> publishedGeodeRecipes = List.of();

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_ID;
    }

    @Override
    public void registerCategories(@SuppressWarnings("null") IRecipeCategoryRegistration registration) {
        var guiHelper = registration.getJeiHelpers().getGuiHelper();
        // Pre-load NPC portraits for shop category
        JeiPortraitCache.preload(guiHelper, JeiPortraitCache.SHOP_NPC_IDS);
        registration.addRecipeCategories(new FishingInfoCategory(guiHelper));

        // Per-machine artisan categories
        Set<String> machineKeys = new HashSet<>();
        for (String machineKey : ArtisanRecipeDataManager.getAllMachineKeys()) {
            ItemStack machineIcon = ArtisanRecipeCategory.getItemStack("stardewcraft:" + machineKey);
            if (machineIcon.isEmpty()) continue;
            var recipeType = ArtisanRecipeCategory.getRecipeType(machineKey);
            registration.addRecipeCategories(new ArtisanRecipeCategory(guiHelper, machineKey, machineIcon, recipeType));
            machineKeys.add(machineKey);
        }
        registeredMachineKeys = Set.copyOf(machineKeys);

        registration.addRecipeCategories(new ShopInfoCategory(guiHelper));
        registration.addRecipeCategories(new GeodeProcessingCategory(guiHelper));
        registration.addRecipeCategories(new StardewCraftingCategory(guiHelper));
    }

    @Override
    @SuppressWarnings("null")
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        for (var holder : ModItems.ITEMS.getEntries()) {
            var item = holder.get();
            if (!isQualityItem(item)) {
                continue;
            }
            registration.registerSubtypeInterpreter(item, new ISubtypeInterpreter<>() {
                @Override
                public Object getSubtypeData(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") UidContext context) {
                    int quality = QualityHelper.getQuality(stack);
                    Integer color = getFlowerColor(stack);
                    if (color != null) {
                        return "q=" + quality + ",c=" + color;
                    }
                    return "q=" + quality;
                }

                @Override
                public String getLegacyStringSubtypeInfo(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") UidContext context) {
                    int quality = QualityHelper.getQuality(stack);
                    Integer color = getFlowerColor(stack);
                    if (color != null) {
                        return "quality=" + quality + ",color=" + color;
                    }
                    return "quality=" + quality;
                }
            });
        }

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
        List<SpawnFishRule> allRules = FishingDataManager.get().getAllFishRules();
        List<SpawnFishRule> uniqueRules = buildFishingRecipes();
        
        if (!uniqueRules.isEmpty()) {
            registration.addRecipes(FishingInfoCategory.RECIPE_TYPE, uniqueRules);
            StardewCraft.LOGGER.info("Registered {} unique fishing info recipes for JEI (deduplicated from {})", uniqueRules.size(), allRules.size());
        } else {
            StardewCraft.LOGGER.warn("No fishing rules loaded when registering JEI recipes!");
        }
        publishedFishingRecipes = List.copyOf(uniqueRules);

        // Artisan machine recipes — per machine
        int totalArtisan = 0;
        Map<String, List<ArtisanRecipeCategory.DisplayRecipe>> artisanRecipes = new LinkedHashMap<>();
        for (String machineKey : ArtisanRecipeDataManager.getAllMachineKeys()) {
            var recipes = ArtisanRecipeCategory.buildRecipesForMachine(machineKey);
            artisanRecipes.put(machineKey, List.copyOf(recipes));
            if (!recipes.isEmpty()) {
                registration.addRecipes(ArtisanRecipeCategory.getRecipeType(machineKey), recipes);
                totalArtisan += recipes.size();
            }
        }
        if (totalArtisan > 0) {
            StardewCraft.LOGGER.info("Registered {} artisan machine recipes across {} machines for JEI",
                    totalArtisan, ArtisanRecipeDataManager.getAllMachineKeys().size());
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

        // Stardew crafting
        var craftingRecipes = StardewCraftingCategory.buildAllRecipes();
        if (!craftingRecipes.isEmpty()) {
            registration.addRecipes(StardewCraftingCategory.RECIPE_TYPE, craftingRecipes);
            StardewCraft.LOGGER.info("Registered {} stardew crafting recipes for JEI", craftingRecipes.size());
        }
        publishedCraftingRecipes = List.copyOf(craftingRecipes);

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
        List<SpawnFishRule> fishingRecipes = buildFishingRecipes();
        refreshRecipeType(recipeManager, FishingInfoCategory.RECIPE_TYPE,
                publishedFishingRecipes, fishingRecipes);
        publishedFishingRecipes = List.copyOf(fishingRecipes);

        Map<String, List<ArtisanRecipeCategory.DisplayRecipe>> artisanRecipes = new LinkedHashMap<>();
        for (String machineKey : registeredMachineKeys) {
            List<ArtisanRecipeCategory.DisplayRecipe> recipes =
                    List.copyOf(ArtisanRecipeCategory.buildRecipesForMachine(machineKey));
            artisanRecipes.put(machineKey, recipes);
            refreshRecipeType(recipeManager, ArtisanRecipeCategory.getRecipeType(machineKey),
                    publishedArtisanRecipes.getOrDefault(machineKey, List.of()), recipes);
        }
        publishedArtisanRecipes = Map.copyOf(artisanRecipes);

        List<StardewCraftingCategory.DisplayRecipe> craftingRecipes =
                List.copyOf(StardewCraftingCategory.buildAllRecipes());
        refreshRecipeType(recipeManager, StardewCraftingCategory.RECIPE_TYPE,
                publishedCraftingRecipes, craftingRecipes);
        publishedCraftingRecipes = craftingRecipes;

        if (com.stardew.craft.client.ClientJeiCatalog.isSynced()) {
            List<ShopInfoCategory.DisplayEntry> shopRecipes =
                    com.stardew.craft.client.ClientJeiCatalog.shops().stream()
                            .map(entry -> new ShopInfoCategory.DisplayEntry(
                                    entry.item(), entry.shopId(), entry.price(), entry.stock(),
                                    entry.seasons(), entry.minYear(), entry.recipe()))
                            .toList();
            refreshRecipeType(recipeManager, ShopInfoCategory.RECIPE_TYPE,
                    publishedShopRecipes, shopRecipes);
            publishedShopRecipes = List.copyOf(shopRecipes);

            List<GeodeProcessingCategory.DisplayEntry> geodeRecipes = new ArrayList<>(
                    GeodeProcessingCategory.buildAllEntries());
            geodeRecipes.addAll(com.stardew.craft.client.ClientJeiCatalog.geodes().stream()
                    .map(entry -> new GeodeProcessingCategory.DisplayEntry(entry.geode(), entry.output()))
                    .toList());
            refreshRecipeType(recipeManager, GeodeProcessingCategory.RECIPE_TYPE,
                    publishedGeodeRecipes, geodeRecipes);
            publishedGeodeRecipes = List.copyOf(geodeRecipes);
        }

        StardewCraft.LOGGER.info("Refreshed JEI from synced server content: {} fishing, {} artisan, {} crafting, {} shop, {} geode recipes",
                fishingRecipes.size(), artisanRecipes.values().stream().mapToInt(List::size).sum(),
                craftingRecipes.size(), publishedShopRecipes.size(), publishedGeodeRecipes.size());
    }

    private static List<SpawnFishRule> buildFishingRecipes() {
        List<SpawnFishRule> uniqueRules = new ArrayList<>();
        Set<String> seenItemIds = new HashSet<>();
        for (SpawnFishRule rule : FishingDataManager.get().getAllFishRules()) {
            if (seenItemIds.add(rule.itemId())) {
                uniqueRules.add(rule);
            }
        }
        uniqueRules.sort(StardewItemComparator.FISH_RULE);
        return uniqueRules;
    }

    private static <T> void refreshRecipeType(
            IRecipeManager recipeManager,
            RecipeType<T> recipeType,
            List<T> previous,
            List<T> replacement
    ) {
        if (previous.equals(replacement)) {
            return;
        }
        List<T> removed = previous.stream().filter(recipe -> !replacement.contains(recipe)).toList();
        List<T> added = replacement.stream().filter(recipe -> !previous.contains(recipe)).toList();
        if (!removed.isEmpty()) {
            recipeManager.hideRecipes(recipeType, removed);
        }
        if (!added.isEmpty()) {
            recipeManager.addRecipes(recipeType, added);
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
        for (String machineKey : ArtisanRecipeDataManager.getAllMachineKeys()) {
            ItemStack machineStack = ArtisanRecipeCategory.getItemStack("stardewcraft:" + machineKey);
            if (!machineStack.isEmpty()) {
                registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, machineStack, ArtisanRecipeCategory.getRecipeType(machineKey));
            }
        }

        // Geode types → geode processing
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.GEODE.get()), GeodeProcessingCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.FROZEN_GEODE.get()), GeodeProcessingCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.MAGMA_GEODE.get()), GeodeProcessingCategory.RECIPE_TYPE);
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.OMNI_GEODE.get()), GeodeProcessingCategory.RECIPE_TYPE);

        // Crafting table → stardew crafting
        registration.addRecipeCatalyst(VanillaTypes.ITEM_STACK, new ItemStack(net.minecraft.world.item.Items.CRAFTING_TABLE), StardewCraftingCategory.RECIPE_TYPE);
    }


    private static boolean isQualityItem(net.minecraft.world.item.Item item) {
        String typeKey = StardewItemDataApi.getTypeKey(new ItemStack(item));
        if (typeKey.isBlank()) {
            return false;
        }
        if (item instanceof StardewQualityItem qualityItem && qualityItem.supportsQuality()) {
            return true;
        }
        if (item instanceof ArtisanDrinkItem drink && drink.supportsQuality()) {
            return true;
        }
        if (item instanceof SmokedFishItem) {
            return true;
        }
        return "stardewcraft.type.crop".equals(typeKey)
            || "stardewcraft.type.crop_seed".equals(typeKey)
            || "stardewcraft.type.fruit".equals(typeKey)
            || "stardewcraft.type.forage".equals(typeKey)
            || "stardewcraft.type.fish".equals(typeKey)
            || "stardewcraft.type.crabpot".equals(typeKey)
            || "stardewcraft.type.legendary_fish".equals(typeKey)
            || "stardewcraft.type.animal_product".equals(typeKey)
            || "stardewcraft.type.artisan_animal_quality".equals(typeKey)
            || "stardewcraft.type.artifact_quality".equals(typeKey);
    }

    private static List<ItemStack> buildSmokedFishVariants() {
        List<ItemStack> stacks = new ArrayList<>();
        for (var holder : ModItems.ITEMS.getEntries()) {
            var item = holder.get();
            if (!(item instanceof SmokedFishItem)) {
                continue;
            }
            ItemStack base = new ItemStack(item);
            stacks.add(QualityHelper.createWithQuality(base, QualityHelper.NORMAL));
            stacks.add(QualityHelper.createWithQuality(base, QualityHelper.SILVER));
            stacks.add(QualityHelper.createWithQuality(base, QualityHelper.GOLD));
            stacks.add(QualityHelper.createWithQuality(base, QualityHelper.IRIDIUM));
        }
        return stacks;
    }

    private static Integer getFlowerColor(@SuppressWarnings("null") ItemStack stack) {
        @SuppressWarnings("null")
        var customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY);
        var tag = customData.copyTag();
        if (tag.contains("FlowerColor")) {
            return tag.getInt("FlowerColor");
        }
        return null;
    }

    private static List<ItemStack> buildPreserveVariants() {
        List<ItemStack> stacks = new ArrayList<>();
        for (var holder : ModItems.ITEMS.getEntries()) {
            var item = holder.get();
            ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
            // 注意：不再依赖 PreservesIngredientDataManager.hasData() 检查
            // 因为在专用服务器客户端上，数据可能在 JEI 初始化后才通过网络同步
            String typeKey = StardewItemDataApi.getTypeKey(new ItemStack(item));
            if (typeKey.isBlank()) continue;
            if (isPreserveCropIngredient(typeKey)) {
                var preserveType = com.stardew.craft.item.artisan.PreservesCropTypeHelper.getCropPreserveType(id);
                if (preserveType != null) {
                    stacks.add(buildPreserveVariant(preserveType, item, getPreserveBaseItem(preserveType)));
                }
            } else if (isPreserveFishIngredient(typeKey)) {
                stacks.add(buildPreserveVariant(PreserveType.ROE, item, ModItems.ROE.get()));
                stacks.add(buildPreserveVariant(PreserveType.AGED_ROE, item, ModItems.AGED_ROE.get()));
                if (isSturgeon(id)) {
                    stacks.add(buildPreserveVariant(PreserveType.CAVIAR, item, ModItems.CAVIAR.get()));
                }
            }

            if (isDehydratorFruitIngredient(typeKey, id)) {
                stacks.add(buildPreserveVariant(PreserveType.DRIED_FRUIT, item, ModItems.DRIED_FRUIT.get()));
            } else if (isDehydratorMushroomIngredient(id)) {
                stacks.add(buildPreserveVariant(PreserveType.DRIED_MUSHROOMS, item, ModItems.DRIED_MUSHROOMS.get()));
            }
        }
        return stacks;
    }

    private static List<ItemStack> buildSpecificBaitVariants() {
        List<ItemStack> stacks = new ArrayList<>();
        int variantIndex = 0;
        for (var holder : ModItems.ITEMS.getEntries()) {
            var item = holder.get();
            if (!isPreserveFishIngredient(StardewItemDataApi.getTypeKey(new ItemStack(item)))) {
                continue;
            }

            ItemStack fishStack = new ItemStack(item);
            if (fishStack.isEmpty()) {
                continue;
            }

            ItemStack baitStack = SpecificBaitItem.createForFish(fishStack, 1);
            // Keep JEI subtype stable even if some environments strip custom NBT.
            SpecificBaitItem.applyCreativeVariantMarker(baitStack, variantIndex);
            stacks.add(baitStack);
            variantIndex++;
        }
        return stacks;
    }

    private static List<ItemStack> buildCategoryDisplayVariants() {
        List<ItemStack> stacks = new ArrayList<>();
        List<net.minecraft.world.item.Item> cookingIngredients = new ArrayList<>();
        List<net.minecraft.world.item.Item> crops = new ArrayList<>();
        List<net.minecraft.world.item.Item> fruits = new ArrayList<>();
        List<net.minecraft.world.item.Item> forages = new ArrayList<>();

        for (var holder : ModItems.ITEMS.getEntries()) {
            var item = holder.get();
            String typeKey = StardewItemDataApi.getTypeKey(new ItemStack(item));
            if (typeKey.isBlank()) continue;
            if ("stardewcraft.type.cooking_ingredient".equals(typeKey)) {
                cookingIngredients.add(item);
            } else if ("stardewcraft.type.crop".equals(typeKey)) {
                crops.add(item);
            } else if ("stardewcraft.type.fruit".equals(typeKey)) {
                fruits.add(item);
            } else if ("stardewcraft.type.forage".equals(typeKey)) {
                forages.add(item);
            }
        }

        stacks.addAll(buildGroupedCategoryStacks(cookingIngredients, false));
    stacks.addAll(buildGroupedCategoryStacks(crops, true));
        stacks.addAll(buildGroupedCategoryStacks(fruits, true));
        stacks.addAll(buildGroupedCategoryStacks(forages, true));
        return stacks;
    }

    @SuppressWarnings("null")
    private static List<ItemStack> buildGroupedCategoryStacks(List<net.minecraft.world.item.Item> items, boolean forceQuality) {
        List<ItemStack> stacks = new ArrayList<>();
        items.sort(java.util.Comparator.comparing(i -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getPath()));
        for (var item : items) {
            boolean supportsQuality = item instanceof StardewQualityItem qualityItem && qualityItem.supportsQuality();
            if (forceQuality || supportsQuality) {
                ItemStack base = new ItemStack(item);
                stacks.add(QualityHelper.createWithQuality(base, QualityHelper.NORMAL));
                stacks.add(QualityHelper.createWithQuality(base, QualityHelper.SILVER));
                stacks.add(QualityHelper.createWithQuality(base, QualityHelper.GOLD));
                stacks.add(QualityHelper.createWithQuality(base, QualityHelper.IRIDIUM));
                continue;
            }
            ItemStack base = new ItemStack(item);
            stacks.add(base);
        }
        return stacks;
    }

    private static boolean isPreserveCropIngredient(String typeKey) {
        return "stardewcraft.type.crop".equals(typeKey);
    }

    private static boolean isPreserveFishIngredient(String typeKey) {
        return "stardewcraft.type.fish".equals(typeKey)
                || "stardewcraft.type.crabpot".equals(typeKey)
                || "stardewcraft.type.legendary_fish".equals(typeKey);
    }

    private static boolean isDehydratorFruitIngredient(String typeKey, ResourceLocation id) {
        if (!"stardewcraft.type.crop".equals(typeKey)) {
            return false;
        }
        if (isGrape(id)) {
            return false;
        }
        return DehydratorIngredientHelper.isFruitCrop(id);
    }

    private static boolean isDehydratorMushroomIngredient(ResourceLocation id) {
        return DehydratorIngredientHelper.isMushroom(id);
    }

    private static boolean isSturgeon(ResourceLocation id) {
        return id != null && "sturgeon".equals(id.getPath());
    }

    private static boolean isGrape(ResourceLocation id) {
        return id != null && "grape".equals(id.getPath());
    }

    @SuppressWarnings("null")
    private static ItemStack buildPreserveVariant(PreserveType type, net.minecraft.world.item.Item ingredient, net.minecraft.world.item.Item baseItem) {
        ItemStack ingredientStack = new ItemStack(ingredient);
        ItemStack resultStack = new ItemStack(baseItem);
        PreservesItem.createFlavored(type, ingredientStack, resultStack);
        return resultStack;
    }

    private static net.minecraft.world.item.Item getPreserveBaseItem(PreserveType type) {
        return switch (type) {
            case JELLY -> ModItems.JELLY.get();
            case PICKLES -> ModItems.PICKLES.get();
            case ROE -> ModItems.ROE.get();
            case AGED_ROE -> ModItems.AGED_ROE.get();
            case CAVIAR -> ModItems.CAVIAR.get();
            case DRIED_FRUIT -> ModItems.DRIED_FRUIT.get();
            case DRIED_MUSHROOMS -> ModItems.DRIED_MUSHROOMS.get();
        };
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
        TagKey<Item> hiddenTag = ItemTags.create(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "hidden"));
        List<ItemStack> toHide = new ArrayList<>();
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(hiddenTag)) {
            toHide.add(new ItemStack(holder.value()));
        }
        if (!toHide.isEmpty()) {
            var ingredientManager = registration.getIngredientManager();
            ingredientManager.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
            StardewCraft.LOGGER.info("Hidden {} items from JEI (tag stardewcraft:hidden)", toHide.size());
        }
    }
}
