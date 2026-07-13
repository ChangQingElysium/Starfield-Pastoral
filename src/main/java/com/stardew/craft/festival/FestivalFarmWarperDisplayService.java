package com.stardew.craft.festival;

import com.stardew.craft.StardewCraft;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class FestivalFarmWarperDisplayService {
    private static final double DISPLAY_SCAN_RADIUS = 2.0D;

    private FestivalFarmWarperDisplayService() {
    }

    public static void spawn(ServerLevel level, String markerTag, Vec3 position) {
        if (level == null || markerTag == null || markerTag.isBlank() || position == null
            || hasDisplay(level, markerTag, position)) {
            return;
        }
        String snbt = "{id:\"minecraft:item_display\",NoGravity:1b,Invulnerable:1b,Silent:1b,Tags:[\""
            + markerTag + "\"],Pos:[" + position.x + "d," + position.y + "d," + position.z + "d],"
            + "item:{count:1,id:\"stardewcraft:warp_totem_farm\"},"
            + "transformation:{left_rotation:[0.0f,0.0f,0.0f,1.0f],right_rotation:[0.0f,0.0f,0.0f,1.0f],"
            + "scale:[1.6f,1.6f,1.6f],translation:[0.0f,0.0f,0.0f]}}";
        try {
            Entity entity = EntityType.loadEntityRecursive(TagParser.parseTag(snbt), level, value -> value);
            if (entity != null) {
                level.addFreshEntity(entity);
            }
        } catch (Exception exception) {
            StardewCraft.LOGGER.error("[FESTIVAL_WARPER] Failed to spawn farm warper item display", exception);
        }
    }

    public static void remove(ServerLevel level, String markerTag, Vec3 position) {
        if (level == null || markerTag == null || markerTag.isBlank() || position == null) {
            return;
        }
        for (Display.ItemDisplay display : level.getEntitiesOfClass(
            Display.ItemDisplay.class,
            displayBounds(position),
            entity -> entity.getTags().contains(markerTag)
        )) {
            display.discard();
        }
    }

    private static boolean hasDisplay(ServerLevel level, String markerTag, Vec3 position) {
        if (level == null || markerTag == null || markerTag.isBlank() || position == null) {
            return false;
        }
        return !level.getEntitiesOfClass(
            Display.ItemDisplay.class,
            displayBounds(position),
            entity -> entity.getTags().contains(markerTag)
        ).isEmpty();
    }

    private static AABB displayBounds(Vec3 position) {
        return new AABB(
            position.x - DISPLAY_SCAN_RADIUS,
            position.y - DISPLAY_SCAN_RADIUS,
            position.z - DISPLAY_SCAN_RADIUS,
            position.x + DISPLAY_SCAN_RADIUS,
            position.y + DISPLAY_SCAN_RADIUS,
            position.z + DISPLAY_SCAN_RADIUS
        );
    }
}
