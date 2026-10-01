package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.stardew.craft.Config;
import com.stardew.craft.api.v1.client.StardewCalendarDate;
import com.stardew.craft.event.DailyInfoSyncEvents;
import com.stardew.craft.fishing.server.FishingSession;
import com.stardew.craft.fishing.server.FishingSessionManager;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.Map;
import java.util.UUID;

@GameTestHolder("stardewcraft_fishing_rules")
@PrefixGameTestTemplate(false)
public final class FishingSkipAndBirthdayGameTests {
    @GameTest(templateNamespace = "stardewcraft_fishing_rules", template = "ring_utilities")
    @SuppressWarnings("unchecked")
    public static void skippedMinigameCollectsOnlyRolledChests(GameTestHelper h) throws Exception {
        // Vanilla GameTest worlds omit the custom mining dimension used by player-data sync.
        var levelsField = net.minecraft.server.MinecraftServer.class.getDeclaredField("levels");
        levelsField.setAccessible(true);
        var levels = (Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
                net.minecraft.server.level.ServerLevel>) levelsField.get(h.getLevel().getServer());
        var mining = com.stardew.craft.core.ModMiningDimensions.STARDEW_MINING;
        var previousLevel = levels.put(mining, h.getLevel());
        boolean previous = Config.ENABLE_FISHING_MINIGAME.get();
        Config.ENABLE_FISHING_MINIGAME.set(false);
        try {
            verifyCatch(h, false, false, false);
            verifyCatch(h, true, false, false);
            verifyCatch(h, true, true, false);
            verifyCatch(h, false, false, true);
        } finally {
            Config.ENABLE_FISHING_MINIGAME.set(previous);
            if (previousLevel == null) levels.remove(mining); else levels.put(mining, previousLevel);
        }
        h.succeed();
    }

    @SuppressWarnings("unchecked")
    private static void verifyCatch(GameTestHelper h, boolean treasure, boolean golden, boolean junk) throws Exception {
        var player = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "Fishing regression"));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.FISHING_ROD.get()));
        player.setPos(h.absolutePos(new BlockPos(3, 2, 3)).getCenter());
        var manager = FishingSessionManager.get(h.getLevel().getServer());
        var session = new FishingSession(UUID.randomUUID(), h.absolutePos(new BlockPos(4, 1, 4)), 5, 0);
        manager.prepareUse(player, UUID.randomUUID());
        Map<UUID, FishingSession> sessions = (Map<UUID, FishingSession>) field(manager, "sessionsByPlayer");
        Map<UUID, ?> pending = (Map<UUID, ?>) field(manager, "pendingTreasureByPlayer");
        try {
            set(session, "state", FishingSession.State.BITE_READY);
            set(session, "plannedCatch", new ItemStack(junk ? ModItems.TRASH.get() : ModItems.SARDINE.get()));
            set(session, "difficulty", 30);
            set(session, "hasTreasure", treasure);
            set(session, "goldenTreasure", golden);
            set(session, "skipMinigame", junk);
            sessions.put(player.getUUID(), session);
            h.assertTrue(manager.tryStartMinigame(player), "Skipped catch did not start");
            h.assertTrue(session.state() == FishingSession.State.DONE, "Catch did not settle");
            h.assertTrue(player.getInventory().countItem(junk ? ModItems.TRASH.get() : ModItems.SARDINE.get()) == 1,
                    "Catch was lost or duplicated");
            h.assertTrue(pending.containsKey(player.getUUID()) == treasure, "Rolled chest was lost or invented");
            if (treasure) {
                h.assertTrue((boolean) field(pending.get(player.getUUID()), "golden") == golden, "Chest color changed");
                h.assertTrue(!session.treasureLoot().isEmpty(), "Chest loot was not generated");
            }
            h.assertTrue(!manager.tryStartMinigame(player), "Completed catch could settle twice");
        } finally {
            manager.cancel(player);
            pending.remove(player.getUUID());
            player.discard();
        }
    }

    @GameTest(templateNamespace = "stardewcraft_fishing_rules", template = "ring_utilities")
    public static void krobusBirthdayReachesCalendarAndGiftRules(GameTestHelper h) throws Exception {
        var root = NpcDataRegistry.events().get("npc_birthdays");
        var krobus = new ResourceLocation("stardewcraft:krobus");
        h.assertTrue(DailyInfoSyncEvents.birthdaysToday(root, new StardewCalendarDate(1, 3, 1)).contains(krobus),
                "Shipped birthday registry omits Krobus on Winter 1");
        h.assertTrue(!DailyInfoSyncEvents.birthdaysToday(root, new StardewCalendarDate(1, 3, 2)).contains(krobus),
                "Krobus birthday leaked into the next day");
        Class<?> dayType = Class.forName(NpcInteractionService.class.getName() + "$DayContext");
        var constructor = dayType.getDeclaredConstructor(int.class, int.class, int.class,
                String.class, String.class, String.class);
        constructor.setAccessible(true);
        var check = NpcInteractionService.class.getDeclaredMethod("isNpcBirthday", String.class, dayType);
        check.setAccessible(true);
        Object today = constructor.newInstance(1, 85, 12, "winter", "Mon", "sunny");
        h.assertTrue((boolean) check.invoke(null, "krobus", today), "Actual birthday gift rule rejects Krobus");
        h.succeed();
    }

    private static Object field(Object object, String name) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void set(Object object, String name, Object value) throws Exception {
        var field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
