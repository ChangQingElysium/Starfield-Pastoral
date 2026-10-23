package com.stardew.craft.platform.fabric.client.keys;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;

@Environment(EnvType.CLIENT)
public final class FabricKeyMappings {
    private FabricKeyMappings() {}
    public static boolean isProduction() { return !FabricLoader.getInstance().isDevelopmentEnvironment(); }
    public static KeyMapping create(String name, IKeyConflictContext context, InputConstants.Type type, int code, String category) {
        return create(name, context, KeyModifier.NONE, type.getOrCreate(code), category);
    }
    public static KeyMapping create(String name, IKeyConflictContext context, InputConstants.Key key, String category) {
        return create(name, context, KeyModifier.NONE, key, category);
    }
    public static KeyMapping create(String name, IKeyConflictContext context, KeyModifier modifier, InputConstants.Type type, int code, String category) {
        return create(name, context, modifier, type.getOrCreate(code), category);
    }
    public static KeyMapping create(String name, IKeyConflictContext context, KeyModifier modifier, InputConstants.Key key, String category) {
        Objects.requireNonNull(context, "context"); Objects.requireNonNull(modifier, "modifier");
        var mapping = new KeyMapping(name, key.getType(), key.getValue(), category);
        var state = state(mapping); state.context(context); state.defaultModifier(modifier);
        setKeyModifierAndCode(mapping, modifier, key);
        return mapping;
    }
    public static InputConstants.Key getKey(KeyMapping mapping) { return extension(mapping).stardewcraft$key(); }
    public static KeyModifier getKeyModifier(KeyMapping mapping) { return state(mapping).modifier(); }
    public static IKeyConflictContext getKeyConflictContext(KeyMapping mapping) { return state(mapping).context(); }
    public static boolean isActiveAndMatches(KeyMapping mapping, InputConstants.Key code) {
        return !code.equals(InputConstants.UNKNOWN) && code.equals(getKey(mapping)) && state(mapping).active(KeyInputState.PHYSICAL);
    }
    public static KeyBindingState state(KeyMapping mapping) { return extension(mapping).stardewcraft$bindingState(); }
    private static KeyMappingExtension extension(KeyMapping mapping) {
        if (mapping instanceof KeyMappingExtension extension) return extension;
        throw new IllegalStateException("Fabric key mapping mixin is missing for " + mapping.getName());
    }
    public static void setKeyModifierAndCode(KeyMapping mapping, KeyModifier modifier, InputConstants.Key key) {
        mapping.setKey(key); state(mapping).modifier(modifier.matches(key) ? KeyModifier.NONE : modifier);
    }
    public static void setToDefault(KeyMapping mapping) {
        setKeyModifierAndCode(mapping, state(mapping).defaultModifier(), mapping.getDefaultKey());
    }
    public static String serialize(KeyMapping mapping, String keyName) {
        var modifier = getKeyModifier(mapping); return keyName + (modifier == KeyModifier.NONE ? "" : ":" + modifier.name());
    }
    public static InputConstants.Key deserialize(KeyMapping mapping, String saved) {
        return deserialize(mapping, saved, InputConstants::getKey);
    }
    public static InputConstants.Key deserialize(KeyMapping mapping, String saved, java.util.function.Function<String, InputConstants.Key> parseKey) {
        int colon = saved.indexOf(':');
        String name = colon < 0 ? saved : saved.substring(0, colon);
        String suffix = colon < 0 ? "" : saved.substring(colon + 1).split(":", 2)[0];
        var key = parseKey.apply(name);
        setKeyModifierAndCode(mapping, KeyModifier.valueFromString(suffix), key);
        return key;
    }
    /** Preserves NeoForge 21.1.217's existing same-key UI fallback, even for disjoint contexts. */
    public static boolean same(KeyMapping first, KeyMapping second) {
        var context = getKeyConflictContext(first); var otherContext = getKeyConflictContext(second);
        var key = getKey(first); var otherKey = getKey(second);
        if (context.conflicts(otherContext) || otherContext.conflicts(context)) {
            var modifier = getKeyModifier(first); var otherModifier = getKeyModifier(second);
            if (modifier.matches(otherKey) || otherModifier.matches(key)) return true;
            if (key.equals(otherKey)) return modifier == otherModifier || context.conflicts(KeyConflictContext.IN_GAME)
                    && (modifier == KeyModifier.NONE || otherModifier == KeyModifier.NONE);
        }
        return key.equals(otherKey);
    }
    public static void click(Iterable<KeyMapping> mappings, InputConstants.Key code, KeyInputState input) {
        for (var mapping : matching(mappings, code, false, input)) extension(mapping).stardewcraft$click();
    }
    public static void set(Iterable<KeyMapping> mappings, InputConstants.Key code, boolean held, KeyInputState input) {
        for (var mapping : matching(mappings, code, !held, input)) mapping.setDown(held);
    }
    /** Mirrors Neo's lookup, using the actual native ALL collection so rebindings never leave a stale index. */
    static List<KeyMapping> matching(Iterable<KeyMapping> mappings, InputConstants.Key code, boolean releasing, KeyInputState input) {
        List<KeyMapping> result = new ArrayList<>();
        var codeModifier = KeyModifier.getKeyModifier(code, input.mac());
        for (var modifier : KeyModifier.MODIFIER_VALUES) if (modifier.isActive(null, input)) {
            if (modifier.matches(code, input.mac())) {
                for (var other : KeyModifier.MODIFIER_VALUES) if (other != codeModifier && other.isActive(null, input))
                    for (var otherCode : other.codes(input.mac())) if (input.isDown(otherCode)) find(mappings, otherCode, modifier, input, result);
            } else find(mappings, code, modifier, input, result);
        }
        if (releasing && codeModifier != KeyModifier.NONE) for (var mapping : mappings) {
            var state = state(mapping);
            if (state.modifier() == codeModifier && !getKey(mapping).equals(InputConstants.UNKNOWN) && state.context().isActive()) result.add(mapping);
        }
        if (releasing || result.isEmpty()) find(mappings, code, KeyModifier.NONE, input, result);
        return result;
    }
    private static void find(Iterable<KeyMapping> mappings, InputConstants.Key code, KeyModifier modifier, KeyInputState input, List<KeyMapping> result) {
        if (code.equals(InputConstants.UNKNOWN)) return;
        for (var mapping : mappings) if (state(mapping).modifier() == modifier && getKey(mapping).equals(code) && state(mapping).active(input)) result.add(mapping);
    }
}
