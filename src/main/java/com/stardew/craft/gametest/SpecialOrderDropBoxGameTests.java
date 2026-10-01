package com.stardew.craft.gametest;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.specialorder.SpecialOrderDropBoxAnchor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.nio.ByteBuffer;
import java.util.zip.InflaterInputStream;

@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class SpecialOrderDropBoxGameTests {
    private SpecialOrderDropBoxGameTests() {}

    @GameTest(templateNamespace = StardewCraft.MODID, template = "ring_utilities")
    public static void robinDeliveryAnchorMatchesWoodPileInShippedMap(GameTestHelper helper) throws Exception {
        var anchor = SpecialOrderDropBoxAnchor.ROBIN_WOOD;
        var pos = anchor.min();
        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;
        String resource = "/pregen/stardew_valley/region/r." + (chunkX >> 5) + "." + (chunkZ >> 5) + ".mca";
        byte[] region;
        try (var stream = SpecialOrderDropBoxGameTests.class.getResourceAsStream(resource)) {
            helper.assertTrue(stream != null, "Delivery anchor lies outside the shipped map");
            region = stream.readAllBytes();
        }
        var bytes = ByteBuffer.wrap(region);
        int chunkOffset = (bytes.getInt(4 * ((chunkX & 31) + (chunkZ & 31) * 32)) >>> 8) * 4096;
        helper.assertTrue(chunkOffset > 0, "Delivery anchor chunk is missing from the shipped map");
        int length = bytes.getInt(chunkOffset);
        helper.assertTrue(region[chunkOffset + 4] == 2, "Expected zlib map chunk compression");
        CompoundTag chunk;
        try (var input = new DataInputStream(new InflaterInputStream(new ByteArrayInputStream(region, chunkOffset + 5, length - 1)))) {
            chunk = NbtIo.read(input, NbtAccounter.UNLIMITED);
        }
        for (Tag entry : chunk.getList("sections", Tag.TAG_COMPOUND)) {
            CompoundTag section = (CompoundTag) entry;
            if (section.getByte("Y") != (pos.getY() >> 4)) continue;
            var states = section.getCompound("block_states");
            var palette = states.getList("palette", Tag.TAG_COMPOUND);
            int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            int perLong = 64 / bits;
            int index = (pos.getY() & 15) * 256 + (pos.getZ() & 15) * 16 + (pos.getX() & 15);
            long[] data = states.getLongArray("data");
            int paletteIndex = data.length == 0 ? 0 : (int) (data[index / perLong] >>> ((index % perLong) * bits) & ((1L << bits) - 1));
            helper.assertTrue(palette.getCompound(paletteIndex).getString("Name").equals("stardewcraft:wood_bundle"),
                    "Robin's delivery anchor must point at the actual wood pile, not air or another furnishing");
            helper.assertTrue(SpecialOrderDropBoxAnchor.at(pos).orElseThrow() == anchor, "Wood pile click does not resolve to Robin's delivery");
            helper.succeed();
            return;
        }
        helper.fail("Robin delivery anchor section is absent from the shipped map");
    }
}
