package com.stardew.craft.animal.runtime;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import java.util.Random;

public final class LivestockProducts {
    private LivestockProducts() {}
    public static ItemStack stack(String item, int count, int sourceQuality) {
        var result = new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(item.contains(":") ? item : "stardewcraft:" + item)), count);
        // The gameplay reducer keeps SDV's 4; this mod's item model/price helpers use 3 for iridium.
        QualityHelper.setQuality(result, sourceQuality == 4 ? QualityHelper.IRIDIUM : sourceQuality);
        return result;
    }
    public static boolean fits(ServerPlayer player, ItemStack stack) {
        int room = 0;
        for (int i = 0; i < 36; i++) {
            var slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) room += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(slot, stack)) room += Math.max(0, slot.getMaxStackSize() - slot.getCount());
        }
        return room >= stack.getCount();
    }
    /** Matches this mod's per-player luck roll for the settlement date, including offline farm members. */
    public static double dailyLuck(MinecraftServer server, BuildingRecord home, int absoluteDay) {
        var farm = LivestockOutdoors.farm(server, home); if (farm == null) return 0;
        int dateKey = absoluteDay - 1 + 112; double sum = 0;
        for (var id : farm.getAllFarmers()) {
            var data = PlayerDataManager.getPlayerData(id);
            if (data.getDailyLuckDateKey() == dateKey) sum += data.getDailyLuck();
            else sum += (new Random(id.getMostSignificantBits() ^ id.getLeastSignificantBits() ^ (long)dateKey * 0x9E3779B97F4A7C15L).nextInt(201) - 100) / 1000.0;
        }
        return sum / farm.getFarmerCount();
    }
    public static ItemStack held(ServerLevel level,LivestockRecord animal){
        var saved=animal.extra().getCompound("HeldProduce");
        var stack=saved.isEmpty()?stack(animal.produce(),1,animal.care().quality()):PortItemStacks.parseOptional(level.registryAccess(),saved);
        if(animal.cracker())stack.setCount(Math.multiplyExact(stack.getCount(),2));return stack;
    }
    public static boolean interact(ServerPlayer player, net.minecraft.world.entity.PathfinderMob entity) {
        var held = player.getMainHandItem();
        boolean milk = held.is(ModItems.MILK_PAIL.get()), shear = held.is(ModItems.SHEARS.get()), cracker = held.is(ModItems.GOLDEN_ANIMAL_CRACKER.get());

        var server = player.serverLevel().getServer(); LivestockService.recover(server);
        var data = LivestockWorldData.get(server); var animal = data.find(entity.getUUID());
        var definition=animal==null?null:animal.species().definition();
        boolean tool=definition!=null&&definition.harvestTool()!=null&&held.is(BuiltInRegistries.ITEM.get(definition.harvestTool()));
        if(!milk&&!shear&&!cracker&&!tool)return false;
        var home = animal == null ? null : BuildingWorldData.get(server).find(animal.home());
        if (home == null || !BuildingService.canManage(player, home)) { LivestockService.message(player, "permission"); return true; }
        if (cracker) {
            if (animal.cracker() || definition==null || !definition.canEatGoldenCrackers()) { LivestockService.message(player, "cracker_unavailable"); return true; }
            data.put(animal.cracker(true)); held.shrink(1); return true;
        }
        if (animal.baby() || animal.produce().isEmpty() || !tool) { LivestockService.message(player,"no_produce");return true; }
        var product = held(player.serverLevel(),animal);
        if (product.isEmpty()) return true;
        if (!fits(player, product)) { LivestockService.message(player, "inventory_full"); return true; }
        int count = product.getCount(); var collected=product.copy();player.getInventory().add(product);
        com.stardew.craft.animal.service.AnimalProduceStatService.recordForPlayer(player,definition,collected);
        var care = animal.care();
        data.put(animal.produce("").withCare(animal.settledDay(), new LivestockCare(care.age(), care.ownedDays(), Math.min(1000, care.friendship() + 5), care.happiness(), care.fullness(), care.daysSinceLay(), care.quality(), care.petted(), care.autoPetted())));
        PlayerStardewDataAPI.addExperience(player, SkillType.FARMING, 5);
        PlayerStardewDataAPI.recordAnimalProductsCollected(player.getUUID(), count); LivestockProjection.refresh(entity,data.find(animal.id())); return true;
    }
}
