package com.stardew.craft.gametest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.stardew.craft.StardewCraft;
import java.io.BufferedWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import com.stardew.craft.port.PortItemData;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CollectionTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Port verification only (never shipped from main): writes a version-neutral dump of every
 * piece of content and loaded data so the 1.21.1 and 1.20.1 builds can be diffed.
 * Output: run-game-test/port-parity/&lt;mc version&gt;.jsonl, one "section\tkey\tjson" line each.
 * PORT(1.20.1): same sections, keys and value formats as the 1.21.1 class; only API calls are adapted
 * (item data via the port components, food/attribute/recipe/loot/test-registry accessors of 1.20.1).
 */
@GameTestHolder(StardewCraft.MODID)
@PrefixGameTestTemplate(false)
public final class PortParityDumpGameTests {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeSpecialFloatingPointValues().create();
    private static final String NS = StardewCraft.MODID;

    @GameTest(templateNamespace = "stardewcraft_port_dump", template = "empty", timeoutTicks = 2000)
    public static void dumpContent(GameTestHelper helper) throws Exception {
        MinecraftServer server = helper.getLevel().getServer();
        Path out = Path.of("port-parity", "dump.jsonl");
        Files.createDirectories(out.getParent());
        TreeMap<String, String> lines = new TreeMap<>();
        dumpRegistries(lines);
        dumpBlocks(lines);
        dumpItems(lines);
        dumpTags(lines);
        dumpEntities(lines);
        dumpRecipes(server, lines);
        dumpDatapackRegistries(server, lines);
        dumpModData(lines);
        for (var fn : net.minecraft.gametest.framework.GameTestRegistry.getAllTestFunctions()) {
            put(lines, "gametest", fn.getTestName(), new JsonPrimitive(fn.isRequired()));
        }
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            for (var e : lines.entrySet()) {
                w.write(e.getKey());
                w.write('\t');
                w.write(e.getValue());
                w.write('\n');
            }
        }
        StardewCraft.LOGGER.info("[PORT-PARITY] wrote {} entries to {}", lines.size(), out.toAbsolutePath());
        helper.succeed();
    }

    // ------------------------------------------------------------------ sections

    private static void put(TreeMap<String, String> lines, String section, String key, JsonElement value) {
        lines.put(section + "\t" + key, GSON.toJson(value));
    }

    private static void dumpRegistries(TreeMap<String, String> lines) {
        for (Registry<?> registry : BuiltInRegistries.REGISTRY) {
            JsonArray ids = new JsonArray();
            registry.keySet().stream().filter(id -> id.getNamespace().startsWith(NS)).map(ResourceLocation::toString)
                    .sorted().forEach(ids::add);
            if (!ids.isEmpty()) put(lines, "registry", registry.key().location().toString(), ids);
        }
    }

    private static void dumpBlocks(TreeMap<String, String> lines) {
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(NS)) continue;
            JsonObject b = new JsonObject();
            b.addProperty("explosionResistance", block.getExplosionResistance());
            b.addProperty("friction", block.getFriction());
            b.addProperty("speedFactor", block.getSpeedFactor());
            b.addProperty("jumpFactor", block.getJumpFactor());
            b.addProperty("descriptionId", block.getDescriptionId());
            b.addProperty("item", BuiltInRegistries.ITEM.getKey(block.asItem()).toString());
            b.addProperty("defaultState", stateKey(block.defaultBlockState()));
            JsonObject props = new JsonObject();
            for (Property<?> p : block.getStateDefinition().getProperties()) {
                JsonArray values = new JsonArray();
                p.getPossibleValues().forEach(v -> values.add(propName(p, v)));
                props.add(p.getName(), values);
            }
            b.add("properties", props);
            put(lines, "block", id.toString(), b);
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                put(lines, "blockstate", id + stateKey(state), stateInfo(state));
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String propName(Property p, Object v) {
        return p.getName((Comparable) v);
    }

    private static String stateKey(BlockState state) {
        StringBuilder sb = new StringBuilder("[");
        new TreeMap<>(state.getValues().entrySet().stream().collect(
                java.util.stream.Collectors.toMap(e -> e.getKey().getName(), e -> propName(e.getKey(), e.getValue()))))
                .forEach((k, v) -> sb.append(k).append('=').append(v).append(','));
        return sb.append(']').toString();
    }

    private static JsonObject stateInfo(BlockState s) {
        JsonObject o = new JsonObject();
        var level = EmptyBlockGetter.INSTANCE;
        var pos = BlockPos.ZERO;
        o.add("light", safe(() -> new JsonPrimitive(s.getLightEmission())));
        o.add("hardness", safe(() -> new JsonPrimitive(s.getDestroySpeed(level, pos))));
        o.add("occludes", safe(() -> new JsonPrimitive(s.canOcclude())));
        o.add("randomTicks", safe(() -> new JsonPrimitive(s.isRandomlyTicking())));
        o.add("renderShape", safe(() -> new JsonPrimitive(s.getRenderShape().name())));
        o.add("toolRequired", safe(() -> new JsonPrimitive(s.requiresCorrectToolForDrops())));
        o.add("sound", safe(() -> new JsonPrimitive(s.getSoundType().getBreakSound().getLocation().toString())));
        o.add("mapColor", safe(() -> new JsonPrimitive(s.getMapColor(level, pos).id)));
        o.add("push", safe(() -> new JsonPrimitive(s.getPistonPushReaction().name())));
        o.add("lavaIgnites", safe(() -> new JsonPrimitive(s.ignitedByLava())));
        o.add("replaceable", safe(() -> new JsonPrimitive(s.canBeReplaced())));
        o.add("hasBlockEntity", safe(() -> new JsonPrimitive(s.hasBlockEntity())));
        o.add("signal", safe(() -> new JsonPrimitive(s.isSignalSource())));
        o.add("fluid", safe(() -> new JsonPrimitive(BuiltInRegistries.FLUID.getKey(s.getFluidState().getType()).toString())));
        o.add("shape", safe(() -> shape(s.getShape(level, pos))));
        o.add("collision", safe(() -> shape(s.getCollisionShape(level, pos))));
        return o;
    }

    private static JsonElement shape(VoxelShape shape) {
        JsonArray arr = new JsonArray();
        List<String> boxes = new ArrayList<>();
        for (AABB b : shape.toAabbs()) {
            boxes.add(String.format(java.util.Locale.ROOT, "%.5f,%.5f,%.5f,%.5f,%.5f,%.5f",
                    b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ));
        }
        boxes.stream().sorted().forEach(arr::add);
        return arr;
    }

    private static void dumpItems(TreeMap<String, String> lines) {
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (!id.getNamespace().equals(NS)) continue;
            ItemStack stack = new ItemStack(item);
            JsonObject o = new JsonObject();
            o.addProperty("maxStack", stack.getMaxStackSize());
            o.addProperty("maxDamage", stack.getMaxDamage());
            o.addProperty("damageable", stack.isDamageableItem());
            o.addProperty("rarity", stack.getRarity().name());
            o.addProperty("descriptionId", stack.getDescriptionId());
            o.addProperty("enchantable", stack.isEnchantable());
            o.addProperty("fireResistant", safeBool(item::isFireResistant));
            var food = PortItemData.get(stack, DataComponents.FOOD);
            if (food != null) {
                JsonObject f = new JsonObject();
                f.addProperty("nutrition", food.getNutrition());
                // 1.21 FoodProperties.Builder#build: saturation = FoodConstants.saturationByModifier(nutrition, modifier).
                f.addProperty("saturation", round((float) food.getNutrition() * food.getSaturationModifier() * 2.0F));
                f.addProperty("alwaysEat", food.canAlwaysEat());
                // 1.21 eatDurationTicks = (int) (eatSeconds * 20): 1.6 s by default, 0.8 s for fast() food.
                f.addProperty("eatTicks", food.isFastFood() ? 16 : 32);
                f.add("effects", norm(food.getEffects().stream().map(e -> List.of(
                        effectName(e.getFirst().getEffect()), e.getFirst().getDuration(),
                        e.getFirst().getAmplifier(), round(e.getSecond()))).toList()));
                o.add("food", f);
            }
            o.add("stack", stackJson(stack));
            put(lines, "item", id.toString(), o);
        }
    }

    /** 1.21 {@code Holder<MobEffect>#getRegisteredName()}. */
    private static String effectName(net.minecraft.world.effect.MobEffect effect) {
        ResourceLocation id = BuiltInRegistries.MOB_EFFECT.getKey(effect);
        return id == null ? "[unregistered]" : id.toString();
    }

    private static void dumpTags(TreeMap<String, String> lines) {
        dumpTags(lines, BuiltInRegistries.BLOCK);
        dumpTags(lines, BuiltInRegistries.ITEM);
        dumpTags(lines, BuiltInRegistries.ENTITY_TYPE);
        dumpTags(lines, BuiltInRegistries.FLUID);
    }

    private static <T> void dumpTags(TreeMap<String, String> lines, Registry<T> registry) {
        registry.getTags().forEach(pair -> {
            TagKey<T> key = pair.getFirst();
            List<String> members = new ArrayList<>();
            boolean relevant = key.location().getNamespace().startsWith(NS) || key.location().getNamespace().equals("c");
            for (Holder<T> h : pair.getSecond()) {
                String m = h.unwrapKey().map(k -> k.location().toString()).orElse("?");
                if (m.startsWith(NS)) relevant = true;
                members.add(m);
            }
            if (!relevant) return;
            JsonArray arr = new JsonArray();
            members.stream().sorted().forEach(arr::add);
            put(lines, "tag/" + registry.key().location().getPath(), key.location().toString(), arr);
        });
    }

    private static void dumpEntities(TreeMap<String, String> lines) {
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (!id.getNamespace().equals(NS)) continue;
            JsonObject o = new JsonObject();
            o.addProperty("category", type.getCategory().name());
            o.addProperty("width", round(type.getWidth()));
            o.addProperty("height", round(type.getHeight()));
            o.addProperty("fireImmune", type.fireImmune());
            o.addProperty("trackingRange", type.clientTrackingRange());
            o.addProperty("updateInterval", type.updateInterval());
            o.addProperty("summonable", type.canSummon());
            @SuppressWarnings("unchecked")
            EntityType<? extends LivingEntity> living = (EntityType<? extends LivingEntity>) type;
            if (DefaultAttributes.hasSupplier(type)) {
                AttributeSupplier supplier = DefaultAttributes.getSupplier(living);
                JsonObject attrs = new JsonObject();
                for (Attribute attribute : BuiltInRegistries.ATTRIBUTE) {
                    if (supplier.hasAttribute(attribute)) {
                        attrs.addProperty(attributeName(BuiltInRegistries.ATTRIBUTE.getKey(attribute)),
                                round(supplier.getBaseValue(attribute)));
                    }
                }
                o.add("attributes", attrs);
            }
            put(lines, "entity", id.toString(), o);
        }
    }

    /** 1.21.1 still uses generic./player. prefixed attribute ids, identical to 1.20.1. */
    private static String attributeName(ResourceLocation id) {
        return id.toString();
    }

    private static void dumpRecipes(MinecraftServer server, TreeMap<String, String> lines) {
        var access = server.registryAccess();
        for (var recipe : server.getRecipeManager().getRecipes()) {
            ResourceLocation id = recipe.getId();
            JsonObject o = new JsonObject();
            o.addProperty("type", BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType()).toString());
            o.addProperty("serializer", BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()).toString());
            o.add("result", stackJson(recipe.getResultItem(access)));
            JsonArray ings = new JsonArray();
            for (Ingredient ing : recipe.getIngredients()) {
                JsonArray alts = new JsonArray();
                java.util.Arrays.stream(ing.getItems()).map(st -> BuiltInRegistries.ITEM.getKey(st.getItem()) + "x" + st.getCount())
                        .sorted().forEach(alts::add);
                ings.add(alts);
            }
            o.add("ingredients", ings);
            boolean relevant = id.getNamespace().startsWith(NS) || o.toString().contains(NS + ":");
            if (relevant) put(lines, "recipe", id.toString(), o);
        }
        server.getLootData().getKeys(LootDataType.TABLE).stream()
                .filter(id -> id.getNamespace().startsWith(NS))
                .forEach(id -> put(lines, "loot_table", id.toString(), JsonNull.INSTANCE));
    }

    private static void dumpDatapackRegistries(MinecraftServer server, TreeMap<String, String> lines) {
        server.registryAccess().registries().forEach(entry -> {
            JsonArray ids = new JsonArray();
            entry.value().keySet().stream().filter(id -> id.getNamespace().startsWith(NS))
                    .map(ResourceLocation::toString).sorted().forEach(ids::add);
            if (!ids.isEmpty()) put(lines, "dynamic_registry", entry.key().location().toString(), ids);
        });
    }

    private static void dumpModData(TreeMap<String, String> lines) {
        dumpObject(lines, "moddata/registry", invokeStatic("com.stardew.craft.network.DataRegistrySyncPayload", "current"));
        dumpObject(lines, "moddata/mail", invokeStatic("com.stardew.craft.network.MailIndexSyncPayload", "current"));
        dumpObject(lines, "moddata/jei", invokeStatic("com.stardew.craft.network.JeiCatalogSyncPayload", "currentSharedCatalog"));
        dumpObject(lines, "moddata/festival", invokeStatic("com.stardew.craft.network.FestivalAvailabilitySyncPayload", "current"));
    }

    /** Snapshot builders are package-private; reflection keeps this verification-only class out of their packages. */
    private static Object invokeStatic(String owner, String method) {
        try {
            var m = Class.forName(owner).getDeclaredMethod(method);
            m.setAccessible(true);
            return m.invoke(null);
        } catch (ReflectiveOperationException e) {
            return "ERR " + e;
        }
    }

    /** Records are split per component so a diff points at the exact data family. */
    private static void dumpObject(TreeMap<String, String> lines, String section, Object value) {
        if (value != null && value.getClass().isRecord()) {
            for (RecordComponent c : value.getClass().getRecordComponents()) {
                try {
                    Object v = c.getAccessor().invoke(value);
                    if (v instanceof String s && (s.startsWith("{") || s.startsWith("["))) {
                        put(lines, section, c.getName(), sortJson(com.google.gson.JsonParser.parseString(s)));
                    } else {
                        put(lines, section, c.getName(), norm(v));
                    }
                } catch (ReflectiveOperationException e) {
                    put(lines, section, c.getName(), new JsonPrimitive("ERR " + e));
                }
            }
        } else {
            put(lines, section, "value", norm(value));
        }
    }

    // ------------------------------------------------------------------ normalisation

    private static JsonElement stackJson(ItemStack stack) {
        JsonObject o = new JsonObject();
        o.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        o.addProperty("count", stack.getCount());
        var custom = PortItemData.get(stack, DataComponents.CUSTOM_DATA);
        if (custom != null && !custom.isEmpty()) o.add("custom", nbt(custom.copyTag()));
        var cmd = PortItemData.get(stack, DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) o.addProperty("customModelData", cmd.value());
        var bs = PortItemData.get(stack, DataComponents.BLOCK_STATE);
        if (bs != null && !bs.isEmpty()) o.add("blockState", norm(bs.properties()));
        var name = PortItemData.get(stack, DataComponents.CUSTOM_NAME);
        if (name != null) o.addProperty("name", name.getString());
        var ench = PortItemData.get(stack, DataComponents.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) {
            JsonObject e = new JsonObject();
            ench.entrySet().forEach(en -> e.addProperty(registeredName(en.getKey()), en.getIntValue()));
            o.add("enchantments", sortJson(e));
        }
        return o;
    }

    /** 1.21 {@code Holder#getRegisteredName()}. */
    private static String registeredName(Holder<?> holder) {
        return holder.unwrapKey().map(k -> k.location().toString()).orElse("[unregistered]");
    }

    private static JsonElement nbt(Tag tag) {
        if (tag instanceof CompoundTag c) {
            JsonObject o = new JsonObject();
            new TreeMap<>(c.getAllKeys().stream().collect(java.util.stream.Collectors.toMap(k -> k, k -> k)))
                    .keySet().forEach(k -> o.add(k, nbt(c.get(k))));
            return o;
        }
        if (tag instanceof CollectionTag<?> list) {
            JsonArray a = new JsonArray();
            for (Tag t : list) a.add(nbt(t));
            return a;
        }
        if (tag instanceof NumericTag n) return new JsonPrimitive(roundNumber(n.getAsNumber()));
        if (tag instanceof StringTag s) return new JsonPrimitive(s.getAsString());
        return new JsonPrimitive(String.valueOf(tag));
    }

    private static JsonElement norm(Object o) {
        return norm(o, 0, new IdentityHashMap<>());
    }

    private static JsonElement norm(Object o, int depth, IdentityHashMap<Object, Boolean> seen) {
        if (o == null) return JsonNull.INSTANCE;
        if (depth > 40) return new JsonPrimitive("<depth>");
        if (o instanceof String s) return new JsonPrimitive(s);
        if (o instanceof Boolean b) return new JsonPrimitive(b);
        if (o instanceof Character c) return new JsonPrimitive(c.toString());
        if (o instanceof Number n) return new JsonPrimitive(roundNumber(n));
        if (o instanceof Enum<?> e) return new JsonPrimitive(e.name());
        if (o instanceof ResourceLocation r) return new JsonPrimitive(r.toString());
        if (o instanceof ResourceKey<?> k) return new JsonPrimitive(k.location().toString());
        if (o instanceof TagKey<?> t) return new JsonPrimitive("#" + t.location());
        if (o instanceof Holder<?> h) return new JsonPrimitive(h.unwrapKey().map(k -> k.location().toString()).orElse("<direct>"));
        if (o instanceof Item i) return new JsonPrimitive(BuiltInRegistries.ITEM.getKey(i).toString());
        if (o instanceof Block b) return new JsonPrimitive(BuiltInRegistries.BLOCK.getKey(b).toString());
        if (o instanceof EntityType<?> t) return new JsonPrimitive(BuiltInRegistries.ENTITY_TYPE.getKey(t).toString());
        if (o instanceof BlockState s) return new JsonPrimitive(BuiltInRegistries.BLOCK.getKey(s.getBlock()) + stateKey(s));
        if (o instanceof ItemStack st) return stackJson(st);
        if (o instanceof Ingredient ing) {
            JsonArray a = new JsonArray();
            java.util.Arrays.stream(ing.getItems()).map(x -> stackJson(x).toString()).sorted().forEach(a::add);
            return a;
        }
        if (o instanceof Tag t) return nbt(t);
        if (o instanceof Component c) return new JsonPrimitive(c.getString());
        if (o instanceof JsonElement j) return sortJson(j);
        if (o instanceof Optional<?> opt) return opt.isPresent() ? norm(opt.get(), depth + 1, seen) : JsonNull.INSTANCE;
        if (o instanceof Map<?, ?> m) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            for (var e : m.entrySet()) sorted.put(keyString(e.getKey()), norm(e.getValue(), depth + 1, seen));
            JsonObject obj = new JsonObject();
            sorted.forEach(obj::add);
            return obj;
        }
        if (o instanceof Collection<?> c) {
            List<JsonElement> items = new ArrayList<>();
            for (Object x : c) items.add(norm(x, depth + 1, seen));
            if (o instanceof java.util.Set<?>) items.sort(java.util.Comparator.comparing(JsonElement::toString));
            JsonArray a = new JsonArray();
            items.forEach(a::add);
            return a;
        }
        if (o.getClass().isArray()) {
            JsonArray a = new JsonArray();
            for (int i = 0; i < Array.getLength(o); i++) a.add(norm(Array.get(o, i), depth + 1, seen));
            return a;
        }
        if (seen.containsKey(o)) return new JsonPrimitive("<cycle " + o.getClass().getSimpleName() + ">");
        seen.put(o, true);
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("@type", o.getClass().getSimpleName());
            if (o.getClass().isRecord()) {
                for (RecordComponent c : o.getClass().getRecordComponents()) {
                    obj.add(c.getName(), norm(c.getAccessor().invoke(o), depth + 1, seen));
                }
            } else {
                TreeMap<String, Field> fields = new TreeMap<>();
                for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                    // PORT(1.20.1): port shims of 1.21 vanilla types count as vanilla (1.21 never walks their fields).
                    if (k.getName().startsWith("net.minecraft") || k.getName().startsWith("java.")
                            || k.getName().startsWith("com.stardew.craft.port.net.minecraft.")) break;
                    for (Field f : k.getDeclaredFields()) {
                        if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic()) fields.putIfAbsent(f.getName(), f);
                    }
                }
                for (var e : fields.entrySet()) {
                    e.getValue().setAccessible(true);
                    obj.add(e.getKey(), norm(e.getValue().get(o), depth + 1, seen));
                }
            }
            return obj;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return new JsonPrimitive("<unreadable " + o.getClass().getSimpleName() + ">");
        } finally {
            seen.remove(o);
        }
    }

    private static String keyString(Object k) {
        JsonElement e = norm(k);
        return e.isJsonPrimitive() ? e.getAsString() : e.toString();
    }

    private static JsonElement sortJson(JsonElement e) {
        if (e.isJsonObject()) {
            TreeMap<String, JsonElement> sorted = new TreeMap<>();
            e.getAsJsonObject().entrySet().forEach(en -> sorted.put(en.getKey(), sortJson(en.getValue())));
            JsonObject o = new JsonObject();
            sorted.forEach(o::add);
            return o;
        }
        if (e.isJsonArray()) {
            JsonArray a = new JsonArray();
            e.getAsJsonArray().forEach(x -> a.add(sortJson(x)));
            return a;
        }
        return e;
    }

    private static Number roundNumber(Number n) {
        if (n instanceof Float || n instanceof Double) return round(n.doubleValue());
        return n.longValue();
    }

    private static double round(double v) {
        return Math.round(v * 1e5) / 1e5;
    }

    private interface JsonSupplier {
        JsonElement get() throws Exception;
    }

    private static JsonElement safe(JsonSupplier s) {
        try {
            return s.get();
        } catch (Throwable t) {
            return new JsonPrimitive("ERR " + t.getClass().getSimpleName());
        }
    }

    private interface BoolSupplier {
        boolean get();
    }

    private static boolean safeBool(BoolSupplier s) {
        try {
            return s.get();
        } catch (Throwable t) {
            return false;
        }
    }
}
