package com.stardew.craft.world;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.WildWeedsBlock;
import com.stardew.craft.block.utility.WoodenChestBlock;
import com.stardew.craft.blockentity.WoodenChestBlockEntity;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.event.MineMonsterSpawnHandler;
import com.stardew.craft.interior.InteriorSubspaceManager;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.PowerSpecialItem;
import com.stardew.craft.item.PowerSpecialItemService;
import com.stardew.craft.menu.WoodenChestMenu;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.sewer.SewerStoryFlags;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.warp.ModTeleport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** Gameplay bootstrap and per-player reward handling for the Mutant Bug Lair. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class MutantBugLairService {
    public static final String ENTRANCE_TARGET_ID = "mutant_bug_lair_enter";
    public static final String EXIT_TARGET_ID = "mutant_bug_lair_exit";

    public static final BlockPos ENTRANCE_PORTAL_BASE = new BlockPos(0, 51, 56);
    public static final BlockPos EXIT_PORTAL_BASE = new BlockPos(-1, -10, 30);
    public static final BlockPos REWARD_CHEST_POS = new BlockPos(15, -10, -18);

    private static final BlockPos ENTRANCE_DESTINATION = new BlockPos(0, -10, 27);
    private static final BlockPos EXIT_DESTINATION = new BlockPos(0, 51, 58);
    private static final String PORTAL_TARGET_PREFIX = "sdv_portal_target:";
    private static final String LAIR_MOB_TAG = "stardewcraft_mutant_bug_lair_mob";
    private static final int REWARD_SLOT = 13;
    private static final int CHEST_BROWN_COLOR = 8;
    private static final int MAINTENANCE_INTERVAL = 40;
    private static final int WEED_TARGET_PERCENT = 15;
    private static final AABB DOMAIN_BOUNDS = new AABB(
            MutantBugLairArea.DOMAIN_MIN_X, MutantBugLairArea.DOMAIN_MIN_Y, MutantBugLairArea.DOMAIN_MIN_Z,
            MutantBugLairArea.DOMAIN_MAX_X + 1, MutantBugLairArea.DOMAIN_MAX_Y + 1, MutantBugLairArea.DOMAIN_MAX_Z + 1);

    private static long lastMaintenanceTick = Long.MIN_VALUE;
    private static WeakReference<ServerLevel> activeLevel = new WeakReference<>(null);
    private static boolean portalsInstalled;
    private static boolean weedsInitialized;

    private MutantBugLairService() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return;
        }
        if (activeLevel.get() != level) {
            activeLevel = new WeakReference<>(level);
            lastMaintenanceTick = Long.MIN_VALUE;
            portalsInstalled = false;
            weedsInitialized = false;
        }
        if (level.getGameTime() == lastMaintenanceTick
                || level.getGameTime() % MAINTENANCE_INTERVAL != 0) {
            return;
        }
        lastMaintenanceTick = level.getGameTime();
        ensureInstalled(level);

        List<ServerPlayer> players = level.players().stream()
                .filter(player -> MutantBugLairArea.contains(player.blockPosition()))
                .toList();
        if (players.isEmpty()) {
            return;
        }
        if (!weedsInitialized) {
            fillWeedsToTarget(level);
            weedsInitialized = true;
        }
        maintainMonsters(level, players);
    }

    public static void enter(ServerPlayer player) {
        if (!isEntranceUnlocked(player)) {
            ObjectDialogueService.show(player, Component.translatable("stardewcraft.mutant_bug_lair.seal_locked"));
            return;
        }
        player.playNotifySound(ModSounds.DOOR_OPEN.get(), SoundSource.PLAYERS, 1.0F, 0.85F);
        ModTeleport.to(player, player.serverLevel(), ENTRANCE_DESTINATION, 180.0F, 0.0F);
    }

    public static void exit(ServerPlayer player) {
        player.playNotifySound(ModSounds.DOOR_OPEN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        ModTeleport.to(player, player.serverLevel(), EXIT_DESTINATION, 0.0F, 0.0F);
    }

    public static boolean isEntranceUnlocked(ServerPlayer player) {
        return player != null && PlayerDataManager.getPlayerData(player).hasMailFlag(SewerStoryFlags.KROBUS_UNSEAL);
    }

    public static boolean isLairMonster(Mob mob) {
        return mob != null && mob.getTags().contains(LAIR_MOB_TAG);
    }

    public static boolean isRewardChestInteraction(
            ServerPlayer player,
            BlockPos pos
    ) {
        return player != null
                && ModDimensions.STARDEW_VALLEY.equals(
                        player.serverLevel().dimension())
                && REWARD_CHEST_POS.equals(pos);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND
                || !ModDimensions.STARDEW_VALLEY.equals(player.serverLevel().dimension())
                || !REWARD_CHEST_POS.equals(event.getPos())) {
            return;
        }
        openRewardChest(player);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level
                && ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                && REWARD_CHEST_POS.equals(event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static void ensureInstalled(ServerLevel level) {
        if (!portalsInstalled) {
            InteriorSubspaceManager.placePortalTriggerArea(level, ENTRANCE_PORTAL_BASE, 2, 1, 1,
                    "mutant_bug_lair_entrance", PORTAL_TARGET_PREFIX + ENTRANCE_TARGET_ID);
            InteriorSubspaceManager.placePortalTriggerArea(level, EXIT_PORTAL_BASE, 2, 3, 1,
                    "mutant_bug_lair_exit", PORTAL_TARGET_PREFIX + EXIT_TARGET_ID);
            portalsInstalled = true;
        }
        ensureRewardChest(level);
    }

    private static void ensureRewardChest(ServerLevel level) {
        level.getChunkAt(REWARD_CHEST_POS);
        BlockState state = level.getBlockState(REWARD_CHEST_POS);
        if (!state.is(ModBlocks.WOODEN_CHEST.get())) {
            state = ModBlocks.WOODEN_CHEST.get().defaultBlockState()
                    .setValue(WoodenChestBlock.FACING, Direction.SOUTH)
                    .setValue(WoodenChestBlock.OPEN, false);
            level.setBlock(REWARD_CHEST_POS, state, 3);
        } else if (state.getValue(WoodenChestBlock.FACING) != Direction.SOUTH
                || state.getValue(WoodenChestBlock.OPEN)) {
            level.setBlock(REWARD_CHEST_POS, state
                    .setValue(WoodenChestBlock.FACING, Direction.SOUTH)
                    .setValue(WoodenChestBlock.OPEN, false), 3);
        }
        if (level.getBlockEntity(REWARD_CHEST_POS) instanceof WoodenChestBlockEntity chest) {
            chest.setColorSelection(CHEST_BROWN_COLOR);
        }
    }

    private static void openRewardChest(ServerPlayer player) {
        RewardContainer container = new RewardContainer(hasClaimedReward(player));
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.translatable("container.stardew_craft.wooden_chest");
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player openingPlayer) {
                return new WoodenChestMenu(containerId, inventory, container, ignored -> {
                }, CHEST_BROWN_COLOR);
            }
        });
    }

    private static boolean hasClaimedReward(ServerPlayer player) {
        var data = PlayerDataManager.getPlayerData(player);
        return data.hasMailFlag(PowerSpecialItemService.DARK_TALISMAN_FLAG)
                || data.hasSpecialItem(PowerSpecialItemService.DARK_TALISMAN_ID);
    }

    private static void fillWeedsToTarget(ServerLevel level) {
        List<BlockPos> candidates = new ArrayList<>();
        int existing = 0;
        for (int x = MutantBugLairArea.SPAWN_MIN_X; x <= MutantBugLairArea.SPAWN_MAX_X; x++) {
            for (int z = MutantBugLairArea.SPAWN_MIN_Z; z <= MutantBugLairArea.SPAWN_MAX_Z; z++) {
                BlockPos pos = new BlockPos(x, MutantBugLairArea.SPAWN_Y, z);
                if (!level.hasChunkAt(pos) || isProtectedSpawnArea(pos)) {
                    continue;
                }
                if (level.getBlockState(pos).is(ModBlocks.WILD_WEEDS.get())) {
                    existing++;
                } else if (isOpenSolidFloor(level, pos)) {
                    candidates.add(pos);
                }
            }
        }
        int totalEligible = existing + candidates.size();
        int needed = Math.max(0, totalEligible * WEED_TARGET_PERCENT / 100 - existing);
        RandomSource random = level.random;
        int season = Math.max(0, Math.min(3, StardewTimeManager.get().getCurrentSeason()));
        for (int i = 0; i < needed && !candidates.isEmpty(); i++) {
            int index = random.nextInt(candidates.size());
            BlockPos pos = candidates.remove(index);
            BlockState weeds = ModBlocks.WILD_WEEDS.get().defaultBlockState()
                    .setValue(WildWeedsBlock.SEASON, season)
                    .setValue(WildWeedsBlock.VARIANT, random.nextInt(3));
            level.setBlock(pos, weeds, 3);
        }
    }

    private static void maintainMonsters(ServerLevel level, List<ServerPlayer> players) {
        int current = level.getEntitiesOfClass(Mob.class, DOMAIN_BOUNDS,
                mob -> mob.isAlive() && mob.getTags().contains(LAIR_MOB_TAG)).size();
        int target = Math.min(24, 12 + Math.max(0, players.size() - 1) * 2);
        int toSpawn = Math.min(2, target - current);
        for (int i = 0; i < toSpawn; i++) {
            spawnMonster(level, players);
        }
    }

    private static void spawnMonster(ServerLevel level, List<ServerPlayer> players) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = MutantBugLairArea.SPAWN_MIN_X + level.random.nextInt(MutantBugLairArea.SPAWN_MAX_X - MutantBugLairArea.SPAWN_MIN_X + 1);
            int z = MutantBugLairArea.SPAWN_MIN_Z + level.random.nextInt(MutantBugLairArea.SPAWN_MAX_Z - MutantBugLairArea.SPAWN_MIN_Z + 1);
            BlockPos pos = new BlockPos(x, MutantBugLairArea.SPAWN_Y, z);
            if (!level.hasChunkAt(pos) || !isOpenSolidFloor(level, pos) || isProtectedSpawnArea(pos)
                    || players.stream().anyMatch(player -> player.distanceToSqr(Vec3.atCenterOf(pos)) < 64.0D)) {
                continue;
            }
            String monsterId = level.random.nextFloat() < 0.65F ? "grub" : "fly";
            MineMonsterSpawnHandler.spawnConfiguredMonster(
                    level, monsterId, Vec3.atBottomCenterOf(pos), 0.0F, 1,
                    mob -> {
                        mob.addTag(LAIR_MOB_TAG);
                        MineMonsterSpawnHandler.applyMutantBugLairProfile(mob, monsterId);
                    });
            return;
        }
    }

    private static boolean isOpenSolidFloor(ServerLevel level, BlockPos standingPos) {
        BlockPos floorPos = standingPos.below();
        return level.getBlockState(floorPos).isFaceSturdy(level, floorPos, Direction.UP)
                && level.getFluidState(floorPos).isEmpty()
                && level.getBlockState(standingPos).isAir()
                && level.getFluidState(standingPos).isEmpty()
                && level.getBlockState(standingPos.above()).isAir()
                && level.getFluidState(standingPos.above()).isEmpty();
    }

    private static boolean isProtectedSpawnArea(BlockPos pos) {
        return pos.distSqr(ENTRANCE_DESTINATION) <= 16.0D || pos.distSqr(REWARD_CHEST_POS) <= 9.0D;
    }

    private static final class RewardContainer extends SimpleContainer {
        private final boolean initiallyClaimed;
        private final ItemStack reward = new ItemStack(ModItems.DARK_TALISMAN.get());
        private boolean stopped;

        private RewardContainer(boolean claimed) {
            super(27);
            initiallyClaimed = claimed;
            if (!claimed) {
                setItem(REWARD_SLOT, reward.copy());
            }
        }

        @Override
        public void stopOpen(Player player) {
            super.stopOpen(player);
            if (stopped || !(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            stopped = true;
            // The PowerSpecialItem can persist its flag from inventoryTick before this menu closes,
            // so the virtual chest itself is the source of truth for first-take feedback.
            boolean claimedNow = !initiallyClaimed && !containsReward();
            if (claimedNow && ModItems.DARK_TALISMAN.get() instanceof PowerSpecialItem item) {
                PowerSpecialItemService.grantFromItem(serverPlayer, item);
                serverPlayer.playNotifySound(ModSounds.REWARD.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                ItemPickupHudPacket.sendTo(serverPlayer, reward, 1, true);
                ObjectDialogueService.show(serverPlayer, Component.translatable(
                        "stardewcraft.mutant_bug_lair.dark_talisman_received", reward.getHoverName()));
            }
            returnNonRewardItems(serverPlayer);
            clearContent();
        }

        private boolean containsReward() {
            for (int i = 0; i < getContainerSize(); i++) {
                ItemStack stack = getItem(i);
                if (!stack.isEmpty() && stack.is(reward.getItem())) {
                    return true;
                }
            }
            return false;
        }

        private void returnNonRewardItems(ServerPlayer player) {
            for (int i = 0; i < getContainerSize(); i++) {
                ItemStack stack = getItem(i);
                if (stack.isEmpty() || (!initiallyClaimed && stack.is(reward.getItem()))) {
                    continue;
                }
                ItemStack copy = stack.copy();
                if (!player.getInventory().add(copy)) {
                    player.drop(copy, false);
                }
            }
        }
    }
}
