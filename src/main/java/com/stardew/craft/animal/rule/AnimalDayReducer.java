package com.stardew.craft.animal.rule;

import com.stardew.craft.animal.model.FarmAnimalDefinition;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure, world-independent port of {@code FarmAnimal.dayUpdate}.
 *
 * <p>The reducer is split at the existing add-on daily-handler boundary. The manager applies
 * {@link #begin(BeginInput)}, lets add-ons mutate the persistent animal record, then constructs a
 * fresh {@link FinishInput} and calls {@link #finish(FinishInput, RandomPort)}. This keeps the
 * source RNG/order rules testable without removing StardewCraft's extension point.
 */
public final class AnimalDayReducer {
    private AnimalDayReducer() {
    }

    public static BeginResult begin(BeginInput input) {
        return begin(input, null);
    }

    /** Runs the begin phase while retaining immutable state checkpoints for diagnostics. */
    public static TracedBeginResult traceBegin(BeginInput input) {
        List<TraceEntry> entries = new ArrayList<>();
        BeginResult result = begin(input, entries);
        return new TracedBeginResult(result, new AnimalDayTrace(entries));
    }

    private static BeginResult begin(
            BeginInput input,
            @Nullable List<TraceEntry> trace
    ) {
        Objects.requireNonNull(input, "input");
        FarmAnimalDefinition definition = input.definition();
        State state = input.state();
        boolean leftOut = false;
        recordTrace(trace, TraceStage.BEGIN_INPUT, state);

        switch (input.homeSituation()) {
            case OUTSIDE_DOOR_OPEN -> {
                int happiness = input.timeOfDay() > 1800
                        ? state.happiness() / 2
                        : state.happiness();
                State returnedHome = state.withHappiness(happiness);
                recordTrace(
                        trace,
                        TraceStage.BEGIN_RETURNED_HOME_EARLY,
                        returnedHome
                );
                return new BeginResult(
                        returnedHome,
                        true,
                        false
                );
            }
            case OUTSIDE_DOOR_CLOSED -> {
                state = state
                        .withMoodMessage(6)
                        .withHappiness(state.happiness() / 2);
                leftOut = true;
            }
            case INSIDE_DOOR_CLOSED ->
                    state = state.withHappiness(
                            state.happiness() + definition.happinessDrain() * 2);
            case NO_HOME, INSIDE_DOOR_OPEN -> {
                // Source has no additional home-state effect.
            }
        }
        recordTrace(trace, TraceStage.BEGIN_HOME_STATE, state);

        state = state.withDaysSinceLastProduce(state.daysSinceLastProduce() + 1);
        recordTrace(trace, TraceStage.BEGIN_PRODUCE_COOLDOWN, state);
        return new BeginResult(state, false, leftOut);
    }

    public static FinishResult finish(FinishInput input, RandomPort random) {
        return finish(input, random, null);
    }

    /** Runs the finish phase while retaining immutable state checkpoints for diagnostics. */
    public static TracedFinishResult traceFinish(
            FinishInput input,
            RandomPort random
    ) {
        List<TraceEntry> entries = new ArrayList<>();
        FinishResult result = finish(input, random, entries);
        return new TracedFinishResult(result, new AnimalDayTrace(entries));
    }

    private static FinishResult finish(
            FinishInput input,
            RandomPort random,
            @Nullable List<TraceEntry> trace
    ) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(random, "random");
        FarmAnimalDefinition definition = input.definition();
        State state = input.state();
        recordTrace(trace, TraceStage.FINISH_INPUT, state);

        if (!state.wasPetToday() && !state.wasAutoPetToday()) {
            state = state
                    .withFriendship(
                            state.friendship()
                                    - AnimalParityRules.missedPetFriendshipPenalty(
                                            state.friendship()))
                    .withHappiness(state.happiness() - 50);
        }
        recordTrace(trace, TraceStage.FINISH_MISSED_PET, state);
        state = state
                .withPetFlags(false, false)
                .withDaysOwned(state.daysOwned() + 1);
        recordTrace(trace, TraceStage.FINISH_DAILY_FLAGS, state);

        if (input.insideAnimalHouse() && state.fullness() < 200 && input.hayConsumed()) {
            state = state.withFullness(255);
        }
        boolean fedToday = state.fullness() >= 200;
        recordTrace(trace, TraceStage.FINISH_FEEDING, state);

        boolean maturedToday = false;
        if (state.fullness() > 200
                || random.nextDouble() < (state.fullness() - 30) / 170.0D) {
            if (state.ageDays() == definition.daysToMature() - 1) {
                state = state
                        .withAgeDays(definition.daysToMature())
                        .withDaysSinceLastProduce(99);
                maturedToday = true;
                if (definition.produceOnMature() && !input.skipDefaultProduction()) {
                    ResourceLocation matureProduce = choose(
                            input.normalProduce(),
                            state.friendship(),
                            random);
                    state = state.withCurrentProduceId(idString(matureProduce));
                }
            } else {
                state = state.withAgeDays(state.ageDays() + 1);
            }
            state = state.withHappiness(
                    state.happiness() + definition.happinessDrain() * 2);
        }
        recordTrace(trace, TraceStage.FINISH_GROWTH, state);

        if (state.fullness() < 200) {
            state = state
                    .withHappiness(state.happiness() - 100)
                    .withFriendship(state.friendship() - 20);
        }
        recordTrace(trace, TraceStage.FINISH_HUNGER, state);

        int productionInterval = input.productionIntervalOverride() == null
                ? AnimalParityRules.productionInterval(
                        definition,
                        state.friendship(),
                        input.hasFasterProduceProfession())
                : input.productionIntervalOverride();
        boolean produceToday = !input.skipDefaultProduction()
                && state.daysSinceLastProduce() >= productionInterval
                && random.nextDouble() < state.fullness() / 200.0D
                && random.nextDouble() < state.happiness() / 70.0D;

        ResourceLocation selectedProduce = null;
        int selectedQuality = state.produceQuality();
        if (produceToday && state.ageDays() >= definition.daysToMature()) {
            selectedProduce = choose(
                    input.normalProduce(),
                    state.friendship(),
                    random);
            if (random.nextDouble() < state.happiness() / 150.0D) {
                float happinessModifier = state.happiness() > 200
                        ? state.happiness() * 1.5F
                        : (state.happiness() <= 100 ? state.happiness() - 100 : 0.0F);
                ResourceLocation deluxeProduce = choose(
                        input.deluxeProduce(),
                        state.friendship(),
                        random);
                if (definition.deluxeProduceCareDivisor() >= 0.0D
                        && deluxeProduce != null
                        && state.friendship() >= definition.deluxeProduceMinimumFriendship()
                        && random.nextDouble() < ((state.friendship() + happinessModifier)
                                / definition.deluxeProduceCareDivisor())
                                + input.averageDailyLuck()
                                * definition.deluxeProduceLuckMultiplier()) {
                    selectedProduce = deluxeProduce;
                }
                state = state.withDaysSinceLastProduce(0);
                selectedQuality = AnimalParityRules.rollProduceQuality(
                        state.friendship(),
                        state.happiness(),
                        input.hasQualityProfession(),
                        random::nextDouble
                );
            }
        }

        Production production = null;
        if (definition.harvestType() != FarmAnimalDefinition.HarvestType.DROP_OVERNIGHT
                && produceToday) {
            state = state
                    .withCurrentProduceId(idString(selectedProduce))
                    .withProduceQuality(selectedQuality);
            if (selectedProduce != null) {
                production = new Production(
                        selectedProduce,
                        selectedQuality,
                        Delivery.HELD
                );
            }
        } else if (selectedProduce != null) {
            production = new Production(
                    selectedProduce,
                    selectedQuality,
                    Delivery.DROP_OVERNIGHT
            );
        }
        recordTrace(trace, TraceStage.FINISH_PRODUCTION, state);

        if (!input.wasLeftOutLastNight()) {
            int mood = state.fullness() < 30
                    ? 4
                    : state.happiness() < 30
                            ? 3
                            : state.happiness() < 200 ? 2 : 1;
            state = state.withMoodMessage(mood);
        }

        state = state
                .withFullness(input.festivalDay() ? 250 : 0)
                .withWasFedToday(fedToday || input.festivalDay());
        recordTrace(trace, TraceStage.FINISH_DAY_END, state);
        return new FinishResult(state, production, maturedToday);
    }

    private static void recordTrace(
            @Nullable List<TraceEntry> trace,
            TraceStage stage,
            State state
    ) {
        if (trace != null) {
            trace.add(new TraceEntry(stage, state));
        }
    }

    /**
     * Applies the source {@code Object.DayUpdate} auto-petter pass.
     *
     * <p>Stardew Valley updates every animal first and only then updates the objects in the
     * animal house. Keeping this as a separate reducer stage means the flag protects the animal
     * on the following day and a same-day manual pet receives only the remaining friendship
     * credit.
     */
    public static State applyAutoPetter(
            FarmAnimalDefinition definition,
            State state,
            boolean insideAnimalHouse,
            boolean hasAutoPetter
    ) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(state, "state");
        if (!insideAnimalHouse || !hasAutoPetter) {
            return state;
        }
        AnimalParityRules.PetOutcome pet = AnimalParityRules.pet(
                state.wasPetToday(),
                state.wasAutoPetToday(),
                true,
                definition.happinessDrain(),
                false
        );
        if (!pet.applied()) {
            return state;
        }
        return state
                .withFriendship(state.friendship() + pet.friendshipDelta())
                .withHappiness(state.happiness() + pet.happinessDelta())
                .withPetFlags(pet.wasPetToday(), pet.wasAutoPetToday());
    }

    /**
     * Applies the source {@code FarmAnimal.OnDayStarted} fullness rule.
     *
     * <p>Animals whose data declares {@code GrassEatAmount < 1} don't need hay or pasture.
     * This stage intentionally runs after {@link #finish(FinishInput, RandomPort)}, because
     * {@code dayUpdate} first clears fullness and {@code OnDayStarted} restores it after the
     * overnight save.
     */
    public static State applyDayStarted(
            State state,
            int grassEatAmount
    ) {
        Objects.requireNonNull(state, "state");
        if (grassEatAmount >= 1) {
            return state;
        }
        return state
                .withFullness(255)
                .withWasFedToday(true);
    }

    @Nullable
    private static ResourceLocation choose(
            List<ProduceCandidate> candidates,
            int friendship,
            RandomPort random
    ) {
        List<ProduceCandidate> eligible = candidates.stream()
                .filter(candidate ->
                        friendship >= candidate.minimumFriendship())
                .toList();
        return eligible.isEmpty()
                ? null
                : eligible.get(random.nextInt(eligible.size()))
                        .itemId();
    }

    private static String idString(@Nullable ResourceLocation itemId) {
        return itemId == null ? "" : itemId.toString();
    }

    public record BeginInput(
            FarmAnimalDefinition definition,
            State state,
            HomeSituation homeSituation,
            int timeOfDay
    ) {
        public BeginInput {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(homeSituation, "homeSituation");
        }
    }

    public record BeginResult(
            State state,
            boolean returnedHomeEarly,
            boolean wasLeftOutLastNight
    ) {
        public BeginResult {
            Objects.requireNonNull(state, "state");
        }
    }

    public record FinishInput(
            FarmAnimalDefinition definition,
            State state,
            boolean wasLeftOutLastNight,
            boolean insideAnimalHouse,
            boolean hayConsumed,
            boolean festivalDay,
            boolean skipDefaultProduction,
            boolean hasFasterProduceProfession,
            boolean hasQualityProfession,
            double averageDailyLuck,
            @Nullable Integer productionIntervalOverride,
            List<ProduceCandidate> normalProduce,
            List<ProduceCandidate> deluxeProduce
    ) {
        public FinishInput {
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(state, "state");
            normalProduce = List.copyOf(normalProduce);
            deluxeProduce = List.copyOf(deluxeProduce);
        }
    }

    public record FinishResult(
            State state,
            @Nullable Production production,
            boolean maturedToday
    ) {
        public FinishResult {
            Objects.requireNonNull(state, "state");
        }
    }

    /** Optional diagnostic snapshot; the ordinary reducer path allocates no trace entries. */
    public record AnimalDayTrace(List<TraceEntry> entries) {
        public AnimalDayTrace {
            entries = List.copyOf(entries);
        }
    }

    public record TraceEntry(TraceStage stage, State state) {
        public TraceEntry {
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(state, "state");
        }
    }

    public record TracedBeginResult(
            BeginResult result,
            AnimalDayTrace trace
    ) {
        public TracedBeginResult {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(trace, "trace");
        }
    }

    public record TracedFinishResult(
            FinishResult result,
            AnimalDayTrace trace
    ) {
        public TracedFinishResult {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(trace, "trace");
        }
    }

    public record State(
            int ageDays,
            int daysOwned,
            int friendship,
            int happiness,
            int fullness,
            int daysSinceLastProduce,
            boolean wasPetToday,
            boolean wasAutoPetToday,
            boolean wasFedToday,
            int moodMessage,
            String currentProduceId,
            int produceQuality
    ) {
        public State {
            ageDays = Math.max(0, ageDays);
            daysOwned = Math.max(0, daysOwned);
            friendship = Math.max(0, Math.min(1000, friendship));
            happiness = Math.max(0, Math.min(255, happiness));
            fullness = Math.max(0, Math.min(255, fullness));
            daysSinceLastProduce = Math.max(0, daysSinceLastProduce);
            moodMessage = Math.max(0, moodMessage);
            currentProduceId = Objects.requireNonNullElse(currentProduceId, "");
            produceQuality = Math.max(0, produceQuality);
        }

        public State withAgeDays(int value) {
            return copy(value, daysOwned, friendship, happiness, fullness,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, wasFedToday,
                    moodMessage, currentProduceId, produceQuality);
        }

        public State withDaysOwned(int value) {
            return copy(ageDays, value, friendship, happiness, fullness,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, wasFedToday,
                    moodMessage, currentProduceId, produceQuality);
        }

        public State withFriendship(int value) {
            return copy(ageDays, daysOwned, value, happiness, fullness,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, wasFedToday,
                    moodMessage, currentProduceId, produceQuality);
        }

        public State withHappiness(int value) {
            return copy(ageDays, daysOwned, friendship, value, fullness,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, wasFedToday,
                    moodMessage, currentProduceId, produceQuality);
        }

        public State withFullness(int value) {
            return copy(ageDays, daysOwned, friendship, happiness, value,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, wasFedToday,
                    moodMessage, currentProduceId, produceQuality);
        }

        public State withDaysSinceLastProduce(int value) {
            return copy(ageDays, daysOwned, friendship, happiness, fullness,
                    value, wasPetToday, wasAutoPetToday, wasFedToday,
                    moodMessage, currentProduceId, produceQuality);
        }

        public State withPetFlags(boolean petToday, boolean autoPetToday) {
            return copy(ageDays, daysOwned, friendship, happiness, fullness,
                    daysSinceLastProduce, petToday, autoPetToday, wasFedToday,
                    moodMessage, currentProduceId, produceQuality);
        }

        public State withWasFedToday(boolean value) {
            return copy(ageDays, daysOwned, friendship, happiness, fullness,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, value,
                    moodMessage, currentProduceId, produceQuality);
        }

        public State withMoodMessage(int value) {
            return copy(ageDays, daysOwned, friendship, happiness, fullness,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, wasFedToday,
                    value, currentProduceId, produceQuality);
        }

        public State withCurrentProduceId(String value) {
            return copy(ageDays, daysOwned, friendship, happiness, fullness,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, wasFedToday,
                    moodMessage, value, produceQuality);
        }

        public State withProduceQuality(int value) {
            return copy(ageDays, daysOwned, friendship, happiness, fullness,
                    daysSinceLastProduce, wasPetToday, wasAutoPetToday, wasFedToday,
                    moodMessage, currentProduceId, value);
        }

        private State copy(
                int newAgeDays,
                int newDaysOwned,
                int newFriendship,
                int newHappiness,
                int newFullness,
                int newDaysSinceLastProduce,
                boolean newWasPetToday,
                boolean newWasAutoPetToday,
                boolean newWasFedToday,
                int newMoodMessage,
                String newCurrentProduceId,
                int newProduceQuality
        ) {
            return new State(
                    newAgeDays,
                    newDaysOwned,
                    newFriendship,
                    newHappiness,
                    newFullness,
                    newDaysSinceLastProduce,
                    newWasPetToday,
                    newWasAutoPetToday,
                    newWasFedToday,
                    newMoodMessage,
                    newCurrentProduceId,
                    newProduceQuality
            );
        }
    }

    public record Production(
            ResourceLocation itemId,
            int quality,
            Delivery delivery
    ) {
        public Production {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(delivery, "delivery");
        }
    }

    /**
     * A condition-approved produce entry whose friendship threshold is evaluated inside the
     * reducer, after source-order missed-pet and hunger penalties have changed friendship.
     */
    public record ProduceCandidate(
            ResourceLocation itemId,
            int minimumFriendship
    ) {
        public ProduceCandidate {
            Objects.requireNonNull(itemId, "itemId");
            if (minimumFriendship < 0) {
                throw new IllegalArgumentException(
                        "minimumFriendship must be non-negative");
            }
        }
    }

    public enum HomeSituation {
        NO_HOME,
        INSIDE_DOOR_OPEN,
        INSIDE_DOOR_CLOSED,
        OUTSIDE_DOOR_OPEN,
        OUTSIDE_DOOR_CLOSED
    }

    public enum Delivery {
        DROP_OVERNIGHT,
        HELD
    }

    public enum TraceStage {
        BEGIN_INPUT,
        BEGIN_HOME_STATE,
        BEGIN_RETURNED_HOME_EARLY,
        BEGIN_PRODUCE_COOLDOWN,
        FINISH_INPUT,
        FINISH_MISSED_PET,
        FINISH_DAILY_FLAGS,
        FINISH_FEEDING,
        FINISH_GROWTH,
        FINISH_HUNGER,
        FINISH_PRODUCTION,
        FINISH_DAY_END
    }

    public interface RandomPort {
        double nextDouble();

        int nextInt(int bound);
    }
}
