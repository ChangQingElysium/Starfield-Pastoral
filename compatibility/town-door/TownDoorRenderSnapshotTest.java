package com.stardew.craft.client.interior;

import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TownDoorRenderSnapshotTest {
    @Test void restoresMutableFogAndProjectionAsWellAsTheOriginalReferences() throws Exception {
        State state = new State();
        Vector3d fog = state.fog;
        Matrix4f projection = state.projection;
        var snapshot = TownDoorRenderSnapshot.capture(state,
                TownDoorRenderSnapshot.fields(State.class, "fog", "projection", "phase", "nullable"));
        state.fog.set(8, 9, 10);
        state.projection.scale(7);
        state.phase = 8;
        state.nullable = new Object();
        snapshot.restore();
        assertSame(fog, state.fog);
        assertSame(projection, state.projection);
        assertEquals(new Vector3d(1, 2, 3), state.fog);
        assertEquals(new Matrix4f(), state.projection);
        assertEquals(3, state.phase);
        assertNull(state.nullable);
    }

    @Test void nestedScopesAndExceptionUnwindingRestoreTheirOwnCaller() throws Exception {
        State state = new State();
        var fields = TownDoorRenderSnapshot.fields(State.class, "fog", "phase");
        var outer = TownDoorRenderSnapshot.capture(state, fields);
        try {
            state.phase = 5;
            state.fog.set(5);
            var inner = TownDoorRenderSnapshot.capture(state, fields);
            try {
                state.phase = 9;
                state.fog = new Vector3d(9);
                throw new IllegalStateException("render failure");
            } catch (IllegalStateException expected) {
                // The renderer's finally path must run even if rendering fails.
            } finally {
                inner.restore();
            }
            assertEquals(5, state.phase);
            assertEquals(new Vector3d(5), state.fog);
        } finally {
            outer.restore();
        }
        assertEquals(3, state.phase);
        assertEquals(new Vector3d(1, 2, 3), state.fog);
    }

    private static final class State {
        private Vector3d fog = new Vector3d(1, 2, 3);
        private Matrix4f projection = new Matrix4f();
        private int phase = 3;
        private Object nullable;
    }
}
