package com.stardew.craft.item.bomb;

import com.stardew.craft.item.ModItems;
import net.minecraft.world.item.Item;
import com.stardew.craft.port.net.neoforged.neoforge.registries.DeferredItem;

import java.util.function.Supplier;

/**
 * SDV 三种炸弹的参数定义。
 *
 * <p>TemporaryAnimatedSprite 的原版半径为 3、5、7 格；
 * GameLocation.explode 的锄地半径为其一半（向下取整）。</p>
 */
public enum BombType {
    CHERRY_BOMB("cherry_bomb", 3, 2400, () -> ModItems.CHERRY_BOMB),
    BOMB("bomb_item", 5, 2400, () -> ModItems.BOMB_ITEM),
    MEGA_BOMB("mega_bomb", 7, 2400, () -> ModItems.MEGA_BOMB);

    private final String id;
    private final int radius;
    private final int fuseTimeMs;
    private final Supplier<DeferredItem<Item>> itemSupplier;

    BombType(String id, int radius, int fuseTimeMs, Supplier<DeferredItem<Item>> itemSupplier) {
        this.id = id;
        this.radius = radius;
        this.fuseTimeMs = fuseTimeMs;
        this.itemSupplier = itemSupplier;
    }

    public String getId() { return id; }

    /** SDV 原版半径（用于伤害计算） */
    public int getRadius() { return radius; }

    /** 保留调用兼容性；一格对应一个 MC 方块，不再缩小范围。 */
    public float getScaledRadius() { return radius; }

    /** Fuse time in game ticks (20 ticks/sec). SDV uses 2400ms = 48 ticks. */
    public int getFuseTicks() { return fuseTimeMs / 50; }

    /** Monster min damage = radius * 6 */
    public int getMinDamage() { return radius * 6; }

    /** Monster max damage = radius * 8 */
    public int getMaxDamage() { return radius * 8; }

    /** Player self-damage = radius * 3 */
    public int getPlayerDamage() { return radius * 3; }

    /** Screen shake duration in ticks: (300 + radius * 100) ms → ticks */
    public int getShakeDurationTicks() { return (300 + radius * 100) / 50; }

    public Item getItem() { return itemSupplier.get().get(); }

    public static BombType fromId(String id) {
        for (BombType t : values()) {
            if (t.id.equals(id)) return t;
        }
        return CHERRY_BOMB;
    }

    public static BombType fromOrdinal(int ordinal) {
        BombType[] vals = values();
        return (ordinal >= 0 && ordinal < vals.length) ? vals[ordinal] : CHERRY_BOMB;
    }
}
