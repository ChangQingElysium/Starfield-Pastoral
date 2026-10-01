package com.stardew.craft.block.utility;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.building.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.PortBlockInteraction;

/** Runtime-only housing manager; old enclosure/relocation menus cannot alter these buildings. */
public class ResidenceManagerBlock extends BuildingManagerModelBlock implements PortBlockInteraction {
    protected ResidenceManagerBlock(Properties properties,String model){super(properties,model);}
    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override public InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(player instanceof ServerPlayer server && !BuildingManagerInteraction.open(server,pos)) BuildingPlacementService.message(server,"work_stale");
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(state.getBlock()!=next.getBlock() && level instanceof ServerLevel server && !BuildingProtection.transferring()) {
            var data=BuildingWorldData.peek(server.getServer());
            if(data!=null){var id=data.occupying(level.dimension().location(),pos);var record=id==null?null:data.find(id);if(record!=null && record.manager().equals(pos))data.detachSelf(id);}
        }
        super.onRemove(state,level,pos,next,moving);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state,LootParams.Builder params){
        var stack=new ItemStack(this); var level=params.getLevel(); var origin=params.getOptionalParameter(LootContextParams.ORIGIN);
        if(origin!=null){var pos=BlockPos.containing(origin);var data=BuildingWorldData.peek(level.getServer());
            if(data!=null)for(var record:data.all().reversed().stream().sorted(java.util.Comparator.comparing(r -> r.phase()==BuildingRecord.Phase.MISSING)).toList())if(record.mode()==BuildingRecord.Mode.SELF_BUILT && PrefabDefinitions.managerBlock(record.family())==this && record.dimension().equals(level.dimension().location()) && record.manager().equals(pos)){
                var tag=new net.minecraft.nbt.CompoundTag();tag.putUUID("ResidenceIdentity",record.id());
                PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_DATA,com.stardew.craft.port.net.minecraft.world.item.component.CustomData.of(tag));
                PortItemData.set(stack, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_NAME,record.title());break;
            }
        }
        return java.util.List.of(stack);
    }
}
