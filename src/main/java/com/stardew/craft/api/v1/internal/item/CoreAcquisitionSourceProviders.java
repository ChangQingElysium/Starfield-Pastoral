package com.stardew.craft.api.v1.internal.item;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.item.StardewAcquisitionContext;
import com.stardew.craft.api.v1.item.StardewAcquisitionSource;
import com.stardew.craft.api.v1.item.StardewAcquisitionSource.Kind;
import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewCropTypes;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import com.stardew.craft.api.v1.internal.progress.StardewProgressRegistry;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.communitycenter.data.BundleDataManager;
import com.stardew.craft.communitycenter.data.BundleDefinition;
import com.stardew.craft.communitycenter.network.BundleClaimRewardPayload;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.mail.MailRegistry;
import com.stardew.craft.museum.MuseumRewardRegistry;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.shop.ShopDataLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Projects existing authoritative catalogs into acquisition descriptions. */
final class CoreAcquisitionSourceProviders {
    private CoreAcquisitionSourceProviders() {
    }

    static List<StardewAcquisitionSource> shops(
            StardewAcquisitionContext context
    ) {
        ArrayList<StardewAcquisitionSource> result = new ArrayList<>();
        ShopDataLoader.snapshot().definitions().forEach((shopId, shop) -> {
            for (var entry : shop.entries()) {
                ResourceLocation itemId = ResourceLocation.tryParse(
                        entry.item());
                if (itemId == null
                        || !BuiltInRegistries.ITEM.containsKey(itemId)
                        || !itemId.equals(context.targetItemId())) {
                    continue;
                }
                boolean conditional = !entry.seasons().isEmpty()
                        || entry.minYear() > 1
                        || entry.minMineLevel() > 0
                        || entry.mailFlag().isPresent()
                        || entry.dayOfWeek() >= 0
                        || entry.dayOfMonthParity() > 0
                        || !entry.availableWhen().isEmpty();
                result.add(source(
                        itemId,
                        Kind.SHOP,
                        shopId,
                        entry.purchaseStack(),
                        "Shop " + shopId,
                        conditional));
            }
        });
        return result;
    }

    static List<StardewAcquisitionSource> crafting(
            StardewAcquisitionContext context
    ) {
        ArrayList<StardewAcquisitionSource> result = new ArrayList<>();
        StardewCraftingRecipeData.snapshot().definitions()
                .forEach((recipeId, recipe) -> {
                    ResourceLocation output = ResourceLocation.tryParse(
                            recipe.output().item());
                    if (context.targetItemId().equals(output)) {
                        result.add(source(
                                output,
                                Kind.CRAFTING,
                                recipeId,
                                recipe.output().count(),
                                "Crafting recipe " + recipeId,
                                !recipe.unlockCondition().isBlank()
                                        || !recipe.unlockWhen().isEmpty()));
                    }
                });
        return result;
    }

    static List<StardewAcquisitionSource> cooking(
            StardewAcquisitionContext context
    ) {
        ArrayList<StardewAcquisitionSource> result = new ArrayList<>();
        VanillaCookingRecipeData.snapshot().definitions()
                .forEach((recipeId, recipe) -> {
                    if (context.targetItemId().equals(recipe.output())) {
                        result.add(source(
                                recipe.output(),
                                Kind.COOKING,
                                recipeId,
                                recipe.outputCount(),
                                "Cooking recipe " + recipeId,
                                true));
                    }
                });
        return result;
    }

    static List<StardewAcquisitionSource> machines(
            StardewAcquisitionContext context
    ) {
        ArrayList<StardewAcquisitionSource> result = new ArrayList<>();
        ArtisanRecipeDataManager.snapshot().definitions()
                .forEach((recipeId, recipe) -> {
                    if (recipe.outputMode()
                                    == ArtisanRecipeDataManager.OutputMode.FIXED
                            && context.targetItemId().equals(
                                    recipe.outputId())) {
                        result.add(source(
                                recipe.outputId(),
                                Kind.MACHINE,
                                recipeId,
                                recipe.outputCount(),
                                "Machine recipe " + recipeId,
                                true));
                    }
                });
        return result;
    }

    static List<StardewAcquisitionSource> crops(
            StardewAcquisitionContext context
    ) {
        ArrayList<StardewAcquisitionSource> result = new ArrayList<>();
        for (var block : BuiltInRegistries.BLOCK) {
            var data = StardewAgricultureDataApi.crop(
                    block.defaultBlockState());
            if (data == null
                    || !context.targetItemId().equals(data.produce())) {
                continue;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
            result.add(new StardewAcquisitionSource(
                    data.produce(),
                    Kind.FARMING,
                    blockId,
                    1,
                    Component.literal("Harvest " + blockId),
                    true));
        }
        for (var type : StardewCropTypes.definitions()) {
            var data = type.data();
            if (data == null
                    || !context.targetItemId().equals(data.produce())) {
                continue;
            }
            result.add(new StardewAcquisitionSource(
                    data.produce(),
                    Kind.FARMING,
                    type.id(),
                    1,
                    Component.translatable(type.translationKey()),
                    true));
        }
        return result;
    }

    static List<StardewAcquisitionSource> progressRewards(
            StardewAcquisitionContext context
    ) {
        ArrayList<StardewAcquisitionSource> result = new ArrayList<>();
        MailRegistry.snapshot().definitions().forEach((mailId, mail) -> {
            for (var attachment : mail.attachedItems()) {
                if (!context.targetItemId().equals(attachment.item())) {
                    continue;
                }
                result.add(source(
                        attachment.item(),
                        Kind.PROGRESS,
                        mailId,
                        attachment.count(),
                        "Mail reward " + mailId,
                        !mail.availableWhen().isEmpty()));
            }
        });
        Collection<BundleDefinition> bundles = context.player() == null
                ? BundleDataManager.getAllBundles()
                : StardewCommunityCenterVariantRegistry.all(
                        context.player().getUUID());
        for (BundleDefinition bundle : bundles) {
            var reward = BundleClaimRewardPayload.parseRewardString(
                    bundle.rewardString());
            if (reward.isEmpty()) {
                continue;
            }
            ResourceLocation itemId =
                    BuiltInRegistries.ITEM.getKey(reward.getItem());
            if (context.targetItemId().equals(itemId)) {
                result.add(source(
                        itemId,
                        Kind.PROGRESS,
                        StardewProgressRegistry
                                .communityCenterBundleKey(
                                        bundle.bundleId()).id(),
                        reward.getCount(),
                        "Community Center bundle "
                                + bundle.bundleId(),
                        true));
            }
        }
        for (MuseumRewardRegistry.MuseumReward reward
                : MuseumRewardRegistry.getAllRewards()) {
            for (var action : reward.actions()) {
                JsonElement encoded = StardewActions.CODEC
                        .encodeStart(JsonOps.INSTANCE, action)
                        .result().orElse(null);
                if (encoded == null || !encoded.isJsonObject()) {
                    continue;
                }
                var root = encoded.getAsJsonObject();
                if (!root.has("type")
                        || !ResourceLocation.fromNamespaceAndPath(
                                StardewCraft.MODID, "add_item").toString()
                                .equals(root.get("type").getAsString())
                        || !root.has("data")
                        || !root.get("data").isJsonObject()) {
                    continue;
                }
                var data = root.getAsJsonObject("data");
                ResourceLocation itemId = data.has("item")
                        ? ResourceLocation.tryParse(
                                data.get("item").getAsString())
                        : null;
                if (!context.targetItemId().equals(itemId)) {
                    continue;
                }
                int count = data.has("count")
                        ? data.get("count").getAsInt() : 1;
                if (count > 0) {
                    result.add(source(
                            itemId,
                            Kind.PROGRESS,
                            StardewProgressRegistry
                                    .museumRewardKey(reward.id()).id(),
                            count,
                            "Museum reward " + reward.id(),
                            true));
                }
            }
        }
        return result;
    }

    private static StardewAcquisitionSource source(
            ResourceLocation itemId,
            Kind kind,
            ResourceLocation sourceId,
            int outputCount,
            String display,
            boolean runtimeDependent
    ) {
        return new StardewAcquisitionSource(
                itemId,
                kind,
                sourceId,
                outputCount,
                Component.literal(display),
                runtimeDependent);
    }
}
