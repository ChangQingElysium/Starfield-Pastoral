package com.stardew.craft.item;

import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import com.stardew.craft.port.net.minecraft.world.item.component.Fireworks;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ICapabilityProvider;

/** Casino prize firework with the original Stardew Valley item description. */
public final class CasinoFireworkItem extends FireworkRocketItem implements IStardewItem {
    static final int ORIGINAL_SELL_PRICE = 50;
    static final String CATEGORY_KEY = "stardewcraft.type.misc";

    private final Fireworks defaultFireworks;

    /**
     * PORT(1.20.1): 1.21 attached the firework payload as a default item component. 1.20.1 items have no
     * default components, so the payload is written into every new stack's {@code Fireworks} tag when the
     * stack is constructed (Forge calls {@link #initCapabilities} from every ItemStack constructor).
     */
    public CasinoFireworkItem(Properties properties, Fireworks defaultFireworks) {
        super(properties);
        this.defaultFireworks = defaultFireworks;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        if (!PortItemData.has(stack, DataComponents.FIREWORKS)) {
            PortItemData.set(stack, DataComponents.FIREWORKS, defaultFireworks);
        }
        return super.initCapabilities(stack, nbt);
    }

    @Override
    public String getItemTypeKey() {
        return CATEGORY_KEY;
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return ORIGINAL_SELL_PRICE;
    }
}
