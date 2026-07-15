package com.stardew.craft.item.misc;

import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.secretnote.SecretNoteStoryFlags;
import java.util.List;
import javax.annotation.Nonnull;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

public class IridiumMilkItem extends Item implements IStardewItem {
    public static final int MAX_HEALTH_GAIN = 25;

    private static final int NAME_BASE_RGB = 0x79D8FF;
    private static final int NAME_HIGHLIGHT_RGB = 0xF4FBFF;

    public IridiumMilkItem(Properties properties) {
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

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    @Override
    public boolean canBeHurtBy(@Nonnull ItemStack stack, @Nonnull net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    @Override
    public Component getName(@Nonnull ItemStack stack) {
        return sweepHighlight(Component.translatable(getDescriptionId(stack)).getString(), NAME_BASE_RGB, NAME_HIGHLIGHT_RGB);
    }

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
            @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("stardewcraft.item.iridium_milk.tooltip.flavor")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x9ADFFF))));
        tooltipComponents.add(Component.translatable("stardewcraft.item.iridium_milk.tooltip.effect", MAX_HEALTH_GAIN)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xD5F4FF)).withBold(true)));
        tooltipComponents.add(Component.translatable("stardewcraft.item.iridium_milk.tooltip.special")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public int getUseDuration(@Nonnull ItemStack stack, @Nonnull LivingEntity entity) {
        return 40;
    }

    @Override
    public UseAnim getUseAnimation(@Nonnull ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer
                && !canApplyPermanentReward(PlayerStardewDataAPI.getData(serverPlayer))) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public @Nonnull ItemStack finishUsingItem(@Nonnull ItemStack stack, @Nonnull Level level,
            @Nonnull LivingEntity livingEntity) {
        if (!level.isClientSide && livingEntity instanceof ServerPlayer serverPlayer) {
            PlayerStardewData data = PlayerStardewDataAPI.getData(serverPlayer);
            if (!canApplyPermanentReward(data)) {
                return stack;
            }
            data.addMailFlag(SecretNoteStoryFlags.QI_CAVE);
            data.removeMailFlag(SecretNoteStoryFlags.QI_CAVE_SCENE_PENDING);
            data.removeMailFlag(SecretNoteStoryFlags.QI_CAVE_TEMP_REWARD_GRANTED);
            data.setMaxHealth(data.getMaxHealth() + MAX_HEALTH_GAIN);
            PlayerDataEventHandler.syncPlayerData(serverPlayer, data);
            serverPlayer.awardStat(Stats.ITEM_USED.get(this));
            playIridiumMilkFeedback(serverPlayer);
            if (!serverPlayer.getAbilities().instabuild) {
                stack.shrink(1);
            }
        }
        return stack;
    }

    static boolean canApplyPermanentReward(PlayerStardewData data) {
        return data != null
                && !data.hasMailFlag(SecretNoteStoryFlags.QI_CAVE)
                && !data.hasMailFlag(SecretNoteStoryFlags.IRIDIUM_MILK_CONSUMED);
    }

    private static void playIridiumMilkFeedback(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        double x = player.getX();
        double y = player.getY() + 1.0D;
        double z = player.getZ();
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 0.85F);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.15D, z, 36, 0.7D, 1.0D, 0.7D, 0.025D);
        player.sendSystemMessage(Component.translatable("stardewcraft.item.iridium_milk.consumed", MAX_HEALTH_GAIN)
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xB9ECFF)).withBold(true)));
    }

    private static MutableComponent sweepHighlight(String raw, int baseRgb, int highlightRgb) {
        long ms = System.currentTimeMillis();
        float pos = (((ms % 60_000L) / 1000.0F * 0.42F) % 1.56F) - 0.28F;
        int length = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < length; i++) {
            float u = length > 1 ? (float) i / (length - 1) : 0.5F;
            float k = Math.max(0.0F, 1.0F - Math.abs(u - pos) / 0.28F);
            k = k * k * (3.0F - 2.0F * k);
            out.append(Component.literal(String.valueOf(raw.charAt(i)))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(lerpRgb(baseRgb, highlightRgb, k))).withBold(true)));
        }
        return out;
    }

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0F, 1.0F);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
