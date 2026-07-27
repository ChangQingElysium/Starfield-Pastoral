package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.service.AnimalBirthService;
import com.stardew.craft.manager.AnimalGrowthManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@SuppressWarnings("null")
public record AnimalBirthNamingSubmitPayload(
        long eventId,
        String name
) implements CustomPacketPayload {
    public static final Type<AnimalBirthNamingSubmitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "animal_birth_naming_submit"
            ));
    public static final StreamCodec<RegistryFriendlyByteBuf, AnimalBirthNamingSubmitPayload>
            STREAM_CODEC = StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    AnimalBirthNamingSubmitPayload::eventId,
                    ByteBufCodecs.STRING_UTF8,
                    AnimalBirthNamingSubmitPayload::name,
                    AnimalBirthNamingSubmitPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            AnimalBirthNamingSubmitPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !(player.level() instanceof ServerLevel level)) {
                return;
            }
            AnimalBirthService.ClaimResult result = AnimalBirthService.claim(
                    level,
                    player,
                    payload.eventId(),
                    payload.name()
            );
            if (result == AnimalBirthService.ClaimResult.CREATED) {
                AnimalGrowthManager.get(level).allowBirthPromptRetry(
                        payload.eventId());
                player.sendSystemMessage(Component.translatable(
                        "stardewcraft.animal.pregnancy.named",
                        payload.name().trim()
                ));
                return;
            }
            AnimalGrowthManager.get(level).allowBirthPromptRetry(payload.eventId());
            player.sendSystemMessage(Component.translatable(
                    "stardewcraft.animal.pregnancy.naming_failed."
                            + result.name().toLowerCase()
            ));
        });
    }
}
