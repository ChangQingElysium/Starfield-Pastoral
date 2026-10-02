package com.stardew.craft.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.stardew.craft.port.PortBlockEntityRenderBounds;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.SortedSet;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** PORT(1.20.1): Embeddium owns separate local and global block-entity frustum checks. */
@Pseudo
@Mixin(targets = "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class SodiumBlockEntityBoundsMixin {
    @WrapOperation(method = {
            "renderBlockEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/RenderBuffers;Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;FLnet/minecraft/client/renderer/MultiBufferSource$BufferSource;DDDLnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;)V",
            "renderGlobalBlockEntities"
    }, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/entity/BlockEntity;getRenderBoundingBox()Lnet/minecraft/world/phys/AABB;"),
            require = 2, expect = 2, allow = 2)
    private AABB stardewcraft$rendererBounds(BlockEntity blockEntity, Operation<AABB> original,
            PoseStack pose, RenderBuffers buffers, Long2ObjectMap<SortedSet<BlockDestructionProgress>> destruction,
            float partialTick, MultiBufferSource.BufferSource consumer, double x, double y, double z,
            BlockEntityRenderDispatcher dispatcher) {
        return PortBlockEntityRenderBounds.bounds(dispatcher, blockEntity);
    }
}
