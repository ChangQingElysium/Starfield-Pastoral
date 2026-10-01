package com.stardew.craft.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.stardew.craft.api.v1.loot.StardewGeodeDropDefinition;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.network.payload.OpenGeodeMenuPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.shop.GeodeDropData;
import com.stardew.craft.shop.GeodeLootService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("stardewcraft_artifact_trove")
@PrefixGameTestTemplate(false)
public final class GeodeDataPackGameTests {
    @GameTest(templateNamespace = "stardewcraft_artifact_trove", template = "empty", timeoutTicks = 100)
    public static void overrideAddonClientSyncAndRejectedReload(GameTestHelper h) throws Exception {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "GeodeData"));
        var originals = new LinkedHashMap<ResourceLocation, JsonElement>();
        GeodeDropData.snapshot().definitions().forEach((id, value) -> originals.put(
                new ResourceLocation(id.getNamespace(), "drops/" + id.getPath()),
                StardewGeodeDropDefinition.CODEC.encodeStart(JsonOps.INSTANCE, value).getOrThrow()));
        var reload = GeodeDropData.ReloadListener.class.getDeclaredMethod("apply", Map.class, ResourceManager.class, ProfilerFiller.class);
        reload.setAccessible(true);
        var listener = new GeodeDropData.ReloadListener();
        int money = PlayerStardewDataAPI.getMoney(player);
        try {
            h.assertTrue(originals.size() >= 5, "Bundled geodes missing from reload catalog");
            var changed = new LinkedHashMap<>(originals);
            var replacement = JsonParser.parseString("""
                    {"inputs":["stardewcraft:artifact_trove"],"crusher_allowed":false,"animation":"artifact_trove",
                     "entries":[{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:diamond","count":2}}}]}
                    """);
            changed.put(new ResourceLocation("stardewcraft:drops/artifact_trove"), replacement);
            changed.put(new ResourceLocation("test_addon:drops/stick_crystal"), JsonParser.parseString("""
                    {"inputs":["minecraft:stick"],"crusher_allowed":false,"animation":"magma_geode",
                     "entries":[{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:emerald","count":3}}}]}
                    """));
            changed.put(new ResourceLocation("old_addon:drops/geode_override"), JsonParser.parseString("""
                    {"inputs":["stardewcraft:geode"],
                     "entries":[{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:iron_ingot"}}}]}
                    """));
            // Two lower-priority candidates are harmless once a higher-priority addon owns the input.
            var shadowed = originals.get(new ResourceLocation("stardewcraft:drops/geode")).deepCopy();
            changed.put(new ResourceLocation("aaa_addon:drops/shadowed_geode"), shadowed);
            changed.put(new ResourceLocation("aab_addon:drops/shadowed_geode"), shadowed.deepCopy());
            changed.put(new ResourceLocation("test_addon:drops/locked_mystery_box"), JsonParser.parseString("""
                    {"inputs":["stardewcraft:mystery_box"],
                     "available_when":[{"type":"stardewcraft:always","data":{"value":false}}],
                     "entries":[{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:diamond"}}}]}
                    """));
            reload.invoke(listener, changed, h.getLevel().getServer().getResourceManager(), InactiveProfiler.INSTANCE);
            h.assertTrue(GeodeDropData.definitionFor(new ItemStack(ModItems.GEODE.get())).equals(new ResourceLocation("old_addon:geode_override"))
                    && GeodeDropData.inputsFor(new ResourceLocation("stardewcraft:geode")).isEmpty(), "Bundled defaults broke legacy addon override or leak duplicate catalog entries");
            var trove = new ResourceLocation("stardewcraft:artifact_trove");
            var treasure = GeodeDropData.roll(trove, player, new Random(0)).orElseThrow();
            h.assertTrue(treasure.is(Items.DIAMOND) && treasure.getCount() == 2, "Java default pool overrode datapack replacement");
            var stick = new ItemStack(Items.STICK, 2);
            h.assertTrue(GeodeLootService.isClintInput(stick) && !GeodeLootService.isGeodeCrusherInput(stick), "Addon processing/ crusher flags ignored");
            h.assertTrue(GeodeDropData.snapshot().definitions().get(new ResourceLocation("test_addon:stick_crystal")).animation().equals("magma_geode"), "Addon animation lost");
            var buf = new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                OpenGeodeMenuPayload.STREAM_CODEC.encode(buf, new OpenGeodeMenuPayload(GeodeLootService.clintInputs(player)));
                var decoded = OpenGeodeMenuPayload.STREAM_CODEC.decode(buf);
                h.assertTrue(decoded.inputs().contains(new ResourceLocation("minecraft:stick")) && decoded.inputs().contains(trove), "Dedicated client input list missing builtin/ addon input");
                h.assertTrue(!decoded.inputs().contains(new ResourceLocation("stardewcraft:mystery_box"))
                        && decoded.inputs().contains(new ResourceLocation("stardewcraft:golden_mystery_box")),
                        "Legacy fallback bypassed addon availability or hid an unmodified input");
            } finally { buf.release(); }
            player.getInventory().clearContent(); player.getInventory().setItem(0, stick);
            PlayerStardewDataAPI.setMoney(player, 100);
            GeodeLootService.handleGeodeCrack(player, 0);
            GeodeLootService.handleGeodeCrack(player, 0);
            h.assertTrue(stick.getCount() == 1 && PlayerStardewDataAPI.getMoney(player) == 75, "Pending addon result overwritten or double-charged");
            GeodeLootService.handleGeodeClaim(player);
            h.assertTrue(player.getInventory().items.stream().anyMatch(s -> s.is(Items.EMERALD) && s.getCount() == 3), "Addon query result/count not delivered");
            // Ambiguous inputs reject the complete reload and retain the last good runtime catalog.
            changed.put(new ResourceLocation("test_addon:drops/conflict"), replacement);
            reload.invoke(listener, changed, h.getLevel().getServer().getResourceManager(), InactiveProfiler.INSTANCE);
            h.assertTrue(GeodeDropData.roll(trove, player, new Random(0)).orElseThrow().is(Items.DIAMOND)
                    && GeodeLootService.isClintInput(new ItemStack(Items.STICK)), "Rejected reload damaged the active catalog");
            changed.remove(new ResourceLocation("test_addon:drops/conflict"));
            changed.put(new ResourceLocation("test_addon:drops/stick_crystal"), JsonParser.parseString("""
                    {"inputs":["minecraft:stick"],
                     "entries":[{"query":{"type":"stardewcraft:item","data":{"item":"minecraft:air"}}}]}
                    """));
            reload.invoke(listener, changed, h.getLevel().getServer().getResourceManager(), InactiveProfiler.INSTANCE);
            var defaulted = GeodeDropData.snapshot().definitions().get(new ResourceLocation("test_addon:stick_crystal"));
            h.assertTrue(defaulted.crusherAllowed() && defaulted.animation().equals("geode"), "Legacy JSON defaults changed");
            var legacyConstructor = new StardewGeodeDropDefinition(defaulted.inputs(), defaulted.availableWhen(), defaulted.entries());
            h.assertTrue(legacyConstructor.crusherAllowed() && legacyConstructor.animation().equals("geode"), "Legacy addon constructor changed");
            GeodeLootService.handleGeodeCrack(player, 0);
            h.assertTrue(stick.getCount() == 1 && PlayerStardewDataAPI.getMoney(player) == 75, "Empty addon query consumed input or money");
            var invalid = changed.get(new ResourceLocation("test_addon:drops/stick_crystal")).deepCopy().getAsJsonObject();
            invalid.addProperty("animation", "unsupported_animation");
            changed.put(new ResourceLocation("test_addon:drops/stick_crystal"), invalid);
            reload.invoke(listener, changed, h.getLevel().getServer().getResourceManager(), InactiveProfiler.INSTANCE);
            h.assertTrue(GeodeDropData.snapshot().definitions().get(new ResourceLocation("test_addon:stick_crystal")).equals(defaulted),
                    "Invalid metadata must retain the last good catalog without throwing out of reload");
        } finally {
            reload.invoke(listener, originals, h.getLevel().getServer().getResourceManager(), InactiveProfiler.INSTANCE);
            GeodeLootService.onPlayerLogout(player); player.getInventory().clearContent();
            PlayerStardewDataAPI.setMoney(player, money); PlayerDataManager.get().removePlayerData(player.getUUID());
        }
        h.succeed();
    }
}
