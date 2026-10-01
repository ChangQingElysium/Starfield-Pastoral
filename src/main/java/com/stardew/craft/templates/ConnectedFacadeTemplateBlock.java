package com.stardew.craft.templates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Connections are local up/right/down/left, so turning a saved facade preserves its joints. */
public final class ConnectedFacadeTemplateBlock extends WallCompositeTemplateBlock {
    public static final IntegerProperty CONNECTIONS=IntegerProperty.create("connections",0,15);
    public ConnectedFacadeTemplateBlock(TemplateShape shape,Properties properties) {
        super(shape,properties);
        registerDefaultState(defaultBlockState().setValue(CONNECTIONS,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        super.createBlockStateDefinition(builder);builder.add(CONNECTIONS);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state=super.getStateForPlacement(context);
        return state==null?null:connect(state,context.getLevel(),context.getClickedPos());
    }
    @Override public BlockState updateShape(BlockState state,Direction direction,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other) {
        return connect(state,level,pos);
    }
    @Override
    public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        if (mirror == net.minecraft.world.level.block.Mirror.NONE) return state;
        int mask = connections(state);
        return super.mirror(state, mirror).setValue(CONNECTIONS, (mask & 5) | ((mask & 2) << 2) | ((mask & 8) >> 2));
    }

    private BlockState connect(BlockState state,BlockGetter level,BlockPos pos) {
        Direction front=state.getValue(FACING);
        Direction[] directions={Direction.UP,front.getClockWise(),Direction.DOWN,front.getCounterClockWise()};
        int mask=0;
        for(int i=0;i<4;i++) {
            BlockState other=level.getBlockState(pos.relative(directions[i]));
            if(!(other.getBlock() instanceof WallCompositeTemplateBlock block) || other.getValue(FACING)!=front)continue;
            TemplateShape shape=block.templateShape();
            if(templateShape().isWindow()?shape.isWindow():shape==TemplateShape.WALL_JUNCTION
                    || shape==TemplateShape.WALL_BEAM && i%2==1 || shape==TemplateShape.WALL_POST && i%2==0) mask|=1<<i;
        }
        return state.setValue(CONNECTIONS,mask);
    }
    public static int connections(BlockState state) { return state.hasProperty(CONNECTIONS)?state.getValue(CONNECTIONS):0; }
}
