package com.stardew.craft.pet;

import com.stardew.craft.port.PortItemStacks;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.item.cosmetic.StardewHatItem;
import com.stardew.craft.time.StardewTimeManager;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PetService {
    private static final Map<UUID, FarmInstance> farms = new HashMap<>();
    private PetService() {}
    public static FarmInstance farm(UUID id) {
        var cached = farms.get(id);
        if (cached != null) return cached;
        for (var farm : FarmInstanceRegistry.get().getAllFarms()) if (farm.getInstanceId().equals(id)) { farms.put(id, farm); return farm; }
        return null;
    }
    public static boolean manages(ServerPlayer player, UUID id) {
        var farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        return farm != null && farm.getInstanceId().equals(id);
    }
    public static boolean selectInitial(ServerPlayer player, FarmInstance farm, String variantId, String name) {
        if (!farm.getOwnerUUID().equals(player.getUUID()) || !manages(player, farm.getInstanceId())) return false;
        var variant = PetVariant.find(variantId).filter(PetVariant::initial).orElse(null);
        String cleaned = PetRecord.cleanName(name);
        if (!variantId.isEmpty() && (variant == null || cleaned.isBlank())) return false;
        var data = PetWorldData.get(player.server);
        if (!data.chooseInitial(farm.getInstanceId())) return false;
        if (variant == null) {
            var playerData = com.stardew.craft.player.PlayerDataManager.getPlayerData(player);
            playerData.addMailFlag(PetManagement.REJECTED_ADOPTION_FLAG);
            com.stardew.craft.player.PlayerDataEventHandler.syncPlayerData(player, playerData);
            return true;
        }
        var pet = new PetRecord(UUID.randomUUID(), farm.getInstanceId(), variant, cleaned, StardewTimeManager.get().getAbsoluteDay());
        if (farm.isInitialized() && player.level().dimension() == ModDimensions.STARDEW_VALLEY && farm.contains(player.blockPosition())) {
            var near = PetHomes.near(player.serverLevel(), farm, player.blockPosition().relative(player.getDirection().getOpposite(), 2), variant, 3);
            if (near != null) pet.position = Vec3.atBottomCenterOf(near);
        }
        data.put(pet); gotPet(player.server, farm); return true;
    }
    public static void remember(ServerLevel level, PetEntity entity) {
        var data = PetWorldData.get(level.getServer()); var pet = data.find(entity.getUUID());
        if (pet == null || level.dimension() != ModDimensions.STARDEW_VALLEY) return;
        if (!entity.position().equals(pet.position) || pet.yaw != entity.getYRot()) { pet.position = entity.position(); pet.yaw = entity.getYRot(); data.setDirty(); }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { farms.clear(); PetManagement.clear(); }
    @SubscribeEvent public static void stopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        var level = event.getServer().getLevel(ModDimensions.STARDEW_VALLEY); if (level == null) return;
        for (var pet : PetWorldData.get(event.getServer()).all()) if (level.getEntity(pet.id) instanceof PetEntity entity) remember(level, entity);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer(); if (server.getTickCount() % 100 != 0) return;
        var level = server.getLevel(ModDimensions.STARDEW_VALLEY); if (level == null) return;
        farms.clear(); for (var farm : FarmInstanceRegistry.get().getAllFarms()) farms.put(farm.getInstanceId(), farm);
        var petData = PetWorldData.get(server);
        for (var farm : farms.values()) if (petData.loved(farm.getInstanceId())) PetManagement.scheduleAdoptionMail(server, farm);
        for (var farm : farms.values()) PetHomes.prepare(level, farm);
        onNewDay(level); updateBowls(level); project(level);
    }
    private static final java.util.Map<UUID, UUID> inFarm = new java.util.HashMap<>();
    /** Pet.behaviorOnFarmerLocationEntry: before 20:00 a pet naps with 50% chance when a farmer enters the farm. */
    @SubscribeEvent public static void farmEntry(ServerTickEvent.Post event) {
        var server = event.getServer(); if (server.getTickCount() % 20 != 0) return;
        var level = server.getLevel(ModDimensions.STARDEW_VALLEY); if (level == null) return;
        boolean day = StardewTimeManager.get().getCurrentTime() < 1200;
        inFarm.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        for (var player : level.players()) {
            var farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
            UUID now = farm != null && farm.isInitialized() && farm.contains(player.blockPosition()) ? farm.getInstanceId() : null;
            UUID before = now == null ? inFarm.remove(player.getUUID()) : inFarm.put(player.getUUID(), now);
            if (now == null || now.equals(before) || !day) continue;
            var data = PetWorldData.get(server);
            for (var pet : data.all()) {
                if (!pet.farm.equals(now) || !pet.variant.available()) continue;
                if (level.getEntity(pet.id) instanceof PetEntity entity && !pet.indoors && level.random.nextBoolean()) entity.napNow();
            }
        }
    }
    public static void onNewDay(ServerLevel level) {
        var data = PetWorldData.get(level.getServer()); int day = StardewTimeManager.get().getAbsoluteDay();
        for (var pet : data.all()) {
            if (pet.settledDay >= day) continue;
            if (!pet.variant.available()) { pet.settledDay = day; data.setDirty(); continue; }
            assignFreeBowl(data, pet);
            var bowl = pet.bowl == null ? null : data.bowl(pet.bowl);
            if (bowl == null) { pet.friendship(-10 * (day - pet.settledDay)); pet.bowlSadDay = day; }
            else if (bowl.wateredDay() >= pet.settledDay && bowl.wateredDay() < day) pet.friendship(6);
            pet.settledDay = day;
            pet.petted.entrySet().removeIf(entry -> entry.getValue() < day - 1);
            if (pet.friendship == 1000 && !data.loved(pet.farm)) {
                data.markLoved(pet.farm);
                PetManagement.scheduleAdoptionMail(level.getServer(), farm(pet.farm));
                lovesYou(level.getServer(), farm(pet.farm), pet.name);
            }
            data.setDirty();
        }
    }
    /** Event.namePet: every farmer of the farm gets the "gotPet" active dialogue topic. */
    public static void gotPet(net.minecraft.server.MinecraftServer server, FarmInstance farm) {
        if (farm == null) return;
        var topics = com.stardew.craft.npc.runtime.NpcDialogueEventData.get(server);
        for (var farmer : farm.getAllFarmers()) topics.activate(farmer, "gotPet");
    }
    /** Pet.GrantLoveMailIfNecessary: PetLovesYou shown to the farm's online farmers. */
    private static void lovesYou(net.minecraft.server.MinecraftServer server, FarmInstance farm, String name) {
        if (farm == null) return;
        for (var farmer : farm.getAllFarmers()) {
            var online = server.getPlayerList().getPlayer(farmer);
            if (online != null) message(online, "loves_you", name);
        }
    }
    public static void assignFreeBowl(PetWorldData data, PetRecord pet) {
        if (pet.bowl != null && data.bowl(pet.bowl) != null) return;
        for (var bowl : data.bowls()) if (bowl.farm().equals(pet.farm) && data.occupant(bowl.position()) == null) { pet.bowl = bowl.position(); data.setDirty(); return; }
    }
    private static void updateBowls(ServerLevel level) {
        var data = PetWorldData.get(level.getServer()); int day = StardewTimeManager.get().getAbsoluteDay(); int season = StardewTimeManager.get().getCurrentSeason();
        boolean rain = com.stardew.craft.weather.WeatherManager.isRaining(level);
        for (var bowl : data.bowls()) {
            var pos = bowl.position();
            if (!level.hasChunkAt(pos)) { if (rain && bowl.outdoors() && bowl.wateredDay() != day) data.bowl(bowl.watered(day)); continue; }
            boolean outdoors = level.canSeeSky(pos.above());
            if (bowl.outdoors() != outdoors) data.bowl(new PetWorldData.Bowl(bowl.farm(), pos, bowl.style(), bowl.wateredDay(), outdoors));
            var state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof PetBowlBlock)) { data.removeBowl(pos); continue; }
            PetBowlBuildings.ensure(level, pos);
            boolean full = bowl.wateredDay() == day;
            if (rain && outdoors) { if (!full) data.bowl(new PetWorldData.Bowl(bowl.farm(), pos, bowl.style(), day, outdoors)); full = true; }
            var next = state.setValue(PetBowlBlock.FULL, full).setValue(PetBowlBlock.SEASON, season);
            if (next != state) level.setBlock(pos, next, 3);
        }
    }
    public static void project(ServerLevel level) {
        var data = PetWorldData.get(level.getServer());
        for (var pet : data.all()) {
            if (!pet.variant.available()) { if (level.getEntity(pet.id) instanceof PetEntity missing) missing.discard(); continue; }
            var farm = farm(pet.farm); if (farm == null || !farm.isInitialized()) continue;
            var existing = level.getEntity(pet.id);
            if (existing instanceof PetEntity entity) { entity.refresh(pet); continue; }
            if (existing != null) continue;
            if (pet.position == null) {
                var player = level.players().stream().filter(p -> farm.isFarmer(p.getUUID()) && farm.contains(p.blockPosition())).findFirst().orElse(null);
                if (player == null) continue;
                var near = PetHomes.near(level, farm, player.blockPosition().relative(player.getDirection().getOpposite(), 2), pet.variant, 3);
                if (near == null) continue;
                pet.position = Vec3.atBottomCenterOf(near); assignFreeBowl(data, pet); data.setDirty();
            }
            var pos = BlockPos.containing(pet.position);
            // A terrain chunk can exist before entity storage is ready, or while it is being unloaded.
            if (!level.isPositionEntityTicking(pos) || !level.areEntitiesLoaded(new net.minecraft.world.level.ChunkPos(pos).toLong())) continue;
            if (!PetHomes.safePosition(level, farm, pet.position, pet.variant)) {
                var safe = PetHomes.near(level, farm, pos, pet.variant, 4);
                if (safe == null) safe = PetHomes.near(level, farm, farm.getSpawnPoint(), pet.variant, 4);
                if (safe == null) continue;
                pet.position = Vec3.atBottomCenterOf(safe); data.setDirty();
            }
            var entity = ModEntities.PET.get().create(level); if (entity == null) continue;
            entity.setUUID(pet.id); entity.refresh(pet); entity.moveTo(pet.position.x, pet.position.y, pet.position.z, pet.yaw, 0);
            level.addFreshEntity(entity);
        }
    }
    public static void environment(ServerLevel level, PetRecord pet, PetEntity entity) {
        var farm = farm(pet.farm); if (farm == null) { entity.discard(); return; }
        boolean indoors = StardewTimeManager.get().getCurrentTime() >= 1200 || com.stardew.craft.weather.WeatherManager.isRaining(level) || pet.bowl == null;
        boolean unsafe = !farm.contains(entity.blockPosition()) || entity.getY() < farm.getOrigin().getY() - 5 || entity.isInWater();
        int day = StardewTimeManager.get().getAbsoluteDay();
        boolean night = StardewTimeManager.get().getCurrentTime() >= 1200;
        if (night && pet.restDay != day) {
            var rest = PetHomes.rest(level, farm, pet, entity.getRandom());
            if (rest != null) { pet.restPosition = rest; pet.restDay = day; PetWorldData.get(level.getServer()).setDirty(); }
        }
        if (night && pet.restPosition != null && PetHomes.safePosition(level, farm, pet.restPosition, pet.variant)) {
            if (entity.position().distanceToSqr(pet.restPosition) > .0025) { entity.getNavigation().stop(); entity.teleportTo(pet.restPosition.x, pet.restPosition.y, pet.restPosition.z); }
            if (!pet.indoors) { pet.indoors = true; PetWorldData.get(level.getServer()).setDirty(); }
            remember(level, entity); return;
        }
        if (indoors == pet.indoors && !unsafe) return;
        BlockPos target = indoors ? PetHomes.home(farm) : pet.bowl != null ? pet.bowl : farm.getSpawnPoint();
        var safe = PetHomes.near(level, farm, target, pet.variant, 4);
        if (safe != null) { entity.getNavigation().stop(); entity.teleportTo(safe.getX() + .5, safe.getY(), safe.getZ() + .5); pet.indoors = indoors; remember(level, entity); PetWorldData.get(level.getServer()).setDirty(); }
    }
    public static void interact(ServerPlayer player, PetEntity entity) {
        var data = PetWorldData.get(player.server); var pet = data.find(entity.getUUID());
        if (pet == null || !pet.variant.available() || !manages(player, pet.farm)) return;
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof StardewHatItem && pet.variant.wearsHat()) {
            ItemStack old = PortItemStacks.parseOptional(player.level().registryAccess(), pet.hat);
            if (!old.isEmpty()) { pet.hat = new CompoundTag(); if (!player.getInventory().add(old)) player.drop(old, false); }
            else { pet.hat = (CompoundTag) com.stardew.craft.port.PortItemStacks.save(held.copyWithCount(1), player.level().registryAccess()); held.shrink(1); }
            entity.playSound(com.stardew.craft.sound.ModSounds.DIRTY_HIT.get(), .6f, 1);
            data.setDirty(); entity.refresh(pet); return;
        }
        if (held.is(com.stardew.craft.item.ModItems.BUTTERFLY_POWDER.get())) { PetManagement.confirmRemoval(player, pet); return; }
        if (player.isShiftKeyDown()) { PetManagement.open(player, pet.id); return; }
        int day = StardewTimeManager.get().getAbsoluteDay();
        if (pet.petted.getOrDefault(player.getUUID(), -1) == day) { PetManagement.open(player, pet.id); return; }
        pet.petted.put(player.getUUID(), day);
        if (pet.careDay != day) {
            pet.careDay = day; pet.friendship(12);
            PetGifts.give(player, entity, pet); pet.timesPet++;
            if (pet.friendship == 1000 && !data.loved(pet.farm)) {
                data.markLoved(pet.farm);
                PetManagement.scheduleAdoptionMail(player.server, farm(pet.farm));
                lovesYou(player.server, farm(pet.farm), pet.name);
            }
        }
        data.setDirty(); entity.feedback.content(); entity.feedback.emote(20);
    }
    public static void message(ServerPlayer player, String key, Object... arguments) { com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("pet.stardewcraft." + key, arguments)); }
}
