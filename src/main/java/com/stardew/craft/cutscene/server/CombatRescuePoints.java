package com.stardew.craft.cutscene.server;

import com.stardew.craft.core.ModDimensions;
import com.stardew.craft.core.ModMiningDimensions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single authoring table for every 3D point used by combat rescue scenes.
 *
 * <p>{@link Status#AUTHOR_CONFIRMED} means the coordinate was captured in
 * Minecraft with the point/camera tools. {@link Status#PENDING_AUTHORING}
 * means the value is only a visible development fallback and must not be used
 * by the normal gameplay entry point.</p>
 */
public final class CombatRescuePoints {
    public enum Status {
        AUTHOR_CONFIRMED,
        PENDING_AUTHORING
    }

    public enum Role {
        PLAYER,
        RESCUER,
        RESCUER_EXIT,
        CAMERA,
        DESTINATION
    }

    public record Point(
            String id,
            Role role,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            Status status,
            String note
    ) {
        public Point {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("point id cannot be blank");
            }
            if (role == null || dimension == null || status == null) {
                throw new IllegalArgumentException("point metadata cannot be null");
            }
            note = note == null ? "" : note;
        }

        public boolean isAuthorConfirmed() {
            return status == Status.AUTHOR_CONFIRMED;
        }
    }

    // Mine — M01-M03 came from the user's point-wand export and M04 from the
    // user's camera-command export in this thread. The user also explicitly
    // confirmed that all four belong to STARDEW_MINING.
    public static final Point M01 = confirmed(
            "M01", Role.PLAYER, ModMiningDimensions.STARDEW_MINING,
            1.0D, 66.0D, -10.0D, 90.0F, 0.0F,
            "user point-wand export: player, west");
    public static final Point M02 = confirmed(
            "M02", Role.RESCUER, ModMiningDimensions.STARDEW_MINING,
            -1.0D, 66.0D, -10.0D, -90.0F, 0.0F,
            "user point-wand export: rescuer, east");
    public static final Point M03 = confirmed(
            "M03", Role.RESCUER_EXIT, ModMiningDimensions.STARDEW_MINING,
            0.0D, 66.0D, -8.0D, 0.0F, 0.0F,
            "user point-wand export: rescuer exit, south");
    public static final Point M04 = confirmed(
            "M04", Role.CAMERA, ModMiningDimensions.STARDEW_MINING,
            -2.270D, 68.256D, -7.442D, -136.5F, 43.8F,
            "user camera-command export");

    // Hospital — H01-H02 came from the user's point-wand export and H03 from
    // the user's camera-command export in this thread.
    public static final Point H01 = confirmed(
            "H01", Role.PLAYER, ModDimensions.STARDEW_VALLEY,
            23.0D, 43.0D, -16.0D, -90.0F, 0.0F,
            "user point-wand export: player, east");
    public static final Point H02 = confirmed(
            "H02", Role.RESCUER, ModDimensions.STARDEW_VALLEY,
            25.0D, 43.0D, -16.0D, 90.0F, 0.0F,
            "user point-wand export: Harvey, west");
    public static final Point H03 = confirmed(
            "H03", Role.CAMERA, ModDimensions.STARDEW_VALLEY,
            24.384D, 44.082D, -12.294D, -178.3F, 31.1F,
            "user camera-command export");

    /*
     * IslandSouth — Minecraft captures have not been supplied.
     *
     * These fallbacks preserve the relative arrangement of the original SDV
     * tile event (farmer 13,33; rescuer 15,33; viewport centred on 13,33) only
     * for authoring/debug. Normal gameplay refuses to start this scene until
     * all three statuses are changed to AUTHOR_CONFIRMED.
     */
    public static final Point I01 = pending(
            "I01", Role.PLAYER, ModDimensions.STARDEW_VALLEY,
            13.0D, 64.0D, 33.0D, 0.0F, 0.0F,
            "PENDING: SDV IslandSouth farmer tile fallback, not a Minecraft capture");
    public static final Point I02 = pending(
            "I02", Role.RESCUER, ModDimensions.STARDEW_VALLEY,
            15.0D, 64.0D, 33.0D, 90.0F, 0.0F,
            "PENDING: SDV IslandSouth rescuer tile fallback, not a Minecraft capture");
    public static final Point I03 = pending(
            "I03", Role.CAMERA, ModDimensions.STARDEW_VALLEY,
            13.0D, 66.0D, 37.0D, 180.0F, 20.0F,
            "PENDING: provisional camera looking at I01/I02, must be recaptured");

    // Desert festival D01 came from the user's point-wand export and has no NPC event.
    public static final Point D01 = confirmed(
            "D01", Role.DESTINATION, ModDimensions.STARDEW_VALLEY,
            -221.0D, 64.0D, -193.0D, 0.0F, 0.0F,
            "user point-wand export: festival recovery, south");

    public static final List<Point> MINE = List.of(M01, M02, M03, M04);
    public static final List<Point> HOSPITAL = List.of(H01, H02, H03);
    public static final List<Point> ISLAND = List.of(I01, I02, I03);
    public static final List<Point> DESERT = List.of(D01);
    public static final Map<String, Point> ALL = indexAll();

    private CombatRescuePoints() {
    }

    public static boolean allAuthorConfirmed(List<Point> points) {
        return points != null && !points.isEmpty()
                && points.stream().allMatch(Point::isAuthorConfirmed);
    }

    public static List<String> pendingPointIds(List<Point> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream()
                .filter(point -> !point.isAuthorConfirmed())
                .map(Point::id)
                .toList();
    }

    private static Point confirmed(
            String id,
            Role role,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String note
    ) {
        return new Point(id, role, dimension, x, y, z, yaw, pitch, Status.AUTHOR_CONFIRMED, note);
    }

    private static Point pending(
            String id,
            Role role,
            ResourceKey<Level> dimension,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            String note
    ) {
        return new Point(id, role, dimension, x, y, z, yaw, pitch, Status.PENDING_AUTHORING, note);
    }

    private static Map<String, Point> indexAll() {
        Map<String, Point> result = new LinkedHashMap<>();
        for (Point point : List.of(M01, M02, M03, M04, H01, H02, H03, I01, I02, I03, D01)) {
            if (result.putIfAbsent(point.id(), point) != null) {
                throw new IllegalStateException("Duplicate combat rescue point ID: " + point.id());
            }
        }
        return Map.copyOf(result);
    }
}
