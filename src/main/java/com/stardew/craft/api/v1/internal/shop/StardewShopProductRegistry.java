package com.stardew.craft.api.v1.internal.shop;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.requirement.StardewRequirement;
import com.stardew.craft.api.v1.requirement.StardewRequirementReport;
import com.stardew.craft.api.v1.requirement.StardewRequirementTypes;
import com.stardew.craft.api.v1.shop.StardewShopProductContext;
import com.stardew.craft.api.v1.shop.StardewShopProductDecision;
import com.stardew.craft.api.v1.shop.StardewShopProductGrant;
import com.stardew.craft.api.v1.shop.StardewShopProductHandler;
import com.stardew.craft.api.v1.shop.StardewShopProductPreparation;
import com.stardew.craft.deco.DecorationType;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.RecipeCatalogData;
import com.stardew.craft.shop.SaloonService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Objects;

/** Ordered, failure-isolated resolver for non-item shop products. */
public final class StardewShopProductRegistry {
    private static final OrderedExtensionRegistry<
            StardewShopProductHandler> REGISTRY =
            new OrderedExtensionRegistry<>(
                    ResourceLocation.fromNamespaceAndPath(
                            StardewCraft.MODID, "shop_products"));
    private static boolean bootstrapped;

    private StardewShopProductRegistry() {
    }

    public static synchronized void bootstrap() {
        if (bootstrapped) {
            return;
        }
        register(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "data_actions"),
                -500,
                new StardewDataShopProductHandler());
        register(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "decorations"),
                -1000,
                new DecorationProductHandler());
        register(
                ResourceLocation.fromNamespaceAndPath(
                        StardewCraft.MODID, "recipes"),
                -1000,
                new RecipeProductHandler());
        bootstrapped = true;
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewShopProductHandler handler
    ) {
        REGISTRY.register(
                id, priority,
                Objects.requireNonNull(handler, "handler"));
    }

    public static Resolution resolve(
            StardewShopProductContext context
    ) {
        Objects.requireNonNull(context, "context");
        for (var entry : REGISTRY.entries()) {
            try {
                StardewShopProductPreparation preparation =
                        REGISTRY.invoke(
                                entry,
                                handler -> handler.prepare(context));
                if (preparation == null) {
                    StardewCraft.LOGGER.error(
                            "Shop product handler {} returned null "
                                    + "for {} / {}",
                            entry.id(), context.shopId(),
                            context.entry().item());
                    continue;
                }
                if (preparation.decision()
                        == StardewShopProductDecision.ACCEPT) {
                    return Resolution.accepted(
                            entry.id(),
                            preparation.grant()
                                    .orElseThrow(),
                            requirements(
                                    entry.id(),
                                    entry.extension(),
                                    context,
                                    StardewShopProductDecision
                                            .ACCEPT));
                }
                if (preparation.decision()
                        == StardewShopProductDecision.REJECT) {
                    return Resolution.rejected(
                            entry.id(),
                            requirements(
                                    entry.id(),
                                    entry.extension(),
                                    context,
                                    StardewShopProductDecision
                                            .REJECT));
                }
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Shop product handler {} failed while preparing "
                                + "{} / {}",
                        entry.id(), context.shopId(),
                        context.entry().item(), exception);
            }
        }
        return Resolution.pass();
    }

    public static StardewRequirementReport requirements(
            StardewShopProductContext context
    ) {
        return resolve(context).requirements();
    }

    public static boolean grant(
            Resolution resolution,
            StardewShopProductContext context
    ) {
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(context, "context");
        if (resolution.decision()
                != StardewShopProductDecision.ACCEPT
                || resolution.grant() == null) {
            return false;
        }
        try {
            return resolution.grant().grant(context);
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Shop product handler {} failed while granting "
                            + "{} / {}",
                    resolution.handlerId(), context.shopId(),
                    context.entry().item(), exception);
            return false;
        }
    }

    public record Resolution(
            StardewShopProductDecision decision,
            ResourceLocation handlerId,
            StardewShopProductGrant grant,
            StardewRequirementReport requirements
    ) {
        public Resolution {
            Objects.requireNonNull(decision, "decision");
            Objects.requireNonNull(requirements, "requirements");
        }

        private static Resolution pass() {
            return new Resolution(
                    StardewShopProductDecision.PASS,
                    null, null,
                    new StardewRequirementReport(List.of()));
        }

        private static Resolution rejected(
                ResourceLocation handlerId,
                StardewRequirementReport requirements
        ) {
            return new Resolution(
                    StardewShopProductDecision.REJECT,
                    handlerId, null, requirements);
        }

        private static Resolution accepted(
                ResourceLocation handlerId,
                StardewShopProductGrant grant,
                StardewRequirementReport requirements
        ) {
            return new Resolution(
                    StardewShopProductDecision.ACCEPT,
                    handlerId, grant, requirements);
        }
    }

    private static StardewRequirementReport requirements(
            ResourceLocation handlerId,
            StardewShopProductHandler handler,
            StardewShopProductContext context,
            StardewShopProductDecision decision
    ) {
        try {
            StardewRequirementReport report =
                    handler.requirements(context, decision);
            boolean expectedSatisfied =
                    decision == StardewShopProductDecision.ACCEPT;
            if (report == null
                    || report.satisfied() != expectedSatisfied) {
                StardewCraft.LOGGER.error(
                        "Shop product handler {} returned requirements "
                                + "which contradict {} for {} / {}",
                        handlerId, decision, context.shopId(),
                        context.entry().item());
                return genericRequirements(context, decision);
            }
            return report;
        } catch (RuntimeException exception) {
            StardewCraft.LOGGER.error(
                    "Shop product handler {} failed while explaining "
                            + "{} / {}",
                    handlerId, context.shopId(),
                    context.entry().item(), exception);
            return genericRequirements(context, decision);
        }
    }

    private static StardewRequirementReport genericRequirements(
            StardewShopProductContext context,
            StardewShopProductDecision decision
    ) {
        boolean accepted =
                decision == StardewShopProductDecision.ACCEPT;
        return new StardewRequirementReport(List.of(
                new StardewRequirement(
                        StardewRequirementTypes
                                .SHOP_PRODUCT_ACCEPTED,
                        accepted
                                ? StardewRequirement.State.SATISFIED
                                : StardewRequirement.State.UNSATISFIED,
                        Component.translatable(
                                "stardewcraft.requirement.shop.product",
                                context.entry().item()),
                        false)));
    }

    private static StardewRequirementReport builtinProductRequirements(
            StardewShopProductContext context,
            StardewShopProductDecision decision,
            String translationKey
    ) {
        if (decision != StardewShopProductDecision.REJECT) {
            return genericRequirements(context, decision);
        }
        return new StardewRequirementReport(List.of(
                new StardewRequirement(
                        StardewRequirementTypes
                                .SHOP_PRODUCT_ACCEPTED,
                        StardewRequirement.State.UNSATISFIED,
                        Component.translatable(
                                translationKey,
                                context.entry().item()),
                        true)));
    }

    private static final class DecorationProductHandler
            implements StardewShopProductHandler {
        @Override
        public StardewShopProductPreparation prepare(
                StardewShopProductContext context
        ) {
            String item = context.entry().item();
            boolean wallpaper = item.startsWith("wallpaper:");
            boolean flooring = item.startsWith("flooring:");
            if (!wallpaper && !flooring) {
                return StardewShopProductPreparation.pass();
            }
            String styleId = item.substring(
                    wallpaper
                            ? "wallpaper:".length()
                            : "flooring:".length());
            DecorationType type = wallpaper
                    ? DecorationType.WALLPAPER
                    : DecorationType.FLOORING;
            if (styleId.isBlank()
                    || PlayerDataManager.getPlayerData(
                            context.player())
                    .isDecorationUnlocked(type, styleId)) {
                return StardewShopProductPreparation.reject();
            }
            return StardewShopProductPreparation.accept(
                    grantContext -> {
                        var data = PlayerDataManager.getPlayerData(
                                grantContext.player());
                        if (!data.unlockDecoration(
                                type, styleId)) {
                            return false;
                        }
                        PlayerDataEventHandler.syncPlayerData(
                                grantContext.player(), data);
                        return true;
                    });
        }

        @Override
        public StardewRequirementReport requirements(
                StardewShopProductContext context,
                StardewShopProductDecision decision
        ) {
            return builtinProductRequirements(
                    context,
                    decision,
                    "stardewcraft.requirement.shop.product.decoration");
        }
    }

    private static final class RecipeProductHandler
            implements StardewShopProductHandler {
        @Override
        public StardewShopProductPreparation prepare(
                StardewShopProductContext context
        ) {
            String item = context.entry().item();
            if (!item.startsWith("recipe:")) {
                return StardewShopProductPreparation.pass();
            }
            String recipeId =
                    SaloonService.extractRecipeId(item);
            var data = PlayerDataManager.getPlayerData(
                    context.player());
            if (recipeId.isBlank()
                    || !RecipeCatalogData
                    .getAllKnownRecipeIds().contains(recipeId)
                    || data.isRecipeUnlocked(recipeId)) {
                return StardewShopProductPreparation.reject();
            }
            return StardewShopProductPreparation.accept(
                    grantContext -> {
                        var current =
                                PlayerDataManager.getPlayerData(
                                        grantContext.player());
                        if (!current.unlockRecipe(recipeId)) {
                            return false;
                        }
                        PlayerDataEventHandler.syncPlayerData(
                                grantContext.player(), current);
                        return true;
                    });
        }

        @Override
        public StardewRequirementReport requirements(
                StardewShopProductContext context,
                StardewShopProductDecision decision
        ) {
            return builtinProductRequirements(
                    context,
                    decision,
                    "stardewcraft.requirement.shop.product.recipe");
        }
    }
}
