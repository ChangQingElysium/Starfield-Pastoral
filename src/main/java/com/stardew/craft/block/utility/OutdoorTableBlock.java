package com.stardew.craft.block.utility;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.blockentity.TableDisplayBlockEntity;
import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import com.stardew.craft.port.net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import com.stardew.craft.port.PortBlockInteraction;
import net.minecraft.world.phys.shapes.*;

/** Each connected cell keeps its own displayed item and saved wood variant. */
public final class OutdoorTableBlock extends Block implements EntityBlock, PortBlockInteraction {
    public static final IntegerProperty CONNECTIONS = IntegerProperty.create("connections", 0, 255);
    public static final IntegerProperty VARIANT = IntegerProperty.create("variant", 0, 2);
    private static final int[][] OFFSETS = {{0,-1},{1,0},{0,1},{-1,0},{1,-1},{1,1},{-1,1},{-1,-1}};
    public OutdoorTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(CONNECTIONS, 0).setValue(VARIANT, 0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder) {
        builder.add(CONNECTIONS, VARIANT);
    }
    public static int canonical(int mask) {
        for (int i=0;i<4;i++) if ((mask & (1<<i))==0 || (mask & (1<<((i+1)%4)))==0) mask &= ~(1<<(4+i));
        return mask;
    }
    private BlockState connected(BlockState state, BlockGetter level, BlockPos pos) {
        int mask=0;
        for(int i=0;i<8;i++) if(level.getBlockState(pos.offset(OFFSETS[i][0],0,OFFSETS[i][1])).is(this)) mask|=1<<i;
        return state.setValue(CONNECTIONS,canonical(mask));
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        Integer fixed=PortItemData.getOrDefault(context.getItemInHand(), DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).get(VARIANT);
        int variant=fixed==null ? (context.getLevel().isClientSide?0:context.getLevel().random.nextInt(3)):fixed;
        return connected(defaultBlockState().setValue(VARIANT,variant),context.getLevel(),context.getClickedPos());
    }
    private void refresh(Level level,BlockPos pos) {
        if(level.isClientSide)return;
        // Diagonal changes also change concave corner legs; cardinal notifications alone miss them.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            BlockPos at=pos.offset(x,0,z);BlockState before=level.getBlockState(at);
            if(before.is(this)) {BlockState after=connected(before,level,at);if(before!=after)level.setBlock(at,after,2);}
        }
    }
    @Override public void onPlace(BlockState state,Level level,BlockPos pos,BlockState old,boolean moving){
        super.onPlace(state,level,pos,old,moving);if(!old.is(this))refresh(level,pos);
    }
    @Override public BlockState updateShape(BlockState state,Direction side,BlockState neighbor,LevelAccessor level,BlockPos pos,BlockPos other){
        return connected(state,level,pos);
    }
    @Override public VoxelShape getShape(BlockState state,BlockGetter level,BlockPos pos,CollisionContext context){
        return ModelVoxelShapeCache.shapeFromModelId("stardewcraft:block/outdoor_table/spring/"+canonical(state.getValue(CONNECTIONS))+"_0");
    }
    @Override public boolean isPathfindable(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, net.minecraft.world.level.pathfinder.PathComputationType type){return false;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new TableDisplayBlockEntity(pos,state);}
    @Override public void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moving){
        if(!next.is(this)&&!level.isClientSide&&level.getBlockEntity(pos) instanceof TableDisplayBlockEntity table&&table.hasDisplayItem())
            popResource(level,pos,table.removeDisplayItem());
        super.onRemove(state,level,pos,next,moving);
        if(!next.is(this))refresh(level,pos);
    }
    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (OakTableBlock.isTableclothItem(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!(level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (tableBe.hasDisplayItem()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (level.isClientSide) {
            return ItemInteractionResult.sidedSuccess(true);
        }

        ItemStack placed = stack.copy();
        placed.setCount(1);
        float snappedYaw = (float) net.minecraft.core.Direction.fromYRot(player.getYRot()).toYRot();
        tableBe.setDisplayItem(placed, snappedYaw);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7f, 1.0f);
        return ItemInteractionResult.sidedSuccess(false);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }

        if (!(level.getBlockEntity(pos) instanceof TableDisplayBlockEntity tableBe)) {
            return InteractionResult.PASS;
        }
        if (!tableBe.hasDisplayItem()) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack removed = tableBe.removeDisplayItem();
        if (removed.isEmpty()) {
            return InteractionResult.PASS;
        }

        if (!player.addItem(removed)) {
            player.drop(removed, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.7f, 1.0f);
        return InteractionResult.CONSUME;
    }
}
