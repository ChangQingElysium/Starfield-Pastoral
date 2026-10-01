package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.terrain.TerrainVariants;
import com.stardew.craft.block.terrain.TerrainWorldUpgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class TerrainWorldUpgradeGameTests {
    private TerrainWorldUpgradeGameTests() {}
    private static final com.mojang.serialization.Codec<PalettedContainer<BlockState>> STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    private static CompoundTag chunk(String block) {
        CompoundTag root = new CompoundTag(); root.putInt("xPos", 3); root.putInt("zPos", -2);
        root.putString("PreserveMe", "farm buildings, crops and entities");
        CompoundTag section = new CompoundTag(); section.putByte("Y", (byte) 4);
        CompoundTag entry = new CompoundTag(); entry.putString("Name", block);
        ListTag palette = new ListTag(); palette.add(entry);
        CompoundTag state = new CompoundTag(); state.put("palette", palette); section.put("block_states", state);
        ListTag sections = new ListTag(); sections.add(section); root.put("sections", sections); return root;
    }
    private static PalettedContainer<BlockState> states(CompoundTag root) {
        return com.stardew.craft.port.PortDataResults.getOrThrow(STATES.parse(NbtOps.INSTANCE, root.getList("sections", 10).getCompound(0).getCompound("block_states")));
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void oldDefaultTerrainGainsPersistentVariants(GameTestHelper helper) {
        var root = chunk("stardewcraft:dirt");
        helper.assertTrue(TerrainWorldUpgrade.upgrade(root, 42), "Old chunk was skipped");
        int[] counts = new int[5]; var states = states(root);
        for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++)
            counts[states.get(x,y,z).getValue(TerrainVariants.DIRT)]++;
        helper.assertTrue(counts[0] > 3500 && counts[0] < 3800, "Dirt defaults must stay near 89%");
        for (int i = 1; i < 5; i++) helper.assertTrue(counts[i] > 0, "A dirt variant never appears");
        helper.assertTrue(root.getString("PreserveMe").equals("farm buildings, crops and entities"), "Unrelated save data changed");
        var once = root.copy();
        helper.assertTrue(!TerrainWorldUpgrade.upgrade(root, 12345) && root.equals(once), "Reload/season change rerolled saved variants");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void retirementRestoresGroundWithoutRegisteringOldBlocks(GameTestHelper helper) {
        for (String old : new String[]{"artifact_spot_dirt", "desert_artifact_spot", "beach_artifact_spot"}) {
            var id = new net.minecraft.resources.ResourceLocation(StardewCraft.MODID, old);
            helper.assertTrue(!net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(id)
                    && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id), "Retired block/item still registered");
            var root = chunk(id.toString()); TerrainWorldUpgrade.upgrade(root, 42);
            var block = states(root).get(0,0,0);
            helper.assertTrue(old.equals("artifact_spot_dirt") ? block.is(ModBlocks.DIRT.get()) : block.is(Blocks.SAND), "Removing old ID left an air hole");
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void bulkWeightsAndExistingChoicesArePreserved(GameTestHelper helper) {
        int[] grass = new int[3], dirt = new int[5];
        for (int i = 0; i < 100000; i++) {
            var pos = new BlockPos(i % 1000, 64, i / 1000);
            grass[TerrainWorldUpgrade.varied(ModBlocks.GRASS_BLOCK.get().defaultBlockState(), 1234, pos).getValue(TerrainVariants.GRASS)]++;
            dirt[TerrainWorldUpgrade.varied(ModBlocks.DIRT.get().defaultBlockState(), 1234, pos).getValue(TerrainVariants.DIRT)]++;
        }
        int[] gw = {88,8,4}, dw = {89,1,3,4,3};
        for (int i = 0; i < gw.length; i++) helper.assertTrue(Math.abs(grass[i] - gw[i] * 1000) < 500, "Bulk grass weights changed");
        for (int i = 0; i < dw.length; i++) helper.assertTrue(Math.abs(dirt[i] - dw[i] * 1000) < 500, "Bulk dirt weights changed");
        var chosen = ModBlocks.DIRT.get().defaultBlockState().setValue(TerrainVariants.DIRT, 4);
        helper.assertTrue(TerrainWorldUpgrade.varied(chosen, 1, BlockPos.ZERO) == chosen, "Existing pit was rerolled");
        for (Block block : new Block[]{ModBlocks.DARK_GRASS_BLOCK.get(), ModBlocks.FARMLAND.get(), Blocks.CHEST})
            helper.assertTrue(TerrainWorldUpgrade.varied(block.defaultBlockState(), 1, BlockPos.ZERO) == block.defaultBlockState(), "Non-varied block changed");
        helper.succeed();
    }

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void schematicPasteVariesUnspecifiedTerrainAndKeepsExplicitZero(GameTestHelper helper) throws Exception {
        var method = java.util.Arrays.stream(com.stardew.craft.mining.StructureLoader.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("placeSchematicBlocks"))
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);
        BlockState[] palette = {ModBlocks.GRASS_BLOCK.get().defaultBlockState(), ModBlocks.DIRT.get().defaultBlockState()};
        int[] indices = new int[256];
        for (int i = 0; i < indices.length; i++) indices[i] = i % 2;
        var origin = helper.absolutePos(new BlockPos(1, 1, 1));
        method.invoke(null, helper.getLevel(), origin, 16, 1, 16, palette, indices,
                new boolean[]{true, false}, new boolean[]{true, true}, false, null);
        int decorated = 0;
        for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
            var state = helper.getLevel().getBlockState(origin.offset(x,0,z));
            if (x % 2 == 0) { if (state.getValue(TerrainVariants.GRASS) != 0) decorated++; }
            else helper.assertTrue(state.getValue(TerrainVariants.DIRT) == 0, "Explicit template zero was changed");
        }
        helper.assertTrue(decorated > 0, "New farm template still has no variants");
        helper.succeed();
    }
}
