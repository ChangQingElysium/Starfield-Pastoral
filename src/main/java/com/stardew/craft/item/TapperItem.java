package com.stardew.craft.item;

import com.stardew.craft.port.PortEntities;
import com.stardew.craft.api.v1.internal.tree.StardewTreeRuntimeRegistry;
import com.stardew.craft.block.ModBlocks;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.tree.prefab.PrefabTreeManager;
import com.stardew.craft.tree.prefab.PrefabTrees;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Server-authoritative attachment search; the item still uses normal BlockItem placement. */
public final class TapperItem extends StardewBlockItem {
    public TapperItem(Properties properties) {
        super(ModBlocks.TAPPER.get(), "stardewcraft.type.utility", -1, properties);
    }

    private record Attachment(BlockPos support, Direction face, double score) {}

    @Override
    public InteractionResult useOn(UseOnContext context) {
        // Generated-tree membership is server data, not a reason to reject client prediction.
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level) || context.getPlayer() == null) {
            return InteractionResult.FAIL;
        }
        var player = context.getPlayer();
        BlockPos clicked = context.getClickedPos();
        var tree = PrefabTreeManager.findOrRestoreForTapper(level, clicked);
        List<BlockPos> supports = new ArrayList<>();
        BlockPos root;
        if (tree != null) {
            if (tree.felled()) return InteractionResult.FAIL;
            root = tree.root();
            var def = PrefabTrees.defById(tree.species());
            if (def == null) return InteractionResult.FAIL;
            for (BlockPos member : tree.members()) {
                var state = level.getBlockState(member);
                if (def.isModernRoot(state) || def.isModernLog(state)) supports.add(member);
            }
        } else {
            var addon = StardewTreeRuntimeRegistry.inspectAddon(level, clicked);
            if (addon == null || !addon.mature()) return InteractionResult.FAIL;
            root = addon.root();
            for (BlockPos support : addon.tapperSupports()) {
                if (StardewTreeRuntimeRegistry.findAddonTapperSupport(level, support) != null) supports.add(support);
            }
        }
        if (!com.stardew.craft.block.utility.TapperBlock.attachedTappers(level, supports).isEmpty()) {
            player.displayClientMessage(Component.translatable("stardewcraft.tapper.tree_full"), true);
            return InteractionResult.FAIL;
        }
        List<Attachment> candidates = new ArrayList<>();
        for (BlockPos support : supports) {
            // Keep the machine accessible at the base, including low bent trunks.
            if (tree != null && (support.getY() < root.getY() || support.getY() > root.getY() + 3)) continue;
            for (Direction face : Direction.Plane.HORIZONTAL) {
                BlockPos place = support.relative(face);
                if (!level.hasChunkAt(place) || !level.isEmptyBlock(place)
                        || !level.getFluidState(place).isEmpty() || !PortEntities.canInteractWithBlock(player, place, 0)) continue;
                if (player instanceof ServerPlayer serverPlayer && level.dimension() == ModDimensions.STARDEW_VALLEY
                        && !FarmAreaProtectionEvents.canModifyAt(serverPlayer, place)) continue;
                Vec3 hit = Vec3.atCenterOf(support).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.499));
                var sight = level.clip(new ClipContext(player.getEyePosition(), hit,
                        ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                if (sight.getType() != HitResult.Type.BLOCK || !sight.getBlockPos().equals(support)
                        || sight.getDirection() != face) continue;
                double score = player.getEyePosition().distanceToSqr(hit)
                        + Math.abs(support.getY() - root.getY() - 1) * 4
                        + (face == context.getClickedFace() ? 0 : 2);
                candidates.add(new Attachment(support, face, score));
            }
        }
        candidates.sort(Comparator.comparingDouble(Attachment::score)
                .thenComparingLong(a -> a.support().asLong()).thenComparingInt(a -> a.face().ordinal()));
        for (Attachment candidate : candidates) {
            var hit = new BlockHitResult(Vec3.atCenterOf(candidate.support())
                    .add(Vec3.atLowerCornerOf(candidate.face().getNormal()).scale(.5)),
                    candidate.face(), candidate.support(), false);
            InteractionResult result = super.useOn(new UseOnContext(level, player, context.getHand(),
                    context.getItemInHand(), hit));
            if (result.consumesAction()) return result;
        }
        player.displayClientMessage(Component.translatable("stardewcraft.tapper.no_space"), true);
        return InteractionResult.FAIL;
    }
}
