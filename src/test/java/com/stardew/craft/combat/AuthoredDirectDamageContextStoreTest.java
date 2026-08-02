package com.stardew.craft.combat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoredDirectDamageContextStoreTest {
    @Test
    void nestedFramesConsumeByLifoSourceIdentityAndTarget() {
        AuthoredDirectDamageContextStore.FrameStack stack =
                new AuthoredDirectDamageContextStore.FrameStack();
        UUID outerOwner = UUID.randomUUID();
        UUID outerTarget = UUID.randomUUID();
        UUID innerOwner = UUID.randomUUID();
        UUID innerTarget = UUID.randomUUID();
        Object outerSource = new Object();
        Object innerSource = new Object();

        stack.bind(outerOwner, outerTarget, outerSource, "outer", 12L);
        assertNull(stack.consume(
                UUID.randomUUID(),
                outerSource,
                10L
        ));
        assertNull(stack.consume(
                outerTarget,
                new Object(),
                10L
        ));
        assertEquals(1, stack.size());

        stack.bind(innerOwner, innerTarget, innerSource, "inner", 12L);
        assertEquals(
                "inner",
                stack.consume(innerTarget, innerSource, 10L).authoredId()
        );
        assertEquals(
                "outer",
                stack.consume(outerTarget, outerSource, 10L).authoredId()
        );
        assertEquals(0, stack.size());
    }

    @Test
    void authoredIdentitySupportsIncomingRecursionExclusion() {
        AuthoredDirectDamageContextStore.FrameStack stack =
                new AuthoredDirectDamageContextStore.FrameStack();
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        Object source = new Object();

        stack.bind(
                owner,
                target,
                source,
                "templar_judgement_share",
                22L
        );

        assertTrue(stack.isBound(
                target,
                source,
                "templar_judgement_share",
                20L
        ));
        assertFalse(stack.isBound(target, source, "other", 20L));
        assertFalse(stack.isBound(
                UUID.randomUUID(),
                source,
                "templar_judgement_share",
                20L
        ));
    }

    @Test
    void finallyDiscardRemovesOnlyTheExactUnconsumedTopFrame() {
        AuthoredDirectDamageContextStore.FrameStack stack =
                new AuthoredDirectDamageContextStore.FrameStack();
        UUID owner = UUID.randomUUID();
        UUID outerTarget = UUID.randomUUID();
        UUID innerTarget = UUID.randomUUID();
        Object outerSource = new Object();
        Object innerSource = new Object();

        stack.bind(owner, outerTarget, outerSource, "outer", 12L);
        stack.bind(owner, innerTarget, innerSource, "inner", 12L);
        stack.discard(outerTarget, outerSource);
        assertEquals(2, stack.size());

        stack.discard(innerTarget, innerSource);
        stack.discard(outerTarget, outerSource);
        assertEquals(0, stack.size());
    }

    @Test
    void ownerCleanupAndExpirationCannotLeakFrames() {
        AuthoredDirectDamageContextStore.FrameStack stack =
                new AuthoredDirectDamageContextStore.FrameStack();
        UUID removedOwner = UUID.randomUUID();
        UUID retainedOwner = UUID.randomUUID();
        UUID retainedTarget = UUID.randomUUID();
        Object retainedSource = new Object();

        stack.bind(
                retainedOwner,
                retainedTarget,
                retainedSource,
                "retained",
                20L
        );
        stack.bind(
                removedOwner,
                UUID.randomUUID(),
                new Object(),
                "removed",
                20L
        );
        stack.clear(removedOwner);
        assertEquals(1, stack.size());
        assertEquals(
                "retained",
                stack.consume(
                        retainedTarget,
                        retainedSource,
                        20L
                ).authoredId()
        );

        UUID expiredTarget = UUID.randomUUID();
        Object expiredSource = new Object();
        stack.bind(
                retainedOwner,
                expiredTarget,
                expiredSource,
                "expired",
                20L
        );
        assertNull(stack.consume(expiredTarget, expiredSource, 21L));
        assertEquals(0, stack.size());
    }
}
