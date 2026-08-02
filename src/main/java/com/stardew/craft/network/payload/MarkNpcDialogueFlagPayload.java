package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Set;

/** Persists the safe, dialogue-only mail key produced by vanilla's $1 token. */
public record MarkNpcDialogueFlagPayload(String flag) implements CustomPacketPayload {
    private static final Set<String> ALLOWED_FLAGS = Set.of(
            "Abigail1", "AbigailHAND", "Caroline1", "Caroline12", "Caroline23",
            "HaleyClothes", "HaleySister", "LeahBug", "LinusFall1", "LinusHeron",
            "PamDrank", "PamDrunk", "RobinDem", "RobinMaru", "RobinSeb",
            "Sebastian1", "ShaneJOSH", "elliottApol", "evelynGarden1",
            "haleySeagull", "linusVandal", "marnieAnimalSal", "pierre1",
            "pierre2", "pierreBlue", "pierreDin", "pierreJoja", "pierreMEGA"
    );

    public static final Type<MarkNpcDialogueFlagPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "mark_npc_dialogue_flag"));
    public static final StreamCodec<ByteBuf, MarkNpcDialogueFlagPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, MarkNpcDialogueFlagPayload::flag,
                    MarkNpcDialogueFlagPayload::new);

    public static void handle(MarkNpcDialogueFlagPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || !ALLOWED_FLAGS.contains(payload.flag())) {
                return;
            }
            var data = PlayerDataManager.getPlayerData(player);
            if (!data.hasMailFlag(payload.flag())) {
                data.addMailFlag(payload.flag());
                PlayerDataEventHandler.syncPlayerData(player, data);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
