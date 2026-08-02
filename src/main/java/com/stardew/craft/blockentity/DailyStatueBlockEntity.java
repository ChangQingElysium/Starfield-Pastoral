package com.stardew.craft.blockentity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stardew.craft.block.utility.DailyStatueBlock;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Persistent output state for the two daily production statues. */
public final class DailyStatueBlockEntity extends BlockEntity
        implements UtilityAutomationAccess, AdvanceableUtility, UtilityMachineInfo {
    private static final String BIRTHDAYS =
            "data/stardewcraft/npc/events/npc_birthdays.json";
    private static final String GIFT_TASTES =
            "data/stardewcraft/npc/vanilla/data/NPCGiftTastes.json";
    private static final String OBJECTS =
            "data/stardewcraft/npc/vanilla/data/Objects.json";
    private static final Map<String, Integer> SEASONS = Map.of(
            "spring", 0, "summer", 1, "fall", 2, "winter", 3);

    private ItemStack product = ItemStack.EMPTY;
    private long lastDayIndex = -1L;
    private final UtilityItemHandler automationItemHandler = new UtilityItemHandler(this);

    public DailyStatueBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DAILY_STATUE.get(), pos, state);
    }

    public enum Kind {
        PERFECTION,
        ENDLESS_FORTUNE
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  DailyStatueBlockEntity statue) {
        long today = dayIndex();
        if (statue.lastDayIndex < 0L) {
            statue.lastDayIndex = today;
            statue.setChanged();
            return;
        }
        if (today == statue.lastDayIndex) {
            return;
        }
        statue.lastDayIndex = today;
        Kind kind = state.getBlock() instanceof DailyStatueBlock dailyStatue
                ? dailyStatue.kind()
                : Kind.PERFECTION;
        // Data/Machines: Endless Fortune clears yesterday's uncollected
        // output overnight; Perfection keeps its existing ore until collected.
        if (statue.product.isEmpty() || kind == Kind.ENDLESS_FORTUNE) {
            statue.product = statue.createDailyOutput(today);
            statue.syncToClient();
        } else {
            statue.setChanged();
        }
    }

    public boolean isReady() {
        return !product.isEmpty();
    }

    public ItemStack harvestOne() {
        if (product.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = product.copy();
        product = ItemStack.EMPTY;
        syncToClient();
        return result;
    }

    public ItemStack getProduct() {
        return product;
    }

    @Override
    public void advanceDays(int days) {
        if (days <= 0 || level == null || level.isClientSide) {
            return;
        }
        long today = dayIndex();
        Kind kind = getBlockState().getBlock() instanceof DailyStatueBlock dailyStatue
                ? dailyStatue.kind()
                : Kind.PERFECTION;
        lastDayIndex = today;
        if (product.isEmpty() || kind == Kind.ENDLESS_FORTUNE) {
            product = createDailyOutput(today + days);
        }
        syncToClient();
    }

    @Override
    public ItemStack getAutomationInput() {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack getAutomationOutput() {
        return isReady() ? product : ItemStack.EMPTY;
    }

    @Override
    public ItemStack insertAutomation(ItemStack stack, boolean simulate) {
        return stack;
    }

    @Override
    public ItemStack extractAutomation(int amount, boolean simulate) {
        if (amount <= 0 || product.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = AutomationStackHelper.extractUpTo(product, amount);
        if (simulate) {
            return extracted;
        }
        if (extracted.getCount() >= product.getCount()) {
            return harvestOne();
        }
        product.shrink(extracted.getCount());
        syncToClient();
        return extracted;
    }

    @Override
    public IItemHandler getAutomationItemHandler() {
        return automationItemHandler;
    }

    @Override
    public String getUtilityTooltipKey() {
        return "daily_statue";
    }

    @Override
    public boolean isReadyForDisplay() {
        return isReady();
    }

    @Override
    public boolean isWorkingForDisplay() {
        return false;
    }

    @Override
    public boolean shouldShowInputInDisplay() {
        return false;
    }

    @Override
    public ItemStack getDisplayOutput() {
        return product;
    }

    @Override
    public String getIdleTooltipKey() {
        return "stardewcraft.tooltip.daily_statue.waiting";
    }

    private ItemStack createDailyOutput(long today) {
        Kind kind = getBlockState().getBlock() instanceof DailyStatueBlock statue
                ? statue.kind()
                : Kind.PERFECTION;
        Random random = new Random(today * 0x9E3779B97F4A7C15L ^ worldPosition.asLong());
        if (kind == Kind.PERFECTION) {
            return new ItemStack(ModItems.IRIDIUM_ORE.get(), 2 + random.nextInt(7));
        }
        ItemStack birthdayGift = birthdayGift();
        if (!birthdayGift.isEmpty()) {
            return birthdayGift;
        }
        return switch (random.nextInt(4)) {
            case 0 -> new ItemStack(ModItems.DIAMOND.get());
            case 1 -> new ItemStack(ModItems.IRIDIUM_BAR.get());
            case 2 -> new ItemStack(ModItems.OMNI_GEODE.get());
            default -> new ItemStack(ModItems.GOLD_BAR.get());
        };
    }

    private static ItemStack birthdayGift() {
        StardewTimeManager time = StardewTimeManager.get();
        JsonObject birthdays = read(BIRTHDAYS);
        JsonObject tastes = read(GIFT_TASTES);
        JsonObject objects = read(OBJECTS);
        if (birthdays == null || tastes == null || objects == null
                || !birthdays.has("birthdays")) {
            return ItemStack.EMPTY;
        }
        String birthdayNpc = null;
        for (Map.Entry<String, JsonElement> entry
                : birthdays.getAsJsonObject("birthdays").entrySet()) {
            JsonObject birthday = entry.getValue().getAsJsonObject();
            Integer season = SEASONS.get(birthday.get("season").getAsString()
                    .toLowerCase(Locale.ROOT));
            if (season != null && season == time.getCurrentSeason()
                    && birthday.get("day").getAsInt() == time.getCurrentDay()) {
                birthdayNpc = entry.getKey();
                break;
            }
        }
        if (birthdayNpc == null) {
            return ItemStack.EMPTY;
        }
        String currentBirthdayNpc = birthdayNpc;
        String tasteKey = tastes.keySet().stream()
                .filter(key -> key.equalsIgnoreCase(currentBirthdayNpc))
                .findFirst().orElse(null);
        if (tasteKey == null) {
            return ItemStack.EMPTY;
        }
        String[] fields = tastes.get(tasteKey).getAsString().split("/", -1);
        if (fields.length < 2 || fields[1].isBlank()) {
            return ItemStack.EMPTY;
        }
        for (String objectId : fields[1].trim().split("\\s+")) {
            JsonObject object = objects.has(objectId) && objects.get(objectId).isJsonObject()
                    ? objects.getAsJsonObject(objectId)
                    : null;
            if (object == null || !object.has("Name")) {
                continue;
            }
            String path = object.get("Name").getAsString()
                    .toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]+", "_")
                    .replaceAll("^_+|_+$", "");
            Item item = BuiltInRegistries.ITEM.get(
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", path));
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    private static JsonObject read(String path) {
        try (InputStream stream = DailyStatueBlockEntity.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (stream == null) {
                return null;
            }
            try (InputStreamReader reader =
                         new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                return element.isJsonObject() ? element.getAsJsonObject() : null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long dayIndex() {
        StardewTimeManager time = StardewTimeManager.get();
        return (long) (time.getCurrentYear() - 1) * 112L
                + (long) time.getCurrentSeason() * 28L
                + time.getCurrentDay();
    }

    private void syncToClient() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!product.isEmpty()) {
            tag.put("Product", product.save(registries));
        }
        tag.putLong("LastDay", lastDayIndex);
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        product = tag.contains("Product")
                ? ItemStack.parse(registries, tag.getCompound("Product"))
                .orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        lastDayIndex = tag.contains("LastDay") ? tag.getLong("LastDay") : -1L;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }
}
