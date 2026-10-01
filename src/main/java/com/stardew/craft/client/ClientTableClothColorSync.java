package com.stardew.craft.client;

import com.stardew.craft.blockentity.TableDisplayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientTableClothColorSync {
    private ClientTableClothColorSync() {
    }

    public static void apply(BlockPos pos, int colorIndex) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || !level.isLoaded(pos)) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe) {
            tableBe.setClothColor(colorIndex);
        }
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 11);
    }
}
