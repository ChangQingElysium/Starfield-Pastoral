package com.stardew.craft.gametest;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonParser;
import com.stardew.craft.api.v1.client.StardewCalendarDate;
import com.stardew.craft.api.v1.client.StardewClientConstructionProgress;
import com.stardew.craft.api.v1.client.StardewClientDailyInfo;
import com.stardew.craft.api.v1.client.StardewConstructionOrderSnapshot;
import com.stardew.craft.api.v1.client.StardewConstructionProgressSnapshot;
import com.stardew.craft.api.v1.client.StardewDailyInfoSnapshot;
import com.stardew.craft.api.v1.client.StardewToolUpgradeSnapshot;
import com.stardew.craft.api.v1.client.StardewQueenOfSauceSnapshot;
import com.stardew.craft.block.tv.TVChannelData;
import com.stardew.craft.api.v1.internal.client.StardewConstructionProgressCache;
import com.stardew.craft.api.v1.internal.client.StardewDailyInfoCache;
import com.stardew.craft.building.runtime.BuildingRecord;
import com.stardew.craft.building.runtime.BuildingWorldData;
import com.stardew.craft.building.runtime.PrefabDefinitions;
import com.stardew.craft.event.BuildingConstructionProgressSyncEvents;
import com.stardew.craft.event.DailyInfoSyncEvents;
import com.stardew.craft.farm.FarmInstanceRegistry;
import com.stardew.craft.farm.FarmType;
import com.stardew.craft.network.DailyInfoSyncPayload;
import com.stardew.craft.network.payload.BuildingConstructionProgressSyncPayload;
import com.stardew.craft.player.PlayerStardewData;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@GameTestHolder("stardewcraft_daily_info")
@PrefixGameTestTemplate(false)
public final class DailyInfoApiGameTests {
    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void personalToolsStaySeparateAcrossYearBoundary(GameTestHelper h) {
        var date = new StardewCalendarDate(1, 3, 28);
        var first = new PlayerStardewData(UUID.randomUUID());
        var second = new PlayerStardewData(UUID.randomUUID());
        first.setToolBeingUpgraded("stardewcraft:iridium_axe");
        first.setDaysLeftForToolUpgrade(2);
        second.setToolBeingUpgraded("stardewcraft:copper_pickaxe");
        second.setDaysLeftForToolUpgrade(0);
        StardewToolUpgradeSnapshot a = DailyInfoSyncEvents.toolUpgrade(first, date).orElseThrow();
        var b = DailyInfoSyncEvents.toolUpgrade(second, date).orElseThrow();
        h.assertTrue(a.expectedReadyDate().equals(new StardewCalendarDate(2, 0, 2)), "Upgrade date did not roll year");
        h.assertTrue(!a.readyForPickup() && b.readyForPickup(), "Players shared tool state");
        h.assertTrue(a.resultItemId().getPath().equals("iridium_axe") && b.resultItemId().getPath().equals("copper_pickaxe"), "Wrong result tool");
        second.setToolBeingUpgraded("");
        h.assertTrue(DailyInfoSyncEvents.toolUpgrade(second, date).isEmpty(), "Collected tool still shown");
        first.setDaysLeftForToolUpgrade(1);
        h.assertTrue(DailyInfoSyncEvents.toolUpgrade(first, date.plusDays(1)).orElseThrow().expectedReadyDate()
                .equals(a.expectedReadyDate()), "Ready date drifted after overnight settlement");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void packetRoundTripAndReconnectClear(GameTestHelper h) {
        var date = new StardewCalendarDate(4, 2, 9);
        var first = info(UUID.randomUUID(), date, -.08, Optional.empty());
        var second = info(UUID.randomUUID(), date, .08, Optional.of(new StardewToolUpgradeSnapshot(
                new ResourceLocation("stardewcraft:iridium_axe"), 1, date.plusDays(1))));
        var unwatched = withCooking(first, new StardewQueenOfSauceSnapshot("stir_fry", false, false, false));
        var watched = withCooking(second, new StardewQueenOfSauceSnapshot("coleslaw", true, true, true));
        StardewDailyInfoCache.clear();
        try {
            h.assertTrue(StardewClientDailyInfo.current().isEmpty(), "Pre-sync data invented");
            for (var source : List.of(first, unwatched, watched, second)) {
                var buf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    DailyInfoSyncPayload.CODEC.encode(buf, new DailyInfoSyncPayload(source));
                    var decoded = DailyInfoSyncPayload.CODEC.decode(buf).snapshot();
                    h.assertTrue(source.equals(decoded) && buf.readableBytes() == 0, "Snapshot codec lost fields");
                    StardewDailyInfoCache.replace(decoded);
                    h.assertTrue(StardewClientDailyInfo.current().orElseThrow().equals(source), "Snapshot was merged with old player");
                } finally { buf.release(); }
            }
        } finally { StardewDailyInfoCache.clear(); }
        h.assertTrue(StardewClientDailyInfo.current().isEmpty(), "Disconnected data leaked");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void constructionProgressIsPermissionScopedAndPacketSafe(GameTestHelper h) {
        var level = h.getLevel();
        var farms = FarmInstanceRegistry.get(level.getServer());
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID outsider = UUID.randomUUID();
        var farm = farms.createFarm(owner, "RobinApiOwner", "Robin API", FarmType.STANDARD);
        h.assertTrue(farms.addMember(owner, member), "Could not create construction API farm member");
        var ownerPlayer = FakePlayerFactory.get(level, new GameProfile(owner, "RobinApiOwner"));
        var memberPlayer = FakePlayerFactory.get(level, new GameProfile(member, "RobinApiMember"));
        var outsiderPlayer = FakePlayerFactory.get(level, new GameProfile(outsider, "RobinApiOutsider"));
        var data = BuildingWorldData.get(level.getServer());
        var construction = buildingRecord(farm.getInstanceId(), farm.getSlotIndex(), PrefabDefinitions.COOP,
                h.absolutePos(new BlockPos(4, 1, 4)), level.dimension().location());
        try {
            UUID constructionPermit = UUID.randomUUID();
            data.recordPurchase(constructionPermit, farm.getInstanceId(), true, construction.family());
            h.assertTrue(data.beginPrefab(construction, constructionPermit, 20) == BuildingWorldData.Result.SUCCESS,
                    "Could not begin construction fixture");
            var constructing = data.find(construction.id());
            h.assertTrue(data.rename(constructing.id(), constructing.revision(), "North Coop")
                    == BuildingWorldData.Result.SUCCESS, "Could not name construction fixture");
            data.markScaffold(construction.id());
            data.constructionDay(21, false);

            StardewConstructionProgressSnapshot constructionOwnerView =
                    BuildingConstructionProgressSyncEvents.snapshotFor(data, ownerPlayer);
            h.assertTrue(constructionOwnerView.totalOrderCount() == 1
                    && constructionOwnerView.orders().size() == 1
                    && !constructionOwnerView.truncated(), "Owner did not receive the active Robin order");
            var constructionView = constructionOwnerView.find(construction.id()).orElseThrow();
            h.assertTrue(constructionView.workType() == StardewConstructionOrderSnapshot.WorkType.CONSTRUCTION
                            && constructionView.targetTier() == 1
                            && constructionView.remainingWorkDays() == 3
                            && constructionView.displayName().getString().equals("North Coop"),
                    "Construction snapshot lost type, tier, paused-day progress or custom name");

            data.constructionDay(22, true);
            data.constructionDay(23, true);
            data.constructionDay(24, true);
            h.assertTrue(data.finishPrefab(construction.id()) == BuildingWorldData.Result.SUCCESS,
                    "Could not finish construction fixture building");
            var ready = data.find(construction.id());
            h.assertTrue(data.beginUpgrade(ready.id(), ready.revision(), 25) == BuildingWorldData.Result.SUCCESS,
                    "Could not begin upgrade fixture");
            StardewConstructionProgressSnapshot ownerView =
                    BuildingConstructionProgressSyncEvents.snapshotFor(data, ownerPlayer);
            var upgradeView = ownerView.find(construction.id()).orElseThrow();
            h.assertTrue(upgradeView.workType() == StardewConstructionOrderSnapshot.WorkType.UPGRADE
                            && upgradeView.targetTier() == 2
                            && upgradeView.remainingWorkDays() == 2,
                    "Upgrade snapshot lost type, target tier or work days");
            h.assertTrue(BuildingConstructionProgressSyncEvents.snapshotFor(data, memberPlayer)
                    .orders().equals(ownerView.orders()), "Farm member did not receive manageable orders");
            h.assertTrue(BuildingConstructionProgressSyncEvents.snapshotFor(data, outsiderPlayer)
                    .orders().isEmpty(), "Outsider received private construction progress");

            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            StardewConstructionProgressCache.clear();
            try {
                var payload = new BuildingConstructionProgressSyncPayload(ownerView);
                BuildingConstructionProgressSyncPayload.STREAM_CODEC.encode(buffer, payload);
                var decoded = BuildingConstructionProgressSyncPayload.STREAM_CODEC.decode(buffer).snapshot();
                h.assertTrue(decoded.equals(ownerView) && buffer.readableBytes() == 0,
                        "Construction packet lost snapshot fields");
                StardewConstructionProgressCache.replace(decoded);
                h.assertTrue(StardewClientConstructionProgress.current().orElseThrow().equals(ownerView),
                        "Client construction cache did not replace atomically");
                boolean immutable = false;
                try {
                    decoded.orders().clear();
                } catch (UnsupportedOperationException expected) {
                    immutable = true;
                }
                h.assertTrue(immutable, "Construction order list escaped mutable");
                var truncated = new StardewConstructionProgressSnapshot(owner, 2,
                        List.of(constructionView));
                h.assertTrue(truncated.truncated(), "Bounded snapshot did not report truncation");
            } finally {
                buffer.release();
                StardewConstructionProgressCache.clear();
            }
            h.assertTrue(StardewClientConstructionProgress.current().isEmpty(),
                    "Construction cache leaked after disconnect clear");
        } finally {
            data.removeFarm(farm.getInstanceId());
            farms.deleteFarm(owner);
        }
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void cookingCalendarAndReadOnlyQueriesMatchTelevision(GameTestHelper h) {
        var data = new PlayerStardewData(UUID.randomUUID());
        for (int day : List.of(1, 2, 3, 4, 5, 6, 8, 9)) {
            h.assertTrue(DailyInfoSyncEvents.queenOfSauce(data, new StardewCalendarDate(1, 0, day)).isEmpty(),
                    "Cooking shown on off-air day or first Wednesday: " + day);
        }
        var first = DailyInfoSyncEvents.queenOfSauce(data, new StardewCalendarDate(1, 0, 7)).orElseThrow();
        h.assertTrue(first.recipeId().equals("stir_fry") && !first.rerun() && first.canLearnRecipe()
                        && !first.watchedToday() && !first.recipeKnown(), "Wrong first Sunday broadcast");
        h.assertTrue(!data.isRecipeUnlocked("stir_fry") && !data.hasWatchedQueenOfSauceOnDay(7),
                "Reading daily info watched/unlocked the recipe");
        h.assertTrue(DailyInfoSyncEvents.queenOfSauce(data, new StardewCalendarDate(1, 0, 10))
                .orElseThrow().rerun(), "Second Wednesday should have a rerun");
        h.assertTrue(DailyInfoSyncEvents.queenOfSauce(data, new StardewCalendarDate(2, 0, 7))
                .orElseThrow().recipeId().equals("pizza"), "Year two broadcast lost its cycle");
        h.assertTrue(DailyInfoSyncEvents.queenOfSauce(data, new StardewCalendarDate(2, 3, 28))
                .orElseThrow().recipeId().equals("shrimp_cocktail"), "Week 32 boundary lost");
        h.assertTrue(DailyInfoSyncEvents.queenOfSauce(data, new StardewCalendarDate(3, 0, 7))
                .orElseThrow().recipeId().equals("stir_fry"), "Two-year broadcast cycle did not repeat");
        data.unlockRecipe("stir_fry");
        var known = DailyInfoSyncEvents.queenOfSauce(data, new StardewCalendarDate(1, 0, 7)).orElseThrow();
        h.assertTrue(known.recipeKnown() && !known.watchedToday() && !known.canLearnRecipe(),
                "Already learned recipe was confused with watching today's TV");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void personalCookingRerunsMatchTvAndStayPinnedAfterWatching(GameTestHelper h) {
        var date = new StardewCalendarDate(1, 0, 17); // Wednesday: two prior broadcasts.
        var first = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CookingFirst"));
        var second = net.minecraftforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CookingSecond"));
        var a = com.stardew.craft.player.PlayerStardewDataAPI.getData(first);
        var b = com.stardew.craft.player.PlayerStardewDataAPI.getData(second);
        a.unlockRecipe("stir_fry");
        b.unlockRecipe("coleslaw");
        var firstInfo = DailyInfoSyncEvents.queenOfSauce(a, date).orElseThrow();
        var secondInfo = DailyInfoSyncEvents.queenOfSauce(b, date).orElseThrow();
        h.assertTrue(firstInfo.recipeId().equals("coleslaw") && secondInfo.recipeId().equals("stir_fry"),
                "Reruns did not select each player's missing recipe");
        for (var player : List.of(first, second)) {
            var data = com.stardew.craft.player.PlayerStardewDataAPI.getData(player);
            var info = DailyInfoSyncEvents.queenOfSauce(data, date).orElseThrow();
            var tv = TVChannelData.buildPayload(player, 17, 2, 17, 0, "Sun", 0, 0, 0, 0);
            h.assertTrue(tv.cookingAvailable() && tv.cookingRecipeId().equals(info.recipeId())
                            && tv.cookingIsRerun() == info.rerun() && tv.cookingAlreadyKnown() == info.recipeKnown(),
                    "API diverged from the actual TV payload");
            h.assertTrue(info.equals(DailyInfoSyncEvents.queenOfSauce(data, date).orElseThrow()),
                    "Read-only queries rerolled the broadcast");
        }
        a.markQueenOfSauceWatched(17, firstInfo.recipeId());
        a.unlockRecipe(firstInfo.recipeId());
        var watched = DailyInfoSyncEvents.queenOfSauce(a, date).orElseThrow();
        h.assertTrue(watched.recipeId().equals(firstInfo.recipeId()) && watched.watchedToday()
                        && watched.recipeKnown() && !watched.canLearnRecipe(), "Watched rerun drifted to another recipe");
        h.assertTrue(DailyInfoSyncEvents.queenOfSauce(b, date).orElseThrow().equals(secondInfo),
                "Watching TV changed the other player's snapshot");
        h.assertTrue(DailyInfoSyncEvents.queenOfSauce(a, date.plusDays(1)).isEmpty(), "Off-air day retained yesterday's show");
        h.succeed();
    }

    private static StardewDailyInfoSnapshot withCooking(StardewDailyInfoSnapshot info, StardewQueenOfSauceSnapshot cooking) {
        return new StardewDailyInfoSnapshot(info.playerId(), info.date(), info.dailyLuck(), info.tomorrowWeather(),
                info.berrySeason(), info.booksellerToday(), info.travelingCartToday(), info.birthdayNpcIds(),
                info.toolUpgrade(), Optional.of(cooking));
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void birthdaysSupportMultipleNpcsAndAddonNamespaces(GameTestHelper h) {
        var root = JsonParser.parseString("""
                {"birthdays":{"Abigail":{"season":"FALL","day":13},
                "addon:visitor":{"season":"fall","day":13},
                "Other":{"season":"fall","day":14},"Broken":{"season":{},"day":"bad"}}}
                """).getAsJsonObject();
        var ids = DailyInfoSyncEvents.birthdaysToday(root, new StardewCalendarDate(2, 2, 13));
        h.assertTrue(ids.size() == 2 && ids.contains(new ResourceLocation("stardewcraft:abigail"))
                && ids.contains(new ResourceLocation("addon:visitor")), "Birthday data or namespace lost");
        var mutable = new ArrayList<>(ids);
        var snapshot = new StardewDailyInfoSnapshot(UUID.randomUUID(), new StardewCalendarDate(2, 2, 13),
                0, "Rain", StardewDailyInfoSnapshot.BerrySeason.NONE, false, true, mutable, Optional.empty());
        mutable.clear();
        h.assertTrue(snapshot.birthdayNpcIds().size() == 2, "Caller mutated snapshot birthdays");
        boolean immutable = false;
        try { snapshot.birthdayNpcIds().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        h.assertTrue(immutable, "Birthday list escaped mutable");
        h.succeed();
    }

    @GameTest(templateNamespace = "stardewcraft_daily_info", template = "ring_utilities")
    public static void luckThresholdsMatchTelevision(GameTestHelper h) {
        double[] values = {-.071, -.07, -.021, -.02, 0, .019, .02, .069, .07};
        var expected = new StardewDailyInfoSnapshot.LuckLevel[] {
                StardewDailyInfoSnapshot.LuckLevel.VERY_BAD, StardewDailyInfoSnapshot.LuckLevel.BAD,
                StardewDailyInfoSnapshot.LuckLevel.BAD, StardewDailyInfoSnapshot.LuckLevel.NEUTRAL,
                StardewDailyInfoSnapshot.LuckLevel.ZERO, StardewDailyInfoSnapshot.LuckLevel.NEUTRAL,
                StardewDailyInfoSnapshot.LuckLevel.GOOD, StardewDailyInfoSnapshot.LuckLevel.GOOD,
                StardewDailyInfoSnapshot.LuckLevel.VERY_GOOD};
        for (int i = 0; i < values.length; i++) {
            h.assertTrue(info(UUID.randomUUID(), new StardewCalendarDate(1, 0, 1), values[i], Optional.empty())
                    .luckLevel() == expected[i], "Incorrect fortune at " + values[i]);
        }
        h.succeed();
    }

    private static StardewDailyInfoSnapshot info(UUID player, StardewCalendarDate date, double luck,
                                                Optional<StardewToolUpgradeSnapshot> tool) {
        return new StardewDailyInfoSnapshot(player, date, luck, "Storm", StardewDailyInfoSnapshot.BerrySeason.BLACKBERRY,
                true, false, List.of(new ResourceLocation("stardewcraft:abigail")), tool);
    }

    private static BuildingRecord buildingRecord(
            UUID farm,
            int slot,
            ResourceLocation family,
            BlockPos anchor,
            ResourceLocation dimension
    ) {
        var definition = PrefabDefinitions.get(family);
        var tier = definition.tier(1);
        return BuildingRecord.waiting(
                farm,
                slot,
                family,
                BuildingRecord.Mode.PREFAB,
                dimension,
                anchor,
                PrefabDefinitions.world(tier.manager(), tier.anchor(), anchor, Rotation.NONE),
                Direction.SOUTH,
                PrefabDefinitions.transform(definition.reservation(), anchor, Rotation.NONE));
    }
}
