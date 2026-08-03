package com.stardew.craft.menu;

import com.stardew.craft.block.utility.FishPondManagerBlock;
import com.stardew.craft.blockentity.FishPondBucketBlockEntity;
import com.stardew.craft.fishpond.data.FishPondWorldData;
import com.stardew.craft.fishpond.model.FishPondRecord;
import com.stardew.craft.fishpond.service.FishPondColorSyncService;
import com.stardew.craft.fishpond.service.FishPondManagerValidationService;
import com.stardew.craft.fishpond.service.FishPondQualifiedItemService;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@SuppressWarnings("null")
public class FishPondManagerMenu extends AbstractContainerMenu {
    public static final int ACTION_BUILD_OR_REFRESH = 0;
    public static final int ACTION_DEMOLISH = 1;
    public static final int ACTION_CLEAR_POND = 2;

    private final Player player;
    private final BlockPos managerPos;
    private static final String STATUS_KEY_PREFIX = "gui.stardew_craft.fish_pond_manager.sdv.";

    private int formed;
    private int ownerMismatch;
    private int fishItemRawId;
    private int currentPopulation;
    private int maxPopulation;
    private int waterCellCount;
    private int netCount;
    private int hasBucket;
    private int hasColorOverride;
    private int neededItemRawId;
    private int neededItemCount;
    private int requestCompleted;
    private int goldenAnimalCracker;
    private int canBuildPond;
    private int reqWaterCells;
    private int reqWaterWidth;
    private int reqWaterLength;
    private int reqNetCount;
    private int reqBucketCount;
    private int curWaterWidth;
    private int curWaterLength;
    private int currentBucketCount;

    public FishPondManagerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, BlockPos.ZERO);
    }

    public FishPondManagerMenu(int containerId, Inventory playerInventory, BlockPos managerPos) {
        super(ModMenuTypes.FISH_POND_MANAGER.get(), containerId);
        this.player = playerInventory.player;
        this.managerPos = managerPos.immutable();

        this.addDataSlot(sync(() -> formed, value -> formed = value));
        this.addDataSlot(sync(() -> ownerMismatch, value -> ownerMismatch = value));
        this.addDataSlot(sync(() -> fishItemRawId, value -> fishItemRawId = value));
        this.addDataSlot(sync(() -> currentPopulation, value -> currentPopulation = value));
        this.addDataSlot(sync(() -> maxPopulation, value -> maxPopulation = value));
        this.addDataSlot(sync(() -> waterCellCount, value -> waterCellCount = value));
        this.addDataSlot(sync(() -> netCount, value -> netCount = value));
        this.addDataSlot(sync(() -> hasBucket, value -> hasBucket = value));
        this.addDataSlot(sync(() -> hasColorOverride, value -> hasColorOverride = value));
        this.addDataSlot(sync(() -> neededItemRawId, value -> neededItemRawId = value));
        this.addDataSlot(sync(() -> neededItemCount, value -> neededItemCount = value));
        this.addDataSlot(sync(() -> requestCompleted, value -> requestCompleted = value));
        this.addDataSlot(sync(() -> goldenAnimalCracker, value -> goldenAnimalCracker = value));
        this.addDataSlot(sync(() -> canBuildPond, value -> canBuildPond = value));
        this.addDataSlot(sync(() -> reqWaterCells, value -> reqWaterCells = value));
        this.addDataSlot(sync(() -> reqWaterWidth, value -> reqWaterWidth = value));
        this.addDataSlot(sync(() -> reqWaterLength, value -> reqWaterLength = value));
        this.addDataSlot(sync(() -> reqNetCount, value -> reqNetCount = value));
        this.addDataSlot(sync(() -> reqBucketCount, value -> reqBucketCount = value));
        this.addDataSlot(sync(() -> curWaterWidth, value -> curWaterWidth = value));
        this.addDataSlot(sync(() -> curWaterLength, value -> curWaterLength = value));
        this.addDataSlot(sync(() -> currentBucketCount, value -> currentBucketCount = value));

        refreshState();
    }

    private static DataSlot sync(java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        return new DataSlot() {
            @Override
            public int get() {
                return getter.getAsInt();
            }

            @Override
            public void set(int value) {
                setter.accept(value);
            }
        };
    }

    private void refreshState() {
        formed = 0;
        ownerMismatch = 0;
        fishItemRawId = -1;
        currentPopulation = 0;
        maxPopulation = 0;
        waterCellCount = 0;
        netCount = 0;
        hasBucket = 0;
        hasColorOverride = 0;
        neededItemRawId = -1;
        neededItemCount = 0;
        requestCompleted = 0;
        goldenAnimalCracker = 0;
        canBuildPond = 0;
        reqWaterCells = 27;
        reqWaterWidth = 3;
        reqWaterLength = 3;
        reqNetCount = 1;
        reqBucketCount = 1;
        curWaterWidth = 0;
        curWaterLength = 0;
        currentBucketCount = 0;

        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) {
            return;
        }

        FishPondWorldData worldData = FishPondWorldData.get(level);
        worldData.reconcileFarmOwnership(level);
        Optional<FishPondRecord> anyOwner = worldData.findPondByManagerAnyOwner(level.dimension().location().toString(), managerPos);
        if (anyOwner.isEmpty()) {
            FishPondManagerValidationService.ValidationResult validation = FishPondManagerValidationService.validate(level, managerPos);
            FishPondManagerValidationService.ScanResult scan = validation.scan();
            waterCellCount = scan.waterCells().size();
            netCount = scan.netPositions().size();
            currentBucketCount = scan.bucketPositions().size();
            hasBucket = currentBucketCount == 1 ? 1 : 0;
            curWaterWidth = scan.width();
            curWaterLength = scan.length();
            canBuildPond = validation.ok() ? 1 : 0;
            return;
        }

        FishPondRecord pond = anyOwner.get();
        formed = 1;
        ownerMismatch = com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(serverPlayer.getUUID(), pond.ownerPlayerUuid()) ? 0 : 1;
        currentPopulation = pond.currentPopulation();
        maxPopulation = pond.maxPopulation();
        waterCellCount = pond.waterCells().size();
        netCount = pond.netPositions().size();
        hasBucket = 1;
        currentBucketCount = 1;
        hasColorOverride = pond.waterColor() >= 0 ? 1 : 0;
        neededItemCount = pond.neededItemCount();
        requestCompleted = pond.hasCompletedRequest() ? 1 : 0;
        goldenAnimalCracker = pond.goldenAnimalCracker() ? 1 : 0;

        if (!pond.fishTypeId().isBlank()) {
            Item fishItem = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(pond.fishTypeId()));
            if (fishItem != null) {
                fishItemRawId = BuiltInRegistries.ITEM.getId(fishItem);
            }
        }

        if (!pond.neededItemId().isBlank()) {
            FishPondQualifiedItemService.resolve(pond.neededItemId())
                .map(FishPondQualifiedItemService.ResolvedItem::item)
                .ifPresent(item -> neededItemRawId = BuiltInRegistries.ITEM.getId(item));
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level)) {
            return false;
        }

        boolean success;
        if (id == ACTION_BUILD_OR_REFRESH) {
            success = FishPondManagerBlock.tryCreateOrRefreshPond(level, managerPos, serverPlayer);
        } else if (id == ACTION_DEMOLISH) {
            success = FishPondManagerBlock.tryDemolishPond(level, managerPos, serverPlayer);
        } else if (id == ACTION_CLEAR_POND) {
            success = clearOwnedPond(level, serverPlayer);
        } else {
            return false;
        }

        refreshState();
        return success;
    }

    private boolean clearOwnedPond(ServerLevel level, ServerPlayer player) {
        FishPondWorldData worldData = FishPondWorldData.get(level);
        worldData.reconcileFarmOwnership(level);
        java.util.UUID farmOwner = com.stardew.craft.farm.FarmResourceOwnership
                .resolveManageableOwner(level, managerPos, player);
        if (farmOwner == null) {
            return false;
        }
        Optional<FishPondRecord> pondOpt = worldData.findPondByManager(
                level.dimension().location().toString(), farmOwner, managerPos);
        if (pondOpt.isEmpty()) {
            return false;
        }
        FishPondRecord pond = pondOpt.get();
        pond.clearPondContents();
        worldData.markChanged();
        FishPondBucketBlockEntity.syncVisualState(level, pond.bucketPos());
        FishPondColorSyncService.broadcastSnapshot(level);
        return true;
    }

    @Override
    public void broadcastChanges() {
        refreshState();
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (managerPos.equals(BlockPos.ZERO)) {
            return true;
        }
        if (!(player.level().getBlockState(managerPos).getBlock() instanceof FishPondManagerBlock)) {
            return false;
        }
        return player.distanceToSqr(managerPos.getX() + 0.5D, managerPos.getY() + 0.5D, managerPos.getZ() + 0.5D) <= 64.0D;
    }

    public boolean isFormed() {
        return formed == 1;
    }

    public boolean isOwnerMismatch() {
        return ownerMismatch == 1;
    }

    public ItemStack getFishPreviewStack() {
        if (fishItemRawId < 0) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.byId(fishItemRawId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public int getCurrentPopulation() {
        return currentPopulation;
    }

    public int getMaxPopulation() {
        return maxPopulation;
    }

    public int getWaterCellCount() {
        return waterCellCount;
    }

    public int getNetCount() {
        return netCount;
    }

    public boolean hasBucket() {
        return hasBucket == 1;
    }

    public boolean hasColorOverride() {
        return hasColorOverride == 1;
    }

    public ItemStack getNeededItemPreviewStack() {
        if (neededItemRawId < 0) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.byId(neededItemRawId);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    public int getNeededItemCount() {
        return neededItemCount;
    }

    public boolean hasCompletedRequest() {
        return requestCompleted == 1;
    }

    public boolean hasPendingRequest() {
        return neededItemCount > 0 || requestCompleted == 1;
    }

    public boolean hasUnresolvedRequest() {
        return neededItemCount > 0 && requestCompleted == 0;
    }

    public boolean hasGoldenAnimalCracker() {
        return goldenAnimalCracker == 1;
    }

    public boolean canBuildPond() {
        return canBuildPond == 1;
    }

    public boolean canBuild() {
        return canBuildPond == 1;
    }

    public int getRequiredWaterCells() {
        return reqWaterCells;
    }

    public int getRequiredWaterWidth() {
        return reqWaterWidth;
    }

    public int getRequiredWaterLength() {
        return reqWaterLength;
    }

    public int getRequiredNetCount() {
        return reqNetCount;
    }

    public int getRequiredBucketCount() {
        return reqBucketCount;
    }

    public int getCurrentWaterWidth() {
        return curWaterWidth;
    }

    public int getCurrentWaterLength() {
        return curWaterLength;
    }

    public int getCurrentBucketCount() {
        return currentBucketCount;
    }

    public boolean canManagePond() {
        return isFormed() && !isOwnerMismatch();
    }

    public Component getStatusText() {
        ItemStack fishPreview = getFishPreviewStack();
        RandomSource random = RandomSource.create(buildStatusSeed());
        if (getCurrentPopulation() <= 0) {
            return Component.translatable(STATUS_KEY_PREFIX + "status_no_fish");
        }
        if (hasCompletedRequest()) {
            return buildCompletedRequestText(fishPreview, random);
        }
        if (getNeededItemCount() > 0) {
            return buildPendingRequestText(fishPreview, random);
        }
        ResourceLocation fishId = fishPreview.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(fishPreview.getItem());
        if (fishId != null && ("coral".equals(fishId.getPath()) || "sea_urchin".equals(fishId.getPath()))) {
            return Component.translatable(STATUS_KEY_PREFIX + "status_ok_coral", fishPreview.getHoverName());
        }
        return Component.translatable(STATUS_KEY_PREFIX + "status_ok_" + random.nextInt(7));
    }

    private Component buildCompletedRequestText(ItemStack fishPreview, RandomSource random) {
        ItemStack needed = getNeededItemPreviewStack();
        Component itemName = needed.isEmpty()
            ? Component.translatable("gui.stardew_craft.fish_pond_manager.status_unknown_item")
            : needed.getHoverName();
        String talkSuffix = getFishTalkSuffix(fishPreview);
        if (!talkSuffix.isEmpty()) {
            return Component.translatable(STATUS_KEY_PREFIX + "status_request_complete" + talkSuffix + "_" + random.nextInt(3), itemName);
        }
        return Component.translatable(STATUS_KEY_PREFIX + "status_request_complete_" + random.nextInt(7), itemName);
    }

    private Component buildPendingRequestText(ItemStack fishPreview, RandomSource random) {
        ItemStack needed = getNeededItemPreviewStack();
        Component singularItemName = needed.isEmpty()
            ? Component.translatable("gui.stardew_craft.fish_pond_manager.status_unknown_item")
            : needed.getHoverName();
        Component pluralItemName = pluralizeForStatus(singularItemName, getNeededItemCount());
        Component itemCount = getNeededItemCount() <= 1
            ? Component.translatable(STATUS_KEY_PREFIX + "status_request_one_count")
            : Component.literal(Integer.toString(getNeededItemCount()));
        Set<String> contextTags = FishPondQualifiedItemService.getContextTags(fishPreview);
        if (contextTags.contains("fish_talk_rude")) {
            return Component.translatable(
                STATUS_KEY_PREFIX + "status_request_pending_rude_" + random.nextInt(3) + "_" + (useMaleRudeVariant() ? "male" : "female"),
                pluralItemName,
                itemCount,
                singularItemName
            );
        }
        String talkSuffix = getFishTalkSuffix(fishPreview);
        if (!talkSuffix.isEmpty()) {
            return Component.translatable(
                STATUS_KEY_PREFIX + "status_request_pending" + talkSuffix + "_" + random.nextInt(3),
                pluralItemName,
                itemCount,
                singularItemName
            );
        }
        return Component.translatable(
            STATUS_KEY_PREFIX + "status_request_pending_" + random.nextInt(7),
            pluralItemName,
            itemCount,
            singularItemName
        );
    }

    private String getFishTalkSuffix(ItemStack fishPreview) {
        Set<String> tags = FishPondQualifiedItemService.getContextTags(fishPreview);
        if (tags.contains("fish_talk_rude")) {
            return "_rude";
        }
        if (tags.contains("fish_talk_stiff")) {
            return "_stiff";
        }
        if (tags.contains("fish_talk_demanding")) {
            return "_demanding";
        }
        for (String tag : tags) {
            if (!tag.startsWith("fish_talk_")) {
                continue;
            }
            return "_" + tag.substring("fish_talk_".length()).toLowerCase(Locale.ROOT);
        }
        if (tags.contains("fish_carnivorous")) {
            return "_carnivore";
        }
        return "";
    }

    private Component pluralizeForStatus(Component singularName, int count) {
        if (count <= 1) {
            return singularName;
        }
        String raw = singularName.getString();
        if (raw.isBlank() || !raw.chars().allMatch(ch -> Character.isLetter(ch) || Character.isWhitespace(ch) || ch == '-' || ch == '\'')) {
            return singularName;
        }
        String plural = raw;
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.endsWith("ch") || lower.endsWith("sh") || lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")) {
            plural = raw + "es";
        } else if (lower.endsWith("y") && raw.length() > 1 && !isVowel(lower.charAt(lower.length() - 2))) {
            plural = raw.substring(0, raw.length() - 1) + "ies";
        } else {
            plural = raw + "s";
        }
        return Component.literal(plural);
    }

    private boolean useMaleRudeVariant() {
        try {
            Class<?> clientPlayerClass = Class.forName("net.minecraft.client.player.AbstractClientPlayer");
            if (clientPlayerClass.isInstance(player)) {
                Object modelName = clientPlayerClass.getMethod("getModelName").invoke(player);
                return !"slim".equals(String.valueOf(modelName));
            }
        } catch (ReflectiveOperationException ignored) {
        }
        return true;
    }

    private static boolean isVowel(char value) {
        return value == 'a' || value == 'e' || value == 'i' || value == 'o' || value == 'u';
    }

    private long buildStatusSeed() {
        long seed = managerPos.asLong();
        seed = 31L * seed + currentAbsoluteDay();
        seed = 31L * seed + fishItemRawId;
        seed = 31L * seed + neededItemRawId;
        seed = 31L * seed + currentPopulation;
        return seed;
    }

    private static int currentAbsoluteDay() {
        StardewTimeManager time = StardewTimeManager.get();
        if (time == null) {
            return 1;
        }
        return (time.getCurrentYear() - 1) * (28 * 4) + time.getCurrentSeason() * 28 + time.getCurrentDay();
    }
}
