package com.stardew.craft.util;

/**
 * Stardew Valley 1.6 parity for Utility.CreateRandom: xxHash32 over five
 * little-endian seed parts, followed by the legacy System.Random algorithm.
 */
public final class StardewDeterministicRandom {
    private static final int MBIG = Integer.MAX_VALUE;
    private static final int MSEED = 161803398;
    private static final int PRIME32_1 = 0x9E3779B1;
    private static final int PRIME32_2 = 0x85EBCA77;
    private static final int PRIME32_3 = 0xC2B2AE3D;
    private static final int PRIME32_4 = 0x27D4EB2F;
    private static final int PRIME32_5 = 0x165667B1;

    private final int[] seedArray = new int[56];
    private int inext;
    private int inextp;

    private StardewDeterministicRandom(int seed) {
        int subtraction = seed == Integer.MIN_VALUE ? Integer.MAX_VALUE : Math.abs(seed);
        int mj = MSEED - subtraction;
        seedArray[55] = mj;
        int mk = 1;
        for (int i = 1; i < 55; i++) {
            int ii = (21 * i) % 55;
            seedArray[ii] = mk;
            mk = mj - mk;
            if (mk < 0) {
                mk += MBIG;
            }
            mj = seedArray[ii];
        }
        for (int k = 1; k < 5; k++) {
            for (int i = 1; i < 56; i++) {
                seedArray[i] -= seedArray[1 + (i + 30) % 55];
                if (seedArray[i] < 0) {
                    seedArray[i] += MBIG;
                }
            }
        }
        inext = 0;
        inextp = 21;
    }

    public static StardewDeterministicRandom create(long seedA, long seedB, long seedC) {
        return create(seedA, seedB, seedC, 0L, 0L);
    }

    public static StardewDeterministicRandom create(long seedA, long seedB, long seedC, long seedD, long seedE) {
        byte[] data = new byte[20];
        writeLittleEndian(data, 0, seedPart(seedA));
        writeLittleEndian(data, 4, seedPart(seedB));
        writeLittleEndian(data, 8, seedPart(seedC));
        writeLittleEndian(data, 12, seedPart(seedD));
        writeLittleEndian(data, 16, seedPart(seedE));
        return new StardewDeterministicRandom(xxHash32(data));
    }

    public int nextInt(int maxExclusive) {
        if (maxExclusive <= 0) {
            throw new IllegalArgumentException("maxExclusive must be positive");
        }
        return (int) (sample() * maxExclusive);
    }

    public double nextDouble() {
        return sample();
    }

    private double sample() {
        return internalSample() * (1.0D / MBIG);
    }

    private int internalSample() {
        int next = inext + 1;
        if (next >= 56) {
            next = 1;
        }
        int nextp = inextp + 1;
        if (nextp >= 56) {
            nextp = 1;
        }
        int value = seedArray[next] - seedArray[nextp];
        if (value == MBIG) {
            value--;
        }
        if (value < 0) {
            value += MBIG;
        }
        seedArray[next] = value;
        inext = next;
        inextp = nextp;
        return value;
    }

    private static int seedPart(long seed) {
        return (int) Math.floorMod(seed, 2147483647L);
    }

    private static void writeLittleEndian(byte[] data, int offset, int value) {
        data[offset] = (byte) value;
        data[offset + 1] = (byte) (value >>> 8);
        data[offset + 2] = (byte) (value >>> 16);
        data[offset + 3] = (byte) (value >>> 24);
    }

    private static int xxHash32(byte[] data) {
        int index = 0;
        int hash;
        if (data.length >= 16) {
            int limit = data.length - 16;
            int v1 = PRIME32_1 + PRIME32_2;
            int v2 = PRIME32_2;
            int v3 = 0;
            int v4 = -PRIME32_1;
            while (index <= limit) {
                v1 = round(v1, readInt(data, index));
                index += 4;
                v2 = round(v2, readInt(data, index));
                index += 4;
                v3 = round(v3, readInt(data, index));
                index += 4;
                v4 = round(v4, readInt(data, index));
                index += 4;
            }
            hash = Integer.rotateLeft(v1, 1) + Integer.rotateLeft(v2, 7)
                + Integer.rotateLeft(v3, 12) + Integer.rotateLeft(v4, 18);
        } else {
            hash = PRIME32_5;
        }
        hash += data.length;
        while (index <= data.length - 4) {
            hash += readInt(data, index) * PRIME32_3;
            hash = Integer.rotateLeft(hash, 17) * PRIME32_4;
            index += 4;
        }
        while (index < data.length) {
            hash += (data[index] & 0xFF) * PRIME32_5;
            hash = Integer.rotateLeft(hash, 11) * PRIME32_1;
            index++;
        }
        hash ^= hash >>> 15;
        hash *= PRIME32_2;
        hash ^= hash >>> 13;
        hash *= PRIME32_3;
        hash ^= hash >>> 16;
        return hash;
    }

    private static int round(int acc, int input) {
        acc += input * PRIME32_2;
        acc = Integer.rotateLeft(acc, 13);
        return acc * PRIME32_1;
    }

    private static int readInt(byte[] data, int offset) {
        return (data[offset] & 0xFF)
            | ((data[offset + 1] & 0xFF) << 8)
            | ((data[offset + 2] & 0xFF) << 16)
            | (data[offset + 3] << 24);
    }
}
