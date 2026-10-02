package com.stardew.craft.animal.runtime;

import com.stardew.craft.building.runtime.*;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.player.PlayerStardewDataAPI;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Also lists animals whose self-built manager was removed, so they can be housed again. */
public final class LivestockManagement {
    private record Session(UUID nonce, UUID farm, long expires, Map<UUID,Integer> sellQuotes) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private LivestockManagement() {}
    public static void clear() { sessions.clear(); }
    public static UUID open(ServerPlayer player) { return open(player, ""); }
    public static UUID open(ServerPlayer player, String selected) { return open(player,selected,null); }
    public static UUID openFromLedger(ServerPlayer player,String selected,UUID ledger) { return open(player,selected,null,ledger); }
    private static UUID open(ServerPlayer player,String selected,UUID reply) { return open(player,selected,reply,null); }
    private static UUID open(ServerPlayer player,String selected,UUID reply,UUID ledger) {
        var server = player.serverLevel().getServer(); LivestockService.recover(server);
        var farm = FarmInstanceRegistry.get(server).getFarmForPlayer(player.getUUID()); if (farm == null) return null;
        var nonce = UUID.randomUUID(); sessions.put(player.getUUID(), new Session(nonce, farm.getInstanceId(), server.getTickCount() + 6000L, new HashMap<>()));
        var tag = new CompoundTag(); tag.putString("Kind", "manage"); tag.putUUID("Nonce", nonce); tag.putString("Selected",selected);if(reply!=null)tag.putUUID("ReplyNonce",reply);
        if(ledger!=null)tag.putUUID("ReplyLedgerSession",ledger);
        var rows = new ListTag(); var data = LivestockWorldData.get(server);
        for (var a : data.all()) if (a.farm().equals(farm.getInstanceId())) {
            var row = new CompoundTag(); row.putUUID("Id", a.id()); row.putUUID("Home", a.home()); row.putString("Name", a.name());
            row.putString("Species", a.species().id()); row.putInt("SellPrice", a.species().sellPrice(a.care().friendship()));
            LivestockUiData.care(row,a); sessions.get(player.getUUID()).sellQuotes.put(a.id(),row.getInt("SellPrice"));
            var residence = BuildingWorldData.get(server).find(a.home());
            if (residence == null || residence.phase() == BuildingRecord.Phase.MISSING)
                row.putString("HomeIssue", "livestock.stardewcraft.awaiting_home");
            else if (residence.residence() != BuildingRecord.Residence.VALID)
                row.putString("HomeIssue", "livestock.stardewcraft.home_needs_facilities");
            row.putBoolean("Newborn", data.newborn(a.id())); row.putBoolean("Reproduction", a.reproduction()); row.putBoolean("CanReproduce", a.species().pregnancy()); rows.add(row);
        }
        tag.put("Animals", rows); var homes = new ListTag(); var buildings = BuildingWorldData.get(server);
        for (var snapshot : buildings.all()) {
            if (!snapshot.farmId().equals(farm.getInstanceId()) || !PrefabDefinitions.available(snapshot)) continue;
            if (buildings.transfer(snapshot.id()) != null) continue;
            var level = LivestockService.level(server, snapshot); if (level == null) continue;
            LivestockHomes.load(level, LivestockHomes.bounds(snapshot));
            if (snapshot.mode() == BuildingRecord.Mode.SELF_BUILT) BuildingResidence.refresh(level, snapshot);
            var home = buildings.find(snapshot.id()); if (!LivestockHomes.accepts(home)) continue;
            var row = new CompoundTag(); row.putUUID("Id", home.id()); row.putString("Family", home.family().toString()); row.putInt("Tier", home.tier());
            row.putInt("X", home.manager().getX()); row.putInt("Y", home.manager().getY()); row.putInt("Z", home.manager().getZ());
            row.putString("BuildingName", home.displayName()); row.putInt("Used", data.occupancy(home.id())); row.putInt("Capacity", LivestockHomes.capacity(level,home)); LivestockHomes.describe(row,level,home);homes.add(row);
        }
        tag.put("Homes", homes); PacketDistributor.sendToPlayer(player, new LivestockShopPayload(tag)); return nonce;
    }
    public static void submit(ServerPlayer player, LivestockManagePayload request) {
        if (request.action().equals("birth")) {
            String result = LivestockBirths.name(player,request.animal(),request.name()); if (!result.isEmpty()) LivestockService.message(player,result); return;
        }
        if (request.action().equals("open")) {
            if (LivestockShop.validSession(player, request.nonce())) open(player);
            return;
        }
        String result = apply(player, request);
        if (!result.isEmpty()) LivestockService.message(player, result);
        open(player,request.action().equals("sell") && result.isEmpty()?"":request.animal().toString(),request.nonce());
    }
    public static String apply(ServerPlayer player, LivestockManagePayload request) {
        var server = player.serverLevel().getServer(); LivestockService.recover(server);
        var session = sessions.get(player.getUUID()); var farm = FarmInstanceRegistry.get(server).getFarmForPlayer(player.getUUID());
        if (session == null || farm == null || !session.nonce.equals(request.nonce()) || session.expires < server.getTickCount() || !session.farm.equals(farm.getInstanceId())) return "expired";
        var data = LivestockWorldData.get(server); var animal = data.find(request.animal());
        if (animal == null || !animal.farm().equals(session.farm)) return "permission";
        var buildings = BuildingWorldData.get(server); if (buildings.transfer(animal.home()) != null) return "unavailable";
        switch (request.action()) {
            case "rename" -> {
                String name = request.name().strip();
                if (name.isEmpty() || name.length() > 32 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xA7)) return "name_required";
                if (data.nameTaken(animal.farm(), name, animal.id())) return "name_taken";
                data.put(animal.rename(name)); data.newborn(animal.id(), false);
            }
            case "sell" -> {
                if (!java.util.Objects.equals(session.sellQuotes.get(animal.id()),animal.species().sellPrice(animal.care().friendship()))) return "expired";
                data.remove(animal.id()); PlayerStardewDataAPI.addMoney(player, animal.species().sellPrice(animal.care().friendship()));
                for (var level : server.getAllLevels()) { var entity = level.getEntity(animal.id()); if (entity != null) entity.discard(); }
            }
            case "enable", "disable" -> {
                if (!animal.species().pregnancy()) return "unavailable";
                data.put(animal.reproduction(request.action().equals("enable")));
            }
            case "move" -> {
                var home = buildings.find(request.home());
                if (home == null || !PrefabDefinitions.available(home) || !home.farmId().equals(session.farm) || buildings.transfer(home.id()) != null || !BuildingService.canManage(player, home)) return "permission";
                var level = LivestockService.level(server, home); if (level == null) return "unavailable";
                LivestockHomes.load(level, LivestockHomes.bounds(home));
                if (home.mode() == BuildingRecord.Mode.SELF_BUILT) BuildingResidence.refresh(level, home);
                home = buildings.find(home.id());
                if (!LivestockHomes.accepts(level,home, animal.species())) return "unavailable";
                if (!home.id().equals(animal.home()) && data.occupancy(home.id()) >= LivestockHomes.capacity(level,home)) return "full";
                if (LivestockHomes.spawn(level, home, animal.species(), animal.baby()) == null) return "no_floor";
                var previousHome = buildings.find(animal.home());
                var moved = animal.rehome(home.id());
                // Animals without a home were paused, not neglected for the missing interval.
                if (previousHome == null || previousHome.phase() == BuildingRecord.Phase.MISSING)
                    moved = moved.withCare(com.stardew.craft.time.StardewTimeManager.get().getAbsoluteDay(), moved.care());
                data.put(moved);
                // Products stranded by removal of a self-built manager follow the recovered animal.
                if (buildings.find(animal.home()) == null || buildings.find(animal.home()).phase()==BuildingRecord.Phase.MISSING) for (var egg : data.eggs()) if (egg.animal().equals(animal.id()))
                    data.product(new LivestockWorldData.Product(egg.id(), egg.animal(), home.id(), egg.large(), egg.quality(), egg.item(), egg.count(), null, egg.stackData()));
                for (var world : server.getAllLevels()) { var entity = world.getEntity(animal.id()); if (entity != null) entity.discard(); }
            }
            default -> { return "unavailable"; }
        }
        sessions.remove(player.getUUID()); LivestockService.project(server); return "";
    }
}
