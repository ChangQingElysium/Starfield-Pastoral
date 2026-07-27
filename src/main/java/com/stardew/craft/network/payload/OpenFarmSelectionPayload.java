package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutConfigField;
import com.stardew.craft.api.v1.farm.StardewFarmLayoutPreview;
import com.stardew.craft.api.v1.farm.StardewFarmLayouts;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S→C: opens farm selection with a server-authored, geometry-free catalog.
 */
@SuppressWarnings("null")
public record OpenFarmSelectionPayload(
        List<StardewFarmLayoutPreview> layouts
) implements CustomPacketPayload {
    private static final int MAX_LAYOUTS = 128;
    private static final int MAX_FIELDS = 64;
    private static final int MAX_CHOICES = 64;

    public static final Type<OpenFarmSelectionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    StardewCraft.MODID, "open_farm_selection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFarmSelectionPayload> STREAM_CODEC =
            StreamCodec.of(
                    OpenFarmSelectionPayload::encode,
                    OpenFarmSelectionPayload::decode);

    public OpenFarmSelectionPayload {
        layouts = List.copyOf(layouts);
        if (layouts.size() > MAX_LAYOUTS) {
            throw new IllegalArgumentException("Too many farm layouts for selection payload");
        }
    }

    /** Builds the current server catalog; retained for existing send sites. */
    public OpenFarmSelectionPayload() {
        this(StardewFarmLayouts.allRegistrations().stream()
                .map(StardewFarmLayoutPreview::from)
                .toList());
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            OpenFarmSelectionPayload payload
    ) {
        buffer.writeVarInt(payload.layouts().size());
        for (StardewFarmLayoutPreview preview : payload.layouts()) {
            ResourceLocation.STREAM_CODEC.encode(buffer, preview.id());
            buffer.writeBoolean(preview.selectable());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(
                    buffer, preview.displayName());
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(
                    buffer, preview.description());
            ResourceLocation.STREAM_CODEC.encode(buffer, preview.iconTexture());
            buffer.writeVarInt(preview.version());
            buffer.writeVarInt(preview.configurationFields().size());
            for (StardewFarmLayoutConfigField field
                    : preview.configurationFields()) {
                encodeField(buffer, field);
            }
        }
    }

    private static OpenFarmSelectionPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        int layoutCount = readBoundedCount(buffer, MAX_LAYOUTS, "farm layouts");
        ArrayList<StardewFarmLayoutPreview> layouts =
                new ArrayList<>(layoutCount);
        for (int i = 0; i < layoutCount; i++) {
            ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
            boolean selectable = buffer.readBoolean();
            Component displayName =
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            Component description =
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
            ResourceLocation icon =
                    ResourceLocation.STREAM_CODEC.decode(buffer);
            int version = buffer.readVarInt();
            int fieldCount = readBoundedCount(
                    buffer, MAX_FIELDS, "farm layout fields");
            ArrayList<StardewFarmLayoutConfigField> fields =
                    new ArrayList<>(fieldCount);
            for (int fieldIndex = 0; fieldIndex < fieldCount; fieldIndex++) {
                fields.add(decodeField(buffer));
            }
            layouts.add(new StardewFarmLayoutPreview(
                    id, selectable, displayName, description,
                    icon, version, fields));
        }
        return new OpenFarmSelectionPayload(layouts);
    }

    private static void encodeField(
            RegistryFriendlyByteBuf buffer,
            StardewFarmLayoutConfigField field
    ) {
        ResourceLocation.STREAM_CODEC.encode(buffer, field.id());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(
                buffer, field.label());
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(
                buffer, field.description());
        buffer.writeEnum(field.type());
        buffer.writeUtf(field.defaultValue(), 128);
        buffer.writeVarInt(field.minimum());
        buffer.writeVarInt(field.maximum());
        buffer.writeVarInt(field.choices().size());
        for (String choice : field.choices()) {
            buffer.writeUtf(choice, 128);
        }
    }

    private static StardewFarmLayoutConfigField decodeField(
            RegistryFriendlyByteBuf buffer
    ) {
        ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
        Component label =
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        Component description =
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buffer);
        StardewFarmLayoutConfigField.Type type =
                buffer.readEnum(StardewFarmLayoutConfigField.Type.class);
        String defaultValue = buffer.readUtf(128);
        int minimum = buffer.readVarInt();
        int maximum = buffer.readVarInt();
        int choiceCount = readBoundedCount(
                buffer, MAX_CHOICES, "farm layout choices");
        ArrayList<String> choices = new ArrayList<>(choiceCount);
        for (int i = 0; i < choiceCount; i++) {
            choices.add(buffer.readUtf(128));
        }
        return new StardewFarmLayoutConfigField(
                id, label, description, type, defaultValue,
                minimum, maximum, choices);
    }

    private static int readBoundedCount(
            RegistryFriendlyByteBuf buffer,
            int maximum,
            String name
    ) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + name + " count: " + count);
        }
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            OpenFarmSelectionPayload payload,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenFarmSelectionPayload payload) {
        net.minecraft.client.Minecraft minecraft =
                net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null || payload.layouts().isEmpty()) {
            return;
        }
        com.stardew.craft.client.farm.FarmLayoutClientCatalog.replace(
                payload.layouts());
        minecraft.setScreen(
                new com.stardew.craft.client.gui.FarmSelectionScreen());
    }
}
