package com.stardew.craft.block.decor;

import com.stardew.craft.fishing.server.BobberStyleService;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class BobberStyleMachineBlock extends MapDecorStaticBlock {
    public BobberStyleMachineBlock(Properties properties){
        super(properties.lightLevel(state->state.getValue(PART)==Part.EXTENSION?10:0),"stardewcraft:block/decor/bobber_style_machine",0,0,3,16,32,13);
    }
    @Override
    public InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        BlockPos main=findMainPos(level,pos,state);
        if(main==null)return InteractionResult.PASS;
        if(player instanceof ServerPlayer server)BobberStyleService.open(server,main);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
