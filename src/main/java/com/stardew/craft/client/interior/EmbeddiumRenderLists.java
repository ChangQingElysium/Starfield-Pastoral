package com.stardew.craft.client.interior;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;

/** PORT(1.20.1): Embeddium 0.3.31's lists contain mutable, region-owned visibility arrays. */
public final class EmbeddiumRenderLists {
    private static final String PREFIX = "me.jellysquid.mods.sodium.client.render.chunk.";
    private final Constructor<?> listsConstructor;
    private final Constructor<?> chunkConstructor;
    private final Field lists;
    private final Field region;
    private final Field[] arrays;
    private final Field[] counts;

    public EmbeddiumRenderLists(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> listsType = Class.forName(PREFIX + "lists.SortedRenderLists", false, loader);
        Class<?> chunkType = Class.forName(PREFIX + "lists.ChunkRenderList", false, loader);
        Class<?> regionType = Class.forName(PREFIX + "region.RenderRegion", false, loader);
        listsConstructor = listsType.getDeclaredConstructor(ObjectArrayList.class);
        listsConstructor.setAccessible(true);
        chunkConstructor = chunkType.getConstructor(regionType);
        lists = field(listsType, "lists");
        region = field(chunkType, "region");
        arrays = fields(chunkType, "sectionsWithGeometry", "sectionsWithSprites", "sectionsWithEntities");
        counts = fields(chunkType, "sectionsWithGeometryCount", "sectionsWithSpritesCount",
                "sectionsWithEntitiesCount", "size", "lastVisibleFrame");
    }

    public Object copy(Object source) throws ReflectiveOperationException {
        var copied = new ObjectArrayList<Object>();
        for (Object entry : (List<?>) lists.get(source)) {
            Object target = chunkConstructor.newInstance(region.get(entry));
            for (Field array : arrays) {
                byte[] from = (byte[]) array.get(entry);
                byte[] to = (byte[]) array.get(target);
                System.arraycopy(from, 0, to, 0, from.length);
            }
            for (Field count : counts) count.setInt(target, count.getInt(entry));
            copied.add(target);
        }
        return listsConstructor.newInstance(copied);
    }

    private static Field[] fields(Class<?> type, String... names) throws NoSuchFieldException {
        Field[] result = new Field[names.length];
        for (int i = 0; i < names.length; i++) result[i] = field(type, names[i]);
        return result;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field result = type.getDeclaredField(name);
        result.setAccessible(true);
        return result;
    }
}
