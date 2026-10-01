package com.stardew.craft.port.event;

import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import com.stardew.craft.port.net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.CanContinueSleepingEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.level.block.CropGrowEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.LevelTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.PlayerTickEvent;
import com.stardew.craft.port.net.neoforged.neoforge.event.tick.ServerTickEvent;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.event.entity.player.SleepingTimeCheckEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * PORT(1.20.1): re-posts Forge 1.20.1 events as their NeoForge 21.1 shims (same bus, same firing point) and copies
 * results back. Installed once from the mod constructor via {@link #install(IEventBus)}; client-only bridges live in
 * {@link PortClientEventBridges}. Pre == {@code Phase.START}, Post == {@code Phase.END}.
 */
public final class PortEventBridges {
    private static boolean installed;

    private PortEventBridges() {}

    public static synchronized void install(IEventBus modBus) {
        if (installed) return;
        installed = true;
        IEventBus forge = MinecraftForge.EVENT_BUS;

        forge.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.class, event -> forge.post(
                event.phase == TickEvent.Phase.START
                        ? new ServerTickEvent.Pre(event::haveTime, event.getServer())
                        : new ServerTickEvent.Post(event::haveTime, event.getServer())));
        forge.addListener(EventPriority.NORMAL, false, TickEvent.LevelTickEvent.class, event -> forge.post(
                event.phase == TickEvent.Phase.START
                        ? new LevelTickEvent.Pre(event::haveTime, event.level)
                        : new LevelTickEvent.Post(event::haveTime, event.level)));
        forge.addListener(EventPriority.NORMAL, false, TickEvent.PlayerTickEvent.class, event -> forge.post(
                event.phase == TickEvent.Phase.START
                        ? new PlayerTickEvent.Pre(event.player)
                        : new PlayerTickEvent.Post(event.player)));

        forge.addListener(EventPriority.NORMAL, false, EntityItemPickupEvent.class, PortEventBridges::onItemPickupPre);
        forge.addListener(EventPriority.NORMAL, false, PlayerEvent.ItemPickupEvent.class, event -> forge.post(
                new ItemEntityPickupEvent.Post(event.getEntity(), event.getOriginalEntity(), event.getStack())));

        forge.addListener(EventPriority.NORMAL, false, SleepingTimeCheckEvent.class, PortEventBridges::onSleepingTimeCheck);
        forge.addListener(EventPriority.NORMAL, false, SleepingLocationCheckEvent.class, PortEventBridges::onSleepingLocationCheck);

        forge.addListener(EventPriority.NORMAL, false, MobSpawnEvent.FinalizeSpawn.class, event -> {
            if (forge.post(new FinalizeSpawnEvent(event))) event.setCanceled(true);
        });

        forge.addListener(EventPriority.NORMAL, false, BlockEvent.CropGrowEvent.Pre.class, PortEventBridges::onCropGrowPre);
        forge.addListener(EventPriority.NORMAL, false, BlockEvent.CropGrowEvent.Post.class, event -> forge.post(
                new CropGrowEvent.Post(event.getLevel(), event.getPos(), event.getOriginalState(), event.getState())));
        forge.addListener(EventPriority.NORMAL, false, SaplingGrowTreeEvent.class, PortEventBridges::onSaplingGrow);

        // NeoForge fires RegisterBrewingRecipesEvent on the game bus when brewing is (re)built; Forge has one
        // global registry, filled once during common setup.
        modBus.addListener(EventPriority.NORMAL, false, FMLCommonSetupEvent.class,
                event -> event.enqueueWork(() -> forge.post(new RegisterBrewingRecipesEvent())));

        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> PortClientEventBridges.install(modBus));
    }

    private static void onItemPickupPre(EntityItemPickupEvent event) {
        ItemEntityPickupEvent.Pre pre = new ItemEntityPickupEvent.Pre(event.getEntity(), event.getItem());
        if (event.getResult() == Event.Result.ALLOW) pre.setCanPickup(TriState.TRUE);
        MinecraftForge.EVENT_BUS.post(pre);
        // PORT(1.20.1): only FALSE is mapped. Forge's ALLOW means "already handled" and skips the inventory insert,
        // which is not NeoForge's TRUE (force the normal pickup); the mod never sets TRUE.
        if (pre.canPickup().isFalse()) event.setCanceled(true);
    }

    private static void onSleepingTimeCheck(SleepingTimeCheckEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide || !player.isSleeping()) return; // also fired by startSleepInBed
        Player.BedSleepingProblem problem = player.level().isDay() ? Player.BedSleepingProblem.NOT_POSSIBLE_NOW : null;
        applyContinueSleeping(event, player, problem);
    }

    private static void onSleepingLocationCheck(SleepingLocationCheckEvent event) {
        LivingEntity sleeper = event.getEntity();
        if (sleeper.level().isClientSide) return;
        BlockPos pos = event.getSleepingLocation();
        boolean hasBed = sleeper.level().getBlockState(pos).isBed(sleeper.level(), pos, sleeper);
        applyContinueSleeping(event, sleeper, hasBed ? null : Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
    }

    private static void applyContinueSleeping(Event forgeEvent, LivingEntity sleeper, @Nullable Player.BedSleepingProblem problem) {
        CanContinueSleepingEvent event = new CanContinueSleepingEvent(sleeper, problem);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.mayContinueSleeping() != (problem == null)) {
            forgeEvent.setResult(event.mayContinueSleeping() ? Event.Result.ALLOW : Event.Result.DENY);
        }
    }

    private static void onCropGrowPre(BlockEvent.CropGrowEvent.Pre event) {
        CropGrowEvent.Pre pre = new CropGrowEvent.Pre(event.getLevel(), event.getPos(), event.getState());
        pre.setResult(event.getResult()); // Forge result slot carries GROW/DEFAULT/DO_NOT_GROW
        MinecraftForge.EVENT_BUS.post(pre);
        event.setResult(pre.getResult());
    }

    private static void onSaplingGrow(SaplingGrowTreeEvent event) {
        if (event.getResult() == Event.Result.DENY) return;
        BlockGrowFeatureEvent grow = new BlockGrowFeatureEvent(event.getLevel(), event.getRandomSource(), event.getPos(), event.getFeature());
        if (MinecraftForge.EVENT_BUS.post(grow)) {
            event.setResult(Event.Result.DENY);
        } else {
            event.setFeature(grow.getFeature());
        }
    }
}
