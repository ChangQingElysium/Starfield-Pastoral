package com.stardew.craft.quest.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.quest.QuestObjectiveContext;
import com.stardew.craft.api.v1.quest.QuestObjectiveResult;
import com.stardew.craft.api.v1.quest.QuestObjectiveRuntime;
import com.stardew.craft.api.v1.quest.QuestProgressEvent;
import com.stardew.craft.api.v1.quest.QuestProgressEvents;
import com.stardew.craft.api.v1.quest.StardewQuestObjectives;
import com.stardew.craft.api.v1.content.StardewContentKey;
import com.stardew.craft.api.v1.content.StardewContentReference;
import com.stardew.craft.api.v1.content.StardewContentReferenceRoles;
import com.stardew.craft.api.v1.content.StardewContentTypes;
import com.stardew.craft.book.BookPowerEffects;
import com.stardew.craft.npc.data.NpcCapabilityProfile;
import com.stardew.craft.npc.data.NpcDataRegistry;
import com.stardew.craft.npc.data.NpcSocialRules;
import com.stardew.craft.npc.runtime.NpcFriendshipDataManager;
import com.stardew.craft.npc.runtime.NpcInteractionService;
import com.stardew.craft.player.PlayerDataManager;
import com.stardew.craft.quest.QuestManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Registration and implementations for the built-in data-driven objective types. */
public final class BuiltinQuestObjectiveTypes {
    private BuiltinQuestObjectiveTypes() {
    }

    public static void registerAll() {
        StardewQuestObjectives.register(id("basic"), Codec.unit(BasicData.INSTANCE), ignored -> new BasicRuntime());
        StardewQuestObjectives.register(id("item_harvest"), ItemCountData.CODEC,
                data -> new CounterRuntime(
                        QuestProgressEvents.ITEM_RECEIVED,
                        data.item().toString(), "", data.count()),
                (owner, data) -> List.of(itemReference(data.item())));
        StardewQuestObjectives.register(id("crafting"), CraftingData.CODEC,
                data -> new CounterRuntime(
                        QuestProgressEvents.RECIPE_CRAFTED,
                        data.recipe(), "", 1),
                (owner, data) -> ownedReference(
                        owner,
                        StardewContentReferenceRoles.OBJECTIVE_RECIPE,
                        StardewContentTypes.CRAFTING_RECIPE,
                        data.recipe()));
        StardewQuestObjectives.register(id("building"), BuildingData.CODEC,
                data -> new CounterRuntime(QuestProgressEvents.BUILDING_EXISTS, data.building(), "", 1));
        StardewQuestObjectives.register(
                id("location"), LocationData.CODEC,
                LocationRuntime::new,
                (owner, data) -> data.location()
                        .startsWith(LocationRuntime.MINE_PREFIX)
                        ? List.of()
                        : ownedReference(
                                owner,
                                StardewContentReferenceRoles
                                        .OBJECTIVE_LOCATION,
                                StardewContentTypes.LOCATION,
                                data.location()));
        StardewQuestObjectives.register(
                id("item_delivery"), DeliveryData.CODEC,
                DeliveryRuntime::new,
                (owner, data) -> combine(
                        List.of(itemReference(data.item())),
                        npcReference(owner, data.targetNpc())));
        StardewQuestObjectives.register(id("secret_lost_item"), SecretLostItemData.CODEC,
                SecretLostItemRuntime::new,
                (owner, data) -> combine(
                        List.of(itemReference(data.item())),
                        npcReference(owner, data.targetNpc()),
                        ownedReference(
                                owner,
                                StardewContentReferenceRoles
                                        .EXCLUSIVE_QUEST,
                                StardewContentTypes.QUEST,
                                data.exclusiveQuest())));
        StardewQuestObjectives.register(id("monster"), MonsterData.CODEC,
                data -> new ReportRuntime(
                        QuestProgressEvents.MONSTER_SLAIN,
                        data.monster(), data.count(),
                        data.targetNpc()),
                (owner, data) ->
                        npcReference(owner, data.targetNpc()));
        StardewQuestObjectives.register(id("fishing"), FishingData.CODEC,
                data -> new ReportRuntime(
                        QuestProgressEvents.FISH_CAUGHT,
                        data.item().toString(), data.count(),
                        data.targetNpc()),
                (owner, data) -> combine(
                        List.of(itemReference(data.item())),
                        npcReference(owner, data.targetNpc())));
        StardewQuestObjectives.register(id("resource"), ResourceData.CODEC,
                data -> new ReportRuntime(
                        QuestProgressEvents.ITEM_RECEIVED,
                        data.item().toString(), data.count(),
                        data.targetNpc()),
                (owner, data) -> combine(
                        List.of(itemReference(data.item())),
                        npcReference(owner, data.targetNpc())));
        StardewQuestObjectives.register(
                id("socialize"), SocialData.CODEC,
                SocialRuntime::new,
                (owner, data) -> data.npcs().stream()
                        .flatMap(npc -> npcReference(owner, npc).stream())
                        .toList());
    }

    private static StardewContentReference itemReference(
            ResourceLocation item
    ) {
        return StardewContentReference.required(
                StardewContentReferenceRoles.OBJECTIVE_ITEM,
                new StardewContentKey(
                        StardewContentTypes.ITEM, item));
    }

    private static List<StardewContentReference> npcReference(
            StardewContentKey owner,
            String rawNpc
    ) {
        return ownedReference(
                owner,
                StardewContentReferenceRoles.TARGET_NPC,
                StardewContentTypes.NPC,
                rawNpc);
    }

    private static List<StardewContentReference> ownedReference(
            StardewContentKey owner,
            ResourceLocation role,
            ResourceLocation type,
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        ResourceLocation target = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(
                        owner.id().getNamespace(), normalized);
        return target == null
                ? List.of()
                : List.of(StardewContentReference.required(
                        role,
                        new StardewContentKey(type, target)));
    }

    @SafeVarargs
    private static List<StardewContentReference> combine(
            List<StardewContentReference>... groups
    ) {
        ArrayList<StardewContentReference> combined =
                new ArrayList<>();
        for (List<StardewContentReference> group : groups) {
            combined.addAll(group);
        }
        return List.copyOf(combined);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(StardewCraft.MODID, path);
    }

    public enum BasicData {
        INSTANCE
    }

    public record ItemCountData(ResourceLocation item, int count) {
        public static final Codec<ItemCountData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(ItemCountData::item),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(ItemCountData::count)
        ).apply(instance, ItemCountData::new));
    }

    public record CraftingData(String recipe) {
        public static final Codec<CraftingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("recipe").forGetter(CraftingData::recipe)
        ).apply(instance, CraftingData::new));
    }

    public record BuildingData(String building) {
        public static final Codec<BuildingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("building").forGetter(BuildingData::building)
        ).apply(instance, BuildingData::new));
    }

    public record LocationData(String location) {
        public static final Codec<LocationData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("location").forGetter(LocationData::location)
        ).apply(instance, LocationData::new));
    }

    public record DeliveryData(
            String targetNpc,
            ResourceLocation item,
            int count,
            String targetMessage,
            int friendship
    ) {
        public static final Codec<DeliveryData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("target_npc").forGetter(DeliveryData::targetNpc),
                ResourceLocation.CODEC.fieldOf("item").forGetter(DeliveryData::item),
                Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("count", 1).forGetter(DeliveryData::count),
                Codec.STRING.optionalFieldOf("target_message", "").forGetter(DeliveryData::targetMessage),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("friendship", 255).forGetter(DeliveryData::friendship)
        ).apply(instance, DeliveryData::new));
    }

    public record SecretLostItemData(
            String targetNpc,
            ResourceLocation item,
            int friendship,
            String exclusiveQuest,
            String targetMessage
    ) {
        public static final Codec<SecretLostItemData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("target_npc").forGetter(SecretLostItemData::targetNpc),
                ResourceLocation.CODEC.fieldOf("item").forGetter(SecretLostItemData::item),
                Codec.intRange(0, Integer.MAX_VALUE).fieldOf("friendship").forGetter(SecretLostItemData::friendship),
                Codec.STRING.fieldOf("exclusive_quest").forGetter(SecretLostItemData::exclusiveQuest),
                Codec.STRING.optionalFieldOf("target_message", "").forGetter(SecretLostItemData::targetMessage)
        ).apply(instance, SecretLostItemData::new));
    }

    public record MonsterData(String monster, int count, String targetNpc) {
        public static final Codec<MonsterData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("monster").forGetter(MonsterData::monster),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(MonsterData::count),
                Codec.STRING.optionalFieldOf("target_npc", "").forGetter(MonsterData::targetNpc)
        ).apply(instance, MonsterData::new));
    }

    public record FishingData(ResourceLocation item, int count, String targetNpc) {
        public static final Codec<FishingData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(FishingData::item),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(FishingData::count),
                Codec.STRING.optionalFieldOf("target_npc", "").forGetter(FishingData::targetNpc)
        ).apply(instance, FishingData::new));
    }

    public record ResourceData(ResourceLocation item, int count, String targetNpc) {
        public static final Codec<ResourceData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(ResourceData::item),
                Codec.intRange(1, Integer.MAX_VALUE).fieldOf("count").forGetter(ResourceData::count),
                Codec.STRING.optionalFieldOf("target_npc", "").forGetter(ResourceData::targetNpc)
        ).apply(instance, ResourceData::new));
    }

    public record SocialData(List<String> npcs, int friendship) {
        public static final Codec<SocialData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.listOf().optionalFieldOf("npcs", List.of()).forGetter(SocialData::npcs),
                Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("friendship", 100).forGetter(SocialData::friendship)
        ).apply(instance, SocialData::new));
    }

    private static final class BasicRuntime implements QuestObjectiveRuntime {
    }

    private static class CounterRuntime implements QuestObjectiveRuntime {
        private final ResourceLocation eventType;
        private final String subject;
        private final String target;
        private final int required;
        protected int progress;

        private CounterRuntime(ResourceLocation eventType, String subject, String target, int required) {
            this.eventType = eventType;
            this.subject = subject;
            this.target = target;
            this.required = required;
        }

        @Override
        public QuestObjectiveResult onProgress(QuestObjectiveContext context, QuestProgressEvent event) {
            if (!eventType.equals(event.type()) || !subject.equals(event.subject())) {
                return QuestObjectiveResult.NONE;
            }
            if (!target.isEmpty() && !target.equalsIgnoreCase(event.target())) {
                return QuestObjectiveResult.NONE;
            }
            int before = progress;
            progress = Math.min(required, progress + Math.max(1, event.amount()));
            return progress == before ? QuestObjectiveResult.NONE : QuestObjectiveResult.progress(progress >= required);
        }

        @Override
        public CompoundTag saveState() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Progress", progress);
            return tag;
        }

        @Override
        public void loadState(CompoundTag state) {
            progress = Math.max(0, Math.min(required, state.getInt("Progress")));
        }

        @Override
        public int currentCount() {
            return progress;
        }

        @Override
        public int targetCount() {
            return required;
        }
    }

    private static final class LocationRuntime extends CounterRuntime {
        private static final String MINE_PREFIX = "MineFloor:";
        private final String location;

        private LocationRuntime(LocationData data) {
            super(data.location().startsWith(MINE_PREFIX)
                            ? QuestProgressEvents.MINE_FLOOR_REACHED : QuestProgressEvents.WARPED,
                    data.location().startsWith(MINE_PREFIX) ? "" : data.location(), "", 1);
            this.location = data.location();
        }

        @Override
        public QuestObjectiveResult onProgress(QuestObjectiveContext context, QuestProgressEvent event) {
            if (!location.startsWith(MINE_PREFIX)) {
                return super.onProgress(context, event);
            }
            if (!QuestProgressEvents.MINE_FLOOR_REACHED.equals(event.type())) {
                return QuestObjectiveResult.NONE;
            }
            try {
                int requiredFloor = Integer.parseInt(location.substring(MINE_PREFIX.length()));
                if (Integer.parseInt(event.subject()) >= requiredFloor) {
                    progress = 1;
                    return QuestObjectiveResult.progress(true);
                }
            } catch (NumberFormatException ignored) {
            }
            return QuestObjectiveResult.NONE;
        }
    }

    private static final class DeliveryRuntime extends CounterRuntime {
        private final DeliveryData data;

        private DeliveryRuntime(DeliveryData data) {
            super(QuestProgressEvents.ITEM_OFFERED_TO_NPC, data.item().toString(), data.targetNpc(), data.count());
            this.data = data;
        }

        @Override
        public QuestObjectiveResult onProgress(QuestObjectiveContext context, QuestProgressEvent event) {
            QuestObjectiveResult result = super.onProgress(context, event);
            return result.changed() ? QuestObjectiveResult.consumed(result.completed()) : result;
        }

        @Override
        public void onCompleted(QuestObjectiveContext context) {
            if (data.friendship() <= 0 || data.targetNpc().isBlank()) {
                return;
            }
            NpcFriendshipDataManager manager = NpcFriendshipDataManager.get(context.player().serverLevel());
            NpcFriendshipDataManager.FriendshipState state = manager.getOrCreate(
                    context.player().getUUID(), data.targetNpc());
            int gain = BookPowerEffects.applyFriendshipGain(
                    PlayerDataManager.getPlayerData(context.player()), data.friendship());
            state.addPoints(gain, NpcInteractionService.getMaxFriendshipPointsFor(data.targetNpc()));
            manager.setDirty();
        }

        @Override
        public boolean matchesItemDelivery(String npcId, String itemId) {
            return data.targetNpc().equalsIgnoreCase(npcId) && data.item().toString().equalsIgnoreCase(itemId);
        }

        @Override
        public String deliveryTargetMessage() {
            return data.targetMessage();
        }
    }

    /** SDV {@code SecretLostItemQuest}: hidden, item-found gated and mutually exclusive. */
    private static final class SecretLostItemRuntime implements QuestObjectiveRuntime {
        private final SecretLostItemData data;
        private boolean itemFound;

        private SecretLostItemRuntime(SecretLostItemData data) {
            this.data = data;
        }

        @Override
        public void onAccepted(QuestObjectiveContext context) {
            if (BuiltInRegistries.ITEM.containsKey(data.item())) {
                itemFound = context.player().getInventory()
                        .countItem(BuiltInRegistries.ITEM.get(data.item())) > 0;
            }
        }

        @Override
        public QuestObjectiveResult onProgress(QuestObjectiveContext context, QuestProgressEvent event) {
            if (QuestProgressEvents.ITEM_RECEIVED.equals(event.type())
                    && data.item().toString().equals(event.subject())) {
                if (itemFound) return QuestObjectiveResult.NONE;
                itemFound = true;
                return QuestObjectiveResult.progress(false);
            }
            if (QuestProgressEvents.ITEM_OFFERED_TO_NPC.equals(event.type())
                    && data.item().toString().equals(event.subject())
                    && data.targetNpc().equalsIgnoreCase(event.target())) {
                // Holding the matching quest item is sufficient proof that it was found.
                // This also repairs older saves whose hidden quest missed ITEM_RECEIVED.
                itemFound = true;
                return QuestObjectiveResult.consumed(true);
            }
            return QuestObjectiveResult.NONE;
        }

        @Override
        public void onCompleted(QuestObjectiveContext context) {
            if (data.friendship() > 0 && !data.targetNpc().isBlank()) {
                NpcFriendshipDataManager manager = NpcFriendshipDataManager.get(context.player().serverLevel());
                NpcFriendshipDataManager.FriendshipState state = manager.getOrCreate(
                        context.player().getUUID(), data.targetNpc());
                int gain = BookPowerEffects.applyFriendshipGain(
                        PlayerDataManager.getPlayerData(context.player()), data.friendship());
                state.addPoints(gain, NpcInteractionService.getMaxFriendshipPointsFor(data.targetNpc()));
                manager.setDirty();
            }
            QuestManager quests = QuestManager.of(context.player());
            if (quests != null && !data.exclusiveQuest().isBlank()) {
                quests.removeQuest(data.exclusiveQuest(), context.player());
            }
        }

        @Override
        public CompoundTag saveState() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("ItemFound", itemFound);
            return tag;
        }

        @Override
        public void loadState(CompoundTag state) {
            itemFound = state.getBoolean("ItemFound");
        }

        @Override
        public boolean matchesItemDelivery(String npcId, String itemId) {
            return data.targetNpc().equalsIgnoreCase(npcId)
                    && data.item().toString().equalsIgnoreCase(itemId);
        }

        @Override
        public String deliveryTargetMessage() {
            return data.targetMessage();
        }

        @Override
        public boolean isSecret() {
            return true;
        }

        @Override
        public List<Component> objectiveComponents(Component fallback) {
            return List.of();
        }
    }

    private static final class ReportRuntime extends CounterRuntime {
        private final String targetNpc;

        private ReportRuntime(ResourceLocation eventType, String subject, int required, String targetNpc) {
            super(eventType, subject, "", required);
            this.targetNpc = targetNpc;
        }

        @Override
        public QuestObjectiveResult onProgress(QuestObjectiveContext context, QuestProgressEvent event) {
            if (progress >= targetCount() && !targetNpc.isBlank()) {
                if (QuestProgressEvents.NPC_SOCIALIZED.equals(event.type())
                        && targetNpc.equalsIgnoreCase(event.subject())) {
                    return QuestObjectiveResult.progress(true);
                }
                return QuestObjectiveResult.NONE;
            }
            QuestObjectiveResult result = super.onProgress(context, event);
            if (result.completed() && !targetNpc.isBlank()) {
                return QuestObjectiveResult.progress(false);
            }
            return result;
        }

        @Override
        public List<Component> objectiveComponents(Component fallback) {
            if (progress >= targetCount() && !targetNpc.isBlank()) {
                return List.of(Component.translatable("stardewcraft.quest.report_to",
                        Component.translatable("entity.stardewcraft.npc." + targetNpc)));
            }
            return super.objectiveComponents(fallback);
        }
    }

    private static final class SocialRuntime implements QuestObjectiveRuntime {
        private final SocialData data;
        private final List<String> remaining = new ArrayList<>();
        private int total;

        private SocialRuntime(SocialData data) {
            this.data = data;
        }

        @Override
        public void onAccepted(QuestObjectiveContext context) {
            if (!remaining.isEmpty()) {
                return;
            }
            if (!data.npcs().isEmpty()) {
                remaining.addAll(data.npcs());
            } else {
                for (var entry : NpcDataRegistry.capabilities().entrySet()) {
                    if (isIntroductionsNpc(entry.getKey(), entry.getValue())) {
                        remaining.add(entry.getKey());
                    }
                }
            }
            total = remaining.size();
        }

        @Override
        public QuestObjectiveResult onProgress(QuestObjectiveContext context, QuestProgressEvent event) {
            if (!QuestProgressEvents.NPC_SOCIALIZED.equals(event.type()) || !remaining.remove(event.subject())) {
                return QuestObjectiveResult.NONE;
            }
            return QuestObjectiveResult.progress(remaining.isEmpty());
        }

        @Override
        public List<Component> objectiveComponents(Component fallback) {
            if (fallback.getContents() instanceof TranslatableContents translatable) {
                return List.of(Component.translatable(
                        translatable.getKey(), currentCount(), targetCount()));
            }
            return QuestObjectiveRuntime.super.objectiveComponents(fallback);
        }

        @Override
        public void onCompleted(QuestObjectiveContext context) {
            if (data.friendship() <= 0) {
                return;
            }
            NpcFriendshipDataManager manager = NpcFriendshipDataManager.get(context.player().serverLevel());
            for (var entry : NpcDataRegistry.capabilities().entrySet()) {
                if (!isIntroductionsNpc(entry.getKey(), entry.getValue())) {
                    continue;
                }
                var state = manager.getOrCreate(context.player().getUUID(), entry.getKey());
                int gain = BookPowerEffects.applyFriendshipGain(
                        PlayerDataManager.getPlayerData(context.player()), data.friendship());
                state.addPoints(gain, NpcInteractionService.getMaxFriendshipPointsFor(entry.getKey()));
            }
            manager.setDirty();
        }

        @Override
        public CompoundTag saveState() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Total", total);
            ListTag list = new ListTag();
            remaining.forEach(npc -> list.add(StringTag.valueOf(npc)));
            tag.put("Remaining", list);
            return tag;
        }

        @Override
        public void loadState(CompoundTag state) {
            total = state.getInt("Total");
            remaining.clear();
            ListTag list = state.getList("Remaining", 8);
            for (int i = 0; i < list.size(); i++) {
                remaining.add(list.getString(i));
            }
        }

        @Override
        public int currentCount() {
            return Math.max(0, total - remaining.size());
        }

        @Override
        public int targetCount() {
            return total;
        }

        private static boolean isIntroductionsNpc(String npcId, NpcCapabilityProfile profile) {
            return NpcSocialRules.isIntroductionsNpc(npcId, profile);
        }
    }
}
