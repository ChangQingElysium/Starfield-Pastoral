package com.stardew.craft.entity.npc;

import com.stardew.craft.model.AnimatedModel;
import com.stardew.craft.model.ModelAnimation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 沙漠骆驼商人占位实体（Native model animation 渲染）。
 * <ul>
 *   <li>固定位置、无 AI、不可受伤、不可消失。逻辑全部由 {@code CamelMerchantEvents} 管理。</li>
 *   <li>右键交互拦截在 {@code CamelMerchantEvents}，直接打开 ShopRegistry "DesertTrade"。</li>
 *   <li>动画恒为 idle 循环。</li>
 * </ul>
 */
@SuppressWarnings("null")
public class CamelMerchantEntity extends PathfinderMob implements AnimatedModel {

    public CamelMerchantEntity(EntityType<? extends PathfinderMob> entityType, Level level) {
        super(entityType, level);
        this.setPersistenceRequired();
        this.setInvulnerable(true);
        this.setNoAi(true);
        this.setSilent(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.FOLLOW_RANGE, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /** 不可被推动（防止其他实体把它撞偏）。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        // no-op：即使物理引擎尝试推动也忽略
    }

    /** 拦截原版默认右键流程，由 CamelMerchantEvents 处理。 */
    @Override
    public net.minecraft.world.InteractionResult mobInteract(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand) {
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public boolean canBeLeashed(net.minecraft.world.entity.player.Player player) {
        return false;
    }

    // ── Native model animation ────────────────────────────────────────────

    @Override
    public ModelAnimation modelAnimation(boolean moving, float partialTick) {
        return ModelAnimation.loop("idle");
    }

    @Override public int modelTransitionTicks() { return 0; }

}
