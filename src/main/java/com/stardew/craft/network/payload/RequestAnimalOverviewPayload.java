package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.farm.FarmInstanceRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Client request for the authoritative animal list shown in the V menu. */
public record RequestAnimalOverviewPayload() implements CustomPacketPayload {
    public static final Type<RequestAnimalOverviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "request_animal_overview"));
    public static final StreamCodec<ByteBuf, RequestAnimalOverviewPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestAnimalOverviewPayload());

    private static final java.util.Set<String> BUILTIN_ANIMAL_TYPES = java.util.Set.of(
            "white_chicken", "brown_chicken", "blue_chicken", "void_chicken",
            "golden_chicken", "duck", "rabbit", "dinosaur", "cow", "brown_cow",
            "goat", "sheep", "pig", "ostrich");

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestAnimalOverviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendOverviewTo(player);
            }
        });
    }

    public static void sendOverviewTo(ServerPlayer player) {
        AnimalWorldData data = AnimalWorldData.get(player.serverLevel());
        FarmInstanceRegistry farms = FarmInstanceRegistry.get();
        List<SyncAnimalOverviewPayload.Entry> rows = new ArrayList<>();
        for (FarmAnimalRecord animal : data.getAnimals()) {
            if (!farms.canOperateBuilding(player.getUUID(), animal.ownerPlayerUuid())) {
                continue;
            }
            FarmAnimalDefinition definition = FarmAnimalDefinitions.find(animal.animalTypeId());
            String sourceType = definition == null ? animal.animalTypeId() : definition.sourceKey();
            String baseType = sourceBaseType(sourceType);
            Visual visual = resolveVisual(animal, definition);
            int petStatus = animal.wasPetToday() ? 2 : animal.wasAutoPetToday() ? 1 : 0;
            rows.add(new SyncAnimalOverviewPayload.Entry(
                    animal.animalId(),
                    animal.animalTypeId(),
                    animal.customName() == null ? "" : animal.customName(),
                    FarmAnimalDefinitions.displayNameKeyFor(animal.animalTypeId()),
                    baseType,
                    sourceType,
                    animal.friendship(),
                    petStatus,
                    animal.hasEatenAnimalCracker(),
                    visual.textureId(),
                    visual.width(),
                    visual.height()
            ));
        }
        rows.sort(Comparator
                .comparing(SyncAnimalOverviewPayload.Entry::baseType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(SyncAnimalOverviewPayload.Entry::sourceType, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Comparator.comparingInt(SyncAnimalOverviewPayload.Entry::friendship).reversed())
                .thenComparingLong(SyncAnimalOverviewPayload.Entry::animalId));
        PacketDistributor.sendToPlayer(player, new SyncAnimalOverviewPayload(rows));
    }

    private static String sourceBaseType(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.trim();
        String[] parts = normalized.split("\\s+");
        return parts.length > 1 ? parts[1] : normalized;
    }

    private static Visual resolveVisual(FarmAnimalRecord animal, FarmAnimalDefinition definition) {
        if (definition == null) {
            return Visual.EMPTY;
        }
        if (StardewCraft.MODID.equals(definition.dataId().getNamespace())
                && BUILTIN_ANIMAL_TYPES.contains(animal.animalTypeId())) {
            boolean baby = animal.isBaby() && !"dinosaur".equals(animal.animalTypeId());
            String spriteName = animal.animalTypeId() + (baby ? "_baby" : "");
            ResourceLocation sprite = ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID,
                    "textures/gui/common/animal_page_sprite_" + spriteName + ".png");
            boolean compact = definition.sourceKey().contains("Chicken")
                    || "Duck".equals(definition.sourceKey())
                    || "Rabbit".equals(definition.sourceKey())
                    || "Dinosaur".equals(definition.sourceKey());
            return new Visual(sprite.toString(), compact ? 16 : 32, compact ? 16 : 28);
        }
        if (definition.shopTextureId() != null) {
            return new Visual(
                    definition.shopTextureId().toString(),
                    definition.shopTextureWidth(),
                    definition.shopTextureHeight());
        }
        return Visual.EMPTY;
    }

    private record Visual(String textureId, int width, int height) {
        private static final Visual EMPTY = new Visual("", 0, 0);
    }
}
