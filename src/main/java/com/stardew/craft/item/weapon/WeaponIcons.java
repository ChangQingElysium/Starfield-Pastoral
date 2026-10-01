package com.stardew.craft.item.weapon;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/**
 * 武器图标字符常量
 * 使用自定义字体 stardewcraft:weapon_icons 显示
 */
public class WeaponIcons {
    
    // 字体资源
    public static final ResourceLocation WEAPON_ICONS_FONT = new ResourceLocation("stardewcraft", "weapon_icons");
    
    // 属性图标字符 (Private Use Area)
    public static final String ICON_DAMAGE = "\uE001";      // 伤害
    public static final String ICON_SPEED = "\uE002";       // 攻击速度
    public static final String ICON_DEFENSE = "\uE003";     // 防御
    public static final String ICON_WEIGHT = "\uE004";      // 重量/击退
    public static final String ICON_CRIT_CHANCE = "\uE005"; // 暴击率
    public static final String ICON_CRIT_POWER = "\uE006";  // 暴击伤害
    public static final String ICON_COOLDOWN = "\uE007";    // 冷却时间
    public static final String ICON_SKILL = "\uE008";       // 技能标记
    public static final String ICON_IMMUNITY = "\uE009";    // 免疫
    public static final String ICON_MAGNETIC = "\uE00A";    // 磁力范围
    public static final String ICON_LUCK = "\uE00B";        // 幸运
    
    // 技能图标 (从 E100 开始)
    public static final String SKILL_RUSTY_SWORD_1 = "\uE100"; // 破伤一击
    public static final String SKILL_STEEL_SMALLSWORD_1 = "\uE101"; // 轻剑反击
    public static final String SKILL_WOODEN_BLADE_1 = "\uE102"; // 树木庇佑
    public static final String SKILL_PIRATE_SWORD_1 = "\uE103"; // 亡命掠夺
    public static final String SKILL_SILVER_SABER_1 = "\uE104"; // 银纹折返
    public static final String SKILL_CUTLASS_1 = "\uE105"; // 弦月斩
    public static final String SKILL_FOREST_SWORD_1 = "\uE106"; // 森林赐福
    public static final String SKILL_IRON_EDGE_1 = "\uE107"; // 钢脊之怒
    public static final String SKILL_MEOWMERE_1 = "\uE108"; // 彩虹光弹
    public static final String SKILL_MEOWMERE_2 = "\uE109"; // 喵星乐章
    public static final String SKILL_BONE_SWORD_1 = "\uE10A"; // 骨裂斩
    public static final String SKILL_CLAYMORE_1 = "\uE10B"; // 回刃折返
    public static final String SKILL_NEPTUNE_GLAIVE_1 = "\uE10C"; // 潮汐印记
    public static final String SKILL_NEPTUNE_GLAIVE_2 = "\uE10D"; // 潮汐锚
    public static final String SKILL_TEMPLARS_BLADE_1 = "\uE10E"; // 誓约反斩
    public static final String SKILL_TEMPLARS_BLADE_2 = "\uE10F"; // 圣堂裁决
    public static final String SKILL_INSECT_HEAD_1 = "\uE110"; // 复眼架势
    public static final String SKILL_INSECT_HEAD_2 = "\uE111"; // 甲翼疾掠
    public static final String SKILL_OBSIDIAN_EDGE_1 = "\uE112"; // 玄刃共鸣
    public static final String SKILL_OBSIDIAN_EDGE_2 = "\uE113"; // 裂界一线
    public static final String SKILL_OSSIFIED_BLADE_1 = "\uE114"; // 断骨刻名
    public static final String SKILL_OSSIFIED_BLADE_2 = "\uE115"; // 白骨行刑
    public static final String SKILL_HOLY_BLADE_1 = "\uE116"; // 圣辉惩戒
    public static final String SKILL_HOLY_BLADE_2 = "\uE117"; // 晨曦圣域
    public static final String SKILL_TEMPERED_BROADSWORD_1 = "\uE118"; // 回炉淬火
    public static final String SKILL_TEMPERED_BROADSWORD_2 = "\uE119"; // 熔锻飞坯
    public static final String SKILL_YETI_TOOTH_1 = "\uE11A"; // 冻牙刻印
    public static final String SKILL_YETI_TOOTH_2 = "\uE11B"; // 冰脊裂地
    public static final String SKILL_STEEL_FALCHION_1 = "\uE11C"; // 疾锋刻线
    public static final String SKILL_STEEL_FALCHION_2 = "\uE11D"; // 斩迹回响
    public static final String SKILL_DARK_SWORD_1 = "\uE11E"; // 祭血斩
    public static final String SKILL_DARK_SWORD_2 = "\uE11F"; // 血月收割
    public static final String SKILL_LAVA_KATANA_1 = "\uE120"; // 熔铸刻印
    public static final String SKILL_LAVA_KATANA_2 = "\uE121"; // 熔潮回鸣
    public static final String SKILL_DRAGONTOOTH_CUTLASS_1 = "\uE122"; // 龙息突刺
    public static final String SKILL_DRAGONTOOTH_CUTLASS_2 = "\uE123"; // 龙息裁决
    public static final String SKILL_DWARF_SWORD_1 = "\uE124"; // 符文回能护斩
    public static final String SKILL_DWARF_SWORD_2 = "\uE125"; // 地脉堡垒·回震
    public static final String SKILL_GALAXY_SWORD_1 = "\uE126"; // 星轨裂星
    public static final String SKILL_GALAXY_SWORD_2 = "\uE127"; // 银河裁决
    public static final String SKILL_INFINITY_BLADE_1 = "\uE128"; // 奇点进化
    public static final String SKILL_INFINITY_BLADE_2 = "\uE129"; // 永恒坍缩
    public static final String SKILL_CARVING_KNIFE_1 = "\uE12A"; // 刻痕连刺
    public static final String SKILL_IRON_DIRK_1 = "\uE12B"; // 折影突刺
    public static final String SKILL_WIND_SPIRE_1 = "\uE12C"; // 风痕突刺
    public static final String SKILL_ELF_BLADE_1 = "\uE12D"; // 月露萤刃
    public static final String SKILL_BURGLARS_SHANK_1 = "\uE12E"; // 盗影割袋
    public static final String SKILL_CRYSTAL_DAGGER_1 = "\uE12F"; // 晶层刺击
    public static final String SKILL_SHADOW_DAGGER_1 = "\uE130"; // 影袭处决
    public static final String SKILL_BROKEN_TRIDENT_1 = "\uE131"; // 鱼获试刺
    public static final String SKILL_BROKEN_TRIDENT_2 = "\uE132"; // 渔潮回钩
    public static final String SKILL_WICKED_KRIS_1 = "\uE133"; // 蛇毒涟漪
    public static final String SKILL_WICKED_KRIS_2 = "\uE134"; // 蛇巢引爆
    public static final String SKILL_DWARF_DAGGER_1 = "\uE135"; // 符文突刺
    public static final String SKILL_DWARF_DAGGER_2 = "\uE136"; // 地脉疾行
    public static final String SKILL_DRAGONTOOTH_SHIV_1 = "\uE137"; // 龙息裂刺
    public static final String SKILL_DRAGONTOOTH_SHIV_2 = "\uE138"; // 龙息态
    public static final String SKILL_IRIDIUM_NEEDLE_1 = "\uE139"; // 三针连斩
    public static final String SKILL_IRIDIUM_NEEDLE_2 = "\uE13A"; // 铱辉狂热
    public static final String SKILL_GALAXY_DAGGER_1 = "\uE13B"; // 星轨裂刺
    public static final String SKILL_GALAXY_DAGGER_2 = "\uE13C"; // 星跃背刺
    public static final String SKILL_INFINITY_DAGGER_1 = "\uE13D"; // 奇点连刺
    public static final String SKILL_INFINITY_DAGGER_2 = "\uE13E"; // 奇点背刺
    public static final String SKILL_FEMUR_1 = "\uE13F"; // 震骨横砸
    
    public static final String SKILL_GALAXY_HAMMER_1 = "\uE140";
    public static final String SKILL_GALAXY_HAMMER_2 = "\uE141";
    public static final String SKILL_INFINITY_GAVEL_1 = "\uE142";
    public static final String SKILL_INFINITY_GAVEL_2 = "\uE143";

    public static final String SKILL_DRAGONTOOTH_CLUB_1 = "\uE14C";
    public static final String SKILL_DRAGONTOOTH_CLUB_2 = "\uE14D";
    public static final String SKILL_RAPIER_1 = "\uE14E";
    public static final String SKILL_THE_SLAMMER_1 = "\uE148";
    public static final String SKILL_THE_SLAMMER_2 = "\uE149";
    public static final String SKILL_DWARF_HAMMER_1 = "\uE14A";
    public static final String SKILL_DWARF_HAMMER_2 = "\uE14B";
    public static final String SKILL_LEAD_ROD_1 = "\uE146";
    public static final String SKILL_KUDGEL_1 = "\uE147";
    public static final String SKILL_WOOD_CLUB_1 = "\uE144";
    public static final String SKILL_WOOD_MALLET_1 = "\uE145";

    // 分隔线
    public static final String SEPARATOR = "════════════════════";
    
    /**
     * 创建带图标字体的组件
     */
    @SuppressWarnings("null")
    public static MutableComponent icon(String iconChar) {
        return Component.literal(iconChar)
                .withStyle(Style.EMPTY.withFont(WEAPON_ICONS_FONT));
    }
    
    /**
     * 创建带图标字体和颜色的组件
     */
    @SuppressWarnings("null")
    public static MutableComponent icon(String iconChar, ChatFormatting color) {
        return Component.literal(iconChar)
                .withStyle(Style.EMPTY.withFont(WEAPON_ICONS_FONT).withColor(color));
    }
    
    /**
     * 创建分隔线组件
     */
    public static MutableComponent separator() {
        return Component.literal(SEPARATOR).withStyle(ChatFormatting.DARK_GRAY);
    }
}
