package com.stardew.craft.platform.mixin.client.model.mixin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.platform.fabric.client.model.ModelTransformationDeserializer;
import net.minecraft.client.renderer.block.model.BlockModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** PL reads root/child transforms through this same native Gson context. */
@Mixin(BlockModel.class)
public abstract class BlockModelTransformationDeserializerMixin {
    @WrapOperation(method = "<clinit>", at = @At(value = "INVOKE",
            target = "Lcom/google/gson/GsonBuilder;create()Lcom/google/gson/Gson;"), require = 1)
    private static Gson stardewcraft$modelTransformAdapter(GsonBuilder builder, Operation<Gson> original) {
        return original.call(ModelTransformationDeserializer.register(builder));
    }
}
