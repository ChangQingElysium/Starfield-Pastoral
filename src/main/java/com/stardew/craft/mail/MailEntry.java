package com.stardew.craft.mail;

import com.stardew.craft.StardewCraft;
import com.stardew.craft.api.v1.action.StardewAction;
import com.stardew.craft.api.v1.condition.StardewCondition;
import com.stardew.craft.api.v1.mail.StardewMailDefinition;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;

/** Compatibility view over the public namespaced mail definition. */
public final class MailEntry {
    private final ResourceLocation id;
    private final String displayId;
    private final StardewMailDefinition definition;

    MailEntry(ResourceLocation id, String displayId, StardewMailDefinition definition) {
        this.id = id;
        this.displayId = displayId;
        this.definition = definition;
    }

    public ResourceLocation definitionId() { return id; }
    public StardewMailDefinition definition() { return definition; }
    public String getId() { return displayId; }
    public String getText() { return definition.text(); }
    public int getBackground() { return definition.background(); }
    @Nullable public String getCustomBgTexture() { return definition.customBackgroundTexture().orElse(null); }
    @Nullable public String getTextColor() { return definition.textColor().orElse(null); }
    public List<AttachedItem> getAttachedItems() {
        return definition.attachedItems().stream()
                .map(item -> new AttachedItem(item.item().toString(), item.count()))
                .toList();
    }
    public int getMoney() { return definition.money(); }
    @Nullable public String getLearnedRecipe() { return definition.learnedRecipe().orElse(null); }
    public boolean isRecipeIsCooking() { return definition.recipeIsCooking(); }
    @Nullable public String getQuestId() { return definition.quest().map(MailEntry::displayLinkedId).orElse(null); }
    @Nullable public String getSpecialOrderId() {
        return definition.specialOrder().map(ResourceLocation::toString).orElse(null);
    }
    public List<StardewCondition> availableWhen() { return definition.availableWhen(); }
    public List<StardewAction> onDelivery() { return definition.onDelivery(); }
    public List<StardewAction> onRead() { return definition.onRead(); }

    private static String displayLinkedId(ResourceLocation id) {
        return StardewCraft.MODID.equals(id.getNamespace()) && id.getPath().matches("\\d+")
                ? id.getPath() : id.toString();
    }

    public record AttachedItem(String id, int count) {}
}
