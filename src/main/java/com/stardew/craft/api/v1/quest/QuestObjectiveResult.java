package com.stardew.craft.api.v1.quest;

/** Result of applying one progress event to an objective runtime. */
public record QuestObjectiveResult(boolean changed, boolean completed, boolean consumed) {
    public static final QuestObjectiveResult NONE = new QuestObjectiveResult(false, false, false);

    public static QuestObjectiveResult progress(boolean completed) {
        return new QuestObjectiveResult(true, completed, false);
    }

    public static QuestObjectiveResult consumed(boolean completed) {
        return new QuestObjectiveResult(true, completed, true);
    }
}
