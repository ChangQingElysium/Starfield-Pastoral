package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.effect.ModMobEffects;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.SkillType;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.Holder;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * C → S：玩家在矮人王雕像菜单点击其中一个图标。
 * 严格按 SDV ChooseFromIconsMenu.doIconAction:305-311 — 仅当玩家不持有任何
 * dwarfStatue* buff 时才应用 dwarf_statue_<chosen>。
 */
public record ChooseDwarfStatueBuffPayload(int chosenIcon) implements CustomPacketPayload {

    @SuppressWarnings("null")
    public static final Type<ChooseDwarfStatueBuffPayload> TYPE = new Type<>(
        new ResourceLocation(StardewCraft.MODID, "choose_dwarf_statue_buff"));

    public static final StreamCodec<ByteBuf, ChooseDwarfStatueBuffPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ChooseDwarfStatueBuffPayload::chosenIcon,
        ChooseDwarfStatueBuffPayload::new
    );

    private static final List<Holder<MobEffect>> DWARF_BUFFS = List.of(
        ModMobEffects.DWARF_STATUE_0,
        ModMobEffects.DWARF_STATUE_1,
        ModMobEffects.DWARF_STATUE_2,
        ModMobEffects.DWARF_STATUE_3,
        ModMobEffects.DWARF_STATUE_4
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(ChooseDwarfStatueBuffPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer sp)) return;
            int i = payload.chosenIcon();
            if (i < 0 || i >= DWARF_BUFFS.size()) return;

            PlayerStardewData data = PlayerDataManager.getPlayerData(sp);
            if (data == null) return;
            if (!data.hasClaimedMasteryReward(SkillType.MINING)) return;

            // 已有任意 dwarf_statue_N 时禁止再领
            for (Holder<MobEffect> b : DWARF_BUFFS) {
                if (sp.hasEffect(b.value())) return;
            }

            sp.addEffect(new MobEffectInstance(DWARF_BUFFS.get(i).value(), -1, 0, false, false, true));
        });
    }
}
