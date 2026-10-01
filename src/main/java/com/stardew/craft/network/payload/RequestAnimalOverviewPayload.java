package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.animal.runtime.LivestockRecord;
import com.stardew.craft.animal.runtime.LivestockService;
import com.stardew.craft.animal.runtime.LivestockWorldData;
import com.stardew.craft.farm.FarmInstanceRegistry;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.stardew.craft.port.net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Client request for the authoritative animal list shown in the V menu. */
public record RequestAnimalOverviewPayload() implements CustomPacketPayload {
    public static final Type<RequestAnimalOverviewPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "request_animal_overview"));
    public static final StreamCodec<ByteBuf, RequestAnimalOverviewPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestAnimalOverviewPayload());

    private static final java.util.Set<String> BUILTIN_ANIMAL_TYPES = java.util.Set.of(
            "white_chicken", "brown_chicken", "blue_chicken", "void_chicken",
            "golden_chicken", "duck", "rabbit", "dinosaur", "white_cow", "brown_cow",
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
        PacketDistributor.sendToPlayer(player, new SyncAnimalOverviewPayload(entriesFor(player)));
    }

    /** Builds the V-menu snapshot from the authoritative post-migration livestock ledger. */
    public static List<SyncAnimalOverviewPayload.Entry> entriesFor(ServerPlayer player) {
        var server = player.serverLevel().getServer();
        LivestockService.recover(server);
        var farm = FarmInstanceRegistry.get(server).getFarmForPlayer(player.getUUID());
        if (farm == null) {
            return List.of();
        }

        List<SyncAnimalOverviewPayload.Entry> rows = new ArrayList<>();
        for (LivestockRecord animal : LivestockWorldData.get(server).all()) {
            if (!farm.getInstanceId().equals(animal.farm())) {
                continue;
            }
            String animalTypeId = animal.species().id();
            FarmAnimalDefinition definition = animal.species().definition();
            String sourceType = definition == null ? animalTypeId : definition.sourceKey();
            String baseType = sourceBaseType(sourceType);
            Visual visual = resolveVisual(animal, definition);
            int petStatus = animal.care().petted() ? 2 : animal.care().autoPetted() ? 1 : 0;
            rows.add(new SyncAnimalOverviewPayload.Entry(
                    animal.randomId(),
                    animalTypeId,
                    animal.name() == null ? "" : animal.name(),
                    FarmAnimalDefinitions.displayNameKeyFor(animal.species().definitionId()),
                    baseType,
                    sourceType,
                    animal.care().friendship(),
                    petStatus,
                    animal.cracker(),
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
        return List.copyOf(rows);
    }

    private static String sourceBaseType(String sourceType) {
        String normalized = sourceType == null ? "" : sourceType.trim();
        String[] parts = normalized.split("\\s+");
        return parts.length > 1 ? parts[1] : normalized;
    }

    private static Visual resolveVisual(LivestockRecord animal, FarmAnimalDefinition definition) {
        if (definition == null) {
            return Visual.EMPTY;
        }
        String animalTypeId = animal.species().id();
        if (StardewCraft.MODID.equals(definition.dataId().getNamespace())
                && BUILTIN_ANIMAL_TYPES.contains(animalTypeId)) {
            boolean baby = animal.baby() && !"dinosaur".equals(animalTypeId);
            String spriteName = ("white_cow".equals(animalTypeId) ? "cow" : animalTypeId)
                    + (baby ? "_baby" : "");
            ResourceLocation sprite = new ResourceLocation(
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
