package com.stardew.craft.client.render;

import com.stardew.craft.client.font.StardewFonts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stardew.craft.Config;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.List;

/**
 * 矿井怪物头顶 HP 条渲染器
 */
@SuppressWarnings({"null", "unused"})
public class MineMonsterHpBarRenderer {

    private static final float RENDER_DISTANCE = 24f;

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!Config.SHOW_MONSTER_HP_BAR.get()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean inMine = mc.level.dimension().equals(ModMiningDimensions.STARDEW_MINING);

        if (!inMine) return;

        Vec3 cam = event.getCamera().getPosition();
        AABB searchBox = new AABB(
                cam.x - RENDER_DISTANCE, cam.y - RENDER_DISTANCE, cam.z - RENDER_DISTANCE,
                cam.x + RENDER_DISTANCE, cam.y + RENDER_DISTANCE, cam.z + RENDER_DISTANCE);

        List<Mob> mobs = mc.level.getEntitiesOfClass(Mob.class, searchBox,
                m -> m.isAlive() && m.getCustomName() != null);

        if (mobs.isEmpty()) return;

        Font font = StardewFonts.small();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        for (Mob mob : mobs) {
            float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            double x = mob.xOld + (mob.getX() - mob.xOld) * partialTick - cam.x;
            double y = mob.yOld + (mob.getY() - mob.yOld) * partialTick - cam.y + mob.getBbHeight() + 0.5;
            double z = mob.zOld + (mob.getZ() - mob.zOld) * partialTick - cam.z;

            float dist = (float) Math.sqrt(x * x + y * y + z * z);
            if (dist > RENDER_DISTANCE) continue;

            event.getPoseStack().pushPose();
            event.getPoseStack().translate(x, y, z);
            // Billboard: use camera rotation (same as DamageNumberClient)
            event.getPoseStack().mulPose(event.getCamera().rotation());
            float scale = 0.025f;
            event.getPoseStack().scale(scale, -scale, scale);

            // Name + HP text
            String mobName = getSDVMobName(mob);
            int hp = Math.round(mob.getHealth());
            int maxHp = Math.round(mob.getMaxHealth());
            float hpRatio = Mth.clamp(mob.getHealth() / mob.getMaxHealth(), 0f, 1f);

            // Line 1: Name [HP/MaxHP]
            String line1 = mobName + " §7[§f" + hp + "§7/§f" + maxHp + "§7]";
            Component nameComp = Component.literal(line1);
            float nameWidth = font.width(nameComp);

            font.drawInBatch(
                nameComp.getVisualOrderText(),
                -nameWidth / 2f, -5,
                0xFFFFFFFF,
                false,
                event.getPoseStack().last().pose(),
                buffer,
                Font.DisplayMode.NORMAL,
                0x80000000,
                0xF000F0
            );

            // Line 2: HP bar as text characters
            int barLen = 20;
            int filled = Math.round(barLen * hpRatio);
            StringBuilder barStr = new StringBuilder();
            int barColor = getHpColor(hpRatio);
            String colorCode = hpRatio > 0.6f ? "§a" : hpRatio > 0.3f ? "§e" : "§c";
            barStr.append(colorCode);
            for (int i = 0; i < filled; i++) barStr.append("█");
            barStr.append("§8");
            for (int i = filled; i < barLen; i++) barStr.append("░");
            Component barComp = Component.literal(barStr.toString());
            float barWidth = font.width(barComp);

            font.drawInBatch(
                barComp.getVisualOrderText(),
                -barWidth / 2f, 5,
                0xFFFFFFFF,
                false,
                event.getPoseStack().last().pose(),
                buffer,
                Font.DisplayMode.NORMAL,
                0,
                0xF000F0
            );

            event.getPoseStack().popPose();
        }

        buffer.endBatch();
        RenderSystem.enableDepthTest();
    }

    private static int getHpColor(float ratio) {
        if (ratio > 0.6f) return 0xFF44DD44;
        if (ratio > 0.3f) return 0xFFDDDD44;
        return 0xFFDD4444;
    }

    private static String getSDVMobName(Mob mob) {
        Component customName = mob.getCustomName();
        if (customName != null) return customName.getString();
        return mob.getName().getString();
    }
}
