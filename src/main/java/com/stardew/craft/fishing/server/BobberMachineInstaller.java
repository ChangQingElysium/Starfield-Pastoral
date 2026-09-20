package com.stardew.craft.fishing.server;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.core.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Verified empty location beside FishShop.3's ship in the shipped v1.3.7 map. Never replaces a player's block. */
@EventBusSubscriber(modid=StardewCraft.MODID)
public final class BobberMachineInstaller extends SavedData {
    private static final String DATA_NAME="stardew_bobber_machine_installation";
    public static final BlockPos LEGACY_POSITION=new BlockPos(71,31,142);
    public static final BlockPos POSITION=LEGACY_POSITION.south();
    // Bump once so saves where the pregen upgrade already removed the machine
    // get a repair pass even when the bundled map version is already current.
    private static final int PLACEMENT_VERSION=3;
    private boolean completed;
    private int placementVersion;
    public BobberMachineInstaller() {}
    public static BobberMachineInstaller get(ServerLevel level){
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(BobberMachineInstaller::new,BobberMachineInstaller::load),DATA_NAME);
    }
    public void resetForMigration(){
        completed=false;
        placementVersion=0;
        setDirty();
    }
    public static BobberMachineInstaller load(CompoundTag tag,HolderLookup.Provider registries){var data=new BobberMachineInstaller();data.completed=tag.getBoolean("Completed");data.placementVersion=tag.getInt("PlacementVersion");return data;}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){tag.putBoolean("Completed",completed);tag.putInt("PlacementVersion",placementVersion);return tag;}
    public static boolean canInstall(BlockGetter level){
        return level.getBlockState(POSITION).isAir()&&level.getBlockState(POSITION.above()).isAir()
                &&BuiltInRegistries.BLOCK.getKey(level.getBlockState(POSITION.below()).getBlock()).toString().equals("stardewcraft:flooring_block");
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if(!(event.getLevel() instanceof ServerLevel level)||level.dimension()!=ModDimensions.STARDEW_VALLEY||level.getGameTime()%20!=0||!level.hasChunkAt(POSITION))return;
        var data=get(level);
        if(data.completed&&data.placementVersion>=PLACEMENT_VERSION)return;
        var machine=ModBlocks.BOBBER_STYLE_MACHINE.get();
        var old=level.getBlockState(LEGACY_POSITION);
        boolean legacy=old.is(machine)&&old.getValue(MapDecorStaticBlock.PART)==MapDecorStaticBlock.Part.MAIN;
        // A completed installation that the player removed must stay removed.
        if(data.completed&&data.placementVersion>=PLACEMENT_VERSION&&!legacy){data.placementVersion=PLACEMENT_VERSION;data.setDirty();return;}
        if(legacy&&!canInstall(level))return;
        if(!level.getBlockState(POSITION).is(machine)){
            if(!canInstall(level))return;
            var state=legacy?old:machine.defaultBlockState().setValue(MapDecorStaticBlock.FACING,Direction.SOUTH);
            if(!level.setBlockAndUpdate(POSITION,state))return;
            if(!machine.placeExtensions(level,POSITION,state)){
                MapDecorStaticBlock.runWithDropsSuppressed(()->level.removeBlock(POSITION,false));return;
            }
        }
        if(legacy)MapDecorStaticBlock.runWithDropsSuppressed(()->level.removeBlock(LEGACY_POSITION,false));
        data.completed=true;data.placementVersion=PLACEMENT_VERSION;data.setDirty();
    }
}
