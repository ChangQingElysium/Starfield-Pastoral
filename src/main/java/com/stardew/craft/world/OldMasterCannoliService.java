package com.stardew.craft.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewSimulationTaskScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/** Per-player Old Master Cannoli Statue reward with a lightweight source-inspired effect. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class OldMasterCannoliService {
    public static final String TARGET_ID = "old_master_cannoli";
    public static final String MARKER_TAG = "stardewcraft_interaction:old_master_cannoli";
    public static final String CLAIM_FLAG = "CF_Statue";
    public static final BlockPos INTERACTION_MIN = new BlockPos(-259, 68, 6);
    public static final BlockPos INTERACTION_MAX = new BlockPos(-258, 69, 6);

    private OldMasterCannoliService() {
    }

    public static boolean hasClaimed(PlayerStardewData data) {
        return data != null && data.hasMailFlag(CLAIM_FLAG);
    }

    public static void interact(ServerPlayer player) {
        if (player == null || !ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) return;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (hasClaimed(data)) return;

        ItemStack held = player.getMainHandItem();
        if (!held.is(ModItems.SWEET_GEM_BERRY.get())) {
            ObjectDialogueService.show(player, "stardewcraft.old_master_cannoli.searching");
            return;
        }

        held.shrink(1);
        ItemStack reward = new ItemStack(ModItems.STARDROP.get());
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        player.getInventory().setChanged();

        data.addMailFlag(CLAIM_FLAG);
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
        playOfferingEffect(player.serverLevel());
        StardewSimulationTaskScheduler.schedule(player.serverLevel(), 12, () -> {
            if (player.isRemoved()) return;
            ItemStack display = new ItemStack(ModItems.STARDROP.get());
            HoldUpItemPayload.sendTo(player, display);
            player.playNotifySound(ModSounds.STARDROP.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.serverLevel().sendParticles(
                    ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0.78F, 0.0F, 1.0F),
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    48, 0.55D, 0.8D, 0.55D, 0.05D);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    player.getX(), player.getY() + 1.0D, player.getZ(),
                    20, 0.45D, 0.7D, 0.45D, 0.035D);
        });
    }

    private static void playOfferingEffect(ServerLevel level) {
        double x = (INTERACTION_MIN.getX() + INTERACTION_MAX.getX() + 1.0D) / 2.0D;
        double y = (INTERACTION_MIN.getY() + INTERACTION_MAX.getY() + 1.0D) / 2.0D;
        double z = (INTERACTION_MIN.getZ() + INTERACTION_MAX.getZ() + 1.0D) / 2.0D;
        level.playSound(null, INTERACTION_MIN, ModSounds.NEW_ARTIFACT.get(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, INTERACTION_MIN, ModSounds.SECRET1.get(),
                SoundSource.BLOCKS, 0.9F, 1.0F);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0F, 0.18F, 0.05F),
                x, y, z, 28, 0.42D, 0.75D, 0.42D, 0.04D);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0F, 0.55F, 0.05F),
                x, y + 0.15D, z, 24, 0.38D, 0.7D, 0.38D, 0.035D);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 1.0F, 1.0F, 1.0F),
                x, y + 0.3D, z, 18, 0.34D, 0.62D, 0.34D, 0.03D);
        level.sendParticles(ParticleTypes.END_ROD,
                x, y + 0.2D, z, 12, 0.3D, 0.55D, 0.3D, 0.025D);
    }

    public static void install(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) return;
        for (BlockPos pos : BlockPos.betweenClosed(INTERACTION_MIN, INTERACTION_MAX)) {
            installInteractionBlock(level, pos);
        }
    }

    private static void installInteractionBlock(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity
                && TARGET_ID.equals(blockEntity.getTargetId())) {
            return;
        }
        level.setBlock(pos, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof PortalTriggerBlockEntity blockEntity) {
            blockEntity.configure(TARGET_ID, MARKER_TAG);
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) install(level);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) install(player.serverLevel());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) install(player.serverLevel());
    }
}
