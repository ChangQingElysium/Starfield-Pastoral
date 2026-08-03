package com.stardew.craft.farm;

import com.stardew.craft.core.FarmAreaResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.UUID;

/** Resolves persistent farm resources by location while keeping actor authorization separate. */
public final class FarmResourceOwnership {
    private FarmResourceOwnership() {}

    @Nullable
    public static UUID resolveManageableOwner(
            ServerLevel level,
            BlockPos pos,
            ServerPlayer actor
    ) {
        UUID farmOwner = FarmAreaResolver.getOwnerAt(pos);
        if (farmOwner == null
                || !FarmPermissionManager.get().canModify(
                        farmOwner, actor.getUUID())) {
            return null;
        }
        return farmOwner;
    }
}
