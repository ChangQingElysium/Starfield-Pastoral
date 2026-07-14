package com.stardew.craft.client.gui;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.StardewRenderMapping;
import com.stardew.craft.mail.MailTextSyntax;
import com.stardew.craft.network.payload.CheckMailboxPayload;
import com.stardew.craft.network.payload.OpenMailPayload;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 信件阅读界面，像素级对齐 SDV LetterViewerMenu。
 * <p>
 * SDV 原版参数:
 * - 信纸尺寸 1280×720 (SDV px)，即 320×180 纹理 ×4
 * - letterBG.png: 1280×512，每个背景 320×180
 * - 文字区域: (x+32, y+32) 到 (x+width-32, y+height-32) SDV px
 * - 前进/后退按钮: cursors.png (352,495,12,11) / (365,495,12,11) scale 4
 */
@SuppressWarnings("null")
public class LetterViewerScreen extends Screen {

    // ── letterBG.png 纹理参数 ──
    private static final ResourceLocation LETTER_BG = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/letter_bg.png");
    private static final int LETTER_BG_WIDTH = 1280;
    private static final int LETTER_BG_HEIGHT = 512;
    private static final Map<String, CustomLetterBackground> CUSTOM_LETTER_BACKGROUNDS = Map.of(
            "LooseSprites/squidFestLetterBG", new CustomLetterBackground(
                    ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/squid_fest_letter_bg.png"),
                    320,
                    204
            )
    );

    // SDV 信纸原始尺寸
    private static final int SDV_LETTER_W = 1280;  // 320×4
    private static final int SDV_LETTER_H = 720;   // 180×4
    private static final int SDV_BG_TILE_W = 320;
    private static final int SDV_BG_TILE_H = 180;

    // SDV 文字边距 (SDV px)
    private static final int SDV_TEXT_MARGIN = 32;

    // SDV 翻页按钮：cursors.png (352,495,12,11) 和 (365,495,12,11)
    private static final int BTN_W = 12, BTN_H = 11;

    private static final int CLOSE_BTN_W = 12, CLOSE_BTN_H = 12;

    // 金币图标纹理
    private static final ResourceLocation GOLD_ICON = ResourceLocation.fromNamespaceAndPath(
            StardewCraft.MODID, "textures/gui/gold_icon.png");
    private static final int GOLD_ICON_SIZE = 16;

    // ── Payload 数据 ──
    private final OpenMailPayload payload;
    private final List<String> pages = new ArrayList<>();
    private int currentPage;
    private ResourceLocation activeLetterTexture = LETTER_BG;
    private int activeLetterTextureWidth = LETTER_BG_WIDTH;
    private int activeLetterTextureHeight = LETTER_BG_HEIGHT;
    private int activeBackground;
    private String activeTextColorName = "";

    // ── 布局（MC GUI 坐标） ──
    private StardewRenderMapping mapping;
    private int letterX, letterY, letterW, letterH;
    private int textX, textY, textW, textH;
    private int backBtnX, backBtnY, backBtnW, backBtnH;
    private int fwdBtnX, fwdBtnY, fwdBtnW, fwdBtnH;
    private int closeBtnX, closeBtnY, closeBtnW, closeBtnH;

    // ── 打开动画 ──
    private float scale;

    private record CustomLetterBackground(ResourceLocation texture, int width, int height) {}

    public LetterViewerScreen(OpenMailPayload payload) {
        super(Component.empty());
        this.payload = payload;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        float guiScale = (float) minecraft.getWindow().getGuiScale();
        mapping = new StardewRenderMapping(width, height, guiScale);

        letterW = mapping.ui(SDV_LETTER_W);
        letterH = mapping.ui(SDV_LETTER_H);
        letterX = mapping.centerX(letterW);
        letterY = (height - letterH) / 2;

        int margin = mapping.ui(SDV_TEXT_MARGIN);
        textX = letterX + margin;
        textY = letterY + margin;
        textW = letterW - margin * 2;
        textH = letterH - margin * 2;

        // 翻页按钮
        float btnScale = mapping.s4();
        backBtnW = (int) (BTN_W * btnScale);
        backBtnH = (int) (BTN_H * btnScale);
        fwdBtnW = backBtnW;
        fwdBtnH = backBtnH;

        backBtnX = letterX + mapping.ui(32);
        backBtnY = letterY + letterH - mapping.ui(32) - backBtnH;
        fwdBtnX = letterX + letterW - mapping.ui(32) - fwdBtnW;
        fwdBtnY = backBtnY;

        // 关闭按钮 — SDV: (xPositionOnScreen + width - 36, yPositionOnScreen - 8, 48, 48)
        closeBtnW = (int) (CLOSE_BTN_W * btnScale);
        closeBtnH = (int) (CLOSE_BTN_H * btnScale);
        closeBtnX = letterX + letterW - closeBtnW + (int)(4 * btnScale);
        closeBtnY = letterY - (int)(8 * btnScale);

        // 分页文本
        paginateText();

        scale = 0f;
    }

    /**
     * 将邮件文本按页面高度分割。
     * 使用 MC font 按行高分页，模拟 SDV SpriteText 的分段。
     */
    private void paginateText() {
        pages.clear();
        resetLetterFormatting();
        String text = payload.text();
        // 如果文本看起来是翻译键（不含空格但含点号），在客户端翻译
        if (text != null && !text.contains(" ") && text.contains(".")) {
            String translated = net.minecraft.network.chat.Component.translatable(text).getString();
            if (!translated.equals(text)) {
                text = translated;
            }
        }
        // 将 SDV 邮件里的换行标记转成真实换行。
        if (text != null) {
            // Vanilla LetterViewerMenu removes the collection title and consumes
            // %item/%action blocks before laying out the visible body.
            text = MailTextSyntax.body(text);
            text = applyLetterFormatting(text);
            if (minecraft != null && minecraft.player != null) {
                text = text.replace("@", minecraft.player.getName().getString());
            }
            if (payload.secretSantaNameKey() != null && !payload.secretSantaNameKey().isBlank()) {
                text = text.replace("%secretsanta",
                        Component.translatable(payload.secretSantaNameKey()).getString());
            }
            text = text.replace("\\n", "\n")
                    .replace("^^", "\n\n")
                    .replace("^", "\n");
        }
        if (text == null || text.isEmpty()) {
            pages.add("");
            return;
        }

        // [#] is the collection title separator and has already been removed.
        String[] sections = new String[] {text};
        for (String section : sections) {
            section = section.trim();
            if (section.isEmpty()) continue;

            // 进一步按高度分割
            List<String> lines = wrapText(section, textW);
            int linesPerPage = Math.max(1, textH / (font.lineHeight + 2));
            StringBuilder page = new StringBuilder();
            int count = 0;
            for (String line : lines) {
                if (count >= linesPerPage && page.length() > 0) {
                    pages.add(page.toString().trim());
                    page = new StringBuilder();
                    count = 0;
                }
                page.append(line).append("\n");
                count++;
            }
            if (page.length() > 0) {
                pages.add(page.toString().trim());
            }
        }

        if (pages.isEmpty()) {
            pages.add("");
        }
        currentPage = 0;
    }

    private void resetLetterFormatting() {
        activeLetterTexture = LETTER_BG;
        activeLetterTextureWidth = LETTER_BG_WIDTH;
        activeLetterTextureHeight = LETTER_BG_HEIGHT;
        activeBackground = payload.background();
        activeTextColorName = payload.textColorName() == null ? "" : payload.textColorName();
    }

    private String applyLetterFormatting(String text) {
        String remaining = text;
        while (remaining.startsWith("[")) {
            int end = remaining.indexOf(']');
            if (end <= 0) {
                break;
            }
            if (!applyLetterFormattingCommand(remaining.substring(1, end).trim())) {
                break;
            }
            remaining = remaining.substring(end + 1);
        }
        return remaining;
    }

    private boolean applyLetterFormattingCommand(String command) {
        if (command.isEmpty()) {
            return false;
        }
        String[] parts = command.split("\\s+");
        String type = parts[0].toLowerCase(Locale.ROOT);
        if ("textcolor".equals(type)) {
            if (parts.length >= 2) {
                activeTextColorName = parts[1];
            }
            return true;
        }
        if ("letterbg".equals(type)) {
            if (parts.length == 2) {
                activeBackground = parseBackgroundIndex(parts[1], activeBackground);
            } else if (parts.length >= 3) {
                String contentPath = normalizeContentPath(parts[1]);
                CustomLetterBackground customBackground = CUSTOM_LETTER_BACKGROUNDS.get(contentPath);
                if (customBackground != null) {
                    activeLetterTexture = customBackground.texture();
                    activeLetterTextureWidth = customBackground.width();
                    activeLetterTextureHeight = customBackground.height();
                }
                activeBackground = parseBackgroundIndex(parts[2], activeBackground);
            }
            return true;
        }
        return false;
    }

    private static String normalizeContentPath(String path) {
        String normalized = path.replace('\\', '/');
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", "/");
        }
        return normalized;
    }

    private static int parseBackgroundIndex(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<String> wrapText(String text, int maxWidth) {
        // 用 MC 自带的 font.split —— 支持 CJK 字符逐字断行，空格断词，宽度溢出强制切。
        // 之前手写按 " " 切的版本对中文整段无空格文本完全无效，会越界画出边框。
        List<String> result = new ArrayList<>();
        for (String rawLine : text.split("\n")) {
            if (rawLine.isEmpty()) {
                result.add("");
                continue;
            }
            for (net.minecraft.util.FormattedCharSequence seq :
                    font.split(net.minecraft.network.chat.Component.literal(rawLine), maxWidth)) {
                StringBuilder sb = new StringBuilder();
                seq.accept((idx, style, codePoint) -> {
                    sb.appendCodePoint(codePoint);
                    return true;
                });
                result.add(sb.toString());
            }
        }
        return result;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        // 背景变暗
        graphics.fill(0, 0, width, height, 0x66000000);

        // 打开动画
        if (scale < 1f) {
            scale += partialTick * 0.003f * 60f;  // ~0.003 per ms × 60fps
            if (scale > 1f) scale = 1f;
        }

        // 绘制信纸背景
        drawLetterBackground(graphics);

        if (scale >= 1f) {
            // 绘制文字
            drawText(graphics);

            // 绘制附件区域（最后一页）
            if (currentPage == pages.size() - 1) {
                drawAttachments(graphics);
            }

            // 绘制翻页按钮
            drawPageButtons(graphics, mouseX, mouseY);

            // 绘制关闭按钮
            drawCloseButton(graphics);
        }
    }

    private void drawLetterBackground(GuiGraphics graphics) {
        int bg = activeBackground;
        // letterBG.png 布局: 每个背景 320×180
        // whichBG % 4 * 320 = U, (whichBG >= 4) ? (204 + (whichBG/4-1)*180) : 0 = V
        int srcU = (bg % 4) * SDV_BG_TILE_W;
        int srcV = (bg >= 4) ? (204 + (bg / 4 - 1) * SDV_BG_TILE_H) : 0;

        // SDV 从中心画，scale 动画。MC 模拟：先平移到中心再缩放
        float drawScale = mapping.s4() * scale;

        graphics.pose().pushPose();
        int cx = letterX + letterW / 2;
        int cy = letterY + letterH / 2;
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(drawScale, drawScale, 1f);
        graphics.blit(activeLetterTexture,
                -SDV_BG_TILE_W / 2, -SDV_BG_TILE_H / 2,
                srcU, srcV,
                SDV_BG_TILE_W, SDV_BG_TILE_H,
                activeLetterTextureWidth, activeLetterTextureHeight);
        graphics.pose().popPose();
    }

    private void drawText(GuiGraphics graphics) {
        if (currentPage >= pages.size()) return;
        String pageText = pages.get(currentPage);

        int textColor = getTextColor();
        int y = textY;
        for (String line : pageText.split("\n")) {
            graphics.drawString(font, line, textX, y, textColor, false);
            y += font.lineHeight + 2;
        }
    }

    private int getTextColor() {
        String colorName = activeTextColorName;
        if (colorName == null || colorName.isEmpty()) {
            // 根据背景选择默认颜色（SDV parity）
            return switch (activeBackground) {
                case 1 -> 0xFF808080;  // gray (lined paper)
                case 2 -> 0xFF00FFFF;  // cyan (wizard)
                case 3 -> 0xFFFFFFFF;  // white (Krobus)
                case 4 -> 0xFF5B9BD5;  // Joja blue
                default -> 0xFF5C1700;  // dark brown (parchment default)
            };
        }
        return switch (colorName.toLowerCase()) {
            case "black" -> 0xFF000000;
            case "blue" -> 0xFF0000FF;
            case "red" -> 0xFFFF0000;
            case "purple" -> 0xFF800080;
            case "white" -> 0xFFFFFFFF;
            case "orange" -> 0xFFFFA500;
            case "green" -> 0xFF00FF00;
            case "cyan" -> 0xFF00FFFF;
            case "gray" -> 0xFF808080;
            case "jojablue" -> 0xFF5B9BD5;
            default -> 0xFF5C1700;
        };
    }

    private void drawAttachments(GuiGraphics graphics) {
        int attachY = letterY + letterH - mapping.ui(96);

        // 金钱附件 — SDV parity: 文字 + 金币图标
        if (payload.money() > 0) {
            String moneyText = Component.translatable("stardewcraft.letter.money_included",
                    payload.money()).getString();
            int tw = font.width(moneyText);
            float iconScale = mapping.s4() * 0.6f;
            int iconW = (int)(GOLD_ICON_SIZE * iconScale);
            int totalW = tw + iconW + 4;
            int mx = letterX + letterW / 2 - totalW / 2;
            int my = attachY;

            // 金币图标（文字左侧）
            graphics.pose().pushPose();
            graphics.pose().translate(mx, my - 1, 0);
            graphics.pose().scale(iconScale, iconScale, 1f);
            graphics.blit(GOLD_ICON, 0, 0, 0, 0, GOLD_ICON_SIZE, GOLD_ICON_SIZE, GOLD_ICON_SIZE, GOLD_ICON_SIZE);
            graphics.pose().popPose();

            // 金额文字
            graphics.drawString(font, moneyText, mx + iconW + 4, my, getTextColor(), false);
        }

        // 配方学习
        if (!payload.learnedRecipe().isEmpty()) {
            String recipeTypeKey = "cooking".equals(payload.cookingOrCrafting())
                    ? "stardewcraft.letter.learned_recipe_cooking"
                    : "stardewcraft.letter.learned_recipe_crafting";
            String line1 = Component.translatable(recipeTypeKey).getString();
            String line2 = payload.learnedRecipe();
            int y1 = attachY;
            graphics.drawCenteredString(font, line1, letterX + letterW / 2, y1, getTextColor());
            graphics.drawCenteredString(font, line2, letterX + letterW / 2, y1 + font.lineHeight + 2, getTextColor());
        }

        // 物品附件 — SDV parity: 底部居中显示物品图标 + 数量
        if (!payload.items().isEmpty()) {
            int totalItems = payload.items().size();
            float bgScale = mapping.s4();
            int slotSize = (int)(24 * bgScale);
            int gap = (int)(4 * bgScale);
            int totalSlotW = totalItems * slotSize + (totalItems - 1) * gap;
            int startX = letterX + letterW / 2 - totalSlotW / 2;
            int slotY = attachY + font.lineHeight + 4;

            for (int i = 0; i < totalItems; i++) {
                OpenMailPayload.ItemAttachment att = payload.items().get(i);
                int slotX = startX + i * (slotSize + gap);

                // 物品背景框
                int bgU = activeBackground * 24;
                int bgV = 180;
                graphics.pose().pushPose();
                graphics.pose().translate(slotX, slotY, 0);
                graphics.pose().scale(bgScale, bgScale, 1f);
                graphics.blit(activeLetterTexture, 0, 0, bgU, bgV, 24, 24,
                        activeLetterTextureWidth, activeLetterTextureHeight);
                graphics.pose().popPose();

                // 物品图标
                ResourceLocation itemRL = ResourceLocation.parse(att.itemId());
                net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(itemRL);
                if (item != Items.AIR) {
                    ItemStack stack = new ItemStack(item, att.count());
                    int iconX = slotX + (int)(4 * bgScale);
                    int iconY = slotY + (int)(4 * bgScale);
                    CommonGuiTextures.drawItem(graphics, stack, iconX, iconY, bgScale);
                    if (att.count() > 1) {
                        CommonGuiTextures.drawItemDecorations(graphics, font, stack, iconX, iconY, bgScale);
                    }
                }
            }
        }
    }

    private void drawPageButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        float btnScale = mapping.s4();

        // 后退按钮
        if (currentPage > 0) {
                CommonGuiTextures.drawBackArrow(graphics, backBtnX, backBtnY, btnScale);
        }

        // 前进按钮
        if (currentPage < pages.size() - 1) {
                CommonGuiTextures.drawForwardArrow(graphics, fwdBtnX, fwdBtnY, btnScale);
        }
    }

    private void drawCloseButton(GuiGraphics graphics) {
        float btnScale = mapping.s4();
        CommonGuiTextures.drawCloseButton(graphics, closeBtnX, closeBtnY, btnScale);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (scale < 1f) return false;

        // 关闭按钮
        if (isInBounds(mouseX, mouseY, closeBtnX, closeBtnY, closeBtnW, closeBtnH)) {
            playCloseSound();
            closeLetter();
            return true;
        }

        // 后退按钮
        if (currentPage > 0 && isInBounds(mouseX, mouseY, backBtnX, backBtnY, backBtnW, backBtnH)) {
            currentPage--;
            playPageSound();
            return true;
        }

        // 前进按钮
        if (currentPage < pages.size() - 1 && isInBounds(mouseX, mouseY, fwdBtnX, fwdBtnY, fwdBtnW, fwdBtnH)) {
            currentPage++;
            playPageSound();
            return true;
        }

        // 点击信纸区域 → 翻页或关闭
        if (isInBounds(mouseX, mouseY, letterX, letterY, letterW, letterH)) {
            if (currentPage < pages.size() - 1) {
                currentPage++;
                playPageSound();
            } else {
                playPageSound();
                closeLetter();
            }
            return true;
        }

        // 点击信纸外部 → 关闭
        playCloseSound();
        closeLetter();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            closeLetter();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void closeLetter() {
        onClose();
        // 如果还有信，自动检查下一封
        if (payload.remainingMailCount() > 0) {
            PacketDistributor.sendToServer(new CheckMailboxPayload());
        }
    }

    private boolean isInBounds(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void playPageSound() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(ModSounds.SHWIP.get(), 0.5f, 1.0f);
        }
    }

    private void playCloseSound() {
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.playSound(ModSounds.BIG_DESELECT.get(), 0.5f, 1.0f);
        }
    }
}
