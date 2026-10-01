package com.stardew.craft.animal.runtime;

import com.stardew.craft.building.runtime.*;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

public final class LivestockShop {
    // FarmAnimals.PurchasePrice 400, Object.salePrice multiplies it by two.
    public static final int CHICKEN_PRICE = 800;
    private record Session(UUID nonce, long expires, Set<UUID> homes,long generation) {}
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private LivestockShop() {}
    public static boolean validSession(ServerPlayer player, UUID nonce) {
        var session = SESSIONS.get(player.getUUID());
        return session != null && session.nonce.equals(nonce) && session.expires >= player.serverLevel().getServer().getTickCount();
    }
    public static void clear() { SESSIONS.clear(); }
    public static UUID openForPlayer(ServerPlayer player) {
        var tag=snapshot(player);PacketDistributor.sendToPlayer(player,new LivestockShopPayload(tag));return tag.getUUID("Nonce");
    }
    private static CompoundTag snapshot(ServerPlayer player) {
        var server = player.serverLevel().getServer(); LivestockService.recover(server);
        var buildings = BuildingWorldData.get(server); var animals = LivestockWorldData.get(server);
        var rows = new ListTag(); var ids = new HashSet<UUID>();
        for (var snapshot : buildings.all()) {
            if (!PrefabDefinitions.available(snapshot) || !BuildingService.canManage(player, snapshot)) continue;
            if (snapshot.phase() != BuildingRecord.Phase.READY && snapshot.phase() != BuildingRecord.Phase.UPGRADING) continue;
            if (buildings.transfer(snapshot.id()) != null) continue;
            var level = LivestockService.level(server, snapshot); if (level == null) continue;
            LivestockHomes.load(level, LivestockHomes.bounds(snapshot));
            if (snapshot.mode() == BuildingRecord.Mode.SELF_BUILT) BuildingResidence.refresh(level, snapshot);
            var home = buildings.find(snapshot.id());
            if (!LivestockHomes.accepts(home)) continue;
            var row = new CompoundTag(); row.putUUID("Id", home.id()); row.putLong("Revision", home.revision());
            row.putInt("X", home.manager().getX()); row.putInt("Y", home.manager().getY()); row.putInt("Z", home.manager().getZ());
            row.putString("BuildingName",home.displayName()); row.putString("Family", home.family().toString()); row.putInt("Tier", home.tier()); row.putInt("Used", animals.occupancy(home.id())); row.putInt("Capacity", LivestockHomes.capacity(level,home));
            LivestockHomes.describe(row,level,home);rows.add(row); ids.add(home.id());
        }
        UUID nonce = UUID.randomUUID();
        SESSIONS.put(player.getUUID(), new Session(nonce, server.getTickCount() + 6000L, ids,com.stardew.craft.animal.model.FarmAnimalDefinitions.generation()));
        var tag = new CompoundTag(); tag.putUUID("Nonce", nonce); tag.put("Homes", rows);
        tag.putInt("Money", PlayerStardewDataAPI.getMoney(player));
        var catalog=new ListTag();
        for(var species:LivestockSpecies.values()) if(species.known() && species.price()>=0) {
            var row=new CompoundTag(); LivestockUiData.describe(row,species);row.putInt("Price",species.price());
            boolean homeFound=rows.stream().map(t->(CompoundTag)t).anyMatch(h->LivestockHomes.offered(h,row) && h.getInt("Used")<h.getInt("Capacity"));
            String reason=!unlocked(player,species)||!LivestockProjection.supported(player.serverLevel(),species)?"unavailable":!homeFound?"no_home":PlayerStardewDataAPI.getMoney(player)<species.price()?"money":"";
            row.putBoolean("Available",reason.isEmpty());row.putString("Reason",reason);catalog.add(row);
        }
        tag.put("Catalog",catalog);
        return tag;
    }
    public static String purchase(ServerPlayer player, UUID nonce, UUID homeId, long revision, String name) {
        return purchase(player, nonce, homeId, revision, name, LivestockSpecies.WHITE_CHICKEN.id());
    }
    public static String purchase(ServerPlayer player, UUID nonce, UUID homeId, long revision, String name, String speciesId) {
        LivestockSpecies species;
        try { species = LivestockSpecies.parse(speciesId); } catch (IllegalArgumentException invalid) { return "unavailable"; }
        if (!species.known() || species.price() < 0 || !unlocked(player,species)) return "unavailable";
        var server = player.serverLevel().getServer(); LivestockService.recover(server);
        var animals = LivestockWorldData.get(server);
        // The server-issued nonce is also the permanent purchase receipt/animal identity.
        var completed = animals.find(nonce);
        if (completed != null) return completed.owner().equals(player.getUUID()) ? "" : "permission";
        var session = SESSIONS.get(player.getUUID());
        if (session == null || !session.nonce.equals(nonce) || session.expires < server.getTickCount() || session.generation!=com.stardew.craft.animal.model.FarmAnimalDefinitions.generation() || !session.homes.contains(homeId)) return "expired";
        var buildings = BuildingWorldData.get(server); var home = buildings.find(homeId);
        if (home == null || !PrefabDefinitions.available(home) || !BuildingService.canManage(player, home)) return "permission";
        if (buildings.transfer(home.id()) != null) return "unavailable";
        var level = LivestockService.level(server, home); if (level == null) return "unavailable";
        LivestockHomes.load(level, LivestockHomes.bounds(home));
        if (home.mode() == BuildingRecord.Mode.SELF_BUILT) BuildingResidence.refresh(level, home);
        home = buildings.find(homeId);
        if (!LivestockHomes.accepts(level,home, species) || home.revision() != revision) return "unavailable";
        if (animals.occupancy(homeId) >= LivestockHomes.capacity(level,home)) return "full";
        name = name.strip();
        if (name.isEmpty() || name.length() > 32 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) return "name_required";
        var selected=LivestockSpecies.parse(com.stardew.craft.animal.service.AnimalShopService.selectPurchasedAnimalType(species.definitionId(),player));
        if(!LivestockProjection.supported(level,selected)||!LivestockHomes.accepts(level,home,selected))return "unavailable";
        if (LivestockHomes.spawn(level, home, selected, selected.matureDays() > 0) == null) return "no_floor";
        if (PlayerStardewDataAPI.getMoney(player) < species.price()) return "money";
        if (species.price() > 0 && !PlayerStardewDataAPI.removeMoney(player, species.price())) return "money";
        animals.put(new LivestockRecord(nonce, player.getUUID(), home.farmId(), home.id(), name,
                animals.allocateRandomId(), StardewTimeManager.get().getAbsoluteDay(), LivestockCare.purchased()).species(selected));
        SESSIONS.remove(player.getUUID()); LivestockService.project(server); return "";
    }
    private static boolean unlocked(ServerPlayer player,LivestockSpecies species) {
        var d=species.definition();
        return d==null || d.unlockCondition()==null || com.stardew.craft.api.v1.condition.StardewConditions.test(
                d.unlockCondition(),com.stardew.craft.api.v1.condition.StardewConditionContext.forPlayer(player)).result().orElse(false);
    }
    public static void submit(ServerPlayer player, LivestockPurchasePayload request) {
        String outcome = purchase(player, request.nonce(), request.home(), request.revision(), request.name(), request.species());
        var tag = outcome.isEmpty()?new CompoundTag():snapshot(player); tag.putString("Result", outcome);tag.putUUID("RequestNonce",request.nonce());
        if(outcome.isEmpty()){var purchased=LivestockWorldData.get(player.server).find(request.nonce());if(purchased!=null)tag.putString("PurchasedName",purchased.name());}
        PacketDistributor.sendToPlayer(player, new LivestockShopPayload(tag));
    }
}
