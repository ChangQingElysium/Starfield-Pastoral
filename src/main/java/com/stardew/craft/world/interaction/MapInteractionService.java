package com.stardew.craft.world.interaction;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewActionContext;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.condition.StardewConditionContext;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.mapinteraction.StardewMapInteractionActions;
import com.stardew.craft.api.v1.mapinteraction.StardewMapInteractionContext;
import com.stardew.craft.api.v1.mapinteraction.StardewMapInteractions;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.network.payload.MapInteractionHintPayload;
import com.stardew.craft.api.v1.interaction.StardewInteractionHint;
import com.stardew.craft.api.v1.interaction.StardewInteractionHintType;
import com.stardew.craft.player.PlayerDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Optional;

/** Resolves Java providers and then ordered data-pack definitions. */
public final class MapInteractionService {
    private MapInteractionService() {
    }

    public static InteractionResult interact(
            ServerPlayer player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        StardewMapInteractionContext base =
                new StardewMapInteractionContext(
                        player,
                        player.serverLevel(),
                        hand,
                        hit,
                        null);
        InteractionResult extension =
                StardewMapInteractions.dispatch(base);
        if (extension != InteractionResult.PASS) {
            return extension;
        }

        for (MapInteractionDefinition definition :
                MapInteractionRegistry.at(
                        player.serverLevel().dimension().location(),
                        hit.getBlockPos())) {
            if (!definition.matches(
                    player.serverLevel(), hit.getBlockPos())) {
                continue;
            }
            InteractionResult result = executeDefinition(
                    definition, base.withDefinition(definition.id()));
            if (result != InteractionResult.PASS) {
                return result;
            }
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult executeDefinition(
            MapInteractionDefinition definition,
            StardewMapInteractionContext context
    ) {
        for (MapInteractionDefinition.Branch branch :
                definition.branches()) {
            if (!conditionsMatch(branch, context.player())) {
                continue;
            }
            InteractionResult result = InteractionResult.SUCCESS;
            if (branch.action() != null) {
                ResourceLocation actionType = branch.action().type();
                result = StardewMapInteractionActions.execute(
                                branch.action(), context)
                        .resultOrPartial(message ->
                                StardewCraft.LOGGER.error(
                                        "[Map interactions] {} branch {} action {}: {}",
                                        definition.id(), branch.id(),
                                        actionType, message))
                        .orElse(InteractionResult.FAIL);
            }
            if (result == InteractionResult.PASS) {
                continue;
            }
            if (result == InteractionResult.FAIL) {
                return result;
            }
            if (!executeEffects(definition, branch, context.player())) {
                return InteractionResult.FAIL;
            }
            if (!branch.messages().isEmpty()) {
                ObjectDialogueService.show(
                        context.player(),
                        branch.messages().stream()
                                .map(MapInteractionDefinition.Message::component)
                                .toList());
            }
            if (definition.showsReadHint(branch)) {
                PlayerDataManager.getPlayerData(context.player())
                        .markMapInteractionRead(definition.id());
                PacketDistributor.sendToPlayer(
                        context.player(),
                        MapInteractionHintPayload.visibleBlock(
                                context.hit().getBlockPos(),
                                new StardewInteractionHint(
                                        StardewInteractionHintType.LOOK,
                                        true,
                                        definition.id())));
            }
            return result;
        }
        return InteractionResult.PASS;
    }

    /**
     * Resolves the readable definition at a block without executing effects.
     * This is used only for the crosshair hint; the actual right-click still
     * runs through {@link #interact(ServerPlayer, InteractionHand, BlockHitResult)}.
     */
    public static Optional<ReadableTarget> findReadable(
            ServerPlayer player,
            BlockPos pos
    ) {
        if (player == null || pos == null) {
            return Optional.empty();
        }
        for (MapInteractionDefinition definition :
                MapInteractionRegistry.at(
                        player.serverLevel().dimension().location(), pos)) {
            if (!definition.matches(player.serverLevel(), pos)) {
                continue;
            }
            for (MapInteractionDefinition.Branch branch :
                    definition.branches()) {
                if (!conditionsMatch(branch, player)) {
                    continue;
                }
                if (definition.showsReadHint(branch)) {
                    boolean read = PlayerDataManager.getPlayerData(player)
                            .hasReadMapInteraction(definition.id());
                    return Optional.of(new ReadableTarget(
                            definition.id(), pos.immutable(), read));
                }
                break;
            }
        }
        return Optional.empty();
    }

    private static boolean executeEffects(
            MapInteractionDefinition definition,
            MapInteractionDefinition.Branch branch,
            ServerPlayer player
    ) {
        StardewActionContext actionContext =
                StardewActionContext.forPlayer(player);
        for (var effect : branch.effects()) {
            var outcome = StardewActions.execute(effect, actionContext)
                    .resultOrPartial(message ->
                            StardewCraft.LOGGER.error(
                                    "[Map interactions] {} branch {} effect {}: {}",
                                    definition.id(), branch.id(),
                                    effect.type(), message))
                    .orElse(null);
            if (outcome == null || !outcome.success()) {
                if (outcome != null && !outcome.message().isBlank()) {
                    StardewCraft.LOGGER.error(
                            "[Map interactions] {} branch {} effect {} rejected: {}",
                            definition.id(), branch.id(),
                            effect.type(), outcome.message());
                }
                return false;
            }
        }
        return true;
    }

    private static boolean conditionsMatch(
            MapInteractionDefinition.Branch branch,
            ServerPlayer player
    ) {
        StardewConditionContext context =
                StardewConditionContext.forPlayer(player);
        for (var condition : branch.conditions()) {
            boolean matched = StardewConditions.test(condition, context)
                    .resultOrPartial(message ->
                            StardewCraft.LOGGER.error(
                                    "[Map interactions] Condition {} failed: {}",
                                    condition.type(), message))
                    .orElse(false);
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    public record ReadableTarget(
            ResourceLocation definitionId,
            BlockPos pos,
            boolean read
    ) {
    }
}
