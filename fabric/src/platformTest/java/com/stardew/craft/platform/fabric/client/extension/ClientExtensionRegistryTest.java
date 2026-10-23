package com.stardew.craft.platform.fabric.client.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.stardew.craft.platform.fabric.event.EventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ClientExtensionRegistryTest {
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }

    @Test
    void registrationPreservesIdentityAndRejectsDuplicateBatchesAtomically() {
        var registry = new ClientExtensionRegistry();
        var first = Items.APPLE; var second = Items.CARROT; var third = Items.POTATO;
        var extension = new IClientItemExtensions() {};
        registry.registerItem(extension, first);
        assertSame(extension, registry.item(first));
        assertSame(IClientItemExtensions.DEFAULT, registry.item(second));
        assertThrows(IllegalStateException.class, () -> registry.registerItem(extension, second, first));
        assertFalse(registry.isItemRegistered(second));
        assertThrows(IllegalStateException.class, () -> registry.registerItem(extension, second, second));
        assertFalse(registry.isItemRegistered(second));
        assertThrows(NullPointerException.class, () -> registry.registerItem(extension, third, null));
        assertFalse(registry.isItemRegistered(third));
        assertThrows(IllegalArgumentException.class, () -> registry.registerItem(extension));
    }

    @Test
    void legacyInheritedCallbacksRunBeforeTheModEventAndCannotBeOverwritten() {
        List<String> order = new ArrayList<>();
        var block = allocate(LegacyBlock.class); block.order = order;
        var inherited = allocate(InheritedLegacyItem.class); inherited.order = order; inherited.extension = new IClientItemExtensions() {};
        var eventItem = Items.APPLE; var registry = new ClientExtensionRegistry();
        var bus = new EventBus();
        bus.addListener(RegisterClientExtensionsEvent.class, event -> {
            assertTrue(event.isBlockRegistered(block));
            assertTrue(event.isItemRegistered(inherited));
            assertSame(inherited.extension, registry.item(inherited));
            assertThrows(IllegalStateException.class, () -> event.registerItem(new IClientItemExtensions() {}, inherited));
            event.registerItem(new IClientItemExtensions() {}, Holder.direct(eventItem));
            order.add("event");
        });
        FabricClientExtensions.initialize(registry, bus, List.of(inherited, Items.STONE), List.of(block));
        assertEquals(List.of("block", "item", "event"), order);
        assertTrue(registry.isItemRegistered(eventItem));
    }

    @Test
    void failuresRetainTheirProviderAndRootCauseInsteadOfDroppingTheExtension() {
        var registry = new ClientExtensionRegistry();
        var failure = assertThrows(IllegalStateException.class, () -> registry.readLegacyItems(List.of(allocate(FailingItem.class))));
        assertTrue(failure.getMessage().contains(FailingItem.class.getName()));
        assertEquals("renderer failed", failure.getCause().getMessage());
        var bad = assertThrows(IllegalStateException.class, () -> registry.readLegacyItems(List.of(allocate(WrongTypeItem.class))));
        assertInstanceOf(IllegalArgumentException.class, bad.getCause());
        assertTrue(bad.getCause().getMessage().contains("IClientItemExtensions"));
    }

    @Test
    void commonLegacyProviderLoadsWithoutResolvingAnyClientExtensionClass() throws Exception {
        String prefix = ClientExtensionRegistryTest.class.getName() + "$";
        String name = prefix + "ServerLoadableItem";
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
                if (className.startsWith("com.stardew.craft.platform.fabric.client.extension.") && !className.startsWith(prefix))
                    throw new ClassNotFoundException("Client class unavailable: " + className);
                if (className.startsWith(prefix)) {
                    synchronized (getClassLoadingLock(className)) {
                        var type = findLoadedClass(className);
                        if (type == null) {
                            try (var bytes = getParent().getResourceAsStream(className.replace('.', '/') + ".class")) {
                                if (bytes == null) throw new ClassNotFoundException(className);
                                byte[] data = bytes.readAllBytes(); type = defineClass(className, data, 0, data.length);
                            } catch (java.io.IOException failure) { throw new ClassNotFoundException(className, failure); }
                        }
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(className, resolve);
            }
        };
        var type = Class.forName(name, true, loader);
        Object provider = allocate(type);
        assertInstanceOf(Item.class, provider);
        assertEquals(Consumer.class, type.getMethod("initializeClient", Consumer.class).getParameterTypes()[0]);
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass(IClientItemExtensions.class.getName()));
        var unavailable = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> type.getMethod("initializeClient", Consumer.class).invoke(provider, (Consumer<Object>) ignored -> {}));
        assertInstanceOf(NoClassDefFoundError.class, unavailable.getCause());
    }

    // Bootstrap freezes the native registry; constructor-free provider fixtures avoid mutating it.
    private static <T> T allocate(Class<T> type) {
        try {
            var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
            return type.cast(((sun.misc.Unsafe) field.get(null)).allocateInstance(type));
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    public static class LegacyItem extends Item {
        List<String> order;
        IClientItemExtensions extension;
        public LegacyItem(List<String> order) { super(new Item.Properties()); this.order = order; }
        public void initializeClient(Consumer<IClientItemExtensions> consumer) { order.add("item"); consumer.accept(extension); }
    }
    public static final class InheritedLegacyItem extends LegacyItem {
        public InheritedLegacyItem(List<String> order) { super(order); }
    }
    public static final class LegacyBlock extends Block {
        List<String> order;
        public LegacyBlock(List<String> order) { super(BlockBehaviour.Properties.of()); this.order = order; }
        public void initializeClient(Consumer<IClientBlockExtensions> consumer) { order.add("block"); consumer.accept(new IClientBlockExtensions() {}); }
    }
    public static final class FailingItem extends Item {
        public FailingItem() { super(new Item.Properties()); }
        public void initializeClient(Consumer<IClientItemExtensions> consumer) { throw new IllegalArgumentException("renderer failed"); }
    }
    public static final class WrongTypeItem extends Item {
        public WrongTypeItem() { super(new Item.Properties()); }
        public void initializeClient(Consumer<Object> consumer) { consumer.accept(new Object()); }
    }
    public static final class ServerLoadableItem extends Item {
        public ServerLoadableItem() { super(new Item.Properties()); }
        public void initializeClient(Consumer<IClientItemExtensions> consumer) { consumer.accept(new IClientItemExtensions() {}); }
    }
}
