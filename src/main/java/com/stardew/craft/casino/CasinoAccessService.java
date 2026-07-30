package com.stardew.craft.casino;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.blockentity.PortalTriggerBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.PowerSpecialItemService;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.MagicWarpFlashPayload;
import com.stardew.craft.network.payload.NpcVisibilityPayload;
import com.stardew.craft.network.payload.OpenDesertFestivalQuestionPayload;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewSimulationTaskScheduler;
import com.stardew.craft.warp.ModTeleport;
import com.stardew.craft.world.PlayerAreaEvictionService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Per-player casino membership gate.
 *
 * <p>The Bouncer itself is spawned by the centralized data-driven NPC runtime.
 * This service owns only the player-specific story flag, invisible portal blocks
 * and the small locked threshold in front of the casino.</p>
 */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class CasinoAccessService {
    public static final String BOUNCER_NPC_ID = "bouncer";
    public static final String BOUNCER_GONE_FLAG = "bouncerGone";
    public static final String ENTRY_TARGET_ID = "casino_enter";
    public static final String EXIT_TARGET_ID = "casino_exit";
    public static final String QI_COIN_MACHINE_TARGET_ID = "casino_qi_coin_machine";
    public static final String QI_COIN_SHOP_TARGET_ID = "casino_qi_coin_shop";
    public static final String QUESTION_CONTEXT = "casino_bouncer";

    public static final BlockPos ENTRY_MIN = new BlockPos(-240, 30, -152);
    public static final BlockPos ENTRY_MAX = new BlockPos(-239, 31, -152);
    public static final BlockPos EXIT_MIN = new BlockPos(-240, 32, -154);
    public static final BlockPos EXIT_MAX = new BlockPos(-239, 34, -154);
    public static final BlockPos QI_COIN_MACHINE_MIN = new BlockPos(-237, 36, -170);
    public static final BlockPos QI_COIN_MACHINE_MAX = new BlockPos(-236, 37, -170);
    public static final BlockPos QI_COIN_SHOP_MIN = new BlockPos(-222, 37, -170);
    public static final BlockPos QI_COIN_SHOP_MAX = new BlockPos(-222, 38, -170);
    public static final Vec3 BOUNCER_POSITION = new Vec3(-239.0D, 30.0D, -150.5D);
    public static final Vec3 ENTRY_DESTINATION = new Vec3(-239.0D, 33.5D, -155.0D);
    public static final Vec3 EXIT_DESTINATION = new Vec3(-239.0D, 30.0D, -151.0D);

    private static final AABB LOCKED_THRESHOLD =
            new AABB(-241.0D, 30.0D, -155.0D, -237.0D, 35.0D, -151.0D);
    private static final String GATE_ID = "casino_bouncer";
    private static final String ENTRY_MARKER = "stardewcraft_interaction:casino_enter";
    private static final String EXIT_MARKER = "stardewcraft_interaction:casino_exit";

    private CasinoAccessService() {
    }

    public static boolean isCasinoPosition(double x, double y, double z) {
        int blockX = net.minecraft.util.Mth.floor(x);
        int blockY = net.minecraft.util.Mth.floor(y);
        int blockZ = net.minecraft.util.Mth.floor(z);
        return blockX >= -250 && blockX <= -219
                && blockY >= 28 && blockY <= 40
                && blockZ >= -171 && blockZ <= -153;
    }

    public static boolean hasClubCard(PlayerStardewData data) {
        return data != null && (data.hasMailFlag(PowerSpecialItemService.CLUB_CARD_FLAG)
                || data.hasSpecialItem(PowerSpecialItemService.CLUB_CARD_ID));
    }

    public static boolean hasClubCard(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return hasClubCard(data) || player.getInventory().contains(
                new net.minecraft.world.item.ItemStack(ModItems.CLUB_CARD.get()));
    }

    public static boolean hasBouncerMovedAside(PlayerStardewData data) {
        return data != null && data.hasMailFlag(BOUNCER_GONE_FLAG);
    }

    public static boolean canUseEntrance(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return hasClubCard(player) && hasBouncerMovedAside(data);
    }

    public static void handlePortal(ServerPlayer player, String targetId) {
        if (!ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return;
        }
        if (ENTRY_TARGET_ID.equals(targetId)) {
            if (!canUseEntrance(player)) {
                return;
            }
            ModTeleport.to(player, player.serverLevel(),
                    ENTRY_DESTINATION.x, ENTRY_DESTINATION.y, ENTRY_DESTINATION.z,
                    Direction.NORTH.toYRot(), 0.0F);
            return;
        }
        if (EXIT_TARGET_ID.equals(targetId)
                && isCasinoPosition(player.getX(), player.getY(), player.getZ())) {
            ModTeleport.to(player, player.serverLevel(),
                    EXIT_DESTINATION.x, EXIT_DESTINATION.y, EXIT_DESTINATION.z,
                    Direction.SOUTH.toYRot(), 0.0F);
            return;
        }
        if (QI_COIN_MACHINE_TARGET_ID.equals(targetId)) {
            CasinoContentService.openQiCoinMachine(player);
            return;
        }
        if (QI_COIN_SHOP_TARGET_ID.equals(targetId)) {
            CasinoContentService.openCasinoShop(player);
        }
    }

    public static InteractionResult interactBouncer(ServerPlayer player,
                                                     StardewNpcEntity bouncer,
                                                     InteractionHand hand) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (hasBouncerMovedAside(data)) {
            return InteractionResult.SUCCESS;
        }
        bouncer.facePlayerTemporarily(player, 60, null);
        if (!hasClubCard(player)) {
            PacketDistributor.sendToPlayer(player, new OpenNpcDialogueScreenPayload(
                    BOUNCER_NPC_ID, "stardewcraft.npc.bouncer.members_only", 0));
            return InteractionResult.SUCCESS;
        }

        Component question = Component.translatable("stardewcraft.npc.bouncer.club_card_question");
        List<OpenDesertFestivalQuestionPayload.ResponseOption> options = List.of(
                response(player, "yes", "stardewcraft.npc.bouncer.club_card_yes"),
                response(player, "insult", "stardewcraft.npc.bouncer.club_card_insult"));
        PacketDistributor.sendToPlayer(player, new OpenDesertFestivalQuestionPayload(
                QUESTION_CONTEXT,
                0,
                "",
                Component.Serializer.toJson(question, player.registryAccess()),
                options));
        return InteractionResult.SUCCESS;
    }

    public static void handleQuestionResponse(ServerPlayer player, String choiceId) {
        if (!"yes".equals(choiceId) && !"insult".equals(choiceId)) {
            return;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!hasClubCard(player) || hasBouncerMovedAside(data)) {
            return;
        }

        data.addMailFlag(BOUNCER_GONE_FLAG);
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
        PacketDistributor.sendToPlayer(player, new NpcVisibilityPayload(BOUNCER_NPC_ID, true));
        PacketDistributor.sendToPlayer(player, new MagicWarpFlashPayload((byte) 0));
        player.playNotifySound(ModSounds.EXPLOSION.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        player.setDeltaMovement(Vec3.ZERO);

        StardewSimulationTaskScheduler.schedule(player.serverLevel(), 10, () -> {
            if (!player.isRemoved()) {
                ObjectDialogueService.show(player, "stardewcraft.npc.bouncer.may_enter");
            }
        });
    }

    private static OpenDesertFestivalQuestionPayload.ResponseOption response(
            ServerPlayer player, String id, String translationKey) {
        return new OpenDesertFestivalQuestionPayload.ResponseOption(
                id,
                Component.Serializer.toJson(
                        Component.translatable(translationKey), player.registryAccess()));
    }

    public static void install(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        installRange(level, ENTRY_MIN, ENTRY_MAX, ENTRY_TARGET_ID, ENTRY_MARKER);
        installRange(level, EXIT_MIN, EXIT_MAX, EXIT_TARGET_ID, EXIT_MARKER);
        installRange(level, QI_COIN_MACHINE_MIN, QI_COIN_MACHINE_MAX,
                QI_COIN_MACHINE_TARGET_ID, "stardewcraft_interaction:casino_qi_coin_machine");
        installRange(level, QI_COIN_SHOP_MIN, QI_COIN_SHOP_MAX,
                QI_COIN_SHOP_TARGET_ID, "stardewcraft_interaction:casino_qi_coin_shop");
    }

    private static void installRange(ServerLevel level, BlockPos min, BlockPos max,
                                     String targetId, String marker) {
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockPos immutable = pos.immutable();
            if (level.getBlockState(immutable).is(ModBlocks.PORTAL_TRIGGER.get())
                    && level.getBlockEntity(immutable) instanceof PortalTriggerBlockEntity existing
                    && targetId.equals(existing.getTargetId())) {
                continue;
            }
            level.setBlock(immutable, ModBlocks.PORTAL_TRIGGER.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(immutable) instanceof PortalTriggerBlockEntity blockEntity) {
                blockEntity.configure(targetId, marker);
            }
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
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        install(player.serverLevel());
        if (hasBouncerMovedAside(PlayerDataManager.getPlayerData(player))) {
            PacketDistributor.sendToPlayer(player, new NpcVisibilityPayload(BOUNCER_NPC_ID, true));
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            install(player.serverLevel());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())) {
            return;
        }
        boolean locked = !canUseEntrance(player)
                && LOCKED_THRESHOLD.intersects(player.getBoundingBox());
        PlayerAreaEvictionService.enforce(
                player,
                GATE_ID,
                locked,
                EXIT_DESTINATION,
                Component.translatable("stardewcraft.npc.bouncer.nice_try"));
    }
}
