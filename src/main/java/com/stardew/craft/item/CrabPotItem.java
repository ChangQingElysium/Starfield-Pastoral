package com.stardew.craft.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.stardew.craft.blockentity.CrabPotBlockEntity;
import com.stardew.craft.block.ModBlocks;

/**
 * 蟹笼物品 - 特殊的放置逻辑
 * 1. 在水下：直接在点击的水源位置放置
 * 2. 从岸边：点击固体方块，在其上方的第一个水源放置
 */
public class CrabPotItem extends StardewBlockItem {
	public CrabPotItem(Block block, String itemTypeKey, int sellPrice, Item.Properties properties) {
		super(block, itemTypeKey, sellPrice, properties);
	}

	@SuppressWarnings("null")
	@Override
	public InteractionResult useOn(@SuppressWarnings("null") UseOnContext context) {
		// 如果玩家直接对着水面右键，很多情况下不会进入 useOn（取决于命中是否算“对方块使用”）。
		// 原版桶/船这类物品主要走 use() + POV hit。这里保留 useOn 作为兜底。
		// 让它在触发时也尽量使用相同逻辑。
		// 重要：UseOnContext 的 clickedPos 在水边经常会“吸”到岸上的方块（你日志里一直是 dirt）。
		// 原版船/桶的做法是基于“玩家视角射线”重新计算命中（POV hit），再按命中结果决定目标。
		var player = context.getPlayer();
		if (player != null) {
			BlockHitResult povHit = clipFromPlayerPOV(context.getLevel(), player, ClipContext.Fluid.SOURCE_ONLY);
			if (povHit.getType() == HitResult.Type.BLOCK) {
				BlockPos hitPos = povHit.getBlockPos();
				@SuppressWarnings("null")
				FluidState hitFluid = context.getLevel().getFluidState(hitPos);
				if (hitFluid.is(Fluids.WATER) && hitFluid.isSource()) {
					return placeAt(context, hitPos);
				}
				// 命中的是固体方块：尝试把蟹笼放到命中面相邻的格子（类似原版船在岸边放到水里的感觉）
				@SuppressWarnings("null")
				BlockPos adjacent = hitPos.relative(povHit.getDirection());
				@SuppressWarnings("null")
				FluidState adjFluid = context.getLevel().getFluidState(adjacent);
				if (adjFluid.is(Fluids.WATER) && adjFluid.isSource()) {
					return placeAt(context, adjacent);
				}
			}
		}

		Level level = context.getLevel();
		BlockPos clickedPos = context.getClickedPos();
		@SuppressWarnings("null")
		BlockState clickedState = level.getBlockState(clickedPos);
		// 关键：很多情况下（例如点到“空气/可替换方块”）blockState 的 fluidState 可能是 empty，
		// 但该格子的真实流体依然是水；必须用 level.getFluidState(pos) 判断。
		@SuppressWarnings("null")
		FluidState clickedFluid = level.getFluidState(clickedPos);

		// 情况1: 点击的是水源方块 - 直接在这个位置放置（水下放置）
		if (clickedFluid.is(Fluids.WATER) && clickedFluid.isSource()) {
			return placeAt(context, clickedPos);
		}

		// 情况1.5: 点击到了岸边方块，但面向的是水的一侧（或者顶面），尝试使用相邻格/上方格作为候选
		// 这能覆盖“准星对着水，但交互命中却落在泥土/草方块上”的常见情况。
		if (!clickedState.isAir()) {
			@SuppressWarnings("null")
			BlockPos sidePos = clickedPos.relative(context.getClickedFace());
			@SuppressWarnings("null")
			FluidState sideFluid = level.getFluidState(sidePos);
			if (sideFluid.is(Fluids.WATER) && sideFluid.isSource()) {
				return placeAt(context, sidePos);
			}
		}

		// 情况2: 点击的是固体方块 - 检查上方是否有水（从岸边放置）
		if (!clickedState.isAir()) {
			BlockPos abovePos = clickedPos.above();
			@SuppressWarnings("null")
			FluidState aboveFluid = level.getFluidState(abovePos);
			
			// 上方是水源方块
			if (aboveFluid.is(Fluids.WATER) && aboveFluid.isSource()) {
				return placeAt(context, abovePos);
			}
		}

		// 情况2.5：如果 useOn 给我们的命中总是落在岸上的实心方块上，再做一次“从玩家视线射线”检测。
		// Vanilla 很多物品（比如船/桶）都会用类似方式找真正瞄准到的流体。
		// 注意：上面已经按 POV hit 处理过一次；这里保留原来的 UseOnContext 分支作为兜底。

		// 情况3: 其他情况失败
		return InteractionResult.FAIL;
	}

	@SuppressWarnings("null")
	@Override
	public InteractionResultHolder<ItemStack> use(@SuppressWarnings("null") Level level, @SuppressWarnings("null") Player player, @SuppressWarnings("null") InteractionHand hand) {
		@SuppressWarnings("null")
		ItemStack stack = player.getItemInHand(hand);
		// 这里才是真正对齐原版桶/船：无论鼠标命中到空气/水/岸边方块，都会用 POV hit 去找实际瞄准位置。
		BlockHitResult povHit = clipFromPlayerPOV(level, player, ClipContext.Fluid.ANY);

		if (povHit.getType() != HitResult.Type.BLOCK) {
			return InteractionResultHolder.pass(stack);
		}

		// 先处理：如果瞄准的是已放置的蟹笼，且手里是鱼饵，则塞饵（不影响放置逻辑）
		InteractionResult bait = tryInsertBait(level, player, hand, povHit);
		if (bait.consumesAction()) {
			return new InteractionResultHolder<>(bait, stack);
		}

		BlockPos hitPos = povHit.getBlockPos();
		@SuppressWarnings("null")
		FluidState hitFluid = level.getFluidState(hitPos);
		// 命中到水（源/流动都算），先尝试直接放在该格（支持水下/水面点哪放哪）
		if (hitFluid.is(Fluids.WATER)) {
			InteractionResult r = placeAt(level, player, hand, stack, hitPos, povHit.getDirection(), povHit.getLocation());
			return new InteractionResultHolder<>(r, stack);
		}

		// 命中到固体，尝试相邻格是否是水（支持岸边放到水里）
		@SuppressWarnings("null")
		BlockPos adjacent = hitPos.relative(povHit.getDirection());
		@SuppressWarnings("null")
		FluidState adjFluid = level.getFluidState(adjacent);
		if (adjFluid.is(Fluids.WATER)) {
			InteractionResult r = placeAt(level, player, hand, stack, adjacent, povHit.getDirection(), povHit.getLocation());
			return new InteractionResultHolder<>(r, stack);
		}

		return InteractionResultHolder.pass(stack);
	}

	@SuppressWarnings("null")
	private InteractionResult tryInsertBait(Level level, Player player, InteractionHand hand, BlockHitResult hit) {
		BlockPos pos = hit.getBlockPos();
		@SuppressWarnings("null")
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.CRAB_POT.get())) {
			return InteractionResult.PASS;
		}

		@SuppressWarnings("null")
		ItemStack held = player.getItemInHand(hand);
		if (held.isEmpty() || !isBaitItem(held)) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		if (!(level.getBlockEntity(pos) instanceof CrabPotBlockEntity be)) {
			return InteractionResult.PASS;
		}

		if (!com.stardew.craft.block.utility.CrabPotBlock.claimOrCheckOwner(player, be)) {
			return InteractionResult.SUCCESS;
		}

		if (be.isReady() || be.hasBait()) {
			return InteractionResult.PASS;
		}

		ItemStack baitCopy = held.copy();
		baitCopy.setCount(1);
		be.setBait(baitCopy);

		if (!player.isCreative()) {
			held.shrink(1);
		}

		level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 1.0F, 1.0F);
		return InteractionResult.SUCCESS;
	}

	@SuppressWarnings("null")
	private boolean isBaitItem(ItemStack stack) {
		// Stardew Valley: Crab Pots accept bait items (not fish).
		// 这里复用本项目已注册的“钓鱼 - 鱼饵”物品集合（也包含 Magnet）。
		// 后续如果你们有更细的 tag/数据驱动鱼饵体系，我们再把判断挪到统一入口。
		return stack.is(ModItems.BAIT.get())
				|| stack.is(ModItems.WILD_BAIT.get())
				|| stack.is(ModItems.MAGIC_BAIT.get())
				|| stack.is(ModItems.DELUXE_BAIT.get())
				|| stack.is(ModItems.CHALLENGE_BAIT.get())
				|| stack.is(ModItems.TARGETED_BAIT.get())
				|| stack.is(ModItems.MAGNET.get());
	}

	@SuppressWarnings("null")
	public static BlockHitResult clipFromPlayerPOV(Level level, Player player, ClipContext.Fluid fluidMode) {
		double reach = player.getBlockReach();
		Vec3 start = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		Vec3 end = start.add(look.x * reach, look.y * reach, look.z * reach);
		return level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, fluidMode, CollisionContext.of(player)));
	}

	private InteractionResult placeAt(UseOnContext context, BlockPos targetPos) {
		return placeAt(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(), targetPos, context.getClickedFace(), context.getClickLocation());
	}

	@SuppressWarnings("null")
	private InteractionResult placeAt(Level level, Player player, InteractionHand hand, ItemStack stack,
							 BlockPos targetPos, Direction face, Vec3 clickLocation) {
		if (player == null) {
			return InteractionResult.FAIL;
		}
		@SuppressWarnings("null")
		BlockState targetState = level.getBlockState(targetPos);
		@SuppressWarnings("null")
		FluidState targetFluid = level.getFluidState(targetPos);

		// 放置需要水，但不强制必须是水源（桶/船的交互也允许对流动水做命中）。
		if (!targetFluid.is(Fluids.WATER)) {
			return InteractionResult.FAIL;
		}

		// 如果目标格子的方块不是可替换（例如实心方块），即使有水也不应该强行放。
		// 在大多数水域这里会是空气/水/水生植物（可替换）。
		if (!targetState.canBeReplaced()) {
			return InteractionResult.FAIL;
		}

		// 关键：走 BlockItem 的标准 place 流程。
		// 这里用 BlockPlaceContext 更接近 BlockItem 原生放置路径（比 super.useOn 的二次 use 更像“照抄原版”）。
		@SuppressWarnings("null")
		BlockHitResult hit = new BlockHitResult(clickLocation, face, targetPos, false);
		@SuppressWarnings("null")
		BlockPlaceContext placeContext = new BlockPlaceContext(level, player, hand, stack, hit);
		InteractionResult result = this.place(placeContext);
		return result;
	}
}
