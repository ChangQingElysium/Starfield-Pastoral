package com.stardew.craft.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
public final class StardewHudMessageManager {
	private static final ResourceLocation HUD_MESSAGE = ResourceLocation.fromNamespaceAndPath(
		StardewCraft.MODID, "textures/gui/hud_message.png"
	);
	private static final ResourceLocation MONEY_DIGITS = ResourceLocation.fromNamespaceAndPath(
		StardewCraft.MODID, "textures/gui/hud_message_digits.png"
	);

	private static final int TEX_WIDTH = 64;
	private static final int TEX_HEIGHT = 64;
	// hud_message.png layout: normal left(0,0) mid(26,0) right(27,0) expensive left(33,0) error(0,24)
	private static final int NORMAL_LEFT_U = 0, NORMAL_LEFT_V = 0;
	private static final int EXPENSIVE_LEFT_U = 33, EXPENSIVE_LEFT_V = 0;
	private static final int BOX_W = 26, BOX_H = 24;
	private static final int MID_U = 26, MID_V = 0, MID_W = 1, MID_H = 24;
	private static final int RIGHT_U = 27, RIGHT_V = 0, RIGHT_W = 6, RIGHT_H = 24;
	private static final int ERROR_U = 0;
	private static final int ERROR_V = 24;
	private static final int ERROR_W = 16;
	private static final int ERROR_H = 16;
	private static final int DIGIT_W = 5;
	private static final int DIGIT_H = 7;
	private static final int DIGIT_TEX_W = 5;
	private static final int DIGIT_TEX_H = 70;

	private static final float VANILLA_TO_MC = 0.40f;
	private static final int BOX_HEIGHT = Math.round(112f * VANILLA_TO_MC);
	private static final float UI_SCALE = 4f * VANILLA_TO_MC;
	private static final float TEXT_SCALE = 1.12f;
	private static final int CORNER_MAX_TEXT_WIDTH = 220;
	private static final int CORNER_MIN_WIDTH = 72;
	private static final int CORNER_MIN_HEIGHT = 30;
	private static final int CORNER_HORIZONTAL_PADDING = 12;
	private static final int CORNER_VERTICAL_PADDING = 8;
	private static final int CORNER_LINE_GAP = 1;
	private static final int CORNER_STACK_GAP = 6;
	private static final int TOAST_STACK_GAP = 4;
	private static final int TEXT_OFFSET_X = Math.round(99f * VANILLA_TO_MC);
	private static final float TINY_DIGIT_SCALE = 3f * VANILLA_TO_MC;
	private static final int SEGMENT_OVERLAP = 1;
	private static final float ITEM_ICON_SCALE = 1.45f;
	private static final float ICON_NUDGE_X = -1.0f;
	private static final float ICON_NUDGE_Y = -0.5f;
	private static final int NUMBER_NUDGE_X = -2;
	private static final int NUMBER_NUDGE_Y = -2;
	private static final float POP_START_MS = 3000f;
	private static final float POP_DURATION_MS = 900f;
	private static final int TEXT_COLOR = 0xFF221122;
	private static final float DEFAULT_TIME_MS = 3500f;
	private static final float FADE_STEP = 0.02f;
	private static final float BASE_FRAME_MS = 16.6667f;
	private static long lastUpdateNanos = System.nanoTime();

	private static final List<HudMessage> MESSAGES = new ArrayList<>();

	private StardewHudMessageManager() {
	}

	@SuppressWarnings("null")
	public static void showMissingItem(Item item, String itemId, int requiredCount) {
		String safeItemId = Objects.requireNonNullElse(itemId, "");
		Component itemName = item != null
			? new ItemStack(item).getHoverName().copy().withStyle(net.minecraft.ChatFormatting.WHITE)
			: Component.literal(safeItemId).withStyle(net.minecraft.ChatFormatting.WHITE);
		Component message = Component.translatable("stardewcraft.hud.requires_item", requiredCount, itemName);
		addErrorMessage(message);
		playCancelSound();
	}

	/** 错误/警告样式 messagebox（带取消音，和「好感度不够」等提示一致）。 */
	public static void showError(Component message) {
		addErrorMessage(message);
		playCancelSound();
	}

	/** SDV {@code Game1.showGlobalMessage}: lower-left, wrapped, iconless corner textbox. */
	public static void showGlobalMessage(Component message) {
		HudMessage hudMessage = new HudMessage(message, MessageKind.CORNER_TEXTBOX);
		hudMessage.timeLeftMs = 5250f;
		addMessage(hudMessage);
	}

	@SuppressWarnings("null")
	public static void showItemPickup(ItemStack stack, int count, boolean expensive) {
		if (stack.isEmpty() || count <= 0) return;
		HudMessage message = new HudMessage(stack.getHoverName(), MessageKind.ITEM_PICKUP);
		message.messageSubject = stack.copy();
		message.typeKey = "pickup_" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		message.number = count;
		message.expensive = expensive;
		addMessage(message);
		playPickupSound();
	}

	public static void showHayHarvest(int deltaHay) {
		if (deltaHay <= 0) {
			return;
		}
		@SuppressWarnings("null")
		ItemStack hayStack = new ItemStack((ItemLike) ModItems.HAY.get());
		HudMessage message = new HudMessage(hayStack.getHoverName(), MessageKind.HAY_HARVEST);
		message.messageSubject = hayStack;
		message.typeKey = "hay";
		message.number = deltaHay;
		addMessage(message);
	}

	@SubscribeEvent
	public static void onRenderGui(RenderGuiEvent.Post event) {
		if (MESSAGES.isEmpty()) {
			return;
		}
		updateMessages();
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui || mc.screen instanceof StardewHudLayoutEditorScreen
				|| (mc.player != null && mc.player.isSpectator())) {
			return;
		}
		Font font = mc.font;
		GuiGraphics graphics = event.getGuiGraphics();
		renderNotifications(graphics, font);
	}

	/**
	 * Render every popup in one chronological stack and from one configurable anchor.
	 * The individual visual styles stay intact, but they can no longer overlap each
	 * other because each message contributes its real height to the shared stack.
	 */
	private static void renderNotifications(GuiGraphics graphics, Font font) {
		StardewHudLayout.Placement placement = StardewHudLayout.current(
			Config.HudElement.NOTIFICATIONS, graphics.guiWidth(), graphics.guiHeight());
		graphics.pose().pushPose();
		graphics.pose().translate(placement.x(), placement.y(), 0.0F);
		graphics.pose().scale(placement.scale(), placement.scale(), 1.0F);
		int heightUsed = 0;
		for (int i = MESSAGES.size() - 1; i >= 0; i--) {
			HudMessage message = MESSAGES.get(i);
			if (message.kind == MessageKind.CORNER_TEXTBOX) {
				drawCornerTextbox(graphics, font, message, heightUsed);
			} else {
				drawToastMessage(graphics, font, message, heightUsed);
			}
			heightUsed += stackStepFor(font, message);
		}
		graphics.pose().popPose();
	}

	private static int stackStepFor(Font font, HudMessage message) {
		if (message.kind == MessageKind.CORNER_TEXTBOX) {
			return cornerBoxLayout(font, message.messageText).height() + CORNER_STACK_GAP;
		}
		return BOX_HEIGHT + TOAST_STACK_GAP;
	}

	public static void renderNotificationPreview(GuiGraphics graphics, Font font, int x, int y, float scale) {
		ItemStack stack = new ItemStack(ModItems.HAY.get());
		HudMessage message = new HudMessage(stack.getHoverName(), MessageKind.ITEM_PICKUP);
		message.messageSubject = stack;
		message.number = 3;
		message.timeLeftMs = 2500.0F;
		message.transparency = 1.0F;
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0.0F);
		graphics.pose().scale(scale, scale, 1.0F);
		drawToastMessage(graphics, font, message, 0);
		graphics.pose().popPose();
	}

	private static void updateMessages() {
		long now = System.nanoTime();
		float deltaMs = (now - lastUpdateNanos) / 1_000_000f;
		lastUpdateNanos = now;
		if (deltaMs <= 0f) {
			return;
		}
		float frameFactor = deltaMs / BASE_FRAME_MS;
		Iterator<HudMessage> iterator = MESSAGES.iterator();
		while (iterator.hasNext()) {
			HudMessage message = iterator.next();
			if (message.update(deltaMs, frameFactor)) {
				iterator.remove();
			}
		}
	}

	private static void addErrorMessage(Component message) {
		HudMessage hudMessage = new HudMessage(message, MessageKind.ERROR);
		hudMessage.whatType = 3;
		addMessage(hudMessage);
	}

	private static void addMessage(HudMessage message) {
		for (HudMessage existing : MESSAGES) {
			if (message.typeKey != null && message.typeKey.equals(existing.typeKey)) {
				existing.number += Math.max(0, message.number);
				existing.timeLeftMs = DEFAULT_TIME_MS;
				existing.transparency = 1f;
				return;
			}
			if (message.whatType != 0 && message.whatType == existing.whatType && message.messageText.equals(existing.messageText)) {
				existing.reset();
				return;
			}
		}
		if (MESSAGES.isEmpty()) {
			lastUpdateNanos = System.nanoTime();
		}
		MESSAGES.add(message);
	}

	private static void drawCornerTextbox(GuiGraphics graphics, Font font,
			HudMessage message, int heightUsed) {
		CornerBoxLayout layout = cornerBoxLayout(font, message.messageText);
		int boxY = -heightUsed;

		float alpha = message.transparency;
		int alphaInt = Math.max(0, Math.min(255, Math.round(255f * alpha)));
		Minecraft minecraft = Minecraft.getInstance();
		float guiScale = minecraft.getWindow() == null ? 1.0F : (float) minecraft.getWindow().getGuiScale();
		float textureScale = 1.0F / Math.max(1.0F, guiScale);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		CommonGuiTextures.drawMenuTextureBox(graphics, 0, boxY, layout.width(), layout.height(),
			textureScale, true, alpha, Math.max(1, Math.round(2 * textureScale)));

		int textColor = (alphaInt << 24) | (TEXT_COLOR & 0xFFFFFF);
		int drawY = boxY + (layout.height() - layout.textHeight()) / 2;
		for (FormattedCharSequence line : layout.lines()) {
			int drawX = (layout.width() - font.width(line)) / 2;
			graphics.drawString(font, line, drawX, drawY, textColor, false);
			drawY += font.lineHeight + CORNER_LINE_GAP;
		}
		RenderSystem.disableBlend();
	}

	private static CornerBoxLayout cornerBoxLayout(Font font, String text) {
		List<FormattedCharSequence> lines = font.split(Component.literal(text), CORNER_MAX_TEXT_WIDTH);
		if (lines.isEmpty()) {
			lines = List.of(FormattedCharSequence.EMPTY);
		}
		int textWidth = 0;
		for (FormattedCharSequence line : lines) {
			textWidth = Math.max(textWidth, font.width(line));
		}
		int textHeight = lines.size() * font.lineHeight + Math.max(0, lines.size() - 1) * CORNER_LINE_GAP;
		int width = Math.max(CORNER_MIN_WIDTH, textWidth + CORNER_HORIZONTAL_PADDING * 2);
		int height = Math.max(CORNER_MIN_HEIGHT, textHeight + CORNER_VERTICAL_PADDING * 2);
		return new CornerBoxLayout(lines, width, height, textHeight);
	}

	private static void drawToastMessage(GuiGraphics graphics, Font font, HudMessage message, int heightUsed) {
		float boxLeft = 0;
		float boxTop = -heightUsed;

		float alpha = message.transparency;
		int alphaInt = Math.max(0, Math.min(255, Math.round(255f * alpha)));

		RenderSystem.enableBlend();

		   // SDV: expensive/normal use different box regions from cursors.png
		   int leftU = message.expensive ? EXPENSIVE_LEFT_U : NORMAL_LEFT_U;
		   int leftV = message.expensive ? EXPENSIVE_LEFT_V : NORMAL_LEFT_V;
		   int leftW = Math.round(BOX_W * UI_SCALE);
		   int messageWidth = Math.max(1, Math.round(font.width(message.messageText) * TEXT_SCALE) + 2);
		   int baseX = Math.round(boxLeft);
		   int baseY = Math.round(boxTop);

		   int midX = baseX + leftW - SEGMENT_OVERLAP;
		   int midW = messageWidth + SEGMENT_OVERLAP;
		   int rightX = midX + midW - SEGMENT_OVERLAP;

		   drawScaledRegion(graphics, baseX, baseY, leftU, leftV, BOX_W, BOX_H, UI_SCALE, UI_SCALE);
		   drawScaledRegion(graphics, midX, baseY, MID_U, MID_V, MID_W, MID_H, midW, UI_SCALE);
		   drawScaledRegion(graphics, rightX, baseY, RIGHT_U, RIGHT_V, RIGHT_W, RIGHT_H, UI_SCALE, UI_SCALE);

		   // Reset color after box drawing
		   RenderSystem.setShaderColor(1f, 1f, 1f, alpha);

		float iconAreaX = boxLeft;
		float iconAreaY = boxTop;
		float iconAreaW = leftW;
		float iconAreaH = Math.round(BOX_H * UI_SCALE);
		float iconCenterX = iconAreaX + iconAreaW * 0.5f + ICON_NUDGE_X;
		float iconCenterY = iconAreaY + iconAreaH * 0.5f + ICON_NUDGE_Y;
		float popScale = UI_SCALE + Math.max(0f, (message.timeLeftMs - POP_START_MS) / POP_DURATION_MS);
		float itemScale = ITEM_ICON_SCALE * Math.max(1f, popScale / UI_SCALE);

		if (message.messageSubject != null) {
			int itemSize = CommonGuiTextures.itemSize(itemScale);
			CommonGuiTextures.drawItem(graphics, message.messageSubject, Math.round(iconCenterX - itemSize / 2.0f), Math.round(iconCenterY - itemSize / 2.0f), itemScale);
		} else if (message.whatType == 3) {
			graphics.pose().pushPose();
			graphics.pose().translate(iconCenterX, iconCenterY, 0f);
			graphics.pose().scale(popScale * 0.9f, popScale * 0.9f, 1f);
			graphics.blit(HUD_MESSAGE, -8, -8, ERROR_U, ERROR_V, ERROR_W, ERROR_H, TEX_WIDTH, TEX_HEIGHT);
			graphics.pose().popPose();
		}

		if (message.number > 1) {
			int panelW = Math.round(iconAreaW);
			int panelH = Math.round(iconAreaH);
			int digitStep = Math.round(DIGIT_W * TINY_DIGIT_SCALE) - 1;
			int digitsWidth = Math.max(0, Integer.toString(message.number).length() * digitStep + 1);
			int numberX = Math.round(iconAreaX) + panelW - digitsWidth - 2 + NUMBER_NUDGE_X;
			int numberY = Math.round(iconAreaY) + panelH - Math.round(DIGIT_H * TINY_DIGIT_SCALE) - 1 + NUMBER_NUDGE_Y;
			drawTinyDigits(
				graphics,
				message.number,
				numberX,
				numberY,
				alpha
			);
		}

		float textX = Math.round(boxLeft) + TEXT_OFFSET_X;
		float textY = Math.round(boxTop + (Math.round(BOX_H * UI_SCALE) - font.lineHeight * TEXT_SCALE) * 0.5f);
		int textColor = (alphaInt << 24) | (TEXT_COLOR & 0xFFFFFF);
		graphics.pose().pushPose();
		graphics.pose().translate(textX, textY, 0f);
		graphics.pose().scale(TEXT_SCALE, TEXT_SCALE, 1f);
		// 用纯字符串渲染 — 忽略 Component 里嵌入的样式（如物品名默认白色），
		// 让 TEXT_COLOR（深棕）统一生效。
		graphics.drawString(font, message.messageText, 0, 0, textColor, false);
		graphics.pose().popPose();

		RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
		RenderSystem.disableBlend();
	}

	@SuppressWarnings("null")
	private static void drawScaledRegion(GuiGraphics graphics, float x, float y, int u, int v, int width, int height, float scaleX, float scaleY) {
		graphics.pose().pushPose();
		graphics.pose().translate(x, y, 0f);
		graphics.pose().scale(scaleX, scaleY, 1f);
		graphics.blit(HUD_MESSAGE, 0, 0, u, v, width, height, TEX_WIDTH, TEX_HEIGHT);
		graphics.pose().popPose();
	}

	@SuppressWarnings("null")
	private static void drawTinyDigits(GuiGraphics graphics, int number, int x, int y, float alpha) {
		String text = Integer.toString(number);
		int cursorX = x;
		for (int i = 0; i < text.length(); i++) {
			char ch = text.charAt(i);
			if (ch < '0' || ch > '9') {
				continue;
			}
			int digit = ch - '0';
			int sourceY = digit * DIGIT_H;
			graphics.pose().pushPose();
			graphics.pose().translate(cursorX, y, 0f);
			graphics.pose().scale(TINY_DIGIT_SCALE, TINY_DIGIT_SCALE, 1f);
			RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
			graphics.blit(MONEY_DIGITS, 0, 0, 0, sourceY, DIGIT_W, DIGIT_H, DIGIT_TEX_W, DIGIT_TEX_H);
			graphics.pose().popPose();
			cursorX += Math.round(DIGIT_W * TINY_DIGIT_SCALE) - 1;
		}
	}

	private static void playCancelSound() {
		Minecraft mc = Minecraft.getInstance();
		var player = mc.player;
		if (player == null) {
			return;
		}
		var sound = ModSounds.CANCEL.get();
		if (sound == null) {
			return;
		}
		player.playSound(sound, 1.0f, 1.0f);
	}

	@SuppressWarnings("null")
	private static void playPickupSound() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.4f, 1.0f);
		}
	}

	private record CornerBoxLayout(List<FormattedCharSequence> lines, int width, int height, int textHeight) {
	}

	private static final class HudMessage {
		private final String messageText;
		private final MessageKind kind;
		private String typeKey;
		private int number = -1;
		private int whatType;
		private ItemStack messageSubject;
		private float timeLeftMs = DEFAULT_TIME_MS;
		private float transparency = 1f;
		private boolean expensive;

		private HudMessage(Component message, MessageKind kind) {
			Objects.requireNonNull(message, "message");
			this.kind = Objects.requireNonNull(kind, "kind");
			String raw = message.getString();
			String stripped = net.minecraft.ChatFormatting.stripFormatting(raw);
			this.messageText = stripped != null ? stripped : raw;
		}

		private void reset() {
			this.timeLeftMs = DEFAULT_TIME_MS;
			this.transparency = 1f;
		}

		private boolean update(float deltaMs, float frameFactor) {
			timeLeftMs -= deltaMs;
			if (timeLeftMs < 0f) {
				transparency -= FADE_STEP * frameFactor;
				return transparency < 0f;
			}
			if (transparency < 1f) {
				transparency = Math.min(transparency + FADE_STEP * frameFactor, 1f);
			}
			return false;
		}
	}

	private enum MessageKind {
		ERROR,
		CORNER_TEXTBOX,
		HAY_HARVEST,
		ITEM_PICKUP
	}
}
