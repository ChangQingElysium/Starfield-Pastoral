package com.stardew.craft.client.gui.specialorder;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.network.payload.AcceptSpecialOrderPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("null")
public class SpecialOrdersBoardScreen extends Screen {
    private static final ResourceLocation BOARD =
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/special_orders/board_normal.png");
    private static final ResourceLocation DUE =
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/special_orders/due_date_icon.png");
    private static final ResourceLocation CHECK =
        ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/special_orders/completed_check.png");
    private static final Pattern TOKEN = Pattern.compile("\\{([A-Za-z0-9_]+)(?::([A-Za-z0-9_]+))?}");

    private CompoundTag data;
    private StardewRenderMapping mapping;
    private float s4;
    private int winX, winY, winW, winH;
    private int closeX, closeY, closeSize;
    private final ButtonBounds[] acceptButtons = new ButtonBounds[2];
    private String hoveredButton = "";

    public SpecialOrdersBoardScreen(CompoundTag data) {
        super(Component.translatable("gui.stardewcraft.special_orders"));
        this.data = data == null ? new CompoundTag() : data.copy();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        super.init();
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        mapping = new StardewRenderMapping(width, height, guiScale);
        s4 = mapping.s4();
        winW = Math.round(338 * s4);
        winH = Math.round(198 * s4);
        float fit = Math.min(1.0F, Math.min(width / (float) winW, height / (float) winH));
        if (fit < 1.0F) {
            s4 *= fit;
            winW = Math.round(338 * s4);
            winH = Math.round(198 * s4);
        }
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        closeSize = u(48);
        closeX = winX + winW - u(20);
        closeY = winY;
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xBF000000);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        data = ClientSpecialOrderBoardData.snapshot().isEmpty() ? data : ClientSpecialOrderBoardData.snapshot();
        acceptButtons[0] = null;
        acceptButtons[1] = null;
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.blit(BOARD, winX, winY, winW, winH, 0, 0, 338, 198, 338, 198);
        CommonGuiTextures.drawCloseButton(graphics, closeX, closeY, s4);

        ListTag active = data.getList("Active", 10);
        ListTag available = data.getList("Available", 10);
        if (available.isEmpty()) {
            if (!active.isEmpty()) {
                drawOrder(graphics, active.getCompound(0), 0, mouseX, mouseY, false, true);
                return;
            }
            Component noneAvailable = Component.translatable("stardewcraft.special_orders.none_available");
            int centerX = winX + winW / 2;
            graphics.drawString(font, noneAvailable, centerX - font.width(noneAvailable) / 2,
                winY + u(96), 0xFF422A15, false);
            return;
        }

        boolean hasAccepted = data.getBoolean("AcceptedThisRefresh") || !active.isEmpty();
        if (!hasAccepted) {
            drawChooseOne(graphics);
        }

        String activeId = !active.isEmpty() ? active.getCompound(0).getString("Id") : "";
        for (int i = 0; i < Math.min(2, available.size()); i++) {
            CompoundTag order = available.getCompound(i);
            boolean selected = hasAccepted && order.getString("Id").equals(activeId);
            drawOrder(graphics, order, i, mouseX, mouseY, !hasAccepted, !hasAccepted || selected);
        }
    }

    private void drawOrder(GuiGraphics graphics, CompoundTag order, int side, int mouseX, int mouseY, boolean showAcceptButton, boolean highlighted) {
        int cardX = winX + (side == 0 ? u(96) : u(736));
        int headerY = winY + u(128);
        int centerX = cardX + u(256);
        int textColor = highlighted ? 0xFF5B5045 : 0x405B5045;
        int bodyColor = highlighted ? 0xFF5B5045 : 0x405B5045;
        float alpha = highlighted ? 1.0F : 0.25F;

        ResourceLocation requester = requesterTexture(order.getString("Requester"));
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(requester, cardX, headerY, Math.round(9 * s4), Math.round(9 * s4), 0, 0, 9, 9, 9, 9);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        Component title = Component.literal(resolve(I18n.get(order.getString("TitleKey")), order)).withStyle(net.minecraft.ChatFormatting.BOLD);
        drawCenteredText(graphics, title, centerX, headerY, textColor);

        int descX = cardX;
        int descY = winY + u(192);
        int descW = u(512);
        Component desc = Component.literal(resolve(I18n.get(order.getString("TextKey")), order));
        drawDescription(graphics, desc, descX, descY, descW, bodyColor, highlighted);

        if (!highlighted) {
            return;
        }

        int dueY = winY + u(576);
        graphics.blit(DUE, descX, dueY, Math.round(9 * s4), Math.round(9 * s4), 0, 0, 9, 9, 9, 9);
        Component days = Component.translatable(order.getInt("DaysLeft") == 1
            ? "stardewcraft.special_orders.day_left.one"
            : "stardewcraft.special_orders.day_left.many", order.getInt("DaysLeft"));
        drawText(graphics, days, descX + u(48), dueY, textColor);
        if (order.getBoolean("Complete")) {
            graphics.blit(CHECK, descX, winY + u(616), Math.round(11 * s4), Math.round(11 * s4), 0, 0, 11, 11, 11, 11);
        }

        if (showAcceptButton) {
            int buttonW = font.width(I18n.get("stardewcraft.special_orders.accept")) + u(24);
            int buttonH = font.lineHeight + u(24);
            int buttonX = winX + (side == 0 ? winW / 4 : winW * 3 / 4) - u(128);
            int buttonY = winY + winH - u(128);
            acceptButtons[side] = new ButtonBounds(buttonX, buttonY, buttonW, buttonH, order.getString("Id"));
            boolean hover = contains(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH);
            if (hover) graphics.setColor(1.0F, 0.7F, 0.75F, 1.0F);
            CommonGuiTextures.drawBillboardAcceptBox(graphics, buttonX, buttonY, buttonW, buttonH, hover ? s4 * 1.5F : s4);
            if (hover) graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            Component label = Component.translatable("stardewcraft.special_orders.accept");
            drawText(graphics, label, buttonX + u(12), buttonY + u(16), textColor);
        }
    }

    private ResourceLocation requesterTexture(String requester) {
        String id = requester == null || requester.isBlank() ? "unknown" : requester.toLowerCase(java.util.Locale.ROOT);
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/special_orders/requester_" + id + ".png");
    }

    private String resolve(String text, CompoundTag order) {
        String out = text == null ? "" : text;
        for (int i = 0; i < 4; i++) {
            String resolved = resolveOnce(out, order);
            if (resolved.equals(out)) {
                return resolved;
            }
            out = resolved;
        }
        return out;
    }

    private String resolveOnce(String text, CompoundTag order) {
        Map<String, Map<String, String>> randomValues = new LinkedHashMap<>();
        CompoundTag randomTag = order.getCompound("RandomValues");
        for (String element : randomTag.getAllKeys()) {
            CompoundTag values = randomTag.getCompound(element);
            Map<String, String> map = new LinkedHashMap<>();
            for (String key : values.getAllKeys()) {
                String value = values.getString(key);
                map.put(key, value.startsWith("stardewcraft.") || value.startsWith("item.") ? I18n.get(value) : value);
            }
            randomValues.put(element, map);
        }
        Matcher matcher = TOKEN.matcher(text);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            Map<String, String> values = randomValues.get(matcher.group(1));
            String field = matcher.group(2);
            if (field == null || field.isBlank()) {
                field = "Text";
            }
            String replacement = values == null ? "" : values.getOrDefault(field, values.getOrDefault(matcher.group(1), ""));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            play(ModSounds.BIG_DESELECT.get());
            onClose();
            return true;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        if (contains(mx, my, closeX, closeY, closeSize, closeSize)) {
            play(ModSounds.BIG_DESELECT.get());
            onClose();
            return true;
        }
        if (!contains(mx, my, winX, winY, winW, winH)) {
            play(ModSounds.BIG_DESELECT.get());
            onClose();
            return true;
        }
        for (ButtonBounds bounds : acceptButtons) {
            if (bounds == null || !contains(mx, my, bounds.x, bounds.y, bounds.w, bounds.h)) continue;
            play(ModSounds.NEW_ARTIFACT.get());
            PacketDistributor.sendToServer(new AcceptSpecialOrderPayload(bounds.orderId));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            play(ModSounds.BIG_DESELECT.get());
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean contains(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        String hover = "";
        int mx = (int) mouseX;
        int my = (int) mouseY;
        for (ButtonBounds bounds : acceptButtons) {
            if (bounds != null && contains(mx, my, bounds.x, bounds.y, bounds.w, bounds.h)) {
                hover = bounds.orderId;
                break;
            }
        }
        updateHoverSound(hover);
    }

    private void updateHoverSound(String key) {
        if (key.isBlank()) {
            if (!hoveredButton.isBlank()) {
                hoveredButton = "";
            }
            return;
        }
        if (!key.equals(hoveredButton)) {
            hoveredButton = key;
            play(ModSounds.COWBOY_GUNSHOT.get());
        }
    }

    private void play(net.minecraft.sounds.SoundEvent sound) {
        if (minecraft != null && minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, 1.0F));
        }
    }

    private void drawChooseOne(GuiGraphics graphics) {
        Component text = Component.translatable("stardewcraft.special_orders.choose_one");
        int textWidth = font.width(text);
        int bannerWidth = textWidth + font.width("W");
        int centerX = winX + winW / 2;
        int y = Math.max(10, winY - u(70));
        int x = centerX - bannerWidth / 2;
        CommonGuiTextures.drawScrollBanner(graphics, x, y - Math.round(3 * s4), bannerWidth, s4);
        drawCenteredText(graphics, text, centerX, y, 0xFF5B5045);
    }

    private void drawDescription(GuiGraphics graphics, Component desc, int x, int y, int width, int color, boolean shadow) {
        float textScale = 1.0F;
        List<FormattedCharSequence> lines = font.split(desc, width);
        int lineStep = font.lineHeight + 2;
        int maxHeight = u(400);
        while (lines.size() * lineStep * textScale > maxHeight && textScale > 0.55F) {
            textScale -= 0.05F;
            lines = font.split(desc, Math.max(1, Math.round(width / textScale)));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(textScale, textScale, 1.0F);
        int lineY = 0;
        for (FormattedCharSequence line : lines) {
            drawText(graphics, line, 0, lineY, color);
            lineY += lineStep;
        }
        graphics.pose().popPose();
    }

    private void drawText(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    private void drawText(GuiGraphics graphics, FormattedCharSequence text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color, false);
    }

    private void drawCenteredText(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        int x = centerX - font.width(text) / 2;
        drawText(graphics, text, x, y, color);
    }

    private int u(int stardewPixels) {
        return Math.round(stardewPixels * s4 / 4.0F);
    }

    private record ButtonBounds(int x, int y, int w, int h, String orderId) {
    }
}
