package com.stardew.craft.platform.mixin.item.transfer.mixin;


import com.stardew.craft.platform.fabric.item.transfer.TransferTransactionGuards;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pinned to fabric-transfer-api-v1 5.4.4+7b3d111d19 (Fabric API 0.116.17). */
@Mixin(targets = "net.fabricmc.fabric.impl.transfer.transaction.TransactionManagerImpl$TransactionImpl", remap = false)
public abstract class TransferTransactionCommitMixin {
    @Inject(method = "commit()V", at = @At("HEAD"), require = 1)
    private void stardewcraft$preflight(CallbackInfo ci) {
        TransferTransactionGuards.beforeCommit((Transaction) (Object) this);
    }
}
