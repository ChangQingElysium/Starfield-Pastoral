package com.stardew.craft.api.v1.internal.reward;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.economy.StardewCurrencies;
import com.stardew.craft.api.v1.internal.communitycenter.StardewCommunityCenterVariantRegistry;
import com.stardew.craft.api.v1.internal.extension.OrderedExtensionRegistry;
import com.stardew.craft.api.v1.internal.festival.StardewFestivalRewardRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressDomains;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.reward.StardewRewardComponent;
import com.stardew.craft.api.v1.reward.StardewRewardPreview;
import com.stardew.craft.api.v1.reward.StardewRewardPreviewProvider;
import com.stardew.craft.communitycenter.network.BundleClaimRewardPayload;
import com.stardew.craft.mail.MailRegistry;
import com.stardew.craft.museum.MuseumRewardRegistry;
import com.stardew.craft.quest.QuestDataLoader;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.specialorder.SpecialOrderDataLoader;
import com.stardew.craft.specialorder.SpecialOrderDefinition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Built-in adapters plus ordered, failure-isolated add-on reward preview composition. */
public final class StardewRewardPreviewRegistry {
    private static final ResourceLocation ADD_ITEM =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "add_item");
    private static final ResourceLocation FRIENDSHIP =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "friendship");
    private static final ResourceLocation MAIL =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mail");
    private static final ResourceLocation DESCRIPTION =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "description");
    private static final ResourceLocation RECIPE =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "recipe");
    private static final ResourceLocation START_QUEST =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "start_quest");
    private static final ResourceLocation START_SPECIAL_ORDER =
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "start_special_order");
    private static final OrderedExtensionRegistry<StardewRewardPreviewProvider> PROVIDERS =
            new OrderedExtensionRegistry<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "reward/preview"));

    private StardewRewardPreviewRegistry() {
    }

    public static void register(
            ResourceLocation id,
            int priority,
            StardewRewardPreviewProvider provider
    ) {
        PROVIDERS.register(
                Objects.requireNonNull(id, "id"),
                priority,
                Objects.requireNonNull(provider, "provider"));
    }

    @Nullable
    public static StardewRewardPreview preview(
            ServerPlayer player,
            StardewProgressKey progress
    ) {
        StardewRewardPreview current = builtin(player, progress);
        for (var entry : PROVIDERS.entries()) {
            try {
                StardewRewardPreview proposedPreview = current;
                StardewRewardPreview candidate = PROVIDERS.invoke(
                        entry,
                        provider -> provider.preview(
                                player, progress, proposedPreview));
                if (candidate == null) {
                    continue;
                }
                if (!candidate.progress().equals(progress)) {
                    StardewCraft.LOGGER.error(
                            "Reward preview provider {} returned {} while resolving {}",
                            entry.id(), candidate.progress(), progress);
                    continue;
                }
                current = candidate;
            } catch (RuntimeException exception) {
                StardewCraft.LOGGER.error(
                        "Reward preview provider {} failed for {}",
                        entry.id(), progress, exception);
            }
        }
        return current;
    }

    @Nullable
    private static StardewRewardPreview builtin(
            ServerPlayer player,
            StardewProgressKey progress
    ) {
        var festivalReward =
                StardewFestivalRewardRegistry.descriptor(progress);
        if (festivalReward != null) {
            return new StardewRewardPreview(
                    progress,
                    festivalReward.preview(),
                    festivalReward.previewExhaustive());
        }
        if (progress.domain().equals(StardewProgressDomains.QUEST)) {
            return quest(player, progress);
        }
        if (progress.domain().equals(StardewProgressDomains.MAIL)) {
            return mail(progress);
        }
        if (progress.domain().equals(
                StardewProgressDomains.COMMUNITY_CENTER)) {
            return communityCenter(player, progress);
        }
        if (progress.domain().equals(StardewProgressDomains.MUSEUM)) {
            return museum(progress);
        }
        if (progress.domain().equals(
                StardewProgressDomains.SPECIAL_ORDER)) {
            return specialOrder(progress);
        }
        return null;
    }

    @Nullable
    private static StardewRewardPreview quest(
            ServerPlayer player,
            StardewProgressKey progress
    ) {
        var definition = QuestDataLoader.getDefinition(progress.id());
        QuestManager manager = QuestManager.of(player);
        var active = manager == null
                ? null : manager.getQuest(progress.id().toString());
        int money = active != null
                ? active.getMoneyReward()
                : definition == null ? 0 : definition.moneyReward();
        ArrayList<StardewRewardComponent> components = new ArrayList<>();
        if (money > 0) {
            components.add(currency(
                    StardewCurrencies.MONEY, money));
        }
        Component description = active != null
                && active.getRewardDescription() != null
                && !active.getRewardDescription().isBlank()
                ? Component.literal(active.getRewardDescription())
                : definition != null
                        && definition.rewardDescription().isPresent()
                        ? definition.rewardDescription().get().component()
                        : null;
        if (description != null) {
            components.add(new StardewRewardComponent(
                    StardewRewardComponent.Kind.OTHER,
                    DESCRIPTION,
                    0,
                    ItemStack.EMPTY,
                    description,
                    true));
        }
        if (definition != null) {
            for (StardewAction action : definition.onComplete()) {
                addAction(components, action);
            }
        }
        return components.isEmpty()
                ? null : new StardewRewardPreview(
                        progress, components, true);
    }

    @Nullable
    private static StardewRewardPreview mail(StardewProgressKey progress) {
        var definition = MailRegistry.getDefinition(progress.id());
        if (definition == null) {
            return null;
        }
        ArrayList<StardewRewardComponent> components = new ArrayList<>();
        for (var attachment : definition.attachedItems()) {
            if (BuiltInRegistries.ITEM.containsKey(attachment.item())) {
                components.add(item(
                        new ItemStack(
                                BuiltInRegistries.ITEM.get(
                                        attachment.item()),
                                attachment.count()),
                        false));
            }
        }
        if (definition.money() > 0) {
            components.add(currency(
                    StardewCurrencies.MONEY, definition.money()));
        }
        definition.learnedRecipe().ifPresent(recipe ->
                components.add(new StardewRewardComponent(
                        StardewRewardComponent.Kind.OTHER,
                        RECIPE,
                        1,
                        ItemStack.EMPTY,
                        Component.literal("Recipe " + recipe),
                        true)));
        definition.quest().ifPresent(quest ->
                components.add(new StardewRewardComponent(
                        StardewRewardComponent.Kind.ACTION,
                        START_QUEST,
                        0,
                        ItemStack.EMPTY,
                        Component.literal("Quest " + quest),
                        true)));
        definition.specialOrder().ifPresent(order ->
                components.add(new StardewRewardComponent(
                        StardewRewardComponent.Kind.ACTION,
                        START_SPECIAL_ORDER,
                        0,
                        ItemStack.EMPTY,
                        Component.literal("Special order " + order),
                        true)));
        for (StardewAction action : definition.onDelivery()) {
            addAction(components, action);
        }
        for (StardewAction action : definition.onRead()) {
            addAction(components, action);
        }
        return components.isEmpty()
                ? null : new StardewRewardPreview(
                        progress, components, true);
    }

    @Nullable
    private static StardewRewardPreview communityCenter(
            ServerPlayer player,
            StardewProgressKey progress
    ) {
        Integer bundleId = numericSuffix(
                progress.id().getPath(), "bundle/");
        if (bundleId == null) {
            return null;
        }
        var definition = StardewCommunityCenterVariantRegistry.bundle(
                player.getUUID(), bundleId);
        if (definition == null) {
            return null;
        }
        ItemStack reward = BundleClaimRewardPayload.parseRewardString(
                definition.rewardString());
        return reward.isEmpty()
                ? null : new StardewRewardPreview(
                        progress,
                        List.of(item(reward, true)),
                        false);
    }

    @Nullable
    private static StardewRewardPreview museum(
            StardewProgressKey progress
    ) {
        for (MuseumRewardRegistry.MuseumReward reward
                : MuseumRewardRegistry.getAllRewards()) {
            if (!com.stardew.craft.api.v1.internal.progress
                    .StardewProgressRegistry.museumRewardKey(
                            reward.id()).equals(progress)) {
                continue;
            }
            ArrayList<StardewRewardComponent> components =
                    new ArrayList<>();
            for (StardewAction action : reward.actions()) {
                addAction(components, action);
            }
            return components.isEmpty()
                    ? null : new StardewRewardPreview(
                            progress, components, true);
        }
        return null;
    }

    @Nullable
    private static StardewRewardPreview specialOrder(
            StardewProgressKey progress
    ) {
        SpecialOrderDefinition definition = SpecialOrderDataLoader
                .snapshot().definitions().get(progress.id());
        if (definition == null) {
            return null;
        }
        ArrayList<StardewRewardComponent> components =
                new ArrayList<>();
        for (SpecialOrderDefinition.RewardDefinition reward
                : definition.rewards()) {
            if (reward.extension() != null) {
                components.add(action(
                        reward.extension().type(),
                        reward.typeName()));
                continue;
            }
            switch (reward.type()) {
                case MONEY -> components.add(currency(
                        StardewCurrencies.MONEY, reward.amount()));
                case FRIENDSHIP -> components.add(
                        new StardewRewardComponent(
                                StardewRewardComponent.Kind.OTHER,
                                FRIENDSHIP,
                                reward.amount(),
                                ItemStack.EMPTY,
                                Component.literal(
                                        "Friendship +" + reward.amount()),
                                false));
                case MAIL -> components.add(
                        new StardewRewardComponent(
                                StardewRewardComponent.Kind.ACTION,
                                MAIL,
                                0,
                                ItemStack.EMPTY,
                                Component.literal(
                                        "Mail " + reward.mailId()),
                                true));
            }
        }
        return components.isEmpty()
                ? null : new StardewRewardPreview(
                        progress, components, true);
    }

    private static void addAction(
            List<StardewRewardComponent> components,
            StardewAction action
    ) {
        JsonElement encoded = StardewActions.CODEC
                .encodeStart(JsonOps.INSTANCE, action)
                .result().orElse(null);
        if (action.type().equals(ADD_ITEM)
                && encoded != null
                && encoded.isJsonObject()
                && encoded.getAsJsonObject().has("data")) {
            var data = encoded.getAsJsonObject()
                    .getAsJsonObject("data");
            ResourceLocation itemId = data.has("item")
                    ? ResourceLocation.tryParse(
                            data.get("item").getAsString())
                    : null;
            int count = data.has("count")
                    ? data.get("count").getAsInt() : 1;
            if (itemId != null
                    && count > 0
                    && BuiltInRegistries.ITEM.containsKey(itemId)) {
                components.add(item(
                        new ItemStack(
                                BuiltInRegistries.ITEM.get(itemId),
                                count),
                        false));
                return;
            }
        }
        components.add(action(
                action.type(), action.type().toString()));
    }

    private static StardewRewardComponent item(
            ItemStack stack,
            boolean runtimeDependent
    ) {
        ResourceLocation itemId =
                BuiltInRegistries.ITEM.getKey(stack.getItem());
        return new StardewRewardComponent(
                StardewRewardComponent.Kind.ITEM,
                itemId,
                stack.getCount(),
                stack,
                stack.getHoverName(),
                runtimeDependent);
    }

    private static StardewRewardComponent currency(
            ResourceLocation currencyId,
            long amount
    ) {
        var currency = StardewCurrencies.definitions().stream()
                .filter(value -> value.id().equals(currencyId))
                .findFirst().orElse(null);
        return new StardewRewardComponent(
                StardewRewardComponent.Kind.CURRENCY,
                currencyId,
                amount,
                currency == null
                        ? new ItemStack(Items.GOLD_NUGGET)
                        : currency.icon(),
                currency == null
                        ? Component.literal(currencyId.toString())
                        : currency.displayName(),
                false);
    }

    private static StardewRewardComponent action(
            ResourceLocation actionId,
            String display
    ) {
        return new StardewRewardComponent(
                StardewRewardComponent.Kind.ACTION,
                actionId,
                0,
                ItemStack.EMPTY,
                Component.literal(display),
                true);
    }

    private static Integer numericSuffix(
            String path,
            String prefix
    ) {
        if (!path.startsWith(prefix)) {
            return null;
        }
        try {
            int value = Integer.parseInt(
                    path.substring(prefix.length()));
            return value < 0 ? null : value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
