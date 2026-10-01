package com.stardew.craft.port;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.SortedSet;

/**
 * Java 21 library methods used by the 1.21.1 sources, re-implemented for the Java 17 runtime
 * of Minecraft 1.20.1 with the exact JDK 21 semantics (including the exceptions thrown).
 */
public final class PortJava {
    private PortJava() {
    }

    // ------------------------------------------------------------ SequencedCollection (JDK 21)

    public static <E> E getFirst(Iterable<E> c) {
        if (c instanceof List<E> list) {
            if (list.isEmpty()) throw new NoSuchElementException();
            return list.get(0);
        }
        if (c instanceof Deque<E> deque) return deque.getFirst();
        if (c instanceof SortedSet<E> set) return set.first();
        Iterator<E> it = c.iterator();
        if (!it.hasNext()) throw new NoSuchElementException();
        return it.next();
    }

    public static <E> E getLast(Iterable<E> c) {
        if (c instanceof List<E> list) {
            if (list.isEmpty()) throw new NoSuchElementException();
            return list.get(list.size() - 1);
        }
        if (c instanceof Deque<E> deque) return deque.getLast();
        if (c instanceof SortedSet<E> set) return set.last();
        Iterator<E> it = c.iterator();
        if (!it.hasNext()) throw new NoSuchElementException();
        E last = it.next();
        while (it.hasNext()) last = it.next();
        return last;
    }

    public static <E> E removeFirst(Iterable<E> c) {
        if (c instanceof List<E> list) {
            if (list.isEmpty()) throw new NoSuchElementException();
            return list.remove(0);
        }
        if (c instanceof Deque<E> deque) return deque.removeFirst();
        Iterator<E> it = c.iterator();
        if (!it.hasNext()) throw new NoSuchElementException();
        E e = it.next();
        it.remove();
        return e;
    }

    public static <E> E removeLast(Iterable<E> c) {
        if (c instanceof List<E> list) {
            if (list.isEmpty()) throw new NoSuchElementException();
            return list.remove(list.size() - 1);
        }
        if (c instanceof Deque<E> deque) return deque.removeLast();
        Iterator<E> it = c.iterator();
        if (!it.hasNext()) throw new NoSuchElementException();
        E last = it.next();
        while (it.hasNext()) last = it.next();
        it.remove();
        return last;
    }

    public static <E> void addFirst(List<E> list, E e) {
        if (list instanceof Deque<?>) {
            @SuppressWarnings("unchecked") Deque<E> d = (Deque<E>) list;
            d.addFirst(e);
            return;
        }
        list.add(0, e);
    }

    public static <E> void addLast(List<E> list, E e) {
        if (list instanceof Deque<?>) {
            @SuppressWarnings("unchecked") Deque<E> d = (Deque<E>) list;
            d.addLast(e);
            return;
        }
        list.add(e);
    }

    /** JDK 21 List#reversed: a live reverse-ordered view. */
    public static <E> List<E> reversed(List<E> list) {
        return com.google.common.collect.Lists.reverse(list);
    }

    // ------------------------------------------------------------ SequencedMap (JDK 21)

    public static <K, V> Map.Entry<K, V> firstEntry(Map<K, V> map) {
        if (map instanceof SortedMap<K, V> sorted) {
            if (sorted.isEmpty()) return null;
            K k = sorted.firstKey();
            return Map.entry(k, sorted.get(k));
        }
        Iterator<Map.Entry<K, V>> it = map.entrySet().iterator();
        if (!it.hasNext()) return null;
        Map.Entry<K, V> e = it.next();
        return new java.util.AbstractMap.SimpleImmutableEntry<>(e);
    }

    public static <K, V> Map.Entry<K, V> lastEntry(Map<K, V> map) {
        if (map instanceof SortedMap<K, V> sorted) {
            if (sorted.isEmpty()) return null;
            K k = sorted.lastKey();
            return Map.entry(k, sorted.get(k));
        }
        Map.Entry<K, V> last = null;
        for (Map.Entry<K, V> e : map.entrySet()) last = e;
        return last == null ? null : new java.util.AbstractMap.SimpleImmutableEntry<>(last);
    }

    public static <K, V> Map.Entry<K, V> pollFirstEntry(Map<K, V> map) {
        Map.Entry<K, V> e = firstEntry(map);
        if (e != null) map.remove(e.getKey());
        return e;
    }

    public static <K, V> Map.Entry<K, V> pollLastEntry(Map<K, V> map) {
        Map.Entry<K, V> e = lastEntry(map);
        if (e != null) map.remove(e.getKey());
        return e;
    }

    public static <K> java.util.Set<K> sequencedKeySet(Map<K, ?> map) {
        return map.keySet();
    }

    public static <V> java.util.Collection<V> sequencedValues(Map<?, V> map) {
        return map.values();
    }

    public static <K, V> java.util.Set<Map.Entry<K, V>> sequencedEntrySet(Map<K, V> map) {
        return map.entrySet();
    }

    /** JDK 21 LinkedHashMap#putLast: re-inserts the key at the end of iteration order. */
    public static <K, V> V putLast(LinkedHashMap<K, V> map, K key, V value) {
        V old = map.remove(key);
        map.put(key, value);
        return old;
    }

    public static <E> java.util.Set<E> reversedSet(LinkedHashSet<E> set) {
        List<E> copy = new ArrayList<>(set);
        Collections.reverse(copy);
        return new LinkedHashSet<>(copy);
    }

    // ------------------------------------------------------------ Math (JDK 21)

    public static int clamp(long value, int min, int max) {
        if (min > max) throw new IllegalArgumentException(min + " > " + max);
        return (int) Math.min(max, Math.max(value, min));
    }

    public static long clamp(long value, long min, long max) {
        if (min > max) throw new IllegalArgumentException(min + " > " + max);
        return Math.min(max, Math.max(value, min));
    }

    public static double clamp(double value, double min, double max) {
        if (!(min < max)) {
            if (Double.isNaN(min)) throw new IllegalArgumentException("min is NaN");
            if (Double.isNaN(max)) throw new IllegalArgumentException("max is NaN");
            if (Double.compare(min, max) > 0) throw new IllegalArgumentException(min + " > " + max);
        }
        return Math.min(max, Math.max(value, min));
    }

    public static float clamp(float value, float min, float max) {
        if (!(min < max)) {
            if (Float.isNaN(min)) throw new IllegalArgumentException("min is NaN");
            if (Float.isNaN(max)) throw new IllegalArgumentException("max is NaN");
            if (Float.compare(min, max) > 0) throw new IllegalArgumentException(min + " > " + max);
        }
        return Math.min(max, Math.max(value, min));
    }

    // ------------------------------------------------------------ String / Character (JDK 21)

    public static String repeat(char c, int count) {
        return String.valueOf(c).repeat(count);
    }

    public static int indexOf(String s, int ch, int beginIndex, int endIndex) {
        int from = Math.max(beginIndex, 0);
        int to = Math.min(endIndex, s.length());
        for (int i = from; i < to; i++) {
            if (s.charAt(i) == ch) return i;
        }
        return -1;
    }
}
