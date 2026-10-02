package com.stardew.craft.gingerisland;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Fireplace clicks toggle; altar torches are controlled by the banana offering event. */
public final class IslandFlameBlock extends MapDecorStaticBlock {
    public static final BooleanProperty LIT = BooleanProperty.create("lit");
    private final boolean playerToggle;
    private final Vec3 flame;

    public IslandFlameBlock(Properties properties, String model, boolean playerToggle, Vec3 flame) {
        super(properties, model);
        this.playerToggle = playerToggle;
        this.flame = flame;
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT);
    }

    public void setLit(Level level, BlockPos main, boolean lit) {
        var state = level.getBlockState(main);
        if (!state.is(this) || state.getValue(PART) != Part.MAIN) return;
        var facing = state.getValue(FACING);
        for (var offset : occupiedOffsets(facing)) {
            var target = main.offset(offset.dx(), offset.dy(), offset.dz());
            var other = level.getBlockState(target);
            if (other.is(this) && main.equals(findMainPos(level, target, other)))
                level.setBlock(target, other.setValue(LIT, lit), 3);
        }
    }

    @Override public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (state.getValue(PART) == Part.EXTENSION) return super.useWithoutItem(state, level, pos, player, hit);
        if (!playerToggle) return InteractionResult.PASS;
        if (!level.isClientSide) {
            if (player instanceof ServerPlayer actor && !IslandContext.canModifyAt(actor, pos)) return InteractionResult.FAIL;
            boolean lit = !state.getValue(LIT);
            setLit(level, pos, lit);
            level.playSound(null, pos, lit ? SoundEvents.FIRECHARGE_USE : SoundEvents.FIRE_EXTINGUISH,
                    SoundSource.BLOCKS, .6F, 1F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIT) && state.getValue(PART) == Part.MAIN ? 15 : 0;
    }

    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT) || state.getValue(PART) != Part.MAIN) return;
        double dx = flame.x - .5, dz = flame.z - .5;
        var offset = switch (state.getValue(FACING)) {
            case EAST -> new Vec3(-dz, flame.y, dx);
            case SOUTH -> new Vec3(-dx, flame.y, -dz);
            case WEST -> new Vec3(dz, flame.y, -dx);
            default -> new Vec3(dx, flame.y, dz);
        };
        if (random.nextInt(4) == 0) level.addParticle(ParticleTypes.SMOKE,
                pos.getX() + .5 + offset.x, pos.getY() + offset.y, pos.getZ() + .5 + offset.z,
                0, .01, 0);
    }
}
