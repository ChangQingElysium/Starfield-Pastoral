package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.shop.ShopItemEntry;
import com.stardew.craft.shop.ShopConditionDisplayData;
import com.stardew.craft.shop.ShopRegistry;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shop source pages with real currency/trade inputs and availability conditions. */
public final class ShopInfoCategory implements IRecipeCategory<ShopInfoCategory.DisplayEntry> {
    public static final RecipeType<DisplayEntry> RECIPE_TYPE = RecipeType.create(
            StardewCraft.MODID, "shop_info", DisplayEntry.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 78;
    private static final int PORTRAIT_X = 6;
    private static final int PORTRAIT_Y = 6;
    private static final int TRADE_X = 48;
    private static final int OUTPUT_X = 48;
    private static final int TRADE_OUTPUT_X = 93;
    private static final int SLOT_Y = 20;
    private static final int TEXT_X = 48;
    private static final int TEXT_WIDTH = WIDTH - TEXT_X - 8;

    private final IDrawable icon;
    private final Component title;

    public record DisplayEntry(
            ItemStack item,
            String shopId,
            String ownerNpcId,
            int price,
            int stock,
            ItemStack tradeItem,
            int tradeItemCount,
            int purchaseStack,
            Set<Integer> seasons,
            int minYear,
            int minMineLevel,
            boolean mailRequired,
            int dayOfWeek,
            int dayOfMonthParity,
            boolean conditional,
            List<String> conditionTokens,
            boolean recipe
    ) {
        public DisplayEntry {
            item = item == null ? ItemStack.EMPTY : item.copy();
            tradeItem = tradeItem == null ? ItemStack.EMPTY : tradeItem.copy();
            shopId = shopId == null ? "" : shopId;
            ownerNpcId = ownerNpcId == null ? "" : ownerNpcId;
            seasons = seasons == null ? Set.of() : Set.copyOf(seasons);
            purchaseStack = Math.max(1, purchaseStack);
            tradeItemCount = Math.max(0, tradeItemCount);
            conditionTokens = conditionTokens == null ? List.of() : List.copyOf(conditionTokens);
        }

        public DisplayEntry(ItemStack item, String shopId, int price, int stock,
                            ItemStack tradeItem, int tradeItemCount, int purchaseStack,
                            Set<Integer> seasons, int minYear, int minMineLevel,
                            boolean mailRequired, int dayOfWeek, int dayOfMonthParity,
                            boolean conditional, List<String> conditionTokens, boolean recipe) {
            this(item, shopId, "", price, stock, tradeItem, tradeItemCount, purchaseStack,
                    seasons, minYear, minMineLevel, mailRequired, dayOfWeek,
                    dayOfMonthParity, conditional, conditionTokens, recipe);
        }

        public DisplayEntry(ItemStack item, String shopId, int price, int stock,
                            ItemStack tradeItem, int tradeItemCount, int purchaseStack,
                            Set<Integer> seasons, int minYear, int minMineLevel,
                            boolean mailRequired, int dayOfWeek, int dayOfMonthParity,
                            boolean conditional, boolean recipe) {
            this(item, shopId, "", price, stock, tradeItem, tradeItemCount, purchaseStack,
                    seasons, minYear, minMineLevel, mailRequired, dayOfWeek,
                    dayOfMonthParity, conditional,
                    conditional ? List.of("unknown") : List.of(), recipe);
        }

        @Override
        public ItemStack item() {
            return item.copy();
        }

        @Override
        public ItemStack tradeItem() {
            return tradeItem.copy();
        }

        public String contentSignature() {
            return JeiRecipeSignatures.stack(item) + '|' + shopId + '|' + ownerNpcId + '|'
                    + price + '|' + stock + '|'
                    + JeiRecipeSignatures.stack(tradeItem) + '|' + tradeItemCount + '|' + purchaseStack + '|'
                    + seasons + '|' + minYear + '|' + minMineLevel + '|' + mailRequired + '|'
                    + dayOfWeek + '|' + dayOfMonthParity + '|' + conditional + '|'
                    + conditionTokens + '|' + recipe;
        }
    }

    public ShopInfoCategory(IGuiHelper guiHelper) {
        JeiDrawHelper.initGoldIcon(guiHelper);
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                ArtisanRecipeCategory.itemStack("stardewcraft:gold_bar"));
        this.title = Component.translatable("stardewcraft.jei.shop_info");
    }

    @Override
    public RecipeType<DisplayEntry> getRecipeType() {
        return RECIPE_TYPE;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DisplayEntry recipe, IFocusGroup focuses) {
        ItemStack output = recipe.item();
        output.setCount(recipe.purchaseStack());
        int outputX = recipe.tradeItem().isEmpty() ? OUTPUT_X : TRADE_OUTPUT_X;
        builder.addSlot(RecipeIngredientRole.OUTPUT, outputX, SLOT_Y)
                .addItemStack(output)
                .setSlotName("shop_output");
        if (!recipe.tradeItem().isEmpty()) {
            ItemStack trade = recipe.tradeItem();
            trade.setCount(Math.max(1, recipe.tradeItemCount()));
            builder.addSlot(RecipeIngredientRole.INPUT, TRADE_X, SLOT_Y)
                    .addItemStack(trade)
                    .setSlotName("trade_input");
        }
    }

    @Override
    public void draw(DisplayEntry recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, 0, 0, WIDTH, HEIGHT, 1.0F);
        drawPortrait(graphics, recipe);
        int outputX = recipe.tradeItem().isEmpty() ? OUTPUT_X : TRADE_OUTPUT_X;
        CommonGuiTextures.drawItemSlot18(graphics, outputX - 1, SLOT_Y - 1, 1.0F);
        if (!recipe.tradeItem().isEmpty()) {
            CommonGuiTextures.drawItemSlot18(graphics, TRADE_X - 1, SLOT_Y - 1, 1.0F);
            CommonGuiTextures.drawForwardArrow(graphics, 70, SLOT_Y + 3, 1.0F);
        }

        Component shopName = shopName(recipe.shopId(), recipe.ownerNpcId());
        if (recipe.recipe()) {
            shopName = shopName.copy().append(" ").append(
                    Component.translatable("stardewcraft.jei.shop.recipe_tag"));
        }
        drawLeftClamped(graphics, font, shopName, TEXT_X, 7, TEXT_WIDTH,
                JeiDrawHelper.TEXT_TITLE, mouseX, mouseY);

        int infoX = recipe.tradeItem().isEmpty() ? 72 : 116;
        if (recipe.price() > 0 && recipe.price() < Integer.MAX_VALUE) {
            JeiDrawHelper.drawGoldAmount(graphics, font, infoX, 22, recipe.price());
        } else if (recipe.tradeItem().isEmpty()) {
            graphics.drawString(font, Component.translatable("stardewcraft.jei.shop.free"),
                    infoX, 22, JeiDrawHelper.TEXT_BODY, false);
        }
        Component quantity = quantityText(recipe);
        drawLeftClamped(graphics, font, quantity, TEXT_X, 38, TEXT_WIDTH,
                JeiDrawHelper.TEXT_MUTED, mouseX, mouseY);

        drawLeftClamped(graphics, font, availability(recipe), 9, 52, WIDTH - 18,
                JeiDrawHelper.TEXT_BODY, mouseX, mouseY);
        Component conditions = conditions(recipe);
        if (conditions != null) {
            drawLeftClamped(graphics, font, conditions, 9, 65, WIDTH - 18,
                    JeiDrawHelper.TEXT_MUTED, mouseX, mouseY);
        }
    }

    private static void drawPortrait(GuiGraphics graphics, DisplayEntry recipe) {
        String portraitKey = JeiPortraitCache.shopIdToPortraitKey(recipe.shopId());
        if (portraitKey == null) return;
        IDrawable portrait = JeiPortraitCache.get(portraitKey);
        if (portrait == null) return;
        CommonGuiTextures.drawShopPortraitFrame(graphics, PORTRAIT_X, PORTRAIT_Y, 0.5F);
        graphics.pose().pushPose();
        graphics.pose().translate(PORTRAIT_X + 5, PORTRAIT_Y + 5, 0);
        float scale = JeiPortraitCache.DEFAULT_SIZE / 64.0F;
        graphics.pose().scale(scale, scale, 1.0F);
        portrait.draw(graphics, 0, 0);
        graphics.pose().popPose();
    }

    private static Component quantityText(DisplayEntry recipe) {
        List<Component> parts = new ArrayList<>();
        if (recipe.purchaseStack() > 1) {
            parts.add(Component.translatable("stardewcraft.jei.shop.receive", recipe.purchaseStack()));
        }
        if (recipe.stock() != Integer.MAX_VALUE) {
            parts.add(Component.translatable("stardewcraft.jei.shop.stock", recipe.stock()));
        }
        return parts.isEmpty()
                ? Component.translatable("stardewcraft.jei.shop.unlimited")
                : join(parts, "  •  ");
    }

    private static Component availability(DisplayEntry recipe) {
        List<Component> parts = new ArrayList<>();
        if (!recipe.seasons().isEmpty()) {
            parts.add(Component.translatable("stardewcraft.jei.season",
                    join(recipe.seasons().stream().sorted().map(ShopInfoCategory::season).toList(), "/")));
        }
        if (recipe.dayOfWeek() >= 0 && recipe.dayOfWeek() <= 6) {
            parts.add(Component.translatable(dayKey(recipe.dayOfWeek())));
        }
        if (recipe.dayOfMonthParity() == 1) {
            parts.add(Component.translatable("stardewcraft.jei.shop.odd_days"));
        } else if (recipe.dayOfMonthParity() == 2) {
            parts.add(Component.translatable("stardewcraft.jei.shop.even_days"));
        }
        if (recipe.minYear() > 1) {
            parts.add(Component.translatable("stardewcraft.jei.shop.year", recipe.minYear()));
        }
        return parts.isEmpty()
                ? Component.translatable("stardewcraft.jei.shop.always")
                : join(parts, "  •  ");
    }

    private static Component conditions(DisplayEntry recipe) {
        List<Component> parts = new ArrayList<>();
        if (recipe.minMineLevel() > 0) {
            parts.add(Component.translatable("stardewcraft.jei.shop.mine_level", recipe.minMineLevel()));
        }
        if (recipe.mailRequired()) {
            parts.add(Component.translatable("stardewcraft.jei.shop.mail_required"));
        }
        for (String token : recipe.conditionTokens()) {
            parts.add(condition(token));
        }
        if (recipe.conditional() && recipe.conditionTokens().isEmpty()) {
            parts.add(Component.translatable("stardewcraft.jei.shop.conditional"));
        }
        return parts.isEmpty() ? null : join(parts, "  •  ");
    }

    private static Component condition(String token) {
        String[] fields = token == null ? new String[0] : token.split("\\|", -1);
        if (fields.length == 0) {
            return Component.translatable("stardewcraft.jei.shop.conditional");
        }
        try {
            return switch (fields[0]) {
                case "never" -> Component.translatable("stardewcraft.jei.shop.condition.never");
                case "has_item" -> hasItemCondition(fields);
                case "lacks_item" -> lacksItemCondition(fields);
                case "money" -> moneyCondition(fields);
                case "flag" -> Component.translatable(Boolean.parseBoolean(fields[1])
                        ? "stardewcraft.jei.shop.condition.story_required"
                        : "stardewcraft.jei.shop.condition.story_absent");
                case "skill" -> Component.translatable("stardewcraft.jei.shop.condition.skill",
                        Component.translatable("stardewcraft.skill." + fields[1].toLowerCase(Locale.ROOT)),
                        Integer.parseInt(fields[2]));
                case "season" -> Component.translatable("stardewcraft.jei.season",
                        join(List.of(fields[1].split(",")).stream()
                                .map(ShopInfoCategory::seasonName).toList(), "/"));
                default -> Component.translatable("stardewcraft.jei.shop.conditional");
            };
        } catch (RuntimeException ignored) {
            return Component.translatable("stardewcraft.jei.shop.conditional");
        }
    }

    private static Component hasItemCondition(String[] fields) {
        ResourceLocation itemId = fields.length > 1 ? ResourceLocation.tryParse(fields[1]) : null;
        int count = fields.length > 2 ? Integer.parseInt(fields[2]) : 1;
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return Component.translatable("stardewcraft.jei.shop.conditional");
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        return Component.translatable("stardewcraft.jei.shop.condition.has_item", stack.getHoverName(), count);
    }

    private static Component lacksItemCondition(String[] fields) {
        ResourceLocation itemId = fields.length > 1 ? ResourceLocation.tryParse(fields[1]) : null;
        int count = fields.length > 2 ? Integer.parseInt(fields[2]) : 1;
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return Component.translatable("stardewcraft.jei.shop.conditional");
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(itemId));
        return Component.translatable(
                "stardewcraft.jei.shop.condition.lacks_item",
                stack.getHoverName(),
                count);
    }

    private static Component moneyCondition(String[] fields) {
        int min = Integer.parseInt(fields[1]);
        int max = Integer.parseInt(fields[2]);
        if (min == Integer.MIN_VALUE && max == Integer.MAX_VALUE) {
            return Component.translatable("stardewcraft.jei.shop.conditional");
        }
        if (max == Integer.MAX_VALUE) {
            return Component.translatable("stardewcraft.jei.shop.condition.money_min", min);
        }
        if (min == Integer.MIN_VALUE) {
            return Component.translatable("stardewcraft.jei.shop.condition.money_max", max);
        }
        return Component.translatable("stardewcraft.jei.shop.condition.money_range", min, max);
    }

    private static Component seasonName(String season) {
        return Component.translatable("stardewcraft.jei.season." + season.toLowerCase(Locale.ROOT));
    }

    private static void drawLeftClamped(GuiGraphics graphics, Font font, Component text,
                                        int x, int y, int maxWidth, int color,
                                        double mouseX, double mouseY) {
        Component shown = GuiText.ellipsize(font, text, maxWidth);
        graphics.drawString(font, shown, x, y, color, false);
        if (mouseX >= x && mouseX < x + maxWidth && mouseY >= y && mouseY < y + font.lineHeight
                && font.width(text) > maxWidth) {
            graphics.renderTooltip(font, text, (int) mouseX, (int) mouseY);
        }
    }

    public static List<DisplayEntry> buildAllEntries() {
        List<DisplayEntry> result = new ArrayList<>();
        for (String shopId : ShopRegistry.allShopIds()) {
            ShopRegistry.ShopDefinition definition = ShopRegistry.get(shopId);
            if (definition == null) continue;
            for (ShopItemEntry entry : definition.items()) {
                boolean recipe = entry.itemId().startsWith("recipe:");
                String rawItemId = recipe ? entry.itemId().substring("recipe:".length()) : entry.itemId();
                ItemStack stack = resolveShopOutput(rawItemId, recipe);
                if (stack.isEmpty()) continue;
                ItemStack trade = entry.requiresTrade()
                        ? ArtisanRecipeCategory.itemStack(entry.tradeItemId()) : ItemStack.EMPTY;
                List<String> conditionTokens = ShopConditionDisplayData.tokens(entry.availableWhen());
                result.add(new DisplayEntry(stack, shopId, definition.ownerNpcId(), entry.price(), entry.stock(),
                        trade, entry.tradeItemCount(), entry.purchaseStack(), entry.seasons(),
                        entry.minYear(), entry.minMineLevel(),
                        entry.mailFlag() != null && !entry.mailFlag().isBlank(),
                        entry.dayOfWeek(), entry.dayOfMonthParity(),
                        !conditionTokens.isEmpty(), conditionTokens, recipe));
            }
        }
        return List.copyOf(result);
    }

    private static ItemStack resolveShopOutput(String rawItemId, boolean recipe) {
        if (!recipe) return ArtisanRecipeCategory.itemStack(rawItemId);
        ItemStack stack = com.stardew.craft.player.StardewCraftingRecipeData.getOutputStack(rawItemId);
        if (!stack.isEmpty()) return stack;
        return com.stardew.craft.cooking.service.VanillaCookingRecipeData.getDefinition(rawItemId)
                .map(definition -> ArtisanRecipeCategory.itemStack(definition.output().toString()))
                .orElse(ItemStack.EMPTY);
    }

    private static Component shopName(String shopId, String ownerNpcId) {
        String key = "stardewcraft.jei.shop.name." + shopId;
        if (I18n.exists(key)) return Component.translatable(key);
        String ownerKey = "entity.stardewcraft.npc."
                + (ownerNpcId == null ? "" : ownerNpcId.toLowerCase(Locale.ROOT));
        if (I18n.exists(ownerKey)) {
            return Component.translatable("stardewcraft.jei.shop.owner_shop",
                    Component.translatable(ownerKey));
        }
        return Component.translatable("stardewcraft.jei.shop.generic");
    }

    private static Component season(int season) {
        return Component.translatable("stardewcraft.jei.season." + switch (season) {
            case 0 -> "spring";
            case 1 -> "summer";
            case 2 -> "fall";
            case 3 -> "winter";
            default -> "unknown";
        });
    }

    private static String dayKey(int day) {
        return "stardewcraft.hud." + switch (day) {
            case 0 -> "monday";
            case 1 -> "tuesday";
            case 2 -> "wednesday";
            case 3 -> "thursday";
            case 4 -> "friday";
            case 5 -> "saturday";
            default -> "sunday";
        };
    }

    private static Component join(List<? extends Component> parts, String separator) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) result.append(separator);
            result.append(parts.get(i));
        }
        return result;
    }
}
