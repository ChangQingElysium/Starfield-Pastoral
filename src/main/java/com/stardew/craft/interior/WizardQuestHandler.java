package com.stardew.craft.interior;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 巫师塔枢纽任务逻辑：
 * - 首次见面剧情对话
 * - 末影之眼交付检测
 * - 解锁星露谷入口
 * - 日常传送 query 分支路由
 */
@SuppressWarnings({"null", "unused"})
@EventBusSubscriber(modid = StardewCraft.MODID)
public final class WizardQuestHandler {

    private static final String NPC_ID = "wizard";

    // 巫师在巫师塔内部的位置（与 default_spawns.json 一致）
    private static final BlockPos WIZARD_POS = new BlockPos(-179, 34, 55);
    private static final double PROXIMITY_RANGE_SQ = 5.0 * 5.0; // 5 格内自动触发
    private static final int PROXIMITY_CHECK_INTERVAL = 20; // 每秒检测一次
    private static final String PROXIMITY_COOLDOWN_TAG = "stardewcraft_wizard_prox_tick";

    // 特殊 nextDialogueNode 值，用于识别巫师传送指令
    public static final String NODE_GO_STARDEW = "wizard_go_stardew";
    public static final String NODE_GO_OVERWORLD = "wizard_go_overworld";
    public static final String NODE_STAY = "wizard_stay";
    public static final String NODE_ACCEPTED = "wizard_accepted";
    public static final String NODE_FIRST_TELEPORT = "wizard_first_teleport";
    // ── SDV Event 112: 巫师森林魔法药水（多阶段过场） ──
    /** Phase 0→1: 巫师展示 Junimo ("看哪！") */
    public static final String NODE_E112_REVEAL     = "wizard_e112_reveal";
    /** Phase 1→2: Junimo 笼子消失，巫师解释 */
    public static final String NODE_E112_DISMISS     = "wizard_e112_dismiss";
    /** Phase 2→2.5: 巫师说"你留在这，我去看看"→ 消失 → 等待 → 回来 */
    public static final String NODE_E112_WIZARD_LEAVE = "wizard_e112_wizard_leave";
    /** Phase 2.5→3: 巫师回来后翻译卷轴 → 走到大锅 */
    public static final String NODE_E112_CAULDRON    = "wizard_e112_cauldron";
    /** Phase 3→4: 玩家喝药水 → 森林幻视 → 解锁 */
    public static final String NODE_E112_DRINK       = "wizard_e112_drink";

    /** 巫师塔内的 Junimo 展示位置（巫师附近） */
    private static final BlockPos JUNIMO_CAGE_POS = WIZARD_POS.offset(0, 0, 2);
    /** Event 112 过程中生成的临时 Junimo 的 tag 前缀（后跟玩家UUID） */
    private static final String TAG_E112_JUNIMO_PREFIX = "stardewcraft_e112_junimo_";
    /** 巫师离开后的等待倒计 tag（存 tick 数） */
    private static final String TAG_E112_WIZARD_AWAY_TICK = "stardewcraft_e112_wizard_away";
    /** 巫师消失到返回的等待时间（tick），约4秒 */
    private static final int WIZARD_AWAY_DURATION = 80;

    private WizardQuestHandler() {}

    /**
     * 玩家退出时清理 E112 状态（旧倒计时 tag）。
     * Junimo 现在由 cutscene 引擎在客户端管理，无需服务端清理。
     */
    public static void onPlayerLogout(ServerPlayer player) {
        player.getPersistentData().remove(TAG_E112_WIZARD_AWAY_TICK);
    }

    /**
     * 玩家靠近巫师时自动触发对话。
     * 仅在首次见面时自动触发（任务发布），其他情况需要右键交互。
     * 同时处理 E112 巫师离开后的等待倒计时。
     */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!ModDimensions.STARDEW_VALLEY.equals(player.level().dimension())) return;

        // E112 倒计时已移至 cutscene 引擎 (wizard_e112.json 中的 pause 命令)

        if (player.tickCount % PROXIMITY_CHECK_INTERVAL != 0) return;

        PlayerStardewData data = PlayerDataManager.getPlayerData(player);

        // Repair older saves where Event 112 set canReadJunimoText but the quest
        // was completed through StardewQuest directly, bypassing manager sync.
        if (CCStoryFlags.canReadJunimoText(player)) {
            completeMeetWizardQuest(player);
        }

        // 只在需要自动触发时才继续检测
        boolean needsIntro = !data.isWizardFirstMet();
        boolean needsE112 = CCStoryFlags.hasSeenJunimoNote(player) && !CCStoryFlags.canReadJunimoText(player);
        if (!needsIntro && !needsE112) return;

        // 检测是否在巫师附近
        double distSq = player.blockPosition().distSqr(WIZARD_POS);
        if (distSq > PROXIMITY_RANGE_SQ) {
            player.getPersistentData().remove(PROXIMITY_COOLDOWN_TAG);
            return;
        }

        // 冷却检测：进入范围后只触发一次
        if (player.getPersistentData().contains(PROXIMITY_COOLDOWN_TAG)) return;
        player.getPersistentData().putBoolean(PROXIMITY_COOLDOWN_TAG, true);

        if (needsIntro) {
            // 首次见面 → 触发 wizard_intro cutscene
            data.setWizardFirstMet(true);
            triggerCutscene(player, "wizard_intro");
        } else {
            // 看过 JunimoNote 但未解锁文字 → 自动触发 wizard_e112
            completeMeetWizardQuest(player);
            CCStoryFlags.addFlag(player, CCStoryFlags.CAN_READ_JUNIMO);
            com.stardew.craft.communitycenter.network.BundleSyncPayload.sendFullSync(player);
            triggerCutscene(player, "wizard_e112");
        }
    }

    /**
     * 在 NPC 交互时调用。如果是巫师，优先处理任务相关对话。
     * @return true 如果已处理（调用方应跳过普通对话），false 走普通对话
     */
    public static boolean handleWizardInteraction(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        com.stardew.craft.farm.FarmInstanceRegistry farmRegistry = com.stardew.craft.farm.FarmInstanceRegistry.get();

        // ── SDV Event 112 parity: 巫师森林魔法药水 → 解锁 Junimo 文字 ──
        // 玩家看过 JunimoNote（收到巫师邀请信）但尚未解锁 → 触发 cutscene 过场
        if (CCStoryFlags.hasSeenJunimoNote(player) && !CCStoryFlags.canReadJunimoText(player)) {
            // 完成 meetTheWizard 任务 (Quest ID 1)
            completeMeetWizardQuest(player);
            // 直接在服务端设 flag，不依赖 cutscene 内的 set_flag 命令
            // 防止 cutscene 失败/跳过导致 flag 永远不被设置，锁死交互
            CCStoryFlags.addFlag(player, CCStoryFlags.CAN_READ_JUNIMO);
            com.stardew.craft.communitycenter.network.BundleSyncPayload.sendFullSync(player);
            triggerCutscene(player, "wizard_e112");
            return true;
        }

        if (data.isWizardQuestComplete()) {
            if (farmRegistry.getFarmForPlayer(player.getUUID()) == null) {
                handleGoStardew(player);
                return true;
            }
            // 任务已完成 → 走普通 NPC 对话系统（每日1次限制、对话轮转、好感度）
            return false;
        }

        if (!data.isWizardFirstMet()) {
            // 首次见面 → 触发 wizard_intro cutscene
            data.setWizardFirstMet(true);
            triggerCutscene(player, "wizard_intro");
            return true;
        }

        // 已见过但未完成任务：检查背包是否有末影之眼
        ItemStack eyeSlot = findEyeOfEnder(player);
        if (eyeSlot != null) {
            StardewCraft.LOGGER.info("[WIZARD] {} has Eye of Ender, consuming from THEIR inventory (UUID={})",
                    player.getName().getString(), player.getStringUUID());
            eyeSlot.shrink(1);
            data.setWizardQuestComplete(true);
            sendDialogue(player, "stardewcraft.npc.wizard.has_eye", 0);
            StardewCraft.LOGGER.info("[WIZARD] {} completed wizard quest (Eye of Ender)", player.getName().getString());

            // 法师任务完成 → 立即触发故事任务（不必等到下一次 onDayStarted）
            com.stardew.craft.time.StardewTimeManager tm = com.stardew.craft.time.StardewTimeManager.get();
            int absDay = (tm.getCurrentYear() - 1) * 112 + tm.getCurrentSeason() * 28 + tm.getCurrentDay();
            data.getQuestManager().triggerStoryQuests(player, absDay);
            return true;
        }

        // 没有末影之眼：提醒（任务进行中，仍拦截）
        // 但如果玩家还没看过 JunimoNote，不应该锁死 → 走普通对话
        if (!CCStoryFlags.hasSeenJunimoNote(player)) {
            return false;
        }
        sendDialogue(player, "stardewcraft.npc.wizard.daily_locked", 0);
        return true;
    }

    private static void completeMeetWizardQuest(ServerPlayer player) {
        com.stardew.craft.quest.QuestManager.of(player).completeActiveQuest("1", player);
    }

    /**
     * 处理 AnswerNpcQuestionPayload 的回调。
     * @return true 如果已处理，false 走普通 handleClientQuestionAnswer 链
     */
    public static boolean handleWizardQuestionAnswer(ServerPlayer player, String nextDialogueNode) {
        if (nextDialogueNode == null) return false;

        return switch (nextDialogueNode) {
            case NODE_GO_STARDEW -> {
                handleGoStardew(player);
                yield true;
            }
            case NODE_GO_OVERWORLD -> {
                CrossDimensionTeleporter.wizardInteriorToOverworld(player);
                yield true;
            }
            case NODE_STAY -> {
                // 关闭对话，什么也不做
                yield true;
            }
            case NODE_ACCEPTED -> {
                // 玩家接受了任务，显示后续对话
                sendDialogue(player, "stardewcraft.npc.wizard.accepted", 0);
                yield true;
            }
            case NODE_FIRST_TELEPORT -> {
                // 首次解锁后传送到星露谷室外（农场门前）
                handleGoStardew(player);
                yield true;
            }
            // E112 多阶段过场已迁移至 cutscene 引擎 (wizard_e112.json)
            default -> false;
        };
    }

    // ═══════════════════════════════════════════════════════════
    // SDV Event 112 — Phase handlers
    // ═══════════════════════════════════════════════════════════

    /**
    // ═══ E112 多阶段过场方法已迁移至 cutscene 引擎 (wizard_e112.json) ═══
    // handleE112Reveal, handleE112Dismiss, handleE112WizardLeave,
    // handleE112WizardReturn, handleE112Drink 已由 JSON cutscene 替代

    /** 在巫师塔附近（含隐藏位置）搜索巫师 NPC 实体 */
    private static java.util.Optional<StardewNpcEntity> findWizardNpc(ServerLevel level) {
        // 搜索 WIZARD_POS 周围范围
        net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(WIZARD_POS).inflate(10);
        return level.getEntitiesOfClass(StardewNpcEntity.class, searchBox,
                e -> NPC_ID.equals(e.getNpcId())
        ).stream().findFirst();
    }

    /** 向玩家发送屏幕闪白 (SDV: screenFlash .8) */
    private static void sendScreenFlash(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player,
                new com.stardew.craft.communitycenter.cutscene.CutscenePayload(
                        com.stardew.craft.communitycenter.cutscene.CutscenePayload.TYPE_AREA_RESTORE,
                        com.stardew.craft.communitycenter.cutscene.CutscenePayload.PHASE_RESTORE,
                        -1, player.blockPosition()));
    }

    /** 播放音效给附近玩家（旧方法，向后兼容） */
    private static void playSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound) {
        player.level().playSound(null, player.blockPosition(), sound, SoundSource.MASTER, 1.0f, 1.0f);
    }

    /** 仅对指定玩家播放音效（不广播给附近其他人），用于per-player剧情 */
    private static void playSoundToPlayer(ServerPlayer player, net.minecraft.sounds.SoundEvent sound) {
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(
                net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),
                SoundSource.MASTER,
                player.getX(), player.getY(), player.getZ(),
                1.0f, 1.0f,
                player.level().random.nextLong()));
    }

    // ═══════════════════════════════════════════════════════════

    /**
     * 前往星露谷室外：如果玩家还没有农场，先弹出农场选择界面。
     */
    private static void handleGoStardew(ServerPlayer player) {
        com.stardew.craft.farm.FarmInstanceRegistry registry = com.stardew.craft.farm.FarmInstanceRegistry.get();
        if (registry.hasFarm(player.getUUID())) {
            // 已有农场，直接传送
            CrossDimensionTeleporter.wizardInteriorToStardewOutdoor(player);
        } else {
            // 没有农场 → 发送打开农场选择界面的包
                com.stardew.craft.farm.FarmJoinManager.syncPendingState(
                    player,
                    com.stardew.craft.farm.FarmJoinManager.hasPending(player.getUUID())
                );
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new com.stardew.craft.network.payload.OpenFarmSelectionPayload());
            StardewCraft.LOGGER.info("[WIZARD] {} has no farm, opening farm selection screen",
                    player.getName().getString());
        }
    }

    private static ItemStack findEyeOfEnder(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.ENDER_EYE) && !stack.isEmpty()) {
                return stack;
            }
        }
        return null;
    }

    private static void sendDialogue(ServerPlayer player, String translateKey, int friendshipPoints) {
        PacketDistributor.sendToPlayer(player,
            new OpenNpcDialogueScreenPayload(NPC_ID, translateKey, friendshipPoints));
    }

    /** 触发 cutscene 事件 (发送 TriggerEventPayload 到客户端) */
    private static void triggerCutscene(ServerPlayer player, String eventId) {
        com.stardew.craft.cutscene.server.ServerCutsceneTracker.startEvent(player, eventId);
        StardewCraft.LOGGER.info("[WIZARD] Triggered cutscene '{}' for {}", eventId, player.getName().getString());
    }
}
