package com.stardew.craft.platform.fabric.cutscene;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Exercises the production walk planner against native block collision shapes, without a client. */
class ActorWalkRouteTest {
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test void authoredWizardInkWalksStayInsideTower() throws Exception {
        var level = allocate(RouteLevel.class);
        var actor = new RouteMob(level);
        int walks = 0;
        try (var input = getClass().getResourceAsStream("/data/stardewcraft/cutscene_events/wizard_magic_ink_return.json")) {
            assertNotNull(input);
            var commands = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("commands");
            for (int i = 0; i < commands.size(); i++) {
                var command = commands.get(i).getAsJsonObject();
                if (!command.has("actor") || !"wizard".equals(command.get("actor").getAsString())) continue;
                String cmd = command.get("cmd").getAsString();
                if (!cmd.equals("spawn_actor") && !cmd.equals("move_actor")) continue;
                var target = new Vec3(command.get("x").getAsDouble(), command.get("y").getAsDouble(), command.get("z").getAsDouble());
                if (cmd.equals("spawn_actor")) {
                    actor.setPos(target);
                    assertTrue(level.noCollision(actor, actor.getBoundingBox()), "Wizard spawn intersects tower wall");
                } else {
                    assertEquals(List.of(actor.position(), target), plan(level, actor, target), "Wizard walk at command " + i);
                    actor.setPos(target);
                    walks++;
                }
            }
        }
        assertEquals(2, walks);
    }

    @Test void walkCannotFinishInsideTowerWall() throws Exception {
        var level = allocate(RouteLevel.class);
        var actor = new RouteMob(level);
        actor.setPos(-185, 34, 55);
        assertEquals(List.of(), plan(level, actor, new Vec3(-186, 34, 55)));
    }

    @SuppressWarnings("unchecked")
    private static List<Vec3> plan(ClientLevel level, Mob actor, Vec3 end) throws Exception {
        Method planner = Class.forName("com.stardew.craft.cutscene.command.NavigateActorCommand")
                .getDeclaredMethod("buildPath", ClientLevel.class, Mob.class, Vec3.class, Vec3.class);
        planner.setAccessible(true);
        return (List<Vec3>) planner.invoke(null, level, actor, actor.position(), end);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> allocator = Class.forName("sun.misc.Unsafe");
        Field field = allocator.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(allocator.getMethod("allocateInstance", Class.class).invoke(field.get(null), type));
    }

    private static final class RouteMob extends Mob {
        private RouteMob(ClientLevel level) { super(EntityType.ZOMBIE, level); }
        @Override public float maxUpStep() { return 1; }
    }

    private static final class RouteLevel extends ClientLevel {
        private RouteLevel() { super(null, null, null, null, 0, 0, null, null, false, 0); }
        @Override public boolean hasChunkAt(BlockPos pos) { return true; }
        @Override public BlockState getBlockState(BlockPos pos) {
            // The embedded tower has a full floor at Y=33 and a west wall at X=-187.
            return pos.getY() < 34 || pos.getX() <= -187
                    ? Blocks.OAK_PLANKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public BlockGetter getChunkForCollisions(int x, int z) { return this; }
        @Override public WorldBorder getWorldBorder() { return new WorldBorder(); }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public Supplier<ProfilerFiller> getProfilerSupplier() { return () -> InactiveProfiler.INSTANCE; }
        @Override public List<VoxelShape> getEntityCollisions(Entity entity, AABB box) { return List.of(); }
    }
}
