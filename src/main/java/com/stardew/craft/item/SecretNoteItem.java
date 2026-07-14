package com.stardew.craft.item;

import com.stardew.craft.secretnote.SecretNoteService;
import com.stardew.craft.secretnote.SecretNoteRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

/** Generic (O)79 secret note; its concrete number is resolved only when used. */
public final class SecretNoteItem extends Item implements IStardewItem {
    private static final String TAG_DISPLAY_NUMBER = "SecretNoteDisplayNumber";
    private static final int CREATIVE_VARIANT_MODEL_BASE = 31_000;
    public static final int FIRST_DISPLAY_NOTE = 1;
    public static final int LAST_DISPLAY_NOTE = 26;

    public SecretNoteItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.special";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1;
    }

    public static ItemStack createCreativeVariant(int displayNumber) {
        ItemStack stack = new ItemStack(ModItems.SECRET_NOTE.get());
        bindDisplayNumber(stack, displayNumber);
        return stack;
    }

    static void bindDisplayNumber(ItemStack stack, int displayNumber) {
        if (stack == null || stack.isEmpty()
                || displayNumber < FIRST_DISPLAY_NOTE || displayNumber > LAST_DISPLAY_NOTE) {
            return;
        }
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(TAG_DISPLAY_NUMBER, displayNumber);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        // CreativeModeTab uses component identity when deduplicating entries. Keep a
        // dedicated marker too, matching SpecificBaitItem's established variant path.
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(CREATIVE_VARIANT_MODEL_BASE + displayNumber));
    }

    public static int getBoundDisplayNumber(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int displayNumber = tag.contains(TAG_DISPLAY_NUMBER) ? tag.getInt(TAG_DISPLAY_NUMBER) : -1;
        if (displayNumber >= FIRST_DISPLAY_NOTE && displayNumber <= LAST_DISPLAY_NOTE) {
            return displayNumber;
        }
        CustomModelData marker = stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT);
        for (int candidate = FIRST_DISPLAY_NOTE; candidate <= LAST_DISPLAY_NOTE; candidate++) {
            if (marker.equals(new CustomModelData(CREATIVE_VARIANT_MODEL_BASE + candidate))) {
                return candidate;
            }
        }
        return -1;
    }

    @Override
    public Component getName(ItemStack stack) {
        int displayNumber = getBoundDisplayNumber(stack);
        return displayNumber > 0
                ? Component.translatable("stardewcraft.secret_note.title", displayNumber)
                : super.getName(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player,
                                                  @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResultHolder.pass(stack);
        int displayNumber = getBoundDisplayNumber(stack);
        boolean read = displayNumber > 0
                ? SecretNoteService.readSpecific(serverPlayer, stack, SecretNoteRegistry.byDisplayNumber(displayNumber))
                : SecretNoteService.readOne(serverPlayer, stack);
        return read
                ? InteractionResultHolder.consume(stack)
                : InteractionResultHolder.fail(stack);
    }
}
