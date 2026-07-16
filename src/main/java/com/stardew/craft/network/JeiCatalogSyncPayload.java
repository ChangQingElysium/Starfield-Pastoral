package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQueryContext;
import com.stardew.craft.client.ClientJeiCatalog;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.fishpond.service.FishPondDataService;
import com.stardew.craft.item.catalog.StardewItemCatalog;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.shop.GeodeDropData;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopConditionDisplayData;
import com.stardew.craft.shop.ShopRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S→C: display-only shop, custom-geode and fish-pond catalog for optional JEI. */
public record JeiCatalogSyncPayload(
        List<ShopEntry> shops,
        List<GeodeEntry> geodes,
        List<FishPondEntry> fishPonds)
        implements CustomPacketPayload {
    private static final int MAX_ENTRIES = 100_000;
    public static final Type<JeiCatalogSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "jei_catalog_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JeiCatalogSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public JeiCatalogSyncPayload decode(RegistryFriendlyByteBuf buf) {
                    int shopCount = readCount(buf);
                    List<ShopEntry> shops = new ArrayList<>(shopCount);
                    for (int i = 0; i < shopCount; i++) shops.add(readShop(buf));
                    int geodeCount = readCount(buf);
                    List<GeodeEntry> geodes = new ArrayList<>(geodeCount);
                    for (int i = 0; i < geodeCount; i++) geodes.add(readGeode(buf));
                    int fishPondCount = readCount(buf);
                    List<FishPondEntry> fishPonds = new ArrayList<>(fishPondCount);
                    for (int i = 0; i < fishPondCount; i++) fishPonds.add(readFishPond(buf));
                    return new JeiCatalogSyncPayload(shops, geodes, fishPonds);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, JeiCatalogSyncPayload payload) {
                    writeCount(buf, payload.shops.size());
                    for (ShopEntry entry : payload.shops) writeShop(buf, entry);
                    writeCount(buf, payload.geodes.size());
                    for (GeodeEntry entry : payload.geodes) writeGeode(buf, entry);
                    writeCount(buf, payload.fishPonds.size());
                    for (FishPondEntry entry : payload.fishPonds) writeFishPond(buf, entry);
                }
            };

    public JeiCatalogSyncPayload {
        shops = shops == null ? List.of() : List.copyOf(shops);
        geodes = geodes == null ? List.of() : List.copyOf(geodes);
        fishPonds = fishPonds == null ? List.of() : List.copyOf(fishPonds);
        if (shops.size() > MAX_ENTRIES || geodes.size() > MAX_ENTRIES || fishPonds.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("JEI catalog exceeds " + MAX_ENTRIES + " entries");
        }
    }

    public JeiCatalogSyncPayload(List<ShopEntry> shops, List<GeodeEntry> geodes) {
        this(shops, geodes, List.of());
    }

    public static JeiCatalogSyncPayload current(ServerPlayer player) {
        return new JeiCatalogSyncPayload(buildShops(), buildCustomGeodes(player), buildFishPonds());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JeiCatalogSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientJeiCatalog.replace(
                    payload.shops.stream().map(ShopEntry::toClient).toList(),
                    payload.geodes.stream().map(GeodeEntry::toClient).toList(),
                    payload.fishPonds.stream().map(FishPondEntry::toClient).toList());
            com.stardew.craft.client.ClientContentRefreshHooks.onSyncedRegistriesChanged();
        });
    }

    private static List<ShopEntry> buildShops() {
        List<ShopEntry> result = new ArrayList<>();
        for (String shopId : ShopRegistry.allShopIds()) {
            ShopRegistry.ShopDefinition definition = ShopRegistry.get(shopId);
            if (definition == null) continue;
            for (ShopItemEntry entry : definition.items()) {
                boolean recipe = entry.itemId().startsWith("recipe:");
                String rawItemId = recipe ? entry.itemId().substring("recipe:".length()) : entry.itemId();
                ItemStack stack = recipe ? resolveRecipeOutput(rawItemId) : resolveItem(rawItemId);
                if (stack.isEmpty() || stack.is(Items.AIR)) continue;
                ItemStack tradeItem = entry.requiresTrade()
                        ? resolveItem(entry.tradeItemId()) : ItemStack.EMPTY;
                List<String> conditionTokens = ShopConditionDisplayData.tokens(entry.availableWhen());
                result.add(new ShopEntry(stack, shopId, definition.ownerNpcId(), entry.price(), entry.stock(),
                        tradeItem, entry.tradeItemCount(), entry.purchaseStack(),
                        entry.seasons(), entry.minYear(), entry.minMineLevel(),
                        entry.mailFlag() != null && !entry.mailFlag().isBlank(),
                        entry.dayOfWeek(), entry.dayOfMonthParity(),
                        !conditionTokens.isEmpty(), conditionTokens, recipe));
            }
        }
        result.sort(Comparator.comparing(ShopEntry::shopId)
                .thenComparing(entry -> BuiltInRegistries.ITEM.getKey(entry.item().getItem()).toString()));
        return List.copyOf(result);
    }

    private static ItemStack resolveRecipeOutput(String recipeId) {
        ItemStack craftingOutput = StardewCraftingRecipeData.getOutputStack(recipeId);
        if (!craftingOutput.isEmpty()) return craftingOutput;
        return VanillaCookingRecipeData.getDefinition(recipeId)
                .map(definition -> resolveItem(definition.output().toString()))
                .orElse(ItemStack.EMPTY);
    }

    private static ItemStack resolveItem(String itemId) {
        ResourceLocation parsed = ResourceLocation.tryParse(itemId);
        if (parsed == null || !BuiltInRegistries.ITEM.containsKey(parsed)) return ItemStack.EMPTY;
        return new ItemStack(BuiltInRegistries.ITEM.get(parsed));
    }

    private static List<GeodeEntry> buildCustomGeodes(ServerPlayer player) {
        List<GeodeEntry> result = new ArrayList<>();
        GeodeDropData.snapshot().definitions().entrySet().stream()
                .sorted(MapEntryComparator.INSTANCE)
                .forEach(definitionEntry -> {
                    ResourceLocation definitionId = definitionEntry.getKey();
                    if (!GeodeDropData.isAvailable(definitionId, player)) return;
                    var definition = definitionEntry.getValue();
                    java.util.Random random = new java.util.Random(definitionId.toString().hashCode());
                    Set<ItemStackKey> seen = new LinkedHashSet<>();
                    for (var weightedEntry : definition.entries()) {
                        List<ItemStack> outputs = StardewItemQueries.resolve(weightedEntry.query(),
                                        StardewItemQueryContext.forPlayer(player, random))
                                .resultOrPartial(message -> StardewCraft.LOGGER.warn(
                                        "Unable to build JEI display for geode {}: {}", definitionId, message))
                                .orElse(List.of());
                        for (ResourceLocation inputId : definition.inputs()) {
                            if (!BuiltInRegistries.ITEM.containsKey(inputId)) continue;
                            ItemStack input = new ItemStack(BuiltInRegistries.ITEM.get(inputId));
                            for (ItemStack output : outputs) {
                                if (output.isEmpty()) continue;
                                ItemStackKey key = new ItemStackKey(inputId,
                                        BuiltInRegistries.ITEM.getKey(output.getItem()), output.getCount());
                                if (seen.add(key)) result.add(new GeodeEntry(input, output));
                            }
                        }
                    }
                });
        return List.copyOf(result);
    }

    private static List<FishPondEntry> buildFishPonds() {
        List<FishPondEntry> result = new ArrayList<>();
        FishPondDataService pondData = FishPondDataService.get();
        for (var fishItem : StardewItemCatalog.visibleItems()) {
            ItemStack fish = new ItemStack(fishItem);
            for (FishPondDataService.DisplayProduction production : pondData.getDisplayProductions(fish)) {
                result.add(new FishPondEntry(
                        fish,
                        production.output(),
                        production.requiredPopulation(),
                        production.outputChance(),
                        production.dailyMinChance(),
                        production.dailyMaxChance(),
                        production.minCount(),
                        production.maxCount(),
                        production.bonusCountPossible()));
            }
        }
        result.sort(Comparator
                .comparing((FishPondEntry entry) -> BuiltInRegistries.ITEM.getKey(entry.fish().getItem()).toString())
                .thenComparing(entry -> BuiltInRegistries.ITEM.getKey(entry.output().getItem()).toString())
                .thenComparingInt(FishPondEntry::requiredPopulation));
        return List.copyOf(result);
    }

    private static void writeShop(RegistryFriendlyByteBuf buf, ShopEntry entry) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.item);
        buf.writeUtf(entry.shopId, 512);
        buf.writeUtf(entry.ownerNpcId, 512);
        buf.writeVarInt(entry.price);
        buf.writeVarInt(entry.stock);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.tradeItem);
        buf.writeVarInt(entry.tradeItemCount);
        buf.writeVarInt(entry.purchaseStack);
        int seasonMask = 0;
        for (int season : entry.seasons) if (season >= 0 && season < 4) seasonMask |= 1 << season;
        buf.writeByte(seasonMask);
        buf.writeVarInt(entry.minYear);
        buf.writeVarInt(entry.minMineLevel);
        buf.writeBoolean(entry.mailRequired);
        buf.writeByte(entry.dayOfWeek);
        buf.writeByte(entry.dayOfMonthParity);
        buf.writeBoolean(entry.conditional);
        writeCount(buf, entry.conditionTokens.size());
        for (String token : entry.conditionTokens) buf.writeUtf(token, 1024);
        buf.writeBoolean(entry.recipe);
    }

    private static ShopEntry readShop(RegistryFriendlyByteBuf buf) {
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        String shopId = buf.readUtf(512);
        String ownerNpcId = buf.readUtf(512);
        int price = buf.readVarInt();
        int stock = buf.readVarInt();
        ItemStack tradeItem = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        int tradeItemCount = buf.readVarInt();
        int purchaseStack = buf.readVarInt();
        int seasonMask = buf.readUnsignedByte();
        Set<Integer> seasons = new LinkedHashSet<>();
        for (int season = 0; season < 4; season++) if ((seasonMask & (1 << season)) != 0) seasons.add(season);
        int minYear = buf.readVarInt();
        int minMineLevel = buf.readVarInt();
        boolean mailRequired = buf.readBoolean();
        int dayOfWeek = buf.readByte();
        int dayOfMonthParity = buf.readByte();
        boolean conditional = buf.readBoolean();
        int conditionCount = readCount(buf);
        List<String> conditionTokens = new ArrayList<>(conditionCount);
        for (int i = 0; i < conditionCount; i++) conditionTokens.add(buf.readUtf(1024));
        boolean recipe = buf.readBoolean();
        return new ShopEntry(item, shopId, ownerNpcId, price, stock, tradeItem, tradeItemCount, purchaseStack,
                seasons, minYear, minMineLevel, mailRequired, dayOfWeek, dayOfMonthParity,
                conditional, conditionTokens, recipe);
    }

    private static void writeGeode(RegistryFriendlyByteBuf buf, GeodeEntry entry) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.geode);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.output);
    }

    private static GeodeEntry readGeode(RegistryFriendlyByteBuf buf) {
        return new GeodeEntry(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
    }

    private static void writeFishPond(RegistryFriendlyByteBuf buf, FishPondEntry entry) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.fish);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.output);
        buf.writeVarInt(entry.requiredPopulation);
        buf.writeDouble(entry.outputChance);
        buf.writeDouble(entry.dailyMinChance);
        buf.writeDouble(entry.dailyMaxChance);
        buf.writeVarInt(entry.minCount);
        buf.writeVarInt(entry.maxCount);
        buf.writeBoolean(entry.bonusCountPossible);
    }

    private static FishPondEntry readFishPond(RegistryFriendlyByteBuf buf) {
        return new FishPondEntry(
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBoolean());
    }

    private static void writeCount(RegistryFriendlyByteBuf buf, int count) {
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid JEI entry count " + count);
        buf.writeVarInt(count);
    }

    private static int readCount(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid JEI entry count " + count);
        return count;
    }

    public record ShopEntry(ItemStack item, String shopId, String ownerNpcId, int price, int stock,
                            ItemStack tradeItem, int tradeItemCount, int purchaseStack,
                            Set<Integer> seasons, int minYear, int minMineLevel,
                            boolean mailRequired, int dayOfWeek, int dayOfMonthParity,
                            boolean conditional, List<String> conditionTokens, boolean recipe) {
        public ShopEntry {
            item = item == null ? ItemStack.EMPTY : item.copy();
            tradeItem = tradeItem == null ? ItemStack.EMPTY : tradeItem.copy();
            shopId = shopId == null ? "" : shopId;
            ownerNpcId = ownerNpcId == null ? "" : ownerNpcId;
            seasons = seasons == null ? Set.of() : Set.copyOf(seasons);
            tradeItemCount = Math.max(0, tradeItemCount);
            purchaseStack = Math.max(1, purchaseStack);
            conditionTokens = conditionTokens == null ? List.of() : List.copyOf(conditionTokens);
        }

        public ShopEntry(ItemStack item, String shopId, int price, int stock,
                         ItemStack tradeItem, int tradeItemCount, int purchaseStack,
                         Set<Integer> seasons, int minYear, int minMineLevel,
                         boolean mailRequired, int dayOfWeek, int dayOfMonthParity,
                         boolean conditional, List<String> conditionTokens, boolean recipe) {
            this(item, shopId, "", price, stock, tradeItem, tradeItemCount, purchaseStack,
                    seasons, minYear, minMineLevel, mailRequired, dayOfWeek,
                    dayOfMonthParity, conditional, conditionTokens, recipe);
        }

        public ShopEntry(ItemStack item, String shopId, int price, int stock,
                         ItemStack tradeItem, int tradeItemCount, int purchaseStack,
                         Set<Integer> seasons, int minYear, int minMineLevel,
                         boolean mailRequired, int dayOfWeek, int dayOfMonthParity,
                         boolean conditional, boolean recipe) {
            this(item, shopId, "", price, stock, tradeItem, tradeItemCount, purchaseStack,
                    seasons, minYear, minMineLevel, mailRequired, dayOfWeek,
                    dayOfMonthParity, conditional,
                    conditional ? List.of("unknown") : List.of(), recipe);
        }

        @Override
        public ItemStack item() {
            return item.copy();
        }

        @Override
        public ItemStack tradeItem() {
            return tradeItem.copy();
        }

        ClientJeiCatalog.ShopEntry toClient() {
            return new ClientJeiCatalog.ShopEntry(item, shopId, ownerNpcId, price, stock,
                    tradeItem, tradeItemCount, purchaseStack, seasons, minYear, minMineLevel,
                    mailRequired, dayOfWeek, dayOfMonthParity, conditional, conditionTokens, recipe);
        }
    }

    public record GeodeEntry(ItemStack geode, ItemStack output) {
        public GeodeEntry {
            geode = geode == null ? ItemStack.EMPTY : geode.copy();
            output = output == null ? ItemStack.EMPTY : output.copy();
        }

        @Override
        public ItemStack geode() {
            return geode.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        ClientJeiCatalog.GeodeEntry toClient() {
            return new ClientJeiCatalog.GeodeEntry(geode, output);
        }
    }

    public record FishPondEntry(
            ItemStack fish,
            ItemStack output,
            int requiredPopulation,
            double outputChance,
            double dailyMinChance,
            double dailyMaxChance,
            int minCount,
            int maxCount,
            boolean bonusCountPossible
    ) {
        public FishPondEntry {
            fish = fish == null ? ItemStack.EMPTY : fish.copy();
            output = output == null ? ItemStack.EMPTY : output.copy();
            requiredPopulation = Math.max(0, requiredPopulation);
            outputChance = clampChance(outputChance);
            dailyMinChance = clampChance(dailyMinChance);
            dailyMaxChance = clampChance(dailyMaxChance);
            minCount = Math.max(1, minCount);
            maxCount = Math.max(minCount, maxCount);
        }

        @Override
        public ItemStack fish() {
            return fish.copy();
        }

        @Override
        public ItemStack output() {
            return output.copy();
        }

        ClientJeiCatalog.FishPondEntry toClient() {
            return new ClientJeiCatalog.FishPondEntry(
                    fish, output, requiredPopulation, outputChance,
                    dailyMinChance, dailyMaxChance, minCount, maxCount, bonusCountPossible);
        }

        private static double clampChance(double value) {
            return Math.max(0.0D, Math.min(1.0D, value));
        }
    }

    private record ItemStackKey(ResourceLocation input, ResourceLocation output, int count) {
    }

    private enum MapEntryComparator implements Comparator<java.util.Map.Entry<ResourceLocation, ?>> {
        INSTANCE;

        @Override
        public int compare(java.util.Map.Entry<ResourceLocation, ?> left,
                           java.util.Map.Entry<ResourceLocation, ?> right) {
            return left.getKey().toString().compareTo(right.getKey().toString());
        }
    }
}
