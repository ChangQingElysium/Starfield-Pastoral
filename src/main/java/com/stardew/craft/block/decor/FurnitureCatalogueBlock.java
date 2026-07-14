package com.stardew.craft.block.decor;

import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.shop.ShopItemEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Furniture Catalogue — SDV parity (ID 1226).
 * When placed and right-clicked, opens a free shop listing ALL furniture-type
 * items plus wallpaper and flooring blocks.
 */
@SuppressWarnings("null")
public class FurnitureCatalogueBlock extends MapDecorStaticBlock {

    public static final String SHOP_ID = "FurnitureCatalogue";

    public FurnitureCatalogueBlock(Properties properties) {
        super(properties, "stardewcraft:decor/common/furniture_catalogue");
    }

    @Override
    protected InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
            @Nonnull BlockPos pos, @Nonnull Player player, @Nonnull BlockHitResult hit) {
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) return InteractionResult.PASS;

        if (!level.isClientSide && player instanceof ServerPlayer sp) {
            openFurnitureCatalogue(sp);
        }
        return InteractionResult.SUCCESS;
    }

    private static void openFurnitureCatalogue(ServerPlayer player) {
        List<ShopItemEntry> items = buildCatalogueItems();
        int money = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        OpenShopScreenPayload payload = new OpenShopScreenPayload(
                SHOP_ID, money, items,
                "", // no NPC owner
                "",  // no dialogue
                new ArrayList<>() // nothing can be sold here
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Dynamically enumerates all items with typeKey "stardewcraft.type.furniture"
     * plus wallpaper_block and flooring_block.  All items are free (price 0)
     * with unlimited stock.
     */
    public static List<ShopItemEntry> buildCatalogueItems() {
        List<ShopItemEntry> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String typeKey = StardewItemDataApi.getTypeKey(new net.minecraft.world.item.ItemStack(item));
            if ("stardewcraft.type.furniture".equals(typeKey)) {
                ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
                items.add(new ShopItemEntry(
                        id.toString(), "", "", 0, Integer.MAX_VALUE,
                        null, 0, Set.of(), 1, 0, null, -1, 0, 1));
            }
        }
        // Add wallpaper and flooring blocks (type "utility" but belong in catalogue)
        addIfExists(items, "stardewcraft:wallpaper_block");
        addIfExists(items, "stardewcraft:flooring_block");
        return items;
    }

    private static void addIfExists(List<ShopItemEntry> items, String itemId) {
        try {
            ResourceLocation rl = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(rl);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                items.add(new ShopItemEntry(
                        itemId, "", "", 0, Integer.MAX_VALUE,
                        null, 0, Set.of(), 1, 0, null, -1, 0, 1));
            }
        } catch (Exception ignored) {
        }
    }
}
