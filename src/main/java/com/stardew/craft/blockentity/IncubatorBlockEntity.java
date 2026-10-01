package com.stardew.craft.blockentity;

import com.stardew.craft.animal.runtime.*;
import com.stardew.craft.building.runtime.*;
import com.stardew.craft.block.utility.IncubatorBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraftforge.network.PacketDistributor;
import java.util.UUID;

/** New-home incubation. The egg's persisted receipt becomes the newborn's stable identity. */
public class IncubatorBlockEntity extends TimedProductionBlockEntity {
    private UUID receipt, owner;
    private boolean legacyClock;
    private String legacyIncubationKey = "";
    public record RemainingTime(int days, int hours, int minutes) {}
    public enum ClaimResult { SUCCESS, NOT_READY, NOT_IN_BUILDING, NOT_OWNER, INVALID_BUILDING, BUILDING_FULL, INVALID_EGG, NAME_DUPLICATE, FAILED }
    public IncubatorBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.INCUBATOR.get(), pos, state); }
    @Override protected boolean readyCheckRequiresProduct() { return false; }
    public boolean isReady() { return ready; }
    public boolean isWorking() { return !input.isEmpty() && !ready; }
    public boolean hasInput() { return !input.isEmpty(); }
    public ItemStack getInput() { return input; }
    public String getReadyAnimalTypeId() { return ready ? resolveAnimalTypeId(input) : null; }
    private static long incubationMinute() {
        var time = StardewTimeManager.get();
        // Utility.CalculateMinutesUntilMorning: 1200 daytime minutes + 400 overnight minutes.
        return (time.getAbsoluteDay() - 1L) * 1600 + Math.max(0, time.getCurrentTime() - 360);
    }
    @Override public long getRemainingAbsMinutes() { return input.isEmpty() ? 0 : Math.max(0, readyAtAbsMinute - (legacyClock ? getCurrentAbsMinute() : incubationMinute())); }
    @Override protected boolean computeReady() { return !input.isEmpty() && readyAtAbsMinute >= 0 && (legacyClock ? getCurrentAbsMinute() : incubationMinute()) >= readyAtAbsMinute; }
    @Override public void advanceDays(int days) {
        if (days <= 0 || input.isEmpty()) return;
        if (level instanceof ServerLevel server) migrateLegacyIncubation(server);
        readyAtAbsMinute = Math.max(0, readyAtAbsMinute - days * (legacyClock ? 1260L : 1600L));
        ready = computeReady(); setChanged(); syncToClient();
    }
    public RemainingTime getRemainingTime() { long n = getRemainingAbsMinutes(); return new RemainingTime((int)(n / 1600), (int)(n % 1600 / 60), (int)(n % 60)); }
    public static String resolveAnimalTypeId(ItemStack egg) {
        if(egg.isEmpty())return null;
        for(var definition:com.stardew.craft.animal.model.FarmAnimalDefinitions.all())
            if(definition.eggItemIds().stream().anyMatch(id->egg.is(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id))))return definition.id();
        return com.stardew.craft.api.v1.agriculture.StardewAnimalIncubation.resolve(egg);
    }
    private BuildingRecord home(ServerLevel level, ItemStack egg) {
        var home = FarmFeed.home(level, worldPosition); var id = resolveAnimalTypeId(egg);
        if (id == null || !LivestockProjection.supported(level,LivestockSpecies.parse(id)) || !LivestockHomes.accepts(level,home, LivestockSpecies.parse(id)) || !LivestockHomes.bounds(home).contains(worldPosition)) return null;
        // The approved incubator model serves both houses; coop incubators start at tier two.
        return home.family().equals(PrefabDefinitions.COOP) && home.tier() < 2 ? null : home;
    }
    public static void serverTick(Level world, BlockPos pos, BlockState state, IncubatorBlockEntity be) {
        if (!(world instanceof ServerLevel level) || level.getGameTime() % 20 != 0) return;
        be.migrateLegacyIncubation(level);
        if (be.alreadyClaimed(level)) be.clearClaimed(level);
        if (be.home(level, be.input) != null && !be.ready && be.refreshReady()) { be.ready = true; be.setChanged(); be.syncToClient(); }
        boolean working = be.isWorking() && be.home(level, be.input) != null;
        if (state.getValue(IncubatorBlock.WORKING) != working) {
            level.setBlock(pos, state.setValue(IncubatorBlock.WORKING, working), 3);
            var extension = pos.above(); var other = level.getBlockState(extension);
            if (other.is(state.getBlock())) level.setBlock(extension, other.setValue(IncubatorBlock.WORKING, working), 3);
        }
    }
    public boolean tryInsert(ItemStack stack, Player player) { return tryInsertWithResult(stack, player).inserted(); }
    public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer actor) || !input.isEmpty()) return InsertResult.fail();
        var home = home(server, stack);
        if (home == null || incubationMinutes(stack)<=0 || !BuildingService.canManage(actor, home)) return InsertResult.fail();
        start(stack, actor.getUUID()); if (!actor.isCreative()) stack.shrink(1); return InsertResult.success();
    }
    /** Detached compatibility view for the legacy builtin building enum. Mutating it does not change the ledger. */
    @Deprecated
    public com.stardew.craft.animal.model.AnimalBuildingRecord getContainingAnimalBuilding(ServerLevel server){
        var home=getContainingRuntimeBuilding(server);if(home==null||!home.family().getNamespace().equals("stardewcraft"))return null;
        com.stardew.craft.animal.model.AnimalBuildingType type;
        try{type=com.stardew.craft.animal.model.AnimalBuildingType.of(home.family().getPath(),home.tier());}catch(IllegalArgumentException unavailable){return null;}
        var bounds=LivestockHomes.bounds(home);var residents=LivestockWorldData.get(server.getServer());
        var members=residents.all().stream().filter(a->a.home().equals(home.id())).map(a->-a.randomId()).collect(java.util.stream.Collectors.toSet());
        var farm=LivestockOutdoors.farm(server.getServer(),home);
        return new com.stardew.craft.animal.model.AnimalBuildingRecord(home.id().toString(),farm==null?"":farm.getOwnerUUID().toString(),type,home.displayName(),home.dimension().toString(),home.manager(),
                Math.max(bounds.maxExclusive().getX()-bounds.min().getX(),bounds.maxExclusive().getZ()-bounds.min().getZ())/2,
                bounds.min().getX(),bounds.min().getY(),bounds.min().getZ(),bounds.maxInclusive().getX(),bounds.maxInclusive().getY(),bounds.maxInclusive().getZ(),
                LivestockHomes.capacity(server,home),0,LivestockHomes.accepts(home),residents.outdoorsAllowed(home.id()),java.util.Set.of(),java.util.Set.of(),members);
    }
    public BuildingRecord getContainingRuntimeBuilding(ServerLevel server){return FarmFeed.home(server,worldPosition);}
    private static int incubationMinutes(ItemStack egg){
        var definition=com.stardew.craft.animal.model.FarmAnimalDefinitions.find(resolveAnimalTypeId(egg));
        var recipe=com.stardew.craft.item.artisan.ArtisanRecipeDataManager.getRecipe("incubator",egg);
        int minutes=recipe.map(com.stardew.craft.item.artisan.ArtisanRecipeDataManager.Recipe::minutes).orElse(definition==null?-1:definition.incubationTime());
        return minutes;
    }
    private void start(ItemStack egg, UUID owner) {
        this.owner = owner; receipt = UUID.randomUUID(); input = egg.copyWithCount(1); product = ItemStack.EMPTY;
        int minutes=incubationMinutes(egg);
        if (PlayerDataManager.getPlayerData(owner).hasProfession(ProfessionType.COOPMASTER)) minutes /= 2;
        readyAtAbsMinute = incubationMinute() + minutes; ready = false; setChanged(); syncToClient();
    }
    public void open(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || !ready) return;
        var home = home(server, input);
        if (home == null || !BuildingService.canManage(player, home)) { LivestockService.message(player, "permission"); return; }
        var tag = new CompoundTag(); tag.putString("Kind", "incubator"); tag.putLong("Position", worldPosition.asLong());
        com.stardew.craft.animal.runtime.LivestockUiData.describe(tag,LivestockSpecies.parse(resolveAnimalTypeId(input)));tag.putString("BuildingName",home.title().getString());
        PacketDistributor.sendToPlayer(player, new LivestockShopPayload(tag));
    }
    public ClaimResult claimReadyAnimal(ServerPlayer player, String name) {
        if (!(level instanceof ServerLevel server) || player.level() != level || player.distanceToSqr(worldPosition.getCenter()) > 64) return ClaimResult.NOT_OWNER;
        LivestockService.recover(server.getServer());
        migrateLegacyIncubation(server);
        var data = LivestockWorldData.get(server.getServer());
        if (alreadyClaimed(server)) { clearClaimed(server); return ClaimResult.SUCCESS; }
        if (!ready || receipt == null) return ClaimResult.NOT_READY;
        var home = home(server, input); if (home == null) return ClaimResult.INVALID_BUILDING;
        if (!BuildingService.canManage(player, home)) return ClaimResult.NOT_OWNER;
        if (data.occupancy(home.id()) >= LivestockHomes.capacity(server,home)) return ClaimResult.BUILDING_FULL;
        name = name.strip();
        if (name.isEmpty() || name.length() > 32 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) return ClaimResult.FAILED;
        if (LivestockHomes.spawn(server, home, LivestockSpecies.parse(resolveAnimalTypeId(input)), true) == null) return ClaimResult.INVALID_BUILDING;
        // Persist the input receipt before the record, then clear only after the record is durable.
        server.getChunkSource().save(true);
        var baby = new LivestockRecord(receipt, owner == null ? player.getUUID() : owner, home.farmId(), home.id(), name, data.allocateRandomId(), StardewTimeManager.get().getAbsoluteDay(), LivestockCare.purchased()).species(LivestockSpecies.parse(resolveAnimalTypeId(input)));
        if (legacyIncubationKey.isEmpty()) data.put(baby);
        else data.importLegacyAnimal(legacyIncubationKey, baby, false);
        if (legacyIncubationKey.isEmpty()) server.getServer().overworld().getDataStorage().save();
        else LegacyLivestockMigration.checkpointAnimals(server.getServer());
        clear(); LivestockService.project(server.getServer()); return ClaimResult.SUCCESS;
    }
    private boolean alreadyClaimed(ServerLevel level) {
        var data = LivestockWorldData.get(level.getServer());
        return receipt != null && data.find(receipt) != null
                || !legacyIncubationKey.isEmpty() && data.legacyImport(legacyIncubationKey) != null;
    }
    private void clearClaimed(ServerLevel level) {
        // A prior claim may have installed its in-memory receipt before a failed disk write.
        if (!legacyIncubationKey.isEmpty()) LegacyLivestockMigration.checkpointAnimals(level.getServer());
        clear();
    }
    private void migrateLegacyIncubation(ServerLevel level) {
        if (!legacyClock || input.isEmpty()) return;
        var home = FarmFeed.home(level, worldPosition);
        var farm = home == null ? FarmFeed.farm(level, worldPosition) : LivestockOutdoors.farm(level.getServer(), home);
        if (farm == null) return;
        // Farm slots can be reused. A previous farm's egg receipt must not consume this farm's input.
        legacyIncubationKey = "incubation:" + farm.getInstanceId() + ":" + level.dimension().location() + ":" + worldPosition.asLong()
                + ":" + readyAtAbsMinute + ":" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(input.getItem());
        receipt = LegacyLivestockMigration.stableId(legacyIncubationKey);
        long remaining = ready ? 0 : Math.max(0, readyAtAbsMinute - getCurrentAbsMinute());
        readyAtAbsMinute = incubationMinute() + remaining;
        owner = farm.getOwnerUUID();
        legacyClock = false; setChanged(); syncToClient();
    }
    private void clear() { input = ItemStack.EMPTY; product = ItemStack.EMPTY; ready = false; readyAtAbsMinute = -1; receipt = null; owner = null; legacyClock = false; legacyIncubationKey = ""; setChanged(); syncToClient(); }
    @Override public ItemStack getAutomationInput() { return input; }
    @Override public ItemStack getAutomationOutput() { return ItemStack.EMPTY; }
    @Override public ItemStack extractAutomation(int amount, boolean simulate) { return ItemStack.EMPTY; }
    @Override public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (!(level instanceof ServerLevel server) || !input.isEmpty()) return stack;
        var home = home(server, stack); if (home == null) return stack;
        var farm = LivestockOutdoors.farm(server.getServer(), home); if (farm == null) return stack;
        if (!simulate) start(stack, farm.getOwnerUUID());
        return AutomationStackHelper.remainderAfterInsert(stack, 1);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) tag.put("Input", input.save(registries)); tag.putLong("ReadyAt", readyAtAbsMinute); tag.putBoolean("Ready", ready);
        if (receipt != null) tag.putUUID("NewbornReceipt", receipt); if (owner != null) tag.putUUID("Caretaker", owner);
        tag.putBoolean("LegacyClock", legacyClock); tag.putString("LegacyIncubationKey", legacyIncubationKey);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains("Input") ? ItemStack.parse(registries, tag.getCompound("Input")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        readyAtAbsMinute = tag.contains("ReadyAt") ? tag.getLong("ReadyAt") : -1; ready = tag.getBoolean("Ready");
        receipt = tag.hasUUID("NewbornReceipt") ? tag.getUUID("NewbornReceipt") : null;
        owner = tag.hasUUID("Caretaker") ? tag.getUUID("Caretaker") : null;
        legacyClock = tag.getBoolean("LegacyClock"); legacyIncubationKey = tag.getString("LegacyIncubationKey");
        if (!tag.contains("ReadyAt") && tag.contains("input")) {
            input = ItemStack.parse(registries, tag.getCompound("input")).orElse(ItemStack.EMPTY);
            readyAtAbsMinute = tag.getLong("readyAtAbsMinute"); ready = tag.getBoolean("ready");
            product = ItemStack.EMPTY; legacyClock = !input.isEmpty();
        }
    }
}
