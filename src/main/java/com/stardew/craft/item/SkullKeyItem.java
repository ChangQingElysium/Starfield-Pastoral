package com.stardew.craft.item;

import com.stardew.craft.communitycenter.state.CCStoryFlags;
import com.stardew.craft.player.PlayerDataEventHandler;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 骷髅钥匙 — 沙漠骷髅矿洞通行证 / 永久纪念物。
 * <p>
 * 视觉表现层：
 * <ul>
 *     <li>物品名：金/古铜底色 + 一道高光从左向右扫过（"扫光" 高级装备效果）</li>
 *     <li>flavor 行：单一暖金色调的明暗呼吸（不变色相）</li>
 *     <li>持有时第一人称/世界中喷出金色 END_ROD 粒子（已在客户端 inventoryTick 中处理）</li>
 *     <li>附魔光泽 + 不可被熔毁/不可销毁/不可出售</li>
 * </ul>
 * Tooltip 边框颜色由 {@link com.stardew.craft.client.SkullKeyClientFx} 在 RenderTooltipEvent.Color
 * 中以同主题动态渐变。
 */
public class SkullKeyItem extends Item implements IStardewItem {

    /** 名字扫光：每秒走过百分比。 */
    private static final float NAME_SWEEP_SPEED = 0.45f;
    /** 名字扫光宽度（亮度峰半宽度，相对总长）。 */
    private static final float NAME_SWEEP_WIDTH = 0.30f;

    /** 暖金底色。 */
    private static final int NAME_BASE_RGB      = 0xC9982A;
    /** 高光：暖白金。 */
    private static final int NAME_HIGHLIGHT_RGB = 0xFFF6C9;

    public SkullKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public String getItemTypeKey() {
        return "stardewcraft.type.special";
    }

    @Override
    public int getSellPrice(ItemStack stack) {
        return -1;
    }

    /** 进入背包瞬间授予 mail flag（仅服务端、首次）。物品永久保留。 */
    @Override
    public void inventoryTick(@Nonnull ItemStack stack, @Nonnull Level level, @Nonnull Entity entity, int slot, boolean selected) {
        if (level.isClientSide) return;
        if (!(entity instanceof ServerPlayer sp)) return;
        PlayerStardewData data = PlayerDataManager.getPlayerData(sp);
        boolean changed = false;
        if (!data.hasMailFlag(CCStoryFlags.HAS_SKULL_KEY)) {
            data.addMailFlag(CCStoryFlags.HAS_SKULL_KEY);
            changed = true;
        }
        if (!data.hasSpecialItem(CCStoryFlags.SKULL_KEY_SPECIAL_ITEM)) {
            data.addSpecialItem(CCStoryFlags.SKULL_KEY_SPECIAL_ITEM);
            changed = true;
        }
        com.stardew.craft.quest.QuestManager qm = com.stardew.craft.quest.QuestManager.of(sp);
        if (qm != null && !qm.hasQuest("19") && !qm.isQuestCompleted("19")) {
            qm.acceptQuest("19", sp);
        }
        if (changed) {
            PlayerDataManager.get().savePlayerData(sp.getUUID(), data);
            PlayerDataEventHandler.syncPlayerData(sp, data);
            sp.playSound(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f);
            sp.sendSystemMessage(Component.translatable("stardewcraft.item.skull_key.obtained"));
        }
    }

    @Override
    public boolean isFoil(@Nonnull ItemStack stack) {
        return true;
    }

    /** 掉落实体不可被火/熔岩/爆炸等伤害（fireResistant 已经覆盖大半，这里再保险）。 */
    @Override
    public boolean canBeHurtBy(@Nonnull ItemStack stack, @Nonnull net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    // ─────────────────────────── 名字：扫光金辉 ───────────────────────────

    @Override
    public Component getName(@Nonnull ItemStack stack) {
        String raw = Component.translatable(this.getDescriptionId(stack)).getString();
        return sweepHighlight(raw, NAME_BASE_RGB, NAME_HIGHLIGHT_RGB,
                NAME_SWEEP_SPEED, NAME_SWEEP_WIDTH, true);
    }

    /**
     * 沿文本从左向右滚动一道高光：每个字符颜色 = lerp(base, highlight, 1 - dist/width)。
     * 高光峰位置随时间循环：{@code -width .. 1+width}，让光带能完全划进/划出文字两端。
     */
    private static MutableComponent sweepHighlight(String raw, int baseRgb, int hiRgb,
                                                   float speedPerSec, float halfWidth,
                                                   boolean bold) {
        long ms = System.currentTimeMillis();
        float t = (ms % 60_000L) / 1000.0f;
        float span = 1.0f + halfWidth * 2.0f;
        float pos = ((t * speedPerSec) % span) - halfWidth;

        int n = raw.length();
        MutableComponent out = Component.empty();
        for (int i = 0; i < n; i++) {
            float u = n > 1 ? (float) i / (n - 1) : 0.5f;
            float dist = Math.abs(u - pos);
            float k = Math.max(0.0f, 1.0f - dist / halfWidth);
            // 平滑曲线 (smoothstep)：让光带边缘更柔和
            k = k * k * (3.0f - 2.0f * k);
            int rgb = lerpRgb(baseRgb, hiRgb, k);
            Style s = Style.EMPTY.withColor(TextColor.fromRgb(rgb));
            if (bold) s = s.withBold(true);
            out.append(Component.literal(String.valueOf(raw.charAt(i))).withStyle(s));
        }
        return out;
    }

    // ─────────────────────────── Tooltip ───────────────────────────

    @Override
    public void appendHoverText(@Nonnull ItemStack stack, @Nonnull TooltipContext context,
                                @Nonnull List<Component> tooltipComponents, @Nonnull TooltipFlag tooltipFlag) {
        // 一条干净的描述句（深金，无斜体）—— 全部用同一色度，不再多行花字
        tooltipComponents.add(Component.translatable("stardewcraft.item.skull_key.tooltip.flavor")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xC9A24A))));
        // 状态行：与 flavor 同主题暖金，仅加粗以突出
        tooltipComponents.add(Component.translatable("stardewcraft.item.skull_key.tooltip.granted")
                .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xE8C46A)).withBold(true)));
    }

    // ─────────────────────────── 颜色工具 ───────────────────────────

    private static int lerpRgb(int a, int b, float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * k);
        int g = Math.round(ag + (bg - ag) * k);
        int bl = Math.round(ab + (bb - ab) * k);
        return (r << 16) | (g << 8) | bl;
    }
}
