package com.stardew.craft.platform.fabric.bootstrap;

import com.google.gson.GsonBuilder;
import com.stardew.craft.platform.fabric.event.EventBusSubscriber;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/** Build-time bytecode scan: includes nested classes and never initializes gameplay code. */
public final class SubscriberIndexGenerator {
    private static final String ANNOTATION = "Lcom/stardew/craft/platform/fabric/event/EventBusSubscriber;";
    private static final String HANDLER = "Lcom/stardew/craft/platform/fabric/event/SubscribeEvent;";
    private static final String EVENT = "com/stardew/craft/platform/fabric/event/Event";
    private static final String MOD_EVENT = "com/stardew/craft/platform/fabric/event/ModBusEvent";

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 2) throw new IllegalArgumentException("Expected compiled classes and resource output");
        Path input = Path.of(arguments[0]);
        List<SubscriberIndex.Entry> entries = new ArrayList<>();
        Hierarchy hierarchy = new Hierarchy(input);
        try (var files = Files.walk(input)) {
            for (Path path : files.filter(path -> path.toString().endsWith(".class")).sorted().toList()) {
                entries.addAll(read(Files.readAllBytes(path), hierarchy));
            }
        }
        entries.sort(Comparator.comparing(SubscriberIndex.Entry::className).thenComparing(SubscriberIndex.Entry::bus));
        SubscriberIndex.Contents contents = new SubscriberIndex.Contents(2,
                entries.stream().mapToInt(SubscriberIndex.Entry::staticHandlers).sum(), entries);
        Path output = Path.of(arguments[1]);
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(contents) + "\n", StandardCharsets.UTF_8);
        long classes = entries.stream().map(SubscriberIndex.Entry::className).distinct().count();
        System.out.println("Indexed " + contents.staticHandlers() + " static handlers in " + classes + " subscribers / " + entries.size() + " bus entries");
    }

    static List<SubscriberIndex.Entry> read(byte[] bytecode) {
        return read(bytecode, new Hierarchy(null));
    }

    private static List<SubscriberIndex.Entry> read(byte[] bytecode, Hierarchy hierarchy) {
        class Visitor extends ClassVisitor {
            String name;
            boolean subscriber;
            Set<EnvType> sides = EnumSet.allOf(EnvType.class);
            List<String> eventTypes = new ArrayList<>();
            List<String> invalidHandlers = new ArrayList<>();

            Visitor() { super(Opcodes.ASM9); }

            @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                this.name = name.replace('/', '.');
            }

            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (!ANNOTATION.equals(descriptor)) return null;
                subscriber = true;
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitArray(String field) {
                        if (!field.equals("value")) return null;
                        sides.clear();
                        return new AnnotationVisitor(Opcodes.ASM9) {
                            @Override public void visitEnum(String ignored, String type, String value) {
                                if (!sides.add(EnvType.valueOf(value))) throw new IllegalArgumentException("Duplicate subscriber side in " + name);
                            }
                        };
                    }
                };
            }

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (!HANDLER.equals(annotation)) return null;
                        Type[] arguments = Type.getArgumentTypes(descriptor);
                        if ((access & Opcodes.ACC_STATIC) == 0 || arguments.length != 1
                                || arguments[0].getSort() != Type.OBJECT || Type.getReturnType(descriptor) != Type.VOID_TYPE) {
                            invalidHandlers.add(name + descriptor);
                        } else {
                            eventTypes.add(arguments[0].getInternalName());
                        }
                        return null;
                    }
                };
            }
        }
        Visitor visitor = new Visitor();
        new ClassReader(bytecode).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (!visitor.subscriber) return List.of();
        if (!visitor.invalidHandlers.isEmpty()) throw new IllegalArgumentException("Invalid automatic subscriber handlers in "
                + visitor.name + ": " + visitor.invalidHandlers);
        int game = 0, mod = 0;
        for (String eventType : visitor.eventTypes) {
            if (!hierarchy.isAssignable(eventType, EVENT)) throw new IllegalArgumentException("Subscriber parameter is not a project event: "
                    + visitor.name + " -> " + eventType);
            if (hierarchy.isAssignable(eventType, MOD_EVENT)) mod++; else game++;
        }
        List<SubscriberIndex.Entry> entries = new ArrayList<>();
        if (game > 0 || mod == 0) entries.add(new SubscriberIndex.Entry(visitor.name, EventBusSubscriber.Bus.GAME, List.copyOf(visitor.sides), game));
        if (mod > 0) entries.add(new SubscriberIndex.Entry(visitor.name, EventBusSubscriber.Bus.MOD, List.copyOf(visitor.sides), mod));
        return entries;
    }

    /** Reads the actual event superclass/interface graph, without defining or initializing any class. */
    private static final class Hierarchy {
        private final Path input;
        private final Map<String, List<String>> parents = new HashMap<>();
        private Hierarchy(Path input) { this.input = input; }

        boolean isAssignable(String type, String target) {
            Set<String> visited = new HashSet<>();
            var pending = new ArrayDeque<String>(); pending.add(type);
            while (!pending.isEmpty()) {
                String current = pending.removeFirst();
                if (current.equals(target)) return true;
                if (!visited.add(current)) continue;
                pending.addAll(parents.computeIfAbsent(current, this::readParents));
            }
            return false;
        }

        private List<String> readParents(String name) {
            try {
                ClassReader reader;
                Path file = input == null ? null : input.resolve(name + ".class");
                if (file != null && Files.isRegularFile(file)) reader = new ClassReader(Files.readAllBytes(file));
                else try (var stream = SubscriberIndexGenerator.class.getResourceAsStream("/" + name + ".class")) {
                    if (stream == null) throw new IllegalArgumentException("Cannot resolve subscriber event hierarchy: " + name);
                    reader = new ClassReader(stream);
                }
                List<String> result = new ArrayList<>(List.of(reader.getInterfaces()));
                if (reader.getSuperName() != null) result.add(reader.getSuperName());
                return List.copyOf(result);
            } catch (IOException exception) {
                throw new IllegalArgumentException("Cannot read subscriber event hierarchy: " + name, exception);
            }
        }
    }

    private SubscriberIndexGenerator() {}
}
