package com.stardew.craft.client.gui.common;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.NpcDisplayNames;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.StardewCollectivePauseScreen;
import com.stardew.craft.sound.ModSounds;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class StardewNpcDialogueScreen extends Screen implements StardewCollectivePauseScreen {
    private static final Pattern NUMERIC_EMOTION = Pattern.compile("\\$([0-9]+)");
    private static final String[] PERCENT_TOKENS = new String[] {
        "%adj", "%noun", "%place", "%spouse", "%name", "%firstnameletter", "%time", "%band", "%book", "%pet",
        "%farm", "%favorite", "%fork", "%year", "%kid1", "%kid2", "%revealtaste", "%season"
    };

    private final String npcId;
    private final String rawText;
    private final int friendshipPoints;
    private static final Map<ResourceLocation, PortraitResource> PORTRAIT_CACHE = new ConcurrentHashMap<>();

    public static void clearPortraitCache() {
        PORTRAIT_CACHE.clear();
    }

    private final List<DialoguePage> pages = new ArrayList<>();
    private StardewRenderMapping mapping;
    
    private record NpcResponseAction(String answerId, int scoreDelta, String nextNodeId, String responseText) {}
    private final List<NpcResponseAction> questionResponses = new ArrayList<>();
    private boolean questionScreenOpened;

    private int pageIndex;
    private int characterIndexInDialogue;
    private int characterAdvanceTimer;
    private int safetyTimer;
    private int newPortraitShakeTimer;
    private long lastUpdateMs;
    private boolean transitioning = true;
    private boolean transitioningBigger = true;
    private boolean transitionInitialized;
    private boolean openingSoundPlayed;
    private int transitionX;
    private int transitionY;
    private int transitionWidth;
    private int transitionHeight;
    private int iconFrameTick;
    private long lastRenderMs;

    /** Optional action to run when the dialogue screen is closed (SDV afterDialogues parity). */
    @javax.annotation.Nullable
    private Runnable afterCloseAction;

    private int boxX;
    private int boxY;
    private int boxW;
    private int boxH;

    private record DialogueChunk(String text, boolean showPortrait, int portraitIndex) {
    }

    private record DialoguePage(String text, boolean showPortrait, int portraitIndex) {
    }

    public StardewNpcDialogueScreen(String npcId, String text, int friendshipPoints) {
        super(Component.literal("NPC Dialogue"));
        this.npcId = npcId == null ? "" : npcId;
        this.rawText = text == null ? "..." : text;
        this.friendshipPoints = Math.max(0, friendshipPoints);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Set an action to run after the dialogue screen closes (SDV Game1.afterDialogues parity). */
    public StardewNpcDialogueScreen withAfterClose(@javax.annotation.Nullable Runnable action) {
        this.afterCloseAction = action;
        return this;
    }

    @Override
    protected void init() {
        super.init();
        recomputeLayout();
        rebuildPages();
        this.questionScreenOpened = false;
        this.pageIndex = 0;
        this.characterIndexInDialogue = 0;
        this.characterAdvanceTimer = 90;
        this.safetyTimer = 750;
        if (!currentPage().text().isEmpty() && currentPage().text().length() <= 20) {
            this.safetyTimer = Math.max(0, this.safetyTimer - 200);
        }
        this.transitioning = true;
        this.transitioningBigger = true;
        this.transitionInitialized = false;
        this.openingSoundPlayed = false;
        this.iconFrameTick = 0;
        this.lastRenderMs = Util.getMillis();
        this.lastUpdateMs = Util.getMillis();
    }

    @Override
    public void tick() {
        recomputeLayout();
        long now = Util.getMillis();
        int elapsed = (int) Math.max(0L, now - this.lastUpdateMs);
        this.lastUpdateMs = now;
        if (transitioning) {
            return;
        }

        if (newPortraitShakeTimer > 0) {
            newPortraitShakeTimer = Math.max(0, newPortraitShakeTimer - elapsed);
        }
        if (safetyTimer > 0) {
            safetyTimer = Math.max(0, safetyTimer - elapsed);
        }

        String current = currentPage().text();
        if (characterIndexInDialogue >= current.length()) {
            openQuestionResponsesIfReady();
            return;
        }

        characterAdvanceTimer -= elapsed;
        while (characterAdvanceTimer <= 0 && characterIndexInDialogue < current.length()) {
            characterAdvanceTimer += 30;
            int old = characterIndexInDialogue;
            characterIndexInDialogue = Math.min(current.length(), characterIndexInDialogue + 1);
            if (characterIndexInDialogue != old && characterIndexInDialogue == current.length()) {
                playUiSound(ModSounds.DIALOGUE_CHARACTER_CLOSE.get(), 1.0f, 1.0f);
            } else if (characterIndexInDialogue > 1 && characterIndexInDialogue < current.length()) {
                playUiSound(ModSounds.DIALOGUE_CHARACTER.get(), 1.0f, 1.0f);
            }
        }
        openQuestionResponsesIfReady();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        recomputeLayout();
        long now = Util.getMillis();
        int renderElapsed = (int) Math.max(0L, now - this.lastRenderMs);
        this.lastRenderMs = now;
        this.iconFrameTick += renderElapsed;
        updateTransition(renderElapsed);

        if (transitioning) {
            if (transitionWidth > 0 && transitionHeight > 0) {
                drawBox(graphics, transitionX, transitionY, transitionWidth, transitionHeight);
            }
            return;
        }

        drawBox(graphics, boxX, boxY, boxW, boxH);

        DialoguePage page = currentPage();
        if (page.showPortrait()) {
            drawPortraitArea(graphics, page);
        }

        drawDialogueText(graphics, page);
        drawContinueOrCloseIcon(graphics, page);
        drawFriendshipHover(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (transitioning) {
            return true;
        }
        receiveAdvanceClick();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (transitioning) {
            return true;
        }
        if (keyCode == 256) {
            // ESC 不能直接关闭对话，必须走正常流程，否则问答类对话会卡死任务
            receiveAdvanceClick();
            return true;
        }
        if (keyCode == 257 || keyCode == 32) {
            receiveAdvanceClick();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void receiveAdvanceClick() {
        String current = currentPage().text();
        if (characterIndexInDialogue < current.length() - 1) {
            characterIndexInDialogue = Math.max(0, current.length() - 1);
            return;
        }
        if (safetyTimer > 0) {
            return;
        }

        int oldPortrait = currentPage().portraitIndex();
        if (pageIndex + 1 < pages.size()) {
            pageIndex++;
            characterIndexInDialogue = 0;
            characterAdvanceTimer = 90;
            safetyTimer = 750;
            if (currentPage().text().length() <= 20) {
                safetyTimer = Math.max(0, safetyTimer - 200);
            }
            int newPortrait = currentPage().portraitIndex();
            if (newPortrait != oldPortrait) {
                newPortraitShakeTimer = (newPortrait == 1) ? 250 : 50;
            }
            playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
            return;
        }

        if (!questionResponses.isEmpty()) {
            openQuestionResponsesIfReady();
            return;
        }

        playUiSound(ModSounds.SMALL_SELECT.get(), 1.0f, 1.0f);
        beginOutro();
    }

    private void openQuestionResponsesIfReady() {
        if (questionScreenOpened
                || questionResponses.isEmpty()
                || pageIndex + 1 < pages.size()
                || characterIndexInDialogue < currentPage().text().length()) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.screen != this) {
            return;
        }
        questionScreenOpened = true;
        List<Component> options = new ArrayList<>();
        for (NpcResponseAction action : questionResponses) {
            options.add(Component.literal(action.responseText()));
        }
        StardewQuestionDialogSpec spec = StardewQuestionDialogSpec.of(
            Component.literal(currentPage().text()),
            options,
            (answerIndex) -> {
                if (answerIndex >= 0 && answerIndex < questionResponses.size()) {
                    NpcResponseAction picked = questionResponses.get(answerIndex);
                    if (com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload.isDirectText(
                            picked.nextNodeId())) {
                        openClientSideResponse(picked.nextNodeId());
                        return;
                    }
                    net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new com.stardew.craft.network.payload.AnswerNpcQuestionPayload(
                            npcId, picked.nextNodeId(), picked.scoreDelta(), picked.answerId()
                        )
                    );
                }
            },
            -1
        );
        minecraft.setScreen(StardewConfirmDialogScreen.createDialogueContinuation(spec));
    }

    /**
     * SDV {@code $y} quick responses are local dialogue continuations. Their text must never be
     * sent through AnswerNpcQuestionPayload, whose next-node field is intentionally a short id.
     */
    private void openClientSideResponse(String source) {
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        String response = com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload
                .resolveClientTextSource(source);
        String accountName = minecraft.player != null && minecraft.player.getGameProfile() != null
                ? minecraft.player.getGameProfile().getName()
                : "player";
        response = com.stardew.craft.network.payload.OpenNpcDialogueScreenPayload
                .resolvePercentTokens(response, accountName);

        StardewNpcDialogueScreen continuation =
                new StardewNpcDialogueScreen(npcId, response, friendshipPoints);
        continuation.afterCloseAction = this.afterCloseAction;
        this.afterCloseAction = null;
        minecraft.setScreen(continuation);
    }

    private void beginOutro() {
        if (transitioning) {
            return;
        }
        this.transitioning = true;
        this.transitioningBigger = false;
        playUiSound(ModSounds.BREATHOUT.get(), 1.0f, 1.0f);
    }

    private void updateTransition(int elapsedMs) {
        if (!transitioning) {
            return;
        }

        if (!transitionInitialized) {
            transitionInitialized = true;
            transitionX = boxX + boxW / 2;
            transitionY = boxY + boxH / 2;
            transitionWidth = 0;
            transitionHeight = 0;
        }

        float ratio = boxW <= 0 ? 1.0f : ((float) boxH / (float) boxW);
        int speed = (int) (elapsedMs * 3f);
        int speedY = (int) (elapsedMs * 3f * ratio);

        if (transitioningBigger) {
            int oldWidth = transitionWidth;
            transitionX -= speed;
            transitionY -= speedY;
            transitionX = Math.max(boxX, transitionX);
            transitionY = Math.max(boxY, transitionY);
            transitionWidth += speed * 2;
            transitionHeight += speedY * 2;
            transitionWidth = Math.min(boxW, transitionWidth);
            transitionHeight = Math.min(boxH, transitionHeight);

            if (!openingSoundPlayed && oldWidth == 0 && transitionWidth > 0) {
                openingSoundPlayed = true;
                playUiSound(ModSounds.BREATHIN.get(), 1.0f, 1.0f);
            }

            if (transitionX == boxX && transitionY == boxY) {
                transitioning = false;
                transitionX = boxX;
                transitionY = boxY;
                transitionWidth = boxW;
                transitionHeight = boxH;
                characterAdvanceTimer = 90;
            }
            return;
        }

        transitionX += speed;
        transitionY += speedY;
        transitionX = Math.min(boxX + boxW / 2, transitionX);
        transitionY = Math.min(boxY + boxH / 2, transitionY);
        transitionWidth -= speed * 2;
        transitionHeight -= speedY * 2;
        transitionWidth = Math.max(0, transitionWidth);
        transitionHeight = Math.max(0, transitionHeight);
        if (transitionWidth == 0 && transitionHeight == 0) {
            closeScreen();
        }
    }

    private void recomputeLayout() {
        float guiScale = this.minecraft == null ? 1.0f : (float) this.minecraft.getWindow().getGuiScale();
        this.mapping = new StardewRenderMapping(this.width, this.height, guiScale);
        this.boxW = mapping.ui(1200);
        this.boxH = mapping.ui(384);
        this.boxX = mapping.centerX(boxW);
        this.boxY = this.height - boxH - mapping.ui(64);
    }

    private void rebuildPages() {
        pages.clear();
        List<DialogueChunk> chunks = parseChunks(rawText);
        for (DialogueChunk chunk : chunks) {
            paginateChunk(chunk, pages);
        }
        if (pages.isEmpty()) {
            com.stardew.craft.StardewCraft.LOGGER.warn("[NPC_DIALOGUE] rebuildPages produced 0 pages! npcId={} rawText(first120)={}",
                npcId, rawText.length() > 120 ? rawText.substring(0, 120) : rawText);
            pages.add(new DialoguePage("...", true, 0));
        }
    }

    private List<DialogueChunk> parseChunks(String text) {
        questionResponses.clear();
        String normalized = text
            .replace("\r", "")
            .replace("\n", "");

        if (normalized.contains("$q ")) {
            int qIndex = normalized.indexOf("$q ");
            String mainText = normalized.substring(0, qIndex).trim();
            String queryPart = normalized.substring(qIndex);

            // Tolerate data that omits the leading '#' before '$r' / '$q'
            // (canonical SDV format uses '#$r'/'#$q'). Insert it where missing
            // so the parser below can rely on '#$r' as the response delimiter.
            queryPart = queryPart.replaceAll("(?<!#)\\$r ", "#\\$r ");

            // Format: $q <id> <next_if_already> <base text>#$r <resId> <score> <resNext>#<resText>#$r...
            // Extract base text
            int firstHash = queryPart.indexOf("#");
            if (firstHash != -1) {
                int nextHash = queryPart.indexOf("#$r", firstHash);
                if (nextHash == -1) nextHash = queryPart.length();
                String baseText = (nextHash > firstHash + 1)
                    ? queryPart.substring(firstHash + 1, nextHash).trim()
                    : "";
                if (!baseText.isEmpty()) {
                    mainText += (mainText.isEmpty() ? "" : " ") + baseText;
                }
                
                // Parse responses
                String remain = queryPart.substring(nextHash);
                while (remain.startsWith("#$r") || remain.startsWith("$r")) {
                    int rStart = remain.indexOf("$r ") + 3;
                    int rHash1 = remain.indexOf("#", rStart);
                    if (rHash1 == -1) break;
                    String[] tokens = remain.substring(rStart, rHash1).trim().split(" ");
                    if (tokens.length >= 3) {
                        int scoreDelta = 0;
                        try { scoreDelta = Integer.parseInt(tokens[1]); } catch (NumberFormatException ignored) {}
                        String answerId = tokens[0];
                        String nextNodeId = tokens[2];
                        
                        int rHash2 = remain.indexOf("#$r", rHash1);
                        if (rHash2 == -1) rHash2 = remain.length();
                        String responseText = remain.substring(rHash1 + 1, rHash2).trim();
                        questionResponses.add(new NpcResponseAction(answerId, scoreDelta, nextNodeId, responseText));
                        remain = remain.substring(rHash2);
                    } else {
                        break;
                    }
                }
            }
            normalized = mainText;
        }

        normalized = normalized
            .replace("#$b#", "\f")
            .replace("$b", "\f")
            .replace("#$e#", "\f")
            .replace("$e", "\f");
        String[] rawChunks = normalized.split("\\f");
        List<DialogueChunk> result = new ArrayList<>();
        for (String raw : rawChunks) {
            String chunk = raw.trim();
            if (chunk.isEmpty()) {
                continue;
            }

            boolean showPortrait = true;
            if (chunk.startsWith("%") && !startsWithPercentToken(chunk)) {
                showPortrait = false;
                chunk = chunk.substring(1);
            }

            // Extract ALL portrait emotion codes (not just one)
            int portraitIndex = 0;
            // Named emotion codes
            if (chunk.contains("$h")) { portraitIndex = 1; chunk = chunk.replace("$h", ""); }
            if (chunk.contains("$s")) { portraitIndex = 2; chunk = chunk.replace("$s", ""); }
            if (chunk.contains("$u")) { portraitIndex = 3; chunk = chunk.replace("$u", ""); }
            if (chunk.contains("$l")) { portraitIndex = 4; chunk = chunk.replace("$l", ""); }
            if (chunk.contains("$a")) { portraitIndex = 5; chunk = chunk.replace("$a", ""); }
            // Numeric emotion codes: $8, $12, etc. — strip ALL occurrences
            Matcher m = NUMERIC_EMOTION.matcher(chunk);
            if (m.find()) {
                try {
                    portraitIndex = Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    portraitIndex = 0;
                }
                chunk = NUMERIC_EMOTION.matcher(chunk).replaceAll("");
            }

            // Safety: strip any remaining unhandled $ command tokens
            chunk = chunk.replaceAll("\\$1\\s+\\w+", "");
            chunk = chunk.replaceAll("\\$[cdpkvy]\\b\\s*\\S*", "");
            chunk = chunk.replaceAll("\\$(?:query|action)\\b[^$]*?(?=\\$|$)", "");
            // Strip remaining % tokens that slipped through
            chunk = chunk.replaceAll("%(?:adj|noun|place|spouse|name|firstnameletter|time|band|book|pet|farm|favorite|fork|year|kid[12]|revealtaste[:\\w]*|season|noturn)\\b", "");
            // Strip any remaining || or stray # delimiters not consumed by resolveDialogueCommands
            chunk = chunk.replace("||", "");
            // Resolve any remaining single | (branch separator) — keep first branch
            int pipeIdx = chunk.indexOf('|');
            if (pipeIdx >= 0) {
                chunk = chunk.substring(0, pipeIdx);
            }
            // Remove stray # that aren't part of $b/$e/$q/$r (already handled upstream)
            chunk = chunk.replace("#", "");
            // Remove escaped pipe \| (data error)
            chunk = chunk.replace("\\|", "");
            // Strip any remaining unrecognized $ tokens as final safety net
            chunk = chunk.replaceAll("\\$[a-zA-Z]\\b", "");
            chunk = chunk.replaceAll("\\$[0-9]+", "");

            chunk = chunk.trim();
            if (!chunk.isEmpty()) {
                result.add(new DialogueChunk(chunk, showPortrait, Math.max(0, portraitIndex)));
            }
        }
        return result;
    }

    private boolean startsWithPercentToken(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : PERCENT_TOKENS) {
            if (lower.startsWith(token)) {
                return true;
            }
        }
        return false;
    }

    private void paginateChunk(DialogueChunk chunk, List<DialoguePage> out) {
        int textWidth = chunk.showPortrait() ? boxW - mapping.ui(460) - mapping.ui(24) : boxW;
        int textHeight = boxH - mapping.ui(16);
        int lineHeight = scaledLineHeight();
        int maxLines = Math.max(1, textHeight / lineHeight);

        String content = chunk.text().trim();
        if (content.isEmpty()) {
            out.add(new DialoguePage("...", chunk.showPortrait(), chunk.portraitIndex()));
            return;
        }

        int wrapWidth = Math.max(1, Math.round(textWidth / textScale()));
        String[] words = content.split("\\s+");
        StringBuilder pageBuilder = new StringBuilder();
        for (String word : words) {
            String candidate = pageBuilder.length() == 0 ? word : pageBuilder + " " + word;
            int lineCount = DialogueTextWrapper.wrap(dialogueTextFont(), candidate, wrapWidth).size();
            if (lineCount > maxLines && pageBuilder.length() > 0) {
                out.add(new DialoguePage(pageBuilder.toString().trim(), chunk.showPortrait(), chunk.portraitIndex()));
                pageBuilder.setLength(0);
                pageBuilder.append(word);
            } else {
                if (pageBuilder.length() > 0) {
                    pageBuilder.append(' ');
                }
                pageBuilder.append(word);
            }
        }

        if (pageBuilder.length() > 0) {
            out.add(new DialoguePage(pageBuilder.toString().trim(), chunk.showPortrait(), chunk.portraitIndex()));
        }
    }

    private DialoguePage currentPage() {
        if (pages.isEmpty()) {
            return new DialoguePage("...", true, 0);
        }
        return pages.get(Math.max(0, Math.min(pageIndex, pages.size() - 1)));
    }

    private void drawDialogueText(GuiGraphics graphics, DialoguePage page) {
        int textX = boxX + mapping.ui(8);
        int textY = boxY + mapping.ui(8);
        int wrap = boxW;
        if (page.showPortrait()) {
            textX = boxX + mapping.ui(8);
            wrap = boxW - mapping.ui(460) - mapping.ui(24);
        }

        String all = page.text();
        int end = Math.max(0, Math.min(characterIndexInDialogue, all.length()));
        Component visible = Component.literal(all.substring(0, end));
        float scale = textScale();
        int unscaledWrap = Math.max(1, Math.round(wrap / scale));
        Font textFont = dialogueTextFont();
        List<String> lines = DialogueTextWrapper.wrap(textFont, visible.getString(), unscaledWrap);

        graphics.pose().pushPose();
        graphics.pose().translate(textX, textY, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        int drawY = 0;
        int unscaledLineStep = unscaledLineStep(scale);
        for (String line : lines) {
            graphics.drawString(textFont, line, 0, drawY, spriteTextColor(), false);
            drawY += unscaledLineStep;
        }
        graphics.pose().popPose();
    }

    private void drawContinueOrCloseIcon(GuiGraphics graphics, DialoguePage page) {
        if (characterIndexInDialogue < page.text().length()) {
            return;
        }

        boolean hasNextPage = pageIndex + 1 < pages.size();
        int x = boxX + boxW - mapping.ui(40);
        int y = boxY + boxH - mapping.ui(40);
        if (page.showPortrait()) {
            x -= mapping.ui(492);
        }

        if (hasNextPage) {
            int frame = (iconFrameTick / 90) % 6;
            int periodic = iconFrameTick % 1500;
            int half = 750;
            int triangle = periodic <= half ? periodic : (1500 - periodic);
            int bob = Math.round((triangle / (float) half) * mapping.ui(8));
            CommonGuiTextures.drawDialogueNextPage(graphics, x, y + bob, frame, mapping.s4());
        } else {
            int frame = (iconFrameTick / 80) % 11;
            CommonGuiTextures.drawDialogueEnd(graphics, x, y - mapping.ui(4), frame, mapping.s4());
        }
    }

    private void drawFriendshipHover(GuiGraphics graphics, int mouseX, int mouseY) {
        if (transitioning || pages.isEmpty() || !currentPage().showPortrait()) {
            return;
        }
        int jewelX = boxX + boxW - mapping.ui(64);
        int jewelY = boxY + mapping.ui(256);
        int jewelSize = mapping.ui(44);
        if (mouseX < jewelX || mouseX >= jewelX + jewelSize || mouseY < jewelY || mouseY >= jewelY + jewelSize) {
            return;
        }

        int hearts = Math.max(0, Math.min(14, friendshipPoints / 250));
        String hover = hearts + "/14<";

        float scale = textScale();
        Font textFont = dialogueTextFont();
        int textWidth = Math.round(textFont.width(hover) * scale);
        int textHeight = scaledLineHeight();
        int hoverX = jewelX + jewelSize / 2 - textWidth / 2;
        int hoverY = jewelY - mapping.ui(64);
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, hoverX - mapping.ui(8), hoverY - mapping.ui(8), textWidth + mapping.ui(16), textHeight + mapping.ui(16), mapping.s4());

        graphics.pose().pushPose();
        graphics.pose().translate(hoverX, hoverY, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(textFont, hover, 0, 0, spriteTextColor(), false);
        graphics.pose().popPose();
    }

    private void drawPortraitArea(GuiGraphics graphics, DialoguePage page) {
        if (boxW < mapping.ui(642)) {
            return;
        }

        int xPositionOfPortraitArea = boxX + boxW - mapping.ui(448) + mapping.ui(4);
        int widthOfPortraitArea = boxX + boxW - xPositionOfPortraitArea;

        CommonGuiTextures.drawDialoguePortraitVerticalStrip(graphics, xPositionOfPortraitArea - mapping.ui(40), boxY, mapping.ui(36), boxH);
        CommonGuiTextures.drawDialoguePortraitDividerTop(graphics, xPositionOfPortraitArea - mapping.ui(40), boxY - mapping.ui(20), mapping.s4());
        CommonGuiTextures.drawDialoguePortraitDividerBottom(graphics, xPositionOfPortraitArea - mapping.ui(40), boxY + boxH, mapping.s4());

        int portraitBoxX = xPositionOfPortraitArea + mapping.ui(76);
        int portraitBoxY = boxY + boxH / 2 - mapping.ui(148) - mapping.ui(36);
        CommonGuiTextures.drawDialoguePortraitFrame(graphics, xPositionOfPortraitArea - mapping.ui(8), boxY, mapping.s4());

        int xOffset = newPortraitShakeTimer > 0 ? mapping.ui((int) (Math.random() * 3.0) - 1) : 0;
        drawNpcPortrait(graphics, portraitBoxX + mapping.ui(16) + xOffset, portraitBoxY + mapping.ui(24), page.portraitIndex());

        String displayName = NpcDisplayNames.translated(normalizedNpcId());
        int nameX = xPositionOfPortraitArea + widthOfPortraitArea / 2;
        int nameY = portraitBoxY + mapping.ui(312);
        drawScaledCenteredText(graphics, displayName, nameX, nameY, 0x2E251A);

        drawFriendshipJewel(graphics);
    }

    private void drawFriendshipJewel(GuiGraphics graphics) {
        int jewelX = boxX + boxW - mapping.ui(64);
        int jewelY = boxY + mapping.ui(256);
        int hearts = Math.max(0, Math.min(14, friendshipPoints / 250));

        int frame = (int) ((System.currentTimeMillis() % 1000L) / 250L);
        CommonGuiTextures.drawFriendshipJewel(graphics, jewelX, jewelY, hearts, frame, mapping.s4());
    }

    private void drawNpcPortrait(GuiGraphics graphics, int x, int y, int portraitIndex) {
        PortraitResource portrait = resolvePortraitResource();
        ResourceLocation portraitTexture = portrait.texture();
        int sheetW = portrait.sheetWidth();
        int sheetH = portrait.sheetHeight();
        int cols = Math.max(1, sheetW / 64);
        int index = Math.max(0, portraitIndex);
        int sx = (index % cols) * 64;
        int sy = (index / cols) * 64;
        if (sy + 64 > sheetH) {
            sx = 0;
            sy = 0;
        }
        graphics.blit(portraitTexture, x, y, mapping.ui(256), mapping.ui(256), sx, sy, 64, 64, sheetW, sheetH);
    }

    private PortraitResource resolvePortraitResource() {
        String id = normalizedNpcId();
        com.stardew.craft.api.v1.npc.StardewNpcDisplay display =
                com.stardew.craft.api.v1.npc.StardewNpcDisplays.resolve(id);
        ResourceLocation genericFallback = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID, "textures/entity/npc/lewis.png");
        String legacyPath =
                com.stardew.craft.client.ClientDisplayFallbacks.stablePath(
                        id, "lewis");
        ResourceLocation legacyFallback = ResourceLocation.fromNamespaceAndPath(
                StardewCraft.MODID,
                "textures/entity/npc/" + legacyPath + ".png");
        ResourceLocation availableFallback =
                com.stardew.craft.client.ClientDisplayFallbacks
                        .availableResource(
                                legacyFallback,
                                genericFallback,
                                this::hasResource);
        ResourceLocation requested =
                display == null ? null : display.portraitTexture();
        ResourceLocation resolved =
                com.stardew.craft.client.ClientDisplayFallbacks
                        .availableResource(
                                requested,
                                availableFallback,
                                this::hasResource);
        boolean usesDisplay = display != null && resolved.equals(requested);
        return loadPortrait(
                resolved,
                usesDisplay ? display.portraitSheetWidth() : 128,
                usesDisplay ? display.portraitSheetHeight() : 128);
    }

    private boolean hasResource(ResourceLocation location) {
        ResourceManager resourceManager = this.minecraft == null ? null : this.minecraft.getResourceManager();
        return resourceManager != null && resourceManager.getResource(location).isPresent();
    }

    private PortraitResource loadPortrait(ResourceLocation location, int fallbackW, int fallbackH) {
        PortraitResource cached = PORTRAIT_CACHE.get(location);
        if (cached != null) {
            return cached;
        }

        ResourceManager resourceManager = this.minecraft == null ? null : this.minecraft.getResourceManager();
        if (resourceManager == null) {
            return new PortraitResource(location, fallbackW, fallbackH);
        }

        int width = fallbackW;
        int height = fallbackH;
        try {
            var resource = resourceManager.getResource(location).orElse(null);
            if (resource != null) {
                try (var stream = resource.open(); NativeImage image = NativeImage.read(stream)) {
                    width = image.getWidth();
                    height = image.getHeight();
                }
            }
        } catch (IOException ignored) {
        }

        PortraitResource resolved = new PortraitResource(location, Math.max(64, width), Math.max(64, height));
        PORTRAIT_CACHE.put(location, resolved);
        return resolved;
    }

    private float textScale() {
        return SdvFontAdapter.scale(
                dialogueTextFont(), languageCode(), guiScale(), SdvFontAdapter.Style.SPRITE_TEXT);
    }

    private int scaledLineHeight() {
        return SdvFontAdapter.lineStep(languageCode(), guiScale(), SdvFontAdapter.Style.SPRITE_TEXT);
    }

    private int unscaledLineStep(float scale) {
        return Math.max(1, Math.round(scaledLineHeight() / Math.max(0.01F, scale)));
    }

    private Font dialogueTextFont() {
        return StardewFonts.spriteText();
    }

    private int spriteTextColor() {
        return 0xFF000000 | StardewFonts.spriteTextDefaultRgb();
    }

    private String languageCode() {
        return this.minecraft == null
                ? "en_us"
                : this.minecraft.getLanguageManager().getSelected();
    }

    private float guiScale() {
        return this.minecraft == null ? 1.0F : (float) this.minecraft.getWindow().getGuiScale();
    }

    private void drawScaledCenteredText(GuiGraphics graphics, String text, int centerX, int y, int color) {
        float scale = textScale();
        int unscaledCenterX = Math.round(centerX / scale);
        int unscaledY = Math.round(y / scale);
        Font textFont = dialogueTextFont();
        int unscaledTextWidth = textFont.width(text);

        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(textFont, text, unscaledCenterX - unscaledTextWidth / 2, unscaledY, spriteTextColor(), false);
        graphics.pose().popPose();
    }

    private String normalizedNpcId() {
        String id = this.npcId.trim().toLowerCase(Locale.ROOT);
        return id.isEmpty() ? "lewis" : id;
    }

    private void drawBox(GuiGraphics graphics, int xPos, int yPos, int boxWidth, int boxHeight) {
        if (!transitionInitialized || boxWidth <= 0 || boxHeight <= 0) {
            return;
        }
        CommonGuiTextures.drawDialogueBoxFill(graphics, xPos, yPos, boxWidth, boxHeight);
        CommonGuiTextures.drawDialogueBoxTop(graphics, xPos, yPos - mapping.ui(20), boxWidth, mapping.ui(24));
        CommonGuiTextures.drawDialogueBoxBottom(graphics, xPos + mapping.ui(12), yPos + boxHeight, Math.max(0, boxWidth - mapping.ui(20)), mapping.ui(32));
        CommonGuiTextures.drawDialogueBoxLeft(graphics, xPos - mapping.ui(32), yPos + mapping.ui(24), mapping.ui(32), Math.max(0, boxHeight - mapping.ui(28)));
        CommonGuiTextures.drawDialogueBoxRight(graphics, xPos + boxWidth, yPos, mapping.ui(28), boxHeight);

        float s4 = mapping.s4();
        CommonGuiTextures.drawDialogueQuestionCornerTl(graphics, xPos - mapping.ui(44), yPos - mapping.ui(28), s4);
        CommonGuiTextures.drawDialogueQuestionCornerTr(graphics, xPos + boxWidth - mapping.ui(8), yPos - mapping.ui(28), s4);
        CommonGuiTextures.drawDialogueQuestionCornerBr(graphics, xPos + boxWidth - mapping.ui(8), yPos + boxHeight - mapping.ui(8), s4);
        CommonGuiTextures.drawDialogueQuestionCornerBl(graphics, xPos - mapping.ui(44), yPos + boxHeight - mapping.ui(4), s4);
    }

    private void closeScreen() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.stardew.craft.network.payload.CloseNpcDialoguePayload(this.npcId)
        );
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
        // SDV afterDialogues parity: execute pending action after dialogue closes
        if (afterCloseAction != null) {
            var action = afterCloseAction;
            afterCloseAction = null;
            action.run();
        }
    }

    private void playUiSound(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }

    private record PortraitResource(ResourceLocation texture, int sheetWidth, int sheetHeight) {
    }
}
