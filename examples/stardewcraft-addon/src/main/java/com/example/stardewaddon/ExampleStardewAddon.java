package com.example.stardewaddon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.action.StardewActionResult;
import com.stardew.craft.api.v1.action.StardewActions;
import com.stardew.craft.api.v1.agriculture.StardewAgricultureDataApi;
import com.stardew.craft.api.v1.agriculture.StardewCropData;
import com.stardew.craft.api.v1.condition.StardewConditions;
import com.stardew.craft.api.v1.cutscene.StardewCutsceneTriggers;
import com.stardew.craft.api.v1.equipment.StardewEquipmentData;
import com.stardew.craft.api.v1.equipment.StardewEquipmentDataApi;
import com.stardew.craft.api.v1.equipment.StardewWeaponSkillHandlers;
import com.stardew.craft.api.v1.item.StardewItemData;
import com.stardew.craft.api.v1.item.StardewItemDataApi;
import com.stardew.craft.api.v1.npc.StardewNpcInteractions;
import com.stardew.craft.api.v1.mining.StardewMineMonsterProviders;
import com.stardew.craft.api.v1.profession.StardewProfessionEffectHandlers;
import com.stardew.craft.api.v1.query.StardewItemQueries;
import com.stardew.craft.api.v1.quest.QuestObjectiveResult;
import com.stardew.craft.api.v1.quest.QuestObjectiveRuntime;
import com.stardew.craft.api.v1.quest.QuestProgressEvents;
import com.stardew.craft.api.v1.quest.StardewQuestObjectives;
import com.stardew.craft.api.v1.shop.StardewShopEntry;
import com.stardew.craft.api.v1.shop.StardewShopInventoryProviders;
import com.stardew.craft.api.v1.specialorder.SpecialOrderProgressEvent;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderObjectives;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderRewards;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.common.Mod;

import java.util.List;
import java.util.Optional;

@Mod(ExampleStardewAddon.MOD_ID)
public final class ExampleStardewAddon {
    public static final String MOD_ID = "example_stardew_addon";

    public ExampleStardewAddon() {
        registerStackMetadataProvider();
        registerCondition();
        registerItemQuery();
        registerAction();
        registerQuestObjective();
        registerShopInventoryProvider();
        registerCutsceneTrigger();
        registerSpecialOrderTypes();
        registerNpcInteractionProvider();
        registerAgricultureProvider();
        registerEquipmentProvider();
        registerWeaponSkill();
        registerMineMonsterProvider();
        registerProfessionEffect();
    }

    private static void registerStackMetadataProvider() {
        StardewItemDataApi.registerProvider(id("charged_amethyst"), 100, stack -> {
            if (!stack.is(Items.AMETHYST_SHARD) || !stack.hasFoil()) {
                return Optional.empty();
            }
            return Optional.of(new StardewItemData(
                    id("charged_gem"), 500, -300, 0, 0, false));
        });
    }

    private static void registerCondition() {
        StardewConditions.register(id("player_named"), PlayerNamedCondition.CODEC,
                (context, data) -> context.player() != null
                        && context.player().getGameProfile().getName().equalsIgnoreCase(data.name()));
    }

    private static void registerItemQuery() {
        StardewItemQueries.register(id("apples"), AppleQuery.CODEC,
                (context, data) -> List.of(new ItemStack(Items.APPLE, data.count())));
    }

    private static void registerAction() {
        StardewActions.register(id("heal"), HealAction.CODEC, (context, data) -> {
            context.player().heal(data.health());
            return StardewActionResult.ok();
        });
    }

    private static void registerQuestObjective() {
        StardewQuestObjectives.register(id("break_targets"), BreakTargetsObjective.CODEC,
                BreakTargetsRuntime::new);
    }

    private static void registerShopInventoryProvider() {
        StardewShopInventoryProviders.register(id("daily_apples"), context -> List.of(
                new StardewShopEntry(
                        "minecraft:apple", "", "", 75, 5,
                        Optional.empty(), 0, List.of(), 1, 0, Optional.empty(),
                        -1, 0, 1, List.of())));
    }

    private static void registerCutsceneTrigger() {
        StardewCutsceneTriggers.register(id("experience_level"), Codec.INT,
                (player, minimum) -> player.experienceLevel >= minimum);
    }

    private static void registerSpecialOrderTypes() {
        StardewSpecialOrderObjectives.register(id("slay_named"), Codec.STRING,
                (context, target, event) -> event.kind() == SpecialOrderProgressEvent.Kind.MONSTER_SLAIN
                        && target.equalsIgnoreCase(event.target()) ? event.amount() : 0);
        StardewSpecialOrderRewards.register(id("heal"), Codec.FLOAT,
                (context, health) -> context.player().heal(health));
    }

    private static void registerNpcInteractionProvider() {
        StardewNpcInteractions.register(id("lewis_apple_greeting"), 100, context -> {
            if (!context.npcId().equals(ResourceLocation.fromNamespaceAndPath("stardewcraft", "lewis"))
                    || !context.player().getItemInHand(context.hand()).is(Items.APPLE)) {
                return InteractionResult.PASS;
            }
            context.player().displayClientMessage(Component.literal("Lewis notices the addon apple."), false);
            return InteractionResult.SUCCESS;
        });
    }

    private static void registerAgricultureProvider() {
        StardewAgricultureDataApi.registerCropProvider(id("mature_wheat"), 100, (level, pos, state) -> {
            if (!state.is(Blocks.WHEAT)) return null;
            return new StardewCropData(
                    List.of("spring", "summer", "fall"), List.of(1, 1, 1, 1, 1, 1, 1),
                    -1, pos.getY() > 80 ? 12 : 8,
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "grab"),
                    ResourceLocation.withDefaultNamespace("wheat"),
                    ResourceLocation.withDefaultNamespace("wheat_seeds"));
        });
    }

    private static void registerEquipmentProvider() {
        StardewEquipmentDataApi.registerProvider(id("enchanted_diamond_sword"), 100, stack -> {
            if (!stack.is(Items.DIAMOND_SWORD) || !stack.hasFoil()) return null;
            return new StardewEquipmentData(
                    ResourceLocation.fromNamespaceAndPath("stardewcraft", "weapon"),
                    0, 0, 3, 0.04F, 0.5F, 0, 0.0F, 0.0F, 0,
                    List.of(id("apple_guard")), Optional.of(new StardewEquipmentData.Weapon(
                    "sword", 22.0F, 30.0F, 0.04F, 0, 0, 0.0F, 1.0F,
                    Optional.of(id("apple_dash")), Optional.empty())));
        });
    }

    private static void registerWeaponSkill() {
        StardewWeaponSkillHandlers.register(id("apple_dash"), context -> {
            context.player().push(context.player().getLookAngle().x * 0.8, 0.1,
                    context.player().getLookAngle().z * 0.8);
            return InteractionResultHolder.success(context.weapon());
        });
    }

    private static void registerMineMonsterProvider() {
        StardewMineMonsterProviders.register(id("silverfish_floor_13"), 100,
                context -> context.floor() == 13 ? EntityType.SILVERFISH : null);
    }

    private static void registerProfessionEffect() {
        StardewProfessionEffectHandlers.register(id("apple_tiller"), context -> {
            if (StardewProfessionEffectHandlers.SELL_PRICE_MULTIPLIER.equals(context.operation())
                    && context.stack().is(Items.APPLE)) {
                return context.value() * 1.02;
            }
            return context.value();
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private record PlayerNamedCondition(String name) {
        private static final Codec<PlayerNamedCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(PlayerNamedCondition::name)
        ).apply(instance, PlayerNamedCondition::new));
    }

    private record AppleQuery(int count) {
        private static final Codec<AppleQuery> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 999).optionalFieldOf("count", 1).forGetter(AppleQuery::count)
        ).apply(instance, AppleQuery::new));
    }

    private record HealAction(float health) {
        private static final Codec<HealAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.floatRange(0.0F, 1024.0F).fieldOf("health").forGetter(HealAction::health)
        ).apply(instance, HealAction::new));
    }

    private record BreakTargetsObjective(String target, int count) {
        private static final Codec<BreakTargetsObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("target").forGetter(BreakTargetsObjective::target),
                Codec.intRange(1, 999).fieldOf("count").forGetter(BreakTargetsObjective::count)
        ).apply(instance, BreakTargetsObjective::new));
    }

    private static final class BreakTargetsRuntime implements QuestObjectiveRuntime {
        private final BreakTargetsObjective definition;
        private int progress;

        private BreakTargetsRuntime(BreakTargetsObjective definition) {
            this.definition = definition;
        }

        @Override
        public QuestObjectiveResult onProgress(
                com.stardew.craft.api.v1.quest.QuestObjectiveContext context,
                com.stardew.craft.api.v1.quest.QuestProgressEvent event
        ) {
            if (!QuestProgressEvents.MONSTER_SLAIN.equals(event.type())
                    || !definition.target().equals(event.subject())) {
                return QuestObjectiveResult.NONE;
            }
            progress = Math.min(definition.count(), progress + Math.max(1, event.amount()));
            return QuestObjectiveResult.progress(progress >= definition.count());
        }

        @Override
        public CompoundTag saveState() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Progress", progress);
            return tag;
        }

        @Override
        public void loadState(CompoundTag state) {
            progress = Math.min(definition.count(), Math.max(0, state.getInt("Progress")));
        }

        @Override
        public List<Component> objectiveComponents(Component fallback) {
            return List.of(Component.literal(
                    "Defeat " + definition.target() + ": " + progress + "/" + definition.count()));
        }

        @Override
        public int currentCount() {
            return progress;
        }

        @Override
        public int targetCount() {
            return definition.count();
        }
    }
}
