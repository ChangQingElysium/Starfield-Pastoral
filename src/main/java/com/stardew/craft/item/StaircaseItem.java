package com.stardew.craft.item;

import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.block.mine.MineBuildingTheme;
import com.stardew.craft.block.mine.MineLadderBlock;
import com.stardew.craft.core.ModMiningDimensions;
import com.stardew.craft.mining.*;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/** SDV (BC)71: consume one crafted staircase to open the clicked mine floor tile. */
public final class StaircaseItem extends SimpleStardewItem {
    public StaircaseItem(Properties properties) {
        super("stardewcraft.type.tool", -1, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var level = context.getLevel();
        var player = context.getPlayer();
        var pos = context.getClickedPos();
        if (player == null || level.dimension() != ModMiningDimensions.STARDEW_MINING) {
            return InteractionResult.PASS;
        }
        int floor = OrdinaryMineRuntime.floorAt(pos);
        MineBuildingTheme theme = null;
        for (var candidate : MineBuildingTheme.values()) {
            if (candidate.rank(level.getBlockState(pos)) > 0) {
                theme = candidate;
                break;
            }
        }
        if (theme == null || floor <= 0 || floor == 120
                || pos.getY() != MiningCoordinates.FIXED_Y - 1
                || !player.mayBuild() || !level.mayInteract(player, pos)
                || !level.getBlockState(pos.above()).isAir()
                || !level.getBlockState(pos.above(2)).isAir()) {
            if (!level.isClientSide) player.displayClientMessage(Component.translatable("message.stardewcraft.staircase.invalid"), true);
            return InteractionResult.FAIL;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        var manager = MineFloorDataManager.get(serverPlayer.serverLevel());
        var data = manager.getFloorData(floor);
        if (data == null || MiningDataManager.getPlayerData(serverPlayer).getCurrentFloor() != floor) {
            return InteractionResult.FAIL;
        }
        var state = ModBlocks.MINE_LADDER.get().defaultBlockState()
                .setValue(MineLadderBlock.THEME, MineLadderBlock.Theme.valueOf(theme.name()))
                .setValue(MineLadderBlock.FACING, context.getHorizontalDirection().getOpposite())
                .setValue(MineLadderBlock.SHAFT, false);
        if (!level.setBlock(pos, state, 3)) return InteractionResult.FAIL;
        // Crafted stairs do not consume the stone-discovery roll or roll a Skull Cavern shaft.
        data.setLadderFound(true);
        data.setLadderPos(pos.immutable());
        manager.setFloorData(floor, data);
        SkullCavernSessionManager.recordCraftedStaircasePlaced(serverPlayer);
        com.stardew.craft.port.PortItemStacks.consume(context.getItemInHand(), 1, player);
        player.awardStat(Stats.ITEM_USED.get(this));
        level.playSound(null, pos, ModSounds.HOE_HIT.get(), SoundSource.BLOCKS, 1, 1);
        return InteractionResult.CONSUME;
    }
}
