package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.inventory.InventoryTrashPolicy;
import com.stardew.craft.item.tool.FishingRodItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.LinkedHashSet;
import java.util.Set;

@SuppressWarnings("null")
public record CraftingMenuInventoryActionPayload(int action, int slotIndex, boolean rightClick, int[] slots) implements CustomPacketPayload {

    public static final int ACTION_CLICK_SLOT = 0;
    public static final int ACTION_TRASH_CARRIED = 1;
    public static final int ACTION_DROP_CARRIED = 2;
    public static final int ACTION_SHIFT_CLICK_SLOT = 3;
    public static final int ACTION_DRAG_DISTRIBUTE = 4;
    public static final int ACTION_DOUBLE_CLICK_COLLECT = 5;
    public static final int ACTION_SHIFT_RIGHT_CLICK_SLOT = 6;

    public CraftingMenuInventoryActionPayload(int action, int slotIndex, boolean rightClick) {
        this(action, slotIndex, rightClick, new int[0]);
    }

    @SuppressWarnings("null")
    public static final Type<CraftingMenuInventoryActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "crafting_menu_inventory_action"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, CraftingMenuInventoryActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.action);
                buf.writeVarInt(payload.slotIndex);
                buf.writeBoolean(payload.rightClick);
                writeSlotArray(buf, payload.slots);
            },
            buf -> new CraftingMenuInventoryActionPayload(buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), readSlotArray(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    @SuppressWarnings("null")
    public static void handle(CraftingMenuInventoryActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            switch (payload.action) {
                case ACTION_CLICK_SLOT -> handleClickSlot(player, payload.slotIndex, payload.rightClick, false);
                case ACTION_TRASH_CARRIED -> trashCarried(player);
                case ACTION_DROP_CARRIED -> dropCarried(player);
                case ACTION_SHIFT_CLICK_SLOT -> handleShiftClickSlot(player, payload.slotIndex);
                case ACTION_DRAG_DISTRIBUTE -> handleDragDistribute(player, payload.slots, payload.rightClick);
                case ACTION_DOUBLE_CLICK_COLLECT -> handleDoubleClickCollect(player, payload.slotIndex);
                case ACTION_SHIFT_RIGHT_CLICK_SLOT -> handleClickSlot(player, payload.slotIndex, true, true);
                default -> {
                }
            }
        });
    }

    private static void writeSlotArray(FriendlyByteBuf buf, int[] slots) {
        int[] safeSlots = slots == null ? new int[0] : slots;
        buf.writeVarInt(Math.min(safeSlots.length, 36));
        for (int i = 0; i < safeSlots.length && i < 36; i++) {
            buf.writeVarInt(safeSlots[i]);
        }
    }

    private static int[] readSlotArray(FriendlyByteBuf buf) {
        int length = buf.readVarInt();
        if (length < 0 || length > 36) {
            throw new IllegalArgumentException("Invalid inventory drag slot count: " + length);
        }
        int[] result = new int[length];
        for (int i = 0; i < length; i++) {
            result[i] = buf.readVarInt();
        }
        return result;
    }

    private static void handleClickSlot(ServerPlayer player, int slotIndex, boolean rightClick, boolean splitStack) {
        Inventory inv = player.getInventory();
        if (slotIndex < 0 || slotIndex >= inv.items.size()) {
            return;
        }

        ItemStack slotStack = inv.items.get(slotIndex);
        ItemStack carried = player.containerMenu.getCarried();

        if (tryHandleFishingRodAttachment(player, inv, slotIndex, slotStack, carried, rightClick)) {
            syncInventory(player);
            return;
        }

        if (!rightClick) {
            if (carried.isEmpty()) {
                if (slotStack.isEmpty()) {
                    return;
                }
                player.containerMenu.setCarried(slotStack.copy());
                inv.setItem(slotIndex, ItemStack.EMPTY);
            } else {
                if (slotStack.isEmpty()) {
                    inv.setItem(slotIndex, carried.copy());
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                } else if (ItemStack.isSameItemSameComponents(slotStack, carried)) {
                    int max = Math.min(slotStack.getMaxStackSize(), inv.getMaxStackSize());
                    int space = Math.max(0, max - slotStack.getCount());
                    if (space > 0) {
                        int move = Math.min(space, carried.getCount());
                        slotStack.grow(move);
                        carried.shrink(move);
                        if (carried.isEmpty()) {
                            player.containerMenu.setCarried(ItemStack.EMPTY);
                        } else {
                            player.containerMenu.setCarried(carried);
                        }
                    } else {
                        inv.setItem(slotIndex, carried.copy());
                        player.containerMenu.setCarried(slotStack.copy());
                    }
                } else {
                    inv.setItem(slotIndex, carried.copy());
                    player.containerMenu.setCarried(slotStack.copy());
                }
            }
        } else {
            if (carried.isEmpty()) {
                if (slotStack.isEmpty()) {
                    return;
                }
                int take = splitStack ? (slotStack.getCount() + 1) / 2 : 1;
                ItemStack picked = slotStack.copyWithCount(take);
                slotStack.shrink(take);
                inv.setItem(slotIndex, slotStack.isEmpty() ? ItemStack.EMPTY : slotStack);
                player.containerMenu.setCarried(picked);
            } else {
                if (slotStack.isEmpty()) {
                    inv.setItem(slotIndex, carried.copyWithCount(1));
                    carried.shrink(1);
                    player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                } else if (ItemStack.isSameItemSameComponents(slotStack, carried) && slotStack.getCount() < slotStack.getMaxStackSize()) {
                    slotStack.grow(1);
                    carried.shrink(1);
                    player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
                } else {
                    inv.setItem(slotIndex, carried.copy());
                    player.containerMenu.setCarried(slotStack.copy());
                }
            }
        }

        syncInventory(player);
    }

    private static boolean tryHandleFishingRodAttachment(ServerPlayer player, Inventory inv, int slotIndex,
                                                         ItemStack slotStack, ItemStack carried, boolean rightClick) {
        if (slotStack.getItem() instanceof FishingRodItem rod) {
            if (carried.isEmpty()) {
                if (!rightClick || !rod.hasAttachmentSlots()) {
                    return false;
                }
                ItemStack popped = rod.popOneAttachment(slotStack);
                if (!popped.isEmpty()) {
                    player.containerMenu.setCarried(popped);
                }
                return true;
            }
            if (!rightClick && !rod.canAcceptAttachment(carried)) {
                return false;
            }
            return rod.tryInsertAttachment(slotStack, carried, replacement -> player.containerMenu.setCarried(replacement));
        }

        if (carried.getItem() instanceof FishingRodItem rod) {
            if (slotStack.isEmpty()) {
                if (!rightClick || !rod.hasAttachmentSlots()) {
                    return false;
                }
                ItemStack popped = rod.popOneAttachment(carried);
                if (popped.isEmpty()) {
                    return false;
                }
                inv.setItem(slotIndex, popped);
                return true;
            }
            if (!rightClick && !rod.canAcceptAttachment(slotStack)) {
                return false;
            }
            return rod.tryInsertAttachment(carried, slotStack, replacement -> inv.setItem(slotIndex, replacement));
        }

        return false;
    }

    private static void handleShiftClickSlot(ServerPlayer player, int slotIndex) {
        Inventory inv = player.getInventory();
        if (!validSlot(inv, slotIndex)) {
            return;
        }

        ItemStack stack = inv.items.get(slotIndex);
        if (stack.isEmpty()) {
            return;
        }

        if (slotIndex < 9) {
            moveStackToRange(inv, stack, 9, inv.items.size());
        } else {
            moveStackToRange(inv, stack, 0, 9);
        }

        if (stack.isEmpty()) {
            inv.setItem(slotIndex, ItemStack.EMPTY);
        }
        syncInventory(player);
    }

    private static void handleDragDistribute(ServerPlayer player, int[] rawSlots, boolean rightClick) {
        Inventory inv = player.getInventory();
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty() || rawSlots == null || rawSlots.length == 0) {
            return;
        }

        Set<Integer> targets = uniqueValidTargets(inv, rawSlots, carried);
        if (targets.isEmpty()) {
            return;
        }

        if (rightClick) {
            for (int slot : targets) {
                if (carried.isEmpty()) {
                    break;
                }
                addCarriedToSlot(inv, slot, carried, 1);
            }
        } else {
            int remainingSlots = targets.size();
            for (int slot : targets) {
                if (carried.isEmpty()) {
                    break;
                }
                int amount = Math.max(1, carried.getCount() / remainingSlots);
                addCarriedToSlot(inv, slot, carried, amount);
                remainingSlots--;
            }
        }

        player.containerMenu.setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        syncInventory(player);
    }

    private static void handleDoubleClickCollect(ServerPlayer player, int slotIndex) {
        Inventory inv = player.getInventory();
        if (!validSlot(inv, slotIndex)) {
            return;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            ItemStack clicked = inv.items.get(slotIndex);
            if (clicked.isEmpty()) {
                return;
            }
            carried = clicked.copy();
            inv.setItem(slotIndex, ItemStack.EMPTY);
            player.containerMenu.setCarried(carried);
        } else {
            ItemStack clicked = inv.items.get(slotIndex);
            if (!clicked.isEmpty() && !ItemStack.isSameItemSameComponents(clicked, carried)) {
                return;
            }
        }

        int max = Math.min(carried.getMaxStackSize(), inv.getMaxStackSize());
        for (int i = 0; i < inv.items.size() && carried.getCount() < max; i++) {
            ItemStack slotStack = inv.items.get(i);
            if (slotStack.isEmpty() || !ItemStack.isSameItemSameComponents(slotStack, carried)) {
                continue;
            }
            int move = Math.min(max - carried.getCount(), slotStack.getCount());
            carried.grow(move);
            slotStack.shrink(move);
            if (slotStack.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }

        player.containerMenu.setCarried(carried);
        syncInventory(player);
    }

    private static void trashCarried(ServerPlayer player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (!InventoryTrashPolicy.canTrash(carried)) {
            return;
        }
        player.containerMenu.setCarried(ItemStack.EMPTY);
        syncInventory(player);
    }

    private static void dropCarried(ServerPlayer player) {
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.isEmpty()) {
            return;
        }
        player.drop(carried.copy(), false);
        player.containerMenu.setCarried(ItemStack.EMPTY);
        syncInventory(player);
    }

    private static boolean validSlot(Inventory inv, int slotIndex) {
        return slotIndex >= 0 && slotIndex < inv.items.size();
    }

    private static void moveStackToRange(Inventory inv, ItemStack source, int startInclusive, int endExclusive) {
        for (int i = startInclusive; i < endExclusive && !source.isEmpty(); i++) {
            ItemStack target = inv.items.get(i);
            if (target.isEmpty() || !ItemStack.isSameItemSameComponents(target, source)) {
                continue;
            }
            int max = Math.min(target.getMaxStackSize(), inv.getMaxStackSize());
            int space = Math.max(0, max - target.getCount());
            if (space <= 0) {
                continue;
            }
            int move = Math.min(space, source.getCount());
            target.grow(move);
            source.shrink(move);
        }

        for (int i = startInclusive; i < endExclusive && !source.isEmpty(); i++) {
            ItemStack target = inv.items.get(i);
            if (!target.isEmpty()) {
                continue;
            }
            int move = Math.min(source.getCount(), Math.min(source.getMaxStackSize(), inv.getMaxStackSize()));
            inv.setItem(i, source.copyWithCount(move));
            source.shrink(move);
        }
    }

    private static Set<Integer> uniqueValidTargets(Inventory inv, int[] rawSlots, ItemStack carried) {
        Set<Integer> result = new LinkedHashSet<>();
        for (int slot : rawSlots) {
            if (!validSlot(inv, slot)) {
                continue;
            }
            ItemStack slotStack = inv.items.get(slot);
            if (slotStack.isEmpty() || (ItemStack.isSameItemSameComponents(slotStack, carried)
                    && slotStack.getCount() < Math.min(slotStack.getMaxStackSize(), inv.getMaxStackSize()))) {
                result.add(slot);
            }
        }
        return result;
    }

    private static void addCarriedToSlot(Inventory inv, int slot, ItemStack carried, int amount) {
        ItemStack slotStack = inv.items.get(slot);
        int max = Math.min(carried.getMaxStackSize(), inv.getMaxStackSize());
        int space = slotStack.isEmpty() ? max : Math.max(0, max - slotStack.getCount());
        int move = Math.min(Math.min(amount, carried.getCount()), space);
        if (move <= 0) {
            return;
        }

        if (slotStack.isEmpty()) {
            inv.setItem(slot, carried.copyWithCount(move));
        } else {
            slotStack.grow(move);
        }
        carried.shrink(move);
    }

    private static void syncInventory(ServerPlayer player) {
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
        player.connection.send(new ClientboundContainerSetSlotPacket(
                -1, player.containerMenu.getStateId(), -1, player.containerMenu.getCarried()));
    }
}
