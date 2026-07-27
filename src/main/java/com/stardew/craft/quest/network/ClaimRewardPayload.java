package com.stardew.craft.quest.network;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.internal.progress.StardewProgressRegistry;
import com.stardew.craft.api.v1.progress.StardewProgressCauses;
import com.stardew.craft.api.v1.progress.StardewProgressEvent;
import com.stardew.craft.api.v1.progress.StardewProgressEventType;
import com.stardew.craft.api.v1.progress.StardewProgressPhase;
import com.stardew.craft.api.v1.progress.StardewProgressDomains;
import com.stardew.craft.api.v1.progress.StardewProgressKey;
import com.stardew.craft.api.v1.progress.StardewProgressOperation;
import com.stardew.craft.api.v1.progress.StardewProgressRequirements;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.quest.QuestManager;
import com.stardew.craft.quest.QuestDataLoader;
import com.stardew.craft.quest.StardewQuest;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * C→S: 玩家领取任务奖励
 */
@SuppressWarnings("null")
public record ClaimRewardPayload(String questId) implements CustomPacketPayload {

    public static final Type<ClaimRewardPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "claim_quest_reward")
    );

    public static final StreamCodec<ByteBuf, ClaimRewardPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        ClaimRewardPayload::questId,
        ClaimRewardPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ClaimRewardPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            PlayerStardewData data = PlayerDataManager.getPlayerData(serverPlayer);
            if (data == null) return;
            ResourceLocation questId =
                    QuestDataLoader.normalizeId(payload.questId());
            if (questId == null
                    || !StardewProgressRequirements.requirements(
                                    serverPlayer,
                                    new StardewProgressKey(
                                            StardewProgressDomains.QUEST,
                                            questId),
                                    StardewProgressOperation
                                            .CLAIM_REWARD)
                            .satisfied()) {
                return;
            }
            QuestManager mgr = data.getQuestManager();
            StardewQuest quest = mgr.getQuest(payload.questId());
            if (quest != null && quest.isCompleted() && quest.hasMoneyReward()) {
                var before = StardewProgressRegistry.questSnapshot(quest, null);
                PlayerStardewDataAPI.addMoney(serverPlayer, quest.getMoneyReward());
                // SDV: questComplete() already fired nextQuests; here we just mark destroy
                // so cleanupDestroyed handles completedQuestIds + nextQuests + removal
                quest.setDestroy(true);
                var after = StardewProgressRegistry.questSnapshot(
                        quest, StardewProgressPhase.COMPLETED);
                StardewProgressRegistry.dispatch(new StardewProgressEvent(
                        StardewProgressEventType.REWARD_CLAIMED,
                        serverPlayer.serverLevel(),
                        Optional.of(serverPlayer.getUUID()),
                        Optional.of(before),
                        after,
                        StardewProgressCauses.REWARD_CLAIM));
                mgr.cleanupDestroyed(serverPlayer);
            }
        });
    }
}
