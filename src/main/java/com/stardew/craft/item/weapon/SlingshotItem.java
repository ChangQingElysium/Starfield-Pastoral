package com.stardew.craft.item.weapon;

import com.stardew.craft.combat.WeaponType;
import com.stardew.craft.combat.equipment.EquipmentResolver;
import com.stardew.craft.entity.projectile.SlingshotProjectile;
import com.stardew.craft.item.IStardewItem;
import com.stardew.craft.sound.ModSounds;
import com.stardew.craft.port.net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import com.stardew.craft.port.net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import java.util.List;

/** Source slingshots: one ammunition attachment, bow-style hold/release, no stamina cost. */
public final class SlingshotItem extends Item implements IStardewItem, IStardewWeapon {
    public static final int CHARGE_TICKS = 6; // SDV Slingshot.GetRequiredChargeTime: 0.3 seconds.
    private final boolean master;
    private final WeaponData data;
    public SlingshotItem(Properties properties) { this(properties, false); }
    public SlingshotItem(Properties properties, boolean master) {
        super(properties.stacksTo(1));
        this.master = master;
        this.data = WeaponData.builder(getWeaponId()).type(WeaponType.SLINGSHOT)
                .level(master ? 2 : 1).damage(1, 1).critChance(0).build();
    }
    public boolean isMaster() { return master; }
    public int damageMultiplier() { return master ? 2 : 1; }
    @Override public boolean onLeftClickEntity(ItemStack stack, Player player, net.minecraft.world.entity.Entity target) {
        return true; // A slingshot has no SDV melee strike.
    }
    @Override public String getItemTypeKey() { return "stardewcraft.type.weapon.slingshot"; }
    @Override public String getWeaponId() { return master ? "master_slingshot" : "slingshot"; }
    @Override public WeaponData getWeaponData() { return data; }
    @Override public InteractionResultHolder<ItemStack> useSkill(Level l, Player p, InteractionHand h, boolean major) {
        return InteractionResultHolder.pass(p.getItemInHand(h));
    }
    public static ItemStack ammunition(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
    }
    public static void ammunition(ItemStack stack, ItemStack ammo) {
        stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(ammo.copy())));
    }
    public static boolean accepts(ItemStack stack) { return SlingshotAmmo.accepts(stack); }
    @Override public boolean overrideOtherStackedOnMe(ItemStack bow, ItemStack incoming, Slot slot,
            ClickAction action, Player player, SlotAccess cursor) {
        if(action != ClickAction.SECONDARY || !slot.allowModification(player)) return false;
        return attach(bow, incoming, cursor::set, player);
    }
    /** Shared by vanilla containers and the Stardew inventory page. */
    public static boolean attach(ItemStack bow, ItemStack incoming, java.util.function.Consumer<ItemStack> cursor, Player player) {
        ItemStack old = ammunition(bow);
        if(incoming.isEmpty()) {
            if(old.isEmpty()) return true; // SDV still handles an empty attachment: do not pick up the tool.
            cursor.accept(old); ammunition(bow, ItemStack.EMPTY);
            attachmentSound(player, ModSounds.DWOP.get()); return true;
        }
        if(!accepts(incoming)) return true; // Invalid ammo leaves the tool and cursor unchanged on right-click.
        if(old.isEmpty()) { ammunition(bow, incoming); cursor.accept(ItemStack.EMPTY); }
        else if(ItemStack.isSameItemSameComponents(old, incoming)) {
            int count = Math.min(incoming.getCount(), old.getMaxStackSize() - old.getCount());
            if (count == 0) { ammunition(bow, incoming); cursor.accept(old); }
            else { old.grow(count); incoming.shrink(count); ammunition(bow, old); }
        } else { ammunition(bow, incoming); cursor.accept(old); }
        attachmentSound(player, ModSounds.BUTTON1.get());
        return true;
    }
    private static void attachmentSound(Player player, net.minecraft.sounds.SoundEvent sound) {
        if (!player.level().isClientSide)
            player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, .7f, 1);
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if(!accepts(ammunition(stack))) {
            if(!level.isClientSide) player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.stardewcraft.slingshot.no_ammo"), true);
            return InteractionResultHolder.fail(stack);
        }
        if(!player.getItemInHand(hand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND).isEmpty()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand); return InteractionResultHolder.consume(stack);
    }
    @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!level.isClientSide && getUseDuration(stack, entity) - remaining == CHARGE_TICKS)
            level.playSound(null, entity.blockPosition(), ModSounds.SLINGSHOT.get(), SoundSource.PLAYERS, .7f, 1);
    }
    @Override public java.util.Optional<net.minecraft.world.inventory.tooltip.TooltipComponent> getTooltipImage(ItemStack stack) {
        return java.util.Optional.of(new com.stardew.craft.tooltip.SlingshotAmmoTooltip(ammunition(stack)));
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
            List<net.minecraft.network.chat.Component> lines, TooltipFlag flag) {
        lines.add(net.minecraft.network.chat.Component.translatable("tooltip.stardewcraft.slingshot.description"));
    }
    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }
    @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if(level.isClientSide || !(entity instanceof ServerPlayer player)
                || getUseDuration(stack, entity) - remaining < CHARGE_TICKS) return;
        ItemStack ammo = ammunition(stack); if(!accepts(ammo)) return;
        var equipment = EquipmentResolver.getMergedStats(player);
        int damage = SlingshotAmmo.rollDamage(ammo, player.getRandom(), equipment.getAttackMultiplier(), damageMultiplier());
        var projectile = new SlingshotProjectile(level, player, stack, ammo.copyWithCount(1), damage);
        // 19 or 20 SDV pixels at 60 updates/s, 64 pixels/tile, converted to MC blocks/tick.
        float speed = (19 + player.getRandom().nextInt(2)) * 3f / 64f * (1 + equipment.getWeaponSpeedMultiplier());
        var direction = player.getLookAngle();
        projectile.shoot(direction.x, direction.y, direction.z, speed, 0);
        if(!level.addFreshEntity(projectile)) return;
        ammo.shrink(1); ammunition(stack, ammo);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
    }
}
