package com.stardew.craft.item.totem;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;

/**
 * 求雨图腾 — 严格复刻 SDV Object.rainTotem()
 * 使用后将明天的天气设为 Rain，播放 thunder + rainsound 音效。
 */
public class RainTotemItem extends Item implements IStardewItem {

    public RainTotemItem(Properties properties) {
        super(properties.stacksTo(999));
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.magic";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return 20;
    }

    @SuppressWarnings("null")
    @Override
    @Nonnull
    public InteractionResultHolder<ItemStack> use(@Nonnull Level level, @Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        if (!player.isAlive()) {
            return InteractionResultHolder.pass(stack);
        }

        // Object.rainTotem: every Stardew location context (Default/Island/Desert, including the mines)
        // allows the totem and ultimately drives the shared valley weather; other dimensions refuse it.
        if (!player.level().dimension().location().getNamespace().equals(com.stardew.craft.StardewCraft.MODID)) {
            player.displayClientMessage(
                    Component.translatable("message.stardewcraft.rain_totem_denied"), true);
            return InteractionResultHolder.fail(stack);
        }

        ServerLevel stardewLevel = ((ServerPlayer) player).server.getLevel(ModDimensions.STARDEW_VALLEY);
        if (stardewLevel == null) {
            return InteractionResultHolder.fail(stack);
        }

        // SDV: 设置明天天气为 Rain（明天是节日则不生效，也没有“暴风雨正在酝酿”提示）
        var clock = com.stardew.craft.time.StardewTimeManager.get();
        boolean festivalTomorrow = clock.getCurrentDay() < 28
                && com.stardew.craft.festival.FestivalRegistry.activeFestivals().stream()
                        .anyMatch(festival -> festival.isDate(clock.getCurrentSeason(), clock.getCurrentDay() + 1));
        if (!festivalTomorrow) {
            WeatherManager.setTomorrowWeather(stardewLevel, "Rain");
        }

        // SDV: 播放 thunder 音效
        level.playSound(null, player.blockPosition(), ModSounds.THUNDER.get(), SoundSource.PLAYERS, 1.0f, 1.0f);

        // SDV: 生成云朵粒子（6组，不同运动方向/延迟）
        if (level instanceof ServerLevel sl) {
            double px = player.getX();
            double py = player.getY();
            double pz = player.getZ();

            for (int i = 0; i < 6; i++) {
                // 云朵粒子向上飘散
                sl.sendParticles(ParticleTypes.CLOUD,
                        px + (sl.random.nextDouble() - 0.5) * 2.0,
                        py + 2.0 + sl.random.nextDouble(),
                        pz + (sl.random.nextDouble() - 0.5) * 2.0,
                        3, 0.3, 0.2, 0.3, 0.02);
            }

            // SDV: 飘浮图腾图标粒子（向上飘 + 摇晃）
            for (int i = 0; i < 8; i++) {
                sl.sendParticles(ParticleTypes.END_ROD,
                        px, py + 1.5, pz,
                        1, 0.1, 0.5, 0.1, 0.03);
            }
        }

        // SDV: 延迟 2000ms 后播放 rainsound
        // 在服务端使用 scheduledTick 模拟延迟
        // 简化：直接播放（MC 中无法精确延迟音效，但效果近似）
        level.playSound(null, player.blockPosition(), ModSounds.RAIN_SOUND.get(), SoundSource.WEATHER, 0.8f, 1.0f);

        // 消耗物品
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        // SDV: 延迟 2000ms 后显示消息 "暴风雨正在酝酿中..."
        if (!festivalTomorrow) {
            player.displayClientMessage(
                    Component.translatable("message.stardewcraft.rain_totem_used"), true);
        }

        return InteractionResultHolder.consume(stack);
    }
}
