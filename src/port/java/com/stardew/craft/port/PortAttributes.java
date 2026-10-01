package com.stardew.craft.port;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Entity attributes that 1.20.5+ vanilla has and 1.20.1 lacks, with the 1.21.1 ranges/defaults/sync flags.
 * Registered as {@code stardewcraft:generic.step_height} / {@code stardewcraft:generic.scale} (1.21 ids are in the
 * {@code minecraft} namespace). Unlike 1.21 they are not on every living entity: only on the mod entities whose
 * attribute builders add them (vanilla mobs keep their 1.20.1 {@code setMaxUpStep} values).
 * <ul>
 * <li>{@link #STEP_HEIGHT}: 1.21 {@code LivingEntity#maxUpStep()} returns the attribute value (at least 1 with a
 * player controlling passenger); {@code PortLivingEntityStepHeightMixin} does the same for entities that carry it.
 * Forge's {@code getStepHeight()} then adds {@code ForgeMod.STEP_HEIGHT_ADDITION} (0 unless another mod changes
 * it), so physics and path finding see the 1.21 value.</li>
 * <li>{@link #SCALE}: see {@link #scale(LivingEntity)}; entities using it apply it to their dimensions.</li>
 * </ul>
 */
public final class PortAttributes {
    private static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, PortBootstrap.NAMESPACE);

    /** 1.21 {@code Attributes.STEP_HEIGHT}. */
    public static final RegistryObject<Attribute> STEP_HEIGHT = ATTRIBUTES.register("generic.step_height",
            () -> new RangedAttribute("attribute.name.generic.step_height", 0.6, 0.0, 10.0).setSyncable(true));
    /** 1.21 {@code Attributes.SCALE}. */
    public static final RegistryObject<Attribute> SCALE = ATTRIBUTES.register("generic.scale",
            () -> new RangedAttribute("attribute.name.generic.scale", 1.0, 0.0625, 16.0).setSyncable(true));

    private static boolean registered;

    private PortAttributes() {
    }

    public static synchronized void register(IEventBus modBus) {
        if (registered) return;
        registered = true;
        ATTRIBUTES.register(modBus);
    }

    /** 1.21 {@code LivingEntity#maxUpStep()} for an entity carrying {@link #STEP_HEIGHT}; NaN when it does not. */
    public static float maxUpStep(LivingEntity entity) {
        if (entity.getAttributes() == null) return Float.NaN;
        AttributeInstance step = entity.getAttribute(STEP_HEIGHT.get());
        if (step == null) return Float.NaN;
        float value = (float) step.getValue();
        return entity.getControllingPassenger() instanceof Player ? Math.max(value, 1.0F) : value;
    }

    /**
     * 1.21 {@code LivingEntity#getScale()}: the sanitized {@link #SCALE} value (1 when the entity has no such
     * attribute). Age scaling (babies) stays separate, as in 1.21 ({@code getAgeScale}).
     */
    public static float scale(LivingEntity entity) {
        if (entity.getAttributes() == null) return 1.0F;
        AttributeInstance scale = entity.getAttribute(SCALE.get());
        return scale == null ? 1.0F : sanitizeScale((float) scale.getValue());
    }

    /** 1.21 {@code LivingEntity#sanitizeScale} (identity in the base class; the mod does not override it). */
    private static float sanitizeScale(float scale) {
        return scale;
    }
}
