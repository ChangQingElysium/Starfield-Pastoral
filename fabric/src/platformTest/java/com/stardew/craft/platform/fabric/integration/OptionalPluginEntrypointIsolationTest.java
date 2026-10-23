package com.stardew.craft.platform.fabric.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.ModContainerImpl;
import net.fabricmc.loader.impl.entrypoint.EntrypointStorage;
import org.junit.jupiter.api.Test;

/** Uses the pinned Loader's actual new-style entry storage, without initializing a game or mod. */
class OptionalPluginEntrypointIsolationTest {
    @Test
    void baseEntrypointsDoNotResolveOptionalApisWhenTheirModsAreMissing() throws Exception {
        List<String> resolved = new ArrayList<>();
        EntrypointStorage storage = fixture(resolved);

        storage.getEntrypointContainers("main", Object.class).getFirst().getEntrypoint();
        storage.getEntrypointContainers("client", Object.class).getFirst().getEntrypoint();

        assertEquals(List.of("base.Main", "base.Client"), resolved);
        assertTrue(storage.getEntrypointContainers("server", Object.class).isEmpty());
    }

    @Test
    void customPluginContainersAreLazyUntilTheirOwningModRequestsTheInstance() throws Exception {
        List<String> resolved = new ArrayList<>();
        EntrypointStorage storage = fixture(resolved);

        var jade = storage.getEntrypointContainers("jade", Object.class);
        var jei = storage.getEntrypointContainers("jei_mod_plugin", Object.class);
        assertEquals(1, jade.size());
        assertEquals(1, jei.size());
        assertTrue(resolved.isEmpty());

        // This fixture deliberately represents a plugin whose optional API is absent.
        // It proves Loader reaches it only through that plugin key's explicit instance request.
        assertThrows(NoClassDefFoundError.class, () -> jade.getFirst().getEntrypoint());
        assertEquals(List.of("missing.JadePlugin"), resolved);
    }

    @SuppressWarnings("unchecked")
    private static EntrypointStorage fixture(List<String> resolved) throws Exception {
        EntrypointStorage storage = new EntrypointStorage();
        LanguageAdapter adapter = new LanguageAdapter() {
            @Override
            public <T> T create(ModContainer mod, String value, Class<T> type) {
                resolved.add(value);
                if (value.startsWith("missing.")) throw new NoClassDefFoundError("optional/api/Absent");
                return type.cast(new Object());
            }
        };
        // Exercise Loader 0.19.5's real private new-style entries without constructing a
        // ModContainerImpl, which would introduce unrelated launch/discovery requirements.
        Class<?> entryClass = Class.forName(EntrypointStorage.class.getName() + "$NewEntry");
        Constructor<?> constructor = entryClass.getDeclaredConstructor(
                ModContainerImpl.class, LanguageAdapter.class, String.class);
        constructor.setAccessible(true);
        Field field = EntrypointStorage.class.getDeclaredField("entryMap");
        field.setAccessible(true);
        Map<String, List<Object>> entries = (Map<String, List<Object>>) field.get(storage);
        entries.put("main", List.of(constructor.newInstance(null, adapter, "base.Main")));
        entries.put("client", List.of(constructor.newInstance(null, adapter, "base.Client")));
        entries.put("jade", List.of(constructor.newInstance(null, adapter, "missing.JadePlugin")));
        entries.put("jei_mod_plugin", List.of(constructor.newInstance(null, adapter, "missing.JeiPlugin")));
        return storage;
    }
}
