package com.stardew.craft.client.interior;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** Copies view-owned visibility/order data while retaining the shared region meshes. */
final class TownDoorSodiumLists {
    private Field lists;
    private Field region;
    private Field[] arrays;
    private Field[] counts;
    private Constructor<?> sortedConstructor;
    private Constructor<?> regionConstructor;

    Object copy(Object source) throws ReflectiveOperationException {
        if (lists == null) resolve(source.getClass());
        var originals = (ObjectArrayList<?>) lists.get(source);
        var copies = new ObjectArrayList<Object>(originals.size());
        for (Object original : originals) {
            Object copy = regionConstructor.newInstance(region.get(original));
            for (Field array : arrays) {
                byte[] from = (byte[]) array.get(original);
                byte[] to = (byte[]) array.get(copy);
                System.arraycopy(from, 0, to, 0, from.length);
            }
            for (Field count : counts) count.setInt(copy, count.getInt(original));
            copies.add(copy);
        }
        return sortedConstructor.newInstance(copies);
    }

    private void resolve(Class<?> sorted) throws ReflectiveOperationException {
        Class<?> chunkList = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList",
                false, sorted.getClassLoader());
        Field nextLists = field(sorted, "lists");
        region = field(chunkList, "region");
        arrays = new Field[]{field(chunkList, "sectionsWithGeometry"), field(chunkList, "sectionsWithSprites"),
                field(chunkList, "sectionsWithEntities")};
        counts = new Field[]{field(chunkList, "sectionsWithGeometryCount"), field(chunkList, "sectionsWithSpritesCount"),
                field(chunkList, "sectionsWithEntitiesCount"), field(chunkList, "size"), field(chunkList, "lastVisibleFrame")};
        regionConstructor = chunkList.getConstructor(region.getType());
        sortedConstructor = sorted.getDeclaredConstructor(nextLists.getType());
        sortedConstructor.setAccessible(true);
        lists = nextLists;
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
