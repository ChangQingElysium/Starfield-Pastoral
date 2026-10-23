package com.stardew.craft.platform.fabric.client.event;

import com.stardew.craft.platform.fabric.event.ModBusEvent;
import com.stardew.craft.platform.fabric.client.decoration.IItemDecorator;
import com.stardew.craft.platform.fabric.event.Event;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ItemLike;

@Environment(EnvType.CLIENT)
public final class RegisterItemDecorationsEvent extends Event implements ModBusEvent {
    private final Map<Item, List<IItemDecorator>> decorators;
    public RegisterItemDecorationsEvent(Map<Item, List<IItemDecorator>> decorators) { this.decorators = Objects.requireNonNull(decorators); }
    public void register(ItemLike item, IItemDecorator decorator) {
        decorators.computeIfAbsent(Objects.requireNonNull(item).asItem(), ignored -> new ArrayList<>()).add(Objects.requireNonNull(decorator));
    }
}
