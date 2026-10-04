package com.stardew.craft.gametest;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.block.utility.totem.TotemPoleBlock;
import com.stardew.craft.block.utility.totem.TotemType;
import com.stardew.craft.blockentity.TotemPoleBlockEntity;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.totem.TeleportTotemItem;
import com.stardew.craft.totem.SystemTotemManager;
import com.stardew.craft.totem.TotemPoleTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@GameTestHolder("stardewcraft_utility_facing")
@PrefixGameTestTemplate(false)
public final class SystemTotemGameTests {
    @GameTest(templateNamespace="stardewcraft_utility_facing", template="ring_utilities", timeoutTicks=200)
    public static void mountainAdoptsAuthoredPoleAndOnlyRemovesGeneratedDuplicates(GameTestHelper h) throws Exception {
        var level = h.getLevel();
        var tracker = TotemPoleTracker.get(level);
        var oldEntry = tracker.getPole(1);
        // Coordinates and inactive MAIN state verified against bundled r.0.-1.mca.
        var authored = new BlockPos(56, 85, -128);
        var duplicate = new BlockPos(52, 88, -128);
        var privatePole = new BlockPos(75, 81, -105);
        var unrelated = new BlockPos(-290, -14, 256);
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        Map<BlockPos, CompoundTag> entities = new LinkedHashMap<>();
        int privateId = -1;
        for (var anchor : List.of(authored, duplicate, privatePole, unrelated)) {
            for (int y = -1; y <= 2; y++) {
                var cell = anchor.above(y);
                states.put(cell, level.getBlockState(cell));
                if (level.getBlockEntity(cell) != null)
                    entities.put(cell, level.getBlockEntity(cell).saveWithFullMetadata());
            }
        }
        var ensure = SystemTotemManager.class.getDeclaredMethod("ensureMountainSystemPole", ServerLevel.class);
        ensure.setAccessible(true);
        try {
            MapDecorStaticBlock.runWithDropsSuppressed(() -> states.keySet().forEach(
                    cell -> level.setBlock(cell, Blocks.AIR.defaultBlockState(), 18)));
            var block = (TotemPoleBlock) ModBlocks.TOTEM_POLE_MOUNTAIN.get();
            var inactive = block.defaultBlockState().setValue(MapDecorStaticBlock.FACING, Direction.SOUTH);
            level.setBlock(authored.below(), ModBlocks.DARK_GRASS_BLOCK.get().defaultBlockState(), 18);
            level.setBlock(authored, inactive, 18);
            var originalPole = (TotemPoleBlockEntity) level.getBlockEntity(authored);
            level.setBlock(duplicate, inactive, 18); block.placeExtensions(level, duplicate, inactive);
            ((TotemPoleBlockEntity) level.getBlockEntity(duplicate)).initSystemPole(level, 1, TotemType.MOUNTAIN.getDefaultNameKey());
            level.setBlock(privatePole, inactive, 18); block.placeExtensions(level, privatePole, inactive);
            var playerPole = (TotemPoleBlockEntity) level.getBlockEntity(privatePole);
            playerPole.initOnPlace(level); playerPole.setPoleName("Player-owned mountain pole"); privateId = playerPole.getPoleId();
            level.setBlock(unrelated, Blocks.CHEST.defaultBlockState(), 18);

            ensure.invoke(null, level);
            h.assertTrue(level.getBlockEntity(authored) == originalPole && originalPole.isSystemPole()
                            && originalPole.getPoleId() == 1 && originalPole.isActivated()
                            && originalPole.getPoleName().equals(TotemType.MOUNTAIN.getDefaultNameKey()),
                    "Authored pole was replaced or did not receive its system ID, activation and translated name");
            h.assertTrue(level.getBlockState(authored).getValue(TotemPoleBlock.ACTIVATED)
                            && level.getBlockState(authored).getValue(MapDecorStaticBlock.FACING) == Direction.SOUTH
                            && level.getBlockState(authored.above()).is(block)
                            && level.getBlockState(authored.above()).getValue(MapDecorStaticBlock.PART) == MapDecorStaticBlock.Part.EXTENSION,
                    "Authored pole lost facing, remained visually inactive or has no upper interaction cell");
            h.assertTrue(tracker.getDefaultPole(TotemType.MOUNTAIN).pos().equals(authored)
                            && tracker.getPole(1).pos().equals(authored), "Default/bound ID still targets the duplicate");
            h.assertTrue(level.getBlockState(duplicate).equals(ModBlocks.GRASS_BLOCK.get().defaultBlockState())
                            && level.getBlockState(duplicate.above()).isAir(), "Generated duplicate or its overwritten terrain was not restored");
            h.assertTrue(level.getBlockEntity(privatePole) == playerPole && !playerPole.isSystemPole()
                            && playerPole.getPoleName().equals("Player-owned mountain pole") && tracker.getPole(privateId) != null
                            && level.getBlockState(unrelated).is(Blocks.CHEST), "Migration removed a player pole or unrelated furniture");
            ensure.invoke(null, level);
            h.assertTrue(level.getBlockEntity(authored) == originalPole && tracker.getPole(1).pos().equals(authored),
                    "Repeated startup replaced or moved the canonical pole");
            var fallback = TeleportTotemItem.class.getDeclaredMethod("getDefaultPosition", ServerPlayer.class);
            fallback.setAccessible(true);
            h.assertTrue(fallback.invoke(ModItems.WARP_TOTEM_MOUNTAIN.get(), (Object) null).equals(authored.south()),
                    "Fallback landing still points at the old mountain pole");

            // Missing/modified authored sites must not cause a fresh pole to overwrite player blocks.
            MapDecorStaticBlock.runWithDropsSuppressed(() -> level.setBlock(authored, Blocks.CHEST.defaultBlockState(), 18));
            ensure.invoke(null, level);
            h.assertTrue(level.getBlockState(authored).is(Blocks.CHEST)
                            && level.getBlockState(duplicate).is(ModBlocks.GRASS_BLOCK.get()),
                    "Missing pregen pole caused new construction or overwrote the site");
        } finally {
            MapDecorStaticBlock.runWithDropsSuppressed(() -> states.forEach((cell, state) -> level.setBlock(cell, state, 18)));
            entities.forEach((cell, tag) -> {
                if (level.getBlockEntity(cell) != null) level.getBlockEntity(cell).load(tag);
            });
            if (privateId >= 0) tracker.unregister(privateId);
            tracker.unregister(1);
            if (oldEntry != null) tracker.register(1, oldEntry);
        }
        h.succeed();
    }
}
