package com.stardew.craft.block.utility;

import com.stardew.craft.block.shape.ModelVoxelShapeCache;
import com.stardew.craft.blockentity.CrabPotBlockEntity;
import com.stardew.craft.blockentity.ModBlockEntities;
import com.stardew.craft.blockentity.UtilityDropHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 蟹笼方块 - 用于在水中捕获海鲜
 * 必须放置在水方块中
 * 需要鱼饵才能捕获
 */
public class CrabPotBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
	public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
	public static final BooleanProperty WORKING = BooleanProperty.create("working");
	
	// 交互/选中轮廓用整格，避免“点不到/交互面积怪”。
	private static final VoxelShape INTERACTION_SHAPE = Block.box(0, 0, 0, 16, 16, 16);
	private static final VoxelShape[] COLLISION_SHAPES = ModelVoxelShapeCache.horizontalShapes("stardewcraft:block/utility/crab_pot", Direction.NORTH);

	@SuppressWarnings("null")
	public CrabPotBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any()
			.setValue(FACING, Direction.NORTH)
			.setValue(WATERLOGGED, true)
			.setValue(WORKING, false)); // 默认含水
	}

	@Override
	protected void createBlockStateDefinition(@SuppressWarnings("null") StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, WATERLOGGED, WORKING);
	}

	@Override
	public RenderShape getRenderShape(@SuppressWarnings("null") BlockState state) {
		return UtilityMachineRenderState.forWorkingState(state.getValue(WORKING));
	}

	@SuppressWarnings("null")
	@Override
	protected void spawnAfterBreak(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") net.minecraft.server.level.ServerLevel level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") ItemStack stack, boolean dropExperience) {
		// 兜底：确保破坏时有碎片粒子。
		// 2001 = levelEvent 的“方块破坏粒子+音效”，data 是 blockstate 的 id。
		level.levelEvent(2001, pos, Block.getId(state));
		super.spawnAfterBreak(state, level, pos, stack, dropExperience);
	}

	@Override
	public VoxelShape getShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
		return INTERACTION_SHAPE;
	}

	@SuppressWarnings("null")
	@Override
	public VoxelShape getCollisionShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") CollisionContext context) {
		return COLLISION_SHAPES[ModelVoxelShapeCache.horizontalIndex(state.getValue(FACING))];
	}

	@Override
	@Nullable
	public BlockEntity newBlockEntity(@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state) {
		return new CrabPotBlockEntity(pos, state);
	}

	@Override
	@Nullable
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockState state, @SuppressWarnings("null") BlockEntityType<T> type) {
		if (level.isClientSide) {
			return null;
		}
		if (type != ModBlockEntities.CRAB_POT.get()) {
			return null;
		}
		return (lvl, pos, st, be) -> CrabPotBlockEntity.serverTick(lvl, pos, st, (CrabPotBlockEntity) be);
	}

	@SuppressWarnings("null")
	@Override
	public BlockState getStateForPlacement(@SuppressWarnings("null") BlockPlaceContext context) {
		return defaultBlockState()
			.setValue(FACING, context.getHorizontalDirection().getOpposite())
			.setValue(WATERLOGGED, true);
	}

	@Override
	@SuppressWarnings("null")
	public void setPlacedBy(@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState state, @Nullable LivingEntity placer, @SuppressWarnings("null") ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (level.isClientSide || !(placer instanceof Player player)) {
			return;
		}
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof CrabPotBlockEntity crabPot) {
			crabPot.setOwnerIfAbsent(player.getUUID());
		}
	}
	
	@SuppressWarnings("null")
	@Override
	public FluidState getFluidState(@SuppressWarnings("null") BlockState state) {
		return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
	}

	@SuppressWarnings("null")
	@Override
	protected BlockState updateShape(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Direction direction, @SuppressWarnings("null") BlockState neighborState, @SuppressWarnings("null") LevelAccessor level,
			@SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockPos neighborPos) {
		// 关键：作为含水方块，邻居更新时要继续维持水的tick，否则经常会被流体系统“纠正”导致方块被替换/弹出。
		if (state.getValue(WATERLOGGED)) {
			level.scheduleTick(pos, (Fluid) Fluids.WATER, Fluids.WATER.getTickDelay(level));
		}
		return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
	}

	@SuppressWarnings("null")
	@Override
	protected ItemInteractionResult useItemOn(@SuppressWarnings("null") ItemStack stack, @SuppressWarnings("null") BlockState state,
			@SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos,
			@SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand,
			@SuppressWarnings("null") BlockHitResult hit) {
		InteractionResult result = useWithoutItem(state, level, pos, player, hit);
		return result.consumesAction()
				? ItemInteractionResult.sidedSuccess(level.isClientSide)
				: ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
	}

	@SuppressWarnings("null")
	@Override
	protected InteractionResult useWithoutItem(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockHitResult hit) {
		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		@SuppressWarnings("null")
		BlockEntity be = level.getBlockEntity(pos);
		if (!(be instanceof CrabPotBlockEntity crabPot)) {
			return InteractionResult.PASS;
		}

		if (!claimOrCheckOwner(player, crabPot)) {
			return InteractionResult.SUCCESS;
		}

		// 如果有产物，收获
		if (crabPot.isReady()) {
			ItemStack product = crabPot.getProduct();
			if (!product.isEmpty()) {
				ItemStack caught = product.copy();
				if (player.addItem(product)) {
					crabPot.clearProduct();
					if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
							&& caught.is(com.stardew.craft.core.ModTags.Items.ALL_FISHING_CATCHES)) {
						com.stardew.craft.fishing.server.FishingCatchProgress.record(
								serverPlayer, caught, caught.getCount());
					}
					// 收获后：ready 变 false，同时如果没有饵就不工作
					@SuppressWarnings("null")
					BlockState newState = level.getBlockState(pos);
					boolean workingNow = crabPot.hasBait() && !crabPot.isReady();
					if (newState.hasProperty(WORKING) && newState.getValue(WORKING) != workingNow) {
						level.setBlock(pos, newState.setValue(WORKING, workingNow), 3);
					}
					UtilityDropHelper.grantHarvestRewards(level, pos, player);
					if (player instanceof net.minecraft.server.level.ServerPlayer xpPlayer) {
						// CrabPot.checkForAction: who.gainExperience(1, 5) on every harvest.
						com.stardew.craft.player.PlayerStardewDataAPI.addExperience(
								xpPlayer, com.stardew.craft.player.SkillType.FISHING, 5);
					}
					return InteractionResult.SUCCESS;
				}
			}
		}

		// 放鱼饵：之前放在 CrabPotItem#use() 里，导致“拿鱼饵右键蟹笼”不会触发（因为手里不是蟹笼物品）。
		// 这里改为和原版一致：对着已放置的蟹笼，手持鱼饵右键即可塞饵。
		ItemStack held = player.getMainHandItem();
		if (!held.isEmpty() && isBaitItem(held) && !crabPot.hasBait() && !crabPot.isReady()) {
			ItemStack baitCopy = held.copy();
			baitCopy.setCount(1);
			crabPot.setBait(baitCopy);
			// 塞饵后进入工作态
			boolean workingNow = crabPot.hasBait() && !crabPot.isReady();
			if (state.hasProperty(WORKING) && state.getValue(WORKING) != workingNow) {
				level.setBlock(pos, state.setValue(WORKING, workingNow), 3);
			}
			if (!player.isCreative()) {
				held.shrink(1);
			}
			level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
			return InteractionResult.SUCCESS;
		}

		return InteractionResult.PASS;
	}

	public static boolean claimOrCheckOwner(Player player, CrabPotBlockEntity crabPot) {
		if (crabPot.canAccess(player.getUUID())) {
			crabPot.setOwnerIfAbsent(player.getUUID());
			return true;
		}
		player.displayClientMessage(Component.translatable("message.stardew_craft.crab_pot.not_owner"), true);
		return false;
	}

	@SuppressWarnings("null")
	private static boolean isBaitItem(ItemStack stack) {
		return stack.is(com.stardew.craft.item.ModItems.BAIT.get())
				|| stack.is(com.stardew.craft.item.ModItems.WILD_BAIT.get())
				|| stack.is(com.stardew.craft.item.ModItems.MAGIC_BAIT.get())
				|| stack.is(com.stardew.craft.item.ModItems.DELUXE_BAIT.get())
				|| stack.is(com.stardew.craft.item.ModItems.CHALLENGE_BAIT.get())
				|| stack.is(com.stardew.craft.item.ModItems.MAGNET.get());
	}

	@SuppressWarnings("null")
	@Override
	public void onRemove(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Level level, @SuppressWarnings("null") BlockPos pos, @SuppressWarnings("null") BlockState newState, boolean isMoving) {
		if (!state.is(newState.getBlock())) {
			@SuppressWarnings("null")
			BlockEntity be = level.getBlockEntity(pos);
			if (be instanceof CrabPotBlockEntity crabPot) {
				// 掉落鱼饵和产物
				if (crabPot.hasBait()) {
					Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), crabPot.getBait());
				}
				if (!crabPot.getProduct().isEmpty()) {
					Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), crabPot.getProduct());
				}
			}
		}
		super.onRemove(state, level, pos, newState, isMoving);
	}

	@Override
	public float getDestroyProgress(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Player player, @SuppressWarnings("null") BlockGetter level, @SuppressWarnings("null") BlockPos pos) {
		// 需求：徒手也能快速拆（类似羊毛）。
		if (player.getAbilities().instabuild) {
			return 1.0f;
		}
		@SuppressWarnings("null")
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness == -1.0f) {
			return 0.0f;
		}
		// 不考虑工具加速：徒手也快。
		float digSpeed = 1.0f;
		int divisor = 30; // 强制当作“可正确采集”来计算进度，达到徒手快拆的效果
		return digSpeed / hardness / (float) divisor;
	}

	@SuppressWarnings("null")
	@Override
	public List<ItemStack> getDrops(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") LootParams.Builder params) {
		// 蟹笼本身掉落（不掉落内容物，内容物在 onRemove 处理）
		return super.getDrops(state, params);
	}

	@SuppressWarnings("null")
	public BlockState rotate(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@SuppressWarnings("null")
	@Override
	public BlockState mirror(@SuppressWarnings("null") BlockState state, @SuppressWarnings("null") Mirror mirror) {
		return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
	}
}
