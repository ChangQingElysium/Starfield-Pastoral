package com.stardew.craft.manager;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.block.nature.SurfaceArtifactSpotBlock;
import com.stardew.craft.book.BookAcquisitionService;
import com.stardew.craft.core.FarmAreaResolver;
import com.stardew.craft.event.FarmAreaProtectionEvents;
import com.stardew.craft.item.tool.HoeItem;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.time.StardewTimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = StardewCraft.MODID)
public final class ArtifactSpotDigService {
    private ArtifactSpotDigService() {}
    public static boolean isSpot(BlockState state) {
        return state.getBlock() instanceof SurfaceArtifactSpotBlock;
    }
    public static BlockPos target(Level level, BlockPos hit) {
        if (isSpot(level.getBlockState(hit))) return hit;
        return level.getBlockState(hit.above()).getBlock() instanceof SurfaceArtifactSpotBlock ? hit.above() : hit;
    }
    public static boolean allowed(ServerPlayer player, BlockPos pos) {
        return player.isCreative() || !FarmAreaResolver.isInAnyFarm(player.level(), pos)
                || FarmAreaProtectionEvents.canModifyAt(player, pos);
    }
    public static boolean dig(ServerLevel level, BlockPos pos, ServerPlayer player, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (!isSpot(state) || !allowed(player, pos)) return false;
        BlockPos ground = pos.below();
        var random = ArtifactDropService.digRandom(level, ground, true);
        List<ItemStack> drops = new ArrayList<>();
        BookAcquisitionService.recordArtifactSpotDugAndMaybeAddDefenseBook(player, drops, random);
        if (state.getBlock() instanceof SurfaceArtifactSpotBlock marker && marker.isSeedSpot()) {
            var time = StardewTimeManager.get();
            drops.add(ArtifactDropService.rollSeedDrop(time.getCurrentSeason(), time.getCurrentDay(),
                    ArtifactDropService.averageDailyLuck(player), random));
        } else drops.addAll(ArtifactDropService.rollDrops(level, ground, player, tool));
        // Remove first: a second click in the same tick cannot receive a second reward.
        level.removeBlock(pos, false);
        for (ItemStack drop : drops) if (!drop.isEmpty()) Block.popResource(level, pos, drop);
        PlayerStardewDataAPI.addExperience(player, SkillType.FORAGING, 15);
        level.playSound(null, pos, com.stardew.craft.sound.ModSounds.HOE_HIT.get(), SoundSource.BLOCKS, 1, 1);
        level.levelEvent(2001, pos, Block.getId(state));
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHoeUse(PlayerInteractEvent.RightClickBlock event) {
        ItemStack tool = event.getItemStack();
        if (tool.getItem() instanceof HoeItem || !tool.canPerformAction(ToolActions.HOE_TILL)) return;
        BlockPos pos = target(event.getLevel(), event.getPos());
        if (!isSpot(event.getLevel().getBlockState(pos))) return;
        if (event.getEntity() instanceof ServerPlayer player && dig(player.serverLevel(), pos, player, tool))
            tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(event.getHand())); // PORT(1.20.1): 1.21 hurtAndBreak(amount, entity, slot) breaks via onEquippedItemBroken(slot)
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }
}
