package com.stardew.craft.port.event;

import com.mojang.logging.LogUtils;
import com.stardew.craft.port.net.neoforged.fml.common.EventBusSubscriber;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.GenericEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.IModBusEvent;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.moddiscovery.ModAnnotation;
import net.minecraftforge.forgespi.language.ModFileScanData;
import org.slf4j.Logger;

/**
 * PORT(1.20.1): NeoForge 21.1 automatic event subscriber semantics on Forge 1.20.1.
 * <p>
 * Every class annotated with the port {@link EventBusSubscriber} is loaded (respecting {@code value} dists and
 * {@code modid}), and each static {@code @SubscribeEvent} method is registered individually on the bus its event type
 * belongs to: {@link IModBusEvent} types on the mod bus, all others on {@link MinecraftForge#EVENT_BUS}. Priority and
 * {@code receiveCanceled} are honoured; non-public methods are supported (NeoForge supports them, Forge's
 * {@code IEventBus.register(Class)} silently skips them).
 */
public final class PortEventSubscribers {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final org.objectweb.asm.Type SUBSCRIBER = org.objectweb.asm.Type.getType(EventBusSubscriber.class);
    private static final MethodType LISTENER_TYPE = MethodType.methodType(void.class, Event.class);

    private PortEventSubscribers() {}

    @SuppressWarnings("unchecked")
    public static void inject(String modId, IEventBus modBus, ClassLoader loader) {
        var modFile = ModList.get().getModFileById(modId);
        if (modFile == null) throw new IllegalStateException("Mod file for " + modId + " is not loaded");
        ModFileScanData scan = modFile.getFile().getScanResult();
        for (ModFileScanData.AnnotationData data : scan.getAnnotations()) {
            if (!SUBSCRIBER.equals(data.annotationType())) continue;
            Map<String, Object> values = data.annotationData();
            String owner = (String) values.getOrDefault("modid", "");
            if (!owner.isEmpty() && !owner.equals(modId)) continue;
            List<ModAnnotation.EnumHolder> dists = (List<ModAnnotation.EnumHolder>) values.get("value");
            if (dists != null && dists.stream().noneMatch(holder -> Dist.valueOf(holder.getValue()) == FMLEnvironment.dist)) {
                continue;
            }
            String className = data.clazz().getClassName();
            try {
                register(Class.forName(className, true, loader), modBus);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Failed to load @EventBusSubscriber class " + className, exception);
            }
        }
    }

    /** Routes every static {@code @SubscribeEvent} method of {@code type} to the bus its event belongs to. */
    public static void register(Class<?> type, IEventBus modBus) {
        MethodHandles.Lookup lookup;
        try {
            lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access event subscriber " + type.getName(), exception);
        }
        for (Method method : type.getDeclaredMethods()) {
            SubscribeEvent subscribe = method.getAnnotation(SubscribeEvent.class);
            if (subscribe == null) continue;
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new IllegalArgumentException("Expected @SubscribeEvent method " + method
                        + " to be static because its class is an @EventBusSubscriber");
            }
            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0])) {
                throw new IllegalArgumentException("Method " + method + " has @SubscribeEvent but does not take exactly one Event");
            }
            Class<? extends Event> eventType = method.getParameterTypes()[0].asSubclass(Event.class);
            IEventBus bus = IModBusEvent.class.isAssignableFrom(eventType) ? modBus : MinecraftForge.EVENT_BUS;
            addListener(bus, lookup, method, eventType, subscribe);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void addListener(IEventBus bus, MethodHandles.Lookup lookup, Method method,
            Class<? extends Event> eventType, SubscribeEvent subscribe) {
        MethodHandle handle;
        try {
            handle = lookup.unreflect(method).asType(LISTENER_TYPE);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access event listener " + method, exception);
        }
        Consumer<Event> listener = event -> invoke(handle, event);
        if (GenericEvent.class.isAssignableFrom(eventType)) {
            Type parameter = method.getGenericParameterTypes()[0];
            if (!(parameter instanceof ParameterizedType parameterized)
                    || !(parameterized.getActualTypeArguments()[0] instanceof Class<?> filter)) {
                throw new IllegalArgumentException("Generic event listener " + method + " needs a concrete type argument");
            }
            bus.addGenericListener((Class) filter, subscribe.priority(), subscribe.receiveCanceled(),
                    (Class) eventType, (Consumer) listener);
        } else {
            bus.addListener(subscribe.priority(), subscribe.receiveCanceled(), (Class) eventType, (Consumer) listener);
        }
        LOGGER.debug("Subscribed {} to the {} bus", method, bus == MinecraftForge.EVENT_BUS ? "game" : "mod");
    }

    private static void invoke(MethodHandle handle, Event event) {
        try {
            handle.invokeExact(event);
        } catch (RuntimeException | Error exception) {
            throw exception;
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
