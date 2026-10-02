package com.stardew.craft.mixin;
import com.stardew.craft.client.light.ComplementaryLampBridge;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="net.irisshaders.iris.shaderpack.ShaderPack",remap=false)
public abstract class IrisMineLampActivePathMixin {
    @Inject(method="readProperties",at=@At("HEAD"))
    private static void stardewcraft$prepare(java.nio.file.Path directory,String file,CallbackInfoReturnable<String> cir) {
        ComplementaryLampBridge.beginPack(directory,file);
    }
    // PORT(1.20.1): Oculus 1.8.0's active preprocessed source is lambda$new$9, not $8.
    @Inject(method="lambda$new$9",at=@At("RETURN"),require=1)
    private static void stardewcraft$activePath(List<?> transforms,@Coerce Object processor,Iterable<?> environment,
            @Coerce Object path,CallbackInfoReturnable<String> cir) {
        ComplementaryLampBridge.observeCompiledSource(path,cir.getReturnValue());
    }
}
