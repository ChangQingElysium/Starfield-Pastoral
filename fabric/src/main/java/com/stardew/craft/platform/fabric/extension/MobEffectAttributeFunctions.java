package com.stardew.craft.platform.fabric.extension;

import java.util.Objects;
import java.util.function.IntToDoubleFunction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/** Keeps amplifier functions, including constant effects, on the native template path. */
public final class MobEffectAttributeFunctions {
    private MobEffectAttributeFunctions() {}

    public interface Template {
        void stardewcraft$amountFunction(IntToDoubleFunction function);
    }

    public static MobEffect add(MobEffect effect, Holder<Attribute> attribute, ResourceLocation id,
            AttributeModifier.Operation operation, IntToDoubleFunction function) {
        Objects.requireNonNull(function, "amount function");
        effect.addAttributeModifier(attribute, id, 0, operation);
        var template = ((com.stardew.craft.platform.mixin.extension.mixin.MobEffectAttributesAccessor) effect)
                .stardewcraft$attributeTemplates().get(attribute);
        if (!(template instanceof Template dynamic)) throw new IllegalStateException("Mob effect template mixin is required");
        dynamic.stardewcraft$amountFunction(function);
        return effect;
    }

    public static AttributeModifier create(ResourceLocation id, AttributeModifier.Operation operation,
            IntToDoubleFunction function, int amplifier) {
        return new AttributeModifier(id, function.applyAsDouble(amplifier), operation);
    }
}
