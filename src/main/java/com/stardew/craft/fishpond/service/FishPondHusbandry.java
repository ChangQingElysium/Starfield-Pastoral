package com.stardew.craft.fishpond.service;

import com.stardew.craft.api.v1.fishpond.StardewFishPondDailyContext;
import com.stardew.craft.api.v1.fishpond.StardewFishPondRequestContext;
import com.stardew.craft.api.v1.internal.fishpond.StardewFishPondEventRegistry;
import com.stardew.craft.api.v1.internal.fishpond.StardewFishPondSnapshots;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.blockentity.FishPondBucketBlockEntity;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.artisan.PreserveType;
import com.stardew.craft.item.artisan.PreservesItem;
import com.stardew.craft.network.ItemPickupHudPacket;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.time.StardewTimeManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

/** Authoritative pond lifecycle; presentation and prefab geometry never determine breeding. */
public final class FishPondHusbandry {
    private FishPondHusbandry() {}
    private static final int QUEST_BASE_EXP = 20;
    private static final float QUEST_SPAWNRATE_EXP_MULTIPLIER = 5F;
    private static final double JUMP_SYNC_RADIUS = 48.0D;
    private static final double JUMP_SYNC_RADIUS_SQR = JUMP_SYNC_RADIUS * JUMP_SYNC_RADIUS;
    private static final Map<String, DebugAdvanceCursor> DEBUG_ADVANCE_CURSORS = new HashMap<>();



    public static void onNewDay(ServerLevel level) {
        applyDayUpdates(level, null, 1, currentAbsoluteDay());
    }

    public static void advanceNearby(ServerLevel level, BlockPos center, int days) {
        applyDayUpdates(level, center, days, reserveDebugAdvanceStartDay(level, days));
    }

    private static void applyDayUpdates(ServerLevel level, BlockPos center, int days, int startDay) {
        if (days <= 0) {
            return;
        }

        FishPondWorldData worldData = FishPondWorldData.get(level);
        worldData.reconcileFarmOwnership(level);
        String dimensionId = level.dimension().location().toString();
        boolean anyColorChanged = false;

        for (FishPondRecord pond : worldData.getPonds()) {
            if (com.stardew.craft.building.runtime.FishPondPrefabs.at(level,pond.managerPos())==null || !dimensionId.equals(pond.dimensionId())) {
                continue;
            }
            if (center != null && !isNearPond(center, pond, 5)) {
                continue;
            }
            for (int i = 0; i < days; i++) {
                if (applySingleDay(level, worldData, pond, startDay + i)) {
                    anyColorChanged = true;
                }
            }
        }

        if (anyColorChanged) {
            FishPondColorSyncService.broadcastSnapshot(level);
        }
    }

    /** Source order: clear completed request, roll output, advance reproduction, request/spawn, color. */
    public static boolean applySingleDay(ServerLevel level, FishPondWorldData worldData,
                                         FishPondRecord pond, int absoluteDay) {
        if (pond.lastUpdateDay() == absoluteDay) return false;
        pond.setLastUpdateDay(absoluteDay);
        if (pond.hasCompletedRequest()) {
            pond.setNeededItemId(""); pond.setNeededItemCount(0); pond.setHasCompletedRequest(false);
        }
        var data = FishPondDataService.get();
        var rule = data.resolveFishTypeId(pond.fishTypeId()).orElse(null);
        if (rule != null && pond.currentPopulation() > 0) {
            var random = RandomSource.create(mixSeed(level.getSeed() ^ pond.pondId().hashCode(), absoluteDay));
            double chance = rule.baseMinProduceChance() >= rule.baseMaxProduceChance()
                ? rule.baseMinProduceChance()
                : Mth.lerp(pond.currentPopulation()/10.0, rule.baseMinProduceChance(), rule.baseMaxProduceChance());
            if (random.nextDouble() < chance) {
                ItemStack output = createProducedItemStack(pond, data.rollProducedItem(pond, random).orElse(null), random);
                pond.setOutputItemId(output.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(output.getItem()).toString());
                pond.setOutputCount(output.getCount());
                pond.setOutputFishType(pond.fishTypeId());
            }
            int spawnTime = data.resolveSpawnTime(pond);
            pond.setMaxPopulation(data.resolveCurrentMaxPopulation(pond));
            pond.setDaysSinceSpawn(Math.min(spawnTime, pond.daysSinceSpawn()+1));
            if (pond.daysSinceSpawn() >= spawnTime) {
                var request = data.resolveNeededItem(pond, level.getSeed(), absoluteDay);
                if (request.isPresent()) {
                    if (pond.neededItemId().isBlank()) {
                        pond.setNeededItemId(request.get().itemId()); pond.setNeededItemCount(request.get().count());
                    }
                } else spawnFish(level, pond, random);
            }
            if (pond.currentPopulation() == 10 && pond.fishTypeId().equals("stardewcraft:crab")) {
                var players = com.stardew.craft.player.PlayerDataManager.get();
                players.getAllPlayerData().forEach((uuid, state) -> {
                    if (!state.hasMailFlag("FullCrabPond")) {
                        state.addMailFlag("FullCrabPond");
                        com.stardew.craft.npc.runtime.NpcDialogueEventData.get(level.getServer()).activate(uuid,"FullCrabPond",14);
                    }
                });
                players.setDirty();
            }
            pond.setWaterColor(data.resolveWaterColor(pond));
        }
        // The spawn clock is persistent even on a day without output or population changes.
        worldData.markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        StardewFishPondEventRegistry.announceDaily(new StardewFishPondDailyContext(
            level, absoluteDay, StardewFishPondSnapshots.from(level, pond)));
        return true;
    }

    private static ItemStack createProducedItemStack(FishPondRecord pond,
                                                     FishPondDataService.ProducedItem producedItem,
                                                     RandomSource random) {
        if (producedItem == null) {
            return ItemStack.EMPTY;
        }

        int count = Math.max(1, producedItem.rollStackCount(random));
        if ("(O)812".equals(producedItem.itemId()) || "stardewcraft:roe".equals(producedItem.itemId())) {
            count = applyRoeBonusRolls(count, random);
            return createRoeStack(pond, count);
        }
        ItemStack stack = FishPondQualifiedItemService.createItemStack(producedItem.itemId(), count);
        if (!stack.isEmpty() && pond.goldenAnimalCracker()) {
            stack.grow(stack.getCount());
        }
        return stack;
    }

    public static ItemStack createOutputStack(FishPondRecord pond) {
        if (pond.outputItemId().isBlank() || pond.outputCount() <= 0) {
            return ItemStack.EMPTY;
        }

        ResourceLocation outputId = ResourceLocation.tryParse(pond.outputItemId());
        if (outputId == null || !BuiltInRegistries.ITEM.containsKey(outputId)) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(outputId), pond.outputCount());
        if (stack.getItem() == ModItems.ROE.get()) {
            PreservesItem.createFlavored(PreserveType.ROE, fishStack(pond.outputFishType().isBlank() ? pond.fishTypeId() : pond.outputFishType()), stack);
        }
        return stack;
    }

    private static ItemStack createRoeStack(FishPondRecord pond, int count) {
        if (pond.goldenAnimalCracker()) {
            count *= 2;
        }
        ItemStack stack = new ItemStack(ModItems.ROE.get(), count);
        PreservesItem.createFlavored(PreserveType.ROE, createFishStack(pond), stack);
        return stack;
    }

    private static int applyRoeBonusRolls(int count, RandomSource random) {
        int adjusted = count;
        while (random.nextDouble() < 0.2D) {
            adjusted++;
        }
        return adjusted;
    }

    public static void resolveNeeds(FishPondRecord pond) {
        pond.setNeededItemCount(0);
        pond.setHasCompletedRequest(true);
        pond.setLastUnlockedPopulationGate(pond.maxPopulation() + 1);
        pond.setMaxPopulation(FishPondDataService.get().resolveCurrentMaxPopulation(pond));
        pond.setDaysSinceSpawn(0);
    }

    public static void resolveNeeds(ServerLevel level, FishPondRecord pond, ServerPlayer player) {
        resolveNeeds(pond);

        int spawnTime = Math.max(0, FishPondDataService.get().resolveSpawnTime(pond));
        if (player != null) {
            int bonusExperience = (int) (spawnTime * QUEST_SPAWNRATE_EXP_MULTIPLIER);
            PlayerStardewDataAPI.addExperience(player, SkillType.FISHING, QUEST_BASE_EXP + bonusExperience);
        }

        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        FishPondColorSyncService.broadcastSnapshot(level);
        broadcastHappyFishJump(level, pond);
    }

    private static void broadcastHappyFishJump(ServerLevel level, FishPondRecord pond) {
        RandomSource random = level.getRandom();
        int delayTicks = 20;
        int jumps = Math.max(1, Math.min(pond.currentPopulation(), 10));
        for (int i = 0; i < jumps; i++) {
            broadcastSingleFishJump(level, pond, random, delayTicks);
            delayTicks += Mth.floor(Mth.lerp(random.nextFloat(), 3.0F, 5.0F));
        }
    }

    public static void broadcastAmbientFishJump(ServerLevel level, FishPondRecord pond) {
        broadcastSingleFishJump(level, pond, level.getRandom(), 0);
    }

    private static void broadcastSpawnFishJump(ServerLevel level, FishPondRecord pond, RandomSource random) {
        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId == null) {
            return;
        }
        String path = fishId.getPath();
        if ("coral".equals(path) || "sea_urchin".equals(path)) {
            return;
        }
        int delayTicks = Mth.floor(Mth.lerp(random.nextFloat(), 40.0F, 100.0F));
        broadcastSingleFishJump(level, pond, random, delayTicks);
    }

    private static void broadcastSingleFishJump(ServerLevel level, FishPondRecord pond, RandomSource random, int delayTicks) {
        if (pond.currentPopulation() <= 0 || pond.fishTypeId().isBlank()) {
            return;
        }

        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId == null || !BuiltInRegistries.ITEM.containsKey(fishId)) {
            return;
        }

        Vec3 end = new Vec3(
            (pond.minX() + pond.maxX() + 1) * 0.5D,
            pond.maxY() + 8.0D/9,
            (pond.minZ() + pond.maxZ() + 1) * 0.5D
        );
        Vec3 start = pickJumpStart(pond, random, end);
        float jumpHeight = Mth.lerp(random.nextFloat(), 75.0F / 64.0F, 100.0F / 64.0F);
        float angularVelocity = (float) Math.toRadians(Mth.lerp(random.nextFloat(), 20.0F, 40.0F));
        boolean flipped = start.x > end.x;

        com.stardew.craft.network.payload.FishPondJumpSyncPayload payload =
            new com.stardew.craft.network.payload.FishPondJumpSyncPayload(
                level.dimension().location().toString(),
                pond.fishTypeId(),
                start.x,
                start.y,
                start.z,
                end.x,
                end.y,
                end.z,
                jumpHeight,
                angularVelocity,
                delayTicks,
                flipped
            );

        for (ServerPlayer target : level.players()) {
            if (target.position().distanceToSqr(end) > JUMP_SYNC_RADIUS_SQR) {
                continue;
            }
            PacketDistributor.sendToPlayer(target, payload);
        }
    }

    private static Vec3 pickJumpStart(FishPondRecord pond, RandomSource random, Vec3 fallback) {
        if (pond.waterCells().isEmpty()) {
            return fallback;
        }

        int targetIndex = random.nextInt(pond.waterCells().size());
        int index = 0;
        for (Long packedPos : pond.waterCells()) {
            if (index++ != targetIndex) {
                continue;
            }

            BlockPos cell = BlockPos.of(packedPos);
            double offsetX = Mth.lerp(random.nextDouble(), -0.25D, 0.25D);
            double offsetZ = Mth.lerp(random.nextDouble(), -0.25D, 0.25D);
            return new Vec3(cell.getX() + 0.5D + offsetX, pond.maxY() + 8.0D/9, cell.getZ() + 0.5D + offsetZ);
        }

        return fallback;
    }

    private static boolean spawnFish(ServerLevel level, FishPondRecord pond, RandomSource random) {
        if (pond.currentPopulation() >= pond.maxPopulation() || pond.currentPopulation() <= 0) {
            return false;
        }
        pond.setDaysSinceSpawn(0);
        pond.setCurrentPopulation(Math.min(pond.maxPopulation(), pond.currentPopulation() + 1));
        broadcastSpawnFishJump(level, pond, random);
        return true;
    }

    private static ItemStack createFishStack(FishPondRecord pond) { return fishStack(pond.fishTypeId()); }

    private static ItemStack fishStack(String id) {
        ResourceLocation fishId = ResourceLocation.tryParse(id);
        if (fishId == null || !BuiltInRegistries.ITEM.containsKey(fishId)) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(fishId));
    }

    private static boolean isNearPond(BlockPos center, FishPondRecord pond, int margin) {
        return center.getX() >= pond.minX() - margin && center.getX() <= pond.maxX() + margin
            && center.getY() >= pond.minY() - margin && center.getY() <= pond.maxY() + margin
            && center.getZ() >= pond.minZ() - margin && center.getZ() <= pond.maxZ() + margin;
    }

    private static int currentAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        if (time == null) {
            return 1;
        }
        return (time.getCurrentYear() - 1) * (28 * 4) + time.getCurrentSeason() * 28 + time.getCurrentDay();
    }

    private static int reserveDebugAdvanceStartDay(ServerLevel level, int days) {
        int currentDay = currentAbsoluteDay();
        String key = level.getServer().getWorldData().getLevelName() + "|" + level.dimension().location();
        DebugAdvanceCursor cursor = DEBUG_ADVANCE_CURSORS.get(key);
        if (cursor == null || cursor.baseDay() != currentDay) {
            cursor = new DebugAdvanceCursor(currentDay, 0);
        }

        int startDay = currentDay + cursor.nextOffset() + 1;
        DEBUG_ADVANCE_CURSORS.put(key, new DebugAdvanceCursor(currentDay, cursor.nextOffset() + days));
        return startDay;
    }

    private static long mixSeed(long pondSeed, long daySeed) {
        long seed = 1469598103934665603L;
        seed = (seed ^ pondSeed) * 1099511628211L;
        seed = (seed ^ daySeed) * 1099511628211L;
        return seed;
    }

    private record DebugAdvanceCursor(int baseDay, int nextOffset) {
    }
    private static final int HARVEST_BASE_EXP = 10;
    private static final float HARVEST_OUTPUT_EXP_MULTIPLIER = 0.04F;

    public enum OutputCollectResult {
        NO_POND(false),
        EMPTY(false),
        INVENTORY_FULL(false),
        COLLECTED(true);

        private final boolean changedState;

        OutputCollectResult(boolean changedState) {
            this.changedState = changedState;
        }

        public boolean changedState() {
            return changedState;
        }
    }

    public enum ItemAbsorbResult {
        IGNORED(false),
        NEED_ITEM_ACCEPTED(true),
        FISH_ACCEPTED(true),
        GOLDEN_CRACKER_ACCEPTED(true),
        WRONG_FISH(false),
        POND_FULL(false);

        private final boolean changedState;

        ItemAbsorbResult(boolean changedState) {
            this.changedState = changedState;
        }

        public boolean changedState() {
            return changedState;
        }
    }



    public static OutputCollectResult collectOutputAtBucket(ServerLevel level, BlockPos bucketPos, ServerPlayer player) {
        FishPondWorldData worldData = FishPondWorldData.get(level);
        FishPondRecord pond = worldData.findPondByBucket(level.dimension().location().toString(), bucketPos).orElse(null);
        if (pond == null) {
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("message.stardew_craft.fish_pond_bucket.empty"));
            return OutputCollectResult.NO_POND;
        }
        return collectOutput(level, bucketPos, player, pond, worldData);
    }

    public static OutputCollectResult collectOutput(ServerLevel level,
                                                    BlockPos bucketPos,
                                                    ServerPlayer player,
                                                    FishPondRecord pond,
                                                    FishPondWorldData worldData) {
        ItemStack output = createOutputStack(pond);
        if (output.isEmpty()) {
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("message.stardew_craft.fish_pond_bucket.empty"));
            return OutputCollectResult.EMPTY;
        }

        if (!canFullyAddToInventory(player.getInventory(), output)) {
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("message.stardew_craft.fish_pond_bucket.inventory_full"));
            return OutputCollectResult.INVENTORY_FULL;
        }

        ItemStack granted = output.copy();
        if (!player.getInventory().add(granted) || !granted.isEmpty()) {
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("message.stardew_craft.fish_pond_bucket.inventory_full"));
            return OutputCollectResult.INVENTORY_FULL;
        }

        pond.setOutputItemId("");
        pond.setOutputCount(0);
        worldData.markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, bucketPos);

        awardHarvestExperience(player, output);
        ItemPickupHudPacket.sendTo(player, output, output.getCount(), false);
        com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player, Component.translatable("message.stardew_craft.fish_pond_bucket.collected"));
        return OutputCollectResult.COLLECTED;
    }

    public static ItemStack pullFishForFishingRod(ServerLevel level, BlockPos bobberPos) {
        if (StardewTimeManager.get().getCurrentTime() >= StardewTimeManager.PASS_OUT_TIME) {
            return ItemStack.EMPTY;
        }

        FishPondWorldData worldData = FishPondWorldData.get(level);
        FishPondRecord pond = worldData.findPondContainingWater(level.dimension().location().toString(), bobberPos).orElse(null);
        if (pond == null || pond.currentPopulation() <= 0 || pond.fishTypeId().isBlank()) {
            return ItemStack.EMPTY;
        }

        ResourceLocation fishId = ResourceLocation.tryParse(pond.fishTypeId());
        if (fishId == null || !BuiltInRegistries.ITEM.containsKey(fishId)) {
            return ItemStack.EMPTY;
        }

        pond.setCurrentPopulation(Math.max(0, pond.currentPopulation() - 1));
        pond.setWaterColor(FishPondDataService.get().resolveWaterColor(pond));
        worldData.markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        FishPondColorSyncService.broadcastSnapshot(level);
        return new ItemStack(BuiltInRegistries.ITEM.get(fishId));
    }

    public static ItemAbsorbResult absorbItemEntity(ServerLevel level, FishPondRecord pond, ItemEntity entity) {
        ItemStack stack=entity.getItem();
        var actor=resolveResponsiblePlayer(level,entity,pond);
        if(entity.getOwner() instanceof ServerPlayer owner &&
            com.stardew.craft.farm.FarmResourceOwnership.resolveManageableOwner(level,pond.managerPos(),owner)==null)
            return ItemAbsorbResult.IGNORED;
        var result=offer(level,pond,stack,actor,stack.getCount());
        if(result.changedState()) {
            spawnEntrySplash(level,entity);
            if(stack.isEmpty())entity.discard();else entity.setItem(stack);
        }
        return result;
    }

    /** A single transaction shared by hand interaction and physical item entry. */
    public static ItemAbsorbResult offer(ServerLevel level,FishPondRecord pond,ItemStack stack,
                                         ServerPlayer actor,int maximum) {
        if(stack.isEmpty() || maximum<=0)return ItemAbsorbResult.IGNORED;
        var rules=FishPondDataService.get();ItemAbsorbResult result;int consumed=1;
        if(stack.is(ModItems.GOLDEN_ANIMAL_CRACKER.get())) {
            if(pond.goldenAnimalCracker() || pond.currentPopulation()<=0)return ItemAbsorbResult.IGNORED;
            pond.setGoldenAnimalCracker(true);result=ItemAbsorbResult.GOLDEN_CRACKER_ACCEPTED;
        } else if(!pond.hasCompletedRequest() && pond.neededItemCount()>0 && rules.resolveNeededItem(pond).isPresent()
                    && FishPondQualifiedItemService.matches(pond.neededItemId(),stack)) {
            consumed=Math.min(Math.min(maximum,stack.getCount()),pond.neededItemCount());
            String requested=pond.neededItemId();pond.setNeededItemCount(pond.neededItemCount()-consumed);
            if(pond.neededItemCount()==0) {
                resolveNeeds(level,pond,actor);
                StardewFishPondEventRegistry.announceRequest(new StardewFishPondRequestContext(level,actor,requested,consumed,StardewFishPondSnapshots.from(level,pond)));
                if(actor!=null)for(var player:level.getServer().getPlayerList().getPlayers())
                    com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,Component.translatable("message.stardew_craft.fish_pond.request_completed_global",actor.getDisplayName()));
                level.playSound(null,pond.managerPos(),ModSounds.JINGLE1.get(),SoundSource.BLOCKS,1,1);
            }
            result=ItemAbsorbResult.NEED_ITEM_ACCEPTED;
        } else {
            if(rules.resolve(stack).isEmpty())return ItemAbsorbResult.IGNORED;
            String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if(!pond.fishTypeId().isBlank() && !pond.fishTypeId().equals(id))return ItemAbsorbResult.WRONG_FISH;
            int capacity=pond.fishTypeId().isBlank()?rules.resolveMaxPopulation(stack):rules.resolveCurrentMaxPopulation(pond);
            if(pond.currentPopulation()>=capacity)return ItemAbsorbResult.POND_FULL;
            pond.setFishTypeId(id);pond.setMaxPopulation(capacity);pond.setCurrentPopulation(pond.currentPopulation()+1);pond.setEmpty(false);
            result=ItemAbsorbResult.FISH_ACCEPTED;
        }
        stack.shrink(consumed);
        pond.setWaterColor(rules.resolveWaterColor(pond));FishPondWorldData.get(level).markChanged();
        FishPondBucketBlockEntity.syncVisualState(level,pond.bucketPos());FishPondColorSyncService.broadcastSnapshot(level);
        return result;
    }

    public static boolean use(ServerLevel level,FishPondRecord pond,ServerPlayer player,net.minecraft.world.InteractionHand hand) {
        ItemStack held=player.getItemInHand(hand);
        // Source doAction collects an existing output before accepting fish/request material.
        if(!held.is(ModItems.GOLDEN_ANIMAL_CRACKER.get()) && pond.outputCount()>0) {
            collectOutput(level,pond.bucketPos(),player,pond,FishPondWorldData.get(level));return true;
        }
        ItemStack input=player.isCreative()?held.copy():held;
        var result=offer(level,pond,input,player,1);
        if(result.changedState()) {
            level.playSound(null,pond.managerPos(),ModSounds.DROP_ITEM_IN_WATER.get(),SoundSource.BLOCKS,1,1);
            return true;
        }
        if(result==ItemAbsorbResult.WRONG_FISH || result==ItemAbsorbResult.POND_FULL) {
            com.stardew.craft.network.GlobalHudMessagePayload.sendTo(player,Component.translatable(
                result==ItemAbsorbResult.WRONG_FISH?"fishpond.stardewcraft.wrong_fish":"fishpond.stardewcraft.full"));return true;
        }
        return false;
    }

    private static ServerPlayer resolveResponsiblePlayer(ServerLevel level, ItemEntity itemEntity, FishPondRecord pond) {
        if (itemEntity.getOwner() instanceof ServerPlayer owner) {
            return owner;
        }
        if (pond.ownerPlayerUuid().isBlank()) {
            return null;
        }
        return level.getServer().getPlayerList().getPlayer(UUID.fromString(pond.ownerPlayerUuid()));
    }

    private static void spawnEntrySplash(ServerLevel level, ItemEntity itemEntity) {
        level.playSound(null, itemEntity.blockPosition(), ModSounds.DROP_ITEM_IN_WATER.get(), SoundSource.BLOCKS, 0.45F, 0.95F + level.random.nextFloat() * 0.1F);
        level.sendParticles(
            ParticleTypes.SPLASH,
            itemEntity.getX(),
            itemEntity.getY(),
            itemEntity.getZ(),
            8,
            0.18D,
            0.04D,
            0.18D,
            0.02D
        );
    }

    private static boolean canFullyAddToInventory(Inventory inventory, ItemStack stack) {
        int remaining = stack.getCount();
        int inventoryMax = inventory.getMaxStackSize();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing.isEmpty()) {
                remaining -= Math.min(stack.getMaxStackSize(), inventoryMax);
            } else if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int slotLimit = Math.min(existing.getMaxStackSize(), inventoryMax);
                remaining -= Math.max(0, slotLimit - existing.getCount());
            }
            if (remaining <= 0) {
                return true;
            }
        }

        return false;
    }

    private static void awardHarvestExperience(ServerPlayer player, ItemStack stack) {
        int totalSellPrice = Math.max(0, StardewItemDataApi.getSellPrice(stack));
        int bonusExperience = (int) (totalSellPrice * HARVEST_OUTPUT_EXP_MULTIPLIER);
        PlayerStardewDataAPI.addExperience(player, SkillType.FISHING, HARVEST_BASE_EXP + bonusExperience);
    }

    public static void clear(ServerLevel level, FishPondRecord pond) {
        ItemStack fish = fishStack(pond.fishTypeId());
        for (int n=0; n<pond.currentPopulation() && !fish.isEmpty(); n++) {
            var dropped = new ItemEntity(level, pond.managerPos().getX()+.5,
                pond.managerPos().getY()+1, pond.managerPos().getZ()+.5, fish.copy());
            dropped.setDefaultPickUpDelay();
            level.addFreshEntity(dropped);
        }
        pond.clearPondContents();
        FishPondWorldData.get(level).markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        FishPondColorSyncService.broadcastSnapshot(level);
    }

}
