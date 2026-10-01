package com.stardew.craft.inventory;

import com.stardew.craft.blockentity.StorageChestBlockEntity;
import com.stardew.craft.network.GlobalHudMessagePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import com.stardew.craft.port.net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.level.BlockEvent;
import java.util.ArrayList;

@EventBusSubscriber(modid = "stardewcraft")
public final class ChestInteractions {
    public static final TagKey<Item> SWAPPABLE = TagKey.create(Registries.ITEM,
            new ResourceLocation("stardewcraft", "swappable_chests"));
    private ChestInteractions() {}
    public static void message(Player player, String key) {
        if (player instanceof ServerPlayer server) GlobalHudMessagePayload.sendTo(server, Component.translatable(key));
    }
    public static boolean allowed(Player player, BlockPos pos) {
        return !(player instanceof ServerPlayer server)
                || server.level().dimension() != com.stardew.craft.core.ModDimensions.STARDEW_VALLEY
                || server.isCreative() || com.stardew.craft.event.FarmAreaProtectionEvents.canModifyAt(server, pos);
    }
    public static boolean canOpen(Player player, ChestStorage chest) {
        if (chest instanceof StorageChestBlockEntity shared && !shared.mayAccess(player)) {
            message(player, "stardewcraft.farm.build_farm_only"); return false;
        }
        if (chest.isInUse()) { message(player, "stardewcraft.chest.in_use"); return false; }
        return true;
    }
    public static ItemInteractionResult swap(ItemStack stack, Level level, BlockPos pos, Player player) {
        if (!(stack.getItem() instanceof BlockItem item) || !stack.is(SWAPPABLE)
                || !(level.getBlockEntity(pos) instanceof ChestStorage source)
                || source.isSharedStorage()
                || !new ItemStack(level.getBlockState(pos).getBlock()).is(SWAPPABLE)
                || item.getBlock() == level.getBlockState(pos).getBlock()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!allowed(player, pos) || source.isInUse()) {
            message(player, source.isInUse() ? "stardewcraft.chest.in_use" : "stardewcraft.farm.build_farm_only");
            return ItemInteractionResult.CONSUME;
        }
        var oldState = level.getBlockState(pos);
        var newState = item.getBlock().defaultBlockState();
        if (newState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            newState = newState.setValue(BlockStateProperties.HORIZONTAL_FACING, oldState.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }
        if (!(item.getBlock() instanceof net.minecraft.world.level.block.EntityBlock factory)) return ItemInteractionResult.CONSUME;
        BlockEntity preview = factory.newBlockEntity(pos, newState);
        if (!(preview instanceof ChestStorage target) || target.isSharedStorage()) return ItemInteractionResult.CONSUME;
        var content = new ArrayList<ItemStack>();
        for (int i = 0; i < source.getContainerSize(); i++) if (!source.getItem(i).isEmpty()) content.add(source.getItem(i).copy());
        if (content.size() > target.getContainerSize()) { message(player, "stardewcraft.chest.swap_full"); return ItemInteractionResult.CONSUME; }
        var oldEntity = (BlockEntity) source;
        var saved = oldEntity.saveWithoutMetadata(level.registryAccess());
        int color = source.getColorSelection();
        // isMoving suppresses onRemove inventory drops; rollback restores the complete old entity data.
        if (!level.setBlock(pos, newState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON)) return ItemInteractionResult.CONSUME;
        if (!(level.getBlockEntity(pos) instanceof ChestStorage placed)) {
            level.setBlock(pos, oldState, Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
            var restored = level.getBlockEntity(pos);
            if (restored != null) restored.loadWithComponents(saved, level.registryAccess());
            return ItemInteractionResult.CONSUME;
        }
        // Preserve addon block-entity data, then repack to the new capacity without discarding sparse slots.
        saved.remove("items");
        ((BlockEntity) placed).loadWithComponents(saved, level.registryAccess());
        for (int i = 0; i < content.size(); i++) placed.setItem(i, content.get(i));
        placed.setColorSelection(color);
        if (!player.isCreative()) stack.shrink(1);
        Block.popResource(level, pos, new ItemStack(oldState.getBlock()));
        level.playSound(null, pos, com.stardew.craft.sound.ModSounds.AXCHOP.get(), net.minecraft.sounds.SoundSource.BLOCKS, .7F, 1F);
        return ItemInteractionResult.CONSUME;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void protectContentsAndMove(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(level.getBlockEntity(event.getPos()) instanceof ChestStorage chest)) return;
        if (chest.isInUse()) { event.setCanceled(true); message(event.getPlayer(), "stardewcraft.chest.in_use"); return; }
        if (chest.isEmpty()) return;
        event.setCanceled(true);
        var player = event.getPlayer();
        if (!allowed(player, event.getPos())) return;
        var tool = player.getMainHandItem();
        if (!tool.is(net.minecraft.tags.ItemTags.AXES) && !tool.is(com.stardew.craft.core.ModTags.Items.PICKAXES)) return;
        Direction preferred = player.getDirection();
        for (Direction direction : new Direction[]{preferred, preferred.getClockWise(), preferred.getCounterClockWise(), preferred.getOpposite()}) {
            BlockPos destination = event.getPos().relative(direction);
            if (!level.hasChunkAt(destination) || !allowed(player, destination) || !level.isEmptyBlock(destination)
                    || !level.getBlockState(destination.below()).isFaceSturdy(level, destination.below(), Direction.UP)) continue;
            var state = level.getBlockState(event.getPos());
            if (!level.noCollision(null, state.getCollisionShape(level, destination).bounds().move(destination))) continue;
            var data = ((BlockEntity) chest).saveWithoutMetadata(level.registryAccess());
            if (!level.setBlock(destination, state, Block.UPDATE_ALL)) continue;
            var replacement = level.getBlockEntity(destination);
            if (!(replacement instanceof ChestStorage)) { level.removeBlock(destination, false); continue; }
            replacement.loadWithComponents(data, level.registryAccess());
            replacement.setChanged();
            level.setBlock(event.getPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_MOVE_BY_PISTON);
            level.sendBlockUpdated(destination, state, state, Block.UPDATE_ALL);
            return;
        }
        message(player, "stardewcraft.chest.move_blocked");
    }
}
