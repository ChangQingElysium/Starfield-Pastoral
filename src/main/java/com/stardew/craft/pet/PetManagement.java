package com.stardew.craft.pet;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.mail.MailService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** Purpose-bound, expiring single-use requests; all prices, ownership and bowl assignments stay server-side. */
public final class PetManagement {
    public static final String ADOPTION_MAIL = "MarniePetAdoption";
    public static final String REJECTED_ADOPTION_FLAG = "MarniePetRejectedAdoption";
    private record Session(UUID nonce, UUID farm, String kind, UUID removal, long expires, BlockPos bowl, long revision) {}
    private static final Map<UUID, Session> sessions = new HashMap<>();
    private PetManagement() {}
    public static void clear() { sessions.clear(); }
    public static void clear(UUID player) { sessions.remove(player); }
    public static boolean unlocked(ServerPlayer player) {
        var farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID());
        if (farm == null) return false;
        var pets = PetWorldData.get(player.server);
        var playerData = PlayerDataManager.getPlayerData(player);
        return playerData.hasMailFlag(ADOPTION_MAIL)
                || playerData.hasMailFlag(REJECTED_ADOPTION_FLAG)
                || StardewTimeManager.get().getCurrentYear() >= 2
                && pets.forFarm(farm.getInstanceId()).isEmpty();
    }
    public static void scheduleAdoptionMail(net.minecraft.server.MinecraftServer server,
                                            com.stardew.craft.farm.FarmInstance farm) {
        if (farm == null || !PetWorldData.get(server).loved(farm.getInstanceId())) return;
        for (UUID farmerId : farm.getAllFarmers()) {
            var data = PlayerDataManager.getPlayerData(farmerId);
            if (data.hasMailFlag(ADOPTION_MAIL)
                    || data.getMailbox().contains(ADOPTION_MAIL)
                    || data.getMailForTomorrow().contains(ADOPTION_MAIL)) continue;
            var online = server.getPlayerList().getPlayer(farmerId);
            if (online != null) MailService.addMailForTomorrow(online, ADOPTION_MAIL);
            else data.addMailForTomorrow(ADOPTION_MAIL);
        }
    }
    public static void open(ServerPlayer player, UUID selected) { show(player, "manage", selected, null); }
    public static void openBowl(ServerPlayer player, BlockPos bowl) {
        showBowl(player, bowl, null);
    }
    private static void showBowl(ServerPlayer player, BlockPos pos, UUID reply) {
        var record = PetBowlBuildings.ensure(player.serverLevel(), pos);
        if (record == null || !PetService.manages(player, record.farmId()) || !player.canInteractWithBlock(pos, 1)
                || !com.stardew.craft.building.runtime.BuildingService.canManage(player, record)) return;
        var occupant = PetWorldData.get(player.server).occupant(pos);
        show(player, "bowl", occupant == null ? null : occupant.id, reply, record);
    }
    public static void openInitial(ServerPlayer player) { if (PetInitialAdoption.needed(player)) show(player, "initial", null, null); }
    public static void openShop(ServerPlayer player) { show(player, "adopt", null, null); }
    public static void openBowls(ServerPlayer player) { if (com.stardew.craft.shop.RobinService.isPlayerAtCounter(player)) show(player, "bowls", null, null); }
    public static void confirmRemoval(ServerPlayer player, PetRecord pet) { show(player, "remove", pet.id, null); }
    private static void show(ServerPlayer player, String kind, UUID selected, UUID reply) { show(player, kind, selected, reply, null); }
    private static void show(ServerPlayer player, String kind, UUID selected, UUID reply, com.stardew.craft.building.runtime.BuildingRecord building) {
        var farm = FarmInstanceRegistry.get().getFarmForPlayer(player.getUUID()); if (farm == null) return;
        var data = PetWorldData.get(player.server); var nonce = UUID.randomUUID();
        sessions.put(player.getUUID(), new Session(nonce, farm.getInstanceId(), kind, kind.equals("remove") ? selected : null, player.server.getTickCount() + 6000L, building == null ? null : building.manager(), building == null ? -1 : building.revision()));
        var tag = new CompoundTag(); tag.putUUID("Nonce", nonce); tag.putString("Kind", kind); if (selected != null) tag.putUUID("Selected", selected); if (reply != null) tag.putUUID("Reply", reply);
        tag.putBoolean("CanChooseInitial", PetInitialAdoption.needed(player));
        if (building != null) { tag.putLong("BowlPosition", building.manager().asLong()); tag.putString("BowlStyle", PetWorldData.get(player.server).bowl(building.manager()).style()); }
        tag.putInt("Hardwood", player.getInventory().countItem(com.stardew.craft.fishpond.service.FishPondQualifiedItemService.createItemStack("(O)709", 1).getItem()));
        tag.putInt("Money", PlayerStardewDataAPI.getMoney(player)); tag.putBoolean("Unlocked", unlocked(player));
        var pets = new ListTag();
        for (var pet : data.forFarm(farm.getInstanceId())) {
            var row = new CompoundTag(); row.putUUID("Id", pet.id); row.putString("Name", pet.name); row.putString("Variant", pet.variant.id()); row.putInt("Friendship", pet.friendship);
            row.putBoolean("Available", pet.variant.available());
            row.putBoolean("Petted", pet.petted.getOrDefault(player.getUUID(), -1) == StardewTimeManager.get().getAbsoluteDay()); row.putBoolean("HasHat", !pet.hat.isEmpty());
            if (pet.position != null) row.putLong("Position", BlockPos.containing(pet.position).asLong()); if (pet.bowl != null) row.putLong("Bowl", pet.bowl.asLong()); pets.add(row);
        }
        tag.put("Pets", pets); var bowls = new ListTag();
        for (var bowl : data.bowls()) if (bowl.farm().equals(farm.getInstanceId())) {
            var row = new CompoundTag(); row.putLong("Position", bowl.position().asLong()); row.putString("Style", bowl.style()); row.putBoolean("Full", bowl.wateredDay() == StardewTimeManager.get().getAbsoluteDay());
            var occupant = data.occupant(bowl.position()); if (occupant != null) row.putUUID("Pet", occupant.id); bowls.add(row);
        }
        tag.put("Bowls", bowls); PacketDistributor.sendToPlayer(player, new PetScreenPayload(tag));
    }
    public static void submit(ServerPlayer player, PetActionPayload request) {
        var session = sessions.get(player.getUUID());
        if (session != null && session.kind().equals("initial") && session.nonce().equals(request.nonce())
                && session.expires() < player.server.getTickCount() && PetService.manages(player, session.farm()) && PetInitialAdoption.needed(player)) {
            PetService.message(player, "expired"); show(player, "initial", null, request.nonce()); return;
        }
        if (session == null || !session.nonce().equals(request.nonce()) || session.expires() < player.server.getTickCount() || !PetService.manages(player, session.farm())) { PetService.message(player, "expired"); return; }
        sessions.remove(player.getUUID());
        if (session.kind().equals("bowl")) { submitBowl(player, session, request); return; }
        String result = apply(player, session, request);
        if (!result.isEmpty()) PetService.message(player, result);
        if (session.kind().equals("initial")) {
            if (PetInitialAdoption.needed(player)) show(player, "initial", null, request.nonce());
            else { var done = new CompoundTag(); done.putString("Kind", "initial_done"); done.putUUID("Reply", request.nonce()); PacketDistributor.sendToPlayer(player, new PetScreenPayload(done)); }
            return;
        }
        if (session.kind().equals("adopt") && result.isEmpty()) {
            var done = new CompoundTag(); done.putString("Kind", "adopt_done"); done.putUUID("Reply", request.nonce());
            PacketDistributor.sendToPlayer(player, new PetScreenPayload(done));
            return;
        }
        if (session.kind().equals("remove") && result.isEmpty()) return;
        show(player, session.kind().equals("adopt") || session.kind().equals("bowls") ? session.kind() : "manage", request.pet(), request.nonce());
    }
    private static void submitBowl(ServerPlayer player, Session session, PetActionPayload request) {
        var record = PetBowlBuildings.ensure(player.serverLevel(), session.bowl());
        var buildings = com.stardew.craft.building.runtime.BuildingWorldData.get(player.server);
        boolean valid = record != null && record.revision() == session.revision() && record.farmId().equals(session.farm())
                && player.canInteractWithBlock(session.bowl(), 1) && com.stardew.craft.building.runtime.BuildingService.canManage(player, record)
                && buildings.transfer(record.id()) == null && !com.stardew.craft.building.runtime.BuildingRemovalJournal.get(player.server).contains(record.id());
        if (!valid) { PetService.message(player, "expired"); closeBowl(player, request.nonce()); return; }
        if (request.action().equals("bowl_initial") && PetInitialAdoption.needed(player)) { show(player, "initial", null, request.nonce()); return; }
        if (request.action().equals("bowl_manage")) {
            var occupant = PetWorldData.get(player.server).occupant(session.bowl());
            show(player, "manage", occupant == null ? null : occupant.id, request.nonce()); return;
        }
        boolean done = switch (request.action()) {
            case "bowl_move" -> PetBowlBuildings.beginMove(player, session.bowl());
            case "bowl_demolish" -> com.stardew.craft.building.runtime.BuildingDemolition.perform(player, record);
            default -> false;
        };
        if (done) closeBowl(player, request.nonce()); else showBowl(player, session.bowl(), request.nonce());
    }
    private static void closeBowl(ServerPlayer player, UUID reply) {
        var done = new CompoundTag(); done.putString("Kind", "bowl_done"); done.putUUID("Reply", reply);
        PacketDistributor.sendToPlayer(player, new PetScreenPayload(done));
    }
    private static String apply(ServerPlayer player, Session session, PetActionPayload request) {
        var data = PetWorldData.get(player.server);
        if (session.kind().equals("initial")) {
            if (!PetInitialAdoption.needed(player)) return "expired";
            var farm = FarmInstanceRegistry.get(player.server).getFarm(player.getUUID());
            if (!request.action().equals("initial")) return "expired";
            var choice = request.selection();
            if (choice.variant().isEmpty() || !PetService.selectInitial(player, farm, choice.variant(), choice.name())) return "name_required";
            var level = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
            if (level != null) { PetHomes.prepare(level, farm); PetService.project(level); }
            PetService.message(player, "initial_arrival"); return "";
        }
        if (request.action().equals("buy_bowl")) {
            if (!session.kind().equals("bowls") || !com.stardew.craft.shop.RobinService.isPlayerAtCounter(player)) return "expired";
            var item = switch (request.value()) { case "wood" -> ModItems.PET_BOWL_WOOD.get(); case "stone" -> ModItems.PET_BOWL_STONE.get(); case "hay" -> ModItems.PET_BOWL_HAY.get(); default -> null; };
            if (item == null) return "expired";
            var wood = com.stardew.craft.fishpond.service.FishPondQualifiedItemService.createItemStack("(O)709", 1).getItem();
            if (player.getInventory().countItem(wood) < 25) return "hardwood_required";
            if (!PlayerStardewDataAPI.removeMoney(player, 5000)) return "money_required";
            int left = 25;
            for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
                var stack = player.getInventory().getItem(i); if (!stack.is(wood)) continue;
                int take = Math.min(left, stack.getCount()); stack.shrink(take); left -= take;
            }
            var bowl = new ItemStack(item); if (!player.getInventory().add(bowl)) player.drop(bowl, false);
            player.getInventory().setChanged(); return "";
        }
        if (request.action().equals("adopt")) {
            if (!session.kind().equals("adopt") || !unlocked(player)) return "locked";
            var choice = request.selection();
            PetVariant variant = PetVariant.find(choice.variant()).filter(PetVariant::adoptable).orElse(null); String name = choice.name();
            if (variant == null || name.isBlank()) return "name_required";
            var free = data.bowls().stream().filter(b -> b.farm().equals(session.farm()) && data.occupant(b.position()) == null).findFirst().orElse(null);
            if (!PlayerStardewDataAPI.removeMoney(player, variant.price())) return "money_required";
            var pet = new PetRecord(session.nonce(), session.farm(), variant, name, StardewTimeManager.get().getAbsoluteDay());
            if (free != null) pet.bowl = free.position();
            data.put(pet);
            var level = player.server.getLevel(ModDimensions.STARDEW_VALLEY); if (level != null) PetService.project(level);
            PetService.message(player, "adopted", name);
            return "";
        }
        var pet = data.find(request.pet());
        if (pet == null || !pet.farm.equals(session.farm())) return "expired";
        if (!pet.variant.available()) return "unavailable";
        if (request.action().equals("remove")) {
            if (!session.kind().equals("remove") || !pet.id.equals(session.removal()) || !player.getMainHandItem().is(ModItems.BUTTERFLY_POWDER.get())
                    || player.level().dimension() != ModDimensions.STARDEW_VALLEY || pet.position == null || player.position().distanceToSqr(pet.position) > 64) return "expired";
            ItemStack hat = ItemStack.parseOptional(player.registryAccess(), pet.hat);
            var departing = player.serverLevel().getEntity(pet.id);
            if (departing instanceof PetEntity entity) entity.feedback.content();
            player.serverLevel().playSound(null, BlockPos.containing(pet.position), com.stardew.craft.sound.ModSounds.FIREBALL.get(), net.minecraft.sounds.SoundSource.PLAYERS, .7f, 1);
            data.remove(pet.id); player.getMainHandItem().shrink(1);
            if (!hat.isEmpty() && !player.getInventory().add(hat)) player.drop(hat, false);
            var entity = player.serverLevel().getEntity(pet.id); if (entity != null) entity.discard();
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, pet.position.x, pet.position.y + .5, pet.position.z, 6, .5, .4, .5, .01);
            player.serverLevel().sendParticles(ParticleTypes.CLOUD, pet.position.x, pet.position.y + .2, pet.position.z, 8, .25, .2, .25, .02);
            PetButterflies.spawn(player.serverLevel(), pet.position);
            PetService.message(player, "goodbye", pet.name); return "";
        }
        if (!session.kind().equals("manage")) return "expired";
        switch (request.action()) {
            case "rename" -> { String name = PetRecord.cleanName(request.value()); if (name.isBlank()) return "name_required"; pet.name = name; }
            case "bind" -> {
                var bowl = data.bowl(request.bowl()); var occupant = data.occupant(request.bowl());
                if (bowl == null || !bowl.farm().equals(pet.farm) || occupant != null && !occupant.id.equals(pet.id)) return "bowl_occupied";
                pet.bowl = bowl.position();
            }
            case "unhat" -> { var hat = ItemStack.parseOptional(player.registryAccess(), pet.hat); pet.hat = new CompoundTag(); if (!hat.isEmpty() && !player.getInventory().add(hat)) player.drop(hat, false); }
            default -> { return "expired"; }
        }
        data.setDirty(); var level = player.server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (level != null && level.getEntity(pet.id) instanceof PetEntity entity) entity.refresh(pet);
        return "";
    }
}
