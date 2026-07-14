package com.stardew.craft.client.gui;

import com.stardew.craft.client.ClientPlayerDataCache;
import com.stardew.craft.client.CookingIngredientAvailabilityCache;
import net.minecraft.client.resources.language.I18n;
import com.mojang.blaze3d.systems.RenderSystem;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.client.gui.common.CommonGuiTextures;
import com.stardew.craft.cooking.service.VanillaCookingRecipeData;
import com.stardew.craft.api.v1.production.StardewCookingIngredient;
import com.stardew.craft.item.cooking.CookingDishItem;
import com.stardew.craft.menu.CookingPotMenu;
import com.stardew.craft.network.payload.CookingPotCookSubmitPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("null")
public class CookingPotScreen extends AbstractContainerScreen<CookingPotMenu> {
    private static final int UI_WIDTH = 360;
    private static final int UI_HEIGHT = 260;

    private static final int BG_OVERLAY = 0xD8111116;
    private static final int TITLE_COLOR = 0xFFF7F2DB; 
    private static final int TEXT_MUTED = 0xFFA0A0A0;
    private static final ResourceLocation ENERGY_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/energy.png");
    private static final ResourceLocation HEALTH_ICON = ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/health.png");

    private final List<ItemStack> availableRecipes = new ArrayList<>();
    private final List<ResourceLocation> recipeIds = new ArrayList<>();
    private int recipePage = 0;

    private int currentFocusIndex = 0;
    private float[] itemHoverScales = new float[45];
    private float showcaseAlpha = 1.0f;

    public CookingPotScreen(CookingPotMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = UI_WIDTH;
        this.imageHeight = UI_HEIGHT;
    }

    private int getGridX() {
        int startIndex = recipePage * 45;
        int pageSize = Math.max(0, Math.min(45, availableRecipes.size() - startIndex));
        int cols = Math.min(9, Math.max(1, pageSize));
        return this.leftPos + 156 + (204 - cols * 22) / 2;
    }

    private int getGridY() {
        int startIndex = recipePage * 45;
        int pageSize = Math.max(0, Math.min(45, availableRecipes.size() - startIndex));
        int rows = Math.max(1, (pageSize + 8) / 9);
        return this.topPos + 24 + (142 - rows * 22) / 2;
    }

    @Override
    protected void init() {
        super.init();
        availableRecipes.clear();
        recipeIds.clear();

        List<ResourceLocation> allIds = new ArrayList<>();
        for (ResourceLocation recipeId : VanillaCookingRecipeData.getRecipeIds()) {
            if (!VanillaCookingRecipeData.getOutputStack(recipeId, 1).isEmpty()) allIds.add(recipeId);
        }

        allIds.sort((r1, r2) -> {
            boolean unlocked1 = isUnlocked(r1);
            boolean unlocked2 = isUnlocked(r2);
            boolean craftable1 = canCraft(r1);
            boolean craftable2 = canCraft(r2);

            int rank1 = craftable1 ? 1 : (unlocked1 ? 2 : 3);
            int rank2 = craftable2 ? 1 : (unlocked2 ? 2 : 3);

            if (rank1 != rank2) return Integer.compare(rank1, rank2);
            return r1.toString().compareTo(r2.toString());
        });

        for (ResourceLocation rl : allIds) {
            availableRecipes.add(VanillaCookingRecipeData.getOutputStack(rl, 1));
            recipeIds.add(rl);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        
        int gridX = getGridX();
        int gridY = getGridY();
        int startIndex = recipePage * 45;
        
        for (int i = 0; i < 45; i++) {
            int cx = gridX + (i % 9) * 22;
            int cy = gridY + (i / 9) * 22;
            int dataIndex = startIndex + i;
            if (dataIndex >= availableRecipes.size()) break;
            
            if (mouseX >= cx && mouseX <= cx + 16 && mouseY >= cy && mouseY <= cy + 16) {
                ResourceLocation rl = recipeIds.get(dataIndex);
                if (!isUnlocked(rl)) {
                    List<Component> tooltip = List.of(Component.literal("???"));
                    g.renderTooltip(this.font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                } else {
                    g.renderTooltip(this.font, availableRecipes.get(dataIndex), mouseX, mouseY);
                }
                break;
            }
        }
        
        if (currentFocusIndex >= 0 && currentFocusIndex < availableRecipes.size()) {
            ResourceLocation recipeId = recipeIds.get(currentFocusIndex);
            List<StardewCookingIngredient> reqs = VanillaCookingRecipeData.getRequirements(recipeId);
            if (!reqs.isEmpty()) {
                int startY = this.topPos + 10 + 128;
                int exactCount = Math.min(4, reqs.size());
                int reqSpacing = 28;
                int totalReqWidth = (exactCount - 1) * reqSpacing + 16;
                int startX = this.leftPos + 8 + (130 - totalReqWidth) / 2;
                
                for (int i = 0; i < exactCount; i++) {
                    int rx = startX + i * reqSpacing;
                    if (mouseX >= rx && mouseX <= rx + 16 && mouseY >= startY && mouseY <= startY + 16) {
                        g.renderTooltip(this.font, resolveRequirementIcon(reqs.get(i)), mouseX, mouseY);
                        break;
                    }
                }
            }
        }

        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        g.fill(x - 20, y - 10, x + imageWidth + 20, y + imageHeight + 10, BG_OVERLAY);

        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx, sy, sx + 16, sy + 16, 0x4A000000);
        }

        g.fillGradient(x + 144, y + 10, x + 145, y + 150, 0x40FFFFFF, 0x00FFFFFF);

        updateVisualFocus(mouseX, mouseY, partialTick);

        if (currentFocusIndex >= 0 && currentFocusIndex < availableRecipes.size()) {
            drawElegantShowcase(g, x + 8, y + 10, currentFocusIndex, partialTick);
        }

        int gridX = getGridX();
        int gridY = getGridY();
        int startIndex = recipePage * 45;

        for (int i = 0; i < 45; i++) {
            int dataIndex = startIndex + i;
            if (dataIndex >= availableRecipes.size()) break;

            int cx = gridX + (i % 9) * 22;
            int cy = gridY + (i / 9) * 22;

            ItemStack stack = availableRecipes.get(dataIndex);

            float scale = itemHoverScales[i];

            float lift = scale > 1.0f ? (scale - 1.0f) * -5.0f : 0.0f;
            int itemSize = CommonGuiTextures.itemSize(scale);
            int itemX = Math.round(cx + 8 - itemSize / 2.0f);
            int itemY = Math.round(cy + 8 + lift - itemSize / 2.0f);

            ResourceLocation rl = recipeIds.get(dataIndex);
            boolean unlocked = isUnlocked(rl);
            boolean craftable = canCraft(rl);

            if (!unlocked) {
                CommonGuiTextures.drawItemTint(g, stack, itemX, itemY, scale, 0.0F, 0.0F, 0.0F, 1.0F);
            } else if (!craftable) {
                CommonGuiTextures.drawItemTint(g, stack, itemX, itemY, scale, 0.35F, 0.35F, 0.35F, 1.0F);
            } else {
                CommonGuiTextures.drawItem(g, stack, itemX, itemY, scale);
            }
        }

        g.drawString(this.font, Component.translatable("stardewcraft.ui.cooking_pot.title"), x + 158, y + 6, TITLE_COLOR, true);

        int maxPage = Math.max(1, ((availableRecipes.size() - 1) / 45) + 1);
        String pageTxt = (recipePage + 1) + " / " + maxPage;
        g.drawString(this.font, pageTxt, x + 350 - this.font.width(pageTxt), y + 6, TEXT_MUTED, false);
    }

    private void updateVisualFocus(int mouseX, int mouseY, float partialTick) {
        int gridX = getGridX();
        int gridY = getGridY();

        for (int i = 0; i < 45; i++) {
            int cx = gridX + (i % 9) * 22;
            int cy = gridY + (i / 9) * 22;
            int dataIndex = recipePage * 45 + i;

            boolean isHover = dataIndex < availableRecipes.size() &&
                              mouseX >= cx - 2 && mouseX <= cx + 20 &&
                              mouseY >= cy - 2 && mouseY <= cy + 20;

            if (isHover) {
                if (currentFocusIndex != dataIndex) {
                    currentFocusIndex = dataIndex;
                    showcaseAlpha = 0.0f;
                }
                itemHoverScales[i] = Mth.lerp(0.3f, itemHoverScales[i], 1.45f);
            } else {
                itemHoverScales[i] = Mth.lerp(0.15f, itemHoverScales[i], 1.0f);
            }
        }

        if (showcaseAlpha < 1.0f) {
            showcaseAlpha = Mth.lerp(0.1f, showcaseAlpha, 1.0f);
        }
    }

    private void drawElegantShowcase(GuiGraphics g, int px, int py, int focusIdx, float partialTick) {
        int width = 130;
        ItemStack selStack = availableRecipes.get(focusIdx);
        ResourceLocation recipeId = recipeIds.get(focusIdx);
        boolean unlocked = isUnlocked(recipeId);

        int alpha = (int)(showcaseAlpha * 255);
        alpha = Mth.clamp(alpha, 0, 255);
        int alphaMask = alpha << 24;

        Component title = unlocked ? selStack.getHoverName().copy().withStyle(net.minecraft.ChatFormatting.BOLD) : Component.literal("???").withStyle(net.minecraft.ChatFormatting.BOLD);
        String printTitle = title.getString();
        
        g.pose().pushPose();
        float titleScale = 1.3f;
        int scaledWidth = (int)(width / titleScale);
        if (this.font.width(printTitle) > scaledWidth - 4) {
            printTitle = this.font.plainSubstrByWidth(printTitle, scaledWidth - 12) + "...";
        }
        int tx = px + (width - (int)(this.font.width(printTitle) * titleScale)) / 2;
        g.pose().translate(tx, py, 0);
        g.pose().scale(titleScale, titleScale, 1.0f);
        
        // 🌟 视觉优化：标题流光溢彩（在暗金与琥珀金之间自然呼吸）
        int shimmerR = 255;
        int shimmerG = 200 + (int)(30 * Math.sin(System.currentTimeMillis() / 250.0));
        int shimmerB = 100;
        int shimmerColor = (shimmerR << 16) | (shimmerG << 8) | shimmerB;
        g.drawString(this.font, printTitle, 0, 0, alphaMask | shimmerColor, true);
        g.pose().popPose();

        if (unlocked) {
            String descKey = selStack.getItem().getDescriptionId() + ".desc";
            if (net.minecraft.client.resources.language.I18n.exists(descKey)) {
            Component desc = Component.translatable(descKey);
            List<FormattedCharSequence> descLines = this.font.split(desc, width - 6);
            int descY = py + 16;
            for (int i = 0; i < Math.min(3, descLines.size()); i++) {
                int dx = px + (width - this.font.width(descLines.get(i))) / 2;
                g.drawString(this.font, descLines.get(i), dx, descY, alphaMask | 0xFFAAAAAA, true);
                descY += this.font.lineHeight;
            }
            }
        }

        float floatY = (float)Math.sin((System.currentTimeMillis() % 6000) / 6000.0f * Math.PI * 2) * 5.0f;
        
        // 🌟 视觉优化：底部动态椭圆阴影，配合上下浮动产生真实的空间Z轴感
        float shadowScale = 1.0f - (floatY + 5.0f) / 20.0f;
        int shadowWidth = (int)(22 * shadowScale);
        int shadowAlpha = (int)(showcaseAlpha * 90 * shadowScale);
        g.fillGradient(px + 65 - shadowWidth, py + 88, px + 65 + shadowWidth, py + 93, (shadowAlpha << 24) | 0x000000, 0x00000000);

        float selectedScale = 4.0f;
        int selectedSize = CommonGuiTextures.itemSize(selectedScale);
        int selectedX = px + 65 - selectedSize / 2;
        int selectedY = Math.round(py + 62 + floatY - selectedSize / 2.0f);
        g.pose().pushPose();
        g.pose().translate(0, 0, 150);
        if (!unlocked) {
            CommonGuiTextures.drawItemTint(g, selStack, selectedX, selectedY, selectedScale, 0.0F, 0.0F, 0.0F, 1.0F);
        } else {
            CommonGuiTextures.drawItem(g, selStack, selectedX, selectedY, selectedScale);
        }
        g.pose().popPose();

        if (!unlocked) {
            Component unlockTxt = Component.translatable("stardewcraft.cooking.unlock_method",
                    Component.translatable("recipe.stardewcraft." + recipeId.getPath() + ".unlock_condition"));
            List<FormattedCharSequence> lines = this.font.split(unlockTxt, width - 6);
            int yPos = py + 110;
            for (FormattedCharSequence line : lines) {
                int dx = px + (width - this.font.width(line)) / 2;
                g.drawString(this.font, line, dx, yPos, alphaMask | 0xFFFFAA, true);
                yPos += this.font.lineHeight;
            }
            return;
        }

        Item item = selStack.getItem();
        int statY = py + 110;
        if (item instanceof CookingDishItem dish) {
            int e = dish.getEnergy(selStack);
            int h = dish.getHealth(selStack);
            List<CookingDishItem.DishBuff> buffs = dish.getBuffs();
            
            int statWidth = 0;
            if (e > 0) statWidth += 18 + this.font.width(String.valueOf(e));
            if (h > 0) statWidth += 18 + this.font.width(String.valueOf(h));
            
            for (int i = 0; i < Math.min(3, buffs.size()); i++) statWidth += 18;
            
            int curX = px + (width - statWidth) / 2;
            
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, showcaseAlpha); 
            
            if (e > 0) {
                g.blit(ENERGY_ICON, curX, statY - 2, 0, 0, 16, 16, 16, 16);
                g.drawString(this.font, String.valueOf(e), curX + 16, statY + 2, alphaMask | 0xFFFFFF, true);
                curX += 18 + this.font.width(String.valueOf(e));
            }
            if (h > 0) {
                g.blit(HEALTH_ICON, curX, statY - 2, 0, 0, 16, 16, 16, 16);
                g.drawString(this.font, String.valueOf(h), curX + 16, statY + 2, alphaMask | 0xFFFFFF, true);
                curX += 18 + this.font.width(String.valueOf(h));
            }
            
            for (int i = 0; i < Math.min(3, buffs.size()); i++) {
                CookingDishItem.DishBuff buff = buffs.get(i);
                ResourceLocation buffIcon = getStardewBuffIcon(buff.type());
                g.blit(buffIcon, curX, statY - 2, 0, 0, 18, 18, 18, 18);
                curX += 18;
            }
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }

        List<StardewCookingIngredient> reqs = VanillaCookingRecipeData.getRequirements(recipeId);
        if (!reqs.isEmpty()) {
            int startY = py + 128;
            int exactCount = Math.min(4, reqs.size());
            int reqSpacing = 28;
            int totalReqWidth = (exactCount - 1) * reqSpacing + 16;
            int startX = px + (width - totalReqWidth) / 2;

            for (int i = 0; i < exactCount; i++) {
                StardewCookingIngredient req = reqs.get(i);
                ItemStack icon = resolveRequirementIcon(req);
                int has = countMatching(req);
                int need = req.count();
                boolean enough = has >= need;

                int rx = startX + i * reqSpacing;
                
                float itemScale;
                float red;
                float green;
                float blue;
                if (enough) {
                    float beat = 1.0f + 0.04f * (float)Math.sin(System.currentTimeMillis() / 150.0);
                    itemScale = beat;
                    red = 1.0F;
                    green = 1.0F;
                    blue = 1.0F;
                } else {
                    itemScale = 0.85f;
                    red = 1.0F;
                    green = 0.4F;
                    blue = 0.4F;
                }
                int reqItemSize = CommonGuiTextures.itemSize(itemScale);
                int reqItemX = Math.round(rx + 8 - reqItemSize / 2.0f);
                int reqItemY = Math.round(startY + 8 - reqItemSize / 2.0f);
                g.pose().pushPose();
                g.pose().translate(0, 0, 50);
                CommonGuiTextures.drawItemTint(g, icon, reqItemX, reqItemY, itemScale, red, green, blue, showcaseAlpha);
                g.pose().popPose();

                String ratio = has + "/" + need;
                int ratioColor = enough ? (alphaMask | 0xEEFFEE) : (alphaMask | 0xFF8888);
                float rScale = 0.8f;
                g.pose().pushPose();
                g.pose().translate(rx + 8 - (this.font.width(ratio)*rScale)/2, startY + 16, 200);
                g.pose().scale(rScale, rScale, 1.0f);
                g.drawString(this.font, ratio, 0, 0, ratioColor, true);
                g.pose().popPose();
            }
        }
    }

    private ResourceLocation getStardewBuffIcon(CookingDishItem.BuffType type) {
        String tex = switch (type) {
            case FARMING -> "farmer_blessing";
            case FISHING -> "sea_king_blessing";
            case MINING -> "miner_blessing";
            case LUCK -> "spirit_blessing";
            case FORAGING -> "forager_blessing";
            case MAX_ENERGY -> "vigorous";
            case MAGNETIC_RADIUS -> "magnetism";
            case SPEED -> "speed";
            case DEFENSE -> "guardian_blessing";
            case ATTACK -> "warrior_blessing";
            default -> "spirit_blessing";
        };
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/mob_effect/" + tex + ".png");
    }

    private boolean canCraft(ResourceLocation recipeId) {
        if (!isUnlocked(recipeId)) return false;
        List<StardewCookingIngredient> reqs = VanillaCookingRecipeData.getRequirements(recipeId);
        for (StardewCookingIngredient req : reqs) {
            if (countMatching(req) < req.count()) {
                return false;
            }
        }
        return true;
    }

    private int countMatching(StardewCookingIngredient ingredient) {
        if (this.minecraft == null || this.minecraft.player == null) return 0;
        int count = 0;
        for (int i = 0; i < this.minecraft.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = this.minecraft.player.getInventory().getItem(i);
            if (!stack.isEmpty() && VanillaCookingRecipeData.matches(stack, ingredient)) {
                count += stack.getCount();
            }
        }
        return count + CookingIngredientAvailabilityCache.getFridgeTokenCount(ingredient.matcherKey());
    }

    private ItemStack resolveRequirementIcon(StardewCookingIngredient ingredient) {
        ItemStack icon = VanillaCookingRecipeData.getDisplayStack(ingredient);
        return icon.isEmpty() ? new ItemStack(Items.BARRIER) : icon;
    }

    private boolean isUnlocked(ResourceLocation recipeId) {
        return ClientPlayerDataCache.hasRecipe(VanillaCookingRecipeData.storageId(recipeId));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = this.leftPos;
        int y = this.topPos;

        if (button == 0 && mouseX >= x + 280 && mouseX <= x + 360 && mouseY >= y + 0 && mouseY <= y + 20) {
            if (hasShiftDown()) recipePage = Math.max(0, recipePage - 1);
            else recipePage = Math.min((availableRecipes.size() - 1) / 45, recipePage + 1);
            return true;
        }

        int gridX = getGridX();
        int gridY = getGridY();
        int startIndex = recipePage * 45;

        for (int i = 0; i < 45; i++) {
            int cx = gridX + (i % 9) * 22;
            int cy = gridY + (i / 9) * 22;
            int dataIndex = startIndex + i;

            if (dataIndex >= availableRecipes.size()) break;

            if (mouseX >= cx - 2 && mouseX <= cx + 20 && mouseY >= cy - 2 && mouseY <= cy + 20) {
                String recipeId = recipeIds.get(dataIndex).toString();
                if (isUnlocked(recipeIds.get(dataIndex))) {
                    int craftCount = button == 1 ? 5 : 1;
                    if (hasShiftDown()) craftCount = -1;

                    PacketDistributor.sendToServer(new CookingPotCookSubmitPayload(recipeId, craftCount));
                }
                return true;
            }
        }

        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double hDelta, double vDelta) {
        if (vDelta > 0 && recipePage > 0) {
            recipePage--;
            return true;
        } else if (vDelta < 0 && recipePage < (availableRecipes.size() - 1) / 45) {
            recipePage++;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, hDelta, vDelta);
    }
}
