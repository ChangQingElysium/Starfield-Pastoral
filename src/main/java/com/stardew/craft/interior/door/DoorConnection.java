package com.stardew.craft.interior.door;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Two fixed upright apertures in the same level. No entity or ticking block entity. */
public record DoorConnection(Vec3 outside, Vec3 inside, double width,
                             int outsideDirectionZ, int insideDirectionZ) {
    public DoorConnection {
        if (outside == null || inside == null || !finite(outside) || !finite(inside)
                || !Double.isFinite(width) || width <= 0) {
            throw new IllegalArgumentException("Door apertures must be finite and have positive width");
        }
        if ((outsideDirectionZ != -1 && outsideDirectionZ != 1)
                || (insideDirectionZ != -1 && insideDirectionZ != 1)) {
            throw new IllegalArgumentException("Door directions must be -1 or 1");
        }
    }

    public Vec3 origin(boolean entering) { return entering ? outside : inside; }
    public Vec3 destination(boolean entering) { return entering ? inside : outside; }
    public Vec3 translation(boolean entering) { return destination(entering).subtract(origin(entering)); }

    /** Preserve jumping/falling height, while lifting a body approaching below the threshold floor. */
    public Vec3 movementDestinationBase(Vec3 source, boolean entering) {
        Vec3 from = origin(entering);
        Vec3 to = destination(entering);
        return new Vec3(source.x + to.x - from.x, Math.max(to.y - 1, source.y + to.y - from.y),
                source.z + to.z - from.z);
    }

    /** Finds the first clear threshold height, accounting for carpet and other thin floor shapes. */
    @Nullable
    public Vec3 movementDestination(Level level, Entity entity, Vec3 source, boolean entering) {
        Vec3 base = movementDestinationBase(source, entering);
        AABB baseBox = entity.getBoundingBox().move(base.subtract(entity.position()));
        // noCollision may inspect neighboring chunks. Never predict into unloaded data or load
        // destination terrain synchronously on the server while processing a movement packet.
        if (!level.hasChunksAt(net.minecraft.core.BlockPos.containing(baseBox.minX, baseBox.minY, baseBox.minZ),
                net.minecraft.core.BlockPos.containing(baseBox.maxX, baseBox.maxY + .75, baseBox.maxZ))) return null;
        for (int step = 0; step <= 12; step++) {
            double offset = step / 16.0;
            if (level.noCollision(entity, baseBox.move(0, offset, 0))) return base.add(0, offset, 0);
        }
        return null;
    }

    public Vec3 direction(boolean entering) {
        return new Vec3(0, 0, entering ? outsideDirectionZ : insideDirectionZ);
    }

    public double distance(Vec3 point, boolean entering) {
        return -(point.z - origin(entering).z) * (entering ? outsideDirectionZ : insideDirectionZ);
    }

    public boolean crosses(Vec3 beforeFeet, Vec3 afterFeet, double bodyWidth, double bodyHeight, boolean entering) {
        if (!finite(beforeFeet) || !finite(afterFeet)) return false;
        double before = distance(beforeFeet, entering);
        double after = distance(afterFeet, entering);
        if (before <= 0 || after > 0 || before - after > 1.5) return false;
        Vec3 atPlane = beforeFeet.lerp(afterFeet, before / (before - after));
        Vec3 center = origin(entering);
        return Math.abs(atPlane.x - center.x) + bodyWidth * .5 <= width * .5 + 1e-6
                && atPlane.y >= center.y - 1.625
                && atPlane.y + bodyHeight <= center.y + 1.625;
    }

    public static boolean finite(Vec3 pos) {
        return Double.isFinite(pos.x) && Double.isFinite(pos.y) && Double.isFinite(pos.z);
    }
}
