package com.stardew.craft.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.TableDisplayBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.PowerSpecialItem;
import com.stardew.craft.item.PowerSpecialItemService;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.ref.WeakReference;

/** Installs the Witch's Swamp/Hut portals and handles the per-player Magic Ink reward. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WitchAreaService {
    public static final String HUT_ENTRANCE_TARGET_ID = "witch_hut_enter";
    public static final String HUT_EXIT_TARGET_ID = "witch_hut_exit";

    public static final BlockPos HUT_ENTRANCE_PORTAL_BASE = new BlockPos(51, 48, -243);
    public static final BlockPos HUT_EXIT_PORTAL_BASE = new BlockPos(51, 40, -242);
    public static final BlockPos MAGIC_INK_TABLE_POS = new BlockPos(48, 40, -245);

    private static final BlockPos HUT_ENTRANCE_DESTINATION = new BlockPos(51, 40, -243);
    private static final BlockPos HUT_EXIT_DESTINATION = new BlockPos(51, 48, -242);
    private static final String PORTAL_TARGET_PREFIX = "sdv_portal_target:";
    public static final String PICKED_UP_MAGIC_INK_FLAG = "hasPickedUpMagicInk";
    private static final int MAINTENANCE_INTERVAL = 40;

    private static WeakReference<ServerLevel> activeLevel = new WeakReference<>(null);
    private static boolean portalsInstalled;

    private WitchAreaService() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        if (activeLevel.get() != level) {
            activeLevel = new WeakReference<>(level);
            portalsInstalled = false;
        }
        if (level.getGameTime() % MAINTENANCE_INTERVAL == 0L) {
            ensureInstalled(level);
        }
    }

    public static void enterHut(ServerPlayer player) {
        player.playNotifySound(ModSounds.STAIRS_DOWN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        ModTeleport.to(player, player.serverLevel(), HUT_ENTRANCE_DESTINATION,
                Direction.NORTH.toYRot(), 0.0F);
    }

    public static void exitHut(ServerPlayer player) {
        player.playNotifySound(ModSounds.STAIRS_DOWN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        ModTeleport.to(player, player.serverLevel(), HUT_EXIT_DESTINATION,
                Direction.SOUTH.toYRot(), 0.0F);
    }

    public static boolean hasClaimedMagicInk(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return data.hasMailFlag(PICKED_UP_MAGIC_INK_FLAG)
                || data.hasMailFlag(PowerSpecialItemService.MAGIC_INK_FLAG)
                || data.hasSpecialItem(PowerSpecialItemService.MAGIC_INK_ID);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())
                || !MAGIC_INK_TABLE_POS.equals(event.getPos())) {
            return;
        }

        if (!hasClaimedMagicInk(player)) {
            grantMagicInk(player);
        }
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                && MAGIC_INK_TABLE_POS.equals(event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static void ensureInstalled(ServerLevel level) {
        if (!portalsInstalled) {
            InteriorSubspaceManager.placePortalTriggerArea(level, HUT_ENTRANCE_PORTAL_BASE, 2, 1, 1,
                    "witch_hut_entrance", PORTAL_TARGET_PREFIX + HUT_ENTRANCE_TARGET_ID);
            InteriorSubspaceManager.placePortalTriggerArea(level, HUT_EXIT_PORTAL_BASE, 2, 1, 1,
                    "witch_hut_exit", PORTAL_TARGET_PREFIX + HUT_EXIT_TARGET_ID);
            portalsInstalled = true;
        }
        ensureMagicInkDisplay(level);
    }

    private static void ensureMagicInkDisplay(ServerLevel level) {
        if (!level.hasChunkAt(MAGIC_INK_TABLE_POS)
                || !level.getBlockState(MAGIC_INK_TABLE_POS).is(ModBlocks.SPRUCE_TABLE.get())
                || !(level.getBlockEntity(MAGIC_INK_TABLE_POS) instanceof TableDisplayBlockEntity table)) {
            return;
        }
        if (!table.getDisplayItem().is(ModItems.MAGIC_INK.get())) {
            table.setDisplayItem(new ItemStack(ModItems.MAGIC_INK.get()), 0.0F);
        }
    }

    private static void grantMagicInk(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        data.addMailFlag(PICKED_UP_MAGIC_INK_FLAG);

        ItemStack reward = new ItemStack(ModItems.MAGIC_INK.get());
        if (!player.getInventory().add(reward.copy())) {
            ItemEntity dropped = player.drop(reward.copy(), false);
            if (dropped != null) {
                dropped.setTarget(player.getUUID());
            }
        }
        player.getInventory().setChanged();

        if (ModItems.MAGIC_INK.get() instanceof PowerSpecialItem item) {
            PowerSpecialItemService.grantFromItem(player, item);
        }
        player.playNotifySound(ModSounds.REWARD.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        ItemPickupHudPacket.sendTo(player, reward, 1, true);
        ObjectDialogueService.show(player, Component.translatable(
                "stardewcraft.witch_hut.magic_ink_received", reward.getHoverName()));
    }
}
