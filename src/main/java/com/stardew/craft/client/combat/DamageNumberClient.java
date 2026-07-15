package com.stardew.craft.client.combat;

import com.stardew.craft.combat.DimensionDamageMapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class DamageNumberClient {

    private static final List<DamageNumber> ACTIVE = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int debugTicker = 0;
    private static final boolean FORCE_DEBUG_SPAWN = false;

    private DamageNumberClient() {}

    public static void add(float x, float y, float z, int damage, boolean crit, String skillId) {
        Vec3 base = new Vec3(x, y, z);
        float driftX = (RANDOM.nextFloat() - 0.5f) * 0.18f;
        float driftZ = (RANDOM.nextFloat() - 0.5f) * 0.18f;
        ACTIVE.add(new DamageNumber(base, driftX, driftZ, damage, crit, skillId));
    }

    @SuppressWarnings("unused")
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (FORCE_DEBUG_SPAWN && mc.level != null && mc.player != null
                && DimensionDamageMapper.isInStardewDimension(mc.player)) {
            debugTicker++;
            if (debugTicker % 40 == 0) {
                @SuppressWarnings("null")
                Vec3 p = mc.player.position().add(0, mc.player.getBbHeight() * 0.75, 0);
                add((float) p.x, (float) p.y, (float) p.z, 123, false, null);
            }
        }

        if (ACTIVE.isEmpty()) return;
        Iterator<DamageNumber> it = ACTIVE.iterator();
        while (it.hasNext()) {
            DamageNumber dn = it.next();
            dn.age++;
            if (dn.age > dn.lifetime) {
                it.remove();
            }
        }
    }

    @SuppressWarnings("null")
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // 维度过滤在服务端已完成，客户端不再二次过滤，确保第三人称/队友都可见

        if (ACTIVE.isEmpty()) return;

        Font font = mc.font;
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        Vec3 camPos = event.getCamera().getPosition();

        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();

        for (DamageNumber dn : ACTIVE) {
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float age = dn.age + partial;
            float t = age / dn.lifetime;
            if (t < 0 || t > 1) continue;

            float scale = dn.crit ? 0.055f : 0.038f;
            scale *= dn.skillId != null ? 1.08f : 1.0f;

            float pop = easeOutBack(t);
            scale *= 1.0f + (0.22f + dn.popStrength * 0.10f) * pop;

            float alpha;
            if (t < 0.08f) {
                alpha = t / 0.08f;
            } else if (t > 0.7f) {
                alpha = (1.0f - t) / 0.3f;
            } else {
                alpha = 1.0f;
            }

            double rise = easeOutCubic(t) * (0.32 + dn.riseExtra);
            double drift = (1.0 - t) * (0.05 + dn.driftScale * 0.02);
            double popOut = pop * (0.10 + dn.popOutStrength * 0.06);
            Vec3 viewerOffset = dn.toViewer.scale(0.4 + pop * 0.8);

                @SuppressWarnings("null")
                Vec3 pos = dn.base
                    .add(dn.driftX * drift, rise + popOut, dn.driftZ * drift)
                    .add(viewerOffset);
            double x = pos.x - camPos.x;
            double y = pos.y - camPos.y;
            double z = pos.z - camPos.z;

            event.getPoseStack().pushPose();
            event.getPoseStack().translate(x, y, z);
            event.getPoseStack().mulPose(event.getCamera().rotation());
            event.getPoseStack().scale(scale, -scale, scale);

                String text = dn.getText();
                if (dn.isRainbow()) {
                    drawRainbowText(font, buffer, event.getPoseStack(), text, alpha);
                } else {
                    int color = dn.getColor(alpha);
                    @SuppressWarnings("null")
                    Component component = Component.literal(text)
                        .setStyle(Style.EMPTY.withBold(true));

                    @SuppressWarnings("null")
                    float xOff = -font.width(component) / 2.0f;
                    font.drawInBatch(
                        component.getVisualOrderText(),
                        xOff,
                        0,
                        color,
                            false,
                        event.getPoseStack().last().pose(),
                        buffer,
                        Font.DisplayMode.NORMAL,
                        0,
                        0xF000F0
                    );
                }

            event.getPoseStack().popPose();
        }

        buffer.endBatch();
        RenderSystem.enableDepthTest();
    }

    private static final class DamageNumber {
        private final Vec3 base;
        private final float driftX;
        private final float driftZ;
        private final int damage;
        private final boolean crit;
        private final String skillId;
        private final int lifetime;
        private int age = 0;
        private final float popStrength;
        private final float riseExtra;
        private final float driftScale;
        private final float popOutStrength;
        private final Vec3 toViewer;

        private DamageNumber(Vec3 base, float driftX, float driftZ, int damage, boolean crit, String skillId) {
            this.base = base;
            this.driftX = driftX;
            this.driftZ = driftZ;
            this.damage = Math.max(0, damage);
            this.crit = crit;
            this.skillId = (skillId != null && !"normal".equals(skillId)) ? skillId : null;
            this.lifetime = crit ? 26 : 22;
            this.popStrength = 0.4f + RANDOM.nextFloat() * 0.6f;
            this.riseExtra = 0.05f + RANDOM.nextFloat() * 0.12f;
            this.driftScale = 0.6f + RANDOM.nextFloat() * 0.8f;
            this.popOutStrength = 0.7f + RANDOM.nextFloat() * 0.6f;

            Minecraft mc = Minecraft.getInstance();
            Vec3 viewer = mc.player != null ? mc.player.position() : base;
            @SuppressWarnings("null")
            Vec3 dir = viewer.subtract(base).normalize();
            this.toViewer = dir.scale(0.10);
        }

        private String getText() {
            if (crit) {
                if (isIridiumNeedleSkill(skillId)) {
                    return getSkillEmoji(skillId) + damage + "!";
                }
                return "💥" + damage + "!";
            }
            if (skillId != null) {
                return getSkillEmoji(skillId) + damage;
            }
            return String.valueOf(damage);
        }

        private int getColor(float alpha) {
            int baseColor;
            if (crit) {
                if (isIridiumNeedleSkill(skillId)) {
                    baseColor = getSkillColor(skillId);
                } else {
                    baseColor = 0xFF3B30; // red
                }
            } else if (skillId != null) {
                baseColor = getSkillColor(skillId);
            } else {
                baseColor = 0xF4D03F; // yellow
            }
            int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
            return (a << 24) | (baseColor & 0x00FFFFFF);
        }

        private boolean isIridiumNeedleSkill(String id) {
            return "iridium_needle_thrust".equals(id) || "iridium_needle_frenzy".equals(id);
        }

        private String getSkillEmoji(String id) {
            if ("tetanus_strike".equals(id)) return "☣";
            if ("tree_blessing".equals(id)) return "🌿";
            if ("dash_slash".equals(id)) return "⚡";
            if ("ground_slam".equals(id)) return "💥";
            if ("shadow_strike".equals(id)) return "🗡";
            if ("spin_attack".equals(id)) return "🌀";
            if ("rapid_thrust".equals(id)) return "⚔";
            if ("desperate_plunder".equals(id)) return "☠";
            if ("silver_foldback".equals(id)) return "↺";
            if ("crescent_slash".equals(id)) return "☾";
            if ("forest_blessing".equals(id)) return "🍃";
            if ("elf_blade_leaf".equals(id)) return "🍃";
            if ("steel_spine_fury".equals(id)) return "⚙";
            if ("steel_spine_fury_weak".equals(id)) return "⚙";
            if ("bone_fracture".equals(id)) return "🦴";
            if ("carving_thrust".equals(id)) return "🗡";
            if ("iron_dirk_thrust".equals(id)) return "❖";
            if ("wind_spire_thrust".equals(id)) return "🌪";
            if ("burglar_shank".equals(id)) return "🪙";
            if ("crystal_dagger_layer".equals(id)) return "💎";
            if ("crystal_dagger_burst".equals(id)) return "✦";
            if ("shadow_dagger_execute".equals(id)) return "🗡";
            if ("shadow_dagger_execute_bonus".equals(id)) return "☠";
            if ("wicked_kris_venom_ripple".equals(id)) return "🐍";
            if ("wicked_kris_poison_dot".equals(id)) return "☣";
            if ("wicked_kris_nest_burst".equals(id)) return "💥";
            if ("wicked_kris_poison_burst".equals(id)) return "💥";
            if ("dwarf_dagger_thrust".equals(id)) return "🗡";
            if ("dwarf_dagger_rush".equals(id)) return "⛏";
            if ("iridium_needle_thrust".equals(id)) return "✦";
            if ("iridium_needle_frenzy".equals(id)) return "💜";
            if ("galaxy_dagger_starstab".equals(id)) return "✦";
            if ("galaxy_dagger_starleap".equals(id)) return "🌠";
            if ("galaxy_dagger_mark_bonus".equals(id)) return "✨";
            if ("infinity_dagger_singularity_stab".equals(id)) return "⚫";
            if ("infinity_dagger_singularity_backstab".equals(id)) return "🗡";
            if ("infinity_dagger_mark_bonus".equals(id)) return "✦";
            if ("claymore_foldback".equals(id)) return "⚔";
            if ("tide_mark".equals(id)) return "💧";
            if ("tide_anchor".equals(id)) return "⚓";
            if ("fishcatch_thrust".equals(id)) return "🐟";
            if ("tide_reel".equals(id)) return "🎣";
            if ("tide_mark_bonus".equals(id)) return "💠";
            if ("templar_vow".equals(id)) return "✝";
            if ("templar_judgement".equals(id)) return "⚖";
            if ("templar_judgement_share".equals(id)) return "✨";
            if ("meowmere_shot".equals(id)) return "🐱";
            if ("meowmere_symphony".equals(id)) return "🎵";
            if ("insect_eye_stance".equals(id)) return "🐛";
            if ("insect_dash".equals(id)) return "💨";
            if ("obsidian_resonance".equals(id)) return "🔮";
            if ("obsidian_crack".equals(id)) return "💎";
            if ("ossified_mark_bonus".equals(id)) return "🦴";
            if ("ossified_execution_dot".equals(id)) return "☠";
            if ("holy_smite".equals(id)) return "✦";
            if ("holy_domain".equals(id)) return "☀";
            if ("tempered_quench".equals(id)) return "🔥";
            if ("tempered_quench_blast".equals(id)) return "💥";
            if ("tempered_billet".equals(id)) return "🧱";
            if ("yeti_tooth_mark".equals(id)) return "❄";
            if ("yeti_tooth_spine".equals(id)) return "🧊";
            if ("steel_falchion_line".equals(id)) return "／";
            if ("steel_falchion_line_dot".equals(id)) return "／";
            if ("steel_falchion_trace".equals(id)) return "✧";
            if ("dark_sword_blood_debt".equals(id)) return "🩸";
            if ("dark_sword_blood_moon".equals(id)) return "🌑";
            if ("dark_sword_blood_moon_burst".equals(id)) return "💥";
            if ("lava_katana_brand".equals(id)) return "🔥";
            if ("lava_katana_burn".equals(id)) return "🔥";
            if ("lava_katana_reverb".equals(id)) return "🌋";
            if ("lava_katana_finisher".equals(id)) return "💥";
               if ("dragon_breath_thrust".equals(id)) return "🐉";
               if ("dragon_breath_judgement".equals(id)) return "🔥";
                if ("dragontooth_shiv_stab".equals(id)) return "🗡";
                if ("dragontooth_shiv_breath".equals(id)) return "🐉";
                if ("dwarf_rune_guard".equals(id)) return "⛓";
                if ("dwarf_fortress".equals(id)) return "🪨";
            if ("startrail_rift".equals(id)) return "✨";
            if ("galaxy_judgement".equals(id)) return "☄";
            if ("singularity_evolve".equals(id)) return "⚫";
            if ("singularity_followup".equals(id)) return "✦";
            if ("eternal_collapse".equals(id)) return "🕳";
            return "✨";
        }

        private int getSkillColor(String id) {
            if ("tetanus_strike".equals(id)) return 0x7CD992; // greenish
            if ("tree_blessing".equals(id)) return 0x6FCF8A; // fresh green
            if ("dash_slash".equals(id)) return 0xF5B041; // orange
            if ("ground_slam".equals(id)) return 0xD35400; // deep orange
            if ("shadow_strike".equals(id)) return 0x9B59B6; // purple
            if ("spin_attack".equals(id)) return 0x5DADE2; // cyan
            if ("rapid_thrust".equals(id)) return 0xF4D03F; // yellow
            if ("desperate_plunder".equals(id)) return 0xC0392B; // blood red - 海盗亡命掠夺的血红色
            if ("silver_foldback".equals(id)) return 0x6FA8FF; // blue - 折返状态
            if ("crescent_slash".equals(id)) return 0xF5D76E; // moonlight gold
            if ("forest_blessing".equals(id)) return 0x7BC96F; // forest green
            if ("elf_blade_leaf".equals(id)) return 0xA6E58F; // leaf green
            if ("steel_spine_fury".equals(id)) return 0x9AA7B2; // steel blue
            if ("steel_spine_fury_weak".equals(id)) return 0x6E7A83; // muted steel
            if ("bone_fracture".equals(id)) return 0xE3D3B0; // bone ivory
            if ("carving_thrust".equals(id)) return 0xD9C58B; // etched gold
            if ("iron_dirk_thrust".equals(id)) return 0xB7D3FF; // pale sky steel
            if ("wind_spire_thrust".equals(id)) return 0x8FD8FF; // wind blue
            if ("burglar_shank".equals(id)) return 0xE8C86A; // coin gold
            if ("crystal_dagger_layer".equals(id)) return 0x9EDBFF; // crystal blue
            if ("crystal_dagger_burst".equals(id)) return 0xC8F0FF; // crystal burst
            if ("shadow_dagger_execute".equals(id)) return 0x7C4B9A; // shadow violet
            if ("shadow_dagger_execute_bonus".equals(id)) return 0xB65DCC; // execute burst
            if ("wicked_kris_venom_ripple".equals(id)) return 0x63D26B; // venom green
            if ("wicked_kris_poison_dot".equals(id)) return 0x4FBF5E; // poison tick
            if ("wicked_kris_nest_burst".equals(id)) return 0x8AE868; // venom burst
            if ("wicked_kris_poison_burst".equals(id)) return 0x8AE868; // venom burst
            if ("dwarf_dagger_thrust".equals(id)) return 0xB58B5A; // dwarf bronze
            if ("dwarf_dagger_rush".equals(id)) return 0xD1A15E; // warm bronze
            if ("iridium_needle_thrust".equals(id)) return 0xB05CFF; // iridium violet
            if ("iridium_needle_frenzy".equals(id)) return 0xD0A2FF; // iridium glow
            if ("galaxy_dagger_starstab".equals(id)) return 0x7B5CFF; // galaxy purple
            if ("galaxy_dagger_starleap".equals(id)) return 0xB89CFF; // galaxy flare
            if ("galaxy_dagger_mark_bonus".equals(id)) return 0x9A7BFF; // star mark
            if ("infinity_dagger_singularity_stab".equals(id)) return 0xF0C35A; // infinity gold
            if ("infinity_dagger_singularity_backstab".equals(id)) return 0xC9A34A; // deep gold
            if ("infinity_dagger_mark_bonus".equals(id)) return 0xF0C35A; // mark bonus
            if ("claymore_foldback".equals(id)) return 0x8FA3B8; // heavy steel
            if ("tide_mark".equals(id)) return 0x56B4E9; // tide blue
            if ("tide_anchor".equals(id)) return 0x3D7DD8; // deep ocean
            if ("fishcatch_thrust".equals(id)) return 0x6CC9FF; // fishcatch blue
            if ("tide_reel".equals(id)) return 0x3FA9F5; // reel blue
            if ("tide_mark_bonus".equals(id)) return 0x6CC9FF; // bonus splash
            if ("templar_vow".equals(id)) return 0xF2D56B; // templar gold
            if ("templar_judgement".equals(id)) return 0xF7E7A5; // holy gold
            if ("templar_judgement_share".equals(id)) return 0xEAD47B; // judgement share
            if ("insect_eye_stance".equals(id)) return 0x7EDC7A; // insect green
            if ("insect_dash".equals(id)) return 0xA6F0A1; // bright green
            if ("obsidian_resonance".equals(id)) return 0x5B3B8C; // obsidian purple
            if ("obsidian_crack".equals(id)) return 0x8C5BFF; // crack violet
            if ("ossified_mark_bonus".equals(id)) return 0xE6D2B5; // bone ivory
            if ("ossified_execution_dot".equals(id)) return 0xB88FD9; // execution violet
            if ("holy_smite".equals(id)) return 0xF7E6A1; // holy gold
            if ("holy_domain".equals(id)) return 0xFFE8B8; // dawn light
            if ("tempered_quench".equals(id)) return 0xF59E2F; // hot orange
            if ("tempered_quench_blast".equals(id)) return 0xF2782B; // blast orange
            if ("tempered_billet".equals(id)) return 0xE07A2D; // forged orange
            if ("yeti_tooth_mark".equals(id)) return 0x7FB3FF; // frost blue
            if ("yeti_tooth_spine".equals(id)) return 0xA7D8FF; // ice blue
            if ("steel_falchion_line".equals(id)) return 0xB0C4DE; // steel blue
            if ("steel_falchion_line_dot".equals(id)) return 0xA0B8D8; // line dot
            if ("steel_falchion_trace".equals(id)) return 0x3A3A3A; // black steel
            if ("dark_sword_blood_debt".equals(id)) return 0x8F1D2C; // dark blood
            if ("dark_sword_blood_moon".equals(id)) return 0x4B0F1A; // blood moon
            if ("dark_sword_blood_moon_burst".equals(id)) return 0xB23A48; // burst crimson
            if ("lava_katana_brand".equals(id)) return 0xFF8A2A; // molten orange
            if ("lava_katana_burn".equals(id)) return 0xFFB14A; // burn amber
               if ("dragon_breath_thrust".equals(id)) return 0x8A4DFF; // dragon purple
            if ("dragon_breath_judgement".equals(id)) return 0x8A4DFF; // dragon purple
                if ("dragontooth_shiv_stab".equals(id)) return 0xFF7A2E; // dragon ember
                if ("dragontooth_shiv_breath".equals(id)) return 0xFF5A1E; // deep ember
            if ("lava_katana_reverb".equals(id)) return 0xFF7A1E; // lava orange
            if ("lava_katana_finisher".equals(id)) return 0xFF4B1E; // finisher ember
                if ("dwarf_rune_guard".equals(id)) return 0xB58B5A; // dwarf bronze
                if ("dwarf_fortress".equals(id)) return 0x9A6A3A; // dwarf earth
            if ("startrail_rift".equals(id)) return 0x7B5CFF; // galaxy purple
            if ("galaxy_judgement".equals(id)) return 0xB89CFF; // galaxy flare
            if ("singularity_evolve".equals(id)) return 0xF0C35A; // infinity gold
            if ("singularity_followup".equals(id)) return 0xF0C35A; // infinity gold
            if ("eternal_collapse".equals(id)) return 0xC9A34A; // deep gold
            return 0x7FB3FF; // default blue
        }

        private boolean isRainbow() {
            return "meowmere_shot".equals(skillId) || "meowmere_symphony".equals(skillId);
        }
    }

    private static void drawRainbowText(Font font, MultiBufferSource.BufferSource buffer, com.mojang.blaze3d.vertex.PoseStack poseStack,
                                        String text, float alpha) {
        @SuppressWarnings("null")
        float totalWidth = font.width(text);
        if (totalWidth <= 0.0f) return;

        FontSet fontSet = FontAccess.getDefaultFontSet(font);
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        float textLeft = -totalWidth / 2.0f;
        float x = textLeft;
        boolean bold = true;
        int packedLight = 0xF000F0;

        int[] codepoints = text.codePoints().toArray();
        for (int cp : codepoints) {
            String s = new String(Character.toChars(cp));
            float advance = font.width(s);
            if (fontSet == null) {
                drawFallbackRainbowChar(font, buffer, matrix, s, x, advance, alpha, packedLight, textLeft, totalWidth);
                x += advance;
                continue;
            }

            BakedGlyph glyph = fontSet.getGlyph(cp);
            if (glyph == null) {
                x += advance;
                continue;
            }

            RenderType renderType = glyph.renderType(Font.DisplayMode.NORMAL);
            if (renderType == null) {
                drawFallbackRainbowChar(font, buffer, matrix, s, x, advance, alpha, packedLight, textLeft, totalWidth);
                x += advance;
                continue;
            }
            VertexConsumer consumer = buffer.getBuffer(renderType);

            if (!renderGlyphGradient(glyph, consumer, pose, x, 0.0f, alpha, textLeft, totalWidth, packedLight)) {
                drawFallbackRainbowChar(font, buffer, matrix, s, x, advance, alpha, packedLight, textLeft, totalWidth);
            } else if (bold) {
                renderGlyphGradient(glyph, consumer, pose, x + 0.5f, 0.0f, alpha, textLeft, totalWidth, packedLight);
            }

            x += advance;
        }
    }

    @SuppressWarnings("null")
    private static boolean renderGlyphGradient(BakedGlyph glyph, VertexConsumer consumer, PoseStack.Pose pose,
                                               float x, float y, float alpha, float textLeft, float totalWidth, int packedLight) {
        if (!GlyphFields.ready()) return false;
        float x0 = x + GlyphFields.left(glyph);
        float x1 = x + GlyphFields.right(glyph);
        float y0 = y + GlyphFields.up(glyph);
        float y1 = y + GlyphFields.down(glyph);
        float u0 = GlyphFields.u0(glyph);
        float u1 = GlyphFields.u1(glyph);
        float v0 = GlyphFields.v0(glyph);
        float v1 = GlyphFields.v1(glyph);

        float hueL = clamp01((x0 - textLeft) / totalWidth);
        float hueR = clamp01((x1 - textLeft) / totalWidth);
        int colorL = rgbaFromHue(hueL, alpha);
        int colorR = rgbaFromHue(hueR, alpha);

        float rL = ((colorL >> 16) & 0xFF) / 255.0f;
        float gL = ((colorL >> 8) & 0xFF) / 255.0f;
        float bL = (colorL & 0xFF) / 255.0f;
        float a = ((colorL >> 24) & 0xFF) / 255.0f;

        float rR = ((colorR >> 16) & 0xFF) / 255.0f;
        float gR = ((colorR >> 8) & 0xFF) / 255.0f;
        float bR = (colorR & 0xFF) / 255.0f;

        int rL8 = (int) (rL * 255.0f);
        int gL8 = (int) (gL * 255.0f);
        int bL8 = (int) (bL * 255.0f);
        int a8 = (int) (a * 255.0f);

        int rR8 = (int) (rR * 255.0f);
        int gR8 = (int) (gR * 255.0f);
        int bR8 = (int) (bR * 255.0f);

        consumer.addVertex(pose, x0, y0, 0.0f)
            .setColor(rL8, gL8, bL8, a8)
            .setUv(u0, v0)
            .setLight(packedLight);
        consumer.addVertex(pose, x0, y1, 0.0f)
            .setColor(rL8, gL8, bL8, a8)
            .setUv(u0, v1)
            .setLight(packedLight);
        consumer.addVertex(pose, x1, y1, 0.0f)
            .setColor(rR8, gR8, bR8, a8)
            .setUv(u1, v1)
            .setLight(packedLight);
        consumer.addVertex(pose, x1, y0, 0.0f)
            .setColor(rR8, gR8, bR8, a8)
            .setUv(u1, v0)
            .setLight(packedLight);
        return true;
    }

    private static int rgbaFromHue(float hue, float alpha) {
        float h = clamp01(hue);
        int rgb = java.awt.Color.HSBtoRGB(h, 1.0f, 1.0f);
        int a = Math.min(255, Math.max(0, (int) (alpha * 255)));
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    @SuppressWarnings("null")
    private static void drawFallbackRainbowChar(Font font, MultiBufferSource.BufferSource buffer, Matrix4f matrix,
                                                String s, float x, float advance, float alpha, int packedLight,
                                                float textLeft, float totalWidth) {
        float center = x + (advance * 0.5f);
        float hue = totalWidth <= 0.0f ? 0.0f : clamp01((center - textLeft) / totalWidth);
        int color = rgbaFromHue(hue, alpha);
        font.drawInBatch(
            Component.literal(s).setStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
            x,
            0,
            color,
            false,
            matrix,
            buffer,
            Font.DisplayMode.NORMAL,
            0,
            packedLight
        );
    }


    private static final class FontAccess {
        private static Method GET_FONT_SET;
        private static boolean INIT_TRIED;

        private static FontSet getDefaultFontSet(Font font) {
            if (!INIT_TRIED) {
                INIT_TRIED = true;
                try {
                    GET_FONT_SET = Font.class.getDeclaredMethod("getFontSet", net.minecraft.resources.ResourceLocation.class);
                    GET_FONT_SET.setAccessible(true);
                } catch (Throwable t) {
                    GET_FONT_SET = null;
                }
            }
            if (GET_FONT_SET == null) return null;
            try {
                return (FontSet) GET_FONT_SET.invoke(font, Style.EMPTY.getFont());
            } catch (Throwable t) {
                return null;
            }
        }
    }

    private static final class GlyphFields {
        private static Field LEFT;
        private static Field RIGHT;
        private static Field UP;
        private static Field DOWN;
        private static Field U0;
        private static Field U1;
        private static Field V0;
        private static Field V1;
        private static boolean ready;
        private static boolean initTried;

        private static boolean ready() {
            if (initTried) return ready;
            initTried = true;
            try {
                LEFT = BakedGlyph.class.getDeclaredField("left");
                RIGHT = BakedGlyph.class.getDeclaredField("right");
                UP = BakedGlyph.class.getDeclaredField("up");
                DOWN = BakedGlyph.class.getDeclaredField("down");
                U0 = BakedGlyph.class.getDeclaredField("u0");
                U1 = BakedGlyph.class.getDeclaredField("u1");
                V0 = BakedGlyph.class.getDeclaredField("v0");
                V1 = BakedGlyph.class.getDeclaredField("v1");
                LEFT.setAccessible(true);
                RIGHT.setAccessible(true);
                UP.setAccessible(true);
                DOWN.setAccessible(true);
                U0.setAccessible(true);
                U1.setAccessible(true);
                V0.setAccessible(true);
                V1.setAccessible(true);
                ready = true;
            } catch (Throwable t) {
                ready = false;
            }
            return ready;
        }

        private static float left(BakedGlyph g) { return getFloat(LEFT, g); }
        private static float right(BakedGlyph g) { return getFloat(RIGHT, g); }
        private static float up(BakedGlyph g) { return getFloat(UP, g); }
        private static float down(BakedGlyph g) { return getFloat(DOWN, g); }
        private static float u0(BakedGlyph g) { return getFloat(U0, g); }
        private static float u1(BakedGlyph g) { return getFloat(U1, g); }
        private static float v0(BakedGlyph g) { return getFloat(V0, g); }
        private static float v1(BakedGlyph g) { return getFloat(V1, g); }

        private static float getFloat(Field f, Object o) {
            try {
                return f != null ? f.getFloat(o) : 0.0f;
            } catch (Throwable t) {
                return 0.0f;
            }
        }
    }

    private static float easeOutCubic(float t) {
        float p = 1.0f - t;
        return 1.0f - p * p * p;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float p = t - 1.0f;
        return 1.0f + c3 * p * p * p + c1 * p * p;
    }
}
