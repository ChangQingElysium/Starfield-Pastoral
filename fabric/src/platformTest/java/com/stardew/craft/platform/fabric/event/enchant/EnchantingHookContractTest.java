package com.stardew.craft.platform.fabric.event.enchant;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class EnchantingHookContractTest {
    @Test
    void anvilHookRunsAfterInitialCostBeforeEmptyAndEnchantmentStorageChecks() throws IOException {
        var method = method(node("net/minecraft/world/inventory/AnvilMenu"), "createResult");
        assertTrue(call(method, "net/minecraft/world/inventory/DataSlot", "set") < call(method, "net/minecraft/world/item/ItemStack", "isEmpty"));
        assertTrue(call(method, "net/minecraft/world/item/ItemStack", "isEmpty") < call(method, "net/minecraft/world/item/enchantment/EnchantmentHelper", "canStoreEnchantments"));
        var mixin = method(node("com/stardew/craft/platform/mixin/event/enchant/mixin/AnvilUpdateMixin"), "stardewcraft$anvilUpdate");
        assertTrue(call(mixin, "com/stardew/craft/platform/fabric/event/enchant/FabricEnchantingEvents", "anvil") < call(mixin, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel"));
    }
    @Test
    void grindstonePlacementPreservesBroadcastAndTakeCapturesTheActualAnonymousConstructor() throws IOException {
        var menu = node("net/minecraft/world/inventory/GrindstoneMenu");
        var create = method(menu, "createResult");
        assertTrue(call(create, "net/minecraft/world/inventory/GrindstoneMenu", "computeResult") < call(create, "net/minecraft/world/inventory/GrindstoneMenu", "broadcastChanges"));
        var slot = node("net/minecraft/world/inventory/GrindstoneMenu$4");
        assertEquals("(Lnet/minecraft/world/inventory/GrindstoneMenu;Lnet/minecraft/world/Container;IIILnet/minecraft/world/inventory/ContainerLevelAccess;)V", method(slot, "<init>").desc);
        assertEquals("(Lnet/minecraft/world/level/Level;)I", method(slot, "getExperienceAmount").desc);
        var take = method(node("com/stardew/craft/platform/mixin/event/enchant/mixin/GrindstoneTakeMixin"), "stardewcraft$take");
        assertTrue(call(take, "com/stardew/craft/platform/fabric/event/enchant/FabricEnchantingEvents", "take") < call(take, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel"));
    }
    @Test
    void enchantCallbackWrapsTheActualListAndLapisCallsAfterBookConversionWithoutRawLocalIndices() throws IOException {
        var method = method(node("net/minecraft/world/inventory/EnchantmentMenu"), "method_17410");
        assertEquals("(Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/world/entity/player/Player;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V", method.desc);
        int apply = call(method, "net/minecraft/world/item/ItemStack", "enchant");
        int lapis = call(method, "net/minecraft/world/item/ItemStack", "consume");
        assertTrue(call(method, "net/minecraft/world/item/ItemStack", "transmuteCopy") < apply);
        assertTrue(apply < lapis); assertTrue(lapis < call(method, "net/minecraft/world/entity/player/Player", "awardStat"));
        var mixin = node("com/stardew/craft/platform/mixin/event/enchant/mixin/EnchantItemMixin");
        var remember = method(mixin, "stardewcraft$rememberEnchantments");
        var consume = method(mixin, "stardewcraft$enchanted");
        assertTrue(call(consume, "net/minecraft/world/Container", "getItem") < call(consume, "com/stardew/craft/platform/fabric/event/enchant/FabricEnchantingEvents", "enchanted"));
        assertTrue(call(consume, "com/stardew/craft/platform/fabric/event/enchant/FabricEnchantingEvents", "enchanted") < call(consume, "com/llamalad7/mixinextras/injector/wrapoperation/Operation", "call"));
        for (var hook : java.util.List.of(remember, consume)) {
            var annotations = new java.util.ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if (hook.visibleParameterAnnotations != null) for (var values : hook.visibleParameterAnnotations) if (values != null) annotations.addAll(values);
            if (hook.invisibleParameterAnnotations != null) for (var values : hook.invisibleParameterAnnotations) if (values != null) annotations.addAll(values);
            assertTrue(annotations.stream().anyMatch(a -> a.desc.equals("Lcom/llamalad7/mixinextras/sugar/Share;") && a.values.contains("stardewcraft$appliedEnchantments")));
            assertFalse(annotations.stream().anyMatch(a -> a.desc.equals("Lcom/llamalad7/mixinextras/sugar/Local;")));
        }
    }
    private static int call(MethodNode method, String owner, String name) {
        for (int i = 0; i < method.instructions.size(); i++) if (method.instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(owner) && call.name.equals(name)) return i;
        fail(owner + "." + name); return -1;
    }
    private static MethodNode method(ClassNode node, String name) { return node.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow(); }
    private static ClassNode node(String name) throws IOException {
        try (var bytes = EnchantingHookContractTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(bytes); var node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.SKIP_FRAMES); return node;
        }
    }
}
