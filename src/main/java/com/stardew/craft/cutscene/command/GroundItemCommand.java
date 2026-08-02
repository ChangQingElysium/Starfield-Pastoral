package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * ground_item: show a temporary client-side item display lying on the ground.
 * JSON: {"cmd":"ground_item", "id":"ruby", "item":"stardewcraft:ruby", "x":6.5, "y":24, "z":34.5}
 */
public class GroundItemCommand implements EventCommand {

    private static final List<Display.ItemDisplay> ACTIVE_DISPLAYS = new ArrayList<>();

    private final String id;
    private final String itemId;
    private final double x;
    private final double y;
    private final double z;
    private final float scale;
    private final float yaw;

    public GroundItemCommand(String id, String itemId, double x, double y, double z, float scale, float yaw) {
        this.id = id;
        this.itemId = itemId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.scale = scale;
        this.yaw = yaw;
    }

    @Override
    public void start(EventPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }

        Display.ItemDisplay display = createClientDisplay(
                level, id, itemId, x, y, z, scale, yaw);
        if (display != null) {
            ACTIVE_DISPLAYS.add(display);
        }
    }

    public static Display.ItemDisplay createClientDisplay(
            ClientLevel level,
            String id,
            String itemId,
            double x,
            double y,
            double z,
            float scale,
            float yaw
    ) {
        ResourceLocation loc = ResourceLocation.parse(itemId);
        Item item = BuiltInRegistries.ITEM.get(loc);
        if (item == net.minecraft.world.item.Items.AIR) {
            return null;
        }

        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:item_display");
        tag.put("Pos", doubleList(x, y + 0.055, z));
        tag.put("Rotation", floatList(yaw, 0.0F));
        tag.putFloat("shadow_radius", 0.0F);
        tag.putFloat("shadow_strength", 0.0F);
        tag.putFloat("view_range", 32.0F);

        CompoundTag itemTag = new CompoundTag();
        itemTag.putString("id", itemId);
        itemTag.putInt("count", 1);
        tag.put("item", itemTag);

        CompoundTag transform = new CompoundTag();
        transform.put("translation", floatList(0.0F, 0.0F, 0.0F));
        transform.put("left_rotation", floatList(0.70710677F, 0.0F, 0.0F, 0.70710677F));
        transform.put("scale", floatList(scale, scale, scale));
        transform.put("right_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        tag.put("transformation", transform);

        Entity entity = EntityType.loadEntityRecursive(tag, level, e -> e);
        if (!(entity instanceof Display.ItemDisplay display)) {
            return null;
        }

        int fakeId = -(("grounditem_" + id).hashCode() & 0x7FFFFFFF) - 1;
        display.setId(fakeId);
        level.addEntity(display);
        return display;
    }

    @Override
    public void tick(EventPlayer player) {
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public boolean isStateCommand() {
        return true;
    }

    @Override
    public void onSkip(EventPlayer player) {
        start(player);
    }

    public static void clearAll() {
        for (Display.ItemDisplay display : ACTIVE_DISPLAYS) {
            if (display != null && !display.isRemoved()) {
                display.discard();
            }
        }
        ACTIVE_DISPLAYS.clear();
    }

    private static ListTag doubleList(double... values) {
        ListTag list = new ListTag();
        for (double value : values) {
            list.add(DoubleTag.valueOf(value));
        }
        return list;
    }

    private static ListTag floatList(float... values) {
        ListTag list = new ListTag();
        for (float value : values) {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }
}
