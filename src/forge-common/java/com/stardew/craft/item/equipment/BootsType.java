package com.stardew.craft.item.equipment;

/**
 * SDV 全部战斗相关靴子定义
 * 精确复刻 Boots.json 数据：名称/防御/免疫/价格
 */
public enum BootsType {
    SNEAKERS(504, 1, 0, 50),
    RUBBER_BOOTS(505, 0, 1, 50),
    LEATHER_BOOTS(506, 1, 1, 50),
    WORK_BOOTS(507, 2, 0, 50),
    COMBAT_BOOTS(508, 3, 0, 150),
    TUNDRA_BOOTS(509, 2, 1, 150),
    THERMAL_BOOTS(510, 1, 2, 50),
    DARK_BOOTS(511, 4, 2, 250),
    FIREWALKER_BOOTS(512, 3, 3, 250),
    GENIE_SHOES(513, 1, 6, 250),
    SPACE_BOOTS(514, 4, 4, 450),
    COWBOY_BOOTS(515, 2, 2, 250),
    LEPRECHAUN_SHOES(806, 2, 1, 250),
    CINDERCLOWN_SHOES(853, 6, 5, 1000),
    MERMAID_BOOTS(854, 5, 8, 1000),
    DRAGONSCALE_BOOTS(855, 7, 0, 1000),
    CRYSTAL_SHOES(878, 3, 5, 1000);

    private final int sdvId;
    private final int defense;
    private final int immunity;
    private final int price;

    BootsType(int sdvId, int defense, int immunity, int price) {
        this.sdvId = sdvId;
        this.defense = defense;
        this.immunity = immunity;
        this.price = price;
    }

    public int getSdvId() { return sdvId; }
    public int getDefense() { return defense; }
    public int getImmunity() { return immunity; }
    public int getPrice() { return price; }
}
