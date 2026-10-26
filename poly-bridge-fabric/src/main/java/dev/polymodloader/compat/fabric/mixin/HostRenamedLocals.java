package dev.polymodloader.compat.fabric.mixin;

import dev.polymodloader.compat.bridge.HostedLaunch;
import dev.polymodloader.compat.fabric.mixin.HostPatchAdjustments.Finding;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.LocalVariableInfo;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Locals a Fabric mixin captures by name ({@code @Local(name = ...)} of MixinExtras) that the host loader's patch
 * renamed (ADR-0021 G8). Mixin finds a local by name only when its type is the handler parameter's own, so such an
 * injection would fail on the patched game. When the patched method has, in the
 * vanilla local's slot, one local under another name whose type is the parameter's or a subtype of it (Forge's {@code
 * LinkedHashSet builder} for vanilla's {@code SequencedSet values} in {@code TagLoader.tryBuildTag}), the {@code @Local}
 * takes that name and the parameter that type ({@code POLY-COMPAT-212}); the handler's code keeps working, as the new
 * type is assignable to the old one. Otherwise the mod is incompatible ({@code POLY-COMPAT-224}, ADR-0030).
 */
final class HostRenamedLocals {

    static final HostRenamedLocals NONE = new HostRenamedLocals(Map.of());

    private static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";

    /** Parameter {@code parameter} of handler {@code handler descriptor}: name {@code from} becomes {@code to}. */
    private record Rename(String handler, String descriptor, int parameter, String from, String to, ClassDesc type) {}

    /** A {@link Rename}, or, without one, why the injection cannot find its local. */
    private record Outcome(Rename rename, String reason) {}

    private final Map<String, List<Rename>> renames;

    private HostRenamedLocals(Map<String, List<Rename>> renames) {
        this.renames = renames;
    }

    /** Collects the renames while {@link HostPatchAdjustments#plan} goes through the mixins. Not thread-safe. */
    static final class Planner {

        private final HostedLaunch host;
        private final HostPatchAdjustments.Classes classes;
        private final Map<String, List<Rename>> renames = new HashMap<>();

        Planner(HostedLaunch host, HostPatchAdjustments.Classes classes) {
            this.host = host;
            this.classes = classes;
        }

        /** The findings for {@code mixin} of mod {@code modId}, from configuration {@code config}. */
        List<Finding> check(String modId, String config, ClassModel mixin) {
            String mixinName = mixin.thisClass().asInternalName();
            List<Finding> findings = new ArrayList<>();
            Set<Rename> own = new LinkedHashSet<>();
            for (String targetName : HostPatchAdjustments.mixinTargets(mixin)) {
                Optional<ClassModel> vanilla = classes.vanilla(targetName);
                Optional<ClassModel> patched = classes.patched(targetName);
                if (vanilla.isEmpty() || patched.isEmpty()) {
                    continue;
                }
                for (MethodModel handler : mixin.methods()) {
                    Optional<Annotation> injector = HostPatchAdjustments.INJECTORS.stream()
                            .flatMap(type -> HostPatchAdjustments.annotation(handler.attributes(), type).stream())
                            .findFirst();
                    if (injector.isEmpty()) {
                        continue;
                    }
                    List<List<Annotation>> parameters = parameterAnnotations(handler);
                    for (int index = 0; index < parameters.size(); index++) {
                        ClassDesc type = handler.methodTypeSymbol().parameterType(index);
                        for (Annotation local : parameters.get(index)) {
                            if (!local.className().equalsString(LOCAL) || argsOnly(local)) {
                                continue;
                            }
                            for (String name : names(local)) {
                                for (String selector : selectors(injector.get())) {
                                    Optional<Outcome> outcome =
                                            check(handler, index, name, type, selector, vanilla.get(), patched.get());
                                    if (outcome.isEmpty()) {
                                        continue;
                                    }
                                    String where = where(handler, injector.get(), targetName, selector);
                                    Rename rename = outcome.get().rename();
                                    if (rename == null) {
                                        findings.add(Finding.incompatible(
                                                modId,
                                                config,
                                                mixinName,
                                                where + ": " + outcome.get().reason(),
                                                false));
                                    } else if (own.add(rename)) {
                                        findings.add(new Finding(
                                                modId,
                                                config,
                                                mixinName,
                                                where + ": " + host.hostName() + "'s patch renamed the local " + name
                                                        + " to " + rename.to() + " ("
                                                        + rename.type().descriptorString()
                                                        + "); the injection captures it under that name and type",
                                                false,
                                                "POLY-COMPAT-212",
                                                false));
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (!own.isEmpty()) {
                renames.put(mixinName, List.copyOf(own));
            }
            return findings;
        }

        HostRenamedLocals build() {
            return renames.isEmpty() ? NONE : new HostRenamedLocals(Map.copyOf(renames));
        }

        /**
         * The rename of local {@code name} that parameter {@code parameter} ({@code type}) of {@code handler} captures
         * in the target method {@code selector}, or why it cannot be found; empty when the patch kept that local.
         */
        private Optional<Outcome> check(
                MethodModel handler,
                int parameter,
                String name,
                ClassDesc type,
                String selector,
                ClassModel vanilla,
                ClassModel patched) {
            String method = selector.replaceFirst("^L[^;]+;", "");
            String descriptor = method.contains("(") ? method.substring(method.indexOf('(')) : null;
            method = descriptor == null ? method : method.substring(0, method.indexOf('('));
            if (method.contains("*") || method.startsWith("/") || method.contains(".")) {
                return Optional.empty();
            }
            List<MethodModel> before = HostPatchAdjustments.methods(vanilla, method, descriptor);
            List<MethodModel> after = HostPatchAdjustments.methods(patched, method, descriptor);
            List<LocalVariableInfo> named = before.stream()
                    .flatMap(model -> locals(model).stream())
                    .filter(local -> local.name().equalsString(name))
                    .toList();
            if (named.isEmpty()
                    || after.isEmpty()
                    || after.stream()
                            .flatMap(model -> locals(model).stream())
                            .anyMatch(local -> local.name().equalsString(name))) {
                return Optional.empty();
            }
            Set<Integer> slots = new HashSet<>();
            named.forEach(local -> slots.add(local.slot()));
            Map<String, ClassDesc> candidates = new HashMap<>();
            for (MethodModel model : after) {
                for (LocalVariableInfo local : locals(model)) {
                    ClassDesc localType = local.typeSymbol();
                    if (slots.contains(local.slot()) && assignable(localType, type)) {
                        candidates.put(local.name().stringValue(), localType);
                    }
                }
            }
            if (candidates.size() == 1) {
                Map.Entry<String, ClassDesc> candidate =
                        candidates.entrySet().iterator().next();
                return Optional.of(new Outcome(
                        new Rename(
                                handler.methodName().stringValue(),
                                handler.methodType().stringValue(),
                                parameter,
                                name,
                                candidate.getKey(),
                                candidate.getValue()),
                        null));
            }
            return Optional.of(new Outcome(
                    null,
                    host.hostName() + "'s patch removed or renamed the local " + name + " that its @Local"
                            + " captures, and no single local of type " + type.descriptorString()
                            + " takes its place; this injection does not run"));
        }

        /** Whether a value of {@code from} can be passed where {@code to} is expected. */
        private boolean assignable(ClassDesc from, ClassDesc to) {
            if (from.equals(to)) {
                return true;
            }
            if (!from.isClassOrInterface() || !to.isClassOrInterface()) {
                return false;
            }
            return to.descriptorString().equals("Ljava/lang/Object;")
                    || isSubtype(internalName(from), internalName(to), new HashSet<>());
        }

        private boolean isSubtype(String type, String target, Set<String> seen) {
            if (type.equals(target)) {
                return true;
            }
            if (!seen.add(type)) {
                return false;
            }
            Optional<ClassModel> model = classes.patched(type).or(() -> classes.vanilla(type));
            if (model.isPresent()) {
                List<String> supertypes = new ArrayList<>();
                model.get().superclass().ifPresent(superclass -> supertypes.add(superclass.asInternalName()));
                model.get().interfaces().forEach(entry -> supertypes.add(entry.asInternalName()));
                return supertypes.stream().anyMatch(supertype -> isSubtype(supertype, target, seen));
            }
            // A class of the Java platform: its own hierarchy answers.
            try {
                ClassLoader platform = ClassLoader.getPlatformClassLoader();
                return Class.forName(target.replace('/', '.'), false, platform)
                        .isAssignableFrom(Class.forName(type.replace('/', '.'), false, platform));
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
    }

    /** {@code bytes} of mixin {@code mixin} with the planned {@code @Local} names and parameter types. */
    byte[] apply(String mixin, byte[] bytes) {
        List<Rename> own = renames.get(mixin);
        if (own == null) {
            return bytes;
        }
        ClassFile classFile = ClassFile.of();
        return classFile.transformClass(classFile.parse(bytes), (builder, element) -> {
            if (!(element instanceof MethodModel method)) {
                builder.with(element);
                return;
            }
            List<Rename> handler = own.stream()
                    .filter(rename -> method.methodName().equalsString(rename.handler())
                            && method.methodType().equalsString(rename.descriptor()))
                    .toList();
            if (handler.isEmpty()) {
                builder.with(element);
                return;
            }
            MethodTypeDesc type = method.methodTypeSymbol();
            for (Rename rename : handler) {
                type = type.changeParameterType(rename.parameter(), rename.type());
            }
            builder.withMethod(
                    method.methodName().stringValue(), type, method.flags().flagsMask(), methodBuilder -> {
                        for (MethodElement methodElement : method) {
                            if (methodElement instanceof AccessFlags) {
                                continue;
                            } else if (methodElement
                                    instanceof RuntimeInvisibleParameterAnnotationsAttribute invisible) {
                                methodBuilder.with(RuntimeInvisibleParameterAnnotationsAttribute.of(
                                        renamed(invisible.parameterAnnotations(), handler)));
                            } else if (methodElement instanceof RuntimeVisibleParameterAnnotationsAttribute visible) {
                                methodBuilder.with(RuntimeVisibleParameterAnnotationsAttribute.of(
                                        renamed(visible.parameterAnnotations(), handler)));
                            } else {
                                methodBuilder.with(methodElement);
                            }
                        }
                    });
        });
    }

    private static List<List<Annotation>> renamed(List<List<Annotation>> parameters, List<Rename> renames) {
        List<List<Annotation>> result = new ArrayList<>();
        for (int index = 0; index < parameters.size(); index++) {
            int parameter = index;
            result.add(parameters.get(index).stream()
                    .map(annotation -> {
                        if (!annotation.className().equalsString(LOCAL)) {
                            return annotation;
                        }
                        List<AnnotationElement> elements = new ArrayList<>();
                        for (AnnotationElement element : annotation.elements()) {
                            if (element.name().equalsString("name")
                                    && element.value() instanceof AnnotationValue.OfArray names) {
                                elements.add(AnnotationElement.of(
                                        "name",
                                        AnnotationValue.ofArray(names.values().stream()
                                                .map(value -> value instanceof AnnotationValue.OfString text
                                                        ? renames.stream()
                                                                .filter(rename -> rename.parameter() == parameter
                                                                        && rename.from()
                                                                                .equals(text.stringValue()))
                                                                .findFirst()
                                                                .<AnnotationValue>map(
                                                                        rename -> AnnotationValue.ofString(rename.to()))
                                                                .orElse(value)
                                                        : value)
                                                .toList())));
                            } else {
                                elements.add(element);
                            }
                        }
                        return Annotation.of(annotation.classSymbol(), elements);
                    })
                    .toList());
        }
        return result;
    }

    private static List<List<Annotation>> parameterAnnotations(MethodModel method) {
        int count = method.methodTypeSymbol().parameterCount();
        List<List<Annotation>> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new ArrayList<>());
        }
        method.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                .ifPresent(attribute -> add(result, attribute.parameterAnnotations()));
        method.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                .ifPresent(attribute -> add(result, attribute.parameterAnnotations()));
        return result;
    }

    /** Adds {@code annotations}, which javac may give fewer entries than parameters, to the last ones. */
    private static void add(List<List<Annotation>> result, List<List<Annotation>> annotations) {
        int offset = result.size() - annotations.size();
        for (int index = 0; index < annotations.size() && offset >= 0; index++) {
            result.get(offset + index).addAll(annotations.get(index));
        }
    }

    private static boolean argsOnly(Annotation local) {
        return local.elements().stream()
                .anyMatch(element -> element.name().equalsString("argsOnly")
                        && element.value() instanceof AnnotationValue.OfBoolean flag
                        && flag.booleanValue());
    }

    private static List<String> names(Annotation local) {
        List<String> names = new ArrayList<>();
        for (AnnotationElement element : local.elements()) {
            if (element.name().equalsString("name") && element.value() instanceof AnnotationValue.OfArray array) {
                array.values().forEach(value -> {
                    if (value instanceof AnnotationValue.OfString text) {
                        names.add(text.stringValue());
                    }
                });
            }
        }
        return names;
    }

    private static List<String> selectors(Annotation injector) {
        List<String> selectors = new ArrayList<>();
        for (AnnotationElement element : injector.elements()) {
            if (element.name().equalsString("method")) {
                if (element.value() instanceof AnnotationValue.OfString text) {
                    selectors.add(text.stringValue());
                } else if (element.value() instanceof AnnotationValue.OfArray array) {
                    array.values().forEach(value -> {
                        if (value instanceof AnnotationValue.OfString text) {
                            selectors.add(text.stringValue());
                        }
                    });
                }
            }
        }
        return selectors;
    }

    private static List<LocalVariableInfo> locals(MethodModel method) {
        return method.code()
                .flatMap(code -> code.findAttribute(Attributes.localVariableTable()))
                .map(table -> List.<LocalVariableInfo>copyOf(table.localVariables()))
                .orElse(List.of());
    }

    private static String where(MethodModel handler, Annotation injector, String targetName, String selector) {
        String type = injector.className().stringValue();
        return "@" + type.substring(type.lastIndexOf('/') + 1, type.length() - 1) + " "
                + handler.methodName().stringValue() + " into " + targetName + "."
                + selector.replaceFirst("^L[^;]+;", "").replaceFirst("\\(.*$", "");
    }

    private static String internalName(ClassDesc type) {
        String descriptor = type.descriptorString();
        return descriptor.substring(1, descriptor.length() - 1);
    }
}
