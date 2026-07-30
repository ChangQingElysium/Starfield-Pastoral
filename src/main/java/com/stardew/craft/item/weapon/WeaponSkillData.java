package com.stardew.craft.item.weapon;

import com.stardew.craft.StardewCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * 武器技能数据
 */
public class WeaponSkillData {
    
    private final String id;
    private final String nameKey;
    private final List<String> descriptionKeys;
    private final int damagePercent;
    private final List<String> effectKeys;
    private final int cooldown;
    private final String iconChar; // 自定义字体中的技能图标字符
    
    private WeaponSkillData(Builder builder) {
        this.id = builder.id;
        this.nameKey = builder.nameKey;
        this.descriptionKeys = List.copyOf(builder.descriptionKeys);
        this.damagePercent = builder.damagePercent;
        this.effectKeys = List.copyOf(builder.effectKeys);
        this.cooldown = builder.cooldown;
        this.iconChar = builder.iconChar;
    }
    
    // Getters
    public String getId() { return id; }
    public String getNameKey() { return nameKey; }
    public List<String> getDescriptionKeys() { return descriptionKeys; }
    public int getDamagePercent() { return damagePercent; }
    public List<String> getEffectKeys() { return effectKeys; }
    public int getCooldown() { return cooldown; }
    public String getIconChar() { return iconChar; }

    /**
     * Canonical runtime identity for this skill.
     *
     * <p>Built-in definitions may keep their compact path-only ids, while API
     * definitions may use a fully namespaced id. Runtime dispatch must never
     * compare those two forms as unrelated strings.</p>
     */
    public ResourceLocation getResourceId() {
        ResourceLocation parsed = id.indexOf(':') >= 0
                ? ResourceLocation.tryParse(id)
                : ResourceLocation.tryBuild(StardewCraft.MODID, id);
        if (parsed == null) {
            throw new IllegalStateException("Invalid weapon skill id: " + id);
        }
        return parsed;
    }

    public boolean matches(ResourceLocation resourceId) {
        return resourceId != null && getResourceId().equals(resourceId);
    }
    
    public static Builder builder(String id) {
        return new Builder(id);
    }
    
    public static class Builder {
        private final String id;
        private String nameKey = "";
        private List<String> descriptionKeys = new ArrayList<>();
        private int damagePercent = 0;
        private List<String> effectKeys = new ArrayList<>();
        private int cooldown = 0;
        private String iconChar = null;
        
        public Builder(String id) {
            this.id = id;
        }
        
        public Builder nameKey(String nameKey) {
            this.nameKey = nameKey;
            return this;
        }
        
        public Builder descriptionKeys(String... keys) {
            this.descriptionKeys = List.of(keys);
            return this;
        }
        
        public Builder damage(int percent) {
            this.damagePercent = percent;
            return this;
        }
        
        public Builder effectKey(String key) {
            this.effectKeys.add(key);
            return this;
        }
        
        public Builder cooldown(int seconds) {
            this.cooldown = seconds;
            return this;
        }
        
        public Builder icon(String iconChar) {
            this.iconChar = iconChar;
            return this;
        }
        
        public WeaponSkillData build() {
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("Weapon skill id must not be blank");
            }
            if (damagePercent < 0) {
                throw new IllegalStateException(
                        "Weapon skill damage percent must not be negative: " + id
                );
            }
            if (cooldown < 0) {
                throw new IllegalStateException(
                        "Weapon skill cooldown must not be negative: " + id
                );
            }
            WeaponSkillData data = new WeaponSkillData(this);
            data.getResourceId();
            return data;
        }
    }
}
