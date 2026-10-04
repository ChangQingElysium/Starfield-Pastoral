package com.stardew.craft.gingerisland;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

/** Old IDs retain their authored model, pivot and BlockItem; retrieval uses the reviewed owner. */
public final class GingerIslandLegacyAliasBlock extends MapDecorStaticBlock {
    private final GingerIslandAssets.BlockAsset asset;

    public GingerIslandLegacyAliasBlock(Properties properties, GingerIslandAssets.BlockAsset asset) {
        super(properties, asset.model());
        this.asset = asset;
    }

    public String catalogOwner() { return asset.catalog_owner(); }

    public ItemStack canonicalStack() {
        var owner = (GingerIslandStateDecorBlock) GingerIslandBlocks.get(catalogOwner());
        // Repair/donation phases remain world progress, never pre-completed inventory items.
        if (!owner.hasBuildingVariants()) return new ItemStack(owner);
        return GingerIslandVariantStacks.nameStack(owner.stackForVisualState(
                asset.canonical_state().get(owner.visualStateProperty())));
    }

    @Override public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return canonicalStack();
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return state.getValue(PART) == Part.MAIN ? List.of(canonicalStack()) : List.of();
    }

    @Override protected ItemStack extensionRemovalDrop(Level level, BlockPos mainPos) {
        return canonicalStack();
    }
}
