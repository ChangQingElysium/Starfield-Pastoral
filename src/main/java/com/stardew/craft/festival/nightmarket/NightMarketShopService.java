package com.stardew.craft.festival.nightmarket;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.npc.TravelingCartEntity;
import com.stardew.craft.festival.FestivalService;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public final class NightMarketShopService {
    public static final String DECORATION_BOAT_TARGET_ID = "night_market_decoration_boat";
    public static final String MAGIC_BOAT_TARGET_ID = "night_market_magic_boat";
    public static final String TRAVELING_CART_MARKER_TAG = "stardewcraft_night_market_traveling_cart";
    public static final String TRAVELING_CART_DIALOGUE = "stardewcraft.shop.night_market.traveling_cart.dialogue";

    public static final BlockPos DECORATION_BOAT_BOTTOM_POS = new BlockPos(47, 60, 148);
    public static final BlockPos DECORATION_BOAT_TOP_POS = new BlockPos(47, 61, 148);
    public static final BlockPos TRAVELING_CART_POS = new BlockPos(78, 60, 135);
    public static final BlockPos MAGIC_BOAT_POS = new BlockPos(92, 61, 154);

    private static final String DECORATION_BOAT_MARKER_TAG = "sdv_festival_marker:night_market_decoration_boat";
    private static final String MAGIC_BOAT_MARKER_TAG = "sdv_festival_marker:night_market_magic_boat";
    private static final String DECORATION_BOAT_SHOP_ID = "Festival_NightMarket_DecorationBoat";
    private static final String MAGIC_BOAT_SHOP_ID_PREFIX = "Festival_NightMarket_MagicBoat_Day";
    private static final float TRAVELING_CART_FACING_YAW = 90.0F;

    private NightMarketShopService() {
    }

    public static void install(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        installInteractionBlock(level, DECORATION_BOAT_BOTTOM_POS,
            DECORATION_BOAT_TARGET_ID, DECORATION_BOAT_MARKER_TAG);
        installInteractionBlock(level, DECORATION_BOAT_TOP_POS,
            DECORATION_BOAT_TARGET_ID, DECORATION_BOAT_MARKER_TAG);
        installInteractionBlock(level, MAGIC_BOAT_POS, MAGIC_BOAT_TARGET_ID, MAGIC_BOAT_MARKER_TAG);
        spawnTravelingCart(level);
    }

    public static void cleanup(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }
        removeInteractionBlock(level, DECORATION_BOAT_BOTTOM_POS, DECORATION_BOAT_TARGET_ID);
        removeInteractionBlock(level, DECORATION_BOAT_TOP_POS, DECORATION_BOAT_TARGET_ID);
        removeInteractionBlock(level, MAGIC_BOAT_POS, MAGIC_BOAT_TARGET_ID);
        removeTravelingCart(level);
    }

    public static void openDecorationBoat(ServerPlayer player) {
        openShop(player, DECORATION_BOAT_SHOP_ID);
    }

    public static void openMagicBoat(ServerPlayer player) {
        int festivalDay = Math.max(1, Math.min(3,
            FestivalService.getDayOfPassiveFestival(NightMarketPainterService.FESTIVAL_ID)));
        openShop(player, MAGIC_BOAT_SHOP_ID_PREFIX + festivalDay);
    }

    public static boolean isNightMarketOpen() {
        return FestivalService.isPassiveFestivalOpen(NightMarketPainterService.FESTIVAL_ID);
    }

    private static void openShop(ServerPlayer player, String shopId) {
        if (player == null || !isStardewLevel(player.serverLevel()) || !isNightMarketOpen()) {
            return;
        }
        ShopRegistry.ShopDefinition shop = ShopRegistry.get(shopId);
        if (shop == null) {
            return;
        }

        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer(shopId, shop, player);
        PacketDistributor.sendToPlayer(player, new OpenShopScreenPayload(
            shopId,
            PlayerStardewDataAPI.getMoney(player),
            items,
            shop.ownerNpcId(),
            shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        ));
    }

    private static void installInteractionBlock(
            ServerLevel level,
            BlockPos pos,
            String targetId,
            String markerTag) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && targetId.equals(blockEntity.getTargetId())) {
            return;
        }
        level.setBlock(pos, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity) {
            blockEntity.configure(targetId, markerTag);
        }
    }

    private static void removeInteractionBlock(ServerLevel level, BlockPos pos, String targetId) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && targetId.equals(blockEntity.getTargetId())) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    private static void spawnTravelingCart(ServerLevel level) {
        if (hasTravelingCart(level)) {
            return;
        }
        removeTravelingCart(level);
        TravelingCartEntity cart = ModEntities.TRAVELING_CART.get().create(level);
        if (cart == null) {
            return;
        }
        cart.moveTo(TRAVELING_CART_POS.getX(), TRAVELING_CART_POS.getY(), TRAVELING_CART_POS.getZ(),
            TRAVELING_CART_FACING_YAW, 0.0F);
        cart.setYHeadRot(TRAVELING_CART_FACING_YAW);
        cart.setYBodyRot(TRAVELING_CART_FACING_YAW);
        cart.setNoAi(true);
        cart.setInvulnerable(true);
        cart.setPersistenceRequired();
        cart.setSilent(true);
        cart.setCustomName(Component.translatable("entity.stardewcraft.traveling_cart"));
        cart.setCustomNameVisible(false);
        cart.addTag(TRAVELING_CART_MARKER_TAG);
        level.addFreshEntity(cart);
    }

    private static boolean hasTravelingCart(ServerLevel level) {
        return !level.getEntitiesOfClass(TravelingCartEntity.class, travelingCartScanBox(),
            entity -> entity.getTags().contains(TRAVELING_CART_MARKER_TAG)).isEmpty();
    }

    private static void removeTravelingCart(ServerLevel level) {
        level.getEntitiesOfClass(TravelingCartEntity.class, travelingCartScanBox(),
            entity -> entity.getTags().contains(TRAVELING_CART_MARKER_TAG))
            .forEach(Entity::discard);
    }

    private static AABB travelingCartScanBox() {
        return new AABB(TRAVELING_CART_POS).inflate(4.0D);
    }

    private static boolean isStardewLevel(ServerLevel level) {
        return level != null && ModDimensions.STARDEW_VALLEY.equals(level.dimension());
    }
}
