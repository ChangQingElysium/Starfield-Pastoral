package com.stardew.craft.item.tool;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.network.payload.RouteEditorSyncPayload;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class RouteEditorWandItem extends Item implements com.stardew.craft.item.IStardewItem {
    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.tool";
    }

    private static final String TAG_ROUTE_ID = "RouteId";
    private static final String TAG_POINTS = "RoutePoints";
    private static final String DEFAULT_ROUTE_ID = "spirit_eve_maze_exit";

    public RouteEditorWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(@Nonnull UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        if (player.isShiftKeyDown()) {
            undoLastPoint(stack);
            sync(serverPlayer, stack, false);
            serverPlayer.displayClientMessage(Component.translatable("stardewcraft.route_editor.undo", getPoints(stack).size()), true);
            return InteractionResult.CONSUME;
        }

        BlockPos point = context.getClickedPos().relative(context.getClickedFace()).immutable();
        addPoint(stack, point);
        sync(serverPlayer, stack, false);
        serverPlayer.displayClientMessage(Component.translatable("stardewcraft.route_editor.added", point.getX(), point.getY(), point.getZ(), getPoints(stack).size()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            sync(serverPlayer, stack, true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static String getRouteId(ItemStack stack) {
        CompoundTag tag = PortItemData.getOrDefault(stack, DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String id = tag.getString(TAG_ROUTE_ID).trim();
        return id.isEmpty() ? DEFAULT_ROUTE_ID : id;
    }

    public static void setRouteId(ItemStack stack, String routeId) {
        String clean = routeId == null ? "" : routeId.trim();
        if (clean.isEmpty()) {
            clean = DEFAULT_ROUTE_ID;
        }
        if (clean.length() > 96) {
            clean = clean.substring(0, 96);
        }
        CompoundTag tag = PortItemData.getOrDefault(stack, DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(TAG_ROUTE_ID, clean);
        PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static List<BlockPos> getPoints(ItemStack stack) {
        CompoundTag tag = PortItemData.getOrDefault(stack, DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        long[] encoded = tag.getLongArray(TAG_POINTS);
        List<BlockPos> points = new ArrayList<>(encoded.length);
        for (long value : encoded) {
            points.add(BlockPos.of(value).immutable());
        }
        return points;
    }

    public static void addPoint(ItemStack stack, BlockPos pos) {
        List<BlockPos> points = getPoints(stack);
        points.add(pos.immutable());
        writePoints(stack, points);
    }

    public static void undoLastPoint(ItemStack stack) {
        List<BlockPos> points = getPoints(stack);
        if (!points.isEmpty()) {
            points.remove(points.size() - 1);
            writePoints(stack, points);
        }
    }

    public static void clearPoints(ItemStack stack) {
        writePoints(stack, List.of());
    }

    public static void sync(ServerPlayer player, ItemStack stack, boolean openScreen) {
        PacketDistributor.sendToPlayer(player, new RouteEditorSyncPayload(getRouteId(stack), getPoints(stack), openScreen));
    }

    private static void writePoints(ItemStack stack, List<BlockPos> points) {
        CompoundTag tag = PortItemData.getOrDefault(stack, DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        long[] encoded = new long[points.size()];
        for (int i = 0; i < points.size(); i++) {
            encoded[i] = points.get(i).asLong();
        }
        tag.putLongArray(TAG_POINTS, encoded);
        PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
}
