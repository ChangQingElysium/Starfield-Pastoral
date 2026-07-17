package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record OpenAnimalPurchaseScreenPayload(
    int playerMoney,
    List<AnimalOption> animalOptions,
    List<BuildingOption> buildingOptions,
    boolean incubatorMode,
    long contextBlockPos
) implements CustomPacketPayload {

    public record AnimalOption(
        String animalTypeId,
        String displayName,
        String family,
        int requiredTier,
        int price,
        boolean unlocked,
        String descriptionKey,
        String lockReasonKey
    ) {
        @SuppressWarnings("null")
        public static final StreamCodec<RegistryFriendlyByteBuf, AnimalOption> STREAM_CODEC = StreamCodec.of(
            (buf, option) -> {
                buf.writeUtf(option.animalTypeId());
                buf.writeUtf(option.displayName());
                buf.writeUtf(option.family());
                buf.writeInt(option.requiredTier());
                buf.writeInt(option.price());
                buf.writeBoolean(option.unlocked());
                buf.writeUtf(option.descriptionKey());
                buf.writeUtf(option.lockReasonKey());
            },
            buf -> new AnimalOption(
                buf.readUtf(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readInt(),
                buf.readInt(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf()
            )
        );
    }

    public record BuildingOption(String buildingId, String displayName, String family, int tier, int animalCount, int capacity) {
        @SuppressWarnings("null")
        public static final StreamCodec<RegistryFriendlyByteBuf, BuildingOption> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            BuildingOption::buildingId,
            ByteBufCodecs.STRING_UTF8,
            BuildingOption::displayName,
            ByteBufCodecs.STRING_UTF8,
            BuildingOption::family,
            ByteBufCodecs.INT,
            BuildingOption::tier,
            ByteBufCodecs.INT,
            BuildingOption::animalCount,
            ByteBufCodecs.INT,
            BuildingOption::capacity,
            BuildingOption::new
        );
    }

    @SuppressWarnings("null")
    public static final Type<OpenAnimalPurchaseScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_animal_purchase_screen"));

    @SuppressWarnings("null")
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenAnimalPurchaseScreenPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.INT,
        OpenAnimalPurchaseScreenPayload::playerMoney,
        AnimalOption.STREAM_CODEC.apply(ByteBufCodecs.list()),
        OpenAnimalPurchaseScreenPayload::animalOptions,
        BuildingOption.STREAM_CODEC.apply(ByteBufCodecs.list()),
        OpenAnimalPurchaseScreenPayload::buildingOptions,
        ByteBufCodecs.BOOL,
        OpenAnimalPurchaseScreenPayload::incubatorMode,
        ByteBufCodecs.VAR_LONG,
        OpenAnimalPurchaseScreenPayload::contextBlockPos,
        OpenAnimalPurchaseScreenPayload::new
    );

    public static OpenAnimalPurchaseScreenPayload normal(
        int playerMoney,
        List<AnimalOption> animalOptions,
        List<BuildingOption> buildingOptions
    ) {
        return new OpenAnimalPurchaseScreenPayload(playerMoney, animalOptions, buildingOptions, false, 0L);
    }

    public static OpenAnimalPurchaseScreenPayload incubator(
        List<AnimalOption> animalOptions,
        List<BuildingOption> buildingOptions,
        BlockPos incubatorPos
    ) {
        return new OpenAnimalPurchaseScreenPayload(0, animalOptions, buildingOptions, true, incubatorPos.asLong());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenAnimalPurchaseScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenAnimalPurchaseScreenPayload payload) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (payload.incubatorMode() && !payload.animalOptions().isEmpty()) {
            minecraft.setScreen(new com.stardew.craft.client.gui.AnimalPurchaseBuildingScreen(
                payload, payload.animalOptions().getFirst()));
        } else {
            minecraft.setScreen(new com.stardew.craft.client.gui.AnimalPurchaseScreen(payload));
        }
    }
}
