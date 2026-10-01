package com.stardew.craft.animal.runtime;

import com.stardew.craft.blockentity.AutoGrabberBlockEntity;
import com.stardew.craft.building.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Computes complete post-collection inventories without mutating a block before the day journal is saved. */
public final class LivestockCollectors {
    private LivestockCollectors() {}
    private record Box(BlockPos pos, com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Resolved facility, CompoundTag state, List<ItemStack> slots) {
        boolean insert(ItemStack product) {
            if(product.isEmpty())return false;
            int room=0;
            for(int i=0;i<slots.size();i++)if(facility.access().canInsert(i,product)) {
                var slot=slots.get(i);int limit=Math.min(product.getMaxStackSize(),Math.max(0,facility.access().slotLimit(i,product)));
                if(slot.isEmpty())room+=limit;else if(ItemStack.isSameItemSameTags(slot,product))room+=Math.max(0,limit-slot.getCount());
            }
            if(room<product.getCount())return false;
            int remaining=product.getCount();
            for(int i=0;i<slots.size()&&remaining>0;i++)if(facility.access().canInsert(i,product)) {
                var slot=slots.get(i);int limit=Math.min(product.getMaxStackSize(),Math.max(0,facility.access().slotLimit(i,product)));
                if(!slot.isEmpty()&&!ItemStack.isSameItemSameTags(slot,product))continue;
                int n=Math.min(remaining,Math.max(0,limit-slot.getCount()));
                if(n>0){if(slot.isEmpty())slots.set(i,product.copyWithCount(n));else slot.grow(n);remaining-=n;}
            }
            return true;
        }
    }
    public static Map<BlockPos,CompoundTag> collect(ServerLevel level, BuildingRecord home, List<LivestockRecord> animals, List<LivestockWorldData.Product> eggs) {
        var boxes=new ArrayList<Box>(); var bounds=LivestockHomes.bounds(home);
        for(var pos:BlockPos.betweenClosed(bounds.min(),bounds.maxInclusive())) {
            var facility=com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.resolve(level,pos);
            if(facility.access().units(com.stardew.craft.api.v1.agriculture.StardewAnimalFacilities.Role.COLLECTOR)<=0)continue;
            var slots=new ArrayList<ItemStack>();facility.access().inventory().forEach(stack->slots.add(stack.copy()));
            boxes.add(new Box(pos.immutable(),facility,facility.access().snapshot().copy(),slots));
        }
        if(boxes.isEmpty()) return Map.of(); var changed=new HashSet<BlockPos>();
        eggs.removeIf(egg -> {
            for(var box:boxes) if(box.insert(LivestockProductEntity.stack(egg,level))) {changed.add(box.pos);return true;}
            return false;
        });
        for(int i=0;i<animals.size();i++) {
            var animal=animals.get(i);
            if(animal.baby() || animal.produce().isEmpty() || animal.species().harvest()==LivestockSpecies.Harvest.DROP || animal.species().harvest()==LivestockSpecies.Harvest.DIG
                    || animal.location()!=null && animal.location().outside()) continue;
            for(var box:boxes) if(box.insert(LivestockProducts.held(level,animal))) {
                changed.add(box.pos);animals.set(i,LivestockStats.stage(animal,LivestockProducts.held(level,animal)).produce(""));break;
            }
        }
        var result=new LinkedHashMap<BlockPos,CompoundTag>();
        for(var box:boxes) if(changed.contains(box.pos)) {
            result.put(box.pos,box.facility.plan(box.facility.access().withInventory(box.state.copy(),box.slots)));
        }
        return result;
    }
}
