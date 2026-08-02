package com.stardew.craft.network.payload;

import com.stardew.craft.StardewCraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Server → Client: open the NPC dialogue screen.
 * Optionally carries an afterCloseItemId: if non-empty, the dialogue screen's
 * close callback will trigger a hold-up animation + HUD pickup notification
 * for that item (SDV holdUpItemThenMessage parity).
 */
@SuppressWarnings("null")
public record OpenNpcDialogueScreenPayload(
        String npcId,
        String translateKey,
        int friendshipPoints,
        String afterCloseItemId,
        boolean garbleDwarvish,
        List<String> answeredDialogueIds
) implements CustomPacketPayload {
    public static final String DIRECT_DIALOGUE_PREFIX = "__direct_dialogue::";

    /** Encodes literal text for the existing client-side NPC dialogue pipeline. */
    public static String directText(String text) {
        return DIRECT_DIALOGUE_PREFIX + encodeDirectDialogue(text);
    }

    /** Convenience: no afterClose action, no garble. */
    public OpenNpcDialogueScreenPayload(String npcId, String translateKey, int friendshipPoints) {
        this(npcId, translateKey, friendshipPoints, "", false, List.of());
    }

    public OpenNpcDialogueScreenPayload(
            String npcId,
            String translateKey,
            int friendshipPoints,
            String afterCloseItemId,
            boolean garbleDwarvish
    ) {
        this(npcId, translateKey, friendshipPoints, afterCloseItemId, garbleDwarvish, List.of());
    }

    public OpenNpcDialogueScreenPayload {
        answeredDialogueIds = answeredDialogueIds == null ? List.of() : List.copyOf(answeredDialogueIds);
    }

    @SuppressWarnings("null")
    public static final Type<OpenNpcDialogueScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "open_npc_dialogue_screen"));

    @SuppressWarnings("null")
    public static final StreamCodec<FriendlyByteBuf, OpenNpcDialogueScreenPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeUtf(payload.npcId(), 64);
            buf.writeUtf(payload.translateKey(), 32767);
            buf.writeInt(payload.friendshipPoints());
            buf.writeUtf(payload.afterCloseItemId(), 256);
            buf.writeBoolean(payload.garbleDwarvish());
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list())
                    .encode(buf, payload.answeredDialogueIds());
        },
        buf -> new OpenNpcDialogueScreenPayload(
            buf.readUtf(64), buf.readUtf(32767), buf.readInt(), buf.readUtf(256), buf.readBoolean(),
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenNpcDialogueScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> handleClient(payload));
    }

    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private static void handleClient(OpenNpcDialogueScreenPayload payload) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        String rawKey = payload.translateKey();
        StringBuilder prefixBuilder = new StringBuilder();
        // Extract all $x prefixes directly mapped to the translatable key.
        // SDV emotion tokens are always $ + single letter, e.g. "$h", "$s", "$b".
        // E.g., "$h$bstardewcraft.key" -> prefix="$h$b", trueKey="stardewcraft.key"
        int i = 0;
        while (i + 1 < rawKey.length() && rawKey.charAt(i) == '$') {
            char next = rawKey.charAt(i + 1);
            if (!Character.isLetter(next)) break; // '$' not followed by letter -> end
            prefixBuilder.append('$').append(next);
            i += 2;
        }

        String trueKey = rawKey.substring(i);
        String displayText = resolveClientTextSource(trueKey);

        String finalDisplayText = prefixBuilder.toString() + displayText;
        finalDisplayText = resolvePreviouslyAnsweredQuestion(
                finalDisplayText, payload.npcId(), payload.answeredDialogueIds());

        // Resolve player profile tokens (@, gender branches, %favorite, etc.).
        String accountName = mc.player.getGameProfile() != null ? mc.player.getGameProfile().getName() : "player";
        String playerName = com.stardew.craft.client.ClientPlayerDataCache.getPlayerDisplayName(accountName);
        finalDisplayText = resolvePlayerDialogueText(
                finalDisplayText, playerName, payload.answeredDialogueIds());

        // SDV parity: garble resolved text to Dwarvish if player can't understand
        if (payload.garbleDwarvish()) {
            finalDisplayText = com.stardew.craft.shop.DwarfService.convertToDwarvish(finalDisplayText);
        }

        com.stardew.craft.client.gui.common.StardewNpcDialogueScreen screen = new com.stardew.craft.client.gui.common.StardewNpcDialogueScreen(
            payload.npcId(), finalDisplayText, payload.friendshipPoints());

        // If afterCloseItemId is set, trigger hold-up animation + HUD on dialogue close
        String afterItemId = payload.afterCloseItemId();
        if (afterItemId != null && !afterItemId.isEmpty()) {
            screen.withAfterClose(() -> {
                // SDV holdUpItemThenMessage: play totem animation + HUD after dialogue closes
                com.stardew.craft.client.hud.HoldUpItemHandler.play(afterItemId);
                try {
                    ResourceLocation rl = ResourceLocation.parse(afterItemId);
                    Item item = BuiltInRegistries.ITEM.get(rl);
                    if (item != null && item != Items.AIR) {
                        com.stardew.craft.client.hud.StardewHudMessageManager.showItemPickup(new ItemStack(item), 1, false);
                    }
                } catch (Exception ignored) {}
            });
        }

        mc.setScreen(screen);
    }

    /** Mirrors Dialogue.parseDialogueString's already-seen $q redirect. */
    static String resolvePreviouslyAnsweredQuestion(
            String text,
            String npcId,
            List<String> answeredDialogueIds
    ) {
        if (text == null || answeredDialogueIds == null || answeredDialogueIds.isEmpty()) {
            return text;
        }
        int questionIndex = text.indexOf("$q ");
        if (questionIndex < 0) {
            return text;
        }
        int headerEnd = text.indexOf('#', questionIndex);
        if (headerEnd < 0) {
            return text;
        }
        String[] header = text.substring(questionIndex + 3, headerEnd).trim().split("\\s+");
        if (header.length < 2 || header[0].equals("-1")) {
            return text;
        }
        boolean seen = false;
        for (String id : header[0].split("/")) {
            if (answeredDialogueIds.contains(id)) {
                seen = true;
                break;
            }
        }
        if (!seen) {
            return text;
        }

        String beforeQuestion = text.substring(0, questionIndex);
        String redirect = header[1];
        if (redirect.equals("null") || npcId == null || npcId.isBlank()) {
            return beforeQuestion;
        }
        String translationKey = "stardewcraft.npc."
                + npcId.toLowerCase(Locale.ROOT)
                + "."
                + redirect.toLowerCase(Locale.ROOT);
        String redirectedText = rawTranslation(translationKey);
        return redirectedText.equals(translationKey) ? beforeQuestion : redirectedText;
    }

    /**
     * Resolves {@code ${male^female}$} inline gender tokens.
     * @param text  raw dialogue text
     * @param male  true to pick male variant, false for female
     */
    public static String resolveInlineGenderTokens(String text, boolean male) {
        return com.stardew.craft.client.PlayerGenderText.resolve(text, male);
    }

    /**
     * Resolves top-level {@code ^} gender split: {@code maleText^femaleText}.
     * Only splits on `^` that is NOT inside a {@code $q/$r} question block or
     * a {@code ${...}$} inline token.  In practice SDV uses at most one
     * top-level {@code ^} per dialogue string.
     *
     * @param text  raw dialogue text (inline tokens should already be resolved)
     * @param male  true to keep male half
     */
    public static String resolveGenderSplit(String text, boolean male) {
        if (text == null || (!text.contains("^") && !text.contains("¦"))) return text;
        // Don't split inside $q/$r question blocks — those use ^ differently
        // Find the first ^ that is not inside ${...}$
        int depth = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '$' && i + 1 < text.length() && text.charAt(i + 1) == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}' && i + 1 < text.length() && text.charAt(i + 1) == '$') {
                depth = Math.max(0, depth - 1);
                i++;
                continue;
            }
            if ((c == '^' || c == '¦') && depth == 0) {
                return male ? text.substring(0, i) : text.substring(i + 1);
            }
        }
        return text;
    }

    /** Applies the shared player-dependent dialogue pipeline for NPC and cutscene text. */
    public static String resolvePlayerDialogueText(String text, String fallbackPlayerName) {
        return resolvePlayerDialogueText(text, fallbackPlayerName, List.of());
    }

    public static String resolvePlayerDialogueText(
            String text,
            String fallbackPlayerName,
            List<String> answeredDialogueIds
    ) {
        String fallback = fallbackPlayerName == null || fallbackPlayerName.isBlank()
                ? "player" : fallbackPlayerName;
        String playerName = com.stardew.craft.client.ClientPlayerDataCache.getPlayerDisplayName(fallback);
        boolean male = com.stardew.craft.client.ClientPlayerDataCache.isPlayerMale();
        String resolved = text == null ? "" : text.replace("@", playerName);
        resolved = resolveInlineGenderTokens(resolved, male);
        resolved = resolveGenderSplit(resolved, male);
        resolved = resolveDialogueCommands(resolved, answeredDialogueIds);
        return resolvePercentTokens(resolved, playerName);
    }

    // ── SDV word lists (from Dialogue.cs) ──────────────────────────────────
    private static final String[] ADJECTIVES = {
        "Purple", "Glistening", "Powerful", "Loud", "Crumbling", "Psychedelic",
        "Greasy", "Starving", "Giggly", "Red", "Sparkling", "Rough", "Wet",
        "Electric", "Cool", "Icy", "Slimy", "Moldy", "Dandy", "Holy"
    };
    private static final String[] NOUNS = {
        "Pumpkin", "Superhero", "Octopus", "Dumpling", "Glass", "Stone",
        "ghost", "skull", "monster", "pillow", "shirt", "candle", "bone",
        "cloud", "fairy", "shoe", "tree", "pickle", "egg", "turtle",
        "demon", "potato", "hippo"
    };
    private static final String[] PLACES = {
        "Castle Village", "Cowboy Junction", "Glitter City", "Zuzu City",
        "Emerald Farm", "Crystal Lake", "Grampleton", "Dusty Meadows",
        "Lilac Ridge", "Pine Gap", "Red Point", "Fern Islands"
    };
    private static final String[] RANDOM_NAMES = {
        "Abby", "Penny", "Mona", "Gerry", "Sam", "Wren", "Ruby",
        "Kim", "Lee", "Pat", "Jamie", "Robin", "Sage", "Dana"
    };

    private static String randomFrom(String[] arr) {
        return arr[ThreadLocalRandom.current().nextInt(arr.length)];
    }

    // ── $ Control token resolution ─────────────────────────────────────────

    /**
     * Resolves SDV dialogue control tokens: {@code $c, $d, $p, $1, $k, $query, $y}.
     * Also converts {@code #} page separators to {@code $b} for parseChunks.
     * Preserves {@code $q/$r} blocks untouched (parseChunks handles those).
     */
    public static String resolveDialogueCommands(String text) {
        return resolveDialogueCommands(text, List.of());
    }

    public static String resolveDialogueCommands(String text, List<String> answeredDialogueIds) {
        if (text == null || text.isEmpty()) return text;

        // SDV || random dialogue selection: pick one alternative at random
        if (text.contains("||")) {
            // Preserve $q portion first (it may contain || in response text)
            String qSafe = "";
            int qCheck = text.indexOf("$q ");
            if (qCheck >= 0) {
                qSafe = text.substring(qCheck);
                text = text.substring(0, qCheck);
            }
            if (text.contains("||")) {
                String[] alternatives = text.split("\\|\\|");
                text = alternatives[Math.floorMod(dialogueWeekIndex(), alternatives.length)].trim();
            }
            if (!qSafe.isEmpty()) {
                text = text + qSafe;
            }
        }

        // Handle $y (quick inline question) — uses ' delimiters, not #
        text = resolveQuickResponse(text);

        // Separate $q/$r portion (parseChunks uses # internally for $q/$r parsing)
        String qPortion = "";
        int qIdx = text.indexOf("$q ");
        if (qIdx >= 0) {
            int splitAt = qIdx;
            if (splitAt > 0 && text.charAt(splitAt - 1) == '#') splitAt--;
            qPortion = text.substring(splitAt);
            text = text.substring(0, splitAt);
        }

        // Process #-delimited segments with $ commands
        if (text.contains("#")) {
            text = processHashSegments(text, answeredDialogueIds == null ? List.of() : answeredDialogueIds);
        }

        // Handle remaining $k (kill/truncate) within $b segments
        if (text.contains("$k")) {
            String[] parts = text.split("\\$b");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                int ki = part.indexOf("$k");
                String clean = ki >= 0 ? part.substring(0, ki) : part;
                clean = clean.trim();
                if (!clean.isEmpty()) {
                    if (sb.length() > 0) sb.append("$b");
                    sb.append(clean);
                }
            }
            text = sb.toString();
        }

        // Strip remaining unhandled $ tokens as safety net
        text = text.replaceAll("\\$1\\s+\\w+", "");
        text = text.replaceAll("\\$c\\s+[\\d.]+", "");
        text = text.replaceAll("\\$d\\s+\\w+", "");
        text = text.replaceAll("\\$p\\s+[\\d,]+", "");
        text = text.replaceAll("\\$(?:query|action)\\b[^$#|]*", "");
        text = text.replaceAll("\\$[ktvKTV]\\b", "");

        // Strip escaped pipe \| (data entry error, should be |)
        text = text.replace("\\|", "");

        // Strip any remaining || random separators that slipped through
        text = text.replace("||", "");

        // Resolve any remaining single | as branch separator (keep first branch)
        if (text.contains("|")) {
            // Process per-page-break ($b) segment so we don't merge pages
            String[] pages = text.split("\\$b");
            StringBuilder sb = new StringBuilder();
            for (String page : pages) {
                int pipeIdx = page.indexOf('|');
                String clean = pipeIdx >= 0 ? page.substring(0, pipeIdx) : page;
                if (sb.length() > 0) sb.append("$b");
                sb.append(clean);
            }
            text = sb.toString();
        }

        // Re-append $q/$r portion verbatim — client StardewNpcDialogueScreen.parseChunks
        // owns the interactive question/answer parsing.
        if (!qPortion.isEmpty()) {
            if (!text.isEmpty()) text += "$b";
            text += qPortion;
        }

        return text;
    }

    /**
     * Process #-delimited segments, resolving $c/$d/$p/$1/$query commands.
     * Returns text with # replaced by $b page breaks.
     */
    private static String processHashSegments(String text, List<String> answeredDialogueIds) {
        String[] segments = text.split("#", -1);
        List<String> output = new ArrayList<>();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int i = 0;
        while (i < segments.length) {
            String seg = segments[i].trim();
            if (seg.isEmpty()) { i++; continue; }

            // ── $c <chance> — random branch ──
            if (seg.matches("\\$c\\s+[\\d.]+")) {
                float chance = 0.5f;
                try { chance = Float.parseFloat(seg.substring(3).trim()); }
                catch (NumberFormatException ignored) {}
                i++; // move past $c to win segment
                boolean win = rng.nextFloat() < chance;
                if (win) {
                    if (i < segments.length) { output.add(segments[i]); i++; }
                    // skip lose segment (unless it's another chained $c)
                    if (i < segments.length && !segments[i].trim().startsWith("$c ")) i++;
                } else {
                    if (i < segments.length) i++; // skip win segment
                    // lose: next iteration handles next segment (might be chained $c)
                }
                continue;
            }

            // ── $1 <mailId> — one-time dialogue → show first-time text ──
            if (seg.matches("\\$1\\s+\\w+")) {
                String messageId = seg.substring(3).trim().split("\\s+", 2)[0];
                if (com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(messageId)) {
                    // Dialogue.cs: skip command, first-time line and $e, then
                    // continue parsing every fallback page.
                    i += 3;
                    continue;
                }
                if (i + 1 < segments.length) {
                    output.add(segments[i + 1]);
                }
                com.stardew.craft.client.ClientPlayerDataCache.markMailFlagLocal(messageId);
                net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                        new MarkNpcDialogueFlagPayload(messageId));
                i = segments.length;
                continue;
            }

            // ── $d <state> — world state branch ──
            if (seg.matches("\\$d\\s+\\w+")) {
                String state = seg.substring(3).trim().toLowerCase(Locale.ROOT);
                i++; // skip command
                if (i < segments.length) {
                    boolean condTrue = dialogueWorldState(state);
                    output.add(pickPipeBranch(segments[i], condTrue));
                    i++;
                }
                continue;
            }

            // ── $p <ids> — prerequisite check → default to first branch ──
            if (seg.matches("\\$p\\s+[\\d\\s,]+")) {
                String[] prerequisiteIds = seg.substring(3).trim().split("[\\s,]+");
                boolean answered = false;
                for (String prerequisiteId : prerequisiteIds) {
                    if (answeredDialogueIds.contains(prerequisiteId)) {
                        answered = true;
                        break;
                    }
                }
                i++; // skip command
                if (i < segments.length) {
                    output.add(pickPipeBranch(segments[i], answered));
                    i++;
                }
                continue;
            }

            // ── $query <condition> — game state query ──
            if (seg.startsWith("$query ")) {
                boolean condTrue = true;
                // If condition mentions married/roommate, default false
                if (seg.contains("married") || seg.contains("roommate")) condTrue = false;
                i++; // skip command
                if (i < segments.length) {
                    output.add(pickPipeBranch(segments[i], condTrue));
                    i++;
                }
                // skip $e if present
                if (i < segments.length && segments[i].trim().equals("$e")) i++;
                continue;
            }

            // ── $e — end marker ──
            if (seg.equals("$e")) { i++; continue; }

            // ── Normal text segment ──
            output.add(seg);
            i++;
        }

        return String.join("$b", output);
    }

    private static int dialogueWeekIndex() {
        com.stardew.craft.time.StardewTimeManager time =
                com.stardew.craft.client.hud.StardewTimeHud.getClientTimeCache();
        if (time == null) {
            return 0;
        }
        int daysPlayed = Math.max(0,
                (time.getCurrentYear() - 1) * 112
                        + time.getCurrentSeason() * 28
                        + time.getCurrentDay() - 1);
        return daysPlayed / 7;
    }

    private static boolean dialogueWorldState(String state) {
        return switch (state) {
            case "joja" -> !com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.communitycenter.state.CCStoryFlags.CC_IS_COMPLETE);
            case "cc", "communitycenter" ->
                    com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                            com.stardew.craft.communitycenter.state.CCStoryFlags.CC_DOOR_UNLOCKED)
                            && !com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                                    com.stardew.craft.communitycenter.state.CCStoryFlags.JOJA_MEMBER);
            case "bus" -> com.stardew.craft.client.ClientPlayerDataCache.hasMailFlag(
                    com.stardew.craft.communitycenter.state.CCStoryFlags.CC_VAULT);
            case "kent" -> {
                com.stardew.craft.time.StardewTimeManager time =
                        com.stardew.craft.client.hud.StardewTimeHud.getClientTimeCache();
                yield time != null && time.getCurrentYear() >= 2;
            }
            default -> false;
        };
    }

    /**
     * Resolves {@code $y 'question_opt1_reply1_opt2_reply2'} tokens.
     * Converts SDV quick questions into the existing $q/$r question flow.
     */
    private static String resolveQuickResponse(String text) {
        if (text == null || !text.contains("$y ")) return text;
        int start = text.indexOf("$y '");
        if (start < 0) return text;
        int end = text.indexOf("'", start + 4);
        if (end < 0) return text;

        String before = text.substring(0, start);
        String after = end + 1 < text.length() ? text.substring(end + 1) : "";
        String content = text.substring(start + 4, end);

        String[] parts = content.split("_");
        if (parts.length < 3) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(before);
        sb.append("$q 0 null #").append(parts[0]);
        for (int i = 1; i + 1 < parts.length; i += 2) {
            sb.append("#$r 0 0 ")
                .append(DIRECT_DIALOGUE_PREFIX)
                .append(encodeDirectDialogue(parts[i + 1]))
                .append("#")
                .append(parts[i]);
        }
        sb.append(after);
        return sb.toString();
    }

    private static String encodeDirectDialogue(String text) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString((text == null ? "" : text).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Picks the first or second branch from a pipe-separated string.
     * {@code "textA|textB"} → textA if first==true, textB otherwise.
     */
    private static String pickPipeBranch(String text, boolean first) {
        if (text == null) return "";
        int pipe = text.indexOf('|');
        if (pipe < 0) return text;
        return first ? text.substring(0, pipe) : text.substring(pipe + 1);
    }

    // ── % Substitution token resolution ────────────────────────────────────

    /**
     * Resolves all {@code %token} substitutions in dialogue text.
     * Tokens that require systems not yet implemented are replaced with
     * sensible defaults or stripped.
     */
    public static String resolvePercentTokens(String text, String playerName) {
        if (text == null || !text.contains("%")) return text;

        // %farm → farm name from client cache, fallback to player name
        if (hasToken(text, "farm")) {
            String farmName = com.stardew.craft.client.ClientPlayerDataCache.getFarmName();
            if (farmName == null || farmName.isBlank()) farmName = playerName;
            text = replaceToken(text, "farm", farmName);
        }

        // %firstnameletter → first half of player name (SDV parity)
        if (hasToken(text, "firstnameletter")) {
            String half = playerName.length() > 1
                ? playerName.substring(0, playerName.length() / 2) : playerName;
            text = replaceToken(text, "firstnameletter", half);
        }

        // %season → derive from MC client level dayTime
        if (hasToken(text, "season")) {
            text = replaceToken(text, "season", getClientSeason());
        }

        // %time → current game time
        if (hasToken(text, "time")) {
            text = replaceToken(text, "time", getClientTime());
        }

        // %year → game year
        if (hasToken(text, "year")) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            int year = mc.level != null ? (int)(mc.level.getDayTime() / 24000 / 112) + 1 : 1;
            text = replaceToken(text, "year", String.valueOf(year));
        }

        // Random word tokens (SDV Dialogue.cs parity)
        if (hasToken(text, "adj"))   text = replaceToken(text, "adj", randomFrom(ADJECTIVES));
        if (hasToken(text, "noun"))  text = replaceToken(text, "noun", randomFrom(NOUNS));
        if (hasToken(text, "place")) text = replaceToken(text, "place", randomFrom(PLACES));
        if (hasToken(text, "name"))  text = replaceToken(text, "name", randomFrom(RANDOM_NAMES));

        // Tokens without backing systems → generic fallback text
        text = replaceToken(text, "pet", "your pet");
        text = replaceToken(text, "spouse", "your partner");
        text = replaceToken(text, "kid1", "your child");
        text = replaceToken(text, "kid2", "your child");
        String favoriteThing = com.stardew.craft.client.ClientPlayerDataCache.getFavoriteThing();
        text = replaceToken(text, "favorite",
                favoriteThing == null || favoriteThing.isBlank() ? "something special" : favoriteThing);
        text = replaceToken(text, "band", "The Stardew Band");
        text = replaceToken(text, "book", "Blue Tower");

        // Behaviour / event tokens → strip (no visible text)
        text = replaceToken(text, "noturn", "");
        text = replaceToken(text, "fork", "");
        text = text.replaceAll("%(?:r|s)evealtaste[:\\w]*", "");

        return text;
    }

    private static boolean hasToken(String text, String token) {
        return text.contains("%" + token) || text.contains(corruptedPercentToken(token));
    }

    private static String replaceToken(String text, String token, String replacement) {
        String value = replacement == null ? "" : replacement;
        return text.replace("%" + token, value)
                .replace(corruptedPercentToken(token), value);
    }

    private static String corruptedPercentToken(String token) {
        if (token == null || token.isEmpty()) {
            return "%s";
        }
        return "%s" + token.substring(1);
    }

    public static String rawTranslation(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        if (key.startsWith(DIRECT_DIALOGUE_PREFIX)) {
            try {
                byte[] decoded = java.util.Base64.getUrlDecoder().decode(key.substring(DIRECT_DIALOGUE_PREFIX.length()));
                return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                return "";
            }
        }
        return net.minecraft.locale.Language.getInstance().getOrDefault(key);
    }

    /** Resolve either a serialized Component or a normal translation key on the client. */
    public static String resolveClientTextSource(String source) {
        if (source == null || source.isBlank()) return "";
        if (source.startsWith(DIRECT_DIALOGUE_PREFIX)) {
            return rawTranslation(source);
        }
        String trimmed = source.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null) {
                try {
                    Component component = Component.Serializer.fromJson(trimmed, mc.level.registryAccess());
                    if (component != null) return component.getString();
                } catch (Exception ignored) {
                    // Fall through for legacy plain strings.
                }
            }
        }
        return rawTranslation(source);
    }

    private static String getClientSeason() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return "Spring";
        long days = mc.level.getDayTime() / 24000;
        return switch ((int) ((days / 28) % 4)) {
            case 1 -> "Summer"; case 2 -> "Fall"; case 3 -> "Winter"; default -> "Spring";
        };
    }

    private static String getClientTime() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return "12:00 PM";
        long tick = mc.level.getDayTime() % 24000;
        int totalMins = (int) (tick * 1440.0 / 24000.0);
        int hour = (totalMins / 60 + 6) % 24;
        int min = totalMins % 60;
        String ampm = hour >= 12 ? "PM" : "AM";
        int h12 = hour % 12;
        if (h12 == 0) h12 = 12;
        return h12 + ":" + String.format(Locale.ROOT, "%02d", min) + " " + ampm;
    }
}
