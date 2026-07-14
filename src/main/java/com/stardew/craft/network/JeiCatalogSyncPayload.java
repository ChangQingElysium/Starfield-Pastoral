package com.stardew.craft.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.query.StardewItemQueryContext;
import com.stardew.craft.client.ClientJeiCatalog;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.shop.GeodeDropData;
import com.stardew.craft.shop.ShopItemEntry;
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

/** S→C: display-only shop and custom-geode catalog for the optional JEI integration. */
public record JeiCatalogSyncPayload(List<ShopEntry> shops, List<GeodeEntry> geodes)
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
                    return new JeiCatalogSyncPayload(shops, geodes);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, JeiCatalogSyncPayload payload) {
                    writeCount(buf, payload.shops.size());
                    for (ShopEntry entry : payload.shops) writeShop(buf, entry);
                    writeCount(buf, payload.geodes.size());
                    for (GeodeEntry entry : payload.geodes) writeGeode(buf, entry);
                }
            };

    public JeiCatalogSyncPayload {
        shops = shops == null ? List.of() : List.copyOf(shops);
        geodes = geodes == null ? List.of() : List.copyOf(geodes);
        if (shops.size() > MAX_ENTRIES || geodes.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("JEI catalog exceeds " + MAX_ENTRIES + " entries");
        }
    }

    public static JeiCatalogSyncPayload current(ServerPlayer player) {
        return new JeiCatalogSyncPayload(buildShops(), buildCustomGeodes(player));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(JeiCatalogSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientJeiCatalog.replace(
                    payload.shops.stream().map(ShopEntry::toClient).toList(),
                    payload.geodes.stream().map(GeodeEntry::toClient).toList());
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
                result.add(new ShopEntry(stack, shopId, entry.price(), entry.stock(),
                        entry.seasons(), entry.minYear(), recipe));
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

    private static void writeShop(RegistryFriendlyByteBuf buf, ShopEntry entry) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.item);
        buf.writeUtf(entry.shopId, 512);
        buf.writeVarInt(entry.price);
        buf.writeVarInt(entry.stock);
        int seasonMask = 0;
        for (int season : entry.seasons) if (season >= 0 && season < 4) seasonMask |= 1 << season;
        buf.writeByte(seasonMask);
        buf.writeVarInt(entry.minYear);
        buf.writeBoolean(entry.recipe);
    }

    private static ShopEntry readShop(RegistryFriendlyByteBuf buf) {
        ItemStack item = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        String shopId = buf.readUtf(512);
        int price = buf.readVarInt();
        int stock = buf.readVarInt();
        int seasonMask = buf.readUnsignedByte();
        Set<Integer> seasons = new LinkedHashSet<>();
        for (int season = 0; season < 4; season++) if ((seasonMask & (1 << season)) != 0) seasons.add(season);
        return new ShopEntry(item, shopId, price, stock, seasons, buf.readVarInt(), buf.readBoolean());
    }

    private static void writeGeode(RegistryFriendlyByteBuf buf, GeodeEntry entry) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.geode);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, entry.output);
    }

    private static GeodeEntry readGeode(RegistryFriendlyByteBuf buf) {
        return new GeodeEntry(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
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

    public record ShopEntry(ItemStack item, String shopId, int price, int stock,
                            Set<Integer> seasons, int minYear, boolean recipe) {
        public ShopEntry {
            item = item == null ? ItemStack.EMPTY : item.copy();
            shopId = shopId == null ? "" : shopId;
            seasons = seasons == null ? Set.of() : Set.copyOf(seasons);
        }

        @Override
        public ItemStack item() {
            return item.copy();
        }

        ClientJeiCatalog.ShopEntry toClient() {
            return new ClientJeiCatalog.ShopEntry(item, shopId, price, stock, seasons, minYear, recipe);
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
