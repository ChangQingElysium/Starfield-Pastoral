package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.PlayerGenderText;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.statue.UncertaintyStatueService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public record OpenUncertaintyStatuePayload(BlockPos statuePos, int mode, List<Integer> skillIds)
        implements CustomPacketPayload {
    public static final int MODE_CONFIRM = 0;
    public static final int MODE_SKILL_SELECT = 1;

    public static final Type<OpenUncertaintyStatuePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_uncertainty_statue"));

    public static final StreamCodec<FriendlyByteBuf, OpenUncertaintyStatuePayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeBlockPos(payload.statuePos());
            buf.writeVarInt(payload.mode());
            buf.writeVarInt(payload.skillIds().size());
            for (int skillId : payload.skillIds()) {
                buf.writeVarInt(skillId);
            }
        },
        buf -> {
            BlockPos pos = buf.readBlockPos();
            int mode = buf.readVarInt();
            int count = buf.readVarInt();
            List<Integer> skills = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                skills.add(buf.readVarInt());
            }
            return new OpenUncertaintyStatuePayload(pos, mode, skills);
        });

    public static OpenUncertaintyStatuePayload confirm(BlockPos pos) {
        return new OpenUncertaintyStatuePayload(pos, MODE_CONFIRM, List.of());
    }

    public static OpenUncertaintyStatuePayload skillSelect(BlockPos pos, List<SkillType> skills) {
        return new OpenUncertaintyStatuePayload(
            pos,
            MODE_SKILL_SELECT,
            skills.stream().map(SkillType::getId).toList());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenUncertaintyStatuePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleClient(OpenUncertaintyStatuePayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        if (payload.mode() == MODE_CONFIRM) {
            mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
                com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                    Component.translatable("stardewcraft.uncertainty_statue.confirm"),
                    List.of(
                        Component.translatable("stardewcraft.dialog.yes"),
                        Component.translatable("stardewcraft.dialog.no")
                    ),
                    index -> PacketDistributor.sendToServer(new UncertaintyStatueResponsePayload(
                        payload.statuePos(),
                        index == 0 ? UncertaintyStatueService.ACTION_CONFIRM : UncertaintyStatueService.ACTION_CANCEL,
                        -1)),
                    1
                )));
            return;
        }

        List<Component> responses = new ArrayList<>();
        for (int skillId : payload.skillIds()) {
            SkillType skill = SkillType.fromId(skillId);
            responses.add(Component.translatable("stardewcraft.skill." + skill.getName()));
        }
        responses.add(Component.literal(PlayerGenderText.preprocess(
                Component.translatable("stardewcraft.uncertainty_statue.cancel").getString())));

        mc.setScreen(com.stardew.craft.client.gui.common.StardewConfirmDialogScreen.createQuestionDialog(
            com.stardew.craft.client.gui.common.StardewQuestionDialogSpec.of(
                Component.translatable("stardewcraft.uncertainty_statue.choose_skill"),
                responses,
                index -> {
                    if (index < 0 || index >= payload.skillIds().size()) {
                        PacketDistributor.sendToServer(new UncertaintyStatueResponsePayload(
                            payload.statuePos(), UncertaintyStatueService.ACTION_CANCEL, -1));
                        return;
                    }
                    PacketDistributor.sendToServer(new UncertaintyStatueResponsePayload(
                        payload.statuePos(),
                        UncertaintyStatueService.ACTION_SELECT_SKILL,
                        payload.skillIds().get(index)));
                },
                responses.size() - 1
            )));
    }
}
