package com.stardew.craft.player;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * 职业类型枚举
 * 对应星露谷物语的职业系统（5级和10级选择）
 */
public enum ProfessionType {
    // 农业职业 (Farming)
    RANCHER(0, "rancher", SkillType.FARMING, 5),
    TILLER(1, "tiller", SkillType.FARMING, 5),
    COOPMASTER(2, "coopmaster", SkillType.FARMING, 10),
    SHEPHERD(3, "shepherd", SkillType.FARMING, 10),
    ARTISAN(4, "artisan", SkillType.FARMING, 10),
    AGRICULTURIST(5, "agriculturist", SkillType.FARMING, 10),
    
    // 钓鱼职业 (Fishing)
    FISHER(6, "fisher", SkillType.FISHING, 5),
    TRAPPER(7, "trapper", SkillType.FISHING, 5),
    ANGLER(8, "angler", SkillType.FISHING, 10),
    PIRATE(9, "pirate", SkillType.FISHING, 10),
    MARINER(10, "mariner", SkillType.FISHING, 10),
    LUREMASTER(11, "luremaster", SkillType.FISHING, 10),
    
    // 觅食职业 (Foraging)
    FORESTER(12, "forester", SkillType.FORAGING, 5),
    GATHERER(13, "gatherer", SkillType.FORAGING, 5),
    LUMBERJACK(14, "lumberjack", SkillType.FORAGING, 10),
    TAPPER(15, "tapper", SkillType.FORAGING, 10),
    BOTANIST(16, "botanist", SkillType.FORAGING, 10),
    TRACKER(17, "tracker", SkillType.FORAGING, 10),
    
    // 采矿职业 (Mining)
    MINER(18, "miner", SkillType.MINING, 5),
    GEOLOGIST(19, "geologist", SkillType.MINING, 5),
    BLACKSMITH(20, "blacksmith", SkillType.MINING, 10),
    PROSPECTOR(21, "prospector", SkillType.MINING, 10),
    EXCAVATOR(22, "excavator", SkillType.MINING, 10),
    GEMOLOGIST(23, "gemologist", SkillType.MINING, 10),
    
    // 战斗职业 (Combat)
    FIGHTER(24, "fighter", SkillType.COMBAT, 5),
    SCOUT(25, "scout", SkillType.COMBAT, 5),
    BRUTE(26, "brute", SkillType.COMBAT, 10),
    DEFENDER(27, "defender", SkillType.COMBAT, 10),
    ACROBAT(28, "acrobat", SkillType.COMBAT, 10),
    DESPERADO(29, "desperado", SkillType.COMBAT, 10);
    
    private final int id;
    private final String name;
    private final SkillType skillType;
    private final int level;  // 需要的技能等级（5或10）
    
    ProfessionType(int id, String name, SkillType skillType, int level) {
        this.id = id;
        this.name = name;
        this.skillType = skillType;
        this.level = level;
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public String getTranslationKey() {
        return ProfessionData.definition(this)
                .map(com.stardew.craft.api.v1.profession.StardewProfessionDefinition::nameKey)
                .orElse("stardewcraft.levelup.profession." + id + ".name");
    }

    public String getDescriptionTranslationKey() {
        return ProfessionData.definition(this)
                .map(com.stardew.craft.api.v1.profession.StardewProfessionDefinition::descKey)
                .orElse("stardewcraft.levelup.profession." + id + ".desc");
    }

    public Component getDisplayName() {
        return Component.translatable(getTranslationKey());
    }
    
    public SkillType getSkillType() {
        return ProfessionData.definition(this)
                .map(com.stardew.craft.api.v1.profession.StardewProfessionDefinition::skill)
                .map(ResourceLocation::getPath)
                .map(SkillType::fromName)
                .orElse(skillType);
    }
    
    public int getRequiredLevel() {
        return ProfessionData.definition(this)
                .map(com.stardew.craft.api.v1.profession.StardewProfessionDefinition::requiredLevel)
                .orElse(level);
    }

    public Optional<ResourceLocation> getParentId() {
        return ProfessionData.definition(this)
                .flatMap(com.stardew.craft.api.v1.profession.StardewProfessionDefinition::parent);
    }
    
    /**
     * 根据ID获取职业类型
     */
    public static ProfessionType fromId(int id) {
        for (ProfessionType profession : values()) {
            if (profession.id == id) {
                return profession;
            }
        }
        return null;
    }
    
    /**
     * 获取某个技能的5级职业选项
     */
    public static ProfessionType[] getLevel5Options(SkillType skill) {
        return switch (skill) {
            case FARMING -> new ProfessionType[]{RANCHER, TILLER};
            case FISHING -> new ProfessionType[]{FISHER, TRAPPER};
            case FORAGING -> new ProfessionType[]{FORESTER, GATHERER};
            case MINING -> new ProfessionType[]{MINER, GEOLOGIST};
            case COMBAT -> new ProfessionType[]{FIGHTER, SCOUT};
        };
    }
    
    /**
     * 获取某个技能的10级职业选项（基于5级选择）
     */
    public static ProfessionType[] getLevel10Options(SkillType skill, ProfessionType level5Choice) {
        return switch (skill) {
            case FARMING -> {
                if (level5Choice == RANCHER) {
                    yield new ProfessionType[]{COOPMASTER, SHEPHERD};
                } else {
                    yield new ProfessionType[]{ARTISAN, AGRICULTURIST};
                }
            }
            case FISHING -> {
                if (level5Choice == FISHER) {
                    yield new ProfessionType[]{ANGLER, PIRATE};
                } else {
                    yield new ProfessionType[]{MARINER, LUREMASTER};
                }
            }
            case FORAGING -> {
                if (level5Choice == FORESTER) {
                    yield new ProfessionType[]{LUMBERJACK, TAPPER};
                } else {
                    yield new ProfessionType[]{BOTANIST, TRACKER};
                }
            }
            case MINING -> {
                if (level5Choice == MINER) {
                    yield new ProfessionType[]{BLACKSMITH, PROSPECTOR};
                } else {
                    yield new ProfessionType[]{EXCAVATOR, GEMOLOGIST};
                }
            }
            case COMBAT -> {
                if (level5Choice == FIGHTER) {
                    yield new ProfessionType[]{BRUTE, DEFENDER};
                } else {
                    yield new ProfessionType[]{ACROBAT, DESPERADO};
                }
            }
        };
    }
}
