package com.stardew.craft.platform.fabric.extension;

import com.mojang.serialization.Codec;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** A canonical, identity-comparable name for a tool action. */
public final class ItemAbility {
    private static final Map<String, ItemAbility> ACTIONS = new ConcurrentHashMap<>();
    private static final Collection<ItemAbility> VIEW = Collections.unmodifiableCollection(ACTIONS.values());
    public static final Codec<ItemAbility> CODEC = Codec.STRING.xmap(ItemAbility::get, ItemAbility::name);

    private final String name;

    private ItemAbility(String name) {
        this.name = name;
    }

    public static ItemAbility get(String name) {
        return ACTIONS.computeIfAbsent(name, ItemAbility::new);
    }

    /** A read-only live view, including actions declared by other Fabric integrations. */
    public static Collection<ItemAbility> getActions() {
        return VIEW;
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "ItemAbility[" + name + "]";
    }
}
