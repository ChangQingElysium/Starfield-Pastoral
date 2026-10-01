package com.stardew.craft.api.v1.agriculture;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Facility plans must be pure; apply restores the exact planned state and must be idempotent. */
public final class StardewAnimalFacilities {
    public enum Role { TROUGH, AUTOMATIC_TROUGH, HOPPER, INCUBATOR, AUTO_PETTER, COLLECTOR, HEATER }
    public interface Access {
        int units(Role role);
        default int hay(){return 0;}
        default CompoundTag snapshot(){return new CompoundTag();}
        default CompoundTag withHay(CompoundTag snapshot,int amount){throw new UnsupportedOperationException("No feed storage");}
        default int slotLimit(int slot,ItemStack stack){return stack.getMaxStackSize();}
        default boolean canInsert(int slot,ItemStack stack){return true;}
        default List<ItemStack> inventory(){return List.of();}
        default CompoundTag withInventory(CompoundTag snapshot,List<ItemStack> inventory){throw new UnsupportedOperationException("No collector storage");}
        default void apply(CompoundTag snapshot){throw new UnsupportedOperationException("No mutable facility");}
    }
    @FunctionalInterface public interface Provider { Access resolve(ServerLevel level,BlockPos pos); }
    public record Resolved(ResourceLocation provider,Access access){
        public CompoundTag plan(CompoundTag state){var tag=new CompoundTag();tag.putString("Provider",provider.toString());tag.put("State",state.copy());return tag;}
    }
    private record Registered(ResourceLocation id,int priority,Provider provider){}
    private static final Map<ResourceLocation,Registered> registrations=new LinkedHashMap<>();
    private static volatile List<Registered> providers=List.of();
    private static final ResourceLocation BUILTIN=new ResourceLocation("stardewcraft:builtin_facility");
    private StardewAnimalFacilities(){}
    public static synchronized void register(ResourceLocation id,int priority,Provider provider){
        Objects.requireNonNull(id);Objects.requireNonNull(provider);
        if(id.equals(BUILTIN)||registrations.containsKey(id))throw new IllegalStateException("Duplicate facility provider "+id);
        registrations.put(id,new Registered(id,priority,provider));
        providers=registrations.values().stream().sorted(Comparator.comparingInt(Registered::priority).reversed().thenComparing(r->r.id().toString())).toList();
    }
    public static Resolved resolve(ServerLevel level,BlockPos pos){
        for(var provider:providers)try{var access=provider.provider().resolve(level,pos.immutable());if(access!=null)return new Resolved(provider.id(),access);}
        catch(RuntimeException failure){com.stardew.craft.StardewCraft.LOGGER.error("Facility provider {} failed at {}",provider.id(),pos,failure);}
        return new Resolved(BUILTIN,builtin(level,pos));
    }
    public static int count(ServerLevel level,BlockPos pos,Role role){return Math.max(0,resolve(level,pos).access().units(role));}
    public static void apply(ServerLevel level,BlockPos pos,CompoundTag plan){
        var current=resolve(level,pos);
        if(!current.provider().toString().equals(plan.getString("Provider")))throw new IllegalStateException("Pending facility provider changed at "+pos);
        current.access().apply(plan.getCompound("State").copy());
    }
    private record Conversion(ResourceLocation source,ResourceLocation target,java.util.function.UnaryOperator<CompoundTag> convert){}
    private static final Map<ResourceLocation,Conversion> conversions=new LinkedHashMap<>();
    public static synchronized void registerUpgrade(ResourceLocation id,ResourceLocation source,ResourceLocation target,java.util.function.UnaryOperator<CompoundTag> convert){
        Objects.requireNonNull(id);Objects.requireNonNull(source);Objects.requireNonNull(target);Objects.requireNonNull(convert);
        if(conversions.containsKey(id)||conversions.values().stream().anyMatch(c->c.source.equals(source)&&c.target.equals(target)))throw new IllegalStateException("Duplicate facility conversion "+id);
        conversions.put(id,new Conversion(source,target,convert));
    }
    public static synchronized boolean canUpgrade(net.minecraft.world.level.block.Block source,net.minecraft.world.level.block.Block target){
        if(source==target||source==ModBlocks.FEED_TROUGH.get()&&target==ModBlocks.AUTOFEED_TROUGH.get())return true;
        return conversions.values().stream().anyMatch(c->c.source.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(source))&&c.target.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(target)));
    }
    public static synchronized CompoundTag upgrade(net.minecraft.world.level.block.Block source,net.minecraft.world.level.block.Block target,CompoundTag state){
        for(var c:conversions.values())if(c.source.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(source))&&c.target.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(target)))return Objects.requireNonNull(c.convert.apply(state.copy())).copy();
        if(canUpgrade(source,target))return state.copy();
        throw new IllegalArgumentException("No facility state conversion registered");
    }
    private static Access builtin(ServerLevel level,BlockPos pos){
        var state=level.getBlockState(pos);var be=level.getBlockEntity(pos);
        return new Access(){
            public int units(Role role){return switch(role){
                case TROUGH -> state.is(ModBlocks.FEED_TROUGH.get())?1:0;
                case AUTOMATIC_TROUGH -> state.is(ModBlocks.AUTOFEED_TROUGH.get())?1:0;
                case HOPPER -> state.is(ModBlocks.HAY_HOPPER.get())&&state.getValue(com.stardew.craft.block.utility.HayHopperBlock.PART)==com.stardew.craft.block.utility.HayHopperBlock.Part.MAIN?1:0;
                case INCUBATOR -> state.is(ModBlocks.INCUBATOR.get())&&state.getValue(com.stardew.craft.block.utility.IncubatorBlock.PART)==com.stardew.craft.block.utility.IncubatorBlock.Part.MAIN?1:0;
                case HEATER -> state.is(ModBlocks.HEATER.get())||state.is(ModBlocks.WOOD_BUNDLE.get())?1:0;
                case AUTO_PETTER -> be instanceof AutoPetterBlockEntity?1:0;
                case COLLECTOR -> be instanceof AutoGrabberBlockEntity?1:0;
            };}
            public int hay(){return be instanceof FeedTroughBlockEntity trough?trough.getAutomationInput().getCount():be instanceof AutoFeedTroughBlockEntity trough?trough.getAutomationInput().getCount():0;}
            public CompoundTag snapshot(){return be==null?new CompoundTag():be.saveCustomOnly(level.registryAccess());}
            public CompoundTag withHay(CompoundTag input,int count){
                if(count<0||count>1)throw new IllegalArgumentException("Builtin trough holds one hay");
                var tag=input.copy();tag.remove("hay");if(count>0)tag.put("hay",new ItemStack(com.stardew.craft.item.ModItems.HAY.get(),count).save(level.registryAccess()));return tag;
            }
            public List<ItemStack> inventory(){
                if(!(be instanceof AutoGrabberBlockEntity box))return List.of();
                var slots=new ArrayList<ItemStack>();for(int i=0;i<box.getContainerSize();i++)slots.add(box.getItem(i).copy());return slots;
            }
            public CompoundTag withInventory(CompoundTag input,List<ItemStack> slots){
                var tag=input.copy();var rows=new ListTag();
                for(int i=0;i<slots.size();i++)if(!slots.get(i).isEmpty()){var row=new CompoundTag();row.putInt("Slot",i);row.put("Stack",slots.get(i).save(level.registryAccess()));rows.add(row);}
                tag.put("items",rows);return tag;
            }
            public void apply(CompoundTag tag){
                if(be==null)throw new IllegalStateException("Pending facility disappeared at "+pos);
                be.loadWithComponents(tag,level.registryAccess());be.setChanged();
                if(be instanceof FeedTroughBlockEntity trough)trough.setHayPresent(tag.contains("hay"));
                if(be instanceof AutoFeedTroughBlockEntity trough)trough.setHayPresent(tag.contains("hay"));
                if(be instanceof AutoGrabberBlockEntity box)box.refreshVisualState();
            }
        };
    }
}
