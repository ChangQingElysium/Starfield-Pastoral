package com.stardew.craft.integration.jei;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.fishing.data.SpawnFishRule;
import com.stardew.craft.item.ModItems;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Fishing source pages grouped by actual catch conditions instead of source-file duplication. */
public final class FishingInfoCategory implements IRecipeCategory<FishingInfoCategory.DisplayEntry> {
    public static final RecipeType<DisplayEntry> RECIPE_TYPE = RecipeType.create(
            StardewCraft.MODID, "fishing_info", DisplayEntry.class);

    private static final int WIDTH = 176;
    private static final int HEIGHT = 96;
    private static final int FISH_X = 10;
    private static final int FISH_Y = 10;
    private static final int ROD_X = 148;
    private static final int ROD_Y = 10;
    private static final List<ItemStack> FISHING_RODS = List.of(
            new ItemStack(ModItems.FISHING_ROD.get()),
            new ItemStack(ModItems.TRAINING_ROD.get()),
            new ItemStack(ModItems.FIBERGLASS_ROD.get()),
            new ItemStack(ModItems.IRIDIUM_ROD.get()),
            new ItemStack(ModItems.ADVANCED_IRIDIUM_ROD.get()));

    private final IDrawable icon;
    private final Component title;

    public record DisplayEntry(String itemId, List<SpawnFishRule> rules) {
        public DisplayEntry {
            itemId = itemId == null ? "" : itemId;
            rules = rules == null ? List.of() : List.copyOf(rules);
            if (rules.isEmpty()) throw new IllegalArgumentException("Fishing display requires at least one rule");
        }

        SpawnFishRule primaryRule() {
            return rules.getFirst();
        }

        public String contentSignature() {
            return itemId + '|' + rules.stream().map(FishingInfoCategory::fullSignature)
                    .sorted().toList();
        }
    }

    public FishingInfoCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableIngredient(VanillaTypes.ITEM_STACK,
                new ItemStack(ModItems.IRIDIUM_ROD.get()));
        this.title = Component.translatable("stardewcraft.jei.fishing_info");
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
        builder.addSlot(RecipeIngredientRole.CATALYST, ROD_X, ROD_Y)
                .addItemStacks(FISHING_RODS)
                .setSlotName("fishing_rod");
        Item fish = item(recipe.itemId());
        if (fish != Items.AIR) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, FISH_X, FISH_Y)
                    .addItemStack(new ItemStack(fish))
                    .setSlotName("catch");
        }
    }

    @Override
    public void draw(DisplayEntry entry, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        SpawnFishRule recipe = entry.primaryRule();
        Font font = Minecraft.getInstance().font;
        CommonGuiTextures.drawTextureBoxNoShadow(graphics, 0, 0, WIDTH, HEIGHT, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, FISH_X - 1, FISH_Y - 1, 1.0F);
        CommonGuiTextures.drawItemSlot18(graphics, ROD_X - 1, ROD_Y - 1, 1.0F);

        Component difficulty = Component.translatable("stardewcraft.jei.difficulty",
                Component.translatable(difficultyKey(recipe.difficulty())), recipe.difficulty());
        Component motion = Component.translatable("stardewcraft.jei.motion_type",
                Component.translatable(motionKey(recipe.motionTypeId())));
        GuiText.drawCenteredClamped(graphics, font, difficulty, 87, 8,
                106, difficultyColor(recipe.difficulty()), false);
        GuiText.drawCenteredClamped(graphics, font, motion, 87, 20,
                106, JeiDrawHelper.TEXT_MUTED, false);

        List<Band> bands = List.of(
                new Band(40, Component.translatable("stardewcraft.jei.location", locations(entry))),
                new Band(53, Component.translatable("stardewcraft.jei.time", time(entry))),
                new Band(66, seasonAndWeather(entry)),
                new Band(79, requirements(entry)));
        for (Band band : bands) {
            drawBand(graphics, font, band, mouseX, mouseY);
        }
    }

    private static void drawBand(GuiGraphics graphics, Font font, Band band,
                                 double mouseX, double mouseY) {
        int x = 9;
        int maxWidth = WIDTH - 18;
        Component shown = GuiText.ellipsize(font, band.text(), maxWidth);
        graphics.drawString(font, shown, x, band.y(), JeiDrawHelper.TEXT_BODY, false);
        if (mouseX >= x && mouseX < x + maxWidth
                && mouseY >= band.y() && mouseY < band.y() + font.lineHeight
                && font.width(band.text()) > maxWidth) {
            graphics.renderTooltip(font, band.text(), (int) mouseX, (int) mouseY);
        }
    }

    private static Component seasonAndWeather(DisplayEntry entry) {
        MutableComponent text = Component.empty();
        boolean allSeasons = entry.rules().stream()
                .anyMatch(rule -> rule.seasons() == null || rule.seasons().isEmpty());
        Set<String> seasonNames = entry.rules().stream()
                .flatMap(rule -> rule.seasons() == null ? java.util.stream.Stream.empty() : rule.seasons().stream())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        if (allSeasons || seasonNames.size() >= 4) {
            text.append(Component.translatable("stardewcraft.jei.season",
                    Component.translatable("stardewcraft.jei.season.all")));
        } else {
            List<Component> seasons = seasonNames.stream()
                    .map(FishingInfoCategory::season)
                    .toList();
            text.append(Component.translatable("stardewcraft.jei.season", join(seasons, "/")));
        }
        text.append("  •  ");
        Set<String> weathers = entry.rules().stream()
                .map(SpawnFishRule::weather)
                .map(weather -> weather == null || weather.isBlank()
                        ? "any" : weather.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        boolean anyWeather = weathers.contains("any")
                || (weathers.contains("sunny") && weathers.contains("rainy"));
        Component weatherText = anyWeather
                ? Component.translatable("stardewcraft.jei.weather.any")
                : join(weathers.stream().map(weather -> Component.translatable(weatherKey(weather))).toList(), "/");
        return text.append(Component.translatable("stardewcraft.jei.weather", weatherText));
    }

    private static Component requirements(DisplayEntry entry) {
        List<Component> parts = new ArrayList<>();
        int minFishingLevel = entry.rules().stream()
                .mapToInt(SpawnFishRule::minFishingLevel).min().orElse(0);
        if (minFishingLevel > 0) {
            parts.add(Component.translatable("stardewcraft.jei.min_level", minFishingLevel));
        }
        int minDepth = entry.rules().stream()
                .mapToInt(SpawnFishRule::minDistanceFromShore).min().orElse(0);
        boolean unboundedDepth = entry.rules().stream()
                .anyMatch(rule -> rule.maxDistanceFromShore() < 0);
        int maxDepth = unboundedDepth ? -1 : entry.rules().stream()
                .mapToInt(SpawnFishRule::maxDistanceFromShore).max().orElse(-1);
        if (minDepth > 0 || maxDepth >= 0) {
            String depth = maxDepth < 0 ? minDepth + "+" : minDepth + "-" + maxDepth;
            parts.add(Component.translatable("stardewcraft.jei.fishing.depth", depth));
        }
        if (entry.rules().stream().allMatch(SpawnFishRule::requireMagicBait)) {
            parts.add(Component.translatable("stardewcraft.jei.fishing.magic_bait"));
        }
        Set<Integer> catchLimits = entry.rules().stream().map(SpawnFishRule::catchLimit)
                .filter(limit -> limit >= 0).collect(java.util.stream.Collectors.toSet());
        if (catchLimits.size() == 1) {
            parts.add(Component.translatable("stardewcraft.jei.fishing.catch_limit",
                    catchLimits.iterator().next()));
        }
        return parts.isEmpty()
                ? Component.translatable("stardewcraft.jei.fishing.no_requirements")
                : join(parts, "  •  ");
    }

    private static Component locations(DisplayEntry entry) {
        Set<String> raw = new TreeSet<>();
        entry.rules().forEach(rule -> raw.addAll(ruleLocations(rule)));
        if (raw.isEmpty()) return Component.translatable("stardewcraft.jei.location.any");
        return join(raw.stream().map(FishingInfoCategory::location).toList(), "/");
    }

    private static Component location(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.contains("beach")) return Component.translatable("stardewcraft.jei.location.beach");
        if (value.contains("ocean")) return Component.translatable("stardewcraft.jei.location.ocean");
        if (value.contains("river")) return Component.translatable("stardewcraft.jei.location.river");
        if (value.contains("mountain_lake") || value.equals("lake")) return Component.translatable("stardewcraft.jei.location.mountain_lake");
        if (value.contains("forest_pond")) return Component.translatable("stardewcraft.jei.location.forest_pond");
        if (value.contains("secret_woods") || value.contains("woods")) return Component.translatable("stardewcraft.jei.location.secret_woods");
        if (value.contains("sewer")) return Component.translatable("stardewcraft.jei.location.sewers");
        if (value.contains("mines_20")) return Component.translatable("stardewcraft.jei.location.mines", "20F");
        if (value.contains("mines_60")) return Component.translatable("stardewcraft.jei.location.mines", "60F");
        if (value.contains("mines_100")) return Component.translatable("stardewcraft.jei.location.mines", "100F");
        if (value.contains("desert")) return Component.translatable("stardewcraft.jei.location.desert");
        if (value.contains("witch")) return Component.translatable("stardewcraft.jei.location.witch_swamp");
        if (value.contains("night_market") || value.contains("nightmarket")) return Component.translatable("stardewcraft.jei.location.night_market");
        if (value.contains("volcano") || value.contains("caldera")) return Component.translatable("stardewcraft.jei.location.volcano");
        if (value.contains("pirate")) return Component.translatable("stardewcraft.jei.location.pirate_cove");
        if (value.contains("island") || value.contains("ginger")) return Component.translatable("stardewcraft.jei.location.ginger_island");
        if (value.contains("freshwater")) return Component.translatable("stardewcraft.jei.location.river");
        if (value.contains("arrowhead")) return Component.translatable("stardewcraft.jei.location.ginger_island");
        if (value.contains("waterfall") || value.contains("jojamart_bridge")) {
            return Component.translatable("stardewcraft.jei.location.river");
        }
        if (value.contains("mutant_bug")) return Component.translatable("stardewcraft.jei.location.sewers");
        return Component.translatable("stardewcraft.jei.location.other");
    }

    private static Component time(DisplayEntry entry) {
        if (entry.rules().stream().anyMatch(rule -> rule.timeRanges() == null || rule.timeRanges().isEmpty())) {
            return Component.translatable("stardewcraft.jei.time.all_day");
        }
        List<? extends Component> ranges = entry.rules().stream()
                .flatMap(rule -> rule.timeRanges().stream())
                .filter(range -> range != null && range.length >= 2)
                .collect(java.util.stream.Collectors.toMap(
                        range -> range[0] + ":" + range[1],
                        range -> range,
                        (first, ignored) -> first,
                        java.util.TreeMap::new))
                .values().stream()
                .map(range -> Component.literal(formatTime(range[0]) + "-" + formatTime(range[1])))
                .toList();
        return join(ranges, " / ");
    }

    private static String formatTime(int stardewTime) {
        int hour = stardewTime / 100;
        int minute = stardewTime % 100;
        return String.format(Locale.ROOT, "%d:%02d", hour, minute);
    }

    private static Component season(String season) {
        String key = switch (season.toLowerCase(Locale.ROOT)) {
            case "spring" -> "spring";
            case "summer" -> "summer";
            case "fall" -> "fall";
            case "winter" -> "winter";
            default -> "unknown";
        };
        return Component.translatable("stardewcraft.jei.season." + key);
    }

    private static String weatherKey(String weather) {
        return "stardewcraft.jei.weather." + switch (weather.toLowerCase(Locale.ROOT)) {
            case "rainy" -> "rainy";
            case "sunny" -> "sunny";
            default -> "unknown";
        };
    }

    private static String difficultyKey(int difficulty) {
        if (difficulty <= 20) return "stardewcraft.jei.difficulty.easy";
        if (difficulty <= 40) return "stardewcraft.jei.difficulty.normal";
        if (difficulty <= 60) return "stardewcraft.jei.difficulty.medium";
        if (difficulty <= 80) return "stardewcraft.jei.difficulty.hard";
        if (difficulty <= 100) return "stardewcraft.jei.difficulty.very_hard";
        return "stardewcraft.jei.difficulty.legendary";
    }

    private static int difficultyColor(int difficulty) {
        if (difficulty <= 40) return 0xFF4F6F32;
        if (difficulty <= 60) return 0xFF9A6427;
        if (difficulty <= 80) return 0xFF9B3F2B;
        return 0xFF773B76;
    }

    private static String motionKey(int motionType) {
        return "stardewcraft.jei.motion." + switch (motionType) {
            case 0 -> "mixed";
            case 1 -> "dart";
            case 2 -> "smooth";
            case 3 -> "sinker";
            case 4 -> "floater";
            default -> "unknown";
        };
    }

    private static Component join(List<? extends Component> components, String separator) {
        MutableComponent result = Component.empty();
        for (int i = 0; i < components.size(); i++) {
            if (i > 0) result.append(separator);
            result.append(components.get(i));
        }
        return result;
    }

    private static Item item(String rawId) {
        ResourceLocation id = rawId == null ? null : ResourceLocation.tryParse(rawId);
        return id == null || !BuiltInRegistries.ITEM.containsKey(id)
                ? Items.AIR : BuiltInRegistries.ITEM.get(id);
    }

    static List<SpawnFishRule> deduplicateRules(List<SpawnFishRule> rules) {
        Map<String, SpawnFishRule> distinct = new LinkedHashMap<>();
        for (SpawnFishRule rule : rules) {
            distinct.putIfAbsent(fullSignature(rule), rule);
        }
        return distinct.values().stream()
                .sorted(Comparator.comparing(SpawnFishRule::itemId)
                        .thenComparing(FishingInfoCategory::nonLocationSignature))
                .toList();
    }

    static List<DisplayEntry> buildEntries(List<SpawnFishRule> rules) {
        Map<String, List<SpawnFishRule>> groups = new LinkedHashMap<>();
        for (SpawnFishRule rule : deduplicateRules(rules)) {
            groups.computeIfAbsent(rule.itemId(), ignored -> new ArrayList<>()).add(rule);
        }
        return groups.entrySet().stream()
                .map(group -> new DisplayEntry(group.getKey(), group.getValue()))
                .sorted(Comparator.comparing(DisplayEntry::itemId))
                .toList();
    }

    private static Set<String> ruleLocations(SpawnFishRule rule) {
        Set<String> locations = new TreeSet<>();
        if (rule.biomeTags() != null) locations.addAll(rule.biomeTags());
        if (rule.biomes() != null) locations.addAll(rule.biomes());
        if (locations.isEmpty() && rule.fishAreaId() != null && !rule.fishAreaId().isBlank()) {
            locations.add(rule.fishAreaId());
        }
        return locations;
    }

    private static String fullSignature(SpawnFishRule rule) {
        return nonLocationSignature(rule) + '|' + normalizedStrings(ruleLocations(rule));
    }

    private static String nonLocationSignature(SpawnFishRule rule) {
        return String.join("|",
                nullSafe(rule.itemId()), Float.toString(rule.chance()),
                Integer.toString(rule.difficulty()), Integer.toString(rule.motionTypeId()),
                Integer.toString(rule.minFishSize()), Integer.toString(rule.maxFishSize()),
                Integer.toString(rule.minFishingLevel()), Integer.toString(rule.minDistanceFromShore()),
                Integer.toString(rule.maxDistanceFromShore()), normalizedStrings(rule.seasons()),
                nullSafe(rule.weather()).toLowerCase(Locale.ROOT), normalizedRanges(rule.timeRanges()),
                Boolean.toString(rule.skipMinigame()), Boolean.toString(rule.canBeInherited()),
                Boolean.toString(rule.requireMagicBait()), Integer.toString(rule.catchLimit()),
                nullSafe(rule.condition()), Float.toString(rule.spawnRate()),
                Integer.toString(rule.maxDepth()), Float.toString(rule.depthMultiplier()),
                Boolean.toString(rule.applyDailyLuck()), Float.toString(rule.curiosityLureBuff()),
                Boolean.toString(rule.isBossFish()), Boolean.toString(rule.isTutorialFish()),
                Boolean.toString(rule.ignoreFishDataRequirements()),
                String.valueOf(rule.chanceModifiers()), String.valueOf(rule.chanceModifierMode()),
                Boolean.toString(rule.useFishCaughtSeededRandom()));
    }

    private static String normalizedRanges(List<int[]> ranges) {
        if (ranges == null || ranges.isEmpty()) return "";
        return ranges.stream().map(Arrays::toString).sorted().toList().toString();
    }

    private static String normalizedStrings(java.util.Collection<String> values) {
        if (values == null || values.isEmpty()) return "";
        return values.stream().filter(java.util.Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT)).sorted().toList().toString();
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record Band(int y, Component text) {
    }
}
