package com.stardew.craft.block.tv;

import com.stardew.craft.block.decor.MapDecorStaticBlock;
import com.stardew.craft.network.payload.OpenTVScreenPayload;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.time.StardewTimeManager;
import com.stardew.craft.weather.WeatherManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nonnull;

/**
 * TV block — extends MapDecorStaticBlock for standard multi-cell decor placement,
 * but adds right-click interaction to open the Stardew Valley TV interface.
 */
public class TVBlock extends MapDecorStaticBlock {

    public TVBlock(Properties properties, String modelId) {
        super(properties, modelId);
    }

    @SuppressWarnings("null")
    @Override
    @Nonnull
    public InteractionResult useWithoutItem(@Nonnull BlockState state, @Nonnull Level level,
                                                @Nonnull BlockPos pos, @Nonnull Player player,
                                                @Nonnull BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        StardewTimeManager timeManager = StardewTimeManager.get();
        int currentDay = timeManager.getCurrentDay();
        int currentSeason = timeManager.getCurrentSeason();
        int currentYear = timeManager.getCurrentYear();
        int daysPlayed = (currentYear - 1) * 112 + currentSeason * 28 + currentDay;
        int dayOfWeek = (currentDay - 1) % 7; // 0=Mon ... 6=Sun

        String tomorrowWeather = WeatherManager.getTomorrowWeather(serverLevel);
        // Game1.getWeatherModificationsForDate: an active festival tomorrow overrides the forecast.
        if (currentDay < 28) {
            for (var festival : com.stardew.craft.festival.FestivalRegistry.activeFestivals()) {
                if (festival.isDate(currentSeason, currentDay + 1)) {
                    tomorrowWeather = "Festival:" + festival.id() + ":" + festival.locationKey()
                            + ":" + festival.startTime() + ":" + festival.endTime();
                    break;
                }
            }
        }

        double dailyLuck = PlayerStardewDataAPI.getDailyLuck(serverPlayer);

        // Resolve main block position (player may have clicked an extension part)
        BlockPos mainPos = findMainPos(level, pos, state);
        if (mainPos == null) mainPos = pos;

        // Build the payload with all channel data computed server-side
        OpenTVScreenPayload payload = TVChannelData.buildPayload(
                serverPlayer, daysPlayed, dayOfWeek, currentDay, currentSeason,
                tomorrowWeather, dailyLuck,
                mainPos.getX(), mainPos.getY(), mainPos.getZ());

        PacketDistributor.sendToPlayer(serverPlayer, payload);
        return InteractionResult.SUCCESS;
    }
}
