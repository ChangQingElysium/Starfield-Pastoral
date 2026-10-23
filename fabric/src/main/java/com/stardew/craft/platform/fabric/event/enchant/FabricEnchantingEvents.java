package com.stardew.craft.platform.fabric.event.enchant;

import com.stardew.craft.platform.fabric.event.IEventBus;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.ToIntFunction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class FabricEnchantingEvents {
    private static IEventBus events;
    private FabricEnchantingEvents() {}
    public static synchronized void install(IEventBus bus) {
        Objects.requireNonNull(bus, "bus");
        if (events != null && events != bus) throw new IllegalStateException("Enchanting events already installed");
        events = bus;
    }

    public static boolean anvil(ItemStack left, ItemStack right, String name, Player player,
            Consumer<ItemStack> output, IntConsumer cost, IntConsumer materials) {
        return anvil(bus(), left, right, name, player, output, cost, materials);
    }
    static boolean anvil(IEventBus bus, ItemStack left, ItemStack right, String name, Player player,
            Consumer<ItemStack> output, IntConsumer cost, IntConsumer materials) {
        if (left.isEmpty()) return true;
        // Neo 21.1 dispatches before it adds either stack's REPAIR_COST: baseCost is zero.
        var event = bus.post(new AnvilUpdateEvent(left, right, name, 0, player));
        if (event.isCanceled()) {
            output.accept(ItemStack.EMPTY); cost.accept(0); materials.accept(0); return false;
        }
        if (event.getOutput().isEmpty()) return true;
        output.accept(event.getOutput());
        cost.accept((int) Math.clamp(event.getCost(), 0L, Integer.MAX_VALUE));
        materials.accept(event.getMaterialCost());
        return false;
    }

    public static PlaceResult place(ItemStack top, ItemStack bottom) { return place(bus(), top, bottom); }
    static PlaceResult place(IEventBus bus, ItemStack top, ItemStack bottom) {
        var event = bus.post(new GrindstoneEvent.OnPlaceItem(top, bottom, -1));
        if (event.isCanceled()) return new PlaceResult(ItemStack.EMPTY, -1, false);
        if (event.getOutput().isEmpty()) return new PlaceResult(ItemStack.EMPTY, Integer.MIN_VALUE, true);
        return new PlaceResult(event.getOutput(), event.getXp(), false);
    }
    public record PlaceResult(ItemStack output, int experience, boolean vanilla) {}

    public static void take(Container inputs, ContainerLevelAccess access, Player player, ToIntFunction<Level> experience) {
        access.execute((level, pos) -> take(bus(), inputs, access, player, experience.applyAsInt(level),
                xp -> { if (level instanceof ServerLevel server) ExperienceOrb.award(server, Vec3.atCenterOf(pos), xp); },
                () -> level.levelEvent(1042, pos, 0)));
    }
    static void take(IEventBus bus, Container inputs, ContainerLevelAccess access, Player player, int xp,
            IntConsumer awardExperience, Runnable sound) {
        var event = bus.post(new GrindstoneEvent.OnTakeItem(access, player, inputs.getItem(0), inputs.getItem(1), xp));
        if (event.isCanceled()) return;
        awardExperience.accept(event.getXp());
        sound.run();
        inputs.setItem(0, event.getNewTopItem());
        inputs.setItem(1, event.getNewBottomItem());
        inputs.setChanged();
    }

    public static void enchanted(Player player, ItemStack stack, List<EnchantmentInstance> enchantments) {
        if (!player.level().isClientSide()) enchanted(bus(), player, stack, enchantments);
    }
    static void enchanted(IEventBus bus, Player player, ItemStack stack, List<EnchantmentInstance> enchantments) {
        bus.post(new PlayerEnchantItemEvent(player, stack, enchantments));
    }
    private static IEventBus bus() {
        return Objects.requireNonNull(events, "Fabric enchanting events have not been installed");
    }
}
