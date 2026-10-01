package com.stardew.craft.combat;

import com.stardew.craft.port.PortItemData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomData;
import com.stardew.craft.port.net.minecraft.world.item.component.CustomModelData;

public final class WeaponForgeData {
    public static final String TAG_STARDEW_FORGE = "StardewForge";
    public static final String TAG_GEM_FORGES = "GemForges";
    public static final String TAG_GEM_ITEM_ID = "ItemId";
    public static final String TAG_GEM_LEVEL = "Level";
    public static final String TAG_PRISMATIC_ENCHANTMENT = "PrismaticEnchantment";
    public static final String TAG_PREVIOUS_ENCHANTMENTS = "PreviousEnchantments";
    public static final String TAG_GALAXY_SOUL_LEVEL = "GalaxySoulLevel";
    public static final String TAG_APPEARANCE_WEAPON_ID = "AppearanceWeaponId";
    public static final String TAG_DRAGON_TOOTH_ENCHANTMENT = "DragonToothEnchantment";
    public static final String TAG_DIAMOND_FORGE = "DiamondForge";
    public static final int APPEARANCE_CUSTOM_MODEL_DATA_BASE = 700_000;
    private static final int APPEARANCE_CUSTOM_MODEL_DATA_RANGE = 100_000;
    private static final Map<String, Integer> APPEARANCE_CUSTOM_MODEL_DATA_BY_ITEM_ID = Map.ofEntries(
            Map.entry("stardewcraft:bone_sword", 700001),
            Map.entry("stardewcraft:claymore", 700002),
            Map.entry("stardewcraft:cutlass", 700003),
            Map.entry("stardewcraft:dark_sword", 700004),
            Map.entry("stardewcraft:dragontooth_cutlass", 700005),
            Map.entry("stardewcraft:dwarf_sword", 700006),
            Map.entry("stardewcraft:forest_sword", 700007),
            Map.entry("stardewcraft:galaxy_sword", 700008),
            Map.entry("stardewcraft:holy_blade", 700009),
            Map.entry("stardewcraft:infinity_blade", 700010),
            Map.entry("stardewcraft:insect_head", 700011),
            Map.entry("stardewcraft:iron_edge", 700012),
            Map.entry("stardewcraft:lava_katana", 700013),
            Map.entry("stardewcraft:meowmere", 700014),
            Map.entry("stardewcraft:neptunes_glaive", 700015),
            Map.entry("stardewcraft:obsidian_edge", 700016),
            Map.entry("stardewcraft:ossified_blade", 700017),
            Map.entry("stardewcraft:pirate_sword", 700018),
            Map.entry("stardewcraft:rusty_sword", 700019),
            Map.entry("stardewcraft:silver_saber", 700020),
            Map.entry("stardewcraft:steel_falchion", 700021),
            Map.entry("stardewcraft:steel_smallsword", 700022),
            Map.entry("stardewcraft:tempered_broadsword", 700023),
            Map.entry("stardewcraft:templars_blade", 700024),
            Map.entry("stardewcraft:wooden_blade", 700025),
            Map.entry("stardewcraft:yeti_tooth", 700026),
            Map.entry("stardewcraft:broken_trident", 700101),
            Map.entry("stardewcraft:burglars_shank", 700102),
            Map.entry("stardewcraft:carving_knife", 700103),
            Map.entry("stardewcraft:crystal_dagger", 700104),
            Map.entry("stardewcraft:dragontooth_shiv", 700105),
            Map.entry("stardewcraft:dwarf_dagger", 700106),
            Map.entry("stardewcraft:elf_blade", 700107),
            Map.entry("stardewcraft:galaxy_dagger", 700108),
            Map.entry("stardewcraft:infinity_dagger", 700109),
            Map.entry("stardewcraft:iridium_needle", 700110),
            Map.entry("stardewcraft:iron_dirk", 700111),
            Map.entry("stardewcraft:shadow_dagger", 700112),
            Map.entry("stardewcraft:wicked_kris", 700113),
            Map.entry("stardewcraft:wind_spire", 700114),
            Map.entry("stardewcraft:femur", 700201),
            Map.entry("stardewcraft:galaxy_hammer", 700202),
            Map.entry("stardewcraft:infinity_gavel", 700203));

    private WeaponForgeData() {
    }

    public record GemForge(String itemId, int level) {
    }

    public record DragonToothBonus(String kind, int level) {
        public DragonToothBonus {
            kind = kind == null ? "" : kind;
            level = Math.max(0, level);
        }
    }

    public record State(
            List<GemForge> gemForges,
            String prismaticEnchantment,
            List<String> previousEnchantments,
            int galaxySoulLevel,
            String appearanceWeaponId,
            String dragonToothEnchantment,
            boolean diamondForge) {
        public State {
            gemForges = List.copyOf(gemForges);
            previousEnchantments = List.copyOf(previousEnchantments);
            prismaticEnchantment = prismaticEnchantment == null ? "" : prismaticEnchantment;
            appearanceWeaponId = appearanceWeaponId == null ? "" : appearanceWeaponId;
            dragonToothEnchantment = dragonToothEnchantment == null ? "" : dragonToothEnchantment;
        }
    }

    public static State empty() {
        return new State(List.of(), "", List.of(), 0, "", "", false);
    }

    public static boolean hasMeaningfulForgeState(ItemStack stack) {
        State state = read(stack);
        return !state.gemForges().isEmpty()
                || !state.prismaticEnchantment().isEmpty()
                || !state.previousEnchantments().isEmpty()
                || state.galaxySoulLevel() > 0
                || !state.appearanceWeaponId().isEmpty()
                || !state.dragonToothEnchantment().isEmpty()
                || state.diamondForge();
    }

    public static List<DragonToothBonus> dragonToothBonuses(String value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        List<DragonToothBonus> bonuses = new ArrayList<>();
        for (String entry : value.split(";")) {
            DragonToothBonus bonus = parseDragonToothBonus(entry);
            if (!bonus.kind().isEmpty() && bonus.level() > 0) {
                bonuses.add(bonus);
            }
        }
        return List.copyOf(bonuses);
    }

    public static String encodeDragonToothBonuses(List<DragonToothBonus> bonuses) {
        if (bonuses == null || bonuses.isEmpty()) {
            return "";
        }
        List<String> entries = new ArrayList<>();
        for (DragonToothBonus bonus : bonuses) {
            if (bonus != null && !bonus.kind().isEmpty() && bonus.level() > 0) {
                entries.add(bonus.kind() + ":" + bonus.level());
            }
        }
        return String.join(";", entries);
    }

    private static DragonToothBonus parseDragonToothBonus(String value) {
        if (value == null || value.isEmpty()) {
            return new DragonToothBonus("", 0);
        }
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            return new DragonToothBonus(value, 1);
        }
        try {
            return new DragonToothBonus(parts[0], Integer.parseInt(parts[1]));
        } catch (NumberFormatException exception) {
            return new DragonToothBonus(parts[0], 1);
        }
    }

    public static State read(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return empty();
        }

        CustomData data = PortItemData.get(stack, DataComponents.CUSTOM_DATA);
        if (data == null) {
            return empty();
        }

        CompoundTag root = data.copyTag();
        if (!root.contains(TAG_STARDEW_FORGE, Tag.TAG_COMPOUND)) {
            return empty();
        }

        CompoundTag forgeTag = root.getCompound(TAG_STARDEW_FORGE);
        List<GemForge> gemForges = new ArrayList<>();
        ListTag gemList = forgeTag.getList(TAG_GEM_FORGES, Tag.TAG_COMPOUND);
        for (int i = 0; i < gemList.size(); i++) {
            CompoundTag gemTag = gemList.getCompound(i);
            String itemId = gemTag.getString(TAG_GEM_ITEM_ID);
            if (!itemId.isEmpty()) {
                gemForges.add(new GemForge(itemId, gemTag.getInt(TAG_GEM_LEVEL)));
            }
        }

        List<String> previousEnchantments = new ArrayList<>();
        ListTag historyList = forgeTag.getList(TAG_PREVIOUS_ENCHANTMENTS, Tag.TAG_STRING);
        for (int i = 0; i < historyList.size(); i++) {
            String enchantment = historyList.getString(i);
            if (!enchantment.isEmpty()) {
                previousEnchantments.add(enchantment);
            }
        }

        return new State(
                gemForges,
                forgeTag.getString(TAG_PRISMATIC_ENCHANTMENT),
                previousEnchantments,
                forgeTag.getInt(TAG_GALAXY_SOUL_LEVEL),
                forgeTag.getString(TAG_APPEARANCE_WEAPON_ID),
                forgeTag.getString(TAG_DRAGON_TOOTH_ENCHANTMENT),
                forgeTag.getBoolean(TAG_DIAMOND_FORGE));
    }

    public static void ensure(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData data = PortItemData.get(stack, DataComponents.CUSTOM_DATA);
        CompoundTag root = data != null ? data.copyTag() : new CompoundTag();
        if (!root.contains(TAG_STARDEW_FORGE, Tag.TAG_COMPOUND)) {
            root.put(TAG_STARDEW_FORGE, toTag(empty()));
            PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
        syncAppearanceModelData(stack, read(stack).appearanceWeaponId());
    }

    public static void write(ItemStack stack, State state) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData data = PortItemData.get(stack, DataComponents.CUSTOM_DATA);
        CompoundTag root = data != null ? data.copyTag() : new CompoundTag();
        root.put(TAG_STARDEW_FORGE, toTag(state));
        PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(root));
        syncAppearanceModelData(stack, state.appearanceWeaponId());
    }

    public static void clear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        CustomData data = PortItemData.get(stack, DataComponents.CUSTOM_DATA);
        if (data == null) {
            return;
        }
        CompoundTag root = data.copyTag();
        root.remove(TAG_STARDEW_FORGE);
        PortItemData.set(stack, DataComponents.CUSTOM_DATA, CustomData.of(root));
        syncAppearanceModelData(stack, "");
    }

    public static int appearanceCustomModelData(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return 0;
        }
        return APPEARANCE_CUSTOM_MODEL_DATA_BY_ITEM_ID.getOrDefault(itemId, 0);
    }

    private static void syncAppearanceModelData(ItemStack stack, String appearanceWeaponId) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (appearanceWeaponId != null && !appearanceWeaponId.isEmpty()) {
            int modelData = appearanceCustomModelData(appearanceWeaponId);
            if (modelData != 0) {
                PortItemData.set(stack, DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(modelData));
            } else {
                clearAppearanceModelData(stack);
            }
            return;
        }

        clearAppearanceModelData(stack);
    }

    private static void clearAppearanceModelData(ItemStack stack) {
        CustomModelData modelData = PortItemData.get(stack, DataComponents.CUSTOM_MODEL_DATA);
        if (modelData != null && isAppearanceCustomModelData(modelData.value())) {
            PortItemData.remove(stack, DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    private static boolean isAppearanceCustomModelData(int value) {
        return value >= APPEARANCE_CUSTOM_MODEL_DATA_BASE
                && value < APPEARANCE_CUSTOM_MODEL_DATA_BASE + APPEARANCE_CUSTOM_MODEL_DATA_RANGE;
    }

    private static CompoundTag toTag(State state) {
        CompoundTag forgeTag = new CompoundTag();
        ListTag gemList = new ListTag();
        for (GemForge gemForge : state.gemForges()) {
            if (gemForge.itemId() == null || gemForge.itemId().isEmpty()) {
                continue;
            }
            CompoundTag gemTag = new CompoundTag();
            gemTag.putString(TAG_GEM_ITEM_ID, gemForge.itemId());
            gemTag.putInt(TAG_GEM_LEVEL, gemForge.level());
            gemList.add(gemTag);
        }
        forgeTag.put(TAG_GEM_FORGES, gemList);
        forgeTag.putString(TAG_PRISMATIC_ENCHANTMENT, state.prismaticEnchantment());

        ListTag historyList = new ListTag();
        for (String enchantment : state.previousEnchantments()) {
            if (enchantment != null && !enchantment.isEmpty()) {
                historyList.add(StringTag.valueOf(enchantment));
            }
        }
        forgeTag.put(TAG_PREVIOUS_ENCHANTMENTS, historyList);
        forgeTag.putInt(TAG_GALAXY_SOUL_LEVEL, state.galaxySoulLevel());
        forgeTag.putString(TAG_APPEARANCE_WEAPON_ID, state.appearanceWeaponId());
        forgeTag.putString(TAG_DRAGON_TOOTH_ENCHANTMENT, state.dragonToothEnchantment());
        forgeTag.putBoolean(TAG_DIAMOND_FORGE, state.diamondForge());
        return forgeTag;
    }
}