package com.stardew.craft.pet;

import com.stardew.craft.port.PortEntities;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Bowls reuse building move previews, permission checks and durable transfer journals. */
public final class PetBowlBuildings {
    private PetBowlBuildings() {}
    public static ResourceLocation family(String style) { return new ResourceLocation("stardewcraft:pet_bowl_" + style); }
    public static boolean isBowl(ResourceLocation family) {
        return family.getNamespace().equals("stardewcraft") && java.util.Set.of("pet_bowl_wood", "pet_bowl_stone", "pet_bowl_hay").contains(family.getPath());
    }

    /** Register both newly placed and pre-building-system bowls before they can be mined. */
    public static BuildingRecord ensure(ServerLevel level, BlockPos pos) {
        if (level.dimension() != ModDimensions.STARDEW_VALLEY || !(level.getBlockState(pos).getBlock() instanceof PetBowlBlock block)) return null;
        var owner = com.stardew.craft.farm.FarmInstanceRegistry.get(level.getServer()).getOwnerAt(pos);
        if (owner == null) return null;
        var farm = com.stardew.craft.farm.FarmInstanceRegistry.get(level.getServer()).getFarm(owner);
        var pets = PetWorldData.get(level.getServer());
        if (pets.bowl(pos) == null) pets.bowl(new PetWorldData.Bowl(farm.getInstanceId(), pos.immutable(), block.style, -1, level.canSeeSky(pos.above())));
        var data = BuildingWorldData.get(level.getServer());
        var id = data.occupying(level.dimension().location(), pos);
        var record = id == null ? null : data.find(id);
        if (record != null) return isBowl(record.family()) && record.manager().equals(pos) ? record : null;
        var claim = new BuildingBounds(pos, pos.offset(2, 1, 2));
        var admission = BuildingService.register(level.getServer(), level.dimension().location(), owner, family(block.style),
                BuildingRecord.Mode.SELF_BUILT, pos, pos, Direction.SOUTH, claim);
        if (admission.failure() != BuildingService.Failure.NONE) return null;
        id = admission.buildingId();
        if (data.acceptSelf(id, 0, 1) != BuildingWorldData.Result.SUCCESS) { data.removeSelfBuilt(id); return null; }
        return data.find(id);
    }

    public static boolean beginMove(ServerPlayer player, BlockPos pos) {
        var level = player.serverLevel();
        if (!PortEntities.canInteractWithBlock(player, pos, 1)) return false;
        var record = ensure(level, pos);
        if (record == null) return false;
        var data = BuildingWorldData.get(player.server);
        if (!isBowl(record.family()) || !record.manager().equals(pos) || data.transfer(record.id()) != null
                || !BuildingService.canManage(player, record)) { BuildingPlacementService.message(player, "overlap"); return false; }
        var held = new java.util.ArrayList<ItemStack>(player.getInventory().items);
        held.add(player.getOffhandItem());
        for (var stack : held) {
            if (BuildingBlueprintItem.isMove(stack) && record.equals(BuildingBlueprintItem.moving(level, stack))) {
                BuildingPlacementService.message(player, "move_hint"); return true;
            }
        }
        var document = new ItemStack(PrefabDefinitions.blueprintItem(record.family()));
        BuildingBlueprintItem.bindMove(document, record);
        var delivery = BuildingPurchasePlan.prepare(player.getInventory(), document, java.util.List.of());
        if (delivery == null) { BuildingPlacementService.message(player, "purchase_space"); return false; }
        delivery.apply(player.getInventory());
        BuildingPlacementService.message(player, "move_hint"); return true;
    }

    public static void removeContents(ServerLevel level, BuildingRecord record) {
        if (!isBowl(record.family())) return;
        PetWorldData.get(level.getServer()).removeBowl(record.manager());
        var floors = com.stardew.craft.floor.SurfaceFloorData.get(level);
        for (var pos : BlockPos.betweenClosed(record.claim().min().below(), record.claim().maxInclusive().below()))
            floors.remove(level, pos, false);
    }

    /** New bowls include the four existing surface-floor tiles, without replacing their terrain. */
    public static void floor(ServerLevel level, BuildingRecord record) {
        if (record == null) return;
        var floors = com.stardew.craft.floor.SurfaceFloorData.get(level);
        var type = switch (record.family().getPath()) {
            case "pet_bowl_stone" -> com.stardew.craft.floor.SurfaceFloorType.STONE;
            case "pet_bowl_hay" -> com.stardew.craft.floor.SurfaceFloorType.STRAW;
            default -> com.stardew.craft.floor.SurfaceFloorType.WOOD;
        };
        for (var pos : BlockPos.betweenClosed(record.claim().min().below(), record.claim().maxInclusive().below()))
            if (floors.at(pos) == null && com.stardew.craft.floor.SurfaceFloorItem.supports(level, pos, level.getBlockState(pos)))
                floors.restore(level, pos, new com.stardew.craft.floor.SurfaceFloorData.Cover(type, 0));
    }

    public static void removed(ServerLevel level, BlockPos pos) {
        var data = BuildingWorldData.get(level.getServer());
        var id = data.occupying(level.dimension().location(), pos);
        var record = id == null ? null : data.find(id);
        if (record != null && isBowl(record.family()) && record.manager().equals(pos)) data.demolish(id);
    }

    /** Capture before block callbacks, including moves of a larger building containing a bowl. */
    public static ListTag capture(ServerLevel level, BuildingRecord before, BuildingRecord after) {
        var result = new ListTag();
        if (level.dimension() != ModDimensions.STARDEW_VALLEY || before.tier() != after.tier()) return result;
        var data = PetWorldData.get(level.getServer());
        var bounds = BuildingTransfer.contentBounds(before);
        for (var bowl : data.bowls()) if (bounds.contains(bowl.position()) && bowl.farm().equals(before.farmId())) {
            var row = new CompoundTag(); row.putUUID("Farm", bowl.farm()); row.putLong("From", bowl.position().asLong());
            row.putLong("To", BuildingTransfer.destination(before, after, bowl.position()).asLong());
            row.putString("Style", bowl.style()); row.putInt("Watered", bowl.wateredDay());
            var pets = new ListTag();
            for (var pet : data.forFarm(bowl.farm())) if (bowl.position().equals(pet.bowl)) {
                var entry = new CompoundTag(); entry.putUUID("Id", pet.id); pets.add(entry);
            }
            row.put("Pets", pets); result.add(row);
        }
        return result;
    }

    public static void project(ServerLevel level, ListTag rows) {
        if (rows.isEmpty()) return;
        var data = PetWorldData.get(level.getServer());
        // Clear all source positions first: overlapping moves can reuse another bowl's old cell.
        for (var entry : rows) data.removeBowl(BlockPos.of(((CompoundTag) entry).getLong("From")));
        for (var entry : rows) {
            var row = (CompoundTag) entry; var to = BlockPos.of(row.getLong("To"));
            data.bowl(new PetWorldData.Bowl(row.getUUID("Farm"), to, row.getString("Style"), row.getInt("Watered"), level.canSeeSky(to.above())));
            for (var raw : row.getList("Pets", 10)) {
                var pet = data.find(((CompoundTag) raw).getUUID("Id"));
                if (pet != null && pet.farm.equals(row.getUUID("Farm"))) pet.bowl = to;
            }
        }
        data.setDirty();
    }
}
