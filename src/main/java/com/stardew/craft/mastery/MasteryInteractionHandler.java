package com.stardew.craft.mastery;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.player.SkillType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Interaction;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Optional;

/**
 * 精通山洞交互：门拦截 / 中央讲台 / 5 个 item_display 祭坛。
 */
@SuppressWarnings("removal")
@EventBusSubscriber(modid = StardewCraft.MODID, bus = EventBusSubscriber.Bus.FORGE)
public final class MasteryInteractionHandler {
    private MasteryInteractionHandler() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!MasterySite.isMasteryDimension(player.level())) return;

        var pos = event.getPos();

        // 中央讲台 → 总览 UI
        if (MasterySite.isCentralPedestal(pos)) {
            MasteryMenuOpener.open(player, null);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        // 山洞门 → 5×Lv10 校验
        if (MasterySite.isDoorPos(pos)) {
            if (!canEnterCave(player)) {
                com.stardew.craft.npc.runtime.NpcInteractionService.sendDialogue(player,
                    new OpenNpcDialogueScreenPayload("", caveMessageKey(maxedSkillCount(player)), 0));
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
            }
            // 满足条件 → 让 vanilla iron_door 正常打开
        }
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!MasterySite.isMasteryDimension(player.level())) return;

        Entity target = event.getTarget();
        Optional<SkillType> skill = Optional.empty();

        // 主路径：Interaction 实体的 scoreboard tag。
        if (target instanceof Interaction) {
            for (SkillType s : SkillType.values()) {
                if (target.getTags().contains(MasterySiteInstaller.interactionTagFor(s))) {
                    skill = Optional.of(s);
                    break;
                }
            }
        }
        // 兜底：手动 /summon 的 ItemDisplay 按位置识别。
        if (skill.isEmpty() && target instanceof Display.ItemDisplay) {
            skill = MasterySite.skillForDisplay(target);
        }
        if (skill.isEmpty()) return;

        MasteryMenuOpener.open(player, skill.get());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** 5 项技能全部 Lv10。 */
    public static boolean canEnterCave(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null) return false;
        return data.hasAllSkillsMaxed();
    }

    private static int maxedSkillCount(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        if (data == null) return 0;
        return data.getMaxedSkillCount();
    }

    private static String caveMessageKey(int maxedSkillCount) {
        int clamped = Math.max(0, Math.min(4, maxedSkillCount));
        return "stardewcraft.mastery.cave." + clamped;
    }
}
