package com.stardew.craft.mining;

import com.stardew.craft.util.StardewDeterministicRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/** MineShaft.getSpecialItemForThisMineLevel; repeated entries preserve the original slot weights. */
public final class OrdinaryMineSpecialLoot {
    public static final String TAG="stardewcraft_mine_special_item";
    private static final String[][] POOLS={
        {"carving_knife","wood_club","sneakers","rubber_boots","small_glow_ring","small_magnet_ring"},
        {"wind_spire","wood_club","sneakers","rubber_boots","small_glow_ring","small_magnet_ring","forest_sword"},
        {"iron_edge","lead_rod","forest_sword","thermal_boots","glow_ring","magnet_ring","wood_mallet"},
        {"lead_rod","wood_mallet","combat_boots","thermal_boots","glow_ring","magnet_ring","shadow_dagger"},
        {"yeti_tooth","yeti_tooth","dark_boots","genie_shoes","burglars_shank","the_slammer","tempered_broadsword","holy_blade"},
        {"shadow_dagger","steel_falchion","dark_boots","genie_shoes","burglars_shank","kudgel","immunity_band","holy_blade"},
        {"wicked_kris","steel_falchion","dark_boots","genie_shoes","burglars_shank","the_slammer","tempered_broadsword","battery_pack","crystal_shoes","curiosity_lure","lucky_ring","immunity_band"}
    };
    public static ItemStack roll(ServerLevel level,int floor,BlockPos pos) {
        var layout=OrdinaryMineLayout.load(level,floor);var origin=layout.origin(floor);
        int x=pos.getX()-origin.getX()-layout.tileX,z=pos.getZ()-origin.getZ()-layout.tileZ;
        var random=StardewDeterministicRandom.createFromDoubles(floor,com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay(),x,z*9999.0,0);
        String[] pool=POOLS[Math.clamp(floor/20,0,6)];String name=pool[random.nextInt(pool.length)];
        var stack=new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation("stardewcraft",name)));
        if(name.equals("holy_blade")) level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(com.stardew.craft.enchantment.StardewEnchantments.CRUSADER).ifPresent(e->stack.enchant(e,1));
        return stack;
    }
}
