package com.stardew.craft.port.net.minecraft.world.item.component;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.FireworkRocketItem;

/**
 * 1.21 {@code minecraft:firework_explosion}; stored in the vanilla 1.20.1 explosion compound
 * ({@code Type, Colors, FadeColors, Trail, Flicker}).
 */
public record FireworkExplosion(Shape shape, IntList colors, IntList fadeColors, boolean hasTrail, boolean hasTwinkle) {
    public static final FireworkExplosion DEFAULT = new FireworkExplosion(Shape.SMALL_BALL, IntList.of(), IntList.of(), false, false);

    public enum Shape {
        SMALL_BALL(FireworkRocketItem.Shape.SMALL_BALL),
        LARGE_BALL(FireworkRocketItem.Shape.LARGE_BALL),
        STAR(FireworkRocketItem.Shape.STAR),
        CREEPER(FireworkRocketItem.Shape.CREEPER),
        BURST(FireworkRocketItem.Shape.BURST);

        private final FireworkRocketItem.Shape vanilla;

        Shape(FireworkRocketItem.Shape vanilla) {
            this.vanilla = vanilla;
        }

        public int getId() {
            return vanilla.getId();
        }

        public String getSerializedName() {
            return vanilla.getName();
        }

        /** PORT(1.20.1): the vanilla shape this maps to. */
        public FireworkRocketItem.Shape portVanilla() {
            return vanilla;
        }

        public static Shape byId(int id) {
            for (Shape shape : values()) {
                if (shape.getId() == id) {
                    return shape;
                }
            }
            return SMALL_BALL;
        }
    }

    /** PORT(1.20.1): vanilla explosion compound. */
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        shape.portVanilla().save(tag);
        tag.putIntArray(FireworkRocketItem.TAG_EXPLOSION_COLORS, colors.toIntArray());
        tag.putIntArray(FireworkRocketItem.TAG_EXPLOSION_FADECOLORS, fadeColors.toIntArray());
        if (hasTrail) {
            tag.putBoolean(FireworkRocketItem.TAG_EXPLOSION_TRAIL, true);
        }
        if (hasTwinkle) {
            tag.putBoolean(FireworkRocketItem.TAG_EXPLOSION_FLICKER, true);
        }
        return tag;
    }

    /** PORT(1.20.1): inverse of {@link #toTag()}. */
    public static FireworkExplosion fromTag(CompoundTag tag) {
        return new FireworkExplosion(
                Shape.byId(tag.getByte(FireworkRocketItem.TAG_EXPLOSION_TYPE)),
                new IntArrayList(tag.getIntArray(FireworkRocketItem.TAG_EXPLOSION_COLORS)),
                new IntArrayList(tag.getIntArray(FireworkRocketItem.TAG_EXPLOSION_FADECOLORS)),
                tag.getBoolean(FireworkRocketItem.TAG_EXPLOSION_TRAIL),
                tag.getBoolean(FireworkRocketItem.TAG_EXPLOSION_FLICKER));
    }
}
