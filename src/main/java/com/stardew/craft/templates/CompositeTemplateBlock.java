package com.stardew.craft.templates;

import java.util.List;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import com.stardew.craft.port.net.neoforged.neoforge.common.util.TriState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Shared two-material interaction and persistence contract for building templates. */
public abstract class CompositeTemplateBlock extends MaterialTemplateBlock {
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    protected CompositeTemplateBlock(TemplateShape shape, Properties properties) {
        super(shape, properties);
        registerDefaultState(defaultBlockState().setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FILLED);
    }

    public abstract boolean targetsFill(BlockState state, BlockHitResult hit);

    @javax.annotation.Nullable
    public BlockState defaultFillMaterial() { return null; }

    void interact(PlayerInteractEvent.RightClickBlock event) {
        // All composite interactions pass through this hook, including shift-clicks.
        event.setUseBlock(TriState.FALSE);
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !(event.getLevel().getBlockEntity(event.getPos()) instanceof TemplateBlockEntity template)) return;
        ItemStack held = event.getItemStack();
        var player = event.getEntity();
        BlockState material = held.getItem() instanceof BlockItem item ? item.getBlock().defaultBlockState() : null;
        boolean shift = player.isShiftKeyDown();
        // The default-looking primary is still an empty slot. Shift + a material
        // can fill the backing alone; otherwise shift removes both explicit slots.
        boolean fillOnly = shift && template.material() == null && TemplateMaterials.isValidFill(material);
        boolean removing = shift && !fillOnly && (template.material() != null || template.fillMaterial() != null);
        boolean fill = fillOnly || template.material() != null;
        BlockState previous = fill ? template.fillMaterial() : template.material();
        boolean applying = !removing && (fillOnly || (!shift && previous == null))
                && (fill ? TemplateMaterials.isValidFill(material) : TemplateMaterials.isValid(material));
        if (!applying && !removing) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        var level = event.getLevel();
        if (level.isClientSide() || (applying && material.equals(previous))) return;
        BlockState primary = template.material(), secondary = template.fillMaterial();
        if (removing) {
            template.setMaterial(null);
            template.setFillMaterial(null);
        } else if (fill) template.setFillMaterial(material);
        else template.setMaterial(material);
        if (!player.getAbilities().instabuild) {
            if (applying) held.shrink(1);
            if (removing) {
                if (primary != null) player.getInventory().placeItemBackInInventory(new ItemStack(primary.getBlock()));
                if (secondary != null) player.getInventory().placeItemBackInInventory(new ItemStack(secondary.getBlock()));
            } else if (previous != null) player.getInventory().placeItemBackInInventory(new ItemStack(previous.getBlock()));
        }
        level.playSound(null, event.getPos(), applying
                ? material.getSoundType(level, event.getPos(), player).getPlaceSound()
                : SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8F, 1F);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TemplateBlockEntity template) {
            for (ItemStack stack : drops) {
                if (stack.is(asItem())) template.saveToItem(stack, params.getLevel().registryAccess());
            }
        }
        return drops;
    }
}
