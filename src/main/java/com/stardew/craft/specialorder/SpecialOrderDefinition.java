package com.stardew.craft.specialorder;

import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderObjective;
import com.stardew.craft.api.v1.specialorder.StardewSpecialOrderReward;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public record SpecialOrderDefinition(
    String id,
    String requester,
    Duration duration,
    boolean repeatable,
    List<String> requiredTags,
    String titleKey,
    String textKey,
    List<ObjectiveDefinition> objectives,
    List<RewardDefinition> rewards,
    List<RandomElement> randomElements,
    String itemToRemoveOnEnd,
    String mailToRemoveOnEnd
) {
    public enum Duration {
        WEEK,
        TWO_WEEKS,
        MONTH
    }

    public enum ObjectiveType {
        COLLECT,
        DONATE,
        DELIVER,
        FISH,
        SHIP,
        SLAY
    }

    public enum RewardType {
        MONEY,
        MAIL,
        FRIENDSHIP
    }

    public record ObjectiveDefinition(
        ObjectiveType type,
        String textKey,
        int requiredCount,
        String acceptedTags,
        String dropBoxId,
        String targetName,
        int minimumCapacity,
        String messageKey,
        @Nullable StardewSpecialOrderObjective extension,
        String typeName
    ) {
        public ObjectiveDefinition(
                ObjectiveType type, String textKey, int requiredCount, String acceptedTags,
                String dropBoxId, String targetName, int minimumCapacity, String messageKey) {
            this(type, textKey, requiredCount, acceptedTags, dropBoxId, targetName,
                    minimumCapacity, messageKey, null, type.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public record RewardDefinition(
        RewardType type,
        int amount,
        String mailId,
        boolean noLetter,
        boolean host,
        @Nullable StardewSpecialOrderReward extension,
        String typeName
    ) {
        public RewardDefinition(RewardType type, int amount, String mailId, boolean noLetter, boolean host) {
            this(type, amount, mailId, noLetter, host, null,
                    type.name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    public record RandomElement(
        String name,
        List<RandomOption> options
    ) {
    }

    public record RandomOption(
        List<String> requiredTags,
        Map<String, String> values
    ) {
    }
}
