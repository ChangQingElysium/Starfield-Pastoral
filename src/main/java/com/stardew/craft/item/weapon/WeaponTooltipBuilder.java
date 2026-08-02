package com.stardew.craft.item.weapon;

import com.stardew.craft.combat.WeaponForgeData;
import com.stardew.craft.combat.StardewWeaponKnockbackRules;
import com.stardew.craft.combat.StardewWeaponCriticalRules;
import com.stardew.craft.combat.WeaponStats;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器Tooltip构建器
 * 生成美观的武器描述信息
 */
public class WeaponTooltipBuilder {
    
    private final ItemStack stack;
    private final WeaponData data;
    private final boolean expanded;
    private final List<Component> lines = new ArrayList<>();
    
    public WeaponTooltipBuilder(ItemStack stack, WeaponData data) {
        this(stack, data, false);
    }

    public WeaponTooltipBuilder(ItemStack stack, WeaponData data, boolean expanded) {
        this.stack = stack;
        this.data = data;
        this.expanded = expanded;
    }
    
    /**
     * 构建完整的tooltip
     */
    public List<Component> build() {
        lines.clear();
        
        if (expanded) {
            // Shift held: show only skill details
            if (hasSkills()) {
                addSkills();
            }
        } else {
            // Default: attributes + compact skill summary
            addEmptyLine();
            addWeaponLevel();
            addAttributes();
            addForgeStatus();
            addEmptyLine();
            addSeparator();
            addSkillsCompact();
            if (hasSkills()) {
                lines.add(Component.translatable("stardewcraft.weapon.tooltip.hold_shift")
                        .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            }
        }
        
        return lines;
    }

    private boolean hasSkills() {
        return data.getSkill1() != null || data.getSkill2() != null;
    }
    
    /**
     * 添加武器类型和等级行
     * 格式: ⚔ 剑 │ Lv.1
     */
    @SuppressWarnings("null")
    private void addWeaponLevel() {
        MutableComponent line = Component.empty();

        line.append(Component.translatable("stardewcraft.weapon.tooltip.weapon_level", data.getLevel())
            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        
        lines.add(line);
    }
    
    /**
     * 添加伤害行
     * 格式: ⚔ 2-5 伤害
     */
    @SuppressWarnings("null")
    private static MutableComponent buildDamage(WeaponStats stats) {
        MutableComponent line = Component.empty();
        line.append(WeaponIcons.icon(WeaponIcons.ICON_DAMAGE));
        line.append(Component.translatable("stardewcraft.weapon.tooltip.damage_range", formatNumber(stats.getMinDamage()), formatNumber(stats.getMaxDamage()))
                .withStyle(ChatFormatting.RED));
        line.append(Component.translatable("stardewcraft.weapon.tooltip.damage").withStyle(ChatFormatting.GRAY));
        return line;
    }
    
    /**
     * 添加属性行（速度、防御、重量、暴击）
     */
    @SuppressWarnings("null")
    private void addAttributes() {
        WeaponStats stats = WeaponStats.fromItemStack(stack);
        MutableComponent damageLine = buildDamage(stats);
        MutableComponent speedLine = buildAttribute(WeaponIcons.ICON_SPEED, "stardewcraft.weapon.tooltip.speed", stats.getSpeed(), true);
        lines.add(buildPairLine(damageLine, speedLine));

        MutableComponent critLine = buildCriticalChance(stats);

        int critPowerBonus = (int) Math.round(stats.getBonusCritPower());
        MutableComponent critPowerLine = Component.empty();
        critPowerLine.append(WeaponIcons.icon(WeaponIcons.ICON_CRIT_POWER));
        critPowerLine.append(Component.translatable("stardewcraft.weapon.tooltip.crit_damage_value", critPowerBonus)
                .withStyle(ChatFormatting.GOLD));
        critPowerLine.append(Component.translatable("stardewcraft.tooltip.crit_power").withStyle(ChatFormatting.GRAY));

        lines.add(buildPairLine(critLine, critPowerLine));

        if (stats.getCritPowerMultiplierBonus() != 0.0f) {
            lines.add(buildPairLine(buildRelativeCritPower(stats), null));
        }

        java.util.List<MutableComponent> extras = new java.util.ArrayList<>();
        if (stats.getDefense() != 0) {
            extras.add(buildAttribute(WeaponIcons.ICON_DEFENSE, "stardewcraft.weapon.tooltip.defense", stats.getDefense(), true));
        }
        int weightPoints = StardewWeaponKnockbackRules.tooltipWeightPoints(
                stats.getWeaponType(),
                stats.getKnockback()
        );
        if (weightPoints != 0) {
            extras.add(buildAttribute(WeaponIcons.ICON_WEIGHT, "stardewcraft.weapon.tooltip.weight", weightPoints, true));
        }
        for (int i = 0; i < extras.size(); i += 2) {
            MutableComponent left = extras.get(i);
            MutableComponent right = (i + 1 < extras.size()) ? extras.get(i + 1) : null;
            lines.add(buildPairLine(left, right));
        }
    }

    private void addForgeStatus() {
        WeaponForgeData.State state = WeaponForgeData.read(stack);
        int forgeLevels = totalGemForgeLevels(state);
        if (forgeLevels > 0 && !state.diamondForge()) {
            lines.add(Component.translatable("stardewcraft.weapon.tooltip.forged", forgeLevels, 3)
                    .withStyle(ChatFormatting.DARK_RED));
        }
        if (state.diamondForge()) {
            lines.add(Component.translatable("stardewcraft.weapon.tooltip.diamond_forge")
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
        if (state.galaxySoulLevel() > 0) {
            lines.add(Component.translatable("stardewcraft.weapon.tooltip.galaxy_forged", state.galaxySoulLevel(), 3)
                    .withStyle(ChatFormatting.DARK_RED));
        }
        if (!state.appearanceWeaponId().isEmpty()) {
            Component appearanceName = appearanceName(state.appearanceWeaponId());
            lines.add(Component.translatable("stardewcraft.weapon.tooltip.appearance", appearanceName)
                    .withStyle(ChatFormatting.GRAY));
        }
        if (!state.dragonToothEnchantment().isEmpty()) {
            for (WeaponForgeData.DragonToothBonus bonus : WeaponForgeData.dragonToothBonuses(state.dragonToothEnchantment())) {
                lines.add(Component.translatable("stardewcraft.weapon.tooltip.dragon_tooth", formatDragonTooth(bonus))
                        .withStyle(ChatFormatting.DARK_PURPLE));
            }
        }
    }

    private static int totalGemForgeLevels(WeaponForgeData.State state) {
        int total = 0;
        for (WeaponForgeData.GemForge forge : state.gemForges()) {
            total += Math.max(0, forge.level());
        }
        return total;
    }

    private static Component appearanceName(String itemId) {
        ResourceLocation location = ResourceLocation.tryParse(itemId);
        if (location == null) {
            return Component.literal(itemId);
        }
        Item item = BuiltInRegistries.ITEM.get(location);
        ItemStack appearanceStack = new ItemStack(item);
        return appearanceStack.isEmpty() ? Component.literal(itemId) : appearanceStack.getHoverName();
    }

    static Component formatDragonTooth(WeaponForgeData.DragonToothBonus bonus) {
        Object value = switch (bonus.kind()) {
            case "crit" -> formatUnsignedPercent(0.02f * bonus.level());
            case "crit_power" -> formatUnsignedPercent(0.5f * bonus.level());
            default -> bonus.level();
        };
        return Component.translatable(
                "stardewcraft.weapon.tooltip.dragon_tooth." + bonus.kind(),
                value
        );
    }

    /** Complete combat-stat presentation for public API weapons. */
    public static List<Component> buildPublicApiCombatAttributes(ItemStack stack) {
        WeaponStats stats = WeaponStats.fromItemStack(stack);
        List<Component> attributes = new ArrayList<>();
        attributes.add(buildDamage(stats));
        attributes.add(buildAttribute(
                WeaponIcons.ICON_SPEED,
                "stardewcraft.weapon.tooltip.speed",
                stats.getSpeed(),
                true
        ));
        attributes.add(buildCriticalChance(stats));
        if (stats.getBonusCritPower() != 0.0F) {
            attributes.add(buildAttribute(
                    WeaponIcons.ICON_CRIT_POWER,
                    "stardewcraft.tooltip.crit_power",
                    stats.getBonusCritPower(),
                    true
            ));
        }
        if (stats.getCritPowerMultiplierBonus() != 0.0f) {
            attributes.add(buildRelativeCritPower(stats));
        }
        if (stats.getDefense() != 0) {
            attributes.add(buildAttribute(
                    WeaponIcons.ICON_DEFENSE,
                    "stardewcraft.weapon.tooltip.defense",
                    stats.getDefense(),
                    true
            ));
        }
        int weightPoints = StardewWeaponKnockbackRules.tooltipWeightPoints(
                stats.getWeaponType(),
                stats.getKnockback()
        );
        if (weightPoints != 0) {
            attributes.add(buildAttribute(
                    WeaponIcons.ICON_WEIGHT,
                    "stardewcraft.weapon.tooltip.weight",
                    weightPoints,
                    true
            ));
        }
        return List.copyOf(attributes);
    }

    /** @deprecated use the complete public weapon combat attribute list. */
    @Deprecated
    public static List<Component> buildPublicApiCriticalAttributes(ItemStack stack) {
        WeaponStats stats = WeaponStats.fromItemStack(stack);
        List<Component> attributes = new ArrayList<>();
        attributes.add(buildCriticalChance(stats));
        if (stats.getCritPowerMultiplierBonus() != 0.0F) {
            attributes.add(buildRelativeCritPower(stats));
        }
        return List.copyOf(attributes);
    }

    private static MutableComponent buildCriticalChance(WeaponStats stats) {
        MutableComponent line = Component.empty();
        line.append(WeaponIcons.icon(WeaponIcons.ICON_CRIT_CHANCE));
        line.append(Component.translatable(
                        "stardewcraft.weapon.tooltip.attr_value",
                        formatPercent(StardewWeaponCriticalRules.displayedChance(stats))
                )
                .withStyle(ChatFormatting.YELLOW));
        line.append(Component.translatable("stardewcraft.weapon.tooltip.crit_chance")
                .withStyle(ChatFormatting.GRAY));
        return line;
    }

    static float effectiveWeaponCriticalChance(WeaponStats stats) {
        return StardewWeaponCriticalRules.intrinsicChance(stats);
    }

    private static MutableComponent buildRelativeCritPower(WeaponStats stats) {
        MutableComponent line = Component.empty();
        line.append(WeaponIcons.icon(WeaponIcons.ICON_CRIT_POWER));
        line.append(Component.translatable(
                        "stardewcraft.weapon.tooltip.attr_value",
                        formatSignedPercent(stats.getCritPowerMultiplierBonus())
                )
                .withStyle(ChatFormatting.GOLD));
        line.append(Component.translatable("stardewcraft.weapon.tooltip.crit_damage")
                .withStyle(ChatFormatting.GRAY));
        return line;
    }
    
    @SuppressWarnings("null")
    private static MutableComponent buildAttribute(String icon, String nameKey, float value, boolean showSign) {
        MutableComponent line = Component.empty();
        ChatFormatting color = value > 0 ? ChatFormatting.GREEN : ChatFormatting.RED;
        line.append(WeaponIcons.icon(icon));

        String valueStr = showSign
                ? formatSignedNumber(value)
                : formatNumber(value);
        line.append(Component.translatable("stardewcraft.weapon.tooltip.attr_value", valueStr).withStyle(color));
        line.append(Component.translatable(nameKey).withStyle(ChatFormatting.GRAY));
        return line;
    }

    @SuppressWarnings("null")
    private MutableComponent buildPairLine(MutableComponent left, MutableComponent right) {
        MutableComponent line = Component.empty();
        line.append(Component.literal(" "));
        line.append(left);
        if (right != null) {
            line.append(Component.literal("   "));
            line.append(right);
        }
        return line;
    }
    
    /**
     * 添加技能信息
     */
    private void addSkills() {
        WeaponSkillData skill1 = data.getSkill1();
        WeaponSkillData skill2 = data.getSkill2();
        if (skill1 == null && skill2 == null) {
            return;
        }

        String minorKeyLabel = getKeyLabel(true);
        String majorKeyLabel = getKeyLabel(false);

        if (skill1 != null && skill2 != null) {
            lines.add(buildSkillTitle(skill1, minorKeyLabel));
            addSkillDetails(skill1);
            addEmptyLine();
            lines.add(buildSkillTitle(skill2, majorKeyLabel));
            addSkillDetails(skill2);
            return;
        }

        if (skill1 != null) {
            lines.add(buildSkillTitle(skill1, minorKeyLabel));
            addSkillDetails(skill1);
        }

        if (skill2 != null) {
            lines.add(buildSkillTitle(skill2, majorKeyLabel));
            addSkillDetails(skill2);
        }
    }

    /**
     * 紧凑版技能信息（默认显示）
     * 每个技能一行：图标 + 名称 [按键] — 伤害% · CD秒
     */
    @SuppressWarnings("null")
    private void addSkillsCompact() {
        WeaponSkillData skill1 = data.getSkill1();
        WeaponSkillData skill2 = data.getSkill2();
        if (skill1 == null && skill2 == null) return;

        String minorKeyLabel = getKeyLabel(true);
        String majorKeyLabel = getKeyLabel(false);

        if (skill1 != null) {
            lines.add(buildCompactSkillLine(skill1, minorKeyLabel));
        }
        if (skill2 != null) {
            lines.add(buildCompactSkillLine(skill2, majorKeyLabel));
        }
    }

    @SuppressWarnings("null")
    private MutableComponent buildCompactSkillLine(WeaponSkillData skill, String keyLabel) {
        MutableComponent line = Component.empty();

        // Icon
        if (skill.getIconChar() != null) {
            line.append(WeaponIcons.icon(skill.getIconChar()));
        } else {
            line.append(WeaponIcons.icon(WeaponIcons.ICON_SKILL));
        }
        line.append(Component.literal(" "));

        // Name [key]
        line.append(Component.translatable(skill.getNameKey()).withStyle(ChatFormatting.YELLOW));
        line.append(Component.translatable("stardewcraft.weapon.tooltip.trigger_format",
                Component.literal(keyLabel)).withStyle(ChatFormatting.DARK_GRAY));

        // Damage%
        if (skill.getDamagePercent() > 0) {
            line.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(Component.translatable("stardewcraft.weapon.tooltip.skill_damage", skill.getDamagePercent())
                    .withStyle(ChatFormatting.AQUA));
        }

        // Cooldown
        line.append(Component.literal(" ").withStyle(ChatFormatting.DARK_GRAY));
        line.append(WeaponIcons.icon(WeaponIcons.ICON_COOLDOWN));
        line.append(Component.translatable("stardewcraft.weapon.tooltip.cooldown_seconds", skill.getCooldown())
                .withStyle(ChatFormatting.BLUE));

        return line;
    }
    
    /**
     * 添加单个技能区块
     */
    @SuppressWarnings("null")
    private MutableComponent buildSkillTitle(WeaponSkillData skill, String keyLabel) {
        MutableComponent titleLine = Component.empty();

        if (skill.getIconChar() != null) {
            titleLine.append(WeaponIcons.icon(skill.getIconChar()));
            titleLine.append(Component.literal(" "));
        } else {
            titleLine.append(WeaponIcons.icon(WeaponIcons.ICON_SKILL));
            titleLine.append(Component.literal(" "));
        }

        titleLine.append(Component.translatable(skill.getNameKey()).withStyle(ChatFormatting.YELLOW));
        titleLine.append(Component.translatable("stardewcraft.weapon.tooltip.trigger_format", Component.literal(keyLabel))
                .withStyle(ChatFormatting.DARK_GRAY));
        return titleLine;
    }

    @SuppressWarnings("null")
    private void addSkillDetails(WeaponSkillData skill) {
        for (String descKey : skill.getDescriptionKeys()) {
            lines.add(Component.translatable("stardewcraft.weapon.tooltip.skill_desc", Component.translatable(descKey))
                    .withStyle(ChatFormatting.GRAY));
        }
        
        addEmptyLine();
        
        // 技能详细数据
        // 伤害
        if (skill.getDamagePercent() > 0) {
            MutableComponent damageLine = Component.empty();
                damageLine.append(Component.translatable("stardewcraft.weapon.tooltip.effect_prefix").withStyle(ChatFormatting.DARK_GRAY));
                damageLine.append(Component.translatable("stardewcraft.weapon.tooltip.skill_damage", skill.getDamagePercent())
                    .withStyle(ChatFormatting.AQUA));
            lines.add(damageLine);
        }
        
        // 特殊效果
        for (String effectKey : skill.getEffectKeys()) {
            MutableComponent effectLine = Component.empty();
            effectLine.append(Component.translatable("stardewcraft.weapon.tooltip.effect_prefix").withStyle(ChatFormatting.DARK_GRAY));
            effectLine.append(Component.translatable(effectKey).withStyle(ChatFormatting.GRAY));
            lines.add(effectLine);
        }
        
        // 冷却时间（最后一项用 └）
        MutableComponent cooldownLine = Component.empty();
        cooldownLine.append(Component.translatable("stardewcraft.weapon.tooltip.cooldown_prefix").withStyle(ChatFormatting.DARK_GRAY));
        cooldownLine.append(WeaponIcons.icon(WeaponIcons.ICON_COOLDOWN));
        cooldownLine.append(Component.translatable("stardewcraft.weapon.tooltip.cooldown_seconds", skill.getCooldown())
                .withStyle(ChatFormatting.BLUE));
        cooldownLine.append(Component.translatable("stardewcraft.weapon.tooltip.cooldown").withStyle(ChatFormatting.GRAY));
        lines.add(cooldownLine);
    }

    @SuppressWarnings({ "null", "unused" })
    private MutableComponent mergeSkillTitles(MutableComponent left, MutableComponent right) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return left.append(Component.literal("  ")).append(right);
        }

        @SuppressWarnings("null")
        int leftWidth = mc.font.width(left.getString());
        int spaceWidth = Math.max(1, mc.font.width(" "));
        int targetColumn = Math.max(120, leftWidth + (spaceWidth * 2));
        int padPx = Math.max(spaceWidth * 2, targetColumn - leftWidth);
        int padSpaces = (int) Math.ceil(padPx / (double) spaceWidth);

        return left.append(Component.literal(" ".repeat(padSpaces))).append(right);
    }
    
    
    /**
     * 添加分隔线
     */
    private void addSeparator() {
        lines.add(WeaponIcons.separator());
    }
    
    /**
     * 添加空行
     */
    private void addEmptyLine() {
        lines.add(Component.empty());
    }
    
    private String getKeyLabel(boolean minor) {
        try {
            if (minor) {
                return com.stardew.craft.client.ModKeyMappings.SKILL_MINOR.getTranslatedKeyMessage().getString();
            }
            return com.stardew.craft.client.ModKeyMappings.SKILL_MAJOR.getTranslatedKeyMessage().getString();
        } catch (Throwable ignored) {
            return minor ? "RMB" : "Z";
        }
    }
    
    /**
     * 格式化百分比
     */
    private static String formatPercent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    private static String formatSignedPercent(double value) {
        int percent = (int) Math.round(value * 100.0);
        return (percent > 0 ? "+" : "") + percent + "%";
    }

    private static String formatUnsignedPercent(double value) {
        return Math.round(value * 100.0) + "%";
    }

    static String formatSignedNumber(float value) {
        String formatted = formatNumber(value);
        return value > 0.0F ? "+" + formatted : formatted;
    }

    private static String formatNumber(float value) {
        if (Math.abs(value - Math.round(value)) < 0.001f) {
            return Integer.toString(Math.round(value));
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
    
}
