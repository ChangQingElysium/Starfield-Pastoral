package com.stardew.craft.combat;

import com.stardew.craft.combat.skill.WeaponDamageSnapshot;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class OrdinaryWeaponAttackFrameStoreTest {
    @Test
    void claimRequiresExactPlayerTargetSourceIdentityAndTick() {
        OrdinaryWeaponAttackFrameStore.FrameStack stack =
                new OrdinaryWeaponAttackFrameStore.FrameStack();
        UUID playerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Object source = new Object();
        WeaponDamageSnapshot snapshot = snapshot("ordinary");

        stack.bind(playerId, targetId, source, snapshot, 42L);

        assertNull(stack.claim(
                UUID.randomUUID(),
                targetId,
                source,
                42L
        ));
        assertNull(stack.claim(
                playerId,
                UUID.randomUUID(),
                source,
                42L
        ));
        assertNull(stack.claim(playerId, targetId, new Object(), 42L));
        assertNull(stack.claim(playerId, targetId, source, 41L));
        assertEquals(1, stack.size(playerId));

        OrdinaryWeaponAttackFrameStore.Frame frame = stack.claim(
                playerId,
                targetId,
                source,
                42L
        );
        assertSame(snapshot, frame.weaponSnapshot());
        assertNull(stack.claim(playerId, targetId, source, 42L));
        assertEquals(0, stack.size(playerId));
    }

    @Test
    void nestedFramesAreClaimedInLifoOrder() {
        OrdinaryWeaponAttackFrameStore.FrameStack stack =
                new OrdinaryWeaponAttackFrameStore.FrameStack();
        UUID playerId = UUID.randomUUID();
        UUID outerTarget = UUID.randomUUID();
        UUID innerTarget = UUID.randomUUID();
        Object outerSource = new Object();
        Object innerSource = new Object();
        WeaponDamageSnapshot outer = snapshot("outer");
        WeaponDamageSnapshot inner = snapshot("inner");

        stack.bind(playerId, outerTarget, outerSource, outer, 50L);
        stack.bind(playerId, innerTarget, innerSource, inner, 50L);

        assertNull(stack.claim(playerId, outerTarget, outerSource, 50L));
        assertSame(
                inner,
                stack.claim(
                        playerId,
                        innerTarget,
                        innerSource,
                        50L
                ).weaponSnapshot()
        );
        assertSame(
                outer,
                stack.claim(
                        playerId,
                        outerTarget,
                        outerSource,
                        50L
                ).weaponSnapshot()
        );
        assertEquals(0, stack.size(playerId));
    }

    @Test
    void discardAndClearCannotRemoveAnUnrelatedFrame() {
        OrdinaryWeaponAttackFrameStore.FrameStack stack =
                new OrdinaryWeaponAttackFrameStore.FrameStack();
        UUID playerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Object source = new Object();

        stack.bind(playerId, targetId, source, snapshot("cleanup"), 60L);
        stack.discard(playerId, UUID.randomUUID(), source);
        stack.discard(playerId, targetId, new Object());
        assertEquals(1, stack.size(playerId));

        stack.clear(playerId);
        assertEquals(0, stack.size(playerId));
    }

    private static WeaponDamageSnapshot snapshot(String path) {
        return WeaponDamageSnapshot.capture(
                ResourceLocation.fromNamespaceAndPath("test", path),
                ItemStack.EMPTY
        );
    }
}
