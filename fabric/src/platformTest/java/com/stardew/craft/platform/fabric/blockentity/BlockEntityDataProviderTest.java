package com.stardew.craft.platform.fabric.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockEntityDataProviderTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test void chunkSnapshotsLoadEmptyStateWhileDefaultEmptyUpdatePacketsPreserveIt() throws Exception {
        var entity = new Fixture();
        var tag = new CompoundTag();
        tag.putInt("value", 9);
        entity.onDataPacket(null, packet(tag), RegistryAccess.EMPTY);
        assertEquals(9, entity.value);
        assertEquals(1, entity.loads);
        entity.onDataPacket(null, packet(new CompoundTag()), RegistryAccess.EMPTY);
        assertEquals(9, entity.value);
        assertEquals(1, entity.loads);
        entity.handleUpdateTag(new CompoundTag(), RegistryAccess.EMPTY);
        assertEquals(0, entity.value);
        assertEquals(2, entity.loads);
    }

    @Test void customFullSnapshotHandlerReceivesEmptyPacketAndCanClearOldVisualState() throws Exception {
        var entity = new Fixture() {
            @Override public void onDataPacket(net.minecraft.network.Connection connection,
                    ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
                handleUpdateTag(packet.getTag(), registries);
            }
        };
        var tag = new CompoundTag();
        tag.putInt("value", 17);
        entity.onDataPacket(null, packet(tag), RegistryAccess.EMPTY);
        assertEquals(17, entity.value);
        entity.onDataPacket(null, packet(new CompoundTag()), RegistryAccess.EMPTY);
        assertEquals(0, entity.value);
        assertEquals(2, entity.loads);
    }

    private static ClientboundBlockEntityDataPacket packet(CompoundTag tag) throws Exception {
        var constructor = ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(BlockPos.class,
                BlockEntityType.class, CompoundTag.class);
        constructor.setAccessible(true);
        return constructor.newInstance(BlockPos.ZERO, BlockEntityType.CHEST, tag);
    }

    private static class Fixture extends FabricBlockEntity {
        int value, loads;
        Fixture() { super(BlockEntityType.CHEST, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()); }
        @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            value = tag.getInt("value");
            loads++;
        }
    }
}
