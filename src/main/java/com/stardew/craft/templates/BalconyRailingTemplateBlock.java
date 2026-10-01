package com.stardew.craft.templates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** A boundary-mounted railing, not a glass pane or a centered vanilla fence. */
public final class BalconyRailingTemplateBlock extends MaterialTemplateBlock {
    public static final IntegerProperty PROFILE=IntegerProperty.create("profile",0,7);
    public BalconyRailingTemplateBlock(Properties properties){super(TemplateShape.BALCONY_RAILING,properties);registerDefaultState(defaultBlockState().setValue(PROFILE,0));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){super.createBlockStateDefinition(builder);builder.add(PROFILE);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){BlockState state=super.getStateForPlacement(context);return state==null?null:connect(state,context.getLevel(),context.getClickedPos());}
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){return connect(state,level,pos);}
    private BlockState connect(BlockState state,BlockGetter level,BlockPos pos){
        Direction front=state.getValue(FACING),left=front.getCounterClockWise(),right=front.getClockWise();
        boolean l=joins(level.getBlockState(pos.relative(left)),front,left),r=joins(level.getBlockState(pos.relative(right)),front,right);
        BlockState back=level.getBlockState(pos.relative(front.getOpposite()));int profile;
        if(back.is(this)&&back.getValue(FACING)==right)profile=l?4:6;
        else if(back.is(this)&&back.getValue(FACING)==left)profile=r?5:7;
        else profile=l?(r?2:3):(r?1:0);
        return state.setValue(PROFILE,profile);
    }
    private boolean joins(BlockState other,Direction front,Direction side){return other.is(this)&&(other.getValue(FACING)==front||other.getValue(FACING)==side);}
    @Override public BlockState mirror(BlockState state,Mirror mirror){
        BlockState result=super.mirror(state,mirror);if(mirror==Mirror.NONE)return result;
        int p=state.getValue(PROFILE);return result.setValue(PROFILE,switch(p){case 1->3;case 3->1;case 4->5;case 5->4;case 6->7;case 7->6;default->p;});
    }
}
