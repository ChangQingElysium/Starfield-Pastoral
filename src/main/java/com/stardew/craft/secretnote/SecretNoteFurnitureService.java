package com.stardew.craft.secretnote;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.payload.HoldUpItemPayload;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Source-parity world rewards revealed by vanilla secret notes 13 and 14. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SecretNoteFurnitureService {
    public static final String JUNIMO_PLUSH_FLAG = "junimoPlush";
    public static final String STONE_JUNIMO_FLAG = "stoneJunimo";
    public static final int JUNIMO_PLUSH_DAY = 28;
    /** Project time is raw minutes; vanilla's 12:00 clock slot spans 720..729 here. */
    public static final int JUNIMO_PLUSH_TIME_START = 12 * 60;
    public static final int JUNIMO_PLUSH_TIME_END_EXCLUSIVE = JUNIMO_PLUSH_TIME_START + 10;

    public static final BlockPos JUNIMO_PLUSH_MIN = new BlockPos(-27, 66, -69);
    public static final BlockPos JUNIMO_PLUSH_MAX = new BlockPos(-25, 67, -67);
    public static final BlockPos STONE_JUNIMO_POS = new BlockPos(52, 66, -63);

    private SecretNoteFurnitureService() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)
                || !isStardewLevel(player.serverLevel())) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = player.serverLevel().getBlockState(pos);
        if (isJunimoPlushBush(pos) && state.is(ModBlocks.BERRY_BUSH.get())) {
            StardewTimeManager time = StardewTimeManager.get();
            if (claimJunimoPlush(player, time.getCurrentDay(), time.getCurrentTime())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }

        if (isStoneJunimoPart(pos) && state.is(ModBlocks.STONE_JUNIMO.get())) {
            claimStoneJunimo(player);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !isStardewLevel(player.serverLevel())
                || !isStoneJunimoPart(event.getPos())
                || !player.serverLevel().getBlockState(event.getPos()).is(ModBlocks.STONE_JUNIMO.get())) {
            return;
        }

        claimStoneJunimo(player);
        event.setCanceled(true);
    }

    static boolean isJunimoPlushBush(BlockPos pos) {
        return pos != null
                && pos.getX() >= JUNIMO_PLUSH_MIN.getX() && pos.getX() <= JUNIMO_PLUSH_MAX.getX()
                && pos.getY() >= JUNIMO_PLUSH_MIN.getY() && pos.getY() <= JUNIMO_PLUSH_MAX.getY()
                && pos.getZ() >= JUNIMO_PLUSH_MIN.getZ() && pos.getZ() <= JUNIMO_PLUSH_MAX.getZ();
    }

    static boolean isStoneJunimoPart(BlockPos pos) {
        return STONE_JUNIMO_POS.equals(pos) || STONE_JUNIMO_POS.above().equals(pos);
    }

    static boolean canClaimJunimoPlush(PlayerStardewData data, int day, int currentTime) {
        // Vanilla Bush.shake() checks only date, exact time and the mail flag;
        // reading note 13 is the clue, not a hidden code prerequisite.
        return data != null
                && day == JUNIMO_PLUSH_DAY
                && isVanillaNoonSlot(currentTime)
                && !data.hasMailFlag(JUNIMO_PLUSH_FLAG);
    }

    static boolean isVanillaNoonSlot(int currentTime) {
        return currentTime >= JUNIMO_PLUSH_TIME_START
                && currentTime < JUNIMO_PLUSH_TIME_END_EXCLUSIVE;
    }

    static boolean canClaimStoneJunimo(PlayerStardewData data) {
        return data != null && !data.hasMailFlag(STONE_JUNIMO_FLAG);
    }

    private static boolean claimJunimoPlush(ServerPlayer player, int day, int currentTime) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!canClaimJunimoPlush(data, day, currentTime)) {
            return false;
        }

        data.addMailFlag(JUNIMO_PLUSH_FLAG);
        saveAndSync(player, data);
        giveItem(player, new ItemStack(ModItems.JUNIMO_PLUSH.get()), true);
        return true;
    }

    private static boolean claimStoneJunimo(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!canClaimStoneJunimo(data)) {
            return false;
        }

        data.addMailFlag(STONE_JUNIMO_FLAG);
        saveAndSync(player, data);
        giveItem(player, new ItemStack(ModItems.STONE_JUNIMO.get()), false);
        syncStoneJunimoVisibility(player);
        return true;
    }

    private static void giveItem(ServerPlayer player, ItemStack reward, boolean holdUp) {
        ItemStack hudStack = reward.copy();
        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);
        }
        if (holdUp) {
            HoldUpItemPayload.sendTo(player, hudStack);
        }
        ItemPickupHudPacket.sendTo(player, hudStack, 1, false);
    }

    private static void saveAndSync(ServerPlayer player, PlayerStardewData data) {
        PlayerDataManager.get().savePlayerData(player.getUUID(), data);
        PlayerDataEventHandler.syncPlayerData(player, data);
    }

    public static void ensureStoneJunimoPlaced(ServerLevel level) {
        if (!isStardewLevel(level)) {
            return;
        }

        BlockState main = ModBlocks.STONE_JUNIMO.get().defaultBlockState()
                .setValue(MapDecorStaticBlock.PART, MapDecorStaticBlock.Part.MAIN)
                .setValue(MapDecorStaticBlock.FACING, Direction.NORTH);
        if (!level.getBlockState(STONE_JUNIMO_POS).equals(main)) {
            level.setBlock(STONE_JUNIMO_POS, main, Block.UPDATE_ALL);
        }
        ModBlocks.STONE_JUNIMO.get().setPlacedBy(level, STONE_JUNIMO_POS, main, null, ItemStack.EMPTY);
    }

    public static void syncStoneJunimoVisibility(ServerPlayer player) {
        if (!isStardewLevel(player.serverLevel())) {
            return;
        }
        ensureStoneJunimoPlaced(player.serverLevel());
        boolean visible = canClaimStoneJunimo(PlayerDataManager.getPlayerData(player));
        sendBlockFor(player, STONE_JUNIMO_POS, visible);
        sendBlockFor(player, STONE_JUNIMO_POS.above(), visible);
    }

    private static void sendBlockFor(ServerPlayer player, BlockPos pos, boolean visible) {
        BlockState state = visible
                ? player.serverLevel().getBlockState(pos)
                : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            ensureStoneJunimoPlaced(level);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncStoneJunimoVisibility(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncStoneJunimoVisibility(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncStoneJunimoVisibility(player);
        }
    }

    /** Re-hides the authored block after a claimed player's client reloads its chunk. */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.tickCount % 20 != 0
                || !isStardewLevel(player.serverLevel())
                || player.distanceToSqr(STONE_JUNIMO_POS.getCenter()) > 64.0D * 64.0D) {
            return;
        }
        if (!canClaimStoneJunimo(PlayerDataManager.getPlayerData(player))) {
            sendBlockFor(player, STONE_JUNIMO_POS, false);
            sendBlockFor(player, STONE_JUNIMO_POS.above(), false);
        }
    }

    private static boolean isStardewLevel(ServerLevel level) {
        return level != null && ModDimensions.STARDEW_VALLEY.equals(level.dimension());
    }
}
