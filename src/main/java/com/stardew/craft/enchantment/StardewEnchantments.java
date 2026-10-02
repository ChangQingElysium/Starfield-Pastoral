package com.stardew.craft.enchantment;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.StardewCraft;
import java.util.Set;
import net.minecraft.core.Holder;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import com.stardew.craft.port.net.minecraft.world.item.enchantment.ItemEnchantments;

public final class StardewEnchantments {
    public static final ResourceKey<Enchantment> ARTFUL = key("artful");
    public static final ResourceKey<Enchantment> HAYMAKER = key("haymaker");
    public static final ResourceKey<Enchantment> BUG_KILLER = key("bug_killer");
    public static final ResourceKey<Enchantment> VAMPIRIC = key("vampiric");
    public static final ResourceKey<Enchantment> CRUSADER = key("crusader");
    public static final ResourceKey<Enchantment> POWERFUL = key("powerful");
    public static final ResourceKey<Enchantment> EFFICIENT = key("efficient");
    public static final ResourceKey<Enchantment> SWIFT = key("swift");
    public static final ResourceKey<Enchantment> EXPANSIVE = key("expansive");
    public static final ResourceKey<Enchantment> BOTTOMLESS = key("bottomless");
    public static final ResourceKey<Enchantment> SHAVING = key("shaving");
    public static final ResourceKey<Enchantment> ARCHAEOLOGIST = key("archaeologist");
    public static final ResourceKey<Enchantment> GENEROUS = key("generous");
    public static final ResourceKey<Enchantment> MASTER = key("master");
    public static final ResourceKey<Enchantment> AUTO_HOOK = key("auto_hook");
    public static final ResourceKey<Enchantment> PRESERVING = key("preserving");
    public static final ResourceKey<Enchantment> FISHER = key("fisher");

    // PORT(1.20.1): 1.20.5 entity type tags; shipped in src/port/resources with the 1.21.1 vanilla structure
    // (#arthropod, #undead = #skeletons + skeleton_horse + #zombies + wither + phantom; 1.21-only minecraft:bogged
    // omitted), so data packs editing the nested tags propagate as on 1.21.1. 1.21.1 other mods tag their mobs into
    // #arthropod/#undead for vanilla Bane of Arthropods/Smite; on 1.20.1 vanilla uses MobType for that, so non-vanilla
    // entity types also count by MobType (see portMobType).
    private static final TagKey<EntityType<?>> SENSITIVE_TO_BANE_OF_ARTHROPODS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("minecraft", "sensitive_to_bane_of_arthropods"));
    private static final TagKey<EntityType<?>> SENSITIVE_TO_SMITE =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation("minecraft", "sensitive_to_smite"));

    private static final Set<String> BUG_MONSTER_TAGS = Set.of(
            "sd_mob_bug",
            "sd_mob_grub",
            "sd_mob_fly",
            "sd_mob_mutant_bug",
            "sd_mob_mutant_grub",
            "sd_mob_armored_bug"
    );

    private static final Set<String> UNDEAD_MONSTER_TAGS = Set.of(
            "sd_mob_mummy",
            "sd_mob_skeleton",
            "sd_mob_ghost",
            "sd_mob_carbon_ghost",
            "sd_mob_shadow_brute",
            "sd_mob_shadow_shaman",
            "sd_mob_shadow_sniper"
    );

    private StardewEnchantments() {
    }

    public static boolean has(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        return getLevel(stack, enchantment) > 0;
    }

    public static int getLevel(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return getLevel(PortItemData.get(stack, DataComponents.ENCHANTMENTS), enchantment);
    }

    public static boolean isBugKillerTarget(LivingEntity target) {
        return target != null && (target.getType().is(SENSITIVE_TO_BANE_OF_ARTHROPODS)
                || portMobType(target, net.minecraft.world.entity.MobType.ARTHROPOD)
                || hasAnyTag(target, BUG_MONSTER_TAGS));
    }

    public static boolean isCrusaderTarget(LivingEntity target) {
        return target != null && (target.getType().is(SENSITIVE_TO_SMITE)
                || portMobType(target, net.minecraft.world.entity.MobType.UNDEAD)
                || hasAnyTag(target, UNDEAD_MONSTER_TAGS));
    }

    /** PORT(1.20.1): a non-vanilla mob that 1.20.1 vanilla Smite/Bane of Arthropods treats as undead/arthropod. */
    private static boolean portMobType(LivingEntity target, net.minecraft.world.entity.MobType mobType) {
        return target.getMobType() == mobType && !"minecraft".equals(
                net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).getNamespace());
    }

    public static int effectiveFishingLevel(net.minecraft.server.level.ServerPlayer player, ItemStack rod) {
        int level = com.stardew.craft.player.PlayerStardewDataAPI.getSkillLevel(player, com.stardew.craft.player.SkillType.FISHING);
        return has(rod, MASTER) ? level + 1 : level;
    }

    private static boolean hasAnyTag(LivingEntity target, Set<String> tags) {
        for (String tag : tags) {
            if (target.getTags().contains(tag)) {
                return true;
            }
        }
        return false;
    }

    private static int getLevel(ItemEnchantments enchantments, ResourceKey<Enchantment> enchantment) {
        if (enchantments == null || enchantments.isEmpty()) {
            return 0;
        }
        int best = 0;
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            if (holder.unwrapKey().filter(enchantment::equals).isPresent()) {
                best = Math.max(best, entry.getIntValue());
            }
        }
        return best;
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, new ResourceLocation(StardewCraft.MODID, name));
    }
}