package com.stardew.craft.client;

import com.stardew.craft.Config;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.economy.sell.ProfessionSellPriceService;
import com.stardew.craft.economy.sell.SellQuote;
import com.stardew.craft.economy.sell.SellSource;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.item.ModItems;
import com.stardew.craft.item.StardewBookItem;
import com.stardew.craft.item.weapon.IStardewWeapon;
import com.stardew.craft.item.weapon.WeaponData;
import com.stardew.craft.item.weapon.WeaponSkillData;
import com.stardew.craft.client.gui.menu.StardewGameMenuScreen;
import com.stardew.craft.client.hud.StardewHudLayout;
import com.stardew.craft.client.hud.StardewHudLayoutEditorScreen;
import com.stardew.craft.client.weapon.FireRingEffectClient;
import com.stardew.craft.core.ModTags;
import com.stardew.craft.network.payload.RequestNpcFriendshipOverviewPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.stardew.craft.combat.network.WeaponSkillUsePayload;

import java.util.List;
import java.util.regex.Pattern;
import java.util.Locale;

@EventBusSubscriber(modid = StardewCraft.MODID, value = Dist.CLIENT)
@SuppressWarnings("null")
public class ModClientEvents {

    private static final Pattern NEWLINE_SPLIT = Pattern.compile("(?:\\\\n|\\n)");

    @SubscribeEvent
    public static void onClientLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        com.stardew.craft.communitycenter.cutscene.ScreenFade.clear();
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        appendStardewCraftingConversionTooltip(stack, event.getToolTip());
        var stardewData = StardewItemDataApi.resolve(stack).orElse(null);
        if (stardewData != null) {
            IStardewItem legacyItem = stack.getItem() instanceof IStardewItem item ? item : null;
            if (stack.getItem() instanceof StardewBookItem) {
                return;
            }

            List<Component> tooltip = event.getToolTip();

            // 喷壶把“水量”存进耐久度系统里，因�?F3+H(高级提示) 会额外显示“耐久度：x/y”�?
            // 这会和我们自己的水量�?97/100)重复，所以这里把耐久度那一行过滤掉�?
            // 武器也不需要耐久度行（不可破坏），这里一起过滤�?
            if (stack.getItem() instanceof com.stardew.craft.item.tool.WateringCanItem
                    || stack.getItem() instanceof IStardewWeapon) {
                tooltip.removeIf(ModClientEvents::isDurabilityTooltipLine);
            }
            // 所有 Stardew 物品都隐藏 "Unbreakable" 提示行
            tooltip.removeIf(ModClientEvents::isUnbreakableTooltipLine);
            
            // 插入位置：紧跟在名字后面 (index 1)
            // 原版tooltip至少包含名字(index 0)
            int insertIdx = 1;
            if (tooltip.isEmpty()) insertIdx = 0; // 理论上不可能，防一�?
            
            // 临时列表，用于按顺序收集自定义tooltip
            java.util.List<Component> customLines = new java.util.ArrayList<>();
            
            // 描述：第一�?种类：XX
            String typeKey = StardewItemDataApi.getTypeKey(stack);
            if (!typeKey.isBlank() && !"stardewcraft.type.hidden".equals(typeKey)) {
                net.minecraft.ChatFormatting typeColor = net.minecraft.ChatFormatting.GREEN;
                Integer typeRgbColor = null;
                
                // 种子的种类颜色改成深绿色
                if (typeKey.contains("seed")) {
                    typeColor = net.minecraft.ChatFormatting.DARK_GREEN;
                } else if ("stardewcraft.type.resource".equals(typeKey)) {
                    // 资源：金�?
                    typeColor = net.minecraft.ChatFormatting.GOLD;
                } else if ("stardewcraft.type.mineral".equals(typeKey)) {
                    // 矿物：青�?
                    typeColor = net.minecraft.ChatFormatting.AQUA;
                } else if ("stardewcraft.type.artifact".equals(typeKey)) {
                    // 古物：深紫色
                    typeColor = net.minecraft.ChatFormatting.DARK_PURPLE;
                } else if ("stardewcraft.type.artisan_goods".equals(typeKey)) {
                    // 工匠物品：浅紫色
                    typeColor = net.minecraft.ChatFormatting.LIGHT_PURPLE;
                } else if ("stardewcraft.type.artisan_animal_quality".equals(typeKey)) {
                    // 动物工匠物品：与工匠物品一�?
                    typeColor = net.minecraft.ChatFormatting.LIGHT_PURPLE;
                } else if ("stardewcraft.type.animal_product".equals(typeKey)) {
                    // 动物产物：使用未占用的颜�?
                    typeColor = net.minecraft.ChatFormatting.BLUE;
                } else if ("stardewcraft.type.utility".equals(typeKey)) {
                    // 实用设施：黄色（粗体由下面统一加）
                    typeColor = net.minecraft.ChatFormatting.YELLOW;
                } else if ("stardewcraft.type.wizard_building".equals(typeKey)) {
                    typeRgbColor = 0xC995FF;
                } else if ("stardewcraft.type.furniture".equals(typeKey)) {
                    // 家具：青色
                    typeColor = net.minecraft.ChatFormatting.DARK_AQUA;
                } else if ("stardewcraft.type.special_furniture".equals(typeKey)) {
                    // 特殊家具：独立的暖紫色，不与普通家具共用类型色。
                    typeRgbColor = 0xB66FD3;
                } else if ("stardewcraft.type.furniture_painting".equals(typeKey)) {
                    typeRgbColor = 0x6AA8FF;
                } else if ("stardewcraft.type.festival_decoration".equals(typeKey)) {
                    // 节日装饰：紫色
                    typeColor = net.minecraft.ChatFormatting.LIGHT_PURPLE;
                } else if ("stardewcraft.type.scarecrow".equals(typeKey)) {
                    // 稻草人：稻草黄（区别于家具）
                    typeColor = net.minecraft.ChatFormatting.YELLOW;
                } else if ("stardewcraft.type.craftable".equals(typeKey)) {
                    // 打造物品：未占用的颜色
                    typeColor = net.minecraft.ChatFormatting.DARK_RED;
                } else if ("stardewcraft.type.fishing".equals(typeKey)) {
                    // 钓鱼：深蓝色
                    typeColor = net.minecraft.ChatFormatting.DARK_BLUE;
                } else if ("stardewcraft.type.quest".equals(typeKey)) {
                    typeColor = net.minecraft.ChatFormatting.BLUE;
                } else if ("stardewcraft.type.fertilizer".equals(typeKey)) {
                    // 肥料：深青色（棕色调�?
                    typeColor = net.minecraft.ChatFormatting.DARK_AQUA;
                } else if ("stardewcraft.type.cooking".equals(typeKey)
                    || "stardewcraft.type.festival_food".equals(typeKey)) {
                    // 食物：亮橙色系
                    typeColor = net.minecraft.ChatFormatting.GOLD;
                } else if ("stardewcraft.type.fruit".equals(typeKey)) {
                    // 水果：浅红色（与其他分类区分）
                    typeColor = net.minecraft.ChatFormatting.LIGHT_PURPLE;
                } else if ("stardewcraft.type.forage".equals(typeKey)) {
                    // 采集：绿色（自然系）
                    typeColor = net.minecraft.ChatFormatting.GREEN;
                } else if ("stardewcraft.type.cooking_ingredient".equals(typeKey)) {
                    // 烹饪原料：浅黄色（基础食材）
                    typeColor = net.minecraft.ChatFormatting.YELLOW;
                } else if ("stardewcraft.type.tool".equals(typeKey)
                        || typeKey.startsWith("stardewcraft.tool.")) {
                    // 农具：天蓝色
                    typeColor = net.minecraft.ChatFormatting.AQUA;
                } else if ("stardewcraft.type.trash".equals(typeKey)) {
                    // 垃圾：灰�?
                    typeColor = net.minecraft.ChatFormatting.GRAY;
                } else if (typeKey.startsWith("stardewcraft.type.weapon")) {
                    // Weapons: red.
                    typeColor = net.minecraft.ChatFormatting.RED;
                } else if ("stardewcraft.type.monster_loot".equals(typeKey)) {
                    // 怪物掉落：红色
                    typeColor = net.minecraft.ChatFormatting.RED;
                } else if ("stardewcraft.type.ring".equals(typeKey)) {
                    // 戒指：深紫色
                    typeColor = net.minecraft.ChatFormatting.DARK_PURPLE;
                } else if ("stardewcraft.type.boots".equals(typeKey)) {
                    // 靴子：深绿色
                    typeColor = net.minecraft.ChatFormatting.DARK_GREEN;
                } else if ("stardewcraft.type.hat".equals(typeKey)) {
                    // 帽子：单独的装饰品颜色
                    typeRgbColor = 0x4CB6B8;
                } else if ("stardewcraft.type.shirt".equals(typeKey)) {
                    typeRgbColor = 0x78C86A;
                } else if ("stardewcraft.type.pants".equals(typeKey)) {
                    typeRgbColor = 0x4F7FE8;
                } else if ("stardewcraft.type.trinket".equals(typeKey)) {
                    typeColor = net.minecraft.ChatFormatting.LIGHT_PURPLE;
                } else if ("stardewcraft.type.magic".equals(typeKey)) {
                    // 魔法：紫色
                    typeColor = net.minecraft.ChatFormatting.LIGHT_PURPLE;
                }

                if (stack.getItem() == ModItems.SKULL_KEY.get()) {
                    // 骷髅钥匙：类型名用流动紫金高光
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                            .withStyle(ChatFormatting.WHITE)
                            .append(SkullKeyClientFx.flowingTypeLabel(
                                    Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.MAGNIFYING_GLASS.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                            .withStyle(ChatFormatting.WHITE)
                            .append(MagnifyingGlassClientFx.flowingTypeLabel(
                                    Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.SPECIAL_CHARM.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                            .withStyle(ChatFormatting.WHITE)
                            .append(SpecialCharmClientFx.flowingTypeLabel(
                                    Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.BEAR_KNOWLEDGE.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                            .withStyle(ChatFormatting.WHITE)
                            .append(BearKnowledgeClientFx.flowingTypeLabel(
                                    Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.DWARVISH_TRANSLATION_GUIDE.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(DwarvishTranslationGuideClientFx.flowingTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.RUSTY_KEY.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                            .withStyle(ChatFormatting.WHITE)
                            .append(RustyKeyClientFx.flowingTypeLabel(
                                    Component.translatable(typeKey).getString())));
                } else if (stack.getItem() instanceof com.stardew.craft.item.PowerSpecialItem powerItem) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                            .withStyle(ChatFormatting.WHITE)
                            .append(PowerSpecialItemClientFx.flowingTypeLabel(
                                    Component.translatable(typeKey).getString(), powerItem.theme())));
                } else if (stack.getItem() instanceof com.stardew.craft.item.WizardBuildingItem wizardBuilding) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                            .withStyle(ChatFormatting.WHITE)
                            .append(WizardBuildingItemClientFx.flowingTypeLabel(
                                    Component.translatable(typeKey).getString(), wizardBuilding.kind())));
                } else if (stack.getItem() == ModItems.WARP_WAND.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(WarpWandClientFx.flowingTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.STARDROP.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(SpecialConsumableClientFx.stardropTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.STARDROP_TEA.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(SpecialConsumableClientFx.stardropTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.IRIDIUM_MILK.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(SpecialConsumableClientFx.iridiumMilkTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.LUCKY_PURPLE_SHORTS.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(SpecialConsumableClientFx.luckyPurpleShortsTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.MONEY_CONTRACT.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(SpecialConsumableClientFx.moneyContractTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.AUCTION_PADDLE.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(SpecialConsumableClientFx.auctionPaddleTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.GOLDEN_PUMPKIN.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(SpecialConsumableClientFx.goldenPumpkinTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() == ModItems.GALAXY_SOUL.get()
                        || stack.getItem() == ModItems.MAGIC_ROCK_CANDY.get()) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(GalaxySoulClientFx.prismaticTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else if (stack.getItem() instanceof com.stardew.craft.item.trinket.StardewTrinketItem) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix")
                        .withStyle(ChatFormatting.WHITE)
                        .append(TrinketClientFx.trinketTypeLabel(
                            Component.translatable(typeKey).getString())));
                } else {
                    MutableComponent typeLabel = Component.translatable(typeKey);
                    if (typeRgbColor != null) {
                        int rgb = typeRgbColor;
                        typeLabel.withStyle(style -> style
                                .withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb))
                                .withBold(true));
                    } else {
                        typeLabel.withStyle(typeColor, ChatFormatting.BOLD);
                    }
                    customLines.add(Component.translatable("stardewcraft.tooltip.type_prefix") // Type label in white, non-bold.
                            .withStyle(ChatFormatting.WHITE)
                            .append(typeLabel));
                }
            }

            SellQuote quote = ProfessionSellPriceService.quoteItemForProfessionNames(
                new java.util.HashSet<>(ClientPlayerDataCache.getProfessions()),
                ClientPlayerDataCache.getMailFlags(), ClientPlayerDataCache.getSpecialItems(),
                stack, SellSource.SHOP_COUNTER);
            int sellPrice = quote.finalUnitPrice();
            boolean hidePriceLine = "stardewcraft.type.special".equals(typeKey)
                || stack.getItem() == ModItems.SECRET_NOTE.get()
                || stack.getItem() == ModItems.WARP_WAND.get()
                || stack.getItem() == ModItems.STARDROP.get()
                || stack.getItem() == ModItems.IRIDIUM_MILK.get();
            if (!hidePriceLine) {
                if (sellPrice > 0) {
                    customLines.add(Component.translatable("stardewcraft.tooltip.price")
                        .append(": ")
                        .append(Component.literal(TooltipConstants.ICON_MONEY).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" " + sellPrice + " G").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
                } else {
                    customLines.add(Component.translatable("stardewcraft.tooltip.price")
                        .append(": ")
                        .append(Component.literal("X ").withStyle(ChatFormatting.RED))
                        .append(Component.translatable("stardewcraft.tooltip.not_sellable")
                            .withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                }
            }

            if (stack.getCount() > 1 && sellPrice > 0) {
                long totalPrice = (long) sellPrice * stack.getCount();
                customLines.add(Component.translatable("stardewcraft.tooltip.total_price")
                    .append(": ")
                    .append(Component.literal(TooltipConstants.ICON_MONEY).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(" " + totalPrice + " G").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            }
            
            // Tool: watering can extra info (before description).
            if (stack.getItem() instanceof com.stardew.craft.item.tool.WateringCanItem) {
                customLines.add(Component.literal(TooltipConstants.MARKER_WATER_AMOUNT));
                customLines.add(Component.literal(TooltipConstants.MARKER_MAX_CHARGE_RANGE));
            }

            // 工具：锄头的额外信息（最大蓄力范围）
            if (stack.getItem() instanceof com.stardew.craft.item.tool.HoeItem) {
                customLines.add(Component.literal(TooltipConstants.MARKER_MAX_CHARGE_RANGE));
            }

			// 工具：鱼竿的鱼饵/渔具槽位（分成两行独�?marker�?
			if (stack.getItem() instanceof com.stardew.craft.item.tool.FishingRodItem rod) {
				if (rod.canUseBait()) {
					customLines.add(Component.literal(TooltipConstants.MARKER_FISHING_ROD_BAIT));
				}
				if (rod.getTackleSlots() > 0) {
					customLines.add(Component.literal(TooltipConstants.MARKER_FISHING_ROD_TACKLE));
				}
			}
            
            boolean isMuseumItem = isMuseumItem(typeKey);
            boolean donated = !isMuseumItem || isMuseumDonated(stack);

            // 第四行：功能性描�?(灰色细体) / 未捐赠提�?
            if (!donated) {
                customLines.add(Component.translatable("stardewcraft.tooltip.museum.gunther")
                        .withStyle(ChatFormatting.GRAY));
            } else {
                String descKey = stack.getDescriptionId() + ".desc";
                // Fallback: BlockItem returns "block.stardewcraft.xxx" but desc may be under "item.stardewcraft.xxx.desc"
                if (!net.minecraft.client.resources.language.I18n.exists(descKey) && descKey.startsWith("block.")) {
                    descKey = "item." + descKey.substring("block.".length());
                }
                if (net.minecraft.client.resources.language.I18n.exists(descKey)) {
                    String descText = net.minecraft.client.resources.language.I18n.get(descKey);
                    String[] lines = NEWLINE_SPLIT.split(descText, -1);
                    for (int i = 0; i < lines.length; i++) {
                        String line = lines[i];
                        if (line == null || line.isEmpty()) {
                            customLines.add(Component.empty());
                            continue;
                        }
                        if (stack.getItem() == ModItems.GALAXY_SOUL.get()
                                || stack.getItem() == ModItems.MAGIC_ROCK_CANDY.get()) {
                            customLines.add(GalaxySoulClientFx.prismaticDescriptionLine(line, i * 0.18F));
                        } else {
                            customLines.add(Component.literal(line).withStyle(i == 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
                        }
                    }
                }

                // 第五行：Lore描述 (深灰色细�?
                String flavorKey = stack.getDescriptionId() + ".flavor";
                if (!net.minecraft.client.resources.language.I18n.exists(flavorKey) && flavorKey.startsWith("block.")) {
                    flavorKey = "item." + flavorKey.substring("block.".length());
                }
                if (net.minecraft.client.resources.language.I18n.exists(flavorKey)) {
                    String flavorText = net.minecraft.client.resources.language.I18n.get(flavorKey);
                    String[] lines = NEWLINE_SPLIT.split(flavorText, -1);
                    for (String line : lines) {
                        if (line == null || line.isEmpty()) {
                            customLines.add(Component.empty());
                            continue;
                        }
                        customLines.add(Component.literal(line).withStyle(ChatFormatting.DARK_GRAY));
                    }
                }
            }
            
            // 第六行：空行 + 恢复数�?(如果是食�?
            if (stardewData.isFood()) {
                int energy = stardewData.energy();
                int health = stardewData.health();
                
                // -300 表示完全不可食用，不显示任何图标（与原版一致）
                if (energy == -300) {
                    // 不显示任何东�?
                } else if (energy >= 0) {
                    // 正面效果：显示能量和生命
                    if (energy > 0 || health > 0) {
                        customLines.add(Component.empty());
                        MutableComponent statsLine = Component.empty();
                        
                        if (energy > 0) {
                            statsLine.append(Component.literal(TooltipConstants.ICON_ENERGY + " " + energy).withStyle(ChatFormatting.GREEN));
                        }
                        
                        if (health > 0) {
                            if (energy > 0) statsLine.append("  "); // 间距
                            statsLine.append(Component.literal(TooltipConstants.ICON_HEALTH + " " + health).withStyle(ChatFormatting.LIGHT_PURPLE));
                        }
                        
                        customLines.add(statsLine);

                        // 吃完�?Buff（若有）
                        java.util.List<Component> afterEat = legacyItem == null
                                ? java.util.List.of()
                                : legacyItem.getAfterEatTooltipLines(stack);
                        if (!afterEat.isEmpty()) {
                            customLines.add(Component.empty());
                            customLines.add(Component.translatable("stardewcraft.tooltip.after_eaten").withStyle(ChatFormatting.GRAY));
                            customLines.addAll(afterEat);
                        }
                    }
                } else {
                    // 负面效果：只显示能量（红色闪电），与原版一�?
                    customLines.add(Component.empty());
                    customLines.add(Component.literal(TooltipConstants.ICON_ENERGY_NEGATIVE + " " + energy).withStyle(ChatFormatting.RED));
                }
            }
            
            // 批量插入到指定位�?
            tooltip.addAll(insertIdx, customLines);
        }
    }

    private static void appendStardewCraftingConversionTooltip(ItemStack stack, List<Component> tooltip) {
        if (stack.isEmpty() || tooltip == null) {
            return;
        }

        Component line = null;
        if (stack.is(ModTags.Items.CRAFTING_HARDWOOD_LOGS)) {
            line = Component.translatable("stardewcraft.tooltip.convertible_hardwood_log_to_hardwood_in_stardew_crafting")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        } else if (stack.is(ModTags.Items.CRAFTING_HARDWOOD_PLANKS)) {
            line = Component.translatable("stardewcraft.tooltip.convertible_hardwood_planks_to_hardwood_in_stardew_crafting")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        } else if (stack.is(ModTags.Items.CRAFTING_LOGS)) {
            line = Component.translatable("stardewcraft.tooltip.convertible_log_to_wood_in_stardew_crafting")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        } else if (stack.is(ModTags.Items.CRAFTING_PLANKS)) {
            line = Component.translatable("stardewcraft.tooltip.convertible_planks_to_wood_in_stardew_crafting")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        }
        if (line == null) {
            return;
        }

        int insertIdx = tooltip.isEmpty() ? 0 : 1;
        tooltip.add(insertIdx, line);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        com.stardew.craft.client.casino.CasinoNpcVisibilityClient.tick();
        com.stardew.craft.client.render.MapInteractionHintRenderer.onClientTick();
        com.stardew.craft.client.sound.StardewMusicManager.onClientTick();
        com.stardew.craft.client.mastery.MasteryCandleFlameClient.onClientTick();
        com.stardew.craft.client.emote.EmoteBubbleClientState.tick();
        com.stardew.craft.client.fishpond.ClientFishPondJumpEffects.onClientTick(event);
        com.stardew.craft.client.emote.EmoteWheelClient.onClientTick();
        com.stardew.craft.communitycenter.cutscene.ScreenFade.tick();
        com.stardew.craft.client.combat.DamageNumberClient.onClientTick(event);
        com.stardew.craft.client.weapon.SkillFailShakeState.tick();
        com.stardew.craft.client.weapon.CameraShakeState.tick();
        com.stardew.craft.client.weapon.presentation.SkillPresentationClient.onClientTick(event);
        com.stardew.craft.client.weapon.TideMarkClientState.onClientTick(event);
        com.stardew.craft.client.weapon.LavaKatanaMarkClientState.onClientTick(event);
        com.stardew.craft.client.weapon.OssifiedMarkClientState.onClientTick(event);
        com.stardew.craft.client.weapon.ElfBladeMarkClientState.onClientTick(event);
        com.stardew.craft.client.weapon.TemplarMarkClientState.onClientTick(event);
        com.stardew.craft.client.weapon.YetiMarkClientState.onClientTick(event);
        com.stardew.craft.client.weapon.GalaxyDaggerMarkClientState.onClientTick(event);
        com.stardew.craft.client.weapon.InfinityDaggerMarkClientState.onClientTick(event);
        com.stardew.craft.client.weapon.YetiFreezeClientState.onClientTick(event);
        com.stardew.craft.client.weapon.CrystalDaggerLayerClientState.onClientTick(event);
        com.stardew.craft.client.weapon.WickedKrisPoisonClientState.onClientTick(event);
        com.stardew.craft.client.weapon.DwarfDaggerThrustClientState.onClientTick(event);
        com.stardew.craft.client.weapon.DwarfDaggerRushClientState.onClientTick(event);
        com.stardew.craft.client.weapon.IridiumNeedleFrenzyClientState.onClientTick(event);
        com.stardew.craft.client.weapon.DragontoothShivBreathClientState.onClientTick(event);
        com.stardew.craft.client.weapon.DashMovementClientState.onClientTick(event);
        com.stardew.craft.client.weapon.WaterRingEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.OssifiedExecutionCircleEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.HolyBladeRingEffectClient.onClientTick(event);
        FireRingEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.ObsidianCrackEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.SteelFalchionLineEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.ShockwaveRingEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.AccretionDiskEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.SingularityCoreEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.SingularityRuneEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.RiftPathEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.StarfallMeteorEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.BlackHolePostEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.StarfallShockwavePostEffectClient.onClientTick(event);
        com.stardew.craft.client.weapon.TemplarVowClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.InsectEyeStanceClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.ObsidianResonanceClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.SteelFalchionTraceClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.DarkSwordBloodDebtClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.DarkSwordBloodMoonClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.LavaKatanaReverbClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.DwarfFortressClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.WindSpireClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.ElfBladeClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.BrokenTridentCatchClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.DwarfDaggerThrustClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.DwarfDaggerRushClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.IridiumNeedleFrenzyClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.IridiumNeedleCritClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.DragontoothShivBreathClientState.clearIfNoPlayer();
        com.stardew.craft.client.weapon.DashMovementClientState.clearIfNoPlayer();

        // ── Cutscene event player tick ──
        com.stardew.craft.cutscene.runtime.EventPlayer.get().tick();
        com.stardew.craft.cutscene.runtime.EventCameraController.tick();
        com.stardew.craft.cutscene.runtime.EventScreenFade.tick();
        com.stardew.craft.cutscene.runtime.EventTriggerChecker.tick();
        com.stardew.craft.client.ritual.GalaxySwordRitualClientState.tick();

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }

        // ── During cutscene: suppress all player input, freeze movement ──
        if (com.stardew.craft.cutscene.runtime.EventPlayer.get().isPlayerFrozen()
            || com.stardew.craft.client.ritual.GalaxySwordRitualClientState.isPlayerFrozen()) {
            // The server places cutscene players in spectator mode; keep the client-side
            // body fixed too so local camera/player commands never accumulate fall state.
            mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            mc.player.fallDistance = 0.0F;
            mc.player.xxa = 0;
            mc.player.zza = 0;
            // Absorb continuous attacks block breaking during cutscenes
            mc.options.keyAttack.setDown(false);
            mc.options.keyUse.setDown(false);
            mc.options.keyPickItem.setDown(false);
            // Consume vanilla attack/use/pick so player can't break/place/interact
            while (mc.options.keyAttack.consumeClick()) {}
            while (mc.options.keyUse.consumeClick()) {}
            while (mc.options.keyPickItem.consumeClick()) {}
            // Consume all custom key clicks so menus/skills don't fire
            while (ModKeyMappings.GAME_MENU.consumeClick()) {}
            while (ModKeyMappings.QUEST_LOG.consumeClick()) {}
            drainSkillKeyClicks();
            return;
        }

        while (ModKeyMappings.GAME_MENU.consumeClick()) {
            if (mc.screen == null) {
                PacketDistributor.sendToServer(new RequestNpcFriendshipOverviewPayload());
                PacketDistributor.sendToServer(new com.stardew.craft.network.payload.OpenStardewGameMenuPayload());
            }
        }

        while (ModKeyMappings.QUEST_LOG.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new com.stardew.craft.client.gui.quest.QuestLogScreen());
            }
        }

        if (mc.screen != null) {
            drainSkillKeyClicks();
            return;
        }

        ItemStack stack = mc.player.getMainHandItem();
        IStardewWeapon weaponItem = stack.getItem() instanceof IStardewWeapon weapon ? weapon : null;
        WeaponData data = weaponItem == null ? null : weaponItem.getWeaponData();
        boolean hasPublicMinor = com.stardew.craft.combat.skill.WeaponSkillDispatcher.hasPublicSkill(stack, false);
        boolean hasPublicMajor = com.stardew.craft.combat.skill.WeaponSkillDispatcher.hasPublicSkill(stack, true);
        boolean hasLegacyMinor = data != null && data.getSkill1() != null;
        boolean hasLegacyMajor = data != null && data.getSkill2() != null;
        if (!hasPublicMinor && !hasPublicMajor && !hasLegacyMinor && !hasLegacyMajor) {
            drainSkillKeyClicks();
            return;
        }

        if (hasPublicMinor || hasLegacyMinor) {
            while (ModKeyMappings.SKILL_MINOR.consumeClick()) {
                if (com.stardew.craft.client.weapon.ConsumedInteractionClientState.wasConsumedThisTick()) {
                    continue;
                }
                if (weaponItem != null && data != null && "femur_slam".equals(data.getSkill1().getId())
                    && !com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.isOnCooldown(weaponItem.getWeaponId(), data.getSkill1().getId())
                    && !mc.player.isUsingItem()) {
                    mc.player.startUsingItem(InteractionHand.MAIN_HAND);
                }
                PacketDistributor.sendToServer(new WeaponSkillUsePayload(false));
            }
        }

        if (!hasPublicMajor && !hasLegacyMajor) {
            return;
        }

        while (ModKeyMappings.SKILL_MAJOR.consumeClick()) {
            PacketDistributor.sendToServer(new WeaponSkillUsePayload(true));
        }
    }

    private static void drainSkillKeyClicks() {
        while (ModKeyMappings.SKILL_MINOR.consumeClick()) {
            // consume pending input
        }
        while (ModKeyMappings.SKILL_MAJOR.consumeClick()) {
            // consume pending input
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        com.stardew.craft.client.combat.DamageNumberClient.onRenderLevel(event);
        com.stardew.craft.client.fishpond.ClientFishPondJumpEffects.onRenderLevel(event);
        com.stardew.craft.client.emote.EmoteBubbleWorldRenderer.onRenderLevel(event);
        com.stardew.craft.client.render.CutsceneTextAboveHeadRenderer.onRenderLevel(event);
        com.stardew.craft.client.weapon.presentation.SkillPresentationClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.TideMarkRenderer.onRenderLevel(event);
        com.stardew.craft.client.weapon.OssifiedMarkRenderer.onRenderLevel(event);
        com.stardew.craft.client.weapon.TemplarMarkRenderer.onRenderLevel(event);
        com.stardew.craft.client.weapon.YetiMarkRenderer.onRenderLevel(event);
        com.stardew.craft.client.weapon.GalaxyDaggerMarkRenderer.onRenderLevel(event);
        com.stardew.craft.client.weapon.InfinityDaggerMarkRenderer.onRenderLevel(event);
        com.stardew.craft.client.weapon.WaterRingEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.OssifiedExecutionCircleEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.HolyBladeRingEffectClient.onRenderLevel(event);
        FireRingEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.ObsidianCrackEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.SteelFalchionLineEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.ShockwaveRingEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.AccretionDiskEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.SingularityCoreEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.SingularityRuneEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.RiftPathEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.StarfallMeteorEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.BlackHolePostEffectClient.onRenderLevel(event);
        com.stardew.craft.client.weapon.StarfallShockwavePostEffectClient.onRenderLevel(event);
        // com.stardew.craft.client.weapon.EvolvedAuraEffectClient.onRenderLevel(event); // 禁用：金色光环效果
        com.stardew.craft.client.render.PortalHintRenderer.onRenderLevel(event);
        com.stardew.craft.client.render.SpecialOrderDropBoxHintRenderer.onRenderLevel(event);
        com.stardew.craft.client.render.JunimoNoteOutlineRenderer.onRenderLevel(event);
        com.stardew.craft.client.render.StarPlaqueRenderer.onRenderLevel(event);
        com.stardew.craft.client.render.BillboardQuestIndicatorRenderer.onRenderLevel(event);
        com.stardew.craft.client.render.SpecialOrderBoardIndicatorRenderer.onRenderLevel(event);
        com.stardew.craft.client.render.PanPointRenderer.onRenderLevel(event);
        com.stardew.craft.client.render.SecretNote31FootprintRenderer.onRenderLevel(event);
        com.stardew.craft.client.auction.AuctionTooltipBoardRenderer.onRenderLevel(event);
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!event.getEntity().getTags().contains("sd_mob_prismatic_slime")) {
            return;
        }
        float age = event.getEntity().tickCount + event.getPartialTick();
        int rgb = Mth.hsvToRgb((age % 120.0F) / 120.0F, 0.85F, 1.0F);
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        RenderSystem.setShaderColor(r, g, b, 1.0F);
    }

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        if (event.getEntity().getTags().contains("sd_mob_prismatic_slime")) {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiLayerEvent.Post event) {
        if (!event.getName().equals(VanillaGuiLayers.HOTBAR)) {
            return;
        }

        // CC 过场全屏 overlay (淡入淡出 / 闪白)
        com.stardew.craft.communitycenter.cutscene.ScreenFade.render(event.getGuiGraphics());

        // Cutscene event fade overlay
        com.stardew.craft.cutscene.runtime.EventScreenFade.render(event.getGuiGraphics());
        com.stardew.craft.client.ritual.GalaxySwordRitualClientState.render(event.getGuiGraphics());
        com.stardew.craft.client.render.PortalHintRenderer.onRenderGui(event.getGuiGraphics());
        com.stardew.craft.client.render.MapInteractionHintRenderer.onRenderGui(event.getGuiGraphics());

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        if (mc.options.hideGui || player.isSpectator() || mc.screen instanceof StardewHudLayoutEditorScreen) {
            return;
        }

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof IStardewWeapon weaponItem)) {
            return;
        }

        WeaponData data = weaponItem.getWeaponData();
        if (data == null) {
            return;
        }

        WeaponSkillData skill1 = data.getSkill1();
        WeaponSkillData skill2 = data.getSkill2();
        if (skill1 == null && skill2 == null) {
            return;
        }

        GuiGraphics gg = event.getGuiGraphics();
        Font font = mc.font;

        StardewHudLayout.Placement placement = StardewHudLayout.current(
                Config.HudElement.WEAPON_SKILLS, gg.guiWidth(), gg.guiHeight());
        gg.pose().pushPose();
        gg.pose().translate(placement.x(), placement.y(), 0.0F);
        gg.pose().scale(placement.scale(), placement.scale(), 1.0F);
        int baseX = 22;
        int baseY = 20;
        int spacingY = 46;

        int index = 0;
        if (skill1 != null) {
            drawSkillHud(gg, font, player, weaponItem.getWeaponId(), skill1,
                    baseX, baseY + (spacingY * index), ModKeyMappings.SKILL_MINOR);
            index++;
        }
        if (skill2 != null) {
            drawSkillHud(gg, font, player, weaponItem.getWeaponId(), skill2,
                    baseX, baseY + (spacingY * index), ModKeyMappings.SKILL_MAJOR);
        }
        gg.pose().popPose();
    }

    public static void renderWeaponSkillPreview(GuiGraphics graphics, int x, int y, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || !(ModItems.GALAXY_SWORD.get() instanceof IStardewWeapon weaponItem)) {
            return;
        }
        WeaponData data = weaponItem.getWeaponData();
        if (data == null) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        int index = 0;
        if (data.getSkill1() != null) {
            drawSkillHud(graphics, mc.font, player, weaponItem.getWeaponId(), data.getSkill1(),
                    22, 20, ModKeyMappings.SKILL_MINOR);
            index++;
        }
        if (data.getSkill2() != null) {
            drawSkillHud(graphics, mc.font, player, weaponItem.getWeaponId(), data.getSkill2(),
                    22, 20 + 46 * index, ModKeyMappings.SKILL_MAJOR);
        }
        graphics.pose().popPose();
    }

    private static void drawSkillHud(GuiGraphics gg, Font font, Player player, String weaponId,
                                     WeaponSkillData skill, int centerX, int centerY, net.minecraft.client.KeyMapping keyMapping) {
        // 从客户端本地冷却存储读取（由服务端同步）
        int totalTicks = com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.getTotalTicks(weaponId, skill.getId());
        if (totalTicks <= 0) {
            totalTicks = Math.max(1, skill.getCooldown() * 20);
        }
        int remaining = com.stardew.craft.client.weapon.WeaponSkillCooldownsClient.getRemainingTicks(weaponId, skill.getId());
        float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) totalTicks));

        boolean isDragonBreathJudgement = "dragon_breath_judgement".equals(skill.getId());
        boolean isGalaxyJudgement = "galaxy_judgement".equals(skill.getId());
        boolean isEternalCollapse = "eternal_collapse".equals(skill.getId());
        int dragonBreathStacks = 0;
        int startrailStacks = 0;
        int singularityStacks = 0;
        if (isDragonBreathJudgement) {
            dragonBreathStacks = com.stardew.craft.client.weapon.DragonBreathClientState.getStacks(player);
        }
        if ("galaxy_sword".equals(weaponId)) {
            startrailStacks = com.stardew.craft.client.weapon.StartrailClientState.getStacks(player);
        }
        if ("infinity_blade".equals(weaponId)) {
            singularityStacks = com.stardew.craft.client.weapon.SingularityClientState.getStacks(player);
        }

        float radius = 16.0f;
        float ringOuter = 19.0f;
        float ringInner = 17.0f;

        int bgColor = 0x66000000;
        int ringColor = 0xCCFFFFFF;
        int textColor = 0xFFFFFFFF;

        drawFilledCircle(gg, centerX, centerY, radius, bgColor);

        if (isDragonBreathJudgement) {
            int purpleSegments = Math.min(15, dragonBreathStacks);
            float purpleSweep = (purpleSegments / 15.0f) * 360.0f;
            if (purpleSweep > 0.0f) {
                drawArcRing(gg, centerX, centerY, 14.5f, 16.5f, -90.0f, purpleSweep, 0xCC7A4DFF);
            }

            int overflowSegments = Math.min(5, Math.max(0, dragonBreathStacks - 15));
            float orangeSweep = (overflowSegments / 5.0f) * 360.0f;
            if (orangeSweep > 0.0f) {
                drawArcRing(gg, centerX, centerY, 16.7f, 18.3f, -90.0f, orangeSweep, 0xCCFF8A2A);
            }
        } else if (isGalaxyJudgement) {
            int filled = Math.min(12, Math.max(0, startrailStacks));
            if (filled > 0) {
                drawSegmentRing(gg, centerX, centerY, 14.5f, 16.5f, 12, filled, 0xCC7A4DFF, 2.0f);
            }
        } else if (isEternalCollapse) {
            int filled = Math.min(20, Math.max(0, singularityStacks));
            if (filled > 0) {
                drawSegmentRing(gg, centerX, centerY, 14.5f, 16.5f, 20, filled, 0xCCF2D56B, 1.5f);
            }
        } else {
            DurationRingInfo durationRing = getSkillDurationRing(player, skill.getId());
            if (durationRing != null) {
                float sweep = durationRing.ratio() * 360.0f;
                drawArcRing(gg, centerX, centerY, 14.5f, 16.5f, -90.0f, sweep, durationRing.argb());
            }
        }

        boolean disabled = remaining > 0 || (isDragonBreathJudgement && dragonBreathStacks < 15);
        if (remaining > 0) {
            float sweep = ratio * 360.0f;
            drawArcRing(gg, centerX, centerY, ringInner, ringOuter, -90.0f, sweep, ringColor);
        }

        ResourceLocation iconTex = getSkillIconTexture(skill.getId());
        int iconSize = 14;
        int iconX = centerX - iconSize / 2;
        int iconY = centerY - iconSize / 2;
        boolean evolvedHud = "infinity_blade".equals(weaponId) && singularityStacks >= 12;
        if (evolvedHud) {
            float pulse = 0.6f + 0.4f * Mth.sin((player.tickCount % 40) / 40.0f * Mth.TWO_PI);
            int alpha = Math.min(255, Math.max(80, (int) (80 + 175 * pulse)));
            int glowColor = (alpha << 24) | 0xF2D56B;
            int border = 2;
            int outer = iconSize + 4;
            int outerX = centerX - outer / 2;
            int outerY = centerY - outer / 2;
            gg.fill(outerX, outerY, outerX + outer, outerY + border, glowColor);
            gg.fill(outerX, outerY + outer - border, outerX + outer, outerY + outer, glowColor);
            gg.fill(outerX, outerY, outerX + border, outerY + outer, glowColor);
            gg.fill(outerX + outer - border, outerY, outerX + outer, outerY + outer, glowColor);
        }
        if (disabled) {
            RenderSystem.setShaderColor(0.6f, 0.6f, 0.6f, 1.0f);
        }
        gg.blit(iconTex, iconX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (remaining > 0) {
            String timeText = String.format(Locale.US, "%.1f", remaining / 20.0f);
            int timeX = centerX - font.width(timeText) / 2;
            int timeY = centerY - 4;
            gg.drawString(font, timeText, timeX, timeY, textColor, false);
        }

        String keyLabel = keyMapping.getTranslatedKeyMessage().getString();
        if ("obsidian_resonance".equals(skill.getId())) {
            keyLabel = Component.translatable("stardewcraft.weapon.skill.passive").getString();
        }
        int keyX = centerX - font.width(keyLabel) / 2;
        int keyY = centerY + 24;
        gg.drawString(font, keyLabel, keyX, keyY, 0xFFFFFFFF, false);
    }

    private static DurationRingInfo getSkillDurationRing(Player player, String skillId) {
        if ("silver_foldback".equals(skillId)) {
            if (com.stardew.craft.client.weapon.SilverSaberFoldbackClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.SilverSaberFoldbackClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.SilverSaberFoldbackClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC4A90E2); // blue
            }
        }
        if ("forest_blessing".equals(skillId)) {
            if (com.stardew.craft.client.weapon.ForestBlessingClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.ForestBlessingClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.ForestBlessingClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC7BC96F); // green
            }
        }
        if ("steel_spine_fury".equals(skillId)) {
            if (com.stardew.craft.client.weapon.SteelSpineFuryClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.SteelSpineFuryClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.SteelSpineFuryClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCCB0B5BA); // steel gray
            }
        }
        if ("templar_vow".equals(skillId)) {
            if (com.stardew.craft.client.weapon.TemplarVowClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.TemplarVowClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.TemplarVowClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCCF2D56B); // golden
            }
        }
        if ("wind_spire_thrust".equals(skillId)) {
            if (com.stardew.craft.client.weapon.WindSpireClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.WindSpireClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.WindSpireClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC8FD8FF); // light blue
            }
        }
        if ("fishcatch_thrust".equals(skillId)) {
            if (com.stardew.craft.client.weapon.BrokenTridentCatchClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.BrokenTridentCatchClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.BrokenTridentCatchClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC6CC9FF); // water blue
            }
        }
        if ("elf_blade_leaf".equals(skillId)) {
            if (com.stardew.craft.client.weapon.ElfBladeClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.ElfBladeClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.ElfBladeClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCCF2D56B); // warm yellow
            }
        }
        if ("crystal_dagger_layer".equals(skillId)) {
            int stacks = com.stardew.craft.client.weapon.CrystalDaggerLayerClientState.getStacks(player);
            if (stacks > 0) {
                float ratio = Math.min(1.0f, Math.max(0.0f, stacks / 4.0f));
                return new DurationRingInfo(ratio, 0xCC9EDBFF); // light blue
            }
        }
        if ("wicked_kris_venom_ripple".equals(skillId)) {
            int stacks = com.stardew.craft.client.weapon.WickedKrisPoisonClientState.getStacks(player);
            if (stacks > 0) {
                float ratio = Math.min(1.0f, Math.max(0.0f, stacks / 5.0f));
                return new DurationRingInfo(ratio, 0xCC63D26B); // venom green
            }
        }
        if ("wicked_kris_nest_burst".equals(skillId)) {
            if (com.stardew.craft.client.weapon.WickedKrisPoisonClientState.hasDetonation(player)) {
                int remaining = com.stardew.craft.client.weapon.WickedKrisPoisonClientState.getDetonationRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.WickedKrisPoisonClientState.getDetonationTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC4DAE5D); // detonation green
            }
        }
        if ("dwarf_dagger_rush".equals(skillId)) {
            if (com.stardew.craft.client.weapon.DwarfDaggerRushClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.DwarfDaggerRushClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.DwarfDaggerRushClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCCB58B5A); // dwarf bronze
            }
        }
        if ("insect_eye_stance".equals(skillId)) {
            if (com.stardew.craft.client.weapon.InsectEyeStanceClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.InsectEyeStanceClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.InsectEyeStanceClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC7EDC7A); // insect green
            }
        }
        if ("obsidian_resonance".equals(skillId)) {
            float ratio = com.stardew.craft.client.weapon.ObsidianResonanceClientState.getChargeRatio(player);
            if (ratio >= 0.0f) {
                return new DurationRingInfo(ratio, 0xCC5B3B8C); // obsidian purple
            }
        }
        if ("steel_falchion_trace".equals(skillId)) {
            if (com.stardew.craft.client.weapon.SteelFalchionTraceClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.SteelFalchionTraceClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.SteelFalchionTraceClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC000000); // black
            }
        }
        if ("dark_sword_blood_debt".equals(skillId)) {
            if (com.stardew.craft.client.weapon.DarkSwordBloodDebtClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.DarkSwordBloodDebtClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.DarkSwordBloodDebtClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC000000); // black
            }
        }
        if ("dark_sword_blood_moon".equals(skillId)) {
            if (com.stardew.craft.client.weapon.DarkSwordBloodMoonClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.DarkSwordBloodMoonClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.DarkSwordBloodMoonClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC000000); // black
            }
        }
        if ("lava_katana_reverb".equals(skillId)) {
            if (com.stardew.craft.client.weapon.LavaKatanaReverbClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.LavaKatanaReverbClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.LavaKatanaReverbClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCCF28C2E); // lava orange
            }
        }
        if ("dwarf_fortress".equals(skillId)) {
            if (com.stardew.craft.client.weapon.DwarfFortressClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.DwarfFortressClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.DwarfFortressClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCC8B6A3E); // dwarf brown
            }
        }
        if ("dragontooth_shiv_breath".equals(skillId)) {
            if (com.stardew.craft.client.weapon.DragontoothShivBreathClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.DragontoothShivBreathClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.DragontoothShivBreathClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCCF06A2E); // dragon ember
            }
        }
        if ("iridium_needle_frenzy".equals(skillId)) {
            if (com.stardew.craft.client.weapon.IridiumNeedleFrenzyClientState.isActive(player)) {
                int remaining = com.stardew.craft.client.weapon.IridiumNeedleFrenzyClientState.getRemainingTicks(player);
                int total = com.stardew.craft.client.weapon.IridiumNeedleFrenzyClientState.getTotalTicks();
                float ratio = Math.min(1.0f, Math.max(0.0f, remaining / (float) total));
                return new DurationRingInfo(ratio, 0xCCB05CFF); // iridium purple
            }
        }
        if ("iridium_needle_thrust".equals(skillId)) {
            int stacks = com.stardew.craft.client.weapon.IridiumNeedleCritClientState.getStacks(player);
            if (stacks > 0) {
                float ratio = Math.min(1.0f, Math.max(0.0f, stacks / 2.0f));
                return new DurationRingInfo(ratio, 0xCCB05CFF); // iridium purple
            }
            if (com.stardew.craft.client.weapon.IridiumNeedleCritClientState.isFlashActive(player)) {
                float pulse = 0.5f + 0.5f * Mth.sin((player.tickCount % 10) / 10.0f * Mth.TWO_PI);
                int alpha = Math.min(255, Math.max(140, (int) (140 + 95 * pulse)));
                int color = (alpha << 24) | 0xD0A2FF;
                return new DurationRingInfo(1.0f, color);
            }
        }
        return null;
    }

    private record DurationRingInfo(float ratio, int argb) {}

    private static ResourceLocation getSkillIconTexture(String skillId) {
        if ("tetanus_strike".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/rusty_sword_1.png");
        }
        if ("light_counter".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/steel_smallsword_1.png");
        }
        if ("tree_blessing".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/wooden_blade_1.png");
        }
        if ("desperate_plunder".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/pirates_sword_1.png");
        }
        if ("silver_foldback".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/silver_saber_1.png");
        }
        if ("crescent_slash".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/cutlass_1.png");
        }
        if ("forest_blessing".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/forest_sword_1.png");
        }
        if ("steel_spine_fury".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/iron_edge_1.png");
        }
        if ("femur_slam".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/femur_1.png");
        }
        if ("carving_thrust".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/carving_knife_1.png");
        }
        if ("iron_dirk_thrust".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/iron_dirk_1.png");
        }
        if ("wind_spire_thrust".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/wind_spire_1.png");
        }
        if ("elf_blade_leaf".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/elf_blade_1.png");
        }
        if ("burglar_shank".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/burglars_shank_1.png");
        }
        if ("crystal_dagger_layer".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/crystal_dagger_1.png");
        }
        if ("shadow_dagger_execute".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/shadow_dagger_1.png");
        }
        if ("wicked_kris_venom_ripple".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/wicked_kris_1.png");
        }
        if ("wicked_kris_nest_burst".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/wicked_kris_2.png");
        }
        if ("dwarf_dagger_thrust".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dwarf_dagger_1.png");
        }
        if ("dwarf_dagger_rush".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dwarf_dagger_2.png");
        }
        if ("dragontooth_shiv_stab".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dragontooth_shiv_1.png");
        }
        if ("dragontooth_shiv_breath".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dragontooth_shiv_2.png");
        }
        if ("iridium_needle_thrust".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/iridium_needle_1.png");
        }
        if ("iridium_needle_frenzy".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/iridium_needle_2.png");
        }
        if ("galaxy_dagger_starstab".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/galaxy_dagger_1.png");
        }
        if ("galaxy_dagger_starleap".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/galaxy_dagger_2.png");
        }
        if ("infinity_dagger_singularity_stab".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/infinity_dagger_1.png");
        }
        if ("infinity_dagger_singularity_backstab".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/infinity_dagger_2.png");
        }
        if ("meowmere_shot".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/meowmere_1.png");
        }
        if ("meowmere_symphony".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/meowmere_2.png");
        }
        if ("bone_fracture".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/bone_sword_1.png");
        }
        if ("claymore_foldback".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/claymore_1.png");
        }
        if ("tide_mark".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/tide_mark.png");
        }
        if ("tide_anchor".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/tide_anchor.png");
        }
        if ("fishcatch_thrust".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/broken_trident_1.png");
        }
        if ("tide_reel".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/broken_trident_2.png");
        }
        if ("templar_vow".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/templars_blade_1.png");
        }
        if ("templar_judgement".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/templars_blade_2.png");
        }
        if ("insect_eye_stance".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/insect_head_1.png");
        }
        if ("insect_dash".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/insect_head_2.png");
        }
        if ("obsidian_resonance".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/obsidian_edge_1.png");
        }
        if ("obsidian_crack".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/obsidian_edge_2.png");
        }
        if ("ossified_mark".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/ossified_blade_1.png");
        }
        if ("ossified_execution".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/ossified_blade_2.png");
        }
        if ("holy_smite".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/holy_blade_1.png");
        }
        if ("holy_domain".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/holy_blade_2.png");
        }
        if ("tempered_quench".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/tempered_broadsword_1.png");
        }
        if ("tempered_billet".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/tempered_broadsword_2.png");
        }
        if ("yeti_tooth_mark".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/yeti_tooth_1.png");
        }
        if ("yeti_tooth_spine".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/yeti_tooth_2.png");
        }
        if ("steel_falchion_line".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/steel_falchion_1.png");
        }
        if ("steel_falchion_trace".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/steel_falchion_2.png");
        }
        if ("dark_sword_blood_debt".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dark_sword_1.png");
        }
        if ("dark_sword_blood_moon".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dark_sword_2.png");
        }
        if ("lava_katana_brand".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/lava_katana_1.png");
        }
        if ("lava_katana_reverb".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/lava_katana_2.png");
        }
        if ("dragon_breath_thrust".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dragontooth_cutlass_1.png");
        }
        if ("dragon_breath_judgement".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dragontooth_cutlass_2.png");
        }
        if ("dwarf_rune_guard".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dwarf_sword_1.png");
        }
        if ("dwarf_fortress".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/dwarf_sword_2.png");
        }
        if ("startrail_rift".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/galaxy_sword_1.png");
        }
        if ("galaxy_judgement".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/galaxy_sword_2.png");
        }
        if ("singularity_evolve".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/infinity_blade_1.png");
        }
        if ("eternal_collapse".equals(skillId)) {
            return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/infinity_blade_2.png");
        }
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, "textures/gui/weapon_skill/icon_skill.png");
    }

    private static void drawFilledCircle(GuiGraphics gg, int cx, int cy, float r, int argb) {
        int radius = Math.round(r);
        int r2 = radius * radius;
        for (int dy = -radius; dy <= radius; dy++) {
            int y = cy + dy;
            int dx = (int) Math.floor(Math.sqrt(r2 - dy * dy));
            int x1 = cx - dx;
            int x2 = cx + dx + 1;
            gg.fill(x1, y, x2, y + 1, argb);
        }
    }

    private static void drawArcRing(GuiGraphics gg, int cx, int cy, float innerR, float outerR,
                                    float startDeg, float sweepDeg, int argb) {
        if (sweepDeg <= 0.0f) return;
        int inner = Math.round(innerR);
        int outer = Math.round(outerR);
        int segments = Math.max(80, (int) (360 * (sweepDeg / 360.0f)));

        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            double ang = Math.toRadians(startDeg + sweepDeg * t);
            float cos = (float) Math.cos(ang);
            float sin = (float) Math.sin(ang);
            int x1 = cx + Math.round(cos * inner);
            int y1 = cy + Math.round(sin * inner);
            int x2 = cx + Math.round(cos * outer);
            int y2 = cy + Math.round(sin * outer);

            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            int maxX = Math.max(x1, x2) + 1;
            int maxY = Math.max(y1, y2) + 1;
            gg.fill(minX, minY, maxX, maxY, argb);
        }
    }

    private static void drawSegmentRing(GuiGraphics gg, int cx, int cy, float innerR, float outerR,
                                        int totalSegments, int filledSegments, int argb, float gapDeg) {
        if (totalSegments <= 0 || filledSegments <= 0) {
            return;
        }
        int segCount = Math.max(1, totalSegments);
        float step = 360.0f / segCount;
        float gap = Math.max(0.0f, gapDeg);
        float sweep = Math.max(0.5f, step - gap);
        float start = -90.0f;
        int filled = Math.min(segCount, filledSegments);

        for (int i = 0; i < filled; i++) {
            float segStart = start + (i * step) + (gap * 0.5f);
            drawArcRing(gg, cx, cy, innerR, outerR, segStart, sweep, argb);
        }
    }

    private static boolean isDurabilityTooltipLine(Component component) {
        if (component == null) {
            return false;
        }

        // Vanilla advanced tooltip uses translation key: item.durability
        if (component.getContents() instanceof TranslatableContents translatable) {
            return "item.durability".equals(translatable.getKey());
        }
        return false;
    }

    private static boolean isUnbreakableTooltipLine(Component component) {
        if (component == null) {
            return false;
        }

        if (component.getContents() instanceof TranslatableContents translatable) {
            return "item.unbreakable".equals(translatable.getKey());
        }
        return false;
    }

    private static boolean isMuseumItem(String typeKey) {
        return "stardewcraft.type.mineral".equals(typeKey) || "stardewcraft.type.artifact".equals(typeKey);
    }

    private static boolean isMuseumDonated(ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ClientMuseumDonationCache.isDonated(id.toString());
    }

    /**
     * 客户端断开连接时清理 per-player NPC visibility 状态
     */
    @SubscribeEvent
    public static void onPlayerLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        com.stardew.craft.network.payload.ClientNpcVisibilityState.clear();
        com.stardew.craft.client.render.ClientStarterChestState.clear();

        // 清理所有客户端静态缓存，防止跨世界/跨服务器内存泄漏
        com.stardew.craft.client.gui.menu.StardewGameMenuScreen.clearPortraitCache();
        com.stardew.craft.client.gui.common.StardewNpcDialogueScreen.clearPortraitCache();
        com.stardew.craft.client.gui.ShopScreen.clearPortraitCache();
        com.stardew.craft.client.fishing.FishingBiteVisuals.clearTexSizeCache();
        com.stardew.craft.block.shape.ModelVoxelShapeCache.clearAll();
        com.stardew.craft.npc.data.NpcContentFilter.clearCache();

        // Reset per-session player data so "isSynced" flags go back to false and stale
        // mail/money/skill/friendship from the previous world doesn't leak into preconditions
        // evaluated before the next PlayerDataSyncPacket arrives.
        com.stardew.craft.client.ClientPlayerDataCache.reset();
        com.stardew.craft.client.ClientMailIndex.clear();
        com.stardew.craft.client.ClientFestivalAvailability.clear();
        com.stardew.craft.client.ClientJeiCatalog.clear();
        com.stardew.craft.api.v1.internal.festival
                .StardewFestivalClientSessionCache.clear();
        com.stardew.craft.communitycenter.network.BundleClientData.INSTANCE.clear();
        com.stardew.craft.client.ClientContentRefreshHooks.onSyncedRegistriesChanged();
        com.stardew.craft.client.hud.StardewTimeHud.resetTimeSync();
        com.stardew.craft.client.sound.StardewMusicManager.setNightMarketFestivalOpen(false);
    }
}
