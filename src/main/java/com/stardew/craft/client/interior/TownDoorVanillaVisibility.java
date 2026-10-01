package com.stardew.craft.client.interior;

import com.stardew.craft.mixin.TownDoorViewAreaAccessor;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.minecraft.core.SectionPos;

/**
 * Non-occluding section discovery for the vanilla nested view.
 *
 * <p>This is the small same-level equivalent of Immersive Portals'
 * {@code VisibleSectionDiscovery}: it keeps the outer world's asynchronous
 * {@code SectionOcclusionGraph} untouched and walks only sections visible
 * through the doorway frustum.</p>
 */
public final class TownDoorVanillaVisibility {
    private static final LongArrayFIFOQueue QUEUE = new LongArrayFIFOQueue();
    private static final LongOpenHashSet VISITED = new LongOpenHashSet();
    /**
     * PORT(1.20.1): LevelRenderer's visible list holds package-private {@code LevelRenderer.RenderChunkInfo}
     * wrappers instead of bare sections. Only {@code chunk} is read from entries outside the occlusion walk.
     */
    private static final MethodHandle NEW_RENDER_CHUNK_INFO = renderChunkInfoConstructor();

    private TownDoorVanillaVisibility() {}

    public static void discover(ClientLevel level, ViewArea viewArea, Camera camera, Frustum sourceFrustum,
                                ObjectArrayList<Object> result) {
        result.clear();
        QUEUE.clear();
        VISITED.clear();

        var cameraBlock = camera.getBlockPosition();
        int cameraX = SectionPos.blockToSectionCoord(cameraBlock.getX());
        int cameraY = SectionPos.blockToSectionCoord(cameraBlock.getY());
        int cameraZ = SectionPos.blockToSectionCoord(cameraBlock.getZ());
        int viewDistance = (((TownDoorViewAreaAccessor) viewArea).stardewcraft$getChunkGridSizeX() - 1) / 2;
        // 1.21 LevelRenderer.offsetFrustum(frustum)
        Frustum frustum = new Frustum(sourceFrustum).offsetToFullyIncludeCameraCube(8);
        var position = camera.getPosition();
        frustum.prepare(position.x, position.y, position.z);

        enqueue(cameraX, cameraY, cameraZ);
        while (!QUEUE.isEmpty()) {
            long packed = QUEUE.dequeueLong();
            int sectionX = SectionPos.x(packed);
            int sectionY = SectionPos.y(packed);
            int sectionZ = SectionPos.z(packed);
            if (Math.abs(sectionX - cameraX) > viewDistance
                    || Math.abs(sectionY - cameraY) > viewDistance
                    || Math.abs(sectionZ - cameraZ) > viewDistance
                    || sectionY < level.getMinSection() || sectionY >= level.getMaxSection()) continue;

            BlockPos origin = new BlockPos(SectionPos.sectionToBlockCoord(sectionX),
                    SectionPos.sectionToBlockCoord(sectionY), SectionPos.sectionToBlockCoord(sectionZ));
            ChunkRenderDispatcher.RenderChunk section =
                    ((TownDoorViewAreaAccessor) viewArea).stardewcraft$getRenderSectionAt(origin);
            if (section == null || !section.getOrigin().equals(origin)) continue;
            boolean cameraSection = sectionX == cameraX && sectionY == cameraY && sectionZ == cameraZ;
            if (!cameraSection && !frustum.isVisible(section.getBoundingBox())) continue;

            result.add(newRenderChunkInfo(section));
            enqueue(sectionX + 1, sectionY, sectionZ);
            enqueue(sectionX - 1, sectionY, sectionZ);
            enqueue(sectionX, sectionY + 1, sectionZ);
            enqueue(sectionX, sectionY - 1, sectionZ);
            enqueue(sectionX, sectionY, sectionZ + 1);
            enqueue(sectionX, sectionY, sectionZ - 1);
        }
    }

    private static Object newRenderChunkInfo(ChunkRenderDispatcher.RenderChunk section) {
        try {
            return NEW_RENDER_CHUNK_INFO.invoke(section, (Direction) null, 0);
        } catch (Throwable error) {
            throw new IllegalStateException("Could not create LevelRenderer.RenderChunkInfo", error);
        }
    }

    private static MethodHandle renderChunkInfoConstructor() {
        try {
            Class<?> type = Class.forName("net.minecraft.client.renderer.LevelRenderer$RenderChunkInfo", false,
                    TownDoorVanillaVisibility.class.getClassLoader());
            var constructor = type.getDeclaredConstructor(ChunkRenderDispatcher.RenderChunk.class, Direction.class, int.class);
            constructor.setAccessible(true);
            return MethodHandles.lookup().unreflectConstructor(constructor)
                    .asType(MethodType.methodType(Object.class, ChunkRenderDispatcher.RenderChunk.class,
                            Direction.class, int.class));
        } catch (ReflectiveOperationException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static void enqueue(int sectionX, int sectionY, int sectionZ) {
        long packed = SectionPos.asLong(sectionX, sectionY, sectionZ);
        if (VISITED.add(packed)) QUEUE.enqueue(packed);
    }
}
