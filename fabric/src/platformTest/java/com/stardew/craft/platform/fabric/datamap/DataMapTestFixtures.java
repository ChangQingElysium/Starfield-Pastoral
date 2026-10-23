package com.stardew.craft.platform.fabric.datamap;

import com.mojang.serialization.Lifecycle;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;

final class DataMapTestFixtures {
    static final ResourceKey<Registry<String>> REGISTRY = ResourceKey.createRegistryKey(ResourceLocation.parse("fixture:objects"));
    static final ResourceKey<String> A = ResourceKey.create(REGISTRY, ResourceLocation.parse("fixture:a"));
    static final ResourceKey<String> B = ResourceKey.create(REGISTRY, ResourceLocation.parse("fixture:b"));
    static final TagKey<String> GROUP = TagKey.create(REGISTRY, ResourceLocation.parse("fixture:group"));

    static MappedRegistry<String> registry() {
        var registry = new MappedRegistry<String>(REGISTRY, Lifecycle.stable());
        registry.register(A, "a", RegistrationInfo.BUILT_IN);
        registry.register(B, "b", RegistrationInfo.BUILT_IN);
        registry.freeze();
        registry.bindTags(Map.of(GROUP, List.of(registry.getHolder(A).orElseThrow())));
        return registry;
    }

    static RegistryAccess access(Registry<?> registry) { return new RegistryAccess.ImmutableRegistryAccess(List.of(registry)); }

    static ResourceManager resources(ResourceLocation file, String... jsonFiles) {
        PackResources pack = (PackResources) Proxy.newProxyInstance(PackResources.class.getClassLoader(),
                new Class<?>[]{PackResources.class}, (proxy, method, args) -> method.getName().equals("packId") ? "fixture" : null);
        var resources = java.util.Arrays.stream(jsonFiles).map(json -> new Resource(pack,
                () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)))).toList();
        return (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(),
                new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getResourceStack")) return file.equals(args[0]) ? resources : List.of();
                    throw new AssertionError(method.getName());
                });
    }

    private DataMapTestFixtures() {}
}
