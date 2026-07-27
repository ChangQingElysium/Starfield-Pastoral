package com.stardew.craft.animal.service;

import com.stardew.craft.animal.data.AnimalWorldData;
import com.stardew.craft.animal.model.FarmAnimalDefinition;
import com.stardew.craft.animal.model.FarmAnimalDefinitions;
import com.stardew.craft.animal.model.FarmAnimalRecord;
import com.stardew.craft.animal.rule.AnimalParityRules;
import com.stardew.craft.player.PlayerStardewDataAPI;
import com.stardew.craft.player.ProfessionType;
import com.stardew.craft.player.SkillType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;

/** Server-owned transaction boundary for manual farm-animal interaction rules. */
public final class AnimalInteractionService {
    private AnimalInteractionService() {
    }

    public static PetResult pet(
            ServerLevel level,
            ServerPlayer player,
            AnimalWorldData data,
            FarmAnimalRecord record
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(record, "record");

        FarmAnimalDefinition definition =
                FarmAnimalDefinitions.find(record.animalTypeId());
        if (definition == null) {
            return new PetResult(
                    Status.DEFINITION_UNAVAILABLE,
                    new AnimalParityRules.PetOutcome(
                            false,
                            0,
                            0,
                            0,
                            record.wasPetToday(),
                            record.wasAutoPetToday()),
                    record.wasAutoPetToday());
        }
        int happinessDrain = definition.happinessDrain();
        ProfessionType happinessProfession = ProfessionType.fromId(
                definition.professionForHappinessBoost());
        boolean hasHappinessProfession = happinessProfession != null
                && PlayerStardewDataAPI.hasProfession(
                        player,
                        happinessProfession
                );

        boolean hadAutoPetToday = record.wasAutoPetToday();
        AnimalParityRules.PetOutcome outcome = AnimalParityRules.pet(
                record.wasPetToday(),
                record.wasAutoPetToday(),
                false,
                happinessDrain,
                hasHappinessProfession
        );
        if (!outcome.applied()) {
            return new PetResult(Status.ALREADY_PET, outcome, hadAutoPetToday);
        }

        record.setWasPetToday(outcome.wasPetToday());
        record.setWasAutoPetToday(outcome.wasAutoPetToday());
        record.addFriendship(outcome.friendshipDelta());
        record.addHappiness(outcome.happinessDelta());
        data.markChanged();
        if (outcome.farmingExperience() > 0) {
            PlayerStardewDataAPI.addExperience(
                    player,
                    SkillType.FARMING,
                    outcome.farmingExperience()
            );
        }
        return new PetResult(Status.APPLIED, outcome, hadAutoPetToday);
    }

    public record PetResult(
            Status status,
            AnimalParityRules.PetOutcome outcome,
            boolean hadAutoPetToday
    ) {
        public PetResult {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    public enum Status {
        APPLIED,
        ALREADY_PET,
        DEFINITION_UNAVAILABLE
    }
}
