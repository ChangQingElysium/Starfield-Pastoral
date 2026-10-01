package com.stardew.craft.item.trinket;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@SuppressWarnings("null")
public class StardewTrinketItem extends Item implements IStardewItem {
    private static final String TAG_SEED = "StardewTrinketSeed";
    private static final String TAG_GENERATED = "StardewTrinketGenerated";
    private static final String TAG_DISPLAY_KEY = "StardewTrinketDisplayKey";
    private static final String TAG_GENERAL_STAT = "GeneralStat";
    private static final String TAG_VARIANT = "Variant";
    private static final String TAG_PROJECTILE_DELAY = "ProjectileDelayMs";
    private static final String TAG_MIN_DAMAGE = "MinDamage";
    private static final String TAG_MAX_DAMAGE = "MaxDamage";
    private static final String TAG_FREEZE_TIME = "FreezeTimeMs";
    private static final String TAG_FAIRY_LEVEL = "FairyLevel";
    private static final String TAG_PARROT_CHANCE_KEY = "ParrotChanceKey";
    private static final String TAG_CUSTOM_DATA = "CustomData";
    private static final String TAG_ITEM_ID = "Id";
        private static final List<TrinketType> NATURAL_DROP_TYPES = Arrays.stream(TrinketType.values())
            .filter(TrinketType::dropsNaturally)
            .toList();

    private final TrinketType trinketType;

    public StardewTrinketItem(TrinketType trinketType, Properties properties) {
        super(properties.stacksTo(1));
        this.trinketType = trinketType;
    }

    public TrinketType getTrinketType() {
        return trinketType;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.trinket";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 1000;
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag tag = getCustomDataCopy(stack);
        String displayKey = tag.getString(TAG_DISPLAY_KEY);
        MutableComponent name;
        if (!displayKey.isBlank()) {
            name = Component.translatable(displayKey);
        } else {
            name = Component.translatable(trinketType.itemTranslationKey());
        }
        return name.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(primaryTextRgb())).withBold(true));
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (!level.isClientSide && entity instanceof ServerPlayer player) {
            long totalMoneyEarned = PlayerDataManager.getPlayerData(player).getTotalMoneyEarned();
            ensureGenerated(stack, totalMoneyEarned, player.getRandom());
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @javax.annotation.Nullable Level level, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, level, tooltipComponents, tooltipFlag);
        CompoundTag tag = getGeneratedTagForDisplay(stack);
        Component description = descriptionFromTag(tag);
        tooltipComponents.add(description.copy().withStyle(Style.EMPTY.withColor(TextColor.fromRgb(descriptionTextRgb()))));
    }

    public static boolean isTrinket(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof StardewTrinketItem;
    }

    public static TrinketType getType(ItemStack stack) {
        return stack.getItem() instanceof StardewTrinketItem item ? item.getTrinketType() : null;
    }

    public static boolean canSpawnFor(ServerPlayer player) {
        return player != null && PlayerDataManager.getPlayerData(player).getUnlockedTrinketSlots() != 0;
    }

    public static boolean canBeReforged(ItemStack stack) {
        return getType(stack) instanceof TrinketType type && type.canBeReforged();
    }

    public static ItemStack createRandomNaturalTrinket(RandomSource random, ServerPlayer player) {
        if (NATURAL_DROP_TYPES.isEmpty()) {
            return ItemStack.EMPTY;
        }
        TrinketType type = NATURAL_DROP_TYPES.get(random.nextInt(NATURAL_DROP_TYPES.size()));
        ItemStack stack = new ItemStack(itemFor(type));
        long totalMoneyEarned = player == null ? 0L : PlayerDataManager.getPlayerData(player).getTotalMoneyEarned();
        ensureGenerated(stack, totalMoneyEarned, random);
        return stack;
    }

    public static ItemStack rerollStats(ItemStack input, ServerPlayer player, RandomSource random) {
        if (!canBeReforged(input)) {
            return ItemStack.EMPTY;
        }
        ItemStack output = input.copyWithCount(1);
        CompoundTag tag = getCustomDataCopy(output);
        tag.putInt(TAG_SEED, random.nextInt(9_999_999));
        tag.putBoolean(TAG_GENERATED, false);
        setCustomData(output, tag);
        long totalMoneyEarned = player == null ? 0L : PlayerDataManager.getPlayerData(player).getTotalMoneyEarned();
        ensureGenerated(output, totalMoneyEarned, random);
        return output;
    }

    public static CompoundTag getGeneratedData(ItemStack stack, ServerPlayer player) {
        if (!(stack.getItem() instanceof StardewTrinketItem)) {
            return new CompoundTag();
        }
        long totalMoneyEarned = PlayerDataManager.getPlayerData(player).getTotalMoneyEarned();
        ensureGenerated(stack, totalMoneyEarned, player.getRandom());
        return getCustomDataCopy(stack);
    }

    public static int getGeneralStat(ItemStack stack, ServerPlayer player) {
        return getGeneratedData(stack, player).getInt(TAG_GENERAL_STAT);
    }

    public static int getProjectileDelayMs(ItemStack stack, ServerPlayer player) {
        return getGeneratedData(stack, player).getInt(TAG_PROJECTILE_DELAY);
    }

    public static int getMinDamage(ItemStack stack, ServerPlayer player) {
        return getGeneratedData(stack, player).getInt(TAG_MIN_DAMAGE);
    }

    public static int getMaxDamage(ItemStack stack, ServerPlayer player) {
        return getGeneratedData(stack, player).getInt(TAG_MAX_DAMAGE);
    }

    public static int getFreezeTimeMs(ItemStack stack, ServerPlayer player) {
        return getGeneratedData(stack, player).getInt(TAG_FREEZE_TIME);
    }

    public static int getFairyLevel(ItemStack stack, ServerPlayer player) {
        return getGeneratedData(stack, player).getInt(TAG_FAIRY_LEVEL);
    }

    public static void ensureGenerated(ItemStack stack, long totalMoneyEarned, RandomSource random) {
        if (!(stack.getItem() instanceof StardewTrinketItem item)) {
            return;
        }
        CompoundTag tag = getCustomDataCopy(stack);
        if (!tag.contains(TAG_SEED)) {
            tag.putInt(TAG_SEED, random.nextInt(9_999_999));
        }
        if (!tag.getBoolean(TAG_GENERATED)) {
            item.generateRandomStats(tag, totalMoneyEarned);
            tag.putBoolean(TAG_GENERATED, true);
        }
        setCustomData(stack, tag);
    }

    public static CompoundTag saveStackToTag(ItemStack stack) {
        CompoundTag out = new CompoundTag();
        if (stack.isEmpty() || !(stack.getItem() instanceof StardewTrinketItem)) {
            return out;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        out.putString(TAG_ITEM_ID, id.toString());
        CompoundTag customData = getCustomDataCopy(stack);
        if (!customData.isEmpty()) {
            out.put(TAG_CUSTOM_DATA, customData);
        }
        return out;
    }

    public static ItemStack loadStackFromTag(CompoundTag tag) {
        if (tag == null || !tag.contains(TAG_ITEM_ID)) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(TAG_ITEM_ID));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (!(item instanceof StardewTrinketItem)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item);
        if (tag.contains(TAG_CUSTOM_DATA, Tag.TAG_COMPOUND)) {
            PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(tag.getCompound(TAG_CUSTOM_DATA)));
        }
        return stack;
    }

    private Component descriptionFromTag(CompoundTag tag) {
        return switch (trinketType) {
            case MAGIC_QUIVER -> Component.translatable(trinketType.descriptionTranslationKey(),
                    formatSeconds(tag.getInt(TAG_PROJECTILE_DELAY), 2),
                    tag.getInt(TAG_MIN_DAMAGE),
                    tag.getInt(TAG_MAX_DAMAGE));
            case FAIRY_BOX -> Component.translatable(trinketType.descriptionTranslationKey(), tag.getInt(TAG_FAIRY_LEVEL));
            case PARROT_EGG -> Component.translatable(trinketType.descriptionTranslationKey(),
                    tag.getInt(TAG_GENERAL_STAT) + 1,
                    Component.translatable(tag.getString(TAG_PARROT_CHANCE_KEY)));
            case ICE_ROD -> Component.translatable(trinketType.descriptionTranslationKey(),
                    formatSeconds(tag.getInt(TAG_PROJECTILE_DELAY), 1),
                    formatSeconds(tag.getInt(TAG_FREEZE_TIME), 1));
            case IRIDIUM_SPUR -> Component.translatable(trinketType.descriptionTranslationKey(), tag.getInt(TAG_GENERAL_STAT));
            default -> Component.translatable(trinketType.descriptionTranslationKey());
        };
    }

    private void generateRandomStats(CompoundTag tag, long totalMoneyEarned) {
        int seed = tag.getInt(TAG_SEED);
        SdvRandom random = SdvRandom.create(seed);
        tag.remove(TAG_DISPLAY_KEY);
        switch (trinketType) {
            case FROG_EGG -> rollFrog(tag, random);
            case MAGIC_QUIVER -> rollMagicQuiver(tag, random);
            case FAIRY_BOX -> rollFairyBox(tag, random);
            case PARROT_EGG -> rollParrotEgg(tag, random, totalMoneyEarned);
            case ICE_ROD -> rollIceRod(tag, random);
            case IRIDIUM_SPUR -> tag.putInt(TAG_GENERAL_STAT, nextInt(random, 5, 11));
            default -> {
            }
        }
    }

    private CompoundTag getGeneratedTagForDisplay(ItemStack stack) {
        CompoundTag tag = getCustomDataCopy(stack);
        if (!tag.contains(TAG_SEED)) {
            tag.putInt(TAG_SEED, 0);
        }
        if (!tag.getBoolean(TAG_GENERATED)) {
            generateRandomStats(tag, 0L);
        }
        return tag;
    }

    private static void rollFrog(CompoundTag tag, SdvRandom random) {
        int variant;
        if (nextBool(random, 0.2)) {
            variant = 0;
        } else if (nextBool(random, 0.8)) {
            variant = random.next(3);
        } else if (nextBool(random, 0.8)) {
            variant = random.next(3) + 3;
        } else {
            variant = random.next(2) + 6;
        }
        tag.putInt(TAG_VARIANT, variant);
        tag.putString(TAG_DISPLAY_KEY, "stardewcraft.trinket.frog_variant_" + variant);
    }

    private static void rollMagicQuiver(CompoundTag tag, SdvRandom random) {
        int minDamage;
        int maxDamage;
        int projectileDelay;
        if (nextBool(random, 0.04)) {
            tag.putString(TAG_DISPLAY_KEY, "stardewcraft.trinket.perfect_magic_quiver");
            minDamage = 30;
            maxDamage = 35;
            projectileDelay = 900;
        } else if (nextBool(random, 0.1)) {
            if (nextBool(random, 0.5)) {
                tag.putString(TAG_DISPLAY_KEY, "stardewcraft.trinket.rapid_magic_quiver");
                minDamage = nextInt(random, 10, 15) - 2;
                maxDamage = minDamage + 5;
                projectileDelay = 600 + random.next(11) * 10;
            } else {
                tag.putString(TAG_DISPLAY_KEY, "stardewcraft.trinket.heavy_magic_quiver");
                minDamage = nextInt(random, 25, 41) - 2;
                maxDamage = minDamage + 5;
                projectileDelay = 1500 + random.next(6) * 100;
            }
        } else {
            minDamage = nextInt(random, 15, 31) - 2;
            maxDamage = minDamage + 5;
            projectileDelay = 1100 + random.next(11) * 100;
        }
        tag.putInt(TAG_MIN_DAMAGE, minDamage);
        tag.putInt(TAG_MAX_DAMAGE, maxDamage);
        tag.putInt(TAG_PROJECTILE_DELAY, projectileDelay);
    }

    private static void rollFairyBox(CompoundTag tag, SdvRandom random) {
        int level = 1;
        if (nextBool(random, 0.45)) {
            level = 2;
        } else if (nextBool(random, 0.25)) {
            level = 3;
        } else if (nextBool(random, 0.125)) {
            level = 4;
        } else if (nextBool(random, 0.0675)) {
            level = 5;
        }
        tag.putInt(TAG_FAIRY_LEVEL, level);
        tag.putInt(TAG_PROJECTILE_DELAY, 5000 - level * 300);
    }

    private static void rollParrotEgg(CompoundTag tag, SdvRandom random, long totalMoneyEarned) {
        int maxLevel = Math.min(4, (int) (1 + Math.max(0L, totalMoneyEarned) / 750_000L));
        int generalStat = random.next(Math.max(1, maxLevel));
        tag.putInt(TAG_GENERAL_STAT, generalStat);
        tag.putString(TAG_PARROT_CHANCE_KEY, "stardewcraft.trinket.parrot_chance_" + generalStat);
    }

    private static void rollIceRod(CompoundTag tag, SdvRandom random) {
        int projectileDelay = nextInt(random, 3000, 5001);
        int freezeTime = nextInt(random, 2000, 4001);
        if (random.nextDouble() < 0.05) {
            tag.putString(TAG_DISPLAY_KEY, "stardewcraft.trinket.perfect_ice_rod");
            projectileDelay = 3000;
            freezeTime = 4000;
        }
        tag.putInt(TAG_PROJECTILE_DELAY, projectileDelay);
        tag.putInt(TAG_FREEZE_TIME, freezeTime);
    }

    private static boolean nextBool(SdvRandom random, double chance) {
        return random.nextDouble() < chance;
    }

    private static int nextInt(SdvRandom random, int minInclusive, int maxExclusive) {
        return minInclusive + random.next(maxExclusive - minInclusive);
    }

    private static String formatSeconds(int millis, int decimals) {
        double seconds = millis / 1000.0;
        String value = String.format(Locale.ROOT, "%." + decimals + "f", seconds);
        while (value.contains(".") && value.endsWith("0")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static Item itemFor(TrinketType type) {
        return switch (type) {
            case MAGIC_HAIR_DYE -> ModItems.MAGIC_HAIR_DYE.get();
            case FROG_EGG -> ModItems.FROG_EGG.get();
            case MAGIC_QUIVER -> ModItems.MAGIC_QUIVER.get();
            case FAIRY_BOX -> ModItems.FAIRY_BOX.get();
            case PARROT_EGG -> ModItems.PARROT_EGG.get();
            case ICE_ROD -> ModItems.ICE_ROD.get();
            case IRIDIUM_SPUR -> ModItems.IRIDIUM_SPUR.get();
            case BASILISK_PAW -> ModItems.BASILISK_PAW.get();
        };
    }

    private static CompoundTag getCustomDataCopy(ItemStack stack) {
        return PortItemData.getOrDefault(stack, DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private static void setCustomData(ItemStack stack, CompoundTag tag) {
        if (tag.isEmpty()) {
            PortItemData.remove(stack, DataComponents.CUSTOM_DATA);
        } else {
            PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    private int primaryTextRgb() {
        return switch (trinketType) {
            case MAGIC_HAIR_DYE -> 0xFF5BFF;
            case FROG_EGG -> 0x8BE858;
            case MAGIC_QUIVER -> 0xDFA7FF;
            case FAIRY_BOX -> 0xFF9FEA;
            case PARROT_EGG -> 0x6FE6FF;
            case ICE_ROD -> 0x8CE8FF;
            case IRIDIUM_SPUR -> 0xFFD064;
            case BASILISK_PAW -> 0xB38CFF;
        };
    }

    private int descriptionTextRgb() {
        return switch (trinketType) {
            case MAGIC_HAIR_DYE -> 0xE9B2FF;
            case FROG_EGG -> 0xC8F78A;
            case MAGIC_QUIVER -> 0xF0D7FF;
            case FAIRY_BOX -> 0xFFD1F5;
            case PARROT_EGG -> 0xC6F5FF;
            case ICE_ROD -> 0xD7FAFF;
            case IRIDIUM_SPUR -> 0xFFE6A8;
            case BASILISK_PAW -> 0xD8CAFF;
        };
    }

    private static final class SdvRandom {
        private static final int MBIG = Integer.MAX_VALUE;
        private static final int MSEED = 161803398;
        private static final int PRIME32_1 = 0x9E3779B1;
        private static final int PRIME32_2 = 0x85EBCA77;
        private static final int PRIME32_3 = 0xC2B2AE3D;
        private static final int PRIME32_4 = 0x27D4EB2F;
        private static final int PRIME32_5 = 0x165667B1;

        private final int[] seedArray = new int[56];
        private int inext;
        private int inextp;

        static SdvRandom create(int generationSeed) {
            return new SdvRandom(createRandomSeed(generationSeed, 0, 0, 0, 0));
        }

        private SdvRandom(int seed) {
            int subtraction = seed == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(seed);
            int mj = MSEED - subtraction;
            seedArray[55] = mj;
            int mk = 1;
            for (int i = 1; i < 55; i++) {
                int ii = (21 * i) % 55;
                seedArray[ii] = mk;
                mk = mj - mk;
                if (mk < 0) {
                    mk += MBIG;
                }
                mj = seedArray[ii];
            }
            for (int k = 1; k < 5; k++) {
                for (int i = 1; i < 56; i++) {
                    seedArray[i] -= seedArray[1 + (i + 30) % 55];
                    if (seedArray[i] < 0) {
                        seedArray[i] += MBIG;
                    }
                }
            }
            inext = 0;
            inextp = 21;
        }

        int next(int maxExclusive) {
            return (int) (sample() * maxExclusive);
        }

        double nextDouble() {
            return sample();
        }

        private double sample() {
            return internalSample() * (1.0 / MBIG);
        }

        private int internalSample() {
            int locINext = inext + 1;
            if (locINext >= 56) {
                locINext = 1;
            }
            int locINextp = inextp + 1;
            if (locINextp >= 56) {
                locINextp = 1;
            }
            int retVal = seedArray[locINext] - seedArray[locINextp];
            if (retVal == MBIG) {
                retVal--;
            }
            if (retVal < 0) {
                retVal += MBIG;
            }
            seedArray[locINext] = retVal;
            inext = locINext;
            inextp = locINextp;
            return retVal;
        }

        private static int createRandomSeed(int seedA, int seedB, int seedC, int seedD, int seedE) {
            byte[] data = new byte[20];
            writeLittleEndian(data, 0, seedA);
            writeLittleEndian(data, 4, seedB);
            writeLittleEndian(data, 8, seedC);
            writeLittleEndian(data, 12, seedD);
            writeLittleEndian(data, 16, seedE);
            return xxHash32(data);
        }

        private static void writeLittleEndian(byte[] data, int offset, int value) {
            data[offset] = (byte) value;
            data[offset + 1] = (byte) (value >>> 8);
            data[offset + 2] = (byte) (value >>> 16);
            data[offset + 3] = (byte) (value >>> 24);
        }

        private static int xxHash32(byte[] data) {
            int index = 0;
            int hash;
            if (data.length >= 16) {
                int limit = data.length - 16;
                int v1 = PRIME32_1 + PRIME32_2;
                int v2 = PRIME32_2;
                int v3 = 0;
                int v4 = -PRIME32_1;
                while (index <= limit) {
                    v1 = round(v1, readInt(data, index));
                    index += 4;
                    v2 = round(v2, readInt(data, index));
                    index += 4;
                    v3 = round(v3, readInt(data, index));
                    index += 4;
                    v4 = round(v4, readInt(data, index));
                    index += 4;
                }
                hash = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7)
                        + Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18);
            } else {
                hash = PRIME32_5;
            }
            hash += data.length;
            while (index <= data.length - 4) {
                hash += readInt(data, index) * PRIME32_3;
                hash = Integer.rotateLeft(hash, 17) * PRIME32_4;
                index += 4;
            }
            while (index < data.length) {
                hash += (data[index] & 0xFF) * PRIME32_5;
                hash = Integer.rotateLeft(hash, 11) * PRIME32_1;
                index++;
            }
            hash ^= hash >>> 15;
            hash *= PRIME32_2;
            hash ^= hash >>> 13;
            hash *= PRIME32_3;
            hash ^= hash >>> 16;
            return hash;
        }

        private static int round(int acc, int input) {
            acc += input * PRIME32_2;
            acc = Integer.rotateLeft(acc, 13);
            acc *= PRIME32_1;
            return acc;
        }

        private static int readInt(byte[] data, int offset) {
            return (data[offset] & 0xFF)
                    | ((data[offset + 1] & 0xFF) << 8)
                    | ((data[offset + 2] & 0xFF) << 16)
                    | (data[offset + 3] << 24);
        }
    }
}
