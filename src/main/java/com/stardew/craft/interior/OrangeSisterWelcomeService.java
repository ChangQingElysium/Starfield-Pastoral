package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.ModEntities;
import com.stardew.craft.entity.junimo.JunimoEntity;
import com.stardew.craft.farm.FarmInstance;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.item.quality.QualityHelper;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.UUID;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class OrangeSisterWelcomeService {

    private OrangeSisterWelcomeService() {}

    private static final String TARGET_PLAYER_NAME = "Da_Cheng_zi";
    private static final String COMPLETE_FLAG = "orange_sister_welcome_complete";

    private static final String TAG_PHASE = "stardewcraft_orange_sister_phase";
    private static final String TAG_TIMER = "stardewcraft_orange_sister_timer";
    private static final String TAG_JUMPS_LEFT = "stardewcraft_orange_sister_jumps_left";
    private static final String TAG_JUNIMO_UUID = "stardewcraft_orange_sister_junimo_uuid";

    private static final int PHASE_NONE = 0;
    private static final int PHASE_WAIT_TO_SPAWN = 1;
    private static final int PHASE_WALK_TO_PLAYER = 2;
    private static final int PHASE_JUMP_CELEBRATION = 3;
    private static final int PHASE_FADE_OUT = 4;

    private static final int SPAWN_DELAY_TICKS = 40;
    private static final int WALK_TIMEOUT_TICKS = 200;
    private static final int JUMP_INTERVAL_TICKS = 10;
    private static final int TOTAL_JUMPS = 3;
    private static final int FADE_OUT_TICKS = 26;
    private static final int ORANGE_COLOR = 0xFFA500;

    public static void scheduleIfEligible(ServerPlayer player) {
        if (!isEligibleForSequence(player)) {
            return;
        }
        if (getPhase(player) != PHASE_NONE) {
            return;
        }

        player.getPersistentData().putInt(TAG_PHASE, PHASE_WAIT_TO_SPAWN);
        player.getPersistentData().putInt(TAG_TIMER, SPAWN_DELAY_TICKS);
        player.getPersistentData().putInt(TAG_JUMPS_LEFT, 0);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide()) {
            return;
        }

        int phase = getPhase(player);
        if (phase == PHASE_NONE) {
            return;
        }

        if (!player.serverLevel().dimension().equals(ModDimensions.STARDEW_VALLEY)) {
            return;
        }

        switch (phase) {
            case PHASE_WAIT_TO_SPAWN -> tickWaitToSpawn(player);
            case PHASE_WALK_TO_PLAYER -> tickWalkToPlayer(player);
            case PHASE_JUMP_CELEBRATION -> tickJumpCelebration(player);
            case PHASE_FADE_OUT -> tickFadeOut(player);
            default -> clearState(player);
        }
    }

    private static void tickWaitToSpawn(ServerPlayer player) {
        if (decrementTimer(player) > 0) {
            return;
        }

        JunimoEntity junimo = spawnGiftJunimo(player);
        if (junimo == null) {
            player.getPersistentData().putInt(TAG_TIMER, 20);
            return;
        }

        player.getPersistentData().putInt(TAG_PHASE, PHASE_WALK_TO_PLAYER);
        player.getPersistentData().putInt(TAG_TIMER, WALK_TIMEOUT_TICKS);
    }

    private static void tickWalkToPlayer(ServerPlayer player) {
        JunimoEntity junimo = getTrackedJunimo(player);
        if (junimo == null) {
            junimo = spawnGiftJunimo(player);
            if (junimo == null) {
                clearState(player);
                return;
            }
        }

        int timer = decrementTimer(player);
        if (timer % 10 == 0) {
            junimo.setTarget(resolveTargetPos(player), null);
        }

        if (junimo.distanceToSqr(player) <= 4.0D || timer <= 0) {
            deliverGift(player, junimo);
            player.getPersistentData().putInt(TAG_PHASE, PHASE_JUMP_CELEBRATION);
            player.getPersistentData().putInt(TAG_TIMER, JUMP_INTERVAL_TICKS);
            player.getPersistentData().putInt(TAG_JUMPS_LEFT, TOTAL_JUMPS);
        }
    }

    private static void tickJumpCelebration(ServerPlayer player) {
        JunimoEntity junimo = getTrackedJunimo(player);
        if (junimo == null) {
            clearState(player);
            return;
        }

        if (decrementTimer(player) > 0) {
            return;
        }

        int jumpsLeft = player.getPersistentData().getInt(TAG_JUMPS_LEFT);
        if (jumpsLeft <= 0) {
            junimo.startFadeOut();
            player.getPersistentData().putInt(TAG_PHASE, PHASE_FADE_OUT);
            player.getPersistentData().putInt(TAG_TIMER, FADE_OUT_TICKS);
            player.serverLevel().playSound(null, junimo.blockPosition(), ModSounds.WAND.get(), SoundSource.NEUTRAL, 0.8F, 1.25F);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    junimo.getX(), junimo.getY() + 0.6D, junimo.getZ(),
                    18, 0.35D, 0.4D, 0.35D, 0.02D);
            return;
        }

        if (junimo.onGround()) {
            junimo.setDeltaMovement(junimo.getDeltaMovement().x, 0.42D, junimo.getDeltaMovement().z);
        }
        player.getPersistentData().putInt(TAG_JUMPS_LEFT, jumpsLeft - 1);
        player.getPersistentData().putInt(TAG_TIMER, JUMP_INTERVAL_TICKS);
        player.serverLevel().playSound(null, junimo.blockPosition(), ModSounds.LEAFRUSTLE.get(), SoundSource.NEUTRAL, 0.8F, 1.1F);
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                junimo.getX(), junimo.getY() + 0.4D, junimo.getZ(),
                8, 0.25D, 0.15D, 0.25D, 0.02D);
    }

    private static void tickFadeOut(ServerPlayer player) {
        JunimoEntity junimo = getTrackedJunimo(player);
        if (junimo != null && player.getPersistentData().getInt(TAG_TIMER) % 6 == 0) {
            player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                    junimo.getX(), junimo.getY() + 0.6D, junimo.getZ(),
                    6, 0.2D, 0.25D, 0.2D, 0.01D);
        }

        if (decrementTimer(player) > 0) {
            return;
        }

        clearState(player);
    }

    private static void deliverGift(ServerPlayer player, JunimoEntity junimo) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (!data.hasMailFlag(COMPLETE_FLAG)) {
            ItemStack oranges = createIridiumOranges();
            giveItemToPlayer(player, oranges);
            data.addMailFlag(COMPLETE_FLAG);
        }

        junimo.setHoldingType(JunimoEntity.HOLDING_NONE);
        junimo.getNavigation().stop();
        junimo.setTarget(null, null);

        player.displayClientMessage(Component.translatable("stardewcraft.orange_sister.thanks"), true);
        player.serverLevel().playSound(null, player.blockPosition(), ModSounds.NEW_ARTIFACT.get(), SoundSource.PLAYERS, 0.9F, 1.15F);
        player.serverLevel().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                20, 0.35D, 0.45D, 0.35D, 0.03D);
        player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 0.8D, player.getZ(),
                12, 0.4D, 0.35D, 0.4D, 0.02D);
    }

    private static JunimoEntity spawnGiftJunimo(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos spawnPos = resolveSpawnPos(player);
        BlockPos targetPos = resolveTargetPos(player);

        JunimoEntity junimo = new JunimoEntity(ModEntities.JUNIMO.get(), level);
        junimo.setJunimoColor(ORANGE_COLOR);
        junimo.setHoldingType(JunimoEntity.HOLDING_ORANGE);
        junimo.setGlowingTag(true);
        junimo.setNoTimeout(true);
        junimo.setPos(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D);
        junimo.setTarget(targetPos, null);
        level.addFreshEntity(junimo);

        player.getPersistentData().putUUID(TAG_JUNIMO_UUID, junimo.getUUID());

        level.playSound(null, spawnPos, ModSounds.TINY_WHIP.get(), SoundSource.NEUTRAL, 0.9F, 1.25F);
        level.sendParticles(ParticleTypes.END_ROD,
                spawnPos.getX() + 0.5D, spawnPos.getY() + 0.7D, spawnPos.getZ() + 0.5D,
                14, 0.3D, 0.45D, 0.3D, 0.02D);
        return junimo;
    }

    private static JunimoEntity getTrackedJunimo(ServerPlayer player) {
        if (!player.getPersistentData().hasUUID(TAG_JUNIMO_UUID)) {
            return null;
        }
        UUID junimoUuid = player.getPersistentData().getUUID(TAG_JUNIMO_UUID);
        AABB searchBox = new AABB(player.blockPosition()).inflate(128.0D);
        return player.serverLevel().getEntitiesOfClass(JunimoEntity.class, searchBox,
                entity -> entity.getUUID().equals(junimoUuid)).stream().findFirst().orElse(null);
    }

    private static boolean isEligibleForSequence(ServerPlayer player) {
        if (!TARGET_PLAYER_NAME.equals(player.getGameProfile().getName())) {
            return false;
        }

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.hasMailFlag(COMPLETE_FLAG)) {
            return false;
        }

        FarmInstance farm = FarmInstanceRegistry.get().getFarm(player.getUUID());
        return farm != null && farm.getOwnerUUID().equals(player.getUUID());
    }

    private static BlockPos resolveSpawnPos(ServerPlayer player) {
        FarmInstance farm = FarmInstanceRegistry.get().getFarm(player.getUUID());
        BlockPos base = farm != null ? farm.getSpawnPoint() : player.blockPosition();
        Direction direction = player.getDirection();
        if (direction == Direction.UP || direction == Direction.DOWN) {
            direction = Direction.SOUTH;
        }
        BlockPos preferred = base.relative(direction, 5);
        return findOpenFeetPos(player.serverLevel(), preferred, base.relative(direction, 4), player.blockPosition());
    }

    private static BlockPos resolveTargetPos(ServerPlayer player) {
        Direction direction = player.getDirection();
        if (direction == Direction.UP || direction == Direction.DOWN) {
            direction = Direction.SOUTH;
        }
        BlockPos preferred = player.blockPosition().relative(direction);
        return findOpenFeetPos(player.serverLevel(), preferred, player.blockPosition(), player.blockPosition().relative(direction.getClockWise()));
    }

    private static BlockPos findOpenFeetPos(ServerLevel level, BlockPos... candidates) {
        for (BlockPos candidate : candidates) {
            BlockPos resolved = resolveFeetPos(level, candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return candidates[0];
    }

    private static BlockPos resolveFeetPos(ServerLevel level, BlockPos center) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos feetPos = center.offset(0, dy, 0);
            if (!level.isEmptyBlock(feetPos) || !level.isEmptyBlock(feetPos.above())) {
                continue;
            }
            BlockState below = level.getBlockState(feetPos.below());
            if (!below.getCollisionShape(level, feetPos.below()).isEmpty()) {
                return feetPos;
            }
        }
        return null;
    }

    private static ItemStack createIridiumOranges() {
        Item orangeItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "orange"));
        ItemStack stack = new ItemStack(orangeItem, 999);
        QualityHelper.setQuality(stack, QualityHelper.IRIDIUM);
        QualityHelper.ensureQualityModelData(stack);
        return stack;
    }

    private static void giveItemToPlayer(ServerPlayer player, ItemStack stack) {
        ItemStack remaining = stack.copy();
        boolean added = player.getInventory().add(remaining);
        if (!added && !remaining.isEmpty()) {
            player.drop(remaining, false);
        }
        player.inventoryMenu.broadcastChanges();
    }

    private static int getPhase(ServerPlayer player) {
        return player.getPersistentData().getInt(TAG_PHASE);
    }

    private static int decrementTimer(ServerPlayer player) {
        int timer = Math.max(0, player.getPersistentData().getInt(TAG_TIMER) - 1);
        player.getPersistentData().putInt(TAG_TIMER, timer);
        return timer;
    }

    private static void clearState(ServerPlayer player) {
        player.getPersistentData().remove(TAG_PHASE);
        player.getPersistentData().remove(TAG_TIMER);
        player.getPersistentData().remove(TAG_JUMPS_LEFT);
        player.getPersistentData().remove(TAG_JUNIMO_UUID);
    }
}
