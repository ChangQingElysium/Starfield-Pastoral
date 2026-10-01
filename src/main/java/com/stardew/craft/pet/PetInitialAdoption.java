package com.stardew.craft.pet;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.time.StardewTimePauseService;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Old farms without an initial choice get one owner questionnaire per login, after other setup. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class PetInitialAdoption {
    private static final Set<UUID> pending = new HashSet<>();
    private PetInitialAdoption() {}

    public static boolean needed(ServerPlayer player) {
        var farm = FarmInstanceRegistry.get(player.server).getFarm(player.getUUID());
        return farm != null && PetWorldData.get(player.server).needsInitialChoice(farm.getInstanceId());
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && needed(player)) pending.add(player.getUUID());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        pending.remove(event.getEntity().getUUID()); PetManagement.clear(event.getEntity().getUUID());
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { pending.clear(); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (var id : Set.copyOf(pending)) {
            var player = event.getServer().getPlayerList().getPlayer(id);
            if (player == null) pending.remove(id); else poll(player);
        }
    }
    public static void poll(ServerPlayer player) {
        if (!pending.contains(player.getUUID())) return;
        if (!needed(player)) { pending.remove(player.getUUID()); return; }
        var farm = FarmInstanceRegistry.get(player.server).getFarm(player.getUUID());
        if (!farm.isInitialized() || !PlayerDataManager.getPlayerData(player).isProfileComplete()
                || !player.isAlive() || player.isSleeping()
                || com.stardew.craft.cutscene.server.ServerCutsceneTracker.isActive(player.getUUID())
                || com.stardew.craft.player.PassOutService.isKnockedOut(player)) return;
        StardewTimePauseService.requestGameplayFeedbackState(player);
        if (!StardewTimePauseService.canPlayGameplayFeedback(player)) return;
        StardewTimePauseService.completeGameplayFeedback(player);
        pending.remove(player.getUUID());
        PetManagement.openInitial(player);
    }
}
