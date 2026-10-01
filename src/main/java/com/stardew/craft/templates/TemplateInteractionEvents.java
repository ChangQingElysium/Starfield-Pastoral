package com.stardew.craft.templates;

import com.stardew.craft.StardewCraft;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;

/** Central material interaction hook shared by all vanilla-derived templates. */
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.FORGE)
public final class TemplateInteractionEvents {
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof TemplateBlock)) {
            return;
        }

        if (event.getLevel().getBlockState(event.getPos()).getBlock() instanceof CompositeTemplateBlock roof) {
            roof.interact(event);
            return;
        }
        ItemStack held = event.getItemStack();
        if (!event.getEntity().isShiftKeyDown() && held.getItem() instanceof BlockItem
                && event.getLevel().getBlockEntity(event.getPos()) instanceof TemplateBlockEntity template
                && template.material() != null) {
            // Keep normal item placement in either hand, bypassing even doors/buttons.
            event.setUseBlock(TriState.FALSE);
            return;
        }
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockState material = held.getItem() instanceof BlockItem blockItem
                ? blockItem.getBlock().defaultBlockState() : null;
        boolean applying = !event.getEntity().isShiftKeyDown() && TemplateMaterials.isValid(material);
        boolean removing = event.getEntity().isShiftKeyDown()
                && event.getLevel().getBlockEntity(event.getPos()) instanceof TemplateBlockEntity template
                && template.material() != null;
        if (!applying && !removing) {
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }

        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof TemplateBlockEntity template)) {
            return;
        }
        BlockState previous = template.material();
        if (applying && material.equals(previous)) {
            return;
        }

        template.setMaterial(applying ? material : null);
        mirrorDoorMaterial(level, pos, applying ? material : null);
        if (!event.getEntity().getAbilities().instabuild) {
            if (applying) held.shrink(1);
            returnMaterial(event, previous);
        }
        if (applying) {
            level.playSound(null, pos, material.getSoundType(level, pos, event.getEntity()).getPlaceSound(),
                    SoundSource.BLOCKS, 1F, 1F);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof CompositeTemplateBlock
                || event.getPlayer().getAbilities().instabuild
                || !(event.getState().getBlock() instanceof TemplateBlock)
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof TemplateBlockEntity template)) {
            return;
        }
        BlockState material = template.material();
        if (material != null && !material.getBlock().asItem().getDefaultInstance().isEmpty()) {
            event.getPlayer().drop(new ItemStack(material.getBlock().asItem()), false);
        }
    }

    private static void mirrorDoorMaterial(Level level, BlockPos pos, BlockState material) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) return;
        BlockPos otherPos = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                ? pos.above() : pos.below();
        if (level.getBlockState(otherPos).getBlock() == state.getBlock()
                && level.getBlockEntity(otherPos) instanceof TemplateBlockEntity other) {
            other.setMaterial(material);
        }
    }

    private static void returnMaterial(PlayerInteractEvent.RightClickBlock event, BlockState previous) {
        if (previous == null || previous.getBlock().asItem().getDefaultInstance().isEmpty()) return;
        ItemStack returned = new ItemStack(previous.getBlock().asItem());
        if (!event.getEntity().addItem(returned)) {
            event.getEntity().drop(returned, false);
        }
    }

    private TemplateInteractionEvents() {
    }
}
