package com.stardew.craft.festival.desert;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class DesertFestivalMineService {
    private static final String DATA_NAME = "stardew_desert_festival_mine";
    private static final int SKULL_CAVERN_FIRST_FLOOR = 121;

    private DesertFestivalMineService() {
    }

    public static MineData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(MineData::new, MineData::load),
            DATA_NAME
        );
    }

    public static boolean isActive() {
        return DesertFestivalService.isFestivalOpen();
    }

    public static int currentRating(ServerLevel level) {
        MineData data = get(level);
        data.prepareForToday();
        return data.currentRating;
    }

    public static int highestRatingToday(ServerLevel level) {
        MineData data = get(level);
        data.prepareForToday();
        return data.highestRatingToday;
    }

    public static int displayRatingForMarlon(ServerLevel level) {
        int highest = highestRatingToday(level);
        return highest <= 0 ? 0 : highest + 1;
    }

    public static void resetCurrentRun(ServerLevel level) {
        MineData data = get(level);
        data.prepareForToday();
        if (data.currentRating != 0 || data.deepestFloorThisRun != 0) {
            data.currentRating = 0;
            data.deepestFloorThisRun = 0;
            data.setDirty();
        }
    }

    public static void recordFloorReached(ServerPlayer player, int previousFloor, int targetFloor, boolean shaft) {
        if (player == null || !(player.level() instanceof ServerLevel level) || !isActive()) {
            return;
        }
        if (targetFloor < SKULL_CAVERN_FIRST_FLOOR) {
            return;
        }
        MineData data = get(level);
        data.prepareForToday();
        if (targetFloor == SKULL_CAVERN_FIRST_FLOOR) {
            syncHud(player, data.currentRating + 1, false);
            return;
        }
        DesertFestivalMarlonChallengeService.recordSkullCavernFloorReached(player, targetFloor);
        int previousDeepest = Math.max(data.deepestFloorThisRun, SKULL_CAVERN_FIRST_FLOOR);
        if (targetFloor <= previousDeepest) {
            syncHud(player, data.currentRating + 1, false);
            return;
        }
        int gained = 0;
        if (shaft) {
            if (targetFloor / 5 > previousFloor / 5) {
                gained = targetFloor / 5 - previousFloor / 5;
            }
        } else if (targetFloor % 5 == 0) {
            gained = 1;
        }
        data.deepestFloorThisRun = targetFloor;
        if (gained > 0) {
            data.addRating(gained);
            scheduleRatingSound(player);
            syncHud(player, data.currentRating + 1, true);
        } else {
            data.setDirty();
            syncHud(player, data.currentRating + 1, false);
        }
    }

    public static void tryAddMonsterEggDrop(Collection<ItemEntity> drops, LivingEntity entity, ServerPlayer player, RandomSource random) {
        if (drops == null || entity == null || player == null || !(entity.level() instanceof ServerLevel level) || !isActive()) {
            return;
        }
        int count = monsterEggCount(level, skullFloorFromPos(entity.blockPosition()), random);
        if (count > 0) drops.add(new ItemEntity(level, entity.getX(), entity.getY(), entity.getZ(), new ItemStack(ModItems.CALICO_EGG.get(), count)));
    }

    /** Monster.InitializeForLocation rolls these into born loot, including Skull Cavern floor one. */
    public static int monsterEggCount(ServerLevel level, int floor, RandomSource random) {
        if (level.dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING || !isActive()) return 0;
        return rollMonsterEggCount(floor, currentRating(level), random);
    }

    public static int rollMonsterEggCount(int floor, int rating, RandomSource random) {
        if (floor < SKULL_CAVERN_FIRST_FLOOR) return 0;
        float chance = Math.min(.02F + (rating * 5 + 1 + floor - SKULL_CAVERN_FIRST_FLOOR) * .002F, .5F);
        return random.nextFloat() < chance ? 1 + random.nextInt(3) : 0;
    }

    public static void tryAddBarrelEggDrop(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level == null || pos == null || random == null || !isActive()) {
            return;
        }
        int floor = skullFloorFromPos(pos);
        if (floor <= SKULL_CAVERN_FIRST_FLOOR) {
            return;
        }
        int floorIndex = floor - SKULL_CAVERN_FIRST_FLOOR;
        int rating = currentRating(level);
        float chance = (floorIndex + rating * 2) * 0.003f;
        chance = Math.min(chance, 0.33f);
        if (random.nextFloat() < chance) {
            Block.popResource(level, pos, new ItemStack(ModItems.CALICO_EGG.get(), 1 + random.nextInt(3)));
        }
    }

    /** Source OnStoneDestroyed includes the first Skull Cavern floor (absolute mine level 121). */
    public static int rollStoneEggCount(int floor, RandomSource random) {
        if (floor < SKULL_CAVERN_FIRST_FLOOR) return 0;
        float chance = Math.min(0.01f + (floor - SKULL_CAVERN_FIRST_FLOOR) * 0.0005f, 0.5f);
        return random.nextFloat() < chance ? 1 + random.nextInt(3) : 0;
    }

    public static void tryAddStoneEggDrop(ServerLevel level, ServerPlayer player, BlockPos pos, RandomSource random) {
        if (level == null || player == null || pos == null || random == null || !isActive()
                || level.dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) return;
        int count = rollStoneEggCount(skullFloorFromPos(pos), random);
        if (count > 0) Block.popResource(level, pos, new ItemStack(ModItems.CALICO_EGG.get(), count));
    }









    public static boolean activateCalicoStatue(ServerPlayer player, ServerLevel level, BlockPos pos, RandomSource random) {
        if (player == null || level == null || random == null || !isActive()) {
            return false;
        }
        MineData data = get(level);
        data.prepareForToday();
        data.addRating(1);
        scheduleRatingSound(player);
        data.totalCalicoStatuesActivatedToday++;
        RandomSource statueRandom = calicoStatueRandom(level, data.totalCalicoStatuesActivatedToday);
        double averageDailyLuck = averageDailyLuck(level, player);
        int effectId = chooseCalicoStatueEffect(data, statueRandom, averageDailyLuck);
        data.addStatueEffect(effectId);
        applyImmediateStatueEffect(player, effectId);
        data.setDirty();
        PlayerStardewData playerData = PlayerDataManager.getPlayerData(player);
        PlayerDataEventHandler.syncPlayerData(player, playerData);
        syncHud(player, data.currentRating + 1, true);
        playCalicoStatueActivationEffects(level, pos);
        player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.mine.calico_statue_activated",
            Component.translatable(effectNameKey(effectId)), Component.translatable(effectDescriptionKey(effectId))));
        return true;
    }

    private static void syncHud(ServerPlayer player, int displayRating, boolean shake) {
        net.minecraftforge.network.PacketDistributor.sendToPlayer(
            player,
            new com.stardew.craft.network.payload.DesertFestivalMineHudPayload(Math.max(0, displayRating), shake)
        );
    }

    public static void clearHud(ServerPlayer player) {
        if (player != null) {
            syncHud(player, 0, false);
        }
    }

    public static void clearHudForAll(MinecraftServer server) {
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            clearHud(player);
        }
    }

    private static void playCalicoStatueActivationEffects(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, ModSounds.OPENBOX.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 1.35D;
        double centerZ = pos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.GLOW, centerX, centerY, centerZ, 20, 1.5D, 1.5D, 1.5D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD, centerX, centerY + 0.7D, centerZ, 8, 0.18D, 0.45D, 0.18D, 0.04D);
    }

    private static void scheduleRatingSound(ServerPlayer player) {
        if (player == null || player.server == null) {
            return;
        }
        player.server.tell(new TickTask(player.server.getTickCount() + 40, () -> {
            if (!player.isRemoved()) {
                player.playNotifySound(ModSounds.YOBA.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }));
    }

    public static float monsterDamageMultiplier(ServerLevel level) {
        if (level == null || !isActive()) {
            return 1.0F;
        }
        MineData data = get(level);
        data.prepareForToday();
        return Math.max(0.25F, 1.0F + data.statueEffectAmount(8) * 0.25F - data.statueEffectAmount(14) * 0.25F);
    }

    public static boolean meagerMealsActive(ServerLevel level) {
        if (level == null || !isActive()) {
            return false;
        }
        MineData data = get(level);
        data.prepareForToday();
        return data.statueEffectAmount(6) > 0;
    }

    public static boolean thinShellsActive(ServerLevel level) {
        if (level == null || !isActive()) {
            return false;
        }
        MineData data = get(level);
        data.prepareForToday();
        return data.statueEffectAmount(5) > 0;
    }

    public static boolean isInFestivalSkullCavern(ServerPlayer player) {
        if (player == null || !isActive()) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel) || player.level().dimension() != com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING) {
            return false;
        }
        return com.stardew.craft.mining.MiningDataManager.getPlayerData(player).getCurrentFloor() > 120;
    }

    private static int chooseCalicoStatueEffect(MineData data, RandomSource random, double averageDailyLuck) {
        if (random.nextDouble() < 0.51D + averageDailyLuck) {
            if (tryRollStatueEffect(data, random, 0.15D, 10, false)) return data.lastRolledEffect;
            if (tryRollStatueEffect(data, random, 0.01D, 17, true)) return data.lastRolledEffect;
            if (tryRollStatueEffect(data, random, 0.05D, 12, true)) return data.lastRolledEffect;
            if (tryRollStatueEffect(data, random, 0.10D, 15, true)) return data.lastRolledEffect;
            if (tryRollStatueEffect(data, random, 0.20D, 16, true)) return data.lastRolledEffect;
            if (tryRollStatueEffect(data, random, 0.10D, 14, true)) return data.lastRolledEffect;
            if (tryRollStatueEffect(data, random, 0.50D, 11, true)) return data.lastRolledEffect;
            return 13;
        }
        if (random.nextDouble() < 0.20D) {
            for (int tries = 0; tries < 30; tries++) {
                int effectId = random.nextInt(4);
                if (data.statueEffectAmount(effectId) <= 0) {
                    return effectId;
                }
            }
        }
        if (tryRollStatueEffect(data, random, 0.10D, 4, false)) return data.lastRolledEffect;
        if (tryRollStatueEffect(data, random, 0.10D, 9, false)) return data.lastRolledEffect;
        if (tryRollStatueEffect(data, random, 0.10D, 5, false)) return data.lastRolledEffect;
        if (tryRollStatueEffect(data, random, 0.10D, 6, false)) return data.lastRolledEffect;
        if (tryRollStatueEffect(data, random, 0.20D, 7, true)) return data.lastRolledEffect;
        if (tryRollStatueEffect(data, random, 0.20D, 8, true)) return data.lastRolledEffect;
        return 13;
    }

    private static boolean tryRollStatueEffect(MineData data, RandomSource random, double chance, int effectId, boolean canStack) {
        if (random.nextDouble() < chance && (canStack || data.statueEffectAmount(effectId) <= 0)) {
            data.lastRolledEffect = effectId;
            return true;
        }
        return false;
    }

    private static RandomSource calicoStatueRandom(ServerLevel level, int activationCount) {
        long seed = level.getSeed();
        seed ^= (long) currentAbsoluteDay() * 0x9E3779B97F4A7C15L;
        seed ^= (long) activationCount * 0xBF58476D1CE4E5B9L;
        seed ^= 0x4F1BBCDCBFA5401DL;
        return RandomSource.create(seed);
    }

    private static double averageDailyLuck(ServerLevel level, ServerPlayer fallback) {
        double total = 0.0D;
        int count = 0;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            total += PlayerStardewDataAPI.getDailyLuck(player);
            count++;
        }
        if (count > 0) {
            return total / count;
        }
        return PlayerStardewDataAPI.getDailyLuck(fallback);
    }

    private static void applyImmediateStatueEffect(ServerPlayer player, int effectId) {
        switch (effectId) {
            case 16 -> DesertFestivalService.giveEggs(player, 10);
            case 15 -> DesertFestivalService.giveEggs(player, 25);
            case 12 -> DesertFestivalService.giveEggs(player, 50);
            case 17 -> DesertFestivalService.giveEggs(player, 100);
            case 11 -> {
                PlayerStardewData data = PlayerDataManager.getPlayerData(player);
                data.setHealth(data.getMaxHealth());
                data.setEnergy(data.getMaxEnergy());
                player.setHealth(player.getMaxHealth());
            }
            case 10 -> player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 6000, 0, false, true, true));
            default -> {
            }
        }
    }

    private static String effectNameKey(int effectId) {
        return "stardewcraft.desert_festival.mine.calico_statue.name." + effectId;
    }

    private static String effectDescriptionKey(int effectId) {
        return "stardewcraft.desert_festival.mine.calico_statue.description." + effectId;
    }

    public static boolean hasClaimedMarlonRatingPrizeToday(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return data.getLastGotDesertFestivalRatingPrizeFromMarlon() == currentAbsoluteDay();
    }

    public static void openMarlonRatingDialog(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isActive()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.not_festival"));
            return;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.getLastGotDesertFestivalRatingPrizeFromMarlon() == currentAbsoluteDay()) {
            int festivalDay = com.stardew.craft.festival.FestivalService.getDayOfPassiveFestival(DesertFestivalService.FESTIVAL_ID);
            player.sendSystemMessage(Component.translatable(festivalDay == 3
                ? "stardewcraft.desert_festival.marlon.next_year"
                : "stardewcraft.desert_festival.marlon.come_back"));
            return;
        }
        int rating = displayRatingForMarlon(level);
        if (rating <= 0) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.no_rating"));
            return;
        }
        if (rating >= 1000) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.rating_1000"));
            return;
        }
        net.minecraftforge.network.PacketDistributor.sendToPlayer(
            player,
            new com.stardew.craft.network.payload.OpenDesertFestivalMarlonRatingPayload(rating)
        );
    }

    public static void handleMarlonRatingClaim(ServerPlayer player) {
        if (player == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isActive()) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.not_festival"));
            return;
        }
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data.getLastGotDesertFestivalRatingPrizeFromMarlon() == currentAbsoluteDay()) {
            int festivalDay = com.stardew.craft.festival.FestivalService.getDayOfPassiveFestival(DesertFestivalService.FESTIVAL_ID);
            player.sendSystemMessage(Component.translatable(festivalDay == 3
                ? "stardewcraft.desert_festival.marlon.next_year"
                : "stardewcraft.desert_festival.marlon.come_back"));
            return;
        }
        int rating = displayRatingForMarlon(level);
        if (rating <= 0) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.no_rating"));
            return;
        }
        if (rating >= 1000) {
            player.sendSystemMessage(Component.translatable("stardewcraft.desert_festival.marlon.rating_1000"));
            return;
        }
        data.setLastGotDesertFestivalRatingPrizeFromMarlon(currentAbsoluteDay());
        Reward reward = rewardForRating(rating, data);
        DesertFestivalService.giveEggs(player, reward.eggCount());
        if (!reward.extra().isEmpty()) {
            ItemStack extra = reward.extra();
            if (!player.getInventory().add(extra)) {
                player.drop(extra, false);
            }
        }
        PlayerDataEventHandler.syncPlayerData(player, data);
        player.sendSystemMessage(Component.translatable(reward.messageKey(), rating));
    }

    private static Reward rewardForRating(int rating, PlayerStardewData data) {
        if (rating >= 55) {
            return new Reward(500, new ItemStack(ModItems.MAGIC_ROCK_CANDY.get()), "stardewcraft.desert_festival.marlon.rating_55");
        }
        if (rating >= 25) {
            ItemStack extra;
            if (!data.hasMailFlag("DF_Gil_Hat")) {
                data.addMailFlag("DF_Gil_Hat");
                extra = ItemStack.EMPTY;
            } else {
                extra = stackByPath("triple_shot_espresso", 5);
            }
            return new Reward(200, extra, "stardewcraft.desert_festival.marlon.rating_25");
        }
        if (rating >= 20) {
            return new Reward(100, stackByPath("triple_shot_espresso", 5), "stardewcraft.desert_festival.marlon.rating_20_24");
        }
        if (rating >= 15) {
            return new Reward(50, stackByPath("triple_shot_espresso", 3), "stardewcraft.desert_festival.marlon.rating_15_19");
        }
        if (rating >= 10) {
            return new Reward(25, stackByPath("triple_shot_espresso", 1), "stardewcraft.desert_festival.marlon.rating_10_14");
        }
        if (rating >= 5) {
            return new Reward(10, new ItemStack(ModItems.COFFEE.get()), "stardewcraft.desert_festival.marlon.rating_5_9");
        }
        return new Reward(1, stackByPath("miner_s_treat", 1), "stardewcraft.desert_festival.marlon.rating_1_4");
    }

    private static ItemStack stackByPath(String path, int count) {
        net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation("stardewcraft", path);
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
        if (item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, count);
    }

    private static int skullFloorFromPos(BlockPos pos) {
        int floor = Math.round((pos.getZ() - 14) / (float) com.stardew.craft.mining.MiningCoordinates.FLOOR_SPACING);
        return Math.max(0, floor);
    }

    private static int currentAbsoluteDay() {
        return StardewTimeManager.get().getAbsoluteDay();
    }

    private record Reward(int eggCount, ItemStack extra, String messageKey) {
    }

    public static final class MineData extends SavedData {
        private int dayKey = Integer.MIN_VALUE;
        private int currentRating;
        private int highestRatingToday;
        private int deepestFloorThisRun;
        private int totalCalicoStatuesActivatedToday;
        private int lastRolledEffect = 13;
        private final int[] statueEffects = new int[18];

        private void prepareForToday() {
            int currentDay = currentAbsoluteDay();
            if (!isActive()) {
                if (currentRating != 0 || highestRatingToday != 0 || deepestFloorThisRun != 0
                    || totalCalicoStatuesActivatedToday != 0 || hasAnyStatueEffects() || dayKey != currentDay) {
                    dayKey = currentDay;
                    currentRating = 0;
                    highestRatingToday = 0;
                    deepestFloorThisRun = 0;
                    totalCalicoStatuesActivatedToday = 0;
                    clearStatueEffects();
                    setDirty();
                }
                return;
            }
            if (dayKey != currentDay) {
                dayKey = currentDay;
                currentRating = 0;
                highestRatingToday = 0;
                deepestFloorThisRun = 0;
                totalCalicoStatuesActivatedToday = 0;
                clearStatueEffects();
                setDirty();
            }
        }

        private void addRating(int amount) {
            currentRating = Math.max(0, currentRating + amount);
            highestRatingToday = Math.max(highestRatingToday, currentRating);
            setDirty();
        }

        private int statueEffectAmount(int effectId) {
            if (effectId < 0 || effectId >= statueEffects.length) {
                return 0;
            }
            return statueEffects[effectId];
        }

        private void addStatueEffect(int effectId) {
            if (effectId >= 0 && effectId < statueEffects.length) {
                statueEffects[effectId]++;
                setDirty();
            }
        }

        private boolean hasAnyStatueEffects() {
            for (int amount : statueEffects) {
                if (amount != 0) {
                    return true;
                }
            }
            return false;
        }

        private void clearStatueEffects() {
            for (int i = 0; i < statueEffects.length; i++) {
                statueEffects[i] = 0;
            }
            lastRolledEffect = 13;
        }

        public static MineData load(CompoundTag tag, HolderLookup.Provider provider) {
            MineData data = new MineData();
            data.dayKey = tag.contains("DayKey") ? tag.getInt("DayKey") : Integer.MIN_VALUE;
            data.currentRating = Math.max(0, tag.getInt("CurrentRating"));
            data.highestRatingToday = Math.max(0, tag.getInt("HighestRatingToday"));
            data.deepestFloorThisRun = Math.max(0, tag.getInt("DeepestFloorThisRun"));
            data.totalCalicoStatuesActivatedToday = Math.max(0, tag.getInt("TotalCalicoStatuesActivatedToday"));
            int[] effects = tag.getIntArray("CalicoStatueEffects");
            for (int i = 0; i < Math.min(effects.length, data.statueEffects.length); i++) {
                data.statueEffects[i] = Math.max(0, effects[i]);
            }
            return data;
        }

        @Override
        public @NotNull CompoundTag save(@SuppressWarnings("null") @NotNull CompoundTag tag,
                         @SuppressWarnings("null") @NotNull HolderLookup.Provider provider) {
            tag.putInt("DayKey", dayKey);
            tag.putInt("CurrentRating", currentRating);
            tag.putInt("HighestRatingToday", highestRatingToday);
            tag.putInt("DeepestFloorThisRun", deepestFloorThisRun);
            tag.putInt("TotalCalicoStatuesActivatedToday", totalCalicoStatuesActivatedToday);
            tag.putIntArray("CalicoStatueEffects", statueEffects);
            return tag;
        }
    }
}
