/*
 * Adapted from FabricMC class-tweaker 0.3.0 (EnumExtensionClassVisitor), the library Fabric Loader 0.19.5 ships.
 * Copyright (c) 2020 FabricMC. Licensed under the Apache License, Version 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0); see THIRD_PARTY_NOTICES.md.
 */
package dev.polymodloader.compat.fabric;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ByteVector;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Adds the constants of the loaded Fabric mods' {@code extend-enum} lines to game enums, with the meaning of Fabric
 * Loader 0.19.5's class tweaker ({@code EnumExtensionClassVisitor}, ADR-0020):
 *
 * <ul>
 *   <li>each constant becomes a {@code public static final} enum field of the enum's own type, placed after the
 *       existing constants and before every other field and method
 *   <li>the field carries the {@code org.spongepowered.asm.mixin.StubEnumConstant} attribute; Mixin's enum support
 *       ({@code EnumInfo}, {@code MixinApplicatorEnum}) treats it as a constant a mod's mixin initialises
 *   <li>a name the class already has as a field of its own type is not added
 * </ul>
 *
 * <p>Like Fabric Loader, nothing else is generated: until a mixin assigns it, the constant is {@code null} and not in
 * {@code values()}.
 *
 * <p>Thread: immutable after construction; {@link #apply} is safe from any thread.
 */
final class EnumExtender {

    private final Map<String, List<ClassTweakerFile.EnumExtension>> byTarget;

    private EnumExtender(Map<String, List<ClassTweakerFile.EnumExtension>> byTarget) {
        this.byTarget = byTarget;
    }

    /** The extensions of {@code lines}, in order. */
    static EnumExtender of(List<ClassTweakerFile.EnumExtension> lines) {
        Map<String, List<ClassTweakerFile.EnumExtension>> byTarget = new LinkedHashMap<>();
        for (ClassTweakerFile.EnumExtension line : lines) {
            byTarget.computeIfAbsent(line.target(), target -> new ArrayList<>()).add(line);
        }
        return new EnumExtender(byTarget);
    }

    boolean isEmpty() {
        return byTarget.isEmpty();
    }

    int count() {
        return byTarget.values().stream().mapToInt(List::size).sum();
    }

    /** {@code bytes} with the constants added when {@code className} is a target, otherwise {@code bytes} itself. */
    byte[] apply(String className, byte[] bytes) {
        List<ClassTweakerFile.EnumExtension> lines = byTarget.get(className.replace('.', '/'));
        if (lines == null || bytes == null) {
            return bytes;
        }
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new Visitor(writer, lines), 0);
        return writer.toByteArray();
    }

    private static final class Visitor extends ClassVisitor {

        private final Set<String> added = new LinkedHashSet<>();
        private final List<FieldNode> existingConstants = new ArrayList<>();
        private final List<Runnable> later = new ArrayList<>();
        private Type type;

        Visitor(ClassVisitor next, List<ClassTweakerFile.EnumExtension> lines) {
            super(Opcodes.ASM9, next);
            lines.forEach(line -> added.add(line.constant()));
        }

        @Override
        public void visit(
                int version, int access, String name, String signature, String superName, String[] interfaces) {
            type = Type.getObjectType(name);
            super.visit(version, access, name, signature, superName, interfaces);
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (type.getDescriptor().equals(descriptor)) {
                added.remove(name); // never a second field of the same name
            }
            FieldNode node = new FieldNode(access, name, descriptor, signature, value);
            if ((access & Opcodes.ACC_ENUM) != 0) {
                existingConstants.add(node);
            } else {
                later.add(() -> node.accept(cv));
            }
            return node;
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            MethodNode node = new MethodNode(access, name, descriptor, signature, exceptions);
            later.add(() -> node.accept(cv));
            return node;
        }

        @Override
        public void visitEnd() {
            existingConstants.forEach(constant -> constant.accept(cv));
            for (String constant : added) {
                FieldVisitor field = super.visitField(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_STATIC | Opcodes.ACC_ENUM,
                        constant,
                        type.getDescriptor(),
                        null,
                        null);
                field.visitAttribute(new StubEnumConstant());
                field.visitEnd();
            }
            later.forEach(Runnable::run);
            super.visitEnd();
        }
    }

    /** The empty marker attribute Mixin's enum support looks for. */
    private static final class StubEnumConstant extends Attribute {

        StubEnumConstant() {
            super("org.spongepowered.asm.mixin.StubEnumConstant");
        }

        @Override
        protected ByteVector write(ClassWriter classWriter, byte[] code, int codeLength, int maxStack, int maxLocals) {
            return new ByteVector();
        }
    }
}
