package com.stardew.craft.api.v1.quest;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** One server-authoritative gameplay event offered to an active quest objective. */
public record QuestProgressEvent(
        ResourceLocation type,
        String subject,
        String target,
        int amount
) {
    public QuestProgressEvent {
        Objects.requireNonNull(type, "type");
        subject = subject == null ? "" : subject;
        target = target == null ? "" : target;
        if (amount < 0) {
            throw new IllegalArgumentException("Quest progress amount must be non-negative");
        }
    }
}
