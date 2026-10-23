package com.stardew.craft.platform.fabric.client.keys;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.KeyMapping;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class KeyDispatchTest {
    private static final AtomicInteger IDS = new AtomicInteger();
    private static final InputConstants.Key R = key(GLFW.GLFW_KEY_R);
    private static final InputConstants.Key SHIFT = key(GLFW.GLFW_KEY_LEFT_SHIFT);
    private static final InputConstants.Key CONTROL = key(GLFW.GLFW_KEY_LEFT_CONTROL);
    private static final Input NONE = new Input(false, false, false, false, Set.of());

    @Test
    void everyActiveMappingWithTheSameCodeReceivesTheClickAndInactiveMappingsDoNot() {
        var game = new Context(true, true); var blueprint = new Context(true, false);
        var skill = mapping(R, game, KeyModifier.NONE); var rotate = mapping(R, blueprint, KeyModifier.NONE);
        var menu = mapping(R, new Context(false, false), KeyModifier.NONE);
        List<KeyMapping> mappings = List.of(skill, rotate, menu);
        FabricKeyMappings.click(mappings, R, NONE);
        FabricKeyMappings.set(mappings, R, true, NONE);
        assertEquals(1, skill.clicks); assertEquals(1, rotate.clicks); assertEquals(0, menu.clicks);
        assertTrue(skill.held); assertTrue(rotate.held); assertFalse(menu.held);
        blueprint.active = false;
        assertFalse(rotate.state.active(NONE)); // isDown() applies this even if the raw flag was previously held.
    }

    @Test
    void modifiedBindingsTakePriorityButOrdinaryInGameKeysStillWorkWhenNoModifiedBindingMatches() {
        var game = new Context(true, true);
        var ordinary = mapping(R, game, KeyModifier.NONE); var modified = mapping(R, game, KeyModifier.SHIFT);
        var shiftDown = new Input(false, true, false, false, Set.of(SHIFT));
        assertEquals(List.of(modified), FabricKeyMappings.matching(List.of(ordinary, modified), R, false, shiftDown));
        assertEquals(List.of(ordinary), FabricKeyMappings.matching(List.of(ordinary), R, false, shiftDown));
        assertEquals(List.of(ordinary), FabricKeyMappings.matching(List.of(ordinary, modified), R, false, NONE));
        var custom = mapping(R, new Context(true, false), KeyModifier.NONE);
        assertTrue(FabricKeyMappings.matching(List.of(custom), R, false, shiftDown).isEmpty());
        assertTrue(KeyModifier.NONE.isActive(game, shiftDown));
        assertFalse(KeyModifier.NONE.isActive(custom.state.context(), shiftDown));
    }

    @Test
    void releasingAKeyOrItsModifierClearsAllActiveAffectedBindingsAndKeepsUnboundKeysOut() {
        var game = new Context(true, true);
        var ordinary = mapping(R, game, KeyModifier.NONE); var modified = mapping(R, game, KeyModifier.SHIFT);
        var unbound = mapping(InputConstants.UNKNOWN, game, KeyModifier.SHIFT);
        var inactive = mapping(R, new Context(false, true), KeyModifier.SHIFT);
        for (var binding : List.of(ordinary, modified, unbound, inactive)) binding.held = true;
        var mappings = List.<KeyMapping>of(ordinary, modified, unbound, inactive);
        FabricKeyMappings.set(mappings, SHIFT, false, NONE);
        assertFalse(modified.held); assertTrue(ordinary.held); assertTrue(unbound.held); assertTrue(inactive.held);
        var shiftDown = new Input(false, true, false, false, Set.of(SHIFT));
        assertEquals(List.of(modified, ordinary), FabricKeyMappings.matching(mappings, R, true, shiftDown));
        assertTrue(FabricKeyMappings.matching(mappings, InputConstants.UNKNOWN, true, NONE).isEmpty());
    }

    @Test
    void twoModifierKeysCanBeBoundToEachOtherAndMacControlUsesSuper() {
        var binding = mapping(CONTROL, new Context(true, true), KeyModifier.SHIFT);
        var input = new Input(true, true, false, false, Set.of(CONTROL, SHIFT));
        assertEquals(List.of(binding), FabricKeyMappings.matching(List.of(binding), SHIFT, false, input));
        assertTrue(KeyModifier.CONTROL.matches(key(GLFW.GLFW_KEY_LEFT_SUPER), true));
        assertFalse(KeyModifier.CONTROL.matches(CONTROL, true));
        assertTrue(KeyModifier.CONTROL.matches(CONTROL, false));
    }

    @Test
    void rebindingUsesTheCurrentNativeKeyAndTheModifierSurvivesOptionsRoundTripsAndReset() {
        var binding = mapping(R, new Context(true, true), KeyModifier.SHIFT);
        binding.state.defaultModifier(KeyModifier.ALT);
        assertEquals("key.keyboard.r:SHIFT", FabricKeyMappings.serialize(binding, "key.keyboard.r"));
        FabricKeyMappings.deserialize(binding, "key.keyboard.t:ALT");
        assertEquals(key(GLFW.GLFW_KEY_T), FabricKeyMappings.getKey(binding)); assertSame(KeyModifier.ALT, binding.state.modifier());
        assertTrue(FabricKeyMappings.matching(List.of(binding), R, false, NONE).isEmpty());
        FabricKeyMappings.deserialize(binding, "key.keyboard.t");
        assertSame(KeyModifier.NONE, binding.state.modifier());
        assertEquals(List.of(binding), FabricKeyMappings.matching(List.of(binding), key(GLFW.GLFW_KEY_T), false, NONE));
        FabricKeyMappings.deserialize(binding, "key.keyboard.t:UNKNOWN");
        assertSame(KeyModifier.NONE, binding.state.modifier());
        FabricKeyMappings.setToDefault(binding);
        assertEquals(R, FabricKeyMappings.getKey(binding)); assertSame(KeyModifier.ALT, binding.state.modifier());
        FabricKeyMappings.setKeyModifierAndCode(binding, KeyModifier.SHIFT, SHIFT);
        assertSame(KeyModifier.NONE, binding.state.modifier()); // a key cannot modify itself.
    }

    @Test
    void sameRetainsNeo211217UiFallbackAndModifierConflictSemantics() {
        var first = mapping(R, new Context(false, false), KeyModifier.NONE);
        var second = mapping(R, new Context(true, false), KeyModifier.SHIFT);
        assertTrue(FabricKeyMappings.same(first, second)); // preserved version-specific fallback, not a port cleanup.
        var shift = mapping(SHIFT, KeyConflictContext.UNIVERSAL, KeyModifier.NONE);
        assertTrue(FabricKeyMappings.same(second, shift));
        var noMatch = mapping(key(GLFW.GLFW_KEY_T), KeyConflictContext.UNIVERSAL, KeyModifier.NONE);
        assertFalse(FabricKeyMappings.same(first, noMatch));
        assertFalse(FabricKeyMappings.isActiveAndMatches(first, InputConstants.UNKNOWN));
    }

    @Test
    void missingMixinIsAnExplicitFailureInsteadOfGlobalDefaultMetadata() {
        var unadapted = new KeyMapping("test.unadapted." + IDS.incrementAndGet(), GLFW.GLFW_KEY_R, "test");
        var failure = assertThrows(IllegalStateException.class, () -> FabricKeyMappings.getKey(unadapted));
        assertTrue(failure.getMessage().contains("mixin is missing"));
    }

    private static TestMapping mapping(InputConstants.Key key, IKeyConflictContext context, KeyModifier modifier) {
        var mapping = new TestMapping(key); mapping.state.context(context); mapping.state.modifier(modifier); return mapping;
    }
    private static InputConstants.Key key(int value) { return InputConstants.Type.KEYSYM.getOrCreate(value); }
    private static final class TestMapping extends KeyMapping implements KeyMappingExtension {
        final KeyBindingState state = new KeyBindingState();
        InputConstants.Key current; int clicks; boolean held;
        TestMapping(InputConstants.Key key) { super("test.fabric." + IDS.incrementAndGet(), key.getType(), key.getValue(), "test"); current = key; }
        @Override public InputConstants.Key stardewcraft$key() { return current; }
        @Override public KeyBindingState stardewcraft$bindingState() { return state; }
        @Override public void stardewcraft$click() { clicks++; }
        @Override public void setKey(InputConstants.Key key) { super.setKey(key); current = key; }
        @Override public void setDown(boolean down) { super.setDown(down); held = down; }
    }
    private static final class Context implements IKeyConflictContext {
        boolean active; final boolean inGame;
        Context(boolean active, boolean inGame) { this.active = active; this.inGame = inGame; }
        @Override public boolean isActive() { return active; }
        @Override public boolean conflicts(IKeyConflictContext other) { return this == other || inGame && other == KeyConflictContext.IN_GAME; }
    }
    private record Input(boolean control, boolean shift, boolean alt, boolean mac, Set<InputConstants.Key> held) implements KeyInputState {
        @Override public boolean isDown(InputConstants.Key key) { return held.contains(key); }
    }
}
