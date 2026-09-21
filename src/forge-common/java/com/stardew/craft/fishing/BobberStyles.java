package com.stardew.craft.fishing;

import net.minecraft.util.RandomSource;

/** Stardew 1.6 FishingRod / ChooseFromIconsMenu rules, independent of the 3D shell. */
public final class BobberStyles {
    public static final int COUNT=39, RANDOM=-2, SHORTS=39;
    private BobberStyles() {}
    public static int normalize(int selected){return selected==RANDOM||selected>=0&&selected<COUNT?selected:0;}
    public static int lastUnlocked(int fishSpecies){return Math.min(COUNT-1,Math.max(0,fishSpecies)/2);}
    public static boolean canSelect(int selected,int fishSpecies){return selected==RANDOM||selected>=0&&selected<=lastUnlocked(fishSpecies);}
    public static int resolve(int selected,int fishSpecies,RandomSource random){
        // C# Next(0) returns 0. Its exclusive bound differs from the menu's inclusive index.
        if(selected==RANDOM)return Math.min(COUNT-1,random.nextInt(Math.max(1,Math.max(0,fishSpecies)/2)));
        return canSelect(selected,fishSpecies)?selected:0;
    }
    public static int lineColor(int style){
        return switch(style){
            case 6,20 -> 0xffc8ff; case 7 -> 0xffff00; case 35,39 -> 0xb4a0ff;
            case 9 -> 0xffffc8; case 10 -> 0xffd0a9; case 11 -> 0xaaaaff;
            case 12 -> 0x696969; case 14,22 -> 0xb2ff70; case 15 -> 0xfac146;
            case 16 -> 0xffaaaa; case 37,38 -> 0xc8ffff; case 17 -> 0xc8dcff;
            case 13 -> 0xe4e4ac; case 31 -> 0x7f0000; case 29,32 -> 0x00a800;
            case 25,27 -> 0x7f7f7f; default -> 0xffffff;
        };
    }
}
