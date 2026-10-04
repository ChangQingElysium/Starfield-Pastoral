package com.stardew.craft.block.utility;

import com.mojang.serialization.MapCodec;
import com.stardew.craft.blockentity.FriendshipDoorBlockEntity;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.ObjectDialogueService;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import net.minecraft.world.InteractionHand;
import com.stardew.craft.port.PortBlockInteraction;

@SuppressWarnings("null")
public class FriendshipDoorBlock extends DoorBlock implements EntityBlock, PortBlockInteraction {

    public FriendshipDoorBlock(Properties properties) {
        super(properties, BlockSetType.OAK); // PORT(1.20.1): 1.20.1 constructor takes Properties first
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FriendshipDoorBlockEntity(pos, state);
    }

    // PORT(1.20.1): replay the 1.21 useItemOn/useWithoutItem dispatch.
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        return PortBlockInteraction.dispatch(this, state, level, pos, player, hand, hit);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!canPlayerUseDoor(level, state, pos, player)) {
            if (!level.isClientSide) {
                FriendshipDoorBlockEntity door = getDoorData(level, state, pos);
                Component npcName = door == null ? Component.translatable("block.stardewcraft.friendship_door.unbound") : door.getNpcDisplayName();
                ObjectDialogueService.show((ServerPlayer) player, "message.stardewcraft.friendship_door.locked", npcName);
            }
            return InteractionResult.SUCCESS;
        }
        // PORT(1.20.1): 1.20.1 DoorBlock#use is the 1.21 DoorBlock#useWithoutItem (hand-independent).
        return super.use(state, level, pos, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (context instanceof EntityCollisionContext entityContext) {
            Entity entity = entityContext.getEntity();
            if (entity instanceof StardewNpcEntity) {
                return Shapes.empty();
            }
            if (entity instanceof ServerPlayer player && !canPlayerUseDoor(level, state, pos, player)) {
                return super.getCollisionShape(state.setValue(OPEN, false), level, pos, context);
            }
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return type == PathComputationType.LAND || super.isPathfindable(state, level, pos, type);
    }

    private boolean canPlayerUseDoor(BlockGetter level, BlockState state, BlockPos pos, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return true;
        }

        FriendshipDoorBlockEntity door = getDoorData(level, state, pos);
        if (door == null || !door.isBound()) {
            return true;
        }

        NpcFriendshipDataManager friendship = NpcFriendshipDataManager.get(serverPlayer.serverLevel());
        var playerData = com.stardew.craft.player.PlayerDataManager.getPlayerData(serverPlayer);
        for (String npcId : door.getNpcIds()) {
            // GameLocation.performAction "Door": once opened, mail doorUnlock<NPC> keeps it open for good.
            String mailKey = "doorUnlock" + (npcId.isEmpty() ? npcId : Character.toUpperCase(npcId.charAt(0)) + npcId.substring(1));
            int points = friendship.getPointsForNpc(serverPlayer.getUUID(), npcId);
            if (points >= door.getRequiredPoints()) {
                playerData.addMailFlag(mailKey);
                return true;
            }
            if (playerData.hasMailFlag(mailKey)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable FriendshipDoorBlockEntity getDoorData(BlockGetter level, BlockState state, BlockPos pos) {
        BlockPos mainPos = mainPos(state, pos);
        if (level.getBlockEntity(mainPos) instanceof FriendshipDoorBlockEntity door) {
            return door;
        }
        if (!mainPos.equals(pos) && level.getBlockEntity(pos) instanceof FriendshipDoorBlockEntity door) {
            return door;
        }
        return null;
    }

    private static BlockPos mainPos(BlockState state, BlockPos pos) {
        if (state.hasProperty(HALF) && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            return pos.below();
        }
        return pos;
    }
}
