package com.stardew.craft.monster;
/** Preserve C# unchecked Int32 addition and remainder; world seed supplies SDV's save identity. */
public final class MetalHeadLoot {
    private MetalHeadLoot(){}
    public static boolean hasHelmet(int sourceKills,long saveIdentity){return (sourceKills+(int)saveIdentity)%100==0;}
}
