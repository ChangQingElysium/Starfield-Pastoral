package com.stardew.craft.client.gui.overnight;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.font.StardewFonts;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.client.gui.common.GuiText;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.network.overnight.ClientOvernightHandler;
import com.stardew.craft.network.overnight.OvernightSettlementPayload;
import com.stardew.craft.network.payload.OvernightProfessionChoicePayload;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.RecipeIdNormalizer;
import com.stardew.craft.player.SkillLevelRecipeUnlocks;
import com.stardew.craft.player.SkillType;
import com.stardew.craft.player.StardewCraftingRecipeData;
import com.stardew.craft.player.UnlockSourceData;
import com.stardew.craft.sound.ModSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@OnlyIn(Dist.CLIENT)
@SuppressWarnings("null")
public class LevelUpMenuScreen extends Screen {

    private static final ResourceLocation TEX_ICON_FARMING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/overnight/icon_farming.png");
    private static final ResourceLocation TEX_ICON_FISHING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/overnight/icon_fishing.png");
    private static final ResourceLocation TEX_ICON_FORAGING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/overnight/icon_foraging.png");
    private static final ResourceLocation TEX_ICON_MINING = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/overnight/icon_mining.png");
    private static final ResourceLocation TEX_ICON_OTHER = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/overnight/icon_other.png");
    private static final ResourceLocation TEX_LITTLE_STAR = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/overnight/little_star.png");

    private final List<Screen> siblingScreens;

    // Stardew variables
    private boolean isProfessionChooser;
    private int currentLevel;
    private int currentSkill;
    private int timerBeforeStart = 250;
    private final List<LittleStar> littleStars = new ArrayList<>();
    private final List<Component> extraInfoForLevel;
    private final List<RecipeDisplayEntry> unlockedRecipes;

    private static final int BORDER_WIDTH = 40;
    private static final int SPACE_TOP = 96;
    private static final int SPACE_SIDE = 16;

    private float guiScale() {
        return this.minecraft == null ? 1.0f : (float) this.minecraft.getWindow().getGuiScale();
    }

    private int px(int stardewPixels) {
        return Math.round(stardewPixels / guiScale());
    }

    private float s4() {
        return 4.0f / guiScale();
    }

    private int getMenuWidthPx() {
        return px(960);
    }

    private int getMenuHeightPx() {
        if (isProfessionChooser) {
            return px(512);
        }
        int recipeHeight = unlockedRecipes.stream()
            .mapToInt(entry -> entry.bigCraftable() ? 128 : 64)
            .sum();
        int stardewHeight = 256 + extraInfoForLevel.size() * 48 + recipeHeight;
        return px(Math.max(256, stardewHeight));
    }

    private int[] getOkButtonRect(int xPos, int yPos, int guiWidth, int guiHeight) {
        int okSize = px(64);
        int okX = xPos + guiWidth + px(4);
        int okY = yPos + guiHeight - px(64 + BORDER_WIDTH);
        if (okX + okSize > this.width) {
            okX = this.width - okSize;
        }
        if (okY + okSize > this.height) {
            okY = this.height - okSize;
        }
        return new int[]{okX, okY, okSize};
    }

    private void playUiSound(SoundEvent sound, float volume, float pitch) {
        if (this.minecraft != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, volume, pitch));
        }
    }

    private void updateLittleStars(int xPos, int yPos, int guiWidth) {
        long now = System.currentTimeMillis();
        littleStars.removeIf(star -> star.isDone(now));

        if (ThreadLocalRandom.current().nextDouble() < 0.03) {
            float scale = guiScale();
            int originalX = Math.round(xPos * scale);
            int originalY = Math.round(yPos * scale);
            int originalWidth = Math.round(guiWidth * scale);
            int y = ThreadLocalRandom.current().nextInt(originalY - 128, originalY - 4) / 20 * 20 + 32;

            int x;
            if (ThreadLocalRandom.current().nextBoolean()) {
                x = ThreadLocalRandom.current().nextInt(
                    originalX + originalWidth / 2 - 228,
                    originalX + originalWidth / 2 - 132
                );
            } else {
                x = ThreadLocalRandom.current().nextInt(
                    originalX + originalWidth / 2 + 116,
                    originalX + originalWidth - 160
                );
            }
            if (y < originalY - 72) {
                x = ThreadLocalRandom.current().nextInt(
                    originalX + originalWidth / 2 - 116,
                    originalX + originalWidth / 2 + 116
                );
            }
            x = x / 20 * 20;

            littleStars.add(new LittleStar(Math.round(x / scale), Math.round(y / scale), now));
        }
    }

    @Override
    protected void init() {
        super.init();
    }

    public LevelUpMenuScreen(OvernightSettlementPayload.LevelUpData levelData, List<Screen> siblingScreens) {
        super(Component.translatable("stardewcraft.levelup.title.screen"));
        this.siblingScreens = siblingScreens;

        this.currentSkill = levelData.skillIndex();
        this.currentLevel = levelData.newLevel();
        this.isProfessionChooser = (currentLevel == 5 || currentLevel == 10) && currentSkill != 5;
        this.extraInfoForLevel = getExtraInfoForLevel(currentSkill, currentLevel);
        this.unlockedRecipes = getUnlockedRecipeEntries();
    }

    @Override
    public void tick() {
        if (timerBeforeStart > 0) {
            timerBeforeStart = Math.max(0, timerBeforeStart - 50);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int guiWidth = getMenuWidthPx();
        int guiHeight = getMenuHeightPx();
        int xPos = this.width / 2 - guiWidth / 2;
        int yPos = this.height / 2 - guiHeight / 2;
        // Stardew updates these particles during the 250 ms input delay even
        // though it does not draw the menu yet.
        updateLittleStars(xPos, yPos, guiWidth);
        if (timerBeforeStart > 0) {
            return;
        }
        // Match Stardew's dim overlay first
        graphics.fill(0, 0, this.width, this.height, 0x80000000);

        long now = System.currentTimeMillis();
        for (LittleStar star : littleStars) {
            star.draw(graphics, now, guiScale());
        }

        LevelUpMenuTextures.drawHeaderRibbon(graphics, xPos + guiWidth / 2 - px(116), yPos - px(20), s4());
        StardewGuiUtil.drawDialogueBoxFrame(graphics, xPos, yPos, guiWidth, guiHeight);

        if (isProfessionChooser) {
            drawSkillIcon(graphics, currentSkill, xPos + px(SPACE_SIDE + BORDER_WIDTH), yPos + px(SPACE_TOP + 16));
            drawSkillIcon(graphics, currentSkill, xPos + guiWidth - px(SPACE_SIDE + BORDER_WIDTH + 64), yPos + px(SPACE_TOP + 16));

            Component title = StardewFonts.title(Component.translatable(
                    "stardewcraft.levelup.title.level_skill", currentLevel, getSkillName(currentSkill)));
            GuiText.drawCenteredClamped(graphics, this.font, title, xPos + guiWidth / 2,
                yPos + px(SPACE_TOP + 16), guiWidth - px(240), 0x3A2A1A, false);

            Component chooseText = Component.translatable("stardewcraft.levelup.choose_profession");
            GuiText.drawCenteredClamped(graphics, this.font, chooseText, xPos + guiWidth / 2,
                yPos + px(SPACE_TOP + 64), guiWidth - px(160), 0x3A2A1A, false);

            // IClickableMenu.drawHorizontalPartition + drawVerticalIntersectingPartition
            int partitionUnit = px(64);
            StardewGuiUtil.drawHorizontalPartition(graphics, xPos, yPos + px(192), guiWidth, partitionUnit);
            StardewGuiUtil.drawVerticalIntersectingPartition(graphics, xPos + guiWidth / 2 - px(32), yPos + px(192), yPos, guiHeight, partitionUnit);

            int[] professions = getProfessionPair(currentSkill, currentLevel);
            Component leftName = getProfessionName(professions[0]);
            Component rightName = getProfessionName(professions[1]);

            boolean inChoiceRows = mouseY >= yPos + px(192) && mouseY <= yPos + guiHeight;
            boolean hoverLeft = inChoiceRows && mouseX >= xPos && mouseX < xPos + guiWidth / 2;
            boolean hoverRight = inChoiceRows && mouseX >= xPos + guiWidth / 2 && mouseX <= xPos + guiWidth;
            int leftColor = hoverLeft ? 0x00AA00 : 0x3A2A1A;
            int rightColor = hoverRight ? 0x00AA00 : 0x3A2A1A;

            int leftX = xPos + px(SPACE_SIDE + 32);
            int rightX = xPos + px(SPACE_SIDE) + guiWidth / 2;
            int textY = yPos + px(SPACE_TOP + 160);
            int lineY = yPos + px(SPACE_TOP + 128 + 8 + 64 * 2);

            graphics.drawString(this.font, GuiText.ellipsize(this.font, leftName, guiWidth / 2 - px(96)),
                leftX, textY, leftColor, false);
            LevelUpMenuTextures.drawProfession(graphics, xPos + px(SPACE_SIDE) + guiWidth / 2 - px(112), yPos + px(SPACE_TOP + 144), professions[0], s4());
            drawWrappedText(graphics, getProfessionDesc(professions[0]), leftX + px(-4), lineY, guiWidth / 2 - px(64), leftColor, px(16));

            graphics.drawString(this.font, GuiText.ellipsize(this.font, rightName, guiWidth / 2 - px(96)),
                rightX, textY, rightColor, false);
            LevelUpMenuTextures.drawProfession(graphics, xPos + px(SPACE_SIDE) + guiWidth - px(128), yPos + px(SPACE_TOP + 144), professions[1], s4());
            drawWrappedText(graphics, getProfessionDesc(professions[1]), rightX + px(-4), lineY, guiWidth / 2 - px(48), rightColor, px(16));
        } else {
            drawSkillIcon(graphics, currentSkill, xPos + px(SPACE_SIDE + BORDER_WIDTH), yPos + px(SPACE_TOP + 16));
            drawSkillIcon(graphics, currentSkill, xPos + guiWidth - px(SPACE_SIDE + BORDER_WIDTH + 64), yPos + px(SPACE_TOP + 16));

            Component title = StardewFonts.title(Component.translatable(
                    "stardewcraft.levelup.title.level_skill", currentLevel, getSkillName(currentSkill)));
            GuiText.drawCenteredClamped(graphics, this.font, title, xPos + guiWidth / 2,
                yPos + px(SPACE_TOP + 16), guiWidth - px(240), 0x3A2A1A, false);

            int lineY = yPos + px(SPACE_TOP + 80);
            for (Component info : extraInfoForLevel) {
                GuiText.drawCenteredClamped(graphics, this.font, info, xPos + guiWidth / 2,
                    lineY, guiWidth - px(160), 0x3A2A1A, false);
                lineY += px(48);
            }

            for (RecipeDisplayEntry entry : unlockedRecipes) {
                Component message = Component.translatable(
                    "stardewcraft.levelup.new_recipe",
                    Component.translatable(entry.cooking()
                        ? "stardewcraft.levelup.recipe_type.cooking"
                        : "stardewcraft.levelup.recipe_type.crafting"),
                    entry.displayName());
                Component shown = GuiText.ellipsize(this.font, message, guiWidth - px(240));
                int textWidth = this.font.width(shown);
                int textX = xPos + guiWidth / 2 - textWidth / 2 - px(64);
                int textY = lineY + px(entry.bigCraftable() ? 38 : 12);
                graphics.drawString(this.font, shown, textX, textY, 0x3A2A1A, false);
                if (!entry.icon().isEmpty()) {
                    CommonGuiTextures.drawItem(graphics, entry.icon(),
                        xPos + guiWidth / 2 + textWidth / 2 - px(48),
                        lineY - px(16), s4());
                }
                lineY += px((entry.bigCraftable() ? 128 : 64) + 8);
            }

            // Draw OK button
            int[] okRect = getOkButtonRect(xPos, yPos, guiWidth, guiHeight);
            int okX = okRect[0];
            int okY = okRect[1];
            int okWidth = okRect[2];
            boolean hovering = mouseX >= okX && mouseX <= okX + okWidth && mouseY >= okY && mouseY <= okY + okWidth;
            graphics.pose().pushPose();
            if (hovering) {
                 graphics.pose().translate(okX + okWidth/2f, okY + okWidth/2f, 0);
                 graphics.pose().scale(1.1f, 1.1f, 1f);
                 graphics.pose().translate(-(okX + okWidth/2f), -(okY + okWidth/2f), 0);
            }
            LevelUpMenuTextures.drawOk(graphics, okX, okY, 1.0f / guiScale());
            graphics.pose().popPose();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (timerBeforeStart > 0) {
            return true;
        }
        if (button == 0) {
            int guiWidth = getMenuWidthPx();
            int guiHeight = getMenuHeightPx();
            int xPos = this.width / 2 - guiWidth / 2;
            int yPos = this.height / 2 - guiHeight / 2;

            if (isProfessionChooser) {
                int splitY = yPos + px(192);
                if (mouseY >= splitY && mouseY <= yPos + guiHeight) {
                    int[] professions = getProfessionPair(currentSkill, currentLevel);
                    if (mouseX >= xPos && mouseX < xPos + guiWidth / 2) {
                        playUiSound(ModSounds.BIG_DESELECT.get(), 1.0f, 1.0f);
                        ClientOvernightHandler.recordLocalProfessionChoice(professions[0]);
                        PacketDistributor.sendToServer(new OvernightProfessionChoicePayload(professions[0]));
                        this.onClose();
                        return true;
                    }
                    if (mouseX >= xPos + guiWidth / 2 && mouseX <= xPos + guiWidth) {
                        playUiSound(ModSounds.BIG_DESELECT.get(), 1.0f, 1.0f);
                        ClientOvernightHandler.recordLocalProfessionChoice(professions[1]);
                        PacketDistributor.sendToServer(new OvernightProfessionChoicePayload(professions[1]));
                        this.onClose();
                        return true;
                    }
                }
            } else {
                int[] okRect = getOkButtonRect(xPos, yPos, guiWidth, guiHeight);
                int okX = okRect[0];
                int okY = okRect[1];
                int okWidth = okRect[2];
                
                if (mouseX >= okX && mouseX <= okX + okWidth && mouseY >= okY && mouseY <= okY + okWidth) {
                    playUiSound(ModSounds.BIG_DESELECT.get(), 1.0f, 1.0f);
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public void onClose() {
        if (this.siblingScreens != null) {
            ClientOvernightHandler.openNextScreen("level_up");
        } else {
            super.onClose();
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (timerBeforeStart > 0 || isProfessionChooser) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<Component> getExtraInfoForLevel(int skill, int level) {
        List<Component> info = new ArrayList<>();
        switch (skill) {
            case 0 -> {
                info.add(Component.translatable("stardewcraft.levelup.extra.farming.watering_can"));
                info.add(Component.translatable("stardewcraft.levelup.extra.farming.hoe"));
            }
            case 1 -> info.add(Component.translatable("stardewcraft.levelup.extra.fishing"));
            case 2 -> {
                info.add(Component.translatable("stardewcraft.levelup.extra.foraging.axe"));
                if (level == 1) {
                    info.add(Component.translatable("stardewcraft.levelup.extra.foraging.wild_berry"));
                } else if (level == 4 || level == 8) {
                    info.add(Component.translatable("stardewcraft.levelup.extra.foraging.berry"));
                }
            }
            case 3 -> info.add(Component.translatable("stardewcraft.levelup.extra.mining"));
            case 4 -> info.add(Component.translatable("stardewcraft.levelup.extra.combat"));
            default -> {
            }
        }
        return List.copyOf(info);
    }

    private Component getSkillName(int skill) {
        return switch (skill) {
            case 0 -> Component.translatable("stardewcraft.levelup.skill.farming");
            case 1 -> Component.translatable("stardewcraft.levelup.skill.fishing");
            case 2 -> Component.translatable("stardewcraft.levelup.skill.foraging");
            case 3 -> Component.translatable("stardewcraft.levelup.skill.mining");
            case 4 -> Component.translatable("stardewcraft.levelup.skill.combat");
            default -> Component.translatable("stardewcraft.levelup.skill.unknown");
        };
    }

    private void drawSkillIcon(GuiGraphics graphics, int skill, int x, int y) {
        ResourceLocation icon = switch (skill) {
            case 0 -> TEX_ICON_FARMING;
            case 1 -> TEX_ICON_FISHING;
            case 2 -> TEX_ICON_FORAGING;
            case 3 -> TEX_ICON_MINING;
            default -> TEX_ICON_OTHER;
        };
        graphics.setColor(1.0F, 1.0F, 1.0F, 0.88f);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(s4(), s4(), 1.0f);
        graphics.blit(icon, 0, 0, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private int[] getProfessionPair(int skill, int level) {
        if (level == 5) {
            return switch (skill) {
                case 0 -> new int[]{0, 1};
                case 1 -> new int[]{6, 7};
                case 2 -> new int[]{12, 13};
                case 3 -> new int[]{18, 19};
                case 4 -> new int[]{24, 25};
                default -> new int[]{0, 1};
            };
        }

        SkillType skillType = mapSkill(skill);
        ProfessionType[] level5Choices = ProfessionType.getLevel5Options(skillType);
        if (ClientOvernightHandler.hasLocalProfession(level5Choices[0]) || ClientPlayerDataCache.hasProfession(level5Choices[0])) {
            ProfessionType[] level10 = ProfessionType.getLevel10Options(skillType, level5Choices[0]);
            return new int[]{level10[0].getId(), level10[1].getId()};
        }
        if (ClientOvernightHandler.hasLocalProfession(level5Choices[1]) || ClientPlayerDataCache.hasProfession(level5Choices[1])) {
            ProfessionType[] level10 = ProfessionType.getLevel10Options(skillType, level5Choices[1]);
            return new int[]{level10[0].getId(), level10[1].getId()};
        }

        return switch (skill) {
            case 0 -> new int[]{2, 3};
            case 1 -> new int[]{8, 9};
            case 2 -> new int[]{14, 15};
            case 3 -> new int[]{20, 21};
            case 4 -> new int[]{26, 27};
            default -> new int[]{2, 3};
        };
    }

    private SkillType mapSkill(int skill) {
        return switch (skill) {
            case 0 -> SkillType.FARMING;
            case 1 -> SkillType.FISHING;
            case 2 -> SkillType.FORAGING;
            case 3 -> SkillType.MINING;
            case 4 -> SkillType.COMBAT;
            default -> SkillType.FARMING;
        };
    }

    private Component getProfessionName(int profession) {
        ProfessionType type = ProfessionType.fromId(profession);
        if (type == null) {
            return Component.translatable("stardewcraft.levelup.profession.unknown.name");
        }
        return Component.translatable(type.getTranslationKey());
    }

    private Component getProfessionDesc(int profession) {
        ProfessionType type = ProfessionType.fromId(profession);
        if (type == null) {
            return Component.translatable("stardewcraft.levelup.profession.unknown.desc");
        }
        return Component.translatable(type.getDescriptionTranslationKey());
    }

    private record RecipeDisplayEntry(
        Component displayName,
        ItemStack icon,
        boolean cooking,
        boolean bigCraftable
    ) {
    }

    private List<RecipeDisplayEntry> getUnlockedRecipeEntries() {
        List<RecipeDisplayEntry> entries = new ArrayList<>();
        SkillType skillType = mapSkill(currentSkill);
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        // 1) unlock_sources.json (新系统)
        UnlockSourceData.UnlockBundle sourceBundle = UnlockSourceData.getSkillLevelUnlocks(skillType, currentLevel);
        seen.addAll(sourceBundle.recipes());

        // 2) skill_level_recipe_unlocks.json (遗留系统)
        for (String id : SkillLevelRecipeUnlocks.getUnlocks(skillType, currentLevel)) {
            seen.add(id);
        }

        // 3) vanilla_crafting_recipes.json — 匹配 "s <skill> <level>" 条件
        String skillName = skillType.getName().toLowerCase(java.util.Locale.ROOT);
        for (StardewCraftingRecipeData.RecipeEntry recipe : StardewCraftingRecipeData.getRecipes()) {
            String cond = recipe.unlockCondition();
            if (cond == null || cond.isBlank()) continue;
            String[] parts = cond.trim().split("\\s+");
            if (parts.length >= 3 && "s".equalsIgnoreCase(parts[0])
                    && skillName.equalsIgnoreCase(parts[1])) {
                try {
                    if (Integer.parseInt(parts[2]) == currentLevel) {
                        seen.add(recipe.id());
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        for (String recipeId : seen) {
            if (recipeId == null || recipeId.isBlank()) continue;
            ResourceLocation definitionId = RecipeIdNormalizer.definitionId(recipeId);
            boolean cooking = definitionId != null
                && VanillaCookingRecipeData.getDefinition(definitionId).isPresent();
            ItemStack icon = cooking
                ? VanillaCookingRecipeData.getOutputStack(definitionId, 1)
                : StardewCraftingRecipeData.getOutputStack(recipeId);
            // SDV parity: 菜单里显示的是配方产物的物品名（不是配方 ID）。
            // 回退到 prettified id 以应对找不到产物的旧配方。
            Component name = icon.isEmpty()
                    ? Component.literal(prettifyRecipeId(recipeId))
                    : icon.getHoverName();
            entries.add(new RecipeDisplayEntry(
                name,
                icon,
                cooking,
                !cooking && StardewCraftingRecipeData.isBigCraftable(recipeId)
            ));
        }
        return entries;
    }

    private static String prettifyRecipeId(String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) sb.append(p.substring(1));
        }
        return sb.toString();
    }

    private void drawWrappedText(GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color, int lineStep) {
        List<FormattedCharSequence> lines = this.font.split(text, Math.max(1, maxWidth));
        int lineY = y;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, lineY, color, false);
            lineY += lineStep;
        }
    }

    private static class LittleStar {
        private static final int FRAME_MS = 80;
        private static final int FRAME_COUNT = 7;
        private static final int SIZE = 5;

        private final int x;
        private final int y;
        private final long startMs;

        private LittleStar(int x, int y, long startMs) {
            this.x = x;
            this.y = y;
            this.startMs = startMs;
        }

        private boolean isDone(long now) {
            return now - startMs >= (long) FRAME_MS * FRAME_COUNT;
        }

        private void draw(GuiGraphics graphics, long now, float guiScale) {
            long age = now - startMs;
            int frame = (int) (age / FRAME_MS);
            if (frame < 0 || frame >= FRAME_COUNT) {
                return;
            }
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            graphics.pose().scale(4.0f / guiScale, 4.0f / guiScale, 1.0f);
            graphics.blit(TEX_LITTLE_STAR, 0, 0, frame * SIZE, 0, SIZE, SIZE, 35, 5);
            graphics.pose().popPose();
        }
    }
}
