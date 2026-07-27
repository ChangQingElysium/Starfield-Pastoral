package com.stardew.craft.blockentity;

import com.stardew.craft.api.v1.agriculture.StardewAnimalIncubation;
import com.stardew.craft.item.artisan.ArtisanRecipeDataManager;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.AnimalBuildingRecord;
import com.stardew.craft.animal.service.AnimalAcquireService;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.ProfessionType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;
import java.util.UUID;

public class IncubatorBlockEntity extends TimedProductionBlockEntity {
    private static final String TAG_INPUT = "input";
    private static final String TAG_READY_AT = "readyAtAbsMinute";
    private static final String TAG_READY = "ready";
    private static final String[] CHICKEN_NAMES = {"Pip", "Bean", "Dot", "Sunny", "Maple", "Mochi", "Biscuit", "Clover"};
    private static final String[] DUCK_NAMES = {"Puddle", "Bubbles", "Waddles", "Pebble", "Marsh", "Ripple", "Mallow", "Drizzle"};
    private static final String[] VOID_CHICKEN_NAMES = {"Nyx", "Soot", "Hex", "Ash", "Murk", "Umbra", "Cinder", "Gloom"};
    private static final String[] GOLDEN_CHICKEN_NAMES = {"Goldie", "Topaz", "Glint", "Honey", "Auric", "Spark", "Amber", "Sunbeam"};
    private static final String[] DINOSAUR_NAMES = {"Fern", "Spike", "Pebble", "Moss", "Sprout", "Juniper", "Rexie", "Bramble"};

    public record RemainingTime(int days, int hours, int minutes) {
    }

    public enum ClaimResult {
        SUCCESS,
        NOT_READY,
        NOT_IN_BUILDING,
        NOT_OWNER,
        INVALID_BUILDING,
        BUILDING_FULL,
        INVALID_EGG,
        NAME_DUPLICATE,
        FAILED
    }

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INCUBATOR.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, IncubatorBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!be.isInsideActiveAnimalBuilding(level, pos)) {
            be.updateWorkingState(level, pos, state);
            return;
        }

        boolean newReady = be.refreshReady();
        if (newReady != be.ready) {
            be.ready = newReady;
            be.setChanged();
            be.syncToClient();
        }

        if (be.ready) {
            be.tryAutoHatch(serverLevel);
        }

        be.updateWorkingState(level, pos, state);
    }

    private boolean tryAutoHatch(ServerLevel serverLevel) {
        if (!ready || input.isEmpty()) {
            return false;
        }

        String animalTypeId = resolveAnimalTypeId(input);
        if (animalTypeId == null) {
            return false;
        }
        AnimalBuildingRecord building =
                getContainingAnimalBuilding(serverLevel, animalTypeId);
        if (building == null || !building.hasCapacity()) {
            return false;
        }

        try {
            String generatedName = generateIncubationName(serverLevel, animalTypeId);
            AnimalAcquireService.incubation(serverLevel, animalTypeId, generatedName, building.buildingId());
            clearIncubationState();
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String generateIncubationName(ServerLevel serverLevel, String animalTypeId) {
        AnimalWorldData data = AnimalWorldData.get(serverLevel);
        RandomSource random = serverLevel.random;
        String[] pool = switch (animalTypeId) {
            case "duck" -> DUCK_NAMES;
            case "void_chicken" -> VOID_CHICKEN_NAMES;
            case "golden_chicken" -> GOLDEN_CHICKEN_NAMES;
            case "dinosaur" -> DINOSAUR_NAMES;
            default -> CHICKEN_NAMES;
        };

        for (int attempt = 0; attempt < 24; attempt++) {
            String candidate = pool[random.nextInt(pool.length)];
            if (!data.hasAnyAnimalWithName(candidate)) {
                return candidate;
            }
        }

        String base = switch (animalTypeId) {
            case "duck" -> "Duck";
            case "void_chicken" -> "Void";
            case "golden_chicken" -> "Goldie";
            case "dinosaur" -> "Dino";
            default -> "Chick";
        };
        for (int attempt = 0; attempt < 64; attempt++) {
            String candidate = base + " " + (100 + random.nextInt(900));
            if (!data.hasAnyAnimalWithName(candidate)) {
                return candidate;
            }
        }
        return base + " " + Math.abs(random.nextInt());
    }

    private boolean isInsideActiveAnimalBuilding(Level level, BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return true;
        }

        String dim = serverLevel.dimension().location().toString();
        for (AnimalBuildingRecord record : AnimalWorldData.get(serverLevel).getBuildings()) {
            if (!dim.equals(record.dimensionId())) {
                continue;
            }
            String family = record.buildingType().family();
            if (!("coop".equalsIgnoreCase(family) || "barn".equalsIgnoreCase(family))) {
                continue;
            }
            if (record.isInBounds(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean readyCheckRequiresProduct() {
        return false;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isWorking() {
        return !input.isEmpty() && !ready && readyAtAbsMinute > 0;
    }

    public boolean hasInput() {
        return !input.isEmpty();
    }

    public ItemStack getInput() {
        return input;
    }

    @Nullable
    public String getReadyAnimalTypeId() {
        if (!ready || input.isEmpty()) {
            return null;
        }
        return resolveAnimalTypeId(input);
    }

    @Nullable
    public static String resolveAnimalTypeId(ItemStack eggStack) {
        if (eggStack.isEmpty()) {
            return null;
        }
        Item egg = eggStack.getItem();
        if (egg == ModItems.EGG_WHITE.get() || egg == ModItems.EGG_BROWN.get()
            || egg == ModItems.LARGE_EGG_WHITE.get() || egg == ModItems.LARGE_EGG_BROWN.get()) {
            return "white_chicken";
        }
        if (egg == ModItems.DUCK_EGG.get()) {
            return "duck";
        }
        if (egg == ModItems.VOID_EGG.get()) {
            return "void_chicken";
        }
        if (egg == ModItems.GOLDEN_EGG.get()) {
            return "golden_chicken";
        }
        if (egg == ModItems.DINOSAUR_EGG.get()) {
            return "dinosaur";
        }
        return StardewAnimalIncubation.resolve(eggStack);
    }

    public RemainingTime getRemainingTime() {
        long remaining = getRemainingAbsMinutes();
        int days = (int) (remaining / EFFECTIVE_MINUTES_PER_DAY);
        int minutesRemainder = (int) (remaining % EFFECTIVE_MINUTES_PER_DAY);
        int hours = minutesRemainder / com.stardew.craft.time.StardewTimeManager.MINUTES_PER_HOUR;
        int minutes = minutesRemainder % com.stardew.craft.time.StardewTimeManager.MINUTES_PER_HOUR;
        return new RemainingTime(days, hours, minutes);
    }

    @Nullable
    public AnimalBuildingRecord getContainingAnimalBuilding(ServerLevel serverLevel) {
        String animalTypeId = input.isEmpty() ? null : resolveAnimalTypeId(input);
        return getContainingAnimalBuilding(serverLevel, animalTypeId);
    }

    @Nullable
    private AnimalBuildingRecord getContainingAnimalBuilding(
            ServerLevel serverLevel,
            @Nullable String animalTypeId
    ) {
        String requiredFamily = animalTypeId == null
                ? null
                : com.stardew.craft.animal.model.AnimalTypeCatalog
                        .resolve(animalTypeId).family();
        return resolveContainingBuilding(serverLevel, worldPosition, requiredFamily);
    }

    public ClaimResult claimReadyAnimal(ServerPlayer player, String customName) {
        if (!ready || input.isEmpty()) {
            return ClaimResult.NOT_READY;
        }
        Level currentLevel = level;
        if (!(currentLevel instanceof ServerLevel serverLevel)) {
            return ClaimResult.FAILED;
        }

        String animalTypeId = resolveAnimalTypeId(input);
        if (animalTypeId == null) {
            return ClaimResult.INVALID_EGG;
        }

        AnimalBuildingRecord building =
                getContainingAnimalBuilding(serverLevel, animalTypeId);
        if (building == null) {
            return ClaimResult.NOT_IN_BUILDING;
        }
        if (!com.stardew.craft.farm.FarmInstanceRegistry.get()
                .canOperateBuilding(player.getUUID(), building.ownerPlayerUuid())) {
            return ClaimResult.NOT_OWNER;
        }
        if (building.memberAnimalIds().size() >= building.capacity()) {
            return ClaimResult.BUILDING_FULL;
        }

        String finalName =
                customName == null ? "" : customName.trim();
        if (finalName.isBlank()) {
            finalName = animalTypeId;
        }

        AnimalWorldData data = AnimalWorldData.get(serverLevel);
        if (data.hasAnyAnimalWithName(finalName)) {
            return ClaimResult.NAME_DUPLICATE;
        }

        try {
            AnimalAcquireService.incubation(serverLevel, animalTypeId, finalName, building.buildingId());
            clearIncubationState();
            return ClaimResult.SUCCESS;
        } catch (RuntimeException ex) {
            return ClaimResult.FAILED;
        }
    }

    public boolean tryInsert(ItemStack stack, Player player) {
        return tryInsertWithResult(stack, player).inserted();
    }

    @SuppressWarnings("null")
    public InsertResult tryInsertWithResult(ItemStack stack, Player player) {
        if (stack.isEmpty()) {
            return InsertResult.fail();
        }
        if (!input.isEmpty() || readyAtAbsMinute >= 0) {
            return InsertResult.fail();
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("incubator", stack);
        if (recipeOpt.isEmpty()) {
            return InsertResult.fail();
        }
        ArtisanRecipeDataManager.Recipe recipe = recipeOpt.get();
        startIncubation(stack, recipe.minutes(), player);
        return InsertResult.success();
    }

    private void startIncubation(ItemStack inputStack, int minutesUntilReady, Player player) {
        input = inputStack.copy();
        input.setCount(1);
        product = ItemStack.EMPTY;
        int adjustedMinutes = applyProfessionTimeModifiers(minutesUntilReady);
        readyAtAbsMinute = getCurrentAbsMinute() + adjustedMinutes;
        ready = false;
        if (player == null || !player.isCreative()) {
            inputStack.shrink(1);
        }
        setChanged();
        syncToClient();
    }

    private int applyProfessionTimeModifiers(int minutesUntilReady) {
        Level currentLevel = this.level;
        if (minutesUntilReady <= 0 || currentLevel == null || currentLevel.isClientSide) {
            return minutesUntilReady;
        }
        if (!(currentLevel instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return minutesUntilReady;
        }

        AnimalBuildingRecord building =
                resolveContainingBuilding(serverLevel, worldPosition, null);
        if (building == null) {
            return minutesUntilReady;
        }
        if (!"coop".equalsIgnoreCase(building.buildingType().family())) {
            return minutesUntilReady;
        }
        if (!ownerHasProfession(building, ProfessionType.COOPMASTER)) {
            return minutesUntilReady;
        }
        return Math.max(1, minutesUntilReady / 2);
    }

    private AnimalBuildingRecord resolveContainingBuilding(
            net.minecraft.server.level.ServerLevel serverLevel,
            BlockPos pos
    ) {
        return resolveContainingBuilding(serverLevel, pos, null);
    }

    private AnimalBuildingRecord resolveContainingBuilding(
            net.minecraft.server.level.ServerLevel serverLevel,
            BlockPos pos,
            @Nullable String requiredFamily
    ) {
        String dim = serverLevel.dimension().location().toString();
        for (AnimalBuildingRecord record : AnimalWorldData.get(serverLevel).getBuildings()) {
            if (!dim.equals(record.dimensionId())) {
                continue;
            }
            if (requiredFamily != null
                    && !requiredFamily.equalsIgnoreCase(record.buildingType().family())) {
                continue;
            }
            if (record.isInBounds(pos)) {
                return record;
            }
        }
        return null;
    }

    private boolean ownerHasProfession(AnimalBuildingRecord building, ProfessionType profession) {
        if (building == null || profession == null) {
            return false;
        }
        UUID ownerUuid;
        try {
            ownerUuid = UUID.fromString(building.ownerPlayerUuid());
        } catch (IllegalArgumentException ex) {
            return false;
        }
        return PlayerDataManager.getPlayerData(ownerUuid).hasProfession(profession);
    }

    public void completeIncubation() {
        if (!ready) {
            return;
        }
        clearIncubationState();
    }

    private void clearIncubationState() {
        input = ItemStack.EMPTY;
        product = ItemStack.EMPTY;
        readyAtAbsMinute = -1;
        ready = false;
        setChanged();
        syncToClient();
    }

    @Override
    public ItemStack getAutomationInput() {
        return input;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !input.isEmpty() || readyAtAbsMinute >= 0) {
            return stack;
        }
        var recipeOpt = ArtisanRecipeDataManager.getRecipe("incubator", stack);
        if (recipeOpt.isEmpty()) {
            return stack;
        }
        if (simulate) {
            return AutomationStackHelper.remainderAfterInsert(stack, 1);
        }
        ItemStack inputCopy = stack.copy();
        startIncubation(inputCopy, recipeOpt.get().minutes(), null);
        return AutomationStackHelper.remainderAfterInsert(stack, 1);
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        return ItemStack.EMPTY;
    }

    @SuppressWarnings("null")
    private void updateWorkingState(Level level, BlockPos pos, BlockState state) {
        BooleanProperty workingProp = com.stardew.craft.block.utility.IncubatorBlock.WORKING;
        boolean workingNow = isWorking();
        if (state.hasProperty(workingProp) && state.getValue(workingProp) != workingNow) {
            level.setBlock(pos, state.setValue(workingProp, workingNow), 3);
            BlockPos extensionPos = com.stardew.craft.block.utility.IncubatorBlock.getExtensionPos(pos, state);
            BlockState extensionState = level.getBlockState(extensionPos);
            if (extensionState.is(state.getBlock()) && extensionState.hasProperty(workingProp)) {
                level.setBlock(extensionPos, extensionState.setValue(workingProp, workingNow), 3);
            }
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @SuppressWarnings("null")
    @Override
    public CompoundTag getUpdateTag(@SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @SuppressWarnings("null")
    @Override
    protected void saveAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put(TAG_INPUT, input.save(registries));
        }
        tag.putLong(TAG_READY_AT, readyAtAbsMinute);
        tag.putBoolean(TAG_READY, ready);
    }

    @SuppressWarnings("null")
    @Override
    protected void loadAdditional(@SuppressWarnings("null") CompoundTag tag, @SuppressWarnings("null") net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains(TAG_INPUT) ? ItemStack.parse(registries, tag.getCompound(TAG_INPUT)).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        product = ItemStack.EMPTY;
        readyAtAbsMinute = tag.getLong(TAG_READY_AT);
        ready = tag.getBoolean(TAG_READY);
    }
}
