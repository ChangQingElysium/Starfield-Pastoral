package com.stardew.craft.port;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PORT(1.20.1): NeoForge 1.21.1 {@code FakePlayer} overrides {@code openMenu} (returns empty, no menu is created) and
 * {@code startRiding} (returns false); Forge 1.20.1's {@code FakePlayer} has neither, so a fake player (another mod's
 * deployer/harvester, or a GameTest's {@code FakePlayerFactory} player) would open StardewCraft menus and sit on
 * StardewCraft seats. {@code PortFakePlayerModInteractionMixin} restores the 1.21.1 result only where StardewCraft is
 * involved: a menu requested by StardewCraft code (or provided by a StardewCraft object), or a StardewCraft vehicle /
 * riding requested by StardewCraft code. Vanilla and other mods' menus and vehicles keep Forge 1.20.1 behaviour.
 */
public final class PortFakePlayers {
    private static final String MOD_PACKAGE = "com.stardew.craft.";
    private static final StackWalker WALKER = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE);

    private PortFakePlayers() {
    }

    /** 1.21.1 {@code FakePlayer#openMenu}: refuse when the menu comes from StardewCraft. */
    public static boolean refusesMenu(ServerPlayer player, Object provider) {
        if (!(player instanceof FakePlayer)) return false;
        return provider != null && isMod(provider.getClass()) || calledFromMod();
    }

    /** 1.21.1 {@code FakePlayer#startRiding}: refuse StardewCraft vehicles and riding requested by StardewCraft. */
    public static boolean refusesRiding(ServerPlayer player, Entity vehicle) {
        if (!(player instanceof FakePlayer)) return false;
        return vehicle != null && "stardewcraft".equals(BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).getNamespace())
                || calledFromMod();
    }

    private static boolean isMod(Class<?> type) {
        return type.getName().startsWith(MOD_PACKAGE);
    }

    /**
     * The first caller outside this helper and the player class hierarchy ({@code Entity#startRiding(Entity)} and the
     * other super/subclass frames; mixin handlers run as {@code ServerPlayer} frames).
     */
    private static boolean calledFromMod() {
        Optional<Class<?>> caller = WALKER.walk(frames -> frames
                .<Class<?>>map(StackWalker.StackFrame::getDeclaringClass)
                .filter(type -> type != PortFakePlayers.class && !type.isAssignableFrom(ServerPlayer.class)
                        && !ServerPlayer.class.isAssignableFrom(type))
                .findFirst());
        return caller.isPresent() && isMod(caller.get());
    }
}
