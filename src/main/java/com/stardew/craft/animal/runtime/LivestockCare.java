package com.stardew.craft.animal.runtime;

import net.minecraft.nbt.CompoundTag;
import java.util.function.DoubleSupplier;

/** FarmAnimal.cs pet/dayUpdate reducer. No entities or legacy animal state. */
public record LivestockCare(int age, int ownedDays, int friendship, int happiness, int fullness,
                          int daysSinceLay, int quality, boolean petted, boolean autoPetted) {
    public LivestockCare(int age, int ownedDays, int friendship, int happiness, int fullness, int daysSinceLay, int quality, boolean petted) { this(age, ownedDays, friendship, happiness, fullness, daysSinceLay, quality, petted, false); }
    public static LivestockCare purchased() { return new LivestockCare(0, 0, 0, 255, 255, 0, 0, false); }
    public boolean baby() { return age < 3; }
    public LivestockCare mood(int value) { return new LivestockCare(age, ownedDays, friendship, com.stardew.craft.port.PortJava.clamp(value, 0, 255), fullness, daysSinceLay, quality, petted, autoPetted); }
    public LivestockCare graze(boolean blue, boolean happy) {
        return new LivestockCare(age, ownedDays, happy ? Math.min(1000, friendship + (blue ? 16 : 8)) : friendship,
                happy ? 255 : happiness, 255, daysSinceLay, quality, petted, autoPetted);
    }
    public LivestockCare pet(boolean profession) { return pet(LivestockSpecies.WHITE_CHICKEN, profession, false); }
    public LivestockCare pet(LivestockSpecies species, boolean profession, boolean automatic) {
        if (petted || automatic && autoPetted) return this;
        int friendshipGain = autoPetted ? 7 : automatic ? 8 : 15;
        int moodGain = Math.max(5, 30 + species.drain());
        if (profession && !automatic) { friendshipGain += 15; moodGain *= 2; }
        return new LivestockCare(age, ownedDays, Math.min(1000, friendship + friendshipGain),
                Math.min(255, happiness + moodGain), fullness, daysSinceLay, quality, !automatic, autoPetted || automatic);
    }
    public record Day(LivestockCare care, boolean egg, boolean large, boolean produceToday, boolean matured) {}
    public Day nextDay(boolean hay, boolean festival, boolean sheltered, boolean profession, DoubleSupplier random) {
        return nextDay(LivestockSpecies.WHITE_CHICKEN, hay, festival, sheltered, profession, 0, random);
    }
    /** Supplier calls preserve System.Random ordering, including singleton choices and maturity. */
    public Day nextDay(LivestockSpecies species, boolean hay, boolean festival, boolean sheltered, boolean profession, double luck, DoubleSupplier random) {
        int speedBonus = species == LivestockSpecies.SHEEP && friendship >= 900 ? 1 : 0;
        int mood = sheltered ? Math.min(255, happiness + species.drain() * 2) : happiness;
        int friendship = this.friendship;
        if (!petted && !autoPetted) { friendship = Math.max(0, friendship - (10 - friendship / 200)); mood = Math.max(0, mood - 50); }
        int food = fullness < 200 && hay ? 255 : fullness;
        int age = this.age, since = daysSinceLay + 1, quality = this.quality;
        boolean matured = false;
        if (food > 200 || random.getAsDouble() < (food - 30) / 170.0) {
            age++; mood = Math.min(255, mood + species.drain() * 2);
            if (age == species.matureDays()) {
                matured = true; since = 99;
                if (species == LivestockSpecies.SHEEP) random.getAsDouble(); // growFully -> GetProduceID
            }
        }
        if (food < 200) { mood = Math.max(0, mood - 100); friendship = Math.max(0, friendship - 20); }
        if (species == LivestockSpecies.SHEEP && profession) speedBonus++;
        boolean produce = since >= species.interval() - speedBonus && random.getAsDouble() < food / 200.0 && random.getAsDouble() < mood / 70.0;
        boolean egg = produce && age >= species.matureDays(), large = false;
        if (egg) {
            random.getAsDouble(); // GetProduceID -> Next(1).
            if (random.getAsDouble() < mood / 150.0) {
                float modifier = mood > 200 ? mood * 1.5f : mood <= 100 ? mood - 100 : 0;
                if (!species.deluxe().isEmpty()) {
                    random.getAsDouble();
                    if (friendship >= species.deluxeFriendship() && random.getAsDouble() < (friendship + modifier) / species.deluxeDivisor() + luck * species.luckMultiplier()) large = true;
                }
                since = 0;
                double chance = friendship / 1000f - (1f - mood / 225f);
                if (profession) chance += 0.33;
                if (chance >= .95 && random.getAsDouble() < chance / 2) quality = 4;
                else if (random.getAsDouble() < chance / 2) quality = 2;
                else if (random.getAsDouble() < chance) quality = 1;
                else quality = 0;
            }
        }
        return new Day(new LivestockCare(age, ownedDays + 1, friendship, mood, festival ? 250 : 0, since, quality, false, false), egg, large, produce, matured);
    }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putIntArray("Values", new int[]{age, ownedDays, friendship, happiness, fullness, daysSinceLay, quality});
        tag.putBoolean("Petted", petted); tag.putBoolean("AutoPetted", autoPetted); return tag;
    }
    public static LivestockCare load(CompoundTag tag) {
        int[] v = tag.getIntArray("Values");
        if (v.length != 7) throw new IllegalArgumentException("Invalid livestock care state");
        return new LivestockCare(v[0], v[1], v[2], v[3], v[4], v[5], v[6], tag.getBoolean("Petted"), tag.getBoolean("AutoPetted"));
    }
}
