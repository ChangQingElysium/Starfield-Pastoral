package com.stardew.craft.economy.sell;

import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.artisan.SmokedFishItem;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.ProfessionData;
import com.stardew.craft.api.v1.profession.StardewProfessionEffectHandlers;
import com.stardew.craft.core.ModTags;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class ProfessionSellPriceService {
    private static final Set<String> CROP_TYPES = Set.of(
        "stardewcraft.type.crop"
    );

    private static final Set<String> ANIMAL_PRODUCT_TYPES = Set.of(
        "stardewcraft.type.animal_product"
    );

    private static final Set<String> ARTISAN_TYPES = Set.of(
        "stardewcraft.type.artisan_goods",
        "stardewcraft.type.artisan_animal_quality"
    );

    private static final Set<String> FISH_TYPES = Set.of(
        "stardewcraft.type.fish",
        "stardewcraft.type.fish_quality"
    );

    private static final Set<String> METAL_BAR_TYPES = Set.of(
        "stardewcraft.type.metal_bar"
    );

    private static final Set<String> GEM_TYPES = Set.of(
        "stardewcraft.type.gem"
    );

    private static final Set<String> TAPPER_TYPES = Set.of(
        "stardewcraft.type.syrup"
    );

    private ProfessionSellPriceService() {
    }

    public static SellQuote quoteItem(ServerPlayer player, ItemStack stack, SellSource source) {
        return quoteItemWithChecker(player, stack, source, profession -> hasProfession(player, profession));
    }

    public static SellQuote quoteItem(PlayerStardewData data, ItemStack stack, SellSource source) {
        return quoteItemWithData(data, stack, source, profession -> hasProfession(data, profession));
    }

    public static SellQuote quoteItemForProfessionNames(Set<String> professionNames, ItemStack stack, SellSource source) {
        Set<String> normalizedNames = new HashSet<>();
        for (String name : professionNames) {
            if (name != null && !name.isBlank()) {
                normalizedNames.add(name.toLowerCase(Locale.ROOT));
            }
        }

        return quoteItemWithChecker(null, stack, source,
            profession -> normalizedNames.contains(profession.getName().toLowerCase(Locale.ROOT)));
    }

    private static SellQuote quoteItemWithChecker(ServerPlayer player, ItemStack stack, SellSource source, ProfessionChecker checker) {
        String typeKey = StardewItemDataApi.getTypeKey(stack);
        int baseUnitPrice = StardewItemDataApi.getSellPrice(stack);
        if (typeKey.isBlank() || baseUnitPrice < 0) {
            return SellQuote.unsellable(SellContext.forItem(source, ""), stack.getCount());
        }

        SellContext context = SellContext.forItem(source, typeKey);
        if (player != null) {
            PlayerStardewData data = PlayerStardewDataAPI.getData(player);
            baseUnitPrice = BookPowerEffects.applyArtifactSellPrice(data, context.itemTypeKey(), baseUnitPrice);
        }
        return quote(baseUnitPrice, stack.getCount(), context, stack, player, checker);
    }

    private static SellQuote quoteItemWithData(PlayerStardewData data, ItemStack stack, SellSource source, ProfessionChecker checker) {
        String typeKey = StardewItemDataApi.getTypeKey(stack);
        int baseUnitPrice = StardewItemDataApi.getSellPrice(stack);
        if (typeKey.isBlank() || baseUnitPrice < 0) {
            return SellQuote.unsellable(SellContext.forItem(source, ""), stack.getCount());
        }

        SellContext context = SellContext.forItem(source, typeKey);
        if (data != null) {
            baseUnitPrice = BookPowerEffects.applyArtifactSellPrice(data, context.itemTypeKey(), baseUnitPrice);
        }
        return quote(baseUnitPrice, stack.getCount(), context, stack, null, checker);
    }

    public static SellQuote quoteAnimal(ServerPlayer player, int basePrice, SellSource source) {
        return quote(basePrice, 1, SellContext.forAnimal(source), ItemStack.EMPTY, player,
            profession -> hasProfession(player, profession));
    }

    public static int payoutItem(ServerPlayer player, ItemStack stack, SellSource source) {
        SellQuote quote = quoteItem(player, stack, source);
        payout(player, quote);
        return quote.totalPrice();
    }

    public static int payoutAnimal(ServerPlayer player, int basePrice, SellSource source) {
        SellQuote quote = quoteAnimal(player, basePrice, source);
        payout(player, quote);
        return quote.totalPrice();
    }

    public static void payout(ServerPlayer player, SellQuote quote) {
        if (quote.sellable() && quote.totalPrice() > 0) {
            PlayerStardewDataAPI.addMoney(player, quote.totalPrice());
        }
    }

    private static SellQuote quote(int baseUnitPrice, int count, SellContext context, ItemStack stack,
                                   ServerPlayer player, ProfessionChecker checker) {
        if (baseUnitPrice <= 0 || count <= 0) {
            return SellQuote.unsellable(context, count);
        }

        double multiplier = resolveMultiplier(context, stack, player, checker);
        int finalUnitPrice = Math.max(0, (int) Math.floor(baseUnitPrice * multiplier));
        int totalPrice = finalUnitPrice * count;
        return new SellQuote(baseUnitPrice, finalUnitPrice, count, totalPrice, multiplier, context, true);
    }

    private static double resolveMultiplier(SellContext context, ItemStack stack, ServerPlayer player,
                                            ProfessionChecker checker) {
        String typeKey = context.itemTypeKey();
        double multiplier = 1.0;
        boolean isCrop = CROP_TYPES.contains(typeKey);
        boolean isAnimalProduct = ANIMAL_PRODUCT_TYPES.contains(typeKey);
        boolean isArtisan = ARTISAN_TYPES.contains(typeKey);
        boolean isFish = FISH_TYPES.contains(typeKey) || isInTag(stack, ModTags.Items.FISHES);
        // SDV Object.getPriceAfterMultipliers 对 PreserveType.SmokedFish 额外触发
        // Fisher/Angler 加成（与 Category=-4 鱼并行），与 Artisan 1.4× 乘法叠加。
        boolean isSmokedFish = stack != null && stack.getItem() instanceof SmokedFishItem;
        boolean isMetalBar = METAL_BAR_TYPES.contains(typeKey) || isInTag(stack, ModTags.Items.BLACKSMITH_PRICE_ITEMS);
        boolean isGem = GEM_TYPES.contains(typeKey) || isInTag(stack, ModTags.Items.GEMOLOGIST_PRICE_ITEMS);
        boolean isTapperProduct = TAPPER_TYPES.contains(typeKey) || isInTag(stack, ModTags.Items.TAPPER_PRICE_ITEMS);

        if (isCrop && checker.hasProfession(ProfessionType.TILLER)) {
            multiplier *= 1.10;
        }

        if (isAnimalProduct && checker.hasProfession(ProfessionType.RANCHER)) {
            multiplier *= 1.20;
        }

        if (isArtisan && checker.hasProfession(ProfessionType.ARTISAN)) {
            multiplier *= 1.40;
        }

        if (isFish) {
            multiplier *= getFishingMultiplier(checker);
        } else if (isSmokedFish) {
            // 熏鱼走 Artisan 分支拿 1.4×，再额外叠加 Fisher/Angler（SDV 显式 SmokedFish 例外）
            multiplier *= getFishingMultiplier(checker);
        }

        if (isMetalBar && checker.hasProfession(ProfessionType.BLACKSMITH)) {
            multiplier *= 1.50;
        }

        if (isGem && checker.hasProfession(ProfessionType.GEMOLOGIST)) {
            multiplier *= 1.30;
        }

        if (isTapperProduct && checker.hasProfession(ProfessionType.TAPPER)) {
            multiplier *= 1.25;
        }

        for (ProfessionType profession : ProfessionType.values()) {
            if (checker.hasProfession(profession)) {
                multiplier = ProfessionData.applyEffect(profession,
                        StardewProfessionEffectHandlers.SELL_PRICE_MULTIPLIER,
                        player, stack, multiplier);
            }
        }

        return multiplier;
    }

    private static double getFishingMultiplier(ProfessionChecker checker) {
        if (checker.hasProfession(ProfessionType.ANGLER)) {
            return 1.50;
        }
        if (checker.hasProfession(ProfessionType.FISHER)) {
            return 1.25;
        }
        return 1.0;
    }

    private static boolean hasProfession(ServerPlayer player, ProfessionType profession) {
        return PlayerStardewDataAPI.hasProfession(player, profession);
    }

    private static boolean hasProfession(PlayerStardewData data, ProfessionType profession) {
        return data != null && data.hasProfession(profession);
    }

    @SuppressWarnings("null")
    private static boolean isInTag(ItemStack stack, TagKey<Item> tag) {
        return stack.is(tag);
    }

    @FunctionalInterface
    private interface ProfessionChecker {
        boolean hasProfession(ProfessionType profession);
    }
}
