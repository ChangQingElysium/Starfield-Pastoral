package com.stardew.craft.cutscene.command;

import com.stardew.craft.cutscene.runtime.EventPlayer;
import com.stardew.craft.cutscene.runtime.EventPlayerActorEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * eat_item: show a cutscene player actor eating one registered item.
 * JSON: {"cmd":"eat_item", "actor":"fake_player", "item":"stardewcraft:cookie", "ticks":40}
 */
public final class EatItemCommand implements EventCommand {
    private final String actorTag;
    private final String itemId;
    private final int durationTicks;
    private ItemStack previousItem = ItemStack.EMPTY;
    private int elapsed;

    public EatItemCommand(String actorTag, String itemId, int durationTicks) {
        this.actorTag = actorTag;
        this.itemId = itemId;
        this.durationTicks = Math.max(1, durationTicks);
    }

    @Override
    public void start(EventPlayer player) {
        elapsed = 0;
        Mob actor = player.getActor(actorTag);
        if (!(actor instanceof EventPlayerActorEntity playerActor)) {
            elapsed = durationTicks;
            return;
        }
        previousItem = playerActor.getMainHandItem().copy();
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        playerActor.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
        playerActor.setEatingItem(true);
    }

    @Override
    public void tick(EventPlayer player) {
        elapsed++;
        if (elapsed >= durationTicks) {
            cleanup(player);
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
        Mob actor = player.getActor(actorTag);
        if (actor instanceof EventPlayerActorEntity playerActor) {
            playerActor.setEatingItem(false);
            playerActor.setItemSlot(EquipmentSlot.MAINHAND, previousItem);
        }
    }
}
