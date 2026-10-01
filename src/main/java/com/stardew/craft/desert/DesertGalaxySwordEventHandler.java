package com.stardew.craft.desert;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.GalaxySwordRitualPayload;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
@SuppressWarnings("null")
public final class DesertGalaxySwordEventHandler {

    private static final String GALAXY_SWORD_FLAG = "galaxySword";
    private static final long RITUAL_DURATION_TICKS = 140L;
    private static final long STARDROP_SOUND_DELAY_TICKS = 30L;
    private static final long EMPTY_TRIGGER_SOUND_COOLDOWN_TICKS = 20L;
    private static final String TAG_LAST_EMPTY_TRIGGER_SOUND = "stardewcraft_galaxy_pillar_empty_sound";

    private static final Map<UUID, RitualState> ACTIVE_RITUALS = new HashMap<>();

    private DesertGalaxySwordEventHandler() {}

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        RitualState state = ACTIVE_RITUALS.get(player.getUUID());
        if (state != null) {
            tickActiveRitual(player, state);
            return;
        }

        if (!ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(GALAXY_SWORD_FLAG)) {
            return;
        }
        if (!player.blockPosition().equals(DesertGalaxyPillarBootstrap.RITUAL_TRIGGER_POS)) {
            return;
        }

        if (player.getMainHandItem().is(ModItems.PRISMATIC_SHARD.get())) {
            startRitual(player);
            return;
        }

        long now = player.serverLevel().getGameTime();
        long lastSoundTick = player.getPersistentData().getLong(TAG_LAST_EMPTY_TRIGGER_SOUND);
        if (now - lastSoundTick >= EMPTY_TRIGGER_SOUND_COOLDOWN_TICKS) {
            player.getPersistentData().putLong(TAG_LAST_EMPTY_TRIGGER_SOUND, now);
            player.playNotifySound(ModSounds.SPRING_BIRDS.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_RITUALS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (isRitualActive(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (isRitualActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isRitualActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isRitualActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isRitualActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isRitualActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isRitualActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isRitualActive(Player player) {
        return player instanceof ServerPlayer serverPlayer && ACTIVE_RITUALS.containsKey(serverPlayer.getUUID());
    }

    private static void startRitual(ServerPlayer player) {
        long startTick = player.serverLevel().getGameTime();
        ACTIVE_RITUALS.put(player.getUUID(), new RitualState(startTick, player.getInventory().selected));

        freezePlayer(player, player.getInventory().selected);
        PacketDistributor.sendToPlayer(player, new GalaxySwordRitualPayload((int) RITUAL_DURATION_TICKS));
        player.playNotifySound(ModSounds.CRIT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        StardewCraft.LOGGER.info("[DESERT] Started galaxy sword ritual for {}", player.getGameProfile().getName());
    }

    private static void tickActiveRitual(ServerPlayer player, RitualState state) {
        if (!ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            ACTIVE_RITUALS.remove(player.getUUID());
            return;
        }

        long elapsed = player.serverLevel().getGameTime() - state.startTick;
        forceSelectedSlot(player, state.selectedSlot);

        if (!state.stardropPlayed && elapsed >= STARDROP_SOUND_DELAY_TICKS) {
            state.stardropPlayed = true;
            player.playNotifySound(ModSounds.STARDROP.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        if (!state.rewardGranted && elapsed >= RITUAL_DURATION_TICKS) {
            state.rewardGranted = true;
            grantGalaxySword(player, state.selectedSlot);
            ACTIVE_RITUALS.remove(player.getUUID());
            return;
        }

        freezePlayer(player, state.selectedSlot);
    }

    private static void freezePlayer(ServerPlayer player, int selectedSlot) {
        forceSelectedSlot(player, selectedSlot);
        Vec3 center = Vec3.atBottomCenterOf(DesertGalaxyPillarBootstrap.RITUAL_TRIGGER_POS);
        player.connection.teleport(center.x, player.getY(), center.z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = true;
    }

    private static void forceSelectedSlot(ServerPlayer player, int selectedSlot) {
        if (player.getInventory().selected != selectedSlot) {
            player.getInventory().selected = selectedSlot;
            player.containerMenu.broadcastChanges();
        }
    }

    private static void grantGalaxySword(ServerPlayer player, int selectedSlot) {
        if (!consumeShard(player, selectedSlot)) {
            StardewCraft.LOGGER.warn("[DESERT] Ritual finished without prismatic shard in main hand for {}", player.getGameProfile().getName());
            return;
        }

        ItemStack galaxySword = new ItemStack(ModItems.GALAXY_SWORD.get());
        HoldUpItemPayload.sendTo(player, galaxySword);
        if (!player.getInventory().add(galaxySword.copy())) {
            player.drop(galaxySword, false);
        }

        PlayerDataManager.getPlayerData(player).addMailFlag(GALAXY_SWORD_FLAG);
        StardewCraft.LOGGER.info("[DESERT] {} obtained Galaxy Sword from desert ritual", player.getGameProfile().getName());
    }

    private static boolean consumeShard(ServerPlayer player, int selectedSlot) {
        forceSelectedSlot(player, selectedSlot);
        ItemStack selected = player.getInventory().getItem(selectedSlot);
        if (!selected.isEmpty() && selected.is(ModItems.PRISMATIC_SHARD.get())) {
            selected.shrink(1);
            player.containerMenu.broadcastChanges();
            return true;
        }
        return false;
    }

    private static final class RitualState {
        private final long startTick;
        private final int selectedSlot;
        private boolean stardropPlayed;
        private boolean rewardGranted;

        private RitualState(long startTick, int selectedSlot) {
            this.startTick = startTick;
            this.selectedSlot = selectedSlot;
        }
    }
}