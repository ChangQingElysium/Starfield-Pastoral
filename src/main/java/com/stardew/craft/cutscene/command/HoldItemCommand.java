package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.Mob;

/**
 * hold_item: display an item floating above an actor's head (like SDV's itemAboveHead).
 * JSON: {"cmd":"hold_item", "actor":"fake_player", "item":"stardewcraft:fishing_rod", "ticks":60}
 * Optional: "offset_y": 0.5 (additional height above actor head)
 */
public class HoldItemCommand implements EventCommand {

    private static final double BASE_OFFSET_ABOVE_HEAD = 1.25;

    private final String actorTag;
    private final String itemId;
    private final int durationTicks;
    private final float offsetY;

    private Display.ItemDisplay displayEntity;
    private int elapsed = 0;

    public HoldItemCommand(String actorTag, String itemId, int durationTicks, float offsetY) {
        this.actorTag = actorTag;
        this.itemId = itemId;
        this.durationTicks = durationTicks;
        this.offsetY = offsetY;
    }

    @Override
    public void start(EventPlayer player) {
        elapsed = 0;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        // Resolve the item
        ResourceLocation loc = new ResourceLocation(itemId);
        Item item = BuiltInRegistries.ITEM.get(loc);

        // Get actor position
        Mob actor = player.getActor(actorTag);
        if (actor == null) return;

        // Trigger arms-up pose if actor is a player actor
        if (actor instanceof EventPlayerActorEntity playerActor) {
            playerActor.setHoldingItemAboveHead(true);
        }

        double ax = actor.getX();
        // Position item above the raised hands; lower offsets clip into actor heads.
        double ay = actor.getY() + actor.getBbHeight() + BASE_OFFSET_ABOVE_HEAD + offsetY;
        double az = actor.getZ();

        // Create client-side item display entity
        displayEntity = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
        displayEntity.setPos(ax, ay, az);

        // Set the item via slot access (slot 0 = the displayed item)
        displayEntity.getSlot(0).set(new ItemStack(item));

        // Assign a fake entity ID
        int fakeId = -(("holditem_" + actorTag).hashCode() & 0x7FFFFFFF) - 1;
        displayEntity.setId(fakeId);

        level.addEntity(displayEntity);
    }

    @Override
    public void tick(EventPlayer player) {
        elapsed++;

        if (elapsed >= durationTicks) {
            cleanup(player);
            return;
        }

        // Follow the actor and bob up/down
        if (displayEntity != null && !displayEntity.isRemoved()) {
            Mob actor = player.getActor(actorTag);
            if (actor != null) {
                double baseY = actor.getY() + actor.getBbHeight() + BASE_OFFSET_ABOVE_HEAD + offsetY;
                double bob = Math.sin(elapsed * 0.15) * 0.05;
                displayEntity.setPos(actor.getX(), baseY + bob, actor.getZ());
            }
        }
    }

    @Override
    public boolean isComplete() {
        return elapsed >= durationTicks;
    }

    @Override
    public void onSkip(EventPlayer player) {
        cleanup(player);
        elapsed = durationTicks;
    }

    private void cleanup(EventPlayer player) {
        // Reset arm pose
        Mob actor = player.getActor(actorTag);
        if (actor instanceof EventPlayerActorEntity playerActor) {
            playerActor.setHoldingItemAboveHead(false);
        }
        if (displayEntity != null && !displayEntity.isRemoved()) {
            displayEntity.discard();
            displayEntity = null;
        }
    }
}
