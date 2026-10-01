package com.stardew.craft.shop;

import com.stardew.craft.entity.npc.StardewNpcEntity;
import com.stardew.craft.network.payload.OpenShopScreenPayload;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.player.PlayerStardewData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Server-side handler for the Dwarf NPC in the mine entrance (floor 0).
 * SDV parity: Dwarf only speaks/opens shop if player has the
 * "HasDwarvishTranslationGuide" mail flag.  Without it, dialogue is
 * converted to garbled Dwarvish text.
 */
@SuppressWarnings("null")
public final class DwarfService {

    /** Mail flag set when the player obtains the Dwarvish Translation Guide. */
    public static final String MAIL_FLAG = "HasDwarvishTranslationGuide";
    public static final String SPECIAL_ITEM_ID = "stardewcraft:dwarvish_translation_guide";

    private DwarfService() {}

    /**
     * Check if the player can understand Dwarvish (any player in the session
     * has obtained the Dwarvish Translation Guide → set the mail flag).
     */
    public static boolean canUnderstandDwarves(ServerPlayer player) {
        PlayerStardewData data = PlayerDataManager.getPlayerData(player);
        return data.hasMailFlag(MAIL_FLAG) || data.hasSpecialItem(SPECIAL_ITEM_ID);
    }

    /**
     * Handle right-click on the Dwarf NPC.
     * If the player can understand Dwarvish → open shop.
     * If not → the caller (NpcInteractionService) will convert dialogue to Dwarvish.
     */
    public static InteractionResult handleDwarfInteraction(ServerPlayer player, StardewNpcEntity dwarf) {
        dwarf.setYRot(180f);
        dwarf.setYHeadRot(180f);

        if (!canUnderstandDwarves(player)) {
            // Caller will handle showing garbled dialogue
            return InteractionResult.PASS;
        }

        openDwarfShop(player);
        return InteractionResult.SUCCESS;
    }

    private static void openDwarfShop(ServerPlayer player) {
        ShopRegistry.ShopDefinition shop = ShopRegistry.get("DwarfShop");
        if (shop == null) return;

        int money = com.stardew.craft.player.PlayerStardewDataAPI.getMoney(player);
        List<ShopItemEntry> items = ShopRegistry.getFilteredItemsForPlayer("DwarfShop", shop, player);

        OpenShopScreenPayload payload = new OpenShopScreenPayload(
            "DwarfShop", money, items,
            shop.ownerNpcId(), shop.ownerDialogue(),
            new ArrayList<>(shop.acceptedSellTypes())
        );
        PacketDistributor.sendToPlayer(player, payload);
    }

    /**
     * Convert readable text to garbled "Dwarvish" letters.
     * SDV parity: exact replication of Dialogue.convertToDwarvish() from SDV source.
     * Detects client language to choose zh vs en branch (matching SDV's LanguageCode check).
     */
    public static String convertToDwarvish(String text) {
        if (text == null || text.isEmpty()) return text;

        // Detect locale: zh → CJK branch, else → English branch
        boolean isZh = false;
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
            isZh = ClientLangHelper.isZhLocale();
        }

        if (isZh) {
            return convertToDwarvishZh(text);
        } else {
            return convertToDwarvishEn(text);
        }
    }

    @net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
    private static final class ClientLangHelper {
        static boolean isZhLocale() {
            try {
                String lang = net.minecraft.client.Minecraft.getInstance()
                        .getLanguageManager().getSelected();
                return lang != null && lang.startsWith("zh");
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /**
     * SDV zh branch: CJK/Hiragana/Katakana/々/Korean → charset mapping,
     * all other characters preserved as-is.
     */
    private static String convertToDwarvishZh(String text) {
        String charset1 = "bcdfghjklmnpqrstvwxyz";       // 21 chars
        String charset2 = "bcd fghj klmn pqrst vwxy z";  // 26 chars

        StringBuilder sb = new StringBuilder(text.length() * 2);
        boolean capitalizeNext = true;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int code = (int) c;

            // CJK Unified Ideographs, Hiragana, Katakana, 々, Korean Syllables
            boolean isCjk = (code >= 0x4E00 && code <= 0x9FFF)
                    || (code >= 0x3040 && code <= 0x30FF)
                    || c == '々'
                    || (code >= 0xAC00 && code <= 0xD7AF);

            if (isCjk) {
                char c1 = charset1.charAt(code % charset1.length());
                if (capitalizeNext) {
                    c1 = Character.toUpperCase(c1);
                    capitalizeNext = false;
                }
                sb.append(c1);
                char c2 = charset2.charAt((code >> 1) % charset2.length());
                sb.append(c2);
            } else {
                // Non-CJK: preserve as-is, capitalizeNext on non-space
                sb.append(c);
                if (c != ' ') {
                    capitalizeNext = true;
                }
            }
        }
        return sb.toString();
    }

    /**
     * SDV en branch: specific letter/number mappings, some preserved,
     * some deleted, default char+2 for unmatched isLetterOrDigit.
     */
    private static String convertToDwarvishEn(String text) {
        StringBuilder sb = new StringBuilder(text.length());

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case 'a': sb.append('o'); break;
                case 'e': sb.append('u'); break;
                case 'i': sb.append('e'); break;
                case 'o': sb.append('a'); break;
                case 'u': sb.append('i'); break;
                case 'y': sb.append("ol"); break;
                case 'z': sb.append('b'); break;
                case 'A': sb.append('O'); break;
                case 'E': sb.append('U'); break;
                case 'I': sb.append('E'); break;
                case 'O': sb.append('A'); break;
                case 'U': sb.append('I'); break;
                case 'Y': sb.append("Ol"); break;
                case 'Z': sb.append('B'); break;
                case '1': sb.append('M'); break;
                case '5': sb.append('X'); break;
                case '9': sb.append('V'); break;
                case '0': sb.append('Q'); break;
                case 'g': sb.append('l'); break;
                case 'c': sb.append('t'); break;
                case 't': sb.append('n'); break;
                case 'd': sb.append('p'); break;
                // Preserved as-is
                case ' ': case '!': case '"': case '\'':
                case ',': case '.': case '?':
                case 'h': case 'm': case 's':
                    sb.append(c); break;
                // Deleted (skipped)
                case '\n': case 'n': case 'p':
                    break;
                default:
                    if (Character.isLetterOrDigit(c)) {
                        sb.append((char) (c + 2));
                    }
                    // Non-letter/digit chars not in any case: dropped (SDV behavior)
                    break;
            }
        }

        return sb.toString().replace("nhu", "doo");
    }
}
