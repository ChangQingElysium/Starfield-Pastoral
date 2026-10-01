package com.stardew.craft.emote;

import java.util.List;

import com.stardew.craft.StardewCraft;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class EmoteCatalog {

	public static final List<EmoteType> ALL = List.of(
		new EmoteType("happy", "Emote_Happy", 32, false),
		new EmoteType("sad", "Emote_Sad", 28, false),
		new EmoteType("heart", "Emote_Heart", 20, false),
		new EmoteType("exclamation", "Emote_Exclamation", 16, false),
		new EmoteType("note", "Emote_Note", 56, false),
		new EmoteType("sleep", "Emote_Sleep", 24, false),
		new EmoteType("game", "Emote_Game", 52, false),
		new EmoteType("question", "Emote_Question", 8, false),
		new EmoteType("x", "Emote_X", 36, false),
		new EmoteType("pause", "Emote_Pause", 40, false),
		new EmoteType("blush", "Emote_Blush", 60, true),
		new EmoteType("angry", "Emote_Angry", 12, false)
	);

	public static final List<EmoteType> WHEEL_ITEMS = ALL;

	private EmoteCatalog() {
	}

	public static EmoteType byId(String id) {
		for (EmoteType emote : ALL) {
			if (emote.id().equals(id)) {
				return emote;
			}
		}
		return null;
	}

	public static int getBubbleBaseIndex(EmoteType emote) {
		if (emote == null) {
			return 4;
		}
		return emote.iconIndex() >= 0 ? emote.iconIndex() : 4;
	}

	@SuppressWarnings("null")
	public static Component getChatIconComponent(EmoteType emote) {
		int frame = getBubbleBaseIndex(emote);
		char glyph = (char) (0xE300 + Math.max(0, Math.min(63, frame)));
		return Component.literal(String.valueOf(glyph)).setStyle(Style.EMPTY.withFont(new ResourceLocation(StardewCraft.MODID, "emote_icons")));
	}
}
