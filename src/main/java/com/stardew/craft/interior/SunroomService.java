package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.nature.TeaBushBlock;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.farming.SeasonLocationRules;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.manager.TeaBushManager;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Caroline's Sunroom and its single world-shared central tea bush. */
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class SunroomService {
    public static final BlockPos MIN = new BlockPos(45, 36, -57);
    public static final BlockPos MAX = new BlockPos(61, 44, -46);
    public static final AABB BOUNDS = new AABB(
            MIN.getX(), MIN.getY(), MIN.getZ(),
            MAX.getX() + 1.0D, MAX.getY() + 1.0D, MAX.getZ() + 1.0D);
    public static final BlockPos CENTRAL_TEA_BUSH = new BlockPos(52, 37, -52);
    public static final BlockPos BROOK_SOUND_CENTER = new BlockPos(49, 37, -56);
    public static final int AXE_HITS_TO_TAKE = 200;

    private static final String DATA_NAME = "stardew_sunroom_tea_bush";
    private static final Map<UUID, Long> LAST_HIT_TICK = new ConcurrentHashMap<>();
    private static boolean seasonRuleRegistered;

    private SunroomService() {
    }

    public static void registerSeasonRule() {
        if (seasonRuleRegistered) return;
        SeasonLocationRules.registerIgnoreSeasonsRule(SunroomService::isInside);
        seasonRuleRegistered = true;
    }

    public static boolean isInside(Level level, BlockPos pos) {
        return level != null
                && ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                && pos != null
                && pos.getX() >= MIN.getX() && pos.getX() <= MAX.getX()
                && pos.getY() >= MIN.getY() && pos.getY() <= MAX.getY()
                && pos.getZ() >= MIN.getZ() && pos.getZ() <= MAX.getZ();
    }

    public static boolean isCentralTeaBush(Level level, BlockPos pos) {
        if (level == null || pos == null
                || !ModDimensions.STARDEW_VALLEY.equals(level.dimension())) {
            return false;
        }
        return pos.equals(CENTRAL_TEA_BUSH) || pos.equals(CENTRAL_TEA_BUSH.above());
    }

    public static int baseVisualStage() {
        return StardewTimeManager.get().getCurrentDay() >= 22 ? 3 : 2;
    }

    /** Server-authoritative entry point for one client-detected physical mouse press. */
    public static void handlePrimaryAction(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                || data(level).removed
                || !isActuallyTargetingBush(player)) {
            return;
        }

        ItemStack tool = player.getMainHandItem();
        BlockState lower = level.getBlockState(CENTRAL_TEA_BUSH);
        if (!(lower.getBlock() instanceof TeaBushBlock bush)) return;

        if (tool.is(ModItems.GOLDEN_SCYTHE.get()) || tool.is(ModItems.IRIDIUM_SCYTHE.get())) {
            bush.interact(player, level, CENTRAL_TEA_BUSH);
            return;
        }
        if (!isAxe(tool)) return;

        long now = level.getGameTime();
        Long previous = LAST_HIT_TICK.put(player.getUUID(), now);
        if (previous != null && previous == now) return;
        hitWithAxe(player, level);
    }

    private static boolean isActuallyTargetingBush(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        return hit instanceof BlockHitResult blockHit
                && isCentralTeaBush(player.serverLevel(), blockHit.getBlockPos());
    }

    private static void hitWithAxe(ServerPlayer player, ServerLevel level) {
        CentralTeaBushData saved = data(level);
        if (saved.removed) return;

        saved.axeHits = Math.min(AXE_HITS_TO_TAKE, saved.axeHits + 1);
        saved.setDirty();
        level.playSound(null, CENTRAL_TEA_BUSH, ModSounds.AXCHOP.get(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
        sendHitParticles(level, saved.axeHits >= AXE_HITS_TO_TAKE ? 28 : 6);

        if (saved.axeHits < AXE_HITS_TO_TAKE) return;

        saved.removed = true;
        saved.setDirty();
        level.removeBlock(CENTRAL_TEA_BUSH, false);
        giveToPlayer(player, new ItemStack(ModItems.TEA_SAPLING.get()));
        level.playSound(null, CENTRAL_TEA_BUSH, ModSounds.TREE_THUD.get(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    public static boolean isPrimaryActionTool(ItemStack stack) {
        return stack.is(ModItems.GOLDEN_SCYTHE.get())
                || stack.is(ModItems.IRIDIUM_SCYTHE.get())
                || isAxe(stack);
    }

    private static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof AxeItem
                || stack.is(ItemTags.AXES)
                || stack.canPerformAction(ItemAbilities.AXE_DIG));
    }

    public static void ensurePlaced(ServerLevel level) {
        if (!ModDimensions.STARDEW_VALLEY.equals(level.dimension())
                || !level.hasChunkAt(CENTRAL_TEA_BUSH)) {
            return;
        }

        CentralTeaBushData saved = data(level);
        if (saved.removed) {
            if (level.getBlockState(CENTRAL_TEA_BUSH).getBlock() instanceof TeaBushBlock) {
                level.removeBlock(CENTRAL_TEA_BUSH, false);
            }
            return;
        }

        BlockState lower = level.getBlockState(CENTRAL_TEA_BUSH);
        if (!(lower.getBlock() instanceof TeaBushBlock)
                || lower.getValue(TeaBushBlock.HALF) != DoubleBlockHalf.LOWER) {
            BlockState replacement = ModBlocks.TEA_BUSH.get().defaultBlockState()
                    .setValue(TeaBushBlock.STAGE, baseVisualStage())
                    .setValue(TeaBushBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(CENTRAL_TEA_BUSH, replacement, Block.UPDATE_ALL);
        }

        TeaBushManager.get(level).ensureMature(level, CENTRAL_TEA_BUSH);
    }

    private static void sendHitParticles(ServerLevel level, int count) {
        BlockState state = ModBlocks.TEA_BUSH.get().defaultBlockState()
                .setValue(TeaBushBlock.STAGE, baseVisualStage());
        level.sendParticles(
                new BlockParticleOption(ParticleTypes.BLOCK, state),
                CENTRAL_TEA_BUSH.getX() + 0.5D,
                CENTRAL_TEA_BUSH.getY() + 1.0D,
                CENTRAL_TEA_BUSH.getZ() + 0.5D,
                count,
                0.35D,
                0.75D,
                0.35D,
                0.03D);
    }

    private static void giveToPlayer(ServerPlayer player, ItemStack stack) {
        ItemStack hudStack = stack.copy();
        ItemStack remainder = stack.copy();
        if (!player.getInventory().add(remainder) && !remainder.isEmpty()) {
            player.drop(remainder, false);
        }
        ItemPickupHudPacket.sendTo(player, hudStack, hudStack.getCount(), false);
    }

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) ensurePlaced(level);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ensurePlaced(player.serverLevel());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) ensurePlaced(player.serverLevel());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_HIT_TICK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player
                && isCentralTeaBush(player.serverLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    private static CentralTeaBushData data(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CentralTeaBushData::new, CentralTeaBushData::load),
                DATA_NAME);
    }

    private static final class CentralTeaBushData extends SavedData {
        private int axeHits;
        private boolean removed;

        @Override
        public @Nonnull CompoundTag save(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
            tag.putInt("AxeHits", axeHits);
            tag.putBoolean("Removed", removed);
            return tag;
        }

        private static CentralTeaBushData load(CompoundTag tag, HolderLookup.Provider provider) {
            CentralTeaBushData data = new CentralTeaBushData();
            data.axeHits = Math.clamp(tag.getInt("AxeHits"), 0, AXE_HITS_TO_TAKE);
            data.removed = tag.getBoolean("Removed");
            return data;
        }
    }
}
