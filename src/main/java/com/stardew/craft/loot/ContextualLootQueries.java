package com.stardew.craft.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stardew.craft.api.v1.action.*;
import com.stardew.craft.api.v1.condition.*;
import com.stardew.craft.api.v1.query.*;
import com.stardew.craft.player.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Composable content rules. Selection is pure; callers commit collected actions after accepting the result. */
public final class ContextualLootQueries {
    private ContextualLootQueries() {}
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("stardewcraft", path); }
    private static List<ItemStack> resolve(StardewItemQuery query, StardewItemQueryContext context) {
        return StardewItemQueries.resolve(query, context).getOrThrow();
    }
    public static void register() {
        StardewItemQueries.register(id("qualified_item"), Qualified.CODEC, (ctx, value) -> {
            var stack = com.stardew.craft.fishpond.service.FishPondQualifiedItemService.createItemStack(value.item(), value.count());
            return stack.isEmpty() ? List.of() : List.of(stack);
        });
        StardewItemQueries.register(id("conditional"), Branch.CODEC, (ctx, rule) -> {
            boolean pass = rule.minimum().entrySet().stream().allMatch(e -> metric(ctx, e.getKey()) >= e.getValue())
                    && rule.maximum().entrySet().stream().allMatch(e -> metric(ctx, e.getKey()) <= e.getValue())
                    && rule.when().stream().allMatch(c -> StardewConditions.test(c,
                        new StardewConditionContext(ctx.level(), ctx.player())).getOrThrow());
            double chance = rule.chance() + rule.chanceAdd().entrySet().stream()
                    .mapToDouble(e -> metric(ctx, e.getKey()) * e.getValue()).sum();
            for (String factor : rule.chanceScale()) chance *= metric(ctx, factor);
            if (pass && (chance >= 1 || chance > 0 && ctx.random().nextDouble() < chance)) return resolve(rule.query(), ctx);
            return rule.otherwise().map(q -> resolve(q, ctx)).orElse(List.of());
        }, (owner, rule) -> {
            var refs = new ArrayList<>(StardewItemQueries.contentReferences(owner, rule.query()).getOrThrow());
            rule.otherwise().ifPresent(q -> refs.addAll(StardewItemQueries.contentReferences(owner, q).getOrThrow()));
            return refs;
        });
        for (String mode : List.of("all", "first_success", "eligible_choice")) {
            StardewItemQueries.register(id(mode), StardewItemQueries.CODEC.listOf().fieldOf("queries").codec(), (ctx, queries) -> {
                List<ItemStack> result = new ArrayList<>();
                List<List<ItemStack>> choices = new ArrayList<>();
                var parameters = new HashMap<>(ctx.parameters());
                for (var query : queries) {
                    // In choice mode, evaluate candidates without committing their effects.
                    var effects = new ArrayList<StardewAction>();
                    parameters.put("prior_output_count", ctx.parameters().getOrDefault("prior_output_count", 0d) + result.size());
                    var nested = new StardewItemQueryContext(ctx.level(), ctx.player(), ctx.random(), parameters,
                            effects::add);
                    var stacks = resolve(query, nested);
                    if (mode.equals("eligible_choice")) {
                        if (!effects.isEmpty()) throw new IllegalArgumentException("eligible_choice candidates must not contain deferred actions");
                        if (!stacks.isEmpty()) choices.add(stacks);
                    } else {
                        if (!stacks.isEmpty()) {
                            effects.forEach(ctx.deferredActions());
                            rememberVirtualEffects(ctx, parameters, effects);
                            if (mode.equals("first_success")) return stacks;
                        }
                        result.addAll(stacks);
                    }
                }
                return mode.equals("eligible_choice")
                        ? choices.isEmpty() ? List.of() : choices.get(ctx.random().nextInt(choices.size())) : result;
            }, (owner, queries) -> queries.stream().flatMap(q -> StardewItemQueries.contentReferences(owner, q).getOrThrow().stream()).toList());
        }
        StardewItemQueries.register(id("repeat"), Repeat.CODEC, (ctx, rule) -> {
            List<ItemStack> result = new ArrayList<>();
            double chance = rule.chance();
            var parameters = new HashMap<>(ctx.parameters());
            for (int i = 0; i < rule.limit() && ctx.random().nextDouble() < chance; i++, chance *= rule.decay()) {
                parameters.put("prior_output_count", ctx.parameters().getOrDefault("prior_output_count", 0d) + result.size());
                var effects = new ArrayList<StardewAction>();
                var stacks = resolve(rule.query(), new StardewItemQueryContext(ctx.level(), ctx.player(), ctx.random(), parameters, effects::add));
                result.addAll(stacks);
                if (!stacks.isEmpty()) { effects.forEach(ctx.deferredActions()); rememberVirtualEffects(ctx, parameters, effects); }
            }
            return result;
        }, (owner, rule) -> StardewItemQueries.contentReferences(owner, rule.query()).getOrThrow());
        StardewItemQueries.register(id("multiply_count"), Multiply.CODEC, (ctx, rule) -> {
            var result = new ArrayList<>(resolve(rule.query(), ctx));
            for (int i = rule.lastOnly() ? Math.max(0, result.size() - 1) : 0; i < result.size(); i++)
                result.get(i).setCount(Math.multiplyExact(result.get(i).getCount(), rule.factor()));
            return result;
        }, (owner, rule) -> StardewItemQueries.contentReferences(owner, rule.query()).getOrThrow());
        StardewItemQueries.register(id("with_actions"), WithActions.CODEC, (ctx, rule) -> {
            var actions = new ArrayList<StardewAction>();
            var nested = new StardewItemQueryContext(ctx.level(), ctx.player(), ctx.random(), ctx.parameters(), actions::add);
            var result = resolve(rule.query(), nested);
            if (!result.isEmpty()) {
                actions.forEach(ctx.deferredActions());
                rule.actions().forEach(ctx.deferredActions());
            }
            return result;
        }, (owner, rule) -> {
            var refs = new ArrayList<>(StardewItemQueries.contentReferences(owner, rule.query()).getOrThrow());
            rule.actions().forEach(a -> refs.addAll(StardewActions.contentReferences(owner, a).getOrThrow()));
            return refs;
        });
        StardewActions.register(id("increment_stat"), StatChange.CODEC, (ctx, value) -> {
            PlayerDataManager.getPlayerData(ctx.player()).incrementStat(value.stat(), value.amount());
            PlayerDataEventHandler.syncPlayerData(ctx.player(), PlayerDataManager.getPlayerData(ctx.player()));
            return StardewActionResult.ok();
        });
        StardewActions.register(id("remember_special_item"), Codec.STRING.fieldOf("item").codec(), (ctx, value) -> {
            PlayerDataManager.getPlayerData(ctx.player()).addSpecialItem(value);
            return StardewActionResult.ok();
        });
        StardewItemQueries.register(id("natural_trinket"), Codec.unit("natural"), (ctx, ignored) -> {
            var stack = com.stardew.craft.item.trinket.StardewTrinketItem.createRandomNaturalTrinket(
                    net.minecraft.util.RandomSource.create(ctx.random().nextLong()), ctx.player());
            return stack.isEmpty() ? List.of() : List.of(stack);
        });
        StardewItemQueries.<Qualified>registerPreview(id("qualified_item"), (ctx, d) -> {
            var stack = com.stardew.craft.fishpond.service.FishPondQualifiedItemService.createItemStack(d.item(), d.count());
            return stack.isEmpty() ? List.of() : List.of(stack);
        });
        StardewItemQueries.<Branch>registerPreview(id("conditional"), (ctx, d) -> {
            var result = new ArrayList<>(StardewItemQueries.preview(d.query(), ctx).getOrThrow());
            d.otherwise().ifPresent(q -> result.addAll(StardewItemQueries.preview(q, ctx).getOrThrow()));
            return result;
        });
        for (String mode : List.of("all", "first_success", "eligible_choice"))
            StardewItemQueries.<List<StardewItemQuery>>registerPreview(id(mode), (ctx, queries) -> queries.stream()
                    .flatMap(q -> StardewItemQueries.preview(q, ctx).getOrThrow().stream()).toList());
        StardewItemQueries.<Repeat>registerPreview(id("repeat"), (ctx, d) -> StardewItemQueries.preview(d.query(), ctx).getOrThrow());
        StardewItemQueries.<WithActions>registerPreview(id("with_actions"), (ctx, d) -> StardewItemQueries.preview(d.query(), ctx).getOrThrow());
        StardewItemQueries.<Multiply>registerPreview(id("multiply_count"), (ctx, d) -> StardewItemQueries.preview(d.query(), ctx).getOrThrow().stream()
                .map(stack -> stack.copyWithCount(Math.multiplyExact(stack.getCount(), d.factor()))).toList());
    }

    /** Make later rolls in this same query see pending progress, without mutating the real player. */
    private static void rememberVirtualEffects(StardewItemQueryContext context, Map<String, Double> values, List<StardewAction> effects) {
        for (var action : effects) {
            if (!Set.of(id("remember_special_item"), id("increment_stat"), id("set_flag")).contains(action.type())) continue;
            var encoded = StardewActions.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, action).getOrThrow();
            var data = encoded.getAsJsonObject().getAsJsonObject("data");
            if (action.type().equals(id("remember_special_item"))) {
                values.put("special_item:" + data.get("item").getAsString(), 1d);
            } else if (action.type().equals(id("increment_stat"))) {
                String key = "stat:" + data.get("stat").getAsString();
                values.put(key, values.getOrDefault(key, metric(context, key)) + data.get("amount").getAsInt());
            } else if (action.type().equals(id("set_flag"))) {
                String flag = data.get("id").getAsString();
                values.put("flag:" + flag, 1d); values.put("mail:" + flag, 1d);
            }
        }
    }

    public static double metric(StardewItemQueryContext ctx, String key) {
        if (ctx.parameters().containsKey(key)) return ctx.parameters().get(key);
        var player = ctx.player();
        if (key.equals("player_present")) return player == null ? 0 : 1;
        if (key.equals("can_spawn_trinket")) return com.stardew.craft.item.trinket.StardewTrinketItem.canSpawnFor(player) ? 1 : 0;
        if (player == null) return 0;
        var data = PlayerDataManager.getPlayerData(player);
        if (key.startsWith("stat:")) return data.getStat(key.substring(5));
        if (key.startsWith("flag:")) return data.hasMailFlag(key.substring(5)) ? 1 : 0;
        if (key.startsWith("mail:")) return com.stardew.craft.mail.MailService.hasOrWillReceiveMail(player, key.substring(5)) ? 1 : 0;
        if (key.startsWith("special_item:")) return data.hasSpecialItem(key.substring(13)) ? 1 : 0;
        if (key.startsWith("mastery:")) return data.hasMastery(SkillType.valueOf(key.substring(8).toUpperCase(Locale.ROOT))) ? 1 : 0;
        if (key.startsWith("skill:")) return PlayerStardewDataAPI.getSkillLevel(player, SkillType.valueOf(key.substring(6).toUpperCase(Locale.ROOT)));
        if (key.equals("deepest_mine_level")) return com.stardew.craft.mining.MiningDataManager.getPlayerData(player).getMaxFloorReached();
        if (key.equals("luck_buff")) return Math.max(0, PlayerStardewDataAPI.getLuckBuffLevel(player));
        if (key.equals("can_find_lost_book")) return com.stardew.craft.museum.LostBookService.canFindAnother(player) ? 1 : 0;
        if (key.equals("host_volcano_shortcut")) {
            var host = player.getServer().getPlayerList().getPlayers().stream()
                    .filter(p -> player.getServer().isSingleplayerOwner(p.getGameProfile())).findFirst().orElse(player);
            return com.stardew.craft.mail.MailService.hasOrWillReceiveMail(host, "volcanoShortcutUnlocked") ? 1 : 0;
        }
        throw new IllegalArgumentException("Unknown loot context value: " + key);
    }
    private static final Codec<Double> FINITE = Codec.DOUBLE.validate(value -> Double.isFinite(value)
            ? com.mojang.serialization.DataResult.success(value)
            : com.mojang.serialization.DataResult.error(() -> "Loot numbers must be finite"));
    private static final Codec<Map<String, Double>> NUMBERS = Codec.unboundedMap(Codec.STRING, FINITE);
    public record Branch(StardewItemQuery query, Optional<StardewItemQuery> otherwise,
            List<StardewCondition> when, Map<String, Double> minimum, Map<String, Double> maximum,
            double chance, Map<String, Double> chanceAdd, List<String> chanceScale) {
        public Branch {
            when = List.copyOf(when); minimum = Map.copyOf(minimum); maximum = Map.copyOf(maximum);
            chanceAdd = Map.copyOf(chanceAdd); chanceScale = List.copyOf(chanceScale);
        }
        static final Codec<Branch> CODEC = RecordCodecBuilder.create(i -> i.group(
                StardewItemQueries.CODEC.fieldOf("query").forGetter(Branch::query),
                StardewItemQueries.CODEC.optionalFieldOf("otherwise").forGetter(Branch::otherwise),
                StardewConditions.CODEC.listOf().optionalFieldOf("when", List.of()).forGetter(Branch::when),
                NUMBERS.optionalFieldOf("minimum", Map.of()).forGetter(Branch::minimum),
                NUMBERS.optionalFieldOf("maximum", Map.of()).forGetter(Branch::maximum),
                FINITE.optionalFieldOf("chance", 1d).forGetter(Branch::chance),
                NUMBERS.optionalFieldOf("chance_add", Map.of()).forGetter(Branch::chanceAdd),
                Codec.STRING.listOf().optionalFieldOf("chance_scale", List.of()).forGetter(Branch::chanceScale)
        ).apply(i, Branch::new));
    }
    public record Repeat(StardewItemQuery query, double chance, double decay, int limit) {
        static final Codec<Repeat> CODEC = RecordCodecBuilder.create(i -> i.group(
                StardewItemQueries.CODEC.fieldOf("query").forGetter(Repeat::query),
                Codec.doubleRange(0, 1).optionalFieldOf("chance", 1d).forGetter(Repeat::chance),
                Codec.doubleRange(0, 1).fieldOf("decay").forGetter(Repeat::decay),
                Codec.intRange(1, 256).optionalFieldOf("limit", 256).forGetter(Repeat::limit)
        ).apply(i, Repeat::new));
    }
    public record Multiply(StardewItemQuery query, int factor, boolean lastOnly) {
        static final Codec<Multiply> CODEC = RecordCodecBuilder.create(i -> i.group(
                StardewItemQueries.CODEC.fieldOf("query").forGetter(Multiply::query),
                Codec.intRange(1, 64).fieldOf("factor").forGetter(Multiply::factor),
                Codec.BOOL.optionalFieldOf("last_only", false).forGetter(Multiply::lastOnly)
        ).apply(i, Multiply::new));
    }
    public record WithActions(StardewItemQuery query, List<StardewAction> actions) {
        static final Codec<WithActions> CODEC = RecordCodecBuilder.create(i -> i.group(
                StardewItemQueries.CODEC.fieldOf("query").forGetter(WithActions::query),
                StardewActions.CODEC.listOf().fieldOf("actions").forGetter(WithActions::actions)
        ).apply(i, WithActions::new));
    }
    public record StatChange(String stat, int amount) {
        static final Codec<StatChange> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("stat").forGetter(StatChange::stat),
                Codec.INT.fieldOf("amount").forGetter(StatChange::amount)
        ).apply(i, StatChange::new));
    }
    public record Qualified(String item, int count) {
        static final Codec<Qualified> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("item").forGetter(Qualified::item),
                Codec.intRange(1, 4096).optionalFieldOf("count", 1).forGetter(Qualified::count)
        ).apply(i, Qualified::new));
    }
}
