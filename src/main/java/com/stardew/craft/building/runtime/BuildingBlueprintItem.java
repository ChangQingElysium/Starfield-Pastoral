package com.stardew.craft.building.runtime;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

public final class BuildingBlueprintItem extends com.stardew.craft.item.SimpleStardewItem {
    public static final String PERMIT = "BuildingPermit";
    private static final String PREVIEW_DEPTH = "BlueprintPreviewDepth";
    private static final java.util.Map<UUID, Long> LAST_USE = new java.util.WeakHashMap<>();
    public static CompoundTag draft(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    public static Direction facing(ItemStack stack) {
        Direction value = Direction.byName(draft(stack).getString("DraftFacing"));
        return value == null || value.getAxis().isVertical() ? Direction.SOUTH : value;
    }
    public static Direction facing(ItemStack stack, Player player) {
        return draft(stack).contains("DraftFacing") ? facing(stack) : player.getDirection().getOpposite();
    }
    /** Cursor grips the front-left corner; stored anchors remain the template's northwest corner. */
    public static BlockPos gripOffset(net.minecraft.resources.ResourceLocation family, Direction facing) {
        return new BlockPos(0,0,PrefabDefinitions.get(family).reservation().maxExclusive().getZ()).rotate(PrefabDefinitions.rotation(facing));
    }
    public static BlockPos targetAnchor(ItemStack stack, BlockPos ground, Direction facing) {
        if (draft(stack).getBoolean("MoveSelf")) return ground.above(1 - BlockPos.of(draft(stack).getLong("MoveMin")).getY());
        var item=(BuildingBlueprintItem)stack.getItem();
        // The clicked ground cell must lie inside the front-left corner, not beyond its vertex.
        return targetAnchor(ground, facing, PrefabDefinitions.get(item.family()).reservation().maxExclusive().getZ());
    }
    /** Remote clients have no server prefab table. Wait for the held item's synced geometry. */
    @org.jetbrains.annotations.Nullable
    public static BlockPos previewTargetAnchor(ItemStack stack, BlockPos ground, Direction facing) {
        var tag = draft(stack);
        if (tag.getBoolean("MoveSelf")) return ground.above(1 - BlockPos.of(tag.getLong("MoveMin")).getY());
        if (!tag.contains(PREVIEW_DEPTH, net.minecraft.nbt.Tag.TAG_INT)) return null;
        return targetAnchor(ground, facing, tag.getInt(PREVIEW_DEPTH));
    }
    private static BlockPos targetAnchor(BlockPos ground, Direction facing, int depth) {
        var frontCell = new BlockPos(0, 0, depth - 1);
        return ground.subtract(PrefabDefinitions.rotateCell(frontCell, PrefabDefinitions.rotation(facing)));
    }
    @Override public void inventoryTick(ItemStack stack,Level level,net.minecraft.world.entity.Entity entity,int slot,boolean selected) {
        if (entity instanceof ServerPlayer player) {
            // The world transfer is committed before the hand interaction returns.  If an
            // inventory restore, disconnect or crash leaves that document behind, its saved
            // revision can no longer move the building and would otherwise pin endless ghosts.
            if (isMove(stack) && moving(player.serverLevel(), stack) == null) {
                discardMoveDocument(player, stack);
                return;
            }
            BuildingWorldData moveData = BuildingWorldData.peek(player.server);
            if (isMove(stack) && draft(stack).getBoolean("LiftedMove") && moveData != null
                    && moveData.moveLift(draft(stack).getUUID("MoveBuilding")) == null) {
                discardMoveDocument(player, stack);
                return;
            }
            if (!selected && player.getOffhandItem() != stack) return;
            var before = draft(stack); var tag = before.copy();
            if (!tag.contains("DraftFacing")) tag.putString("DraftFacing",player.getDirection().getOpposite().getName());
            // Vanilla inventory synchronization covers initial selection, offhand and reconnects.
            // Refresh after datapack reloads; placement still uses authoritative server definitions.
            if (PrefabDefinitions.available(family)) tag.putInt(PREVIEW_DEPTH, PrefabDefinitions.get(family).reservation().maxExclusive().getZ());
            else tag.remove(PREVIEW_DEPTH);
            if (!tag.equals(before)) {
                stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));player.getInventory().setChanged();
            }
        }
    }
    public static BlockPos pinned(ItemStack stack, Level level) {
        CompoundTag tag = draft(stack);
        return tag.contains("DraftAnchor") && tag.getString("DraftDimension").equals(level.dimension().location().toString())
                ? BlockPos.of(tag.getLong("DraftAnchor")) : null;
    }
    public static void rotate(ServerPlayer player, net.minecraft.world.InteractionHand hand, boolean reverse) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BuildingBlueprintItem item) || !PrefabDefinitions.supported(item.family())) return;
        BuildingDrafts.get(player.server).apply(stack);
        if (pinned(stack, player.level()) != null && !aimsAtPinned(player, stack, item.family())) return;
        CompoundTag tag = draft(stack);
        Direction previous=facing(stack,player), next=reverse?previous.getCounterClockWise():previous.getClockWise();
        if (pinned(stack,player.level())!=null && !tag.getBoolean("MoveSelf"))
            tag.putLong("DraftAnchor",BlockPos.of(tag.getLong("DraftAnchor")).offset(gripOffset(item.family(),previous)).subtract(gripOffset(item.family(),next)).asLong());
        tag.putString("DraftFacing",next.getName());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); BuildingDrafts.get(player.server).write(stack); player.getInventory().setChanged();
    }
    public static boolean aimsAtPinned(Player player, ItemStack stack, net.minecraft.resources.ResourceLocation family) {
        BlockPos anchor = pinned(stack, player.level());
        if (anchor == null || !PrefabDefinitions.supported(family)) return false;
        var bounds = draft(stack).getBoolean("MoveSelf") ? (draft(stack).contains("MoveMax") ? UtilityBuildings.rotateBounds(new BuildingBounds(BlockPos.of(draft(stack).getLong("MoveMin")),BlockPos.of(draft(stack).getLong("MoveMax"))),anchor,PrefabDefinitions.rotation(facing(stack))) : UtilityBuildings.bounds(family,anchor)) : PrefabDefinitions.transform(PrefabDefinitions.get(family).reservation(), anchor, PrefabDefinitions.rotation(facing(stack)));
        return aimsAt(player, BuildingPlacementService.aabb(bounds));
    }
    public static boolean aimsAt(Player player, net.minecraft.world.phys.AABB box) {
        var eye = player.getEyePosition(); var end = eye.add(player.getViewVector(1).scale(player.blockInteractionRange()));
        var point = box.contains(eye) ? java.util.Optional.of(eye) : box.clip(eye, end);
        if (point.isEmpty()) return false;
        var solid = target(player);
        return solid.getType() == HitResult.Type.MISS || eye.distanceToSqr(point.get()) <= eye.distanceToSqr(solid.getLocation()) + 0.01;
    }

    /** Cancelling only changes the held document; terrain, reach and farm permissions are irrelevant. */
    public static void cancel(ServerPlayer player, InteractionHand hand, boolean endMove) {
        var stack=player.getItemInHand(hand);
        if (!(stack.getItem() instanceof BuildingBlueprintItem)) return;
        var drafts=BuildingDrafts.get(player.server);drafts.apply(stack);
        if(endMove && isMove(stack)) { BuildingMoveSession.restoreHeld(player,stack);drafts.consume(stack);stack.shrink(1); }
        else {
            var tag=draft(stack);tag.remove("DraftAnchor");tag.remove("DraftDimension");
            stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));drafts.write(stack);
        }
        player.getInventory().setChanged();drafts.synchronize(player);
    }

    private final net.minecraft.resources.ResourceLocation family;
    public net.minecraft.resources.ResourceLocation family() { return family; }
    public BuildingBlueprintItem(net.minecraft.resources.ResourceLocation family, Properties properties) { super("stardewcraft.type.building", -1, properties.stacksTo(1)); this.family = family; }
    public static void bindMove(ItemStack stack, BuildingRecord record) {
        stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.translatable("building.stardewcraft.move_building").append(" · ").append(record.title()));
        CompoundTag tag = new CompoundTag(); tag.putUUID("DraftId",UUID.randomUUID()); tag.putUUID("MoveBuilding", record.id()); tag.putLong("MoveRevision", record.revision());
        if (record.mode() == BuildingRecord.Mode.SELF_BUILT) {
            var local = UtilityBuildings.moveBounds(record,BlockPos.ZERO,Direction.SOUTH);
            tag.putLong("MoveMin",local.min().asLong());tag.putLong("MoveMax",local.maxExclusive().asLong());
        }
        tag.putBoolean("MoveSelf", record.mode() == BuildingRecord.Mode.SELF_BUILT); tag.putBoolean("LiftedMove",true); tag.putInt("MoveTier", record.tier()); tag.putString("MoveFacing", record.facing().getName()); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static BuildingRecord moving(net.minecraft.server.level.ServerLevel level, ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.hasUUID("MoveBuilding")) return null;
        var record = BuildingWorldData.get(level.getServer()).find(tag.getUUID("MoveBuilding"));
        return record != null && record.revision() == tag.getLong("MoveRevision") ? record : null;
    }
    public static boolean isMove(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().hasUUID("MoveBuilding"); }
    private static void discardMoveDocument(ServerPlayer player, ItemStack stack) {
        BuildingMoveSession.restoreHeld(player,stack);
        BuildingDrafts.get(player.server).consume(stack);
        stack.shrink(1);
        player.getInventory().setChanged();
    }
    /** Remove duplicate documents left by an interrupted/repeated move click. */
    public static void consumeMoveDocuments(ServerPlayer player, UUID buildingId) {
        var held = new java.util.ArrayList<ItemStack>(player.getInventory().items);
        held.add(player.getOffhandItem());
        for (var stack : held) {
            var tag = draft(stack);
            if (!tag.hasUUID("MoveBuilding") || !buildingId.equals(tag.getUUID("MoveBuilding"))) continue;
            BuildingDrafts.get(player.server).consume(stack);
            stack.shrink(1);
        }
        player.getInventory().setChanged();
    }
    public static Direction moveFacing(ItemStack stack) { return Direction.byName(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("MoveFacing")); }
    public static UUID permit(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(PERMIT) ? tag.getUUID(PERMIT) : null;
    }
    public static void bind(ItemStack stack, UUID permit) {
        CompoundTag tag = new CompoundTag(); tag.putUUID(PERMIT, permit); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static BlockHitResult target(Player player) {
        var eye = player.getEyePosition();
        return player.level().clip(new ClipContext(eye, eye.add(player.getViewVector(1).scale(player.blockInteractionRange())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context,
            java.util.List<net.minecraft.network.chat.Component> lines, net.minecraft.world.item.TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable(isMove(stack) ? "building.stardewcraft.move_hint" : "building.stardewcraft.blueprint_hint").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        return place(context.getPlayer(), context.getHand(), context.getItemInHand());
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        return new InteractionResultHolder<>(place(player, hand, stack), stack);
    }
    private InteractionResult place(Player player, InteractionHand hand, ItemStack stack) {
        if (player == null) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        BuildingDrafts.get(serverPlayer.server).apply(stack);
        if (isMove(stack) && moving(serverPlayer.serverLevel(), stack) == null) {
            discardMoveDocument(serverPlayer, stack);
            BuildingPlacementService.message(serverPlayer, "work_stale");
            return InteractionResult.FAIL;
        }
        if(player.isShiftKeyDown()) {
            if(draft(stack).contains("DraftAnchor"))cancel(serverPlayer,hand,false);
            return InteractionResult.CONSUME;
        }
        if (!PrefabDefinitions.supported(family)) { BuildingPlacementService.message(serverPlayer, "asset_error"); return InteractionResult.FAIL; }
        long tick = player.level().getGameTime();
        if (LAST_USE.getOrDefault(player.getUUID(), Long.MIN_VALUE) == tick) return InteractionResult.CONSUME;
        LAST_USE.put(player.getUUID(), tick);
        BlockPos anchor = pinned(stack, player.level());
        if (anchor == null) {
            BlockHitResult hit = target(player);
            if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != Direction.UP) return InteractionResult.FAIL;
            CompoundTag tag = draft(stack); tag.putLong("DraftAnchor", targetAnchor(stack,hit.getBlockPos(),facing(stack,player)).asLong());
            tag.putString("DraftFacing",facing(stack,player).getName());
            tag.putString("DraftDimension", player.level().dimension().location().toString());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); BuildingDrafts.get(serverPlayer.server).write(stack); player.getInventory().setChanged();
            return InteractionResult.CONSUME;
        }
        if (!aimsAtPinned(player, stack, family)) return InteractionResult.CONSUME;
        boolean placed;
        UUID movedId = null;
        if (isMove(stack)) {
            var record = moving(serverPlayer.serverLevel(), stack);
            movedId = record == null ? null : record.id();
            placed = record != null && record.family().equals(family)
                    && BuildingLifecycleService.move(serverPlayer, record, anchor, facing(stack));
            if (record == null) BuildingPlacementService.message(serverPlayer, "work_stale");
        } else placed = BuildingPlacementService.placePrefab(serverPlayer, anchor, facing(stack), permit(stack), family);
        if (placed) {
            if (movedId != null) consumeMoveDocuments(serverPlayer, movedId);
            else consumePlacedBlueprint(serverPlayer, hand, stack);
        }
        return placed ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** Construction has started server-side; consume the document in every game mode. */
    static void consumePlacedBlueprint(ServerPlayer player, InteractionHand hand, ItemStack attempted) {
        // Use the authoritative hand slot instead of trusting UseOnContext's stack reference.
        // Some interaction paths pass a copy; shrinking that copy leaves the real blueprint in
        // the inventory even though the server has already registered the construction.
        UUID document = BuildingDrafts.id(attempted);
        ItemStack held = player.getItemInHand(hand);
        if (held.getItem() instanceof BuildingBlueprintItem && document != null
                && document.equals(BuildingDrafts.id(held))) {
            BuildingDrafts.get(player.server).consume(held);
            player.setItemInHand(hand, ItemStack.EMPTY);
        } else {
            BuildingDrafts.get(player.server).consume(attempted);
            attempted.setCount(0);
        }
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastFullState();
        player.containerMenu.broadcastChanges();
    }
}
