package com.stardew.craft.client.interior;

import com.stardew.craft.interior.InteriorRegionRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.phys.Vec3;

/** Render-thread state for the one nonrecursive town doorway view. */
public final class TownDoorRenderContext {
    private static Vec3 cameraPosition;
    private static PortalCone portalCone;
    private static ClipPlane clipPlane;
    private static SectionPos discoveryOrigin;
    private static TargetBounds targetBounds;
    private static boolean rendererManagesTerrain;
    private static boolean isolatedTarget;
    private static boolean shadowPass;
    private static Runnable foregroundTranslucents;

    static void foregroundTranslucents(Runnable render) { foregroundTranslucents = render; }

    /** Consumed once, after the child terrain's translucent layer, before Iris resolves it. */
    public static void renderForegroundTranslucents() {
        Runnable render = foregroundTranslucents;
        foregroundTranslucents = null;
        if (render != null) render.run();
    }

    private TownDoorRenderContext() {}

    static void begin(Vec3 camera, Vec3 apertureCenter, Vec3 destinationNormal, double apertureWidth) {
        if (cameraPosition != null) throw new IllegalStateException("Recursive town door render");
        PortalCone nextCone = PortalCone.create(camera, apertureCenter, apertureWidth);
        ClipPlane nextPlane = ClipPlane.create(camera, apertureCenter, destinationNormal);
        SectionPos nextOrigin = SectionPos.of(BlockPos.containing(apertureCenter.add(destinationNormal.scale(.05))));
        TargetBounds nextBounds = InteriorRegionRegistry.fixedInteriorAt(BlockPos.containing(
                        apertureCenter.x, apertureCenter.y, apertureCenter.z))
                .map(TargetBounds::from)
                .orElse(null);
        cameraPosition = camera;
        portalCone = nextCone;
        clipPlane = nextPlane;
        discoveryOrigin = nextOrigin;
        targetBounds = nextBounds;
        rendererManagesTerrain = false;
    }

    static void rendererManagesTerrain(boolean managesTerrain) {
        rendererManagesTerrain = managesTerrain;
    }

    static void isolatedTarget(boolean isolated) { isolatedTarget = isolated; }
    public static boolean usesIsolatedTarget() { return isolatedTarget; }
    public static boolean isShadowPass() { return shadowPass; }
    public static void shadowPass(boolean active) { shadowPass = active; }

    static void end() {
        cameraPosition = null;
        portalCone = null;
        clipPlane = null;
        discoveryOrigin = null;
        targetBounds = null;
        rendererManagesTerrain = false;
        isolatedTarget = false;
        shadowPass = false;
        foregroundTranslucents = null;
    }

    public static boolean isRendering() {
        return cameraPosition != null;
    }

    public static Vec3 cameraPosition() {
        if (cameraPosition == null) throw new IllegalStateException("No town door render is active");
        return cameraPosition;
    }

    /** Sodium must start its section walk at the destination aperture, not behind its clip plane. */
    public static SectionPos discoveryOrigin() {
        return shadowPass ? null : discoveryOrigin;
    }

    /** Vanilla terrain discovery is replaced only when no supported renderer backend owns it. */
    public static boolean shouldOverrideVanillaTerrainSetup() {
        return cameraPosition != null && !rendererManagesTerrain && !shadowPass;
    }

    /** Vanilla supplies world coordinates to {@code Frustum.cubeInFrustum}. */
    public static boolean isWorldBoxOutsidePortalView(double minX, double minY, double minZ,
                                                       double maxX, double maxY, double maxZ) {
        if (cameraPosition == null || shadowPass) return false;
        if (targetBounds != null && !targetBounds.intersects(minX, minY, minZ, maxX, maxY, maxZ)) return true;
        if (clipPlane != null && clipPlane.fullyClips(
                minX - cameraPosition.x, minY - cameraPosition.y, minZ - cameraPosition.z,
                maxX - cameraPosition.x, maxY - cameraPosition.y, maxZ - cameraPosition.z)) return true;
        return portalCone != null && portalCone.isFullyOutside(
                minX - cameraPosition.x, minY - cameraPosition.y, minZ - cameraPosition.z,
                maxX - cameraPosition.x, maxY - cameraPosition.y, maxZ - cameraPosition.z);
    }

    /** Sodium's inner frustum call has already translated its box into camera coordinates. */
    public static boolean isCameraBoxOutsidePortalView(double minX, double minY, double minZ,
                                                        double maxX, double maxY, double maxZ) {
        if (cameraPosition == null || shadowPass) return false;
        if (targetBounds != null && !targetBounds.intersects(
                minX + cameraPosition.x, minY + cameraPosition.y, minZ + cameraPosition.z,
                maxX + cameraPosition.x, maxY + cameraPosition.y, maxZ + cameraPosition.z)) return true;
        if (clipPlane != null && clipPlane.fullyClips(minX, minY, minZ, maxX, maxY, maxZ)) return true;
        return portalCone != null && portalCone.isFullyOutside(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /** Coarse CPU-side version of the shader front clip, eliminating whole sections behind the door. */
    private record ClipPlane(Vec3 normal, double constant) {
        static ClipPlane create(Vec3 camera, Vec3 point, Vec3 normal) {
            Vec3 relativePoint = point.add(normal.scale(.01)).subtract(camera);
            return new ClipPlane(normal, -normal.dot(relativePoint));
        }

        boolean fullyClips(double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {
            double x = normal.x >= 0 ? maxX : minX;
            double y = normal.y >= 0 ? maxY : minY;
            double z = normal.z >= 0 ? maxZ : minZ;
            return x * normal.x + y * normal.y + z * normal.z + constant < -1e-4;
        }
    }

    /** Fixed interiors share a dimension with the town, so the portal view also needs a target-space bound. */
    private record TargetBounds(double minX, double minY, double minZ,
                                double maxX, double maxY, double maxZ) {
        static TargetBounds from(InteriorRegionRegistry.InteriorRegion region) {
            return new TargetBounds(region.minX(), region.minY(), region.minZ(),
                    region.maxX() + 1.0, region.maxY() + 1.0, region.maxZ() + 1.0);
        }

        boolean intersects(double boxMinX, double boxMinY, double boxMinZ,
                           double boxMaxX, double boxMaxY, double boxMaxZ) {
            return boxMaxX > minX && boxMinX < maxX
                    && boxMaxY > minY && boxMinY < maxY
                    && boxMaxZ > minZ && boxMinZ < maxZ;
        }
    }

    /** Four side planes through the destination aperture and virtual camera, matching IP's inner frustum. */
    private record PortalCone(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3) {
        static PortalCone create(Vec3 camera, Vec3 center, double width) {
            double half = width * .5;
            Vec3 v0 = new Vec3(center.x + half, center.y - 1, center.z).subtract(camera);
            Vec3 v1 = new Vec3(center.x + half, center.y + 1, center.z).subtract(camera);
            Vec3 v2 = new Vec3(center.x - half, center.y + 1, center.z).subtract(camera);
            Vec3 v3 = new Vec3(center.x - half, center.y - 1, center.z).subtract(camera);
            Vec3 forward = center.subtract(camera);
            return new PortalCone(inward(v1.cross(v0), forward), inward(v2.cross(v1), forward),
                    inward(v3.cross(v2), forward), inward(v0.cross(v3), forward));
        }

        private static Vec3 inward(Vec3 plane, Vec3 forward) {
            if (plane.lengthSqr() < 1e-12) return Vec3.ZERO;
            Vec3 normal = plane.normalize();
            return normal.dot(forward) < 0 ? normal.scale(-1) : normal;
        }

        boolean isFullyOutside(double minX, double minY, double minZ,
                               double maxX, double maxY, double maxZ) {
            return fullyBehind(p0, minX, minY, minZ, maxX, maxY, maxZ)
                    || fullyBehind(p1, minX, minY, minZ, maxX, maxY, maxZ)
                    || fullyBehind(p2, minX, minY, minZ, maxX, maxY, maxZ)
                    || fullyBehind(p3, minX, minY, minZ, maxX, maxY, maxZ);
        }

        private static boolean fullyBehind(Vec3 plane, double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ) {
            double x = plane.x > 0 ? maxX : minX;
            double y = plane.y > 0 ? maxY : minY;
            double z = plane.z > 0 ? maxZ : minZ;
            return x * plane.x + y * plane.y + z * plane.z < -1e-4;
        }
    }
}
