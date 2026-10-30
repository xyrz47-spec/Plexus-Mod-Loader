package dev.plexusmodloader.compat.fabric.mixin;

import dev.plexusmodloader.compat.bridge.HostedLaunch;
import dev.plexusmodloader.compat.bridge.InjectionPointReplacement;
import dev.plexusmodloader.compat.fabric.mixin.HostPatchAdjustments.Finding;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
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
 * takes that name and the parameter that type ({@code PLEXUS-COMPAT-212}); the handler's code keeps working, as the new
 * type is assignable to the old one. Otherwise the mod is incompatible ({@code PLEXUS-COMPAT-224}, ADR-0030).
 *
 * <p>Also locals a Fabric mixin captures by type alone ({@code @Local} without name, ordinal or index: Mixin takes the
 * one local of the parameter's type in scope) at a call the host's patch replaced by another at the same place
 * ({@link InjectionPointReplacement.Placement#SAME_PLACE}), where the injection moves. When the patched method has
 * several locals of that type in scope there (Forge's {@code ServerPlayer.drop(boolean)} keeps the selected stack beside
 * vanilla's {@code removed}), Mixin would find no single one. If exactly one of them has the name of the one vanilla
 * local, the {@code @Local} captures it by that name ({@code PLEXUS-COMPAT-212}); otherwise the mod is incompatible.
 */
final class HostRenamedLocals {

    static final HostRenamedLocals NONE = new HostRenamedLocals(Map.of());

    private static final String LOCAL = "Lcom/llamalad7/mixinextras/sugar/Local;";

    /**
     * Parameter {@code parameter} of handler {@code handler descriptor}: name {@code from} becomes {@code to}; with
     * {@code from} null, the {@code @Local} without a name gets name {@code to}.
     */
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
                            if (implicit(local)) {
                                for (String selector : selectors(injector.get())) {
                                    Optional<Outcome> outcome = checkImplicit(
                                            handler,
                                            index,
                                            type,
                                            injector.get(),
                                            targetName,
                                            selector,
                                            vanilla.get(),
                                            patched.get());
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
                                                where + ": " + outcome.get().reason(),
                                                false,
                                                "PLEXUS-COMPAT-212",
                                                false));
                                    }
                                }
                            }
                            for (String name : names(local)) {
                                for (String selector : selectors(injector.get())) {
                                    Optional<Outcome> outcome = check(
                                            handler,
                                            index,
                                            name,
                                            type,
                                            injector.get(),
                                            targetName,
                                            selector,
                                            vanilla.get(),
                                            patched.get());
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
                                                "PLEXUS-COMPAT-212",
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
         * in the target method {@code selector}, or why it cannot be found; empty when the patch kept that local. When
         * the patch moved the body with {@code injector}'s point into another method ({@link
         * InjectionPointReplacement.Placement#IN_METHOD}), where the injection moves, the local is looked up there.
         */
        private Optional<Outcome> check(
                MethodModel handler,
                int parameter,
                String name,
                ClassDesc type,
                Annotation injector,
                String targetName,
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
            List<MethodModel> after = movedTo(
                    injector, targetName, method, HostPatchAdjustments.methods(patched, method, descriptor), patched);
            // Where the injection point is a call both methods make, the local is looked up in scope there: a patch
            // may keep the name in one scope and rename it in another (Forge 26.1-62.0.9's stopServer keeps the first
            // ServerLevel local's name level and calls the one at ServerLevel.close() levelx).
            List<String> calls = invokePoints(injector).stream()
                    .filter(call -> before.stream().anyMatch(model -> HostPatchAdjustments.contains(model, call))
                            && after.stream().anyMatch(model -> HostPatchAdjustments.contains(model, call)))
                    .toList();
            List<LocalVariableInfo> named = before.stream()
                    .flatMap(model -> calls.isEmpty() ? locals(model).stream() : inScopeAt(model, calls).stream())
                    .filter(local -> local.name().equalsString(name))
                    .toList();
            List<LocalVariableInfo> patchedLocals = after.stream()
                    .flatMap(model -> calls.isEmpty() ? locals(model).stream() : inScopeAt(model, calls).stream())
                    .toList();
            if (named.isEmpty()
                    || after.isEmpty()
                    || patchedLocals.stream().anyMatch(local -> local.name().equalsString(name))) {
                return Optional.empty();
            }
            Set<Integer> slots = new HashSet<>();
            named.forEach(local -> slots.add(local.slot()));
            Map<String, ClassDesc> candidates = new HashMap<>();
            for (LocalVariableInfo local : patchedLocals) {
                ClassDesc localType = local.typeSymbol();
                if (slots.contains(local.slot()) && assignable(localType, type)) {
                    candidates.put(local.name().stringValue(), localType);
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

        /**
         * For parameter {@code parameter} ({@code type}) of {@code handler}, whose {@code @Local} names no local: the
         * name it takes where {@code injector} moves in the target method {@code selector} of {@code targetName}, or why
         * it cannot capture the local it captures on the vanilla game; empty when the injection does not move there
         * through a {@link InjectionPointReplacement.Placement#SAME_PLACE} replacement, or one local of that type is in
         * scope at the vanilla and at the patched point.
         */
        private Optional<Outcome> checkImplicit(
                MethodModel handler,
                int parameter,
                ClassDesc type,
                Annotation injector,
                String targetName,
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
            if (before.isEmpty() || after.isEmpty()) {
                return Optional.empty();
            }
            if (descriptor == null) {
                // Without a descriptor Mixin injects into the first method of that name only.
                before = List.of(before.getFirst());
                after = List.of(after.getFirst());
            }
            List<String[]> points = new ArrayList<>();
            for (AnnotationElement element : injector.elements()) {
                if (element.name().equalsString("at")) {
                    HostPatchAdjustments.points(element.value(), points);
                }
            }
            for (String[] point : points) {
                String target = point[1];
                if (!"INVOKE".equals(point[0])
                        || target == null
                        || after.stream().anyMatch(model -> HostPatchAdjustments.contains(model, target))) {
                    continue;
                }
                String name = method;
                List<MethodModel> patchedMethods = after;
                Optional<String> replacement = host.injectionPointReplacements().stream()
                        .filter(candidate -> candidate.placement() == InjectionPointReplacement.Placement.SAME_PLACE
                                && candidate.targetClass().equals(targetName)
                                && candidate.method().equals(name)
                                && candidate.replaced().equals(target)
                                && patchedMethods.stream()
                                        .anyMatch(
                                                model -> HostPatchAdjustments.contains(model, candidate.replacement())))
                        .map(InjectionPointReplacement::replacement)
                        .findFirst();
                if (replacement.isEmpty()) {
                    continue;
                }
                Set<String> vanillaLocals = new LinkedHashSet<>();
                before.forEach(model -> vanillaLocals.addAll(localsAt(model, target, type)));
                Set<String> patchedLocals = new LinkedHashSet<>();
                after.forEach(model -> patchedLocals.addAll(localsAt(model, replacement.get(), type)));
                if (vanillaLocals.size() != 1 || patchedLocals.size() == 1) {
                    continue;
                }
                String local = vanillaLocals.iterator().next();
                if (patchedLocals.contains(local)) {
                    return Optional.of(new Outcome(
                            new Rename(
                                    handler.methodName().stringValue(),
                                    handler.methodType().stringValue(),
                                    parameter,
                                    null,
                                    local,
                                    type),
                            host.hostName() + "'s patch has " + patchedLocals.size() + " locals of type "
                                    + type.descriptorString() + " " + patchedLocals + " at " + replacement.get()
                                    + ", where the injection moves; its @Local captures the vanilla local " + local
                                    + " by that name"));
                }
                return Optional.of(new Outcome(
                        null,
                        host.hostName() + "'s patch has " + patchedLocals.size() + " locals of type "
                                + type.descriptorString() + " " + patchedLocals + " at " + replacement.get()
                                + ", where the injection moves, and none named " + local + " as on the vanilla"
                                + " game; its @Local without a name cannot capture one; this injection does not run"));
            }
            return Optional.empty();
        }

        /**
         * The patched methods {@code injector} applies to in {@code method} of {@code targetName}: {@code after}, or,
         * when the patch moved the body with one of its points into another method ({@link
         * InjectionPointReplacement.Placement#IN_METHOD}) as {@link HostPatchAdjustments} moves the injection, that
         * method.
         */
        private List<MethodModel> movedTo(
                Annotation injector, String targetName, String method, List<MethodModel> after, ClassModel patched) {
            List<String[]> points = new ArrayList<>();
            for (AnnotationElement element : injector.elements()) {
                if (element.name().equalsString("at")) {
                    HostPatchAdjustments.points(element.value(), points);
                }
            }
            for (String[] point : points) {
                String target = point[1];
                if (target == null || after.stream().anyMatch(model -> HostPatchAdjustments.contains(model, target))) {
                    continue;
                }
                for (InjectionPointReplacement replacement : host.injectionPointReplacements()) {
                    if (replacement.placement() != InjectionPointReplacement.Placement.IN_METHOD
                            || !replacement.targetClass().equals(targetName)
                            || !replacement.method().equals(method)
                            || !replacement.replaced().equals(target)) {
                        continue;
                    }
                    String spec = replacement.replacement();
                    int colon = spec.indexOf(':');
                    String moved = colon < 0 ? spec : spec.substring(0, colon);
                    String movedTarget = colon < 0 ? target : spec.substring(colon + 1);
                    int parenthesis = moved.indexOf('(');
                    List<MethodModel> found = HostPatchAdjustments.methods(
                            patched,
                            parenthesis < 0 ? moved : moved.substring(0, parenthesis),
                            parenthesis < 0 ? null : moved.substring(parenthesis));
                    if (found.stream().anyMatch(model -> HostPatchAdjustments.contains(model, movedTarget))) {
                        return found;
                    }
                }
            }
            return after;
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
                        Optional<Rename> named = renames.stream()
                                .filter(rename -> rename.parameter() == parameter && rename.from() == null)
                                .findFirst();
                        if (named.isPresent() && implicit(annotation)) {
                            List<AnnotationElement> elements = new ArrayList<>(annotation.elements());
                            elements.add(AnnotationElement.of(
                                    "name",
                                    AnnotationValue.ofArray(
                                            AnnotationValue.ofString(named.get().to()))));
                            return Annotation.of(annotation.classSymbol(), elements);
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
                                                                        && text.stringValue()
                                                                                .equals(rename.from()))
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

    /** Whether {@code local} names no local: no name, ordinal or index, so Mixin takes the one local of its type. */
    private static boolean implicit(Annotation local) {
        return local.elements().stream()
                .noneMatch(element -> element.name().equalsString("name")
                        || element.name().equalsString("ordinal")
                        || element.name().equalsString("index"));
    }

    /**
     * The names of the locals of exactly {@code type} (in the local variable table, parameters included) in scope at
     * each instruction of {@code method} that {@code target} names (Mixin's notation).
     */
    private static Set<String> localsAt(MethodModel method, String target, ClassDesc type) {
        Set<String> names = new LinkedHashSet<>();
        inScopeAt(method, List.of(target)).stream()
                .filter(local -> local.typeSymbol().equals(type))
                .forEach(local -> names.add(local.name().stringValue()));
        return names;
    }

    /** The locals of {@code method} in scope at any of its calls of {@code targets} (Mixin's target notation). */
    private static List<LocalVariableInfo> inScopeAt(MethodModel method, List<String> targets) {
        List<LocalVariableInfo> found = new ArrayList<>();
        if (method.code().isEmpty()) {
            return found;
        }
        List<LocalVariableInfo> locals = locals(method);
        int position = 0;
        for (CodeElement element : method.code().get()) {
            if (!(element instanceof Instruction instruction)) {
                continue;
            }
            int at = position;
            if (targets.stream().anyMatch(target -> HostPatchAdjustments.matches(instruction, target))) {
                locals.stream()
                        .filter(local -> local.startPc() <= at
                                && at < local.startPc() + local.length()
                                && !found.contains(local))
                        .forEach(found::add);
            }
            position += instruction.sizeInBytes();
        }
        return found;
    }

    /** The targets of {@code injector}'s {@code INVOKE} injection points. */
    private static List<String> invokePoints(Annotation injector) {
        List<String[]> points = new ArrayList<>();
        for (AnnotationElement element : injector.elements()) {
            if (element.name().equalsString("at")) {
                HostPatchAdjustments.points(element.value(), points);
            }
        }
        return points.stream()
                .filter(point -> "INVOKE".equals(point[0]) && point[1] != null)
                .map(point -> point[1])
                .toList();
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
