package com.stardew.craft.platform.fabric.item.damage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Unit;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.injection.Inject;

class ItemDamageHooksTest {
    @BeforeAll static void bootstrap() { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }

    @Test void theItemCanVetoDroppedEntityDamageUsingTheExactStackAndDamageSource() throws Exception {
        var item = allocate(ProtectedItem.class); var stack = new ItemStack(Items.DIAMOND); var source = new Source(false);
        assertFalse(ItemDamageHooks.allowsDamage(item, stack, source));
        assertSame(stack, item.lastStack); assertSame(source, item.lastSource);
        assertEquals(1, item.queries);
    }

    @Test void perStackPropertiesCanChangeTheVetoWithoutChangingTheItemsIdentity() throws Exception {
        var item = allocate(StackDependentItem.class); var first = new ItemStack(Items.DIAMOND); var second = first.copy();
        second.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        assertTrue(ItemDamageHooks.allowsDamage(item, first, new Source(false)));
        assertFalse(ItemDamageHooks.allowsDamage(item, second, new Source(false)));
        assertTrue(ItemDamageHooks.allowsDamage(item, first, new Source(false)));
    }

    @Test void ordinaryItemsAndTheDefaultProviderAllowNativeChecksToContinue() {
        var stack = new ItemStack(Items.DIAMOND); var source = new Source(false);
        assertTrue(ItemDamageHooks.allowsDamage(Items.DIAMOND, stack, source));
        assertTrue(new ItemDamageProvider() {}.canBeHurtBy(stack, source));
        assertTrue(stack.canBeHurtBy(source));
    }

    @Test void nativeFireResistanceRemainsEffectiveAfterAnItemAllowsTheDamage() throws Exception {
        var stack = new ItemStack(Items.DIAMOND); stack.set(DataComponents.FIRE_RESISTANT, Unit.INSTANCE);
        var fire = new Source(true); var other = new Source(false);
        assertTrue(ItemDamageHooks.allowsDamage(Items.DIAMOND, stack, fire));
        assertFalse(stack.canBeHurtBy(fire)); assertTrue(stack.canBeHurtBy(other));
        var method = nativeMethod(ItemStack.class, "canBeHurtBy");
        assertEquals("(Lnet/minecraft/world/damagesource/DamageSource;)Z", method.desc);
        var calls = calls(method);
        assertTrue(calls.contains("net/minecraft/world/item/ItemStack.has(Lnet/minecraft/core/component/DataComponentType;)Z"));
        assertTrue(calls.contains("net/minecraft/world/damagesource/DamageSource.is(Lnet/minecraft/tags/TagKey;)Z"));
    }

    @Test void theMixinVetoesAtTheNativeStackEntryPointBeforeComponentChecksAndOnlyReturnsFalse() throws Exception {
        var mixin = Class.forName("com.stardew.craft.platform.mixin.item.damage.mixin.ItemStackDamageMixin");
        var hooks = java.util.Arrays.stream(mixin.getDeclaredMethods()).map(method -> method.getAnnotation(Inject.class)).filter(java.util.Objects::nonNull).toList();
        assertEquals(1, hooks.size()); var hook = hooks.getFirst();
        assertArrayEquals(new String[]{"canBeHurtBy"}, hook.method()); assertEquals("HEAD", hook.at()[0].value());
        assertTrue(hook.cancellable()); assertEquals(1, hook.require());
        var method = nativeMethod(mixin, "stardewcraft$itemDamageVeto");
        assertTrue(calls(method).contains("com/stardew/craft/platform/fabric/item/damage/ItemDamageHooks.allowsDamage(Lnet/minecraft/world/item/Item;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/damagesource/DamageSource;)Z"));
        assertTrue(java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).anyMatch(insn -> insn.getOpcode() == Opcodes.ICONST_0));
        assertFalse(java.util.stream.StreamSupport.stream(method.instructions.spliterator(), false).anyMatch(insn -> insn.getOpcode() == Opcodes.ICONST_1));
    }

    @Test void nativeItemEntityDamageChecksRemainOrderedBeforeHealthLossAndDiscard() throws Exception {
        var calls = calls(nativeMethod(ItemEntity.class, "hurt"));
        int invulnerable = calls.indexOf("net/minecraft/world/entity/item/ItemEntity.isInvulnerableTo(Lnet/minecraft/world/damagesource/DamageSource;)Z");
        int stackGate = calls.indexOf("net/minecraft/world/item/ItemStack.canBeHurtBy(Lnet/minecraft/world/damagesource/DamageSource;)Z");
        int markHurt = calls.indexOf("net/minecraft/world/entity/item/ItemEntity.markHurt()V");
        int discard = calls.indexOf("net/minecraft/world/entity/item/ItemEntity.discard()V");
        assertTrue(invulnerable >= 0 && invulnerable < stackGate); assertTrue(stackGate < markHurt && markHurt < discard);
        assertTrue(calls.contains("net/minecraft/world/damagesource/DamageSource.is(Lnet/minecraft/tags/TagKey;)Z"));
    }

    @Test void damageContractsContainNoClientLinkOrClientOnlyStrippingAnnotation() throws Exception {
        for (var type : List.of(ItemDamageProvider.class, ItemDamageHooks.class,
                Class.forName("com.stardew.craft.platform.mixin.item.damage.mixin.ItemStackDamageMixin"))) {
            try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
                assertNotNull(stream); var bytes = new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
                assertFalse(bytes.contains("net/minecraft/client/")); assertFalse(bytes.contains("net/fabricmc/api/Environment"));
            }
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception { var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type)); }
    private static final class ProtectedItem extends Item implements ItemDamageProvider {
        ItemStack lastStack; DamageSource lastSource; int queries;
        ProtectedItem() { super(new Properties()); }
        @Override public boolean canBeHurtBy(ItemStack stack, DamageSource source) { lastStack = stack; lastSource = source; queries++; return false; }
    }
    private static final class StackDependentItem extends Item implements ItemDamageProvider {
        StackDependentItem() { super(new Properties()); }
        @Override public boolean canBeHurtBy(ItemStack stack, DamageSource source) { return !stack.has(DataComponents.FIRE_RESISTANT); }
    }
    private static final class Source extends DamageSource {
        private final boolean fire;
        Source(boolean fire) { super(Holder.direct(new DamageType("stardewcraft_item_test", 0))); this.fire = fire; }
        @Override public boolean is(TagKey<DamageType> tag) { return fire && tag.equals(DamageTypeTags.IS_FIRE); }
    }
    private static MethodNode nativeMethod(Class<?> type, String name) throws Exception {
        var node = new ClassNode(); try (var stream = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) { assertNotNull(stream); new ClassReader(stream).accept(node, 0); }
        var matches = node.methods.stream().filter(method -> method.name.equals(name)).toList(); assertEquals(1, matches.size()); return matches.getFirst();
    }
    private static List<String> calls(MethodNode method) { var result = new ArrayList<String>(); for (var insn : method.instructions) if (insn instanceof MethodInsnNode call) result.add(call.owner + "." + call.name + call.desc); return result; }
}
