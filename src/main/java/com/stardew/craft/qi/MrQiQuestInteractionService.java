package com.stardew.craft.qi;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorWallThinBlock;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.mail.MailService;
import com.stardew.craft.network.payload.OpenObjectDialoguePayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewSimulationTaskScheduler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Authored world integration for the original Mr. Qi scavenger-hunt interactions. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MrQiQuestInteractionService {
    public static final BlockPos TUNNEL_SAFE_POS = new BlockPos(-123, 65, -67);
    public static final BlockPos SAND_DRAGON_POS = new BlockPos(-244, 68, -165);

    public static final String SAND_DRAGON_TARGET_ID = "mr_qi_sand_dragon";
    public static final String CLUB_CARD_MAIL_ID = "mrQiClubCard";
    private static final String SAND_DRAGON_MARKER_TAG = "stardewcraft_interaction:mr_qi_sand_dragon";

    private MrQiQuestInteractionService() {
    }

    public static void interact(ServerPlayer player, MrQiQuestAnchor anchor) {
        if (!ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return;
        }

        MrQiQuestRules.Decision decision = MrQiQuestService.interact(player, anchor);
        playSound(player, decision.soundCue());
        if (anchor == MrQiQuestAnchor.SAND_DRAGON
                && decision.outcome() == MrQiQuestRules.Outcome.SUCCESS) {
            // The project intentionally replaces the farmhouse lumber-pile pickup with mailbox delivery.
            MailService.addMail(player, CLUB_CARD_MAIL_ID);
        }
        if (!decision.dialogueKeys().isEmpty()) {
            List<Component> messages = decision.dialogueKeys().stream()
                    .<Component>map(Component::translatable)
                    .toList();
            PacketDistributor.sendToPlayer(player, new OpenObjectDialoguePayload(messages));
        }
    }

    public static void interactSandDragon(ServerPlayer player) {
        if (!hasActiveSandDragonQuest(player)) {
            return;
        }
        interact(player, MrQiQuestAnchor.SAND_DRAGON);
    }

    public static boolean hasActiveSandDragonQuest(ServerPlayer player) {
        QuestManager quests = PlayerDataManager.getPlayerData(player).getQuestManager();
        return quests.hasQuest(MrQiQuestRules.QUEST_SAND_DRAGON);
    }

    public static void install(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        installTunnelSafe(level);
        installSandDragonInteraction(level);
    }

    private static void installTunnelSafe(ServerLevel level) {
        BlockState expected = ModBlocks.QI_TUNNEL_SAFE.get().defaultBlockState()
                .setValue(MapDecorWallThinBlock.FACING, Direction.SOUTH);
        if (level.getBlockState(TUNNEL_SAFE_POS).equals(expected)) {
            return;
        }
        level.destroyBlock(TUNNEL_SAFE_POS, false);
        level.setBlock(TUNNEL_SAFE_POS, expected, Block.UPDATE_ALL);
    }

    private static void installSandDragonInteraction(ServerLevel level) {
        if (level.getBlockState(SAND_DRAGON_POS).is(ModBlocks.PORTAL_TRIGGER.get())
                && level.getBlockEntity(SAND_DRAGON_POS) instanceof PortalTriggerBlockEntity blockEntity
                && SAND_DRAGON_TARGET_ID.equals(blockEntity.getTargetId())) {
            return;
        }
        level.setBlock(SAND_DRAGON_POS, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(SAND_DRAGON_POS) instanceof PortalTriggerBlockEntity blockEntity) {
            blockEntity.configure(SAND_DRAGON_TARGET_ID, SAND_DRAGON_MARKER_TAG);
        }
    }

    private static void playSound(ServerPlayer player, MrQiQuestRules.SoundCue cue) {
        switch (cue) {
            case NONE, CLUB_CARD_REWARD -> {
            }
            case TUNNEL_OPEN -> {
                player.playNotifySound(ModSounds.OPENBOX.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                StardewSimulationTaskScheduler.schedule(player.serverLevel(), 10, () -> {
                    if (!player.isRemoved()) {
                        player.playNotifySound(
                                ModSounds.DOOR_CREAK_REVERSE.get(),
                                SoundSource.BLOCKS,
                                1.0F,
                                1.0F
                        );
                    }
                });
            }
            case SHIP -> player.playNotifySound(ModSounds.SHIP.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            case COIN -> player.playNotifySound(ModSounds.COIN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            case EAT -> player.playNotifySound(ModSounds.EAT.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            install(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            install(player.serverLevel());
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            install(player.serverLevel());
        }
    }
}
