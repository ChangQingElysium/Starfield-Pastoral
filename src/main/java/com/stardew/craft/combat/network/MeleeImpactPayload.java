package com.stardew.craft.combat.network;

import com.stardew.craft.StardewCraft;
import io.netty.buffer.ByteBuf;
import com.stardew.craft.port.net.minecraft.network.codec.ByteBufCodecs;
import com.stardew.craft.port.net.minecraft.network.codec.StreamCodec;
import com.stardew.craft.port.net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.stardew.craft.port.net.neoforged.neoforge.network.handling.IPayloadContext;

/** Confirmed contact for the dagger and club samples; ground contacts are distinct from damage. */
public record MeleeImpactPayload(int casterId, int targetId, long gameTick, Kind kind, String weaponId,
                                 double x, double y, double z, boolean critical) implements CustomPacketPayload {
    public enum Kind { DAGGER, THRUST, BONUS_THRUST, CLUB, SLAM, GROUND, SWORD, DRAGON_PIERCE, DRAGON_JUDGEMENT, YETI_MARK, YETI_SPINE, SHIV_HIT, SHIV_STAB, SHIV_BREATH_HIT, GALAXY_RIFT, GALAXY_JUDGEMENT, GALAXY_STAB, GALAXY_LEAP, INFINITY_EVOLVE, INFINITY_COLLAPSE, INFINITY_STAB, INFINITY_BACK, INFINITY_RIFT, TIDE_ANCHOR, TIDE_BONUS, TIDE_STAB, TIDE_REEL, FOREST_RELEASE, ELF_STAB, ELF_LEAF, HOLY_SMITE, HOLY_PULSE, TEMPLAR_STRIKE, MEOW_PROJECTILE, OBSIDIAN_RESONANCE, OBSIDIAN_CRACK, OSSIFIED_BONUS, OSSIFIED_PULSE, DARK_DEBT, DARK_BURST, FORGE_QUENCH, FORGE_BLAST, FORGE_BILLET, FORGE_RING, CRYSTAL_LAYER, CRYSTAL_BURST, VENOM_RIPPLE, VENOM_NEST, VENOM_DOT, VENOM_BURST, SHADOW_EXECUTE, SHADOW_FINISH, INSECT_EYE, INSECT_DASH, NEEDLE_STRIKE, NEEDLE_FINAL, NEEDLE_FRENZY, BURGLAR_STRIKE, DWARF_GUARD, DWARF_SHOCK, DWARF_THRUST, BONE_FRACTURE, CLAYMORE_OUT, CLAYMORE_RETURN, IRON_THRUST, WIND_THRUST, PIRATE_PLUNDER, SILVER_HIT, LIGHT_COUNTER, SPINE_STRIKE, SPINE_WEAK, RUST_STRIKE, WOOD_BLESS, CRESCENT_SLASH, FALCHION_DOT, FALCHION_BURST }

    public static final Type<MeleeImpactPayload> TYPE = new Type<>(
            new ResourceLocation(StardewCraft.MODID, "melee_impact"));
    public static final StreamCodec<ByteBuf, MeleeImpactPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MeleeImpactPayload decode(ByteBuf buffer) {
            return new MeleeImpactPayload(ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_LONG.decode(buffer),
                    Kind.values()[buffer.readUnsignedByte()], ByteBufCodecs.STRING_UTF8.decode(buffer), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readBoolean());
        }

        @Override
        public void encode(ByteBuf buffer, MeleeImpactPayload value) {
            ByteBufCodecs.VAR_INT.encode(buffer, value.casterId());
            ByteBufCodecs.VAR_INT.encode(buffer, value.targetId());
            ByteBufCodecs.VAR_LONG.encode(buffer, value.gameTick());
            buffer.writeByte(value.kind().ordinal());
            ByteBufCodecs.STRING_UTF8.encode(buffer, value.weaponId());
            buffer.writeDouble(value.x()).writeDouble(value.y()).writeDouble(value.z());
            buffer.writeBoolean(value.critical());
        }
    };

    @Override
    public Type<MeleeImpactPayload> type() { return TYPE; }

    public static void handle(MeleeImpactPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> com.stardew.craft.client.weapon.MeleeWeaponVisuals.impact(payload));
    }
}
