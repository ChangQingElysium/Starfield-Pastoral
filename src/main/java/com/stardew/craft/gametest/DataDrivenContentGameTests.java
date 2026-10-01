package com.stardew.craft.gametest;

import com.stardew.craft.port.PortItemData;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.action.*;
import com.stardew.craft.api.v1.query.*;
import com.stardew.craft.api.v1.fishing.StardewFishingTreasurePoolDefinition;
import com.stardew.craft.api.v1.world.StardewWorldLootPoolDefinition;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.fishing.data.*;
import com.stardew.craft.mining.SkullCavernTreasurePool;
import com.stardew.craft.player.*;
import com.stardew.craft.shop.*;
import com.stardew.craft.world.data.WorldLootPoolData;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.*;
import net.minecraft.world.item.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("stardewcraft_data_driven")
@PrefixGameTestTemplate(false)
public final class DataDrivenContentGameTests {
    private static JsonElement json(String s) { return JsonParser.parseString(s); }
    private static ResourceLocation id(String s) { return new ResourceLocation(s); }
    private static ServerPlayer player(GameTestHelper h, String name) {
        return FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }
    private static void reload(Object listener, Map<ResourceLocation, JsonElement> map, GameTestHelper h) throws Exception {
        var apply = listener.getClass().getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
        apply.setAccessible(true); apply.invoke(listener, map, h.getLevel().getServer().getResourceManager(), InactiveProfiler.INSTANCE);
    }
    private static void cleanup(ServerPlayer p) {
        p.getInventory().clearContent(); GeodeLootService.onPlayerLogout(p); BlacksmithService.onLogout(p);
        PlayerDataManager.get().removePlayerData(p.getUUID());
    }
    @GameTest(templateNamespace="stardewcraft_data_driven", template="empty", timeoutTicks=100)
    public static void mysteryQueriesArePureAndOpeningCommitsEffects(GameTestHelper h) throws Exception {
        var p = player(h, "DataMystery");
        try {
            var data = PlayerDataManager.getPlayerData(p);
            data.incrementStat("MysteryBoxesOpened", 25000);
            var random = new Random(1) { @Override public double nextDouble() { return .5; } };
            var before = data.getStat("MysteryBoxesOpened");
            var effects = new ArrayList<StardewAction>();
            var result = GeodeDropData.roll(id("stardewcraft:mystery_box"), p, random, effects::add).orElseThrow();
            h.assertTrue(result.is(com.stardew.craft.item.ModItems.BOOK_MYSTERY.get()), "Mystery book progression was not read from data");
            h.assertTrue(!data.hasMailFlag("GotMysteryBook") && data.getStat("MysteryBoxesOpened") == before,
                    "Query preview changed player progression");
            h.assertTrue(effects.size() == 2, "Book and opening effects were not deferred");
            var definition = GeodeDropData.snapshot().definitions().get(id("stardewcraft:mystery_box"));
            var preview = StardewItemQueries.preview(com.stardew.craft.port.PortJava.getFirst(definition.entries()).query(),
                    StardewItemQueryContext.forPlayer(p,new Random(0))).getOrThrow();
            h.assertTrue(preview.stream().anyMatch(s -> s.is(com.stardew.craft.item.ModItems.PRISMATIC_SHARD.get()))
                    && preview.stream().anyMatch(s -> s.is(com.stardew.craft.item.ModItems.COFFEE_BEAN.get()))
                    && preview.stream().anyMatch(s -> s.is(com.stardew.craft.item.ModItems.BOOK_MYSTERY.get()))
                    && data.getStat("MysteryBoxesOpened") == before && !data.hasMailFlag("GotMysteryBook"),
                    "Preview sampled one branch or executed reward effects");
            var unique = json("""
                    {"type":"stardewcraft:conditional","data":{"maximum":{"special_item:test_addon:unique":0},"query":
                      {"type":"stardewcraft:with_actions","data":{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:diamond"}},
                       "actions":[{"type":"stardewcraft:remember_special_item","data":{"item":"test_addon:unique"}}]}}}}
                    """);
            var sequence = new JsonObject(); sequence.addProperty("type","stardewcraft:all");
            var sequenceData = new JsonObject(); var queries = new JsonArray(); queries.add(unique); queries.add(unique.deepCopy());
            sequenceData.add("queries",queries);sequence.add("data",sequenceData);
            var uniqueQuery = StardewItemQueries.CODEC.parse(JsonOps.INSTANCE,sequence).getOrThrow();
            var uniqueResult = StardewItemQueries.resolve(uniqueQuery,StardewItemQueryContext.forPlayer(p,new Random(0))).getOrThrow();
            h.assertTrue(uniqueResult.size()==1 && !data.hasSpecialItem("test_addon:unique"),
                    "Deferred progress allowed duplicate unique loot in one query or changed real preview state");
            for (String kind : List.of("mystery_box", "golden_mystery_box")) for (int seed = 0; seed < 200; seed++)
                h.assertTrue(!GeodeDropData.roll(id("stardewcraft:" + kind), p, new Random(seed)).orElseThrow().isEmpty(), "Builtin mystery table produced an empty item");
            p.getInventory().setItem(0, new ItemStack(com.stardew.craft.item.ModItems.MYSTERY_BOX.get(), 2));
            PlayerStardewDataAPI.setMoney(p, 100);
            GeodeLootService.handleGeodeCrack(p, 0);
            GeodeLootService.handleGeodeCrack(p, 0);
            h.assertTrue(data.getStat("MysteryBoxesOpened") == before + 1 && PlayerStardewDataAPI.getMoney(p) == 75,
                    "Opening did not commit once, or duplicate request charged twice");
            GeodeLootService.handleGeodeClaim(p);
        } finally { cleanup(p); }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_data_driven", template="empty", timeoutTicks=100)
    public static void fishingBaseLegacyAndNamespacedReplacementAreConsumed(GameTestHelper h) throws Exception {
        var p = player(h, "DataFish");
        var originals = new LinkedHashMap<ResourceLocation, JsonElement>();
        FishingTreasurePoolData.snapshot().definitions().forEach((key, d) -> originals.put(
                new ResourceLocation(key.getNamespace(), "treasure_pools/"+key.getPath()),
                StardewFishingTreasurePoolDefinition.CODEC.encodeStart(JsonOps.INSTANCE, d).getOrThrow()));
        var listener = new FishingTreasurePoolData.ReloadListener();
        try {
            var manager = new TreasureLootManager(); manager.loadFromBundledData();
            for (int seed = 0; seed < 100; seed++) {
                var loot = manager.generateTreasure(10, seed % 2 == 0, RandomSource.create(seed), 5, .05, p);
                h.assertTrue(!loot.isEmpty(), "Bundled fishing query was not usable");
            }
            var decode = TreasureLootManager.class.getDeclaredMethod("decode", JsonElement.class); decode.setAccessible(true);
            var apply = TreasureLootManager.class.getDeclaredMethod("apply", TreasureLootManager.TreasureData.class, ResourceManager.class, ProfilerFiller.class); apply.setAccessible(true);
            var legacy = decode.invoke(null, json("""
                    {"rollChanceDecayNormal":0,"rareChance":0,"commonLoot":[{"item":"minecraft:diamond","minCount":3,"maxCount":3,"weight":1}]}
                    """));
            apply.invoke(manager, legacy, h.getLevel().getServer().getResourceManager(), InactiveProfiler.INSTANCE);
            var oldJsonLoot = manager.generateTreasure(0, false, RandomSource.create(2), 0, 0, p);
            h.assertTrue(oldJsonLoot.size()==1 && com.stardew.craft.port.PortJava.getFirst(oldJsonLoot).is(Items.DIAMOND) && com.stardew.craft.port.PortJava.getFirst(oldJsonLoot).getCount()==3,
                    "Legacy fishing JSON is still ignored");
            var replacements = new LinkedHashMap<>(originals);
            var replacement = json("""
                    {"replace_base":true,"entries":[{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:emerald","count":2}}}]}
                    """);
            replacements.put(id("test_addon:treasure_pools/replacement"), replacement);
            reload(listener, replacements, h);
            var replaced = manager.generateTreasure(0, false, RandomSource.create(3), 0, 0, p);
            h.assertTrue(replaced.isEmpty(), "Addon replacement left the Java/base rewards active");
            FishingTreasurePoolData.appendLoot(replaced,p,0,false,0,RandomSource.create(3));
            h.assertTrue(replaced.size()==1 && com.stardew.craft.port.PortJava.getFirst(replaced).is(Items.EMERALD), "Namespaced replacement failed to award its result");
            replacement.getAsJsonObject().addProperty("chance", 0);
            reload(listener, replacements, h);
            var empty = manager.generateTreasure(0,false,RandomSource.create(4),0,0,p);
            FishingTreasurePoolData.appendLoot(empty,p,0,false,0,RandomSource.create(4));
            h.assertTrue(empty.isEmpty(), "An intentionally empty replacement reinserted hardcoded fallback loot");
        } finally { reload(listener, originals, h); cleanup(p); }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_data_driven", template="empty", timeoutTicks=100)
    public static void skullSpecialAndBasePoolsCanBothBeOverridden(GameTestHelper h) throws Exception {
        var p = player(h,"DataSkull");
        var original = new LinkedHashMap<ResourceLocation,JsonElement>();
        WorldLootPoolData.snapshot().definitions().forEach((key,d)->original.put(key,StardewWorldLootPoolDefinition.CODEC.encodeStart(JsonOps.INSTANCE,d).getOrThrow()));
        var listener = new WorldLootPoolData.ReloadListener();
        try {
            var changed = new LinkedHashMap<>(original);
            changed.put(id("stardewcraft:skull_cavern_special"),json("""
                    {"source":"stardewcraft:skull_cavern_treasure","group":"special","mode":"sequential","entries":[{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:air"}}}]}
                    """));
            changed.put(id("stardewcraft:skull_cavern_treasure"),json("""
                    {"source":"stardewcraft:skull_cavern_treasure","entries":[{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:diamond","count":2}}}]}
                    """));
            reload(listener,changed,h);
            for(int seed=0;seed<50;seed++) {
                var stack=SkullCavernTreasurePool.roll(RandomSource.create(seed),p);
                h.assertTrue(stack.is(Items.DIAMOND)&&stack.getCount()==2,"Hardcoded skull reward bypassed overridden data");
            }
            h.assertTrue(SkullCavernTreasurePool.rollSlot(0,RandomSource.create(1),p).is(Items.DIAMOND),"Legacy slot adapter still used a Java switch");
        } finally { reload(listener,original,h);cleanup(p); }
        h.succeed();
    }
    @GameTest(templateNamespace="stardewcraft_data_driven", template="empty", timeoutTicks=100)
    public static void addonToolUpgradePreservesComponentsAndPersistsOrder(GameTestHelper h) throws Exception {
        var p=player(h,"DataTools");
        var original=new LinkedHashMap<ResourceLocation,JsonElement>();
        ToolUpgradeData.snapshot().forEach((key,d)->original.put(key,ToolUpgradeData.Definition.CODEC.encodeStart(JsonOps.INSTANCE,d).getOrThrow()));
        var listener=new ToolUpgradeData.ReloadListener();
        try {
            var changed=new LinkedHashMap<>(original);
            changed.put(id("test_addon:axe"),json("""
                    {"family":"test_addon:axe","tier":1,"input":"minecraft:wooden_axe","output":"minecraft:diamond_axe","price":17,"material":"minecraft:iron_ingot","material_count":2,"days":3}
                    """));
            reload(listener,changed,h);
            var old=new ItemStack(Items.WOODEN_AXE);old.setDamageValue(10);
            PortItemData.set(old, com.stardew.craft.port.net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Addon tool"));
            p.getInventory().setItem(0,old);p.getInventory().setItem(1,new ItemStack(Items.IRON_INGOT,2));
            PlayerStardewDataAPI.setMoney(p,100);
            var build=BlacksmithService.class.getDeclaredMethod("buildToolUpgradeItems",ServerPlayer.class);build.setAccessible(true);
            @SuppressWarnings("unchecked") var offers=(List<ShopItemEntry>)build.invoke(null,p);
            int index=-1;for(int i=0;i<offers.size();i++)if(offers.get(i).itemId().equals("minecraft:diamond_axe"))index=i;
            h.assertTrue(index>=0,"Addon tool requires a builtin Java tool subclass");
            BlacksmithService.handleToolUpgradePurchaseFromShop(p,index,1);
            var data=PlayerDataManager.getPlayerData(p);
            h.assertTrue(data.getDaysLeftForToolUpgrade()==3 && data.getToolBeingUpgraded().equals("minecraft:diamond_axe")
                    && p.getInventory().countItem(Items.WOODEN_AXE)==0 && p.getInventory().countItem(Items.IRON_INGOT)==0
                    && PlayerStardewDataAPI.getMoney(p)==83,"Upgrade ignored data cost/input/duration");
            var restored=PlayerStardewData.fromNBT(data.toNBT(p.registryAccess()),p.getUUID(),p.registryAccess());
            var stack=restored.getToolUpgradeStack(p.registryAccess());
            h.assertTrue(stack.is(Items.DIAMOND_AXE)&&stack.getDamageValue()==0&&stack.getHoverName().getString().equals("Addon tool"),"Upgrade lost components or its persisted result");
            BlacksmithService.handleToolUpgradePurchaseFromShop(p,index,1);
            h.assertTrue(PlayerStardewDataAPI.getMoney(p)==83,"Duplicate upgrade request charged again");
            var valid = ToolUpgradeData.snapshot().get(id("test_addon:axe"));
            changed.get(id("test_addon:axe")).getAsJsonObject().addProperty("price",-1);
            reload(listener,changed,h);
            h.assertTrue(valid.equals(ToolUpgradeData.snapshot().get(id("test_addon:axe")))
                    && data.getToolUpgradeStack(p.registryAccess()).is(Items.DIAMOND_AXE), "Invalid reload damaged catalog or pending order");
            data.setToolBeingUpgraded("");
            p.getInventory().setItem(0,new ItemStack(Items.WOODEN_AXE));p.getInventory().setItem(1,new ItemStack(Items.IRON_INGOT,2));
            PlayerStardewDataAPI.setMoney(p,100);build.invoke(null,p);
            changed.get(id("test_addon:axe")).getAsJsonObject().addProperty("price",18);
            reload(listener,changed,h);
            BlacksmithService.handleToolUpgradePurchaseFromShop(p,index,1);
            h.assertTrue(PlayerStardewDataAPI.getMoney(p)==100 && data.getToolBeingUpgraded().isEmpty()
                    && p.getInventory().countItem(Items.WOODEN_AXE)==1, "Stale shop index bought a reloaded recipe");
        } finally { reload(listener,original,h);cleanup(p); }
        h.succeed();
    }
    private static boolean failReward;
    private static final ResourceLocation FAIL_ACTION=id("test_addon:reward_retry");
    private static void registerTestAction() {
        if (!StardewActions.registeredIds().contains(FAIL_ACTION)) StardewActions.register(FAIL_ACTION,
                com.mojang.serialization.Codec.unit("retry"),(ctx,value)->failReward?StardewActionResult.failure("Expected test failure"):StardewActionResult.ok());
    }
    @GameTest(templateNamespace="stardewcraft_data_driven", template="empty", timeoutTicks=100)
    public static void gilCompositeRewardRetriesWithoutRepeatingSuccessfulActions(GameTestHelper h) throws Exception {
        registerTestAction();
        var level=h.getLevel();
        var dimension=net.minecraft.world.level.Level.class.getDeclaredField("dimension");dimension.setAccessible(true);
        var oldDimension=dimension.get(level);
        var p=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"DataGil"));p.setPos(GilService.X,GilService.Y,GilService.Z);
        var originals=new LinkedHashMap<String,MonsterSlayerGoalRegistry.SlayerGoal>();
        MonsterSlayerGoalRegistry.getAllGoals().forEach(g->originals.put(g.goalKey(),g));
        var apply=MonsterSlayerGoalRegistry.class.getDeclaredMethod("applyCandidate",Map.class,List.class);apply.setAccessible(true);
        try {
            dimension.set(level,ModDimensions.STARDEW_VALLEY);
            var actions=StardewActions.CODEC.listOf().parse(JsonOps.INSTANCE,json("""
                    [{"type":"stardewcraft:add_money","data":{"amount":10}},
                     {"type":"test_addon:reward_retry"},
                     {"type":"stardewcraft:add_item","data":{"item":"minecraft:emerald","count":2}}]
                    """)).getOrThrow();
            var goal=new MonsterSlayerGoalRegistry.SlayerGoal("test_addon:goal","test_addon.goal",1,List.of("test_addon"),actions,Optional.of(id("minecraft:diamond")));
            var goals=new LinkedHashMap<>(originals);goals.put(goal.goalKey(),goal);apply.invoke(null,goals,List.of());
            var data=PlayerDataManager.getPlayerData(p);data.addMonsterKills(goal.goalKey(),1);PlayerStardewDataAPI.setMoney(p,100);
            var menu=new GilRewardMenu(1,p.getInventory());
            h.assertTrue(GilService.rewardItem(goal).is(Items.DIAMOND)&&PlayerStardewDataAPI.getMoney(p)==100,"Preview executed actions or ignored explicit icon");
            failReward=true;
            h.assertTrue(!GilService.claim(p,goal)&&PlayerStardewDataAPI.getMoney(p)==110&&!data.hasClaimedSlayerReward(goal.goalKey()),"Failed composite claim was lost or prematurely marked complete");
            var restored=PlayerStardewData.fromNBT(data.toNBT(p.registryAccess()),p.getUUID(),p.registryAccess());
            h.assertTrue(restored.getGuildRewardClaims().getCompound(goal.goalKey()).getInt("Next")==1,"Reward continuation was not persisted");
            goals.put(goal.goalKey(), new MonsterSlayerGoalRegistry.SlayerGoal(goal.goalKey(), goal.translationKey(), 1,
                    goal.monsterTags(), actions.subList(0,1)));
            apply.invoke(null,goals,List.of());
            failReward=false;
            h.assertTrue(GilService.claim(p,goal)&&PlayerStardewDataAPI.getMoney(p)==110&&p.getInventory().countItem(Items.EMERALD)==2,"Retry duplicated money or lost the remaining actions");
            h.assertTrue(p.getInventory().countItem(Items.DIAMOND)==0&&!GilService.claim(p,goal),"Preview icon became a real reward or repeat claim succeeded");
            menu.removed(p);
        } finally { failReward=false;apply.invoke(null,originals,List.of());cleanup(p);dimension.set(level,oldDimension); }
        h.succeed();
    }
}
