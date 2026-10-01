package com.stardew.craft.client.gui.common;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.stardew.craft.block.utility.WoodenChestColorPalette;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.LongSupplier;

import com.stardew.craft.port.PortVertex;
/** A popover owned by the existing container screen, with no screen or menu transition. */
@OnlyIn(Dist.CLIENT)
public final class ChestColorWheel {
    public record Option(int id, int rgb, Component label) {}
    @FunctionalInterface public interface Preview { void draw(GuiGraphics graphics, int x, int y, int color); }
    private static final String[] NAMES = {"black", "indigo", "sky", "teal", "mint", "moss", "lime", "gold",
            "amber", "orange", "red", "wine", "pink", "rose", "orchid", "violet", "purple", "charcoal", "gray", "silver", "white"};
    private final List<Option> options;
    private final IntConsumer commit;
    private final Preview preview;
    private final LongSupplier clock;
    private final Set<Integer> ownedKeys = new HashSet<>();
    private final Set<Integer> ownedButtons = new HashSet<>();
    private List<List<ColorWheelArt.Span>> petals = List.of();
    private final Map<Integer, List<List<ColorWheelArt.Span>>> pageArt = new HashMap<>();
    private float[] hover = new float[0];
    private ColorWheelLayout layout = new ColorWheelLayout(0, 0);
    private int page, candidate = -1, selected = -1, hovered = ColorWheelLayout.NONE;
    private int anchorX, anchorY;
    private boolean committed;
    private boolean visible, closing, keyboard;
    private long openedAt, closedAt, frameAt, hoverSoundAt;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;

    public ChestColorWheel(List<Option> options, IntConsumer commit, Preview preview) {
        this(options, commit, preview, Util::getMillis);
    }

    public ChestColorWheel(List<Option> options, IntConsumer commit, Preview preview, LongSupplier clock) {
        this.options = List.copyOf(options);
        if (options.stream().map(Option::id).distinct().count() != options.size()
                || options.stream().anyMatch(o -> o.id() < 0)) throw new IllegalArgumentException("Color IDs must be unique and nonnegative");
        this.commit = commit;
        this.preview = preview;
        this.clock = clock;
    }

    public static List<Option> chestOptions() {
        List<Option> options = new ArrayList<>();
        for (int i = 1; i < Math.min(17, WoodenChestColorPalette.size()); i++) options.add(option(i));
        if (WoodenChestColorPalette.size() > 0) options.add(option(0));
        for (int i = 17; i < WoodenChestColorPalette.size(); i++) options.add(option(i));
        return List.copyOf(options);
    }

    private static Option option(int id) {
        return new Option(id, WoodenChestColorPalette.rgbAt(id), id < NAMES.length
                ? Component.translatable("stardewcraft.color_wheel." + NAMES[id])
                : Component.translatable("stardewcraft.wooden_chest.color_tooltip", id + 1));
    }

    public void layout(int width, int height, int left, int right, int entryY) {
        layout = ColorWheelLayout.beside(width, height, left, right, entryY);
        anchorX = right + 15; anchorY = entryY + 9;
    }

    public void center(int width, int height) {
        layout = new ColorWheelLayout(width / 2, height / 2);
        anchorX = layout.x(); anchorY = layout.y();
    }

    public void open(int color) {
        selected = candidate = color;
        page = Math.max(0, indexOf(color)) / ColorWheelLayout.PAGE_SIZE;
        buildPage();
        openedAt = frameAt = clock.getAsLong();
        lastMouseX = lastMouseY = Double.NaN;
        visible = true; closing = keyboard = committed = false; hovered = ColorWheelLayout.NONE;
        ownedButtons.add(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        sound(ModSounds.SHWIP.get(), .32F);
    }

    public boolean active() {
        if (closing && clock.getAsLong() - closedAt >= duration(100)) visible = false;
        return visible;
    }

    public void cancel() {
        if (!active() || closing) return;
        candidate = selected;
        close(); sound(ModSounds.CANCEL.get(), .22F);
    }

    private void close() { closing = true; closedAt = clock.getAsLong(); }
    public Rect2i bounds() { return new Rect2i(layout.x() - 78, layout.y() - 78, 156, 156); }

    public boolean click(double x, double y, int button, boolean entry) {
        if (!active()) return false;
        ownedButtons.add(button);
        if (closing) return true;
        if (entry || button == 1 || !layout.contains(x, y)) { cancel(); return true; }
        if (button != 0) return true;
        if (pages() > 1 && Math.abs(y - layout.y() - 39) < 6) {
            if (x < layout.x() - 15) changePage(-1);
            else if (x > layout.x() + 15) changePage(1);
            return true;
        }
        int hit = layout.hit(x, y, pageCount());
        if (hit == ColorWheelLayout.ORIGINAL) choose(-1);
        else if (hit >= 0) choose(options.get(page * ColorWheelLayout.PAGE_SIZE + hit).id());
        return true;
    }

    private void choose(int id) {
        candidate = id;
        committed = true;
        if (id != selected) commit.accept(id);
        close(); sound(ModSounds.SELECT.get(), .38F);
    }

    public boolean release(int button) { boolean owned = ownedButtons.remove(button); return active() || owned; }
    public void claimButton(int button) { ownedButtons.add(button); }
    public boolean dragging(int button) { return active() || ownedButtons.contains(button); }
    public boolean keyReleased(int key) { boolean owned = ownedKeys.remove(key); return active() || owned; }

    public boolean key(int key) {
        if (!active()) return ownedKeys.contains(key);
        ownedKeys.add(key);
        if (closing) return true;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> cancel();
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> step(-1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_TAB -> step(1);
            case GLFW.GLFW_KEY_HOME -> { keyboard = true; candidate = -1; announce(); }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(candidate);
            case GLFW.GLFW_KEY_PAGE_UP -> changePage(-1);
            case GLFW.GLFW_KEY_PAGE_DOWN -> changePage(1);
            default -> { /* Inventory hotkeys belong to the popover until it closes. */ }
        }
        return true;
    }

    public boolean scroll(double amount) {
        if (!active()) return false;
        if (!closing && amount != 0) step(amount > 0 ? -1 : 1);
        return true;
    }

    private void step(int delta) {
        int index = indexOf(candidate) + 1;
        index = Math.floorMod(index + delta, options.size() + 1);
        candidate = index == 0 ? -1 : options.get(index - 1).id();
        int nextPage = index == 0 ? page : (index - 1) / ColorWheelLayout.PAGE_SIZE;
        if (page != nextPage) { page = nextPage; buildPage(); }
        keyboard = true; tickSound(); announce();
    }

    private void changePage(int delta) {
        page = Math.floorMod(page + delta, pages()); buildPage();
        if (pageCount() > 0) candidate = options.get(page * ColorWheelLayout.PAGE_SIZE).id();
        keyboard = true; tickSound(); announce();
    }

    private int indexOf(int id) { for (int i = 0; i < options.size(); i++) if (options.get(i).id() == id) return i; return -1; }
    private int pages() { return Math.max(1, (options.size() + 20) / 21); }
    private int pageCount() { return Math.min(21, options.size() - page * 21); }
    private void buildPage() {
        petals = pageArt.computeIfAbsent(page, ignored -> {
            List<List<ColorWheelArt.Span>> art = new ArrayList<>();
            for (int i = 0; i < pageCount(); i++) art.add(ColorWheelArt.petal(i, pageCount(), options.get(page * 21 + i).rgb()));
            return List.copyOf(art);
        });
        hover = new float[petals.size()];
    }

    private Component label(int id) {
        int index = indexOf(id);
        return index < 0 ? Component.translatable("stardewcraft.color_wheel.original") : options.get(index).label();
    }
    private void announce() {
        if (Minecraft.getInstance() != null) Minecraft.getInstance().getNarrator().sayNow(label(candidate));
    }
    private void tickSound() {
        long now = clock.getAsLong();
        if (now - hoverSoundAt >= 70) { hoverSoundAt = now; sound(ModSounds.SMALL_SELECT.get(), .15F); }
    }
    private static void sound(SoundEvent event, float volume) {
        if (Minecraft.getInstance() != null) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, volume));
    }
    private static int duration(int normal) {
        var mc = Minecraft.getInstance();
        return mc != null && mc.options.screenEffectScale().get() == 0 ? 1 : normal;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!active()) return;
        long now = clock.getAsLong();
        float dt = Math.min(100, now - frameAt) / 80F; frameAt = now;
        float fade = closing ? 1 - ColorWheelLayout.ease((now - closedAt) / (float) duration(100))
                : ColorWheelLayout.ease((now - openedAt) / (float) duration(140));
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            if (keyboard) hovered = ColorWheelLayout.NONE;
            keyboard = false; lastMouseX = mouseX; lastMouseY = mouseY;
        }
        int hit = closing ? ColorWheelLayout.NONE : layout.hit(mouseX, mouseY, pageCount());
        if (!keyboard && hit != hovered) {
            hovered = hit;
            if (hit >= 0) { candidate = options.get(page * 21 + hit).id(); tickSound(); }
            else if (hit == ColorWheelLayout.ORIGINAL) { candidate = -1; tickSound(); }
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        // Opaque local backing keeps inventory contents from competing with the small preview.
        for (int y = -43; y <= 43; y++) {
            int half = (int)Math.sqrt(43 * 43 - y * y);
            graphics.fill(layout.x() - half, layout.y() + y, layout.x() + half + 1, layout.y() + y + 1, alpha(0xFF51352C, fade));
            if (Math.abs(y) <= 40) {
                int inside = (int)Math.sqrt(40 * 40 - y * y);
                graphics.fill(layout.x() - inside, layout.y() + y, layout.x() + inside + 1, layout.y() + y + 1,
                        alpha(y < -36 ? 0xFFFFD892 : 0xFFEBC68D, fade));
            }
        }
        graphics.flush();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        // PORT(1.20.1): Tesselator#begin returns void; an empty 1.20.1 buffer is skipped by BufferUploader like 1.21's null mesh.
        BufferBuilder buffer = Tesselator.getInstance().getBuilder(); buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        for (int i = 0; i < petals.size(); i++) {
            boolean focus = !closing && (keyboard ? options.get(page * 21 + i).id() == candidate : hovered == i);
            hover[i] += (focus ? 1 - hover[i] : -hover[i]) * Math.min(1, dt);
            double angle = ColorWheelLayout.angle(i, petals.size());
            float shift = -6 * (1 - fade) + 2 * hover[i];
            float x = layout.x() + Math.round(Math.cos(angle) * shift), y = layout.y() + Math.round(Math.sin(angle) * shift);
            for (ColorWheelArt.Span span : petals.get(i)) quad(buffer, matrix, x + span.x(), y + span.y(), span.width(), alpha(span.color(), fade));
        }
        BufferUploader.drawWithShader(buffer.end());
        for (int i = 0; i < petals.size(); i++) {
            if (!closing && (keyboard ? options.get(page * 21 + i).id() == candidate : hovered == i)) {
                double focusAngle = ColorWheelLayout.angle(i, petals.size());
                int fx = layout.x() + (int)Math.round(Math.cos(focusAngle) * 75), fy = layout.y() + (int)Math.round(Math.sin(focusAngle) * 75);
                graphics.fill(fx - 2, fy - 2, fx + 3, fy + 3, alpha(0xFF493128, fade));
                graphics.fill(fx - 1, fy - 1, fx + 2, fy + 2, alpha(0xFFFFE09A, fade));
            }
            if (options.get(page * 21 + i).id() != selected) continue;
            double angle = ColorWheelLayout.angle(i, petals.size());
            int x = layout.x() + (int)Math.round(Math.cos(angle) * 59), y = layout.y() + (int)Math.round(Math.sin(angle) * 59);
            graphics.fill(x - 3, y - 3, x + 4, y + 4, alpha(0xFF493128, fade));
            graphics.fill(x - 1, y - 1, x + 2, y + 2, alpha(0xFFFFF2BE, fade));
        }
        // Keep 3D lighting/state outside the wheel batch. Preview owns a detached object.
        if (fade > .35F) preview.draw(graphics, layout.x(), layout.y() - 9, candidate);
        boolean resetHover = !closing && (keyboard ? candidate == -1 : hit == ColorWheelLayout.ORIGINAL);
        int bx = layout.x() - 25, by = layout.y() + 19;
        graphics.fill(bx, by, bx + 50, by + 16, alpha(0xFF644034, fade));
        graphics.fill(bx + 1, by + 1, bx + 49, by + 14, alpha(resetHover ? 0xFFFFDEA0 : 0xFFDCA767, fade));
        if (selected == -1) {
            graphics.fill(bx - 6, by + 5, bx - 1, by + 10, alpha(0xFF493128, fade));
            graphics.fill(bx - 5, by + 6, bx - 2, by + 9, alpha(0xFFFFF2BE, fade));
        }
        var font = StardewFonts.small();
        GuiText.drawCenteredClamped(graphics, font, Component.translatable("stardewcraft.color_wheel.original"),
                layout.x(), by + 3, 44, alpha(0xFF4A2D25, fade), false);
        if (pages() > 1) graphics.drawCenteredString(font, Component.literal("‹  " + (page + 1) + "/" + pages() + "  ›"), layout.x(), layout.y() + 37, 0xFF51352C);
        if (closing && committed) {
            int index = indexOf(candidate), local = index - page * 21;
            double angle = ColorWheelLayout.angle(Math.max(0, local), Math.max(1, pageCount()));
            double sx = index < 0 ? layout.x() : layout.x() + Math.cos(angle) * 59;
            double sy = index < 0 ? layout.y() + 26 : layout.y() + Math.sin(angle) * 59;
            float t = ColorWheelLayout.ease((now - closedAt) / (float)duration(100));
            int x = (int)Math.round(sx + (anchorX - sx) * t);
            int y = (int)Math.round(sy + (anchorY - sy) * t - 10 * Math.sin(Math.PI * t));
            int color = index < 0 ? 0xFFBC8146 : options.get(index).rgb() | 0xFF000000;
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, 120);
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF493128);
            graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
        if (!closing && (hit >= 0 || keyboard)) {
            graphics.renderTooltip(StardewFonts.tooltip(), label(candidate), mouseX, mouseY);
        } else if (!closing && hit == ColorWheelLayout.ORIGINAL) {
            graphics.renderTooltip(StardewFonts.tooltip(), Component.translatable("stardewcraft.color_wheel.reset_hint"), mouseX, mouseY);
        }
    }

    private static int alpha(int rgb, float amount) { return ((int)(255 * amount) << 24) | (rgb & 0xFFFFFF); }
    private static void quad(BufferBuilder b, Matrix4f m, float x, float y, int width, int color) {
        PortVertex.of(b).addVertex(m, x, y, 0).setColor(color).endVertex(); PortVertex.of(b).addVertex(m, x, y + 1, 0).setColor(color).endVertex();
        PortVertex.of(b).addVertex(m, x + width, y + 1, 0).setColor(color).endVertex(); PortVertex.of(b).addVertex(m, x + width, y, 0).setColor(color).endVertex();
    }
}
