package com.stardew.craft.port.net.minecraft.client.resources;

import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * 1.20.2+ {@code net.minecraft.client.resources.PlayerSkin}. 1.20.1 exposes the same data as separate getters on
 * {@link AbstractClientPlayer} / {@link PlayerInfo}; {@link #of} snapshots them (the rewrite turns
 * {@code x.getSkin()} into {@code PlayerSkin.of(x)}). {@code textureUrl} and {@code secure} are not tracked by 1.20.1.
 */
public record PlayerSkin(ResourceLocation texture, @Nullable String textureUrl, @Nullable ResourceLocation capeTexture,
                         @Nullable ResourceLocation elytraTexture, Model model, boolean secure) {

    public static PlayerSkin of(AbstractClientPlayer player) {
        return new PlayerSkin(player.getSkinTextureLocation(), null, player.getCloakTextureLocation(),
                player.getElytraTextureLocation(), Model.byName(player.getModelName()), false);
    }

    public static PlayerSkin of(PlayerInfo info) {
        return new PlayerSkin(info.getSkinLocation(), null, info.getCapeLocation(), info.getElytraLocation(),
                Model.byName(info.getModelName()), false);
    }

    public enum Model {
        SLIM("slim"),
        WIDE("default");

        private final String id;

        Model(String id) {
            this.id = id;
        }

        /** The 1.20.1 model name ({@code "slim"} / {@code "default"}), also the key of skin renderer maps. */
        public String id() {
            return this.id;
        }

        public static Model byName(@Nullable String name) {
            return "slim".equals(name) ? SLIM : WIDE;
        }
    }
}
