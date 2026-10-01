package com.stardew.craft.client.interior;

import com.stardew.craft.mixin.TownDoorViewAreaAccessor;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import com.stardew.craft.port.net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
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

    private TownDoorVanillaVisibility() {}

    public static void discover(ClientLevel level, ViewArea viewArea, Camera camera, Frustum sourceFrustum,
                                ObjectArrayList<SectionRenderDispatcher.RenderSection> result) {
        result.clear();
        QUEUE.clear();
        VISITED.clear();

        var cameraBlock = camera.getBlockPosition();
        int cameraX = SectionPos.blockToSectionCoord(cameraBlock.getX());
        int cameraY = SectionPos.blockToSectionCoord(cameraBlock.getY());
        int cameraZ = SectionPos.blockToSectionCoord(cameraBlock.getZ());
        int viewDistance = viewArea.getViewDistance();
        Frustum frustum = LevelRenderer.offsetFrustum(sourceFrustum);
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
            SectionRenderDispatcher.RenderSection section =
                    ((TownDoorViewAreaAccessor) viewArea).stardewcraft$getRenderSectionAt(origin);
            if (section == null || !section.getOrigin().equals(origin)) continue;
            boolean cameraSection = sectionX == cameraX && sectionY == cameraY && sectionZ == cameraZ;
            if (!cameraSection && !frustum.isVisible(section.getBoundingBox())) continue;

            result.add(section);
            enqueue(sectionX + 1, sectionY, sectionZ);
            enqueue(sectionX - 1, sectionY, sectionZ);
            enqueue(sectionX, sectionY + 1, sectionZ);
            enqueue(sectionX, sectionY - 1, sectionZ);
            enqueue(sectionX, sectionY, sectionZ + 1);
            enqueue(sectionX, sectionY, sectionZ - 1);
        }
    }

    private static void enqueue(int sectionX, int sectionY, int sectionZ) {
        long packed = SectionPos.asLong(sectionX, sectionY, sectionZ);
        if (VISITED.add(packed)) QUEUE.enqueue(packed);
    }
}
