package com.stardew.craft.item;

import com.stardew.craft.book.BookDefinition;
import com.stardew.craft.book.BookService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

public class StardewBookItem extends Item implements IStardewItem {
    private static final int USE_DURATION_TICKS = 21;

    private final BookDefinition definition;

    public StardewBookItem(BookDefinition definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    public BookDefinition getDefinition() {
        return definition;
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.book";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return definition.price();
    }

    @Override
    public Component getName(@Nonnull ItemStack stack) {
        return Component.translatable(getDescriptionId(stack))
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x5B3417)).withBold(true));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            if (player instanceof ServerPlayer serverPlayer) {
                int token = BookService.startReadingVisual(serverPlayer, USE_DURATION_TICKS);
                BookService.scheduleBookRead(serverPlayer, definition, hand, token, USE_DURATION_TICKS);
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(@Nonnull ItemStack stack) {
        return USE_DURATION_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(@Nonnull ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public @Nonnull ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull LivingEntity livingEntity) {
        if (!level.isClientSide && livingEntity instanceof ServerPlayer player) {
            BookService.readBook(player, definition, stack);
        }
        return stack;
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack,
                                @javax.annotation.Nullable Level level,
                                @Nonnull List<Component> tooltipComponents,
                                @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable(getDescriptionId(stack) + ".desc").withStyle(ChatFormatting.GRAY));
        if (definition.wellReadPower()) {
            tooltipComponents.add(Component.translatable("stardewcraft.book.tooltip.power").withStyle(ChatFormatting.GOLD));
        }
    }
}