package dev.plexusmodloader.compat.fabric.mixin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import dev.plexusmodloader.compat.bridge.HostedLaunch;
import dev.plexusmodloader.compat.bridge.InjectionPointReplacement;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.Annotation;
import java.lang.classfile.AnnotationElement;
import java.lang.classfile.AnnotationValue;
import java.lang.classfile.Attribute;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeElement;
import java.lang.classfile.FieldModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.attribute.RuntimeInvisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.StoreInstruction;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Fabric mods' mixins on a host loader's patched game (ADR-0021 G8, ADR-0030). Under MinecraftForge or NeoForge the
 * Fabric mods' mixins apply to the host's patched classes, where some vanilla calls they inject at, or members they
 * use, are gone or changed. Before the runtime decides which mods load, and again before Mixin starts, this compares
 * each mixin of the Fabric mods with the vanilla class and the class the host patched. Each difference is one {@link
 * Finding}, of one of two kinds:
 *
 * <ul>
 *   <li>The mixin still works as written, because Plexus Mod Loader moves it to where the host put the same thing
 *       ({@code PLEXUS-COMPAT-226}, {@code 212}, {@code 222}):
 *       <ul>
 *         <li>An injection at a call, field access or object creation that the patch moved goes to where the host says
 *             its patch put the same point ({@link InjectionPointReplacement}); an injector bound to the call itself
 *             ({@code @Redirect} and the like) moves only to a call of the same shape. When the host says its patch
 *             moved the call into a helper that returns true exactly when it made it, a non-cancellable {@code
 *             @Inject} before it runs after each such helper call through a MixinExtras {@code @WrapOperation} bridge
 *             ({@link Wrap}). A call the patch makes with more arguments at the end keeps its injection ({@link
 *             Appended}). A {@code @WrapOperation} of a call whose {@code Either} result the patch reads with an
 *             {@code Optional} on the left side (MinecraftForge's conditional registry elements) runs through a bridge
 *             that hands the handler the vanilla value ({@link HostOptionalResults}).
 *         <li>An {@code @Inject} handler written for a method whose parameters the patch only added to, or reordered so
 *             that each type stands once, stays as a plain method, and a bridge with the patched parameters carries the
 *             injection and passes the original ones on ({@link Bridge}).
 *         <li>An accessor or method the patch added with the same code is left to the patched class ({@link
 *             HostAddedMembers}); a local an injection captures by name that the patch renamed is captured under its
 *             patched name and type ({@link HostRenamedLocals}).
 *         <li>An {@code @Inject} into a method that a subclass's override reaches only in the patched game (vanilla's
 *             override does not call {@code super}, the patched one does) keeps to the receivers it has in vanilla
 *             ({@link Receivers}): NeoForge's {@code ServerGamePacketListenerImpl.handleCustomPayload} calls {@code
 *             super}, where Fabric API expects only the configuration listener (ADR-0026 41).
 *       </ul>
 *   <li>The mixin cannot work as written on the patched game ({@link Finding#incompatible()}, {@code PLEXUS-COMPAT-224}):
 *       a member it uses is gone, an injection point is gone with no replacement, the handler's parameters cannot match
 *       the patched method, it would replace what the host's patch computes, an injection depends on one that cannot
 *       run, a default method or member collides with the host's. Plexus Mod Loader does not run such a mod with that
 *       part left out: the runtime does not load it, says so where the player sees it, and names Plexus Mod Loader, not
 *       the mod, as the one to fix it.
 * </ul>
 *
 * <p>The mods' injectors keep their own {@code require} and local capture settings, as under Fabric Loader: an
 * injection that the comparison could not foresee (expressions, slices) and that finds nothing in the patched class
 * stops the game with Mixin's error, which {@link HostedMixinErrors} reports as Plexus Mod Loader's ({@code
 * PLEXUS-COMPAT-225}). Mixin reads the changed classes through {@link #apply}. Without a host nothing changes: Fabric mods
 * run against the vanilla game as under Fabric Loader.
 */
final class HostPatchAdjustments {

    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/Mixin;";
    static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";
    static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";
    static final String ACCESSOR = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    static final String INVOKER = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_ARG = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String MODIFY_ARGS = "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;";
    private static final String MODIFY_EXPRESSION_VALUE = "Lcom/llamalad7/mixinextras/injector/ModifyExpressionValue;";
    private static final String AT = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String SLICE = "Lorg/spongepowered/asm/mixin/injection/Slice;";
    static final Set<String> INJECTORS = Set.of(
            INJECT,
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArg;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyArgs;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;",
            "Lorg/spongepowered/asm/mixin/injection/ModifyConstant;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
            MODIFY_EXPRESSION_VALUE,
            "Lcom/llamalad7/mixinextras/injector/ModifyReturnValue;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;",
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;");
    private static final Set<String> CHECKED_POINTS = Set.of("INVOKE", "INVOKE_ASSIGN", "FIELD", "NEW");
    /** Injectors whose handler takes or changes the parameters of the call they target. */
    private static final Set<String> CALL_SIGNATURE_INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            MODIFY_ARG,
            MODIFY_ARGS,
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;");
    /**
     * Injectors whose handler takes the receiver of the call or field access they target when it has one: their
     * handler does not fit when the patch makes that call or access static, or makes a static one an instance one.
     */
    private static final Set<String> RECEIVER_INJECTORS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/Redirect;",
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;",
            "Lcom/llamalad7/mixinextras/injector/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/v2/WrapWithCondition;",
            "Lcom/llamalad7/mixinextras/injector/ModifyReceiver;");

    /**
     * One difference between a mixin and the host's patched class: {@code code} is its error code, {@code leftOut}
     * whether Mixin could not apply the mixin at all, {@code incompatible} whether the mixin cannot work as written on
     * the patched game, so that the mod is not loaded ({@code PLEXUS-COMPAT-224}).
     */
    record Finding(
            String modId,
            String config,
            String mixin,
            String reason,
            boolean leftOut,
            String code,
            boolean incompatible) {

        /** A {@code PLEXUS-COMPAT-224} finding: the mixin cannot work as written, so the mod is not loaded. */
        static Finding incompatible(String modId, String config, String mixin, String reason, boolean leftOut) {
            return new Finding(modId, config, mixin, reason, leftOut, "PLEXUS-COMPAT-224", true);
        }

        /** A {@code PLEXUS-COMPAT-226} finding: Plexus Mod Loader moved the mixin to the patch, and it works as written. */
        static Finding adapted(String modId, String config, String mixin, String reason) {
            return new Finding(modId, config, mixin, reason, false, "PLEXUS-COMPAT-226", false);
        }
    }

    /** An {@code @At} target in one handler's injector that moves to the host's replacement. */
    private record Retarget(String handler, String descriptor, String replaced, String replacement) {}

    /** A {@code method} selector in one handler's injector that moves to the method where the patch placed the code. */
    private record MethodRetarget(String handler, String descriptor, String selector, String replacement) {}

    /**
     * A {@code @Slice} in one handler's injector whose target call on a receiver field the patch moved before the
     * other call's receiver field {@code toField}: the slice's boundary becomes {@code to = @At(value = "FIELD",
     * target = toField, opcode = GETFIELD)}.
     */
    private record SliceRetarget(String handler, String descriptor, String toField) {}

    /**
     * A {@code @Redirect} handler ({@code handler} {@code descriptor}) of a unary call {@code (P0)P0} that mutates its
     * argument in place before returning a copy, where the host's patch replaced that call with a {@code void} hook
     * {@code replacement} taking {@code P0} first ({@code Language.loadDefault}'s {@code Map.copyOf(map)} replaced by
     * {@code LanguageHook.captureLanguageMap(map, ...)}): a {@code @ModifyArg(index = 0)} bridge on {@code replacement}
     * calls the handler to populate {@code P0}, discards the returned copy, and passes the mutable {@code P0} on.
     */
    private record InPlaceRedirect(String handler, String descriptor, String replacement) {}

    /**
     * An {@code @Inject} handler ({@code handler} {@code descriptor}) whose target method the patch gave more
     * parameters: the bridge {@code bridgeDescriptor} takes the patched method's parameters followed by the handler's
     * own {@code CallbackInfo} and captured locals, and passes on the ones the handler was written for. {@code
     * positions} holds, for each target parameter of the handler, its index among the patched method's parameters.
     */
    private record Bridge(String handler, String descriptor, String bridgeDescriptor, List<Integer> positions) {}

    /** An injection whose handler cannot match the patched method, or that would undo the host's patch. */
    private record TakenOut(String handler, String descriptor) {}

    /**
     * A {@code @WrapOperation} handler ({@code handler} {@code descriptor}) of the call {@code replaced}, which the patch
     * makes as {@code replacement}: the same method with more parameters at the end (ADR-0026 40). A bridge wraps {@code
     * replacement}, takes its arguments, and calls the handler with the vanilla ones and an {@link AppendedArguments}
     * that passes the added ones on.
     */
    private record Appended(String handler, String descriptor, String replaced, String replacement) {}

    /**
     * An {@code @Inject} handler ({@code handler} {@code descriptor}) before a call the patch moved into a helper that
     * makes it exactly when it returns true ({@link InjectionPointReplacement.Placement#AFTER_TRUE_RESULT}): a MixinExtras
     * {@code @WrapOperation} bridge on each call {@code helperCall} in {@code method} ({@code methodDescriptor}) runs the
     * helper and, when it returned true, the handler. {@code helperStatic} says whether the helper is static, {@code
     * targetParameters} whether the handler takes {@code method}'s parameters, and {@code callbackInfo} the descriptor of
     * its {@code CallbackInfo} parameter.
     */
    private record Wrap(
            String handler,
            String descriptor,
            String method,
            String methodDescriptor,
            String helperCall,
            boolean helperStatic,
            boolean targetParameters,
            String callbackInfo) {}

    /**
     * A default method {@code name} {@code descriptor} that both an interface the mixin adds and the host's interface
     * {@code hostInterface} (a direct interface of the target class) give the target class, which does not declare it:
     * the mixin gets a public method that calls {@code hostInterface}'s.
     */
    private record HostDefault(String name, String descriptor, String hostInterface, boolean sameCode) {}

    /**
     * An {@code @Inject} handler ({@code handler} {@code descriptor}) into a method that the patched overrides in {@code
     * excluded} (internal names) reach through {@code super}, where vanilla's overrides do not: a guard method carries
     * the injection, returns for a receiver of one of those classes, and calls the handler for the others (ADR-0026 41).
     */
    private record Receivers(String handler, String descriptor, List<String> excluded) {}

    /**
     * A bed sleep event injection in {@code LivingEntityMixin} adapted for Forge's bed patches via {@code @WrapOperation}.
     */
    private record BedWrap(
            String handler,
            String descriptor,
            String targetMethod,
            String targetMethodDescriptor,
            String targetCall,
            Kind kind) {
        enum Kind {
            DIRECTION,
            OCCUPIED
        }
    }

    /**
     * A {@code @WrapOperation} of vanilla's {@code FluidState.is(FluidTags.WATER)} that Forge replaced with a call
     * returning the same answer from its fluid types; the bridge makes Forge's call and runs the handler on its result
     * ({@link #fluidWrapMethod}). BOAT_SUPPORT: the fluid state is Forge's argument; SWIMMING: the bridge reads the same
     * fluid state vanilla reads ({@code level().getFluidState(blockPosition())}).
     */
    private record FluidWrap(
            String handler,
            String descriptor,
            String targetMethod,
            String targetMethodDescriptor,
            String targetCall,
            Kind kind) {
        enum Kind {
            BOAT_SUPPORT,
            SWIMMING
        }
    }

    /**
     * An item injection at a call Forge replaced. The remainder kinds are kept only when the handler makes no call but
     * {@code ItemStack.getCraftingRemainder()} on the stack Forge calls it on ({@link #onlyStackRemainder}), which is
     * Forge's own call; ENCHANTMENT_CHECK only when the handler is Fabric's {@code canBeEnchantedWith} check
     * ({@link #onlyFabricEnchantingCheck}).
     */
    private record ItemWrap(
            String handler,
            String descriptor,
            String targetMethod,
            String targetMethodDescriptor,
            String targetCall,
            Kind kind) {
        enum Kind {
            FURNACE_REMAINDER,
            BREWING_REMAINDER,
            CRAFTING_CAPTURE,
            CRAFTING_REMAINDER,
            ENCHANTMENT_CHECK
        }
    }

    /**
     * A dynamic registries lookup injection adapted for Forge's worldgen stream via {@code @WrapOperation}.
     */
    private record RegistrySyncWrap(
            String handler, String descriptor, String targetMethod, String targetMethodDescriptor, String targetCall) {}

    /**
     * A {@code @WrapOperation} handler ({@code handler} {@code descriptor}) of a call whose {@code Either} result the
     * patch reads with an {@code Optional} on the left side where vanilla has the value: a bridge hands the handler the
     * value and wraps its result again ({@link HostOptionalResults}).
     */
    private record OptionalLeft(String handler, String descriptor) {}

    /**
     * A {@code @Redirect} handler ({@code handler} {@code descriptor}) of a static {@code List} field read {@code
     * replaced} that the patch replaced with the call {@code replacement}, which returns the field's elements followed
     * by the host's own: a bridge at {@code replacement} returns the handler's list followed by the host's own elements
     * ({@link HostListExtensions}).
     */
    private record HostList(String handler, String descriptor, String replaced, String replacement) {}

    /**
     * A {@code @WrapOperation} handler ({@code handler} {@code descriptor}) of a call {@code replaced} that the patch
     * replaced at the same place with {@code replacement} on the same receiver: a bridge at {@code replacement} hands the
     * handler the host's arguments and result ({@link HostReplacedCalls}).
     */
    private record ReplacedCall(String handler, String descriptor, String replaced, String replacement) {}

    private static final String BRIDGE_PREFIX = "plexus$hostParameters$";
    private static final String APPENDED_PREFIX = "plexus$hostArguments$";
    private static final ClassDesc APPENDED_ARGUMENTS = ClassDesc.of(AppendedArguments.class.getName());
    private static final String WRAP_PREFIX = "plexus$hostCall$";
    private static final String HOST_MAKES_HANDLER_CALL =
            " there; the redirect only calls ItemStack.getCraftingRemainder() on that stack, matching Forge's call, so dropping the handler preserves behavior";
    private static final ClassDesc FLUID_STATE = ClassDesc.of("net.minecraft.world.level.material.FluidState");
    private static final ClassDesc TAG_KEY = ClassDesc.of("net.minecraft.tags.TagKey");
    private static final ClassDesc ITEM_STACK = ClassDesc.of("net.minecraft.world.item.ItemStack");
    private static final ClassDesc LOCAL_REF = ClassDesc.of("com.llamalad7.mixinextras.sugar.ref.LocalRef");
    private static final ClassDesc ENTITY = ClassDesc.of("net.minecraft.world.entity.Entity");
    private static final ClassDesc LEVEL = ClassDesc.of("net.minecraft.world.level.Level");
    private static final ClassDesc BLOCK_POS = ClassDesc.of("net.minecraft.core.BlockPos");
    private static final ClassDesc FLUID_TAGS = ClassDesc.of("net.minecraft.tags.FluidTags");
    private static final ClassDesc HOLDER = ClassDesc.of("net.minecraft.core.Holder");
    private static final ClassDesc ENCHANTMENT = ClassDesc.of("net.minecraft.world.item.enchantment.Enchantment");
    private static final ClassDesc ENCHANTING_CONTEXT =
            ClassDesc.of("net.fabricmc.fabric.api.item.v1.EnchantingContext");
    private static final String CAN_BE_ENCHANTED_WITH =
            "(Lnet/minecraft/core/Holder;Lnet/fabricmc/fabric/api/item/v1/EnchantingContext;)Z";
    private static final ClassDesc PRECOMPUTED_CALL = ClassDesc.of(PrecomputedCall.class.getName());
    private static final String REDIRECT_PREFIX = "plexus$hostRedirect$";
    private static final String RECEIVERS_PREFIX = "plexus$receivers$";
    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final ClassDesc OPERATION =
            ClassDesc.ofDescriptor("Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;");
    private static final String SUGAR_PACKAGE = "Lcom/llamalad7/mixinextras/sugar/";
    private static final Set<String> CALLBACK_INFOS = Set.of(
            "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;",
            "Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable;");

    static final HostPatchAdjustments NONE = new HostPatchAdjustments(
            Set.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            HostAddedMembers.NONE,
            HostRenamedLocals.NONE);

    /** Every mixin class of the checked configurations (internal names). */
    private final Set<String> mixins;

    private final Map<String, List<Retarget>> retargets;
    private final Map<String, List<MethodRetarget>> methodRetargets;
    private final Map<String, List<SliceRetarget>> sliceRetargets;
    private final Map<String, List<InPlaceRedirect>> inPlaceRedirects;
    private final Map<String, List<Bridge>> bridges;
    private final Map<String, List<Wrap>> wraps;
    private final Map<String, List<Appended>> appended;
    private final Map<String, List<HostDefault>> hostDefaults;
    private final Map<String, List<Receivers>> receivers;
    private final Map<String, List<BedWrap>> bedWraps;
    private final Map<String, List<FluidWrap>> fluidWraps;
    private final Map<String, List<ItemWrap>> itemWraps;
    private final Map<String, List<RegistrySyncWrap>> registrySyncWraps;
    private final Map<String, List<OptionalLeft>> optionalLefts;
    private final Map<String, List<HostList>> hostLists;
    private final Map<String, List<ReplacedCall>> replacedCalls;

    private final List<Finding> findings;

    /** Mixin members that meet a member the patch added or changed ({@link HostAddedMembers}). */
    private final HostAddedMembers members;

    /** {@code @Local} captures of locals the patch renamed ({@link HostRenamedLocals}). */
    private final HostRenamedLocals locals;

    private HostPatchAdjustments(
            Set<String> mixins,
            Map<String, List<Retarget>> retargets,
            Map<String, List<MethodRetarget>> methodRetargets,
            Map<String, List<SliceRetarget>> sliceRetargets,
            Map<String, List<InPlaceRedirect>> inPlaceRedirects,
            Map<String, List<Bridge>> bridges,
            Map<String, List<Wrap>> wraps,
            Map<String, List<Appended>> appended,
            Map<String, List<HostDefault>> hostDefaults,
            Map<String, List<Receivers>> receivers,
            Map<String, List<BedWrap>> bedWraps,
            Map<String, List<FluidWrap>> fluidWraps,
            Map<String, List<ItemWrap>> itemWraps,
            Map<String, List<RegistrySyncWrap>> registrySyncWraps,
            Map<String, List<OptionalLeft>> optionalLefts,
            Map<String, List<HostList>> hostLists,
            Map<String, List<ReplacedCall>> replacedCalls,
            List<Finding> findings,
            HostAddedMembers members,
            HostRenamedLocals locals) {
        this.mixins = mixins;
        this.retargets = retargets;
        this.methodRetargets = methodRetargets;
        this.sliceRetargets = sliceRetargets;
        this.inPlaceRedirects = inPlaceRedirects;
        this.bridges = bridges;
        this.wraps = wraps;
        this.appended = appended;
        this.hostDefaults = hostDefaults;
        this.receivers = receivers;
        this.bedWraps = bedWraps;
        this.fluidWraps = fluidWraps;
        this.itemWraps = itemWraps;
        this.registrySyncWraps = registrySyncWraps;
        this.optionalLefts = optionalLefts;
        this.hostLists = hostLists;
        this.replacedCalls = replacedCalls;
        this.findings = findings;
        this.members = members;
        this.locals = locals;
    }

    List<Finding> findings() {
        return findings;
    }

    /** Whether a mixin of the checked mods cannot work as written on the patched game ({@code PLEXUS-COMPAT-224}). */
    boolean hasIncompatible() {
        return findings.stream().anyMatch(Finding::incompatible);
    }

    /**
     * Compares the mixins of the configurations in {@code configToMod} (read from each mod's jar in {@code modJars}) with
     * {@code vanilla} and {@code patched}, the game's classes before and after {@code host}'s patch.
     */
    static HostPatchAdjustments plan(
            HostedLaunch host,
            GameSide side,
            Map<String, String> configToMod,
            Map<String, Path> modJars,
            Function<String, Optional<byte[]>> vanilla,
            GameClassBytes patched) {
        if (!host.isHosted() || configToMod.isEmpty()) {
            return NONE;
        }
        Classes classes = new Classes(vanilla, patched);
        Set<String> mixins = new HashSet<>();
        Map<String, List<Retarget>> retargets = new HashMap<>();
        Map<String, List<MethodRetarget>> methodRetargets = new HashMap<>();
        Map<String, List<SliceRetarget>> sliceRetargets = new HashMap<>();
        Map<String, List<InPlaceRedirect>> inPlaceRedirects = new HashMap<>();
        Map<String, List<Bridge>> bridges = new HashMap<>();
        Map<String, List<Wrap>> wraps = new HashMap<>();
        Map<String, List<Appended>> appended = new HashMap<>();
        Map<String, List<HostDefault>> hostDefaults = new HashMap<>();
        Map<String, List<Receivers>> receivers = new HashMap<>();
        Map<String, List<BedWrap>> bedWraps = new HashMap<>();
        Map<String, List<FluidWrap>> fluidWraps = new HashMap<>();
        Map<String, List<ItemWrap>> itemWraps = new HashMap<>();
        Map<String, List<RegistrySyncWrap>> registrySyncWraps = new HashMap<>();
        Map<String, List<OptionalLeft>> optionalLefts = new HashMap<>();
        Map<String, List<HostList>> hostLists = new HashMap<>();
        Map<String, List<ReplacedCall>> replacedCalls = new HashMap<>();
        List<Finding> findings = new ArrayList<>();
        HostAddedMembers.Planner members = new HostAddedMembers.Planner(host, classes);
        HostRenamedLocals.Planner locals = new HostRenamedLocals.Planner(host, classes);
        // Types that left-out mixins would have given the game (their interfaces; accessor mixins themselves), and the
        // mixins that stay, for the injections that use those types (ADR-0026 39) and for coupled state (ADR-0026 32).
        Map<String, String> leftOutTypes = new LinkedHashMap<>();
        List<Checked> checked = new ArrayList<>();
        // Every class a mixin targets: the subclasses whose patched overrides newly call super (ADR-0026 41).
        Set<String> targets = new TreeSet<>();
        for (Map.Entry<String, String> entry : configToMod.entrySet()) {
            String config = entry.getKey();
            String modId = entry.getValue();
            Path jar = modJars.get(modId);
            if (jar == null) {
                continue;
            }
            try (JarFile file = new JarFile(jar.toFile())) {
                for (Map.Entry<String, String> mixin :
                        mixinClasses(file, config, side).entrySet()) {
                    byte[] bytes = read(file, mixin.getValue() + ".class");
                    if (bytes == null) {
                        continue;
                    }
                    mixins.add(mixin.getValue());
                    ClassModel model = ClassFile.of().parse(bytes);
                    targets.addAll(mixinTargets(model));
                    Optional<String> missing = missingMember(model, host, classes);
                    if (missing.isPresent()) {
                        findings.add(Finding.incompatible(modId, config, mixin.getValue(), missing.get(), true));
                        addedTypes(model, leftOutTypes);
                        continue;
                    }
                    List<Finding> memberFindings =
                            members.check(modId, config, model, overwriteNeedsAnnotation(file, config));
                    if (memberFindings.stream().anyMatch(Finding::leftOut)) {
                        findings.addAll(memberFindings);
                        addedTypes(model, leftOutTypes);
                        continue;
                    }
                    findings.addAll(memberFindings);
                    List<Finding> localFindings = locals.check(modId, config, model);
                    Set<String> notRunning = new HashSet<>();
                    Set<String> adjusted = new HashSet<>();
                    List<Injection> injections = injections(model, host, classes, notRunning, adjusted);
                    // An injection already reported as not running is not reported again for its @Local.
                    for (Finding local : localFindings) {
                        String where =
                                local.reason().substring(0, local.reason().indexOf(": "));
                        if (!local.incompatible()
                                || injections.stream()
                                        .noneMatch(injection -> injection.doesNotRun()
                                                && (injection.reason().startsWith(where + " ")
                                                        || injection.reason().startsWith(where + ":")))) {
                            findings.add(local);
                        }
                    }
                    for (Injection injection : injections) {
                        if (injection.retarget() != null) {
                            retargets
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.retarget());
                        }
                        if (injection.methodRetarget() != null) {
                            methodRetargets
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.methodRetarget());
                        }
                        if (injection.sliceRetarget() != null) {
                            sliceRetargets
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.sliceRetarget());
                        }
                        if (injection.inPlaceRedirect() != null) {
                            inPlaceRedirects
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.inPlaceRedirect());
                        }
                        if (injection.bridge() != null) {
                            bridges.computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.bridge());
                        }
                        if (injection.wrap() != null) {
                            wraps.computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.wrap());
                        }
                        if (injection.appended() != null) {
                            appended.computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.appended());
                        }
                        if (injection.bedWrap() != null) {
                            bedWraps.computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.bedWrap());
                        }
                        if (injection.fluidWrap() != null) {
                            fluidWraps
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.fluidWrap());
                        }
                        if (injection.itemWrap() != null) {
                            itemWraps
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.itemWrap());
                        }
                        if (injection.registrySyncWrap() != null) {
                            registrySyncWraps
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.registrySyncWrap());
                        }
                        if (injection.optionalLeft() != null) {
                            optionalLefts
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.optionalLeft());
                        }
                        if (injection.hostList() != null) {
                            hostLists
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.hostList());
                        }
                        if (injection.replacedCall() != null) {
                            replacedCalls
                                    .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                    .add(injection.replacedCall());
                            ReplacedCall dropped = injection.replacedCall();
                            if (dropped.replaced().equals(HostConditionalSkip.SKIP)) {
                                String companion = dropped.replacement();
                                int open = companion.indexOf('(');
                                int hash = companion.lastIndexOf('#');
                                replacedCalls
                                        .get(mixin.getValue())
                                        .add(new ReplacedCall(
                                                companion.substring(0, open),
                                                companion.substring(open, hash),
                                                HostConditionalSkip.COMPANION,
                                                companion.substring(hash + 1)));
                            }
                        }
                        findings.add(
                                injection.doesNotRun()
                                        ? Finding.incompatible(
                                                modId, config, mixin.getValue(), injection.reason(), false)
                                        : Finding.adapted(modId, config, mixin.getValue(), injection.reason()));
                    }
                    checked.add(new Checked(modId, config, mixin.getValue(), model, notRunning, adjusted));
                    for (HostDefault conflict : conflictingDefaults(model, file, classes)) {
                        hostDefaults
                                .computeIfAbsent(mixin.getValue(), name -> new ArrayList<>())
                                .add(conflict);
                        if (conflict.sameCode()) {
                            findings.add(Finding.adapted(
                                    modId,
                                    config,
                                    mixin.getValue(),
                                    "it adds an interface whose default method " + conflict.name()
                                            + conflict.descriptor() + " " + host.hostName() + "'s "
                                            + conflict.hostInterface() + " gives "
                                            + String.join(", ", mixinTargets(model))
                                            + " too; the class keeps " + conflict.hostInterface()
                                            + "'s (overrides still win)"));
                        } else {
                            findings.add(Finding.incompatible(
                                    modId,
                                    config,
                                    mixin.getValue(),
                                    "it adds an interface whose default method " + conflict.name()
                                            + conflict.descriptor() + " " + host.hostName() + "'s "
                                            + conflict.hostInterface() + " gives "
                                            + String.join(", ", mixinTargets(model))
                                            + " too; the JVM refuses every call of two such defaults, and only one of them"
                                            + " can run",
                                    false));
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to read " + jar + " for its Mixin configuration", e);
            }
        }
        for (Checked mixin : checked) {
            Set<String> handlers = handlers(mixin.model());
            // Injections that use a type a left-out mixin would have added do not run (ADR-0026 39), and injections
            // that read what a stopped injection of the same mixin sets stop too (ADR-0026 32).
            Map<String, String> stopped = new LinkedHashMap<>(
                    CoupledInjections.usingLeftOutTypes(mixin.model(), handlers, mixin.notRunning(), leftOutTypes));
            Set<String> notRunning = new HashSet<>(mixin.notRunning());
            notRunning.addAll(stopped.keySet());
            CoupledInjections.leftOut(mixin.model(), handlers, notRunning).forEach(stopped::putIfAbsent);
            stopped.forEach((handler, reason) ->
                    findings.add(Finding.incompatible(mixin.modId(), mixin.config(), mixin.name(), reason, false)));
            for (MethodModel handler : mixin.model().methods()) {
                String key = CoupledInjections.key(handler);
                if (mixin.adjusted().contains(key) || stopped.containsKey(key)) {
                    continue;
                }
                newReceivers(mixin.model(), handler, host, classes, targets).ifPresent(found -> {
                    receivers
                            .computeIfAbsent(mixin.name(), name -> new ArrayList<>())
                            .add(found.receivers());
                    findings.add(new Finding(
                            mixin.modId(),
                            mixin.config(),
                            mixin.name(),
                            found.reason(),
                            false,
                            "PLEXUS-COMPAT-222",
                            false));
                });
            }
        }
        return new HostPatchAdjustments(
                Set.copyOf(mixins),
                Map.copyOf(retargets),
                Map.copyOf(methodRetargets),
                Map.copyOf(sliceRetargets),
                Map.copyOf(inPlaceRedirects),
                Map.copyOf(bridges),
                Map.copyOf(wraps),
                Map.copyOf(appended),
                Map.copyOf(hostDefaults),
                Map.copyOf(receivers),
                Map.copyOf(bedWraps),
                Map.copyOf(fluidWraps),
                Map.copyOf(itemWraps),
                Map.copyOf(registrySyncWraps),
                Map.copyOf(optionalLefts),
                Map.copyOf(hostLists),
                Map.copyOf(replacedCalls),
                List.copyOf(findings),
                members.build(),
                locals.build());
    }

    /** A mixin that stays in its configuration, with the injections already known not to run. */
    private record Checked(
            String modId, String config, String name, ClassModel model, Set<String> notRunning, Set<String> adjusted) {}

    /**
     * The types a left-out mixin would have given the game, to how, into {@code types}: the interfaces it adds to its
     * targets, and the mixin itself when it is an interface (an accessor or invoker mixin, which the game's class
     * would implement).
     */
    private static void addedTypes(ClassModel mixin, Map<String, String> types) {
        String name = mixin.thisClass().asInternalName();
        String targets = String.join(", ", mixinTargets(mixin));
        for (ClassEntry added : mixin.interfaces()) {
            types.putIfAbsent(added.asInternalName(), "which the mixin " + name + " adds to " + targets);
        }
        if (mixin.flags().has(AccessFlag.INTERFACE)) {
            types.putIfAbsent(name, "the accessor mixin " + name + " of " + targets);
        }
    }

    /** {@code bytes} of class {@code className} as Mixin reads it: a checked mixin with its changes, others as they are. */
    byte[] apply(String className, byte[] bytes) {
        String internalName = className.replace('.', '/');
        if (!mixins.contains(internalName)) {
            return bytes;
        }
        bytes = members.apply(internalName, bytes);
        List<Retarget> own = retargets.getOrDefault(internalName, List.of());
        List<MethodRetarget> ownMethods = methodRetargets.getOrDefault(internalName, List.of());
        List<SliceRetarget> ownSlices = sliceRetargets.getOrDefault(internalName, List.of());
        List<InPlaceRedirect> ownRedirects = inPlaceRedirects.getOrDefault(internalName, List.of());
        List<Bridge> ownBridges = bridges.getOrDefault(internalName, List.of());
        List<Wrap> ownWraps = wraps.getOrDefault(internalName, List.of());
        List<Appended> ownAppended = appended.getOrDefault(internalName, List.of());
        List<Receivers> ownReceivers = receivers.getOrDefault(internalName, List.of());
        List<BedWrap> ownBedWraps = bedWraps.getOrDefault(internalName, List.of());
        List<FluidWrap> ownFluidWraps = fluidWraps.getOrDefault(internalName, List.of());
        List<ItemWrap> ownItemWraps = itemWraps.getOrDefault(internalName, List.of());
        List<RegistrySyncWrap> ownRegistrySyncWraps = registrySyncWraps.getOrDefault(internalName, List.of());
        List<OptionalLeft> ownOptionalLefts = optionalLefts.getOrDefault(internalName, List.of());
        List<HostList> ownHostLists = hostLists.getOrDefault(internalName, List.of());
        List<ReplacedCall> ownReplacedCalls = replacedCalls.getOrDefault(internalName, List.of());
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(bytes);
        ClassTransform transform = (builder, element) -> {
            if (element instanceof MethodModel method) {
                List<Retarget> handlers = own.stream()
                        .filter(retarget -> method.methodName().equalsString(retarget.handler())
                                && method.methodType().equalsString(retarget.descriptor()))
                        .toList();
                List<MethodRetarget> methodHandlers = ownMethods.stream()
                        .filter(retarget -> method.methodName().equalsString(retarget.handler())
                                && method.methodType().equalsString(retarget.descriptor()))
                        .toList();
                List<SliceRetarget> sliceHandlers = ownSlices.stream()
                        .filter(retarget -> method.methodName().equalsString(retarget.handler())
                                && method.methodType().equalsString(retarget.descriptor()))
                        .toList();
                Optional<InPlaceRedirect> inPlaceRedirect = ownRedirects.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<Bridge> bridge = ownBridges.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<Wrap> wrap = ownWraps.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<Appended> moved = ownAppended.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<Receivers> guarded = ownReceivers.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<BedWrap> bedWrap = ownBedWraps.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<FluidWrap> fluidWrap = ownFluidWraps.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<ItemWrap> itemWrap = ownItemWraps.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<RegistrySyncWrap> registrySyncWrap = ownRegistrySyncWraps.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<OptionalLeft> optionalLeft = ownOptionalLefts.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<HostList> hostList = ownHostLists.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                Optional<ReplacedCall> replacedCall = ownReplacedCalls.stream()
                        .filter(candidate -> method.methodName().equalsString(candidate.handler())
                                && method.methodType().equalsString(candidate.descriptor()))
                        .findFirst();
                boolean withoutInject = bridge.isPresent() || wrap.isPresent() || guarded.isPresent();
                builder.transformMethod(method, (methodBuilder, methodElement) -> {
                    if (methodElement instanceof RuntimeVisibleAnnotationsAttribute attribute) {
                        methodBuilder.with(RuntimeVisibleAnnotationsAttribute.of(attribute.annotations().stream()
                                .filter(annotation -> !(withoutInject
                                                && annotation.className().equalsString(INJECT))
                                        && !((moved.isPresent() || optionalLeft.isPresent() || replacedCall.isPresent())
                                                && annotation.className().equalsString(WRAP_OPERATION))
                                        && !(replacedCall.isPresent()
                                                && annotation.className().equalsString(INJECT))
                                        && !(replacedCall.isPresent()
                                                && annotation.className().equalsString(MODIFY_ARG))
                                        && !((inPlaceRedirect.isPresent() || hostList.isPresent())
                                                && annotation.className().equalsString(REDIRECT))
                                        && !(bedWrap.isPresent()
                                                && (annotation.className().equalsString(WRAP_OPERATION)
                                                        || annotation
                                                                .className()
                                                                .equalsString(REDIRECT)))
                                        && !(fluidWrap.isPresent()
                                                && annotation.className().equalsString(WRAP_OPERATION))
                                        && !(itemWrap.isPresent()
                                                && (annotation.className().equalsString(WRAP_OPERATION)
                                                        || annotation
                                                                .className()
                                                                .equalsString(REDIRECT)))
                                        && !(registrySyncWrap.isPresent()
                                                && annotation.className().equalsString(REDIRECT)))
                                .map(annotation -> adjust(annotation, handlers, methodHandlers, sliceHandlers))
                                .toList()));
                    } else {
                        methodBuilder.with(methodElement);
                    }
                });
                bridge.ifPresent(present -> {
                    Annotation inject = annotation(method.attributes(), INJECT).orElseThrow();
                    bridgeMethod(
                            builder, model, method, present, adjust(inject, handlers, methodHandlers, sliceHandlers));
                });
                wrap.ifPresent(present -> wrapMethod(builder, model, method, present));
                guarded.ifPresent(present -> {
                    Annotation inject = annotation(method.attributes(), INJECT).orElseThrow();
                    receiversMethod(
                            builder, model, method, present, adjust(inject, handlers, methodHandlers, sliceHandlers));
                });
                inPlaceRedirect.ifPresent(present -> {
                    Annotation redirect =
                            annotation(method.attributes(), REDIRECT).orElseThrow();
                    inPlaceRedirectMethod(
                            builder, model, method, present, adjust(redirect, handlers, methodHandlers, sliceHandlers));
                });
                moved.ifPresent(present -> {
                    Annotation wrapOperation =
                            annotation(method.attributes(), WRAP_OPERATION).orElseThrow();
                    appendedMethod(
                            builder,
                            model,
                            method,
                            present,
                            adjust(
                                    wrapOperation,
                                    List.of(new Retarget(
                                            present.handler(),
                                            present.descriptor(),
                                            present.replaced(),
                                            present.replacement())),
                                    methodHandlers,
                                    sliceHandlers));
                });
                bedWrap.ifPresent(present -> bedWrapMethod(builder, model, method, present));
                fluidWrap.ifPresent(present -> fluidWrapMethod(builder, model, method, present));
                itemWrap.ifPresent(present -> itemWrapMethod(builder, method, present));
                registrySyncWrap.ifPresent(present -> registrySyncWrapMethod(builder, model, method, present));
                optionalLeft.ifPresent(present -> HostOptionalResults.bridgeMethod(
                        builder,
                        model,
                        method,
                        adjust(
                                annotation(method.attributes(), WRAP_OPERATION).orElseThrow(),
                                handlers,
                                methodHandlers,
                                sliceHandlers)));
                hostList.ifPresent(present -> HostListExtensions.bridgeMethod(
                        builder,
                        model,
                        method,
                        adjust(
                                annotation(method.attributes(), REDIRECT).orElseThrow(),
                                handlers,
                                methodHandlers,
                                sliceHandlers),
                        present.replaced(),
                        present.replacement()));
                replacedCall.ifPresent(present -> {
                    if (present.replaced().equals(HostConditionalSkip.SKIP)) {
                        return;
                    }
                    if (present.replaced().equals(HostConditionalSkip.COMPANION)) {
                        HostConditionalSkip.companionBridge(
                                builder,
                                model,
                                method,
                                adjust(
                                        annotation(method.attributes(), WRAP_OPERATION)
                                                .orElseThrow(),
                                        handlers,
                                        methodHandlers,
                                        sliceHandlers),
                                present.replacement());
                        return;
                    }
                    if (HostThreadDump.covers(present.replaced(), present.replacement())) {
                        HostThreadDump.bridgeMethod(builder, model, method);
                        return;
                    }
                    if (HostHopperTransfer.covers(present.replaced(), present.replacement())) {
                        HostHopperTransfer.bridgeMethod(
                                builder,
                                model,
                                method,
                                adjust(
                                        annotation(method.attributes(), INJECT).orElseThrow(),
                                        handlers,
                                        methodHandlers,
                                        sliceHandlers),
                                present.replaced(),
                                present.replacement());
                        return;
                    }
                    Annotation wrapOperation = adjust(
                            annotation(method.attributes(), WRAP_OPERATION).orElseThrow(),
                            handlers,
                            methodHandlers,
                            sliceHandlers);
                    if (HostEffectRemoval.covers(present.replaced(), present.replacement())) {
                        HostEffectRemoval.bridgeMethod(builder, model, method, wrapOperation);
                    } else {
                        HostReplacedCalls.bridgeMethod(
                                builder, model, method, wrapOperation, present.replaced(), present.replacement());
                    }
                });
            } else {
                builder.with(element);
            }
        };
        List<HostDefault> ownDefaults = hostDefaults.getOrDefault(internalName, List.of());
        if (!ownDefaults.isEmpty()) {
            transform = transform.andThen(ClassTransform.endHandler(builder -> {
                for (HostDefault conflict : ownDefaults) {
                    hostDefaultMethod(builder, conflict);
                }
            }));
        }
        return locals.apply(internalName, classFile.transformClass(model, transform));
    }

    /** {@code conflict}'s method: public, it calls {@code hostInterface}'s default with the same arguments. */
    private static void hostDefaultMethod(java.lang.classfile.ClassBuilder builder, HostDefault conflict) {
        MethodTypeDesc type = MethodTypeDesc.ofDescriptor(conflict.descriptor());
        ClassDesc hostInterface = ClassDesc.ofInternalName(conflict.hostInterface());
        builder.withMethodBody(conflict.name(), type, ClassFile.ACC_PUBLIC, code -> {
            code.aload(0);
            int slot = 1;
            for (ClassDesc parameter : type.parameterList()) {
                code.loadLocal(TypeKind.from(parameter), slot);
                slot += TypeKind.from(parameter).slotSize();
            }
            code.invokespecial(hostInterface, conflict.name(), type, true);
            code.return_(TypeKind.from(type.returnType()));
        });
    }

    /**
     * Whether two default methods make the same calls in the same order on the same receiver ({@link #normalized}):
     * keeping the host's then runs the mod's code as written (MinecraftForge's and Fabric API's {@code
     * getCraftingRemainder} on {@code Item} and {@code ItemStack}).
     */
    static boolean sameCode(Optional<MethodModel> mine, Optional<MethodModel> host) {
        if (mine.isEmpty() || host.isEmpty()) {
            return false;
        }
        Optional<List<String>> left = normalized(mine.get());
        return left.isPresent() && left.equals(normalized(host.get()));
    }

    /**
     * A straight-line default method's instructions as text, with the interface's own receiver ({@code this} cast to the
     * class, or the extension interfaces' {@code self()}) as {@code this}; empty for code with a branch or a switch.
     */
    private static Optional<List<String>> normalized(MethodModel method) {
        List<Instruction> code = instructions(method);
        List<String> text = new ArrayList<>();
        for (int index = 0; index < code.size(); index++) {
            Instruction instruction = code.get(index);
            Instruction next = index + 1 < code.size() ? code.get(index + 1) : null;
            if (instruction instanceof LoadInstruction load && load.slot() == 0) {
                if (next instanceof TypeCheckInstruction check && check.opcode() == Opcode.CHECKCAST) {
                    text.add("this " + check.type().asSymbol().descriptorString());
                    index++;
                    continue;
                }
                if (next instanceof InvokeInstruction self
                        && self.opcode() == Opcode.INVOKEINTERFACE
                        && self.name().equalsString("self")
                        && self.typeSymbol().parameterCount() == 0) {
                    text.add("this " + self.typeSymbol().returnType().descriptorString());
                    index++;
                    continue;
                }
            }
            if (instruction instanceof BranchInstruction
                    || instruction instanceof TableSwitchInstruction
                    || instruction instanceof LookupSwitchInstruction) {
                return Optional.empty();
            }
            text.add(
                    switch (instruction) {
                        case InvokeInstruction invoke ->
                            invoke.opcode() + " " + invoke.owner().asInternalName() + "."
                                    + invoke.name().stringValue()
                                    + invoke.typeSymbol().descriptorString();
                        case FieldInstruction field ->
                            field.opcode() + " " + field.owner().asInternalName() + "."
                                    + field.name().stringValue();
                        case TypeCheckInstruction check ->
                            check.opcode() + " " + check.type().asInternalName();
                        case LoadInstruction load -> load.opcode() + " " + load.slot();
                        case StoreInstruction store -> store.opcode() + " " + store.slot();
                        case ConstantInstruction constant -> constant.opcode() + " " + constant.constantValue();
                        default -> instruction.opcode().toString();
                    });
        }
        return Optional.of(text);
    }

    /** The method {@code method} (name and descriptor) that {@code owner} declares in {@code classes}, if any. */
    private static Optional<MethodModel> declaredMethod(
            Function<String, Optional<ClassModel>> classes, String owner, String method) {
        return classes.apply(owner)
                .flatMap(model -> model.methods().stream()
                        .filter(candidate ->
                                method.equals(candidate.methodName().stringValue()
                                        + candidate.methodType().stringValue()))
                        .findFirst());
    }

    /**
     * The default methods that an interface the mixin adds (read from the mod's jar, or the game's classes) and an
     * interface the host's patch gives a target class both give that class, which declares neither it nor the mixin.
     * The JVM throws {@code IncompatibleClassChangeError} ("Conflicting default methods") on every call of such a method.
     */
    private static List<HostDefault> conflictingDefaults(ClassModel mixin, JarFile jar, Classes classes) {
        if (mixin.interfaces().isEmpty()) {
            return List.of();
        }
        Function<String, Optional<ClassModel>> modClass = name -> {
            try {
                byte[] bytes = read(jar, name + ".class");
                return bytes != null ? Optional.of(ClassFile.of().parse(bytes)) : classes.patched(name);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        Map<String, String> added = new LinkedHashMap<>();
        for (var type : mixin.interfaces()) {
            defaults(type.asInternalName(), modClass, new HashSet<>()).forEach(added::putIfAbsent);
        }
        Set<String> declared = new HashSet<>();
        mixin.methods()
                .forEach(method -> declared.add(
                        method.methodName().stringValue() + method.methodType().stringValue()));
        List<HostDefault> conflicts = new ArrayList<>();
        for (String targetName : mixinTargets(mixin)) {
            Optional<ClassModel> target = classes.patched(targetName);
            if (target.isEmpty() || target.get().flags().has(AccessFlag.INTERFACE)) {
                continue;
            }
            // A method of the class or a superclass wins over every default.
            Optional<ClassModel> type = target;
            for (int depth = 0; type.isPresent() && depth < 32; depth++) {
                type.get()
                        .methods()
                        .forEach(method -> declared.add(method.methodName().stringValue()
                                + method.methodType().stringValue()));
                type = type.get().superclass().flatMap(parent -> classes.patched(parent.asInternalName()));
            }
            // Only an interface the host's patch added: a conflict with a vanilla one is the mod's own.
            Set<String> vanillaInterfaces = new HashSet<>();
            classes.vanilla(targetName)
                    .ifPresent(vanilla ->
                            vanilla.interfaces().forEach(face -> vanillaInterfaces.add(face.asInternalName())));
            for (var hostInterface : target.get().interfaces()) {
                String hostName = hostInterface.asInternalName();
                if (added.containsValue(hostName)) {
                    continue;
                }
                Map<String, String> hostDefaults = defaults(hostName, classes::patched, new HashSet<>());
                if (vanillaInterfaces.contains(hostName)) {
                    // A vanilla interface: only the defaults the patch added to it or its superinterfaces (NeoForge's
                    // ItemInstanceExtension under ItemInstance).
                    defaults(hostName, classes::vanilla, new HashSet<>())
                            .keySet()
                            .forEach(hostDefaults::remove);
                }
                for (Map.Entry<String, String> hostDefault : hostDefaults.entrySet()) {
                    String method = hostDefault.getKey();
                    String mine = added.get(method);
                    // A mod interface that extends the host's interface overrides its default: the JVM selects the
                    // mod's, the more specific one (Forgified Fabric API's FabricItemStack extends
                    // IItemStackExtension).
                    if (mine != null
                            && !mine.equals(hostDefault.getValue())
                            && !declared.contains(method)
                            && !extendsInterface(mine, hostDefault.getValue(), modClass, classes, new HashSet<>())) {
                        int split = method.indexOf('(');
                        conflicts.add(new HostDefault(
                                method.substring(0, split),
                                method.substring(split),
                                hostName,
                                sameCode(
                                        declaredMethod(modClass, mine, method),
                                        declaredMethod(classes::patched, hostDefault.getValue(), method))));
                        declared.add(method);
                    }
                }
            }
        }
        return conflicts;
    }

    /** Whether interface {@code name} (a mod's or the game's) extends {@code ancestor}, directly or further up. */
    private static boolean extendsInterface(
            String name,
            String ancestor,
            Function<String, Optional<ClassModel>> modClass,
            Classes classes,
            Set<String> seen) {
        if (!seen.add(name)) {
            return false;
        }
        Optional<ClassModel> model = modClass.apply(name).or(() -> classes.patched(name));
        if (model.isEmpty()) {
            return false;
        }
        for (var parent : model.get().interfaces()) {
            String parentName = parent.asInternalName();
            if (parentName.equals(ancestor) || extendsInterface(parentName, ancestor, modClass, classes, seen)) {
                return true;
            }
        }
        return false;
    }

    /** The default methods of interface {@code name} and its superinterfaces: name and descriptor, to the declaring one. */
    private static Map<String, String> defaults(
            String name, Function<String, Optional<ClassModel>> classes, Set<String> seen) {
        Map<String, String> found = new LinkedHashMap<>();
        if (!seen.add(name)) {
            return found;
        }
        Optional<ClassModel> model = classes.apply(name);
        if (model.isEmpty() || !model.get().flags().has(AccessFlag.INTERFACE)) {
            return found;
        }
        for (MethodModel method : model.get().methods()) {
            int flags = method.flags().flagsMask();
            if (method.code().isPresent()
                    && (flags & (ClassFile.ACC_STATIC | ClassFile.ACC_PRIVATE)) == 0
                    && !method.methodName().equalsString(ConstantDescs.CLASS_INIT_NAME)) {
                found.put(
                        method.methodName().stringValue() + method.methodType().stringValue(), name);
            }
        }
        for (var parent : model.get().interfaces()) {
            defaults(parent.asInternalName(), classes, seen).forEach(found::putIfAbsent);
        }
        return found;
    }

    /**
     * Adds {@code bridge}'s method: the patched method's parameters, then the handler's {@code CallbackInfo} and
     * captured locals; it calls {@code handler} with the parameters it was written for, and carries {@code inject}.
     */
    private static void bridgeMethod(
            java.lang.classfile.ClassBuilder builder,
            ClassModel mixin,
            MethodModel handler,
            Bridge bridge,
            Annotation inject) {
        MethodTypeDesc handlerType = MethodTypeDesc.ofDescriptor(bridge.descriptor());
        MethodTypeDesc bridgeType = MethodTypeDesc.ofDescriptor(bridge.bridgeDescriptor());
        int flags = handler.flags().flagsMask();
        boolean isStatic = (flags & ClassFile.ACC_STATIC) != 0;
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        ClassDesc owner = mixin.thisClass().asSymbol();
        int patchedCount = bridgeType.parameterCount()
                - (handlerType.parameterCount() - bridge.positions().size());
        // Where each handler parameter goes in the bridge: target parameters where the patch put them, the rest
        // (CallbackInfo, sugar parameters such as MixinExtras' @Local) after the patched parameters, in order.
        int[] placement = new int[handlerType.parameterCount()];
        for (int index = 0; index < placement.length; index++) {
            placement[index] = index < bridge.positions().size()
                    ? bridge.positions().get(index)
                    : patchedCount + index - bridge.positions().size();
        }
        builder.withMethod(BRIDGE_PREFIX + handler.methodName().stringValue(), bridgeType, flags, method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
            // Mixin reads a sugar parameter by its annotation; without it the bridge's descriptor is invalid.
            placedParameterAnnotations(handler, placement, bridgeType.parameterCount(), true)
                    .ifPresent(method::with);
            placedParameterAnnotations(handler, placement, bridgeType.parameterCount(), false)
                    .ifPresent(method::with);
            method.withCode(code -> {
                int[] slots = new int[bridgeType.parameterCount()];
                int slot = isStatic ? 0 : 1;
                for (int index = 0; index < slots.length; index++) {
                    slots[index] = slot;
                    slot += TypeKind.from(bridgeType.parameterType(index)).slotSize();
                }
                if (!isStatic) {
                    code.aload(0);
                }
                for (int index = 0; index < handlerType.parameterCount(); index++) {
                    int from = index < bridge.positions().size()
                            ? bridge.positions().get(index)
                            : patchedCount + index - bridge.positions().size();
                    code.loadLocal(TypeKind.from(bridgeType.parameterType(from)), slots[from]);
                }
                if (isStatic) {
                    code.invokestatic(owner, handler.methodName().stringValue(), handlerType, isInterface);
                } else {
                    code.invokespecial(owner, handler.methodName().stringValue(), handlerType, isInterface);
                }
                code.return_();
            });
        });
    }

    /** What {@link #newReceivers} found: the finding's reason and the guard. */
    private record Guard(String reason, Receivers receivers) {}

    /**
     * An {@code @Inject} handler of {@code mixin} into a method of its target that the patched override in a class of
     * {@code candidates} (a subclass of the target) reaches through {@code super}, while vanilla's override does not, so
     * the injection would run for receivers it never sees in vanilla (ADR-0026 41); empty otherwise. Only a handler of
     * one method of one target class is checked, and only the overrides of classes some mixin targets: the game's other
     * subclasses are not read, to keep the startup cost to the classes the mods touch.
     */
    private static Optional<Guard> newReceivers(
            ClassModel mixin, MethodModel handler, HostedLaunch host, Classes classes, Set<String> candidates) {
        Optional<Annotation> inject = annotation(handler.attributes(), INJECT);
        List<String> targetNames = mixinTargets(mixin);
        if (inject.isEmpty()
                || (handler.flags().flagsMask() & ClassFile.ACC_STATIC) != 0
                || mixin.flags().has(AccessFlag.INTERFACE)
                || !handler.methodTypeSymbol().returnType().equals(ConstantDescs.CD_void)
                || targetNames.size() != 1
                // A MixinExtras expression's definitions stay on the handler; the guard would not carry them.
                || annotations(handler.attributes()).stream()
                        .anyMatch(annotation -> annotation
                                .className()
                                .stringValue()
                                .startsWith("Lcom/llamalad7/mixinextras/expression/"))) {
            return Optional.empty();
        }
        List<String> selectors = new ArrayList<>();
        for (AnnotationElement element : inject.get().elements()) {
            if (element.name().equalsString("method")) {
                strings(element.value(), selectors);
            }
        }
        if (selectors.size() != 1) {
            return Optional.empty();
        }
        String name = selectors.getFirst().replaceFirst("^L[^;]+;", "");
        String descriptor = name.contains("(") ? name.substring(name.indexOf('(')) : null;
        name = descriptor == null ? name : name.substring(0, name.indexOf('('));
        if (name.contains("*") || name.startsWith("/") || name.contains(".") || name.startsWith("<")) {
            return Optional.empty();
        }
        String targetName = targetNames.getFirst();
        Optional<ClassModel> vanillaTarget = classes.vanilla(targetName);
        if (vanillaTarget.isEmpty() || vanillaTarget.get().flags().has(AccessFlag.INTERFACE)) {
            return Optional.empty();
        }
        List<MethodModel> methods = methods(vanillaTarget.get(), name, descriptor);
        if (methods.size() != 1
                || (methods.getFirst().flags().flagsMask()
                                & (ClassFile.ACC_STATIC | ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL))
                        != 0) {
            return Optional.empty();
        }
        String type = methods.getFirst().methodType().stringValue();
        List<String> excluded = new ArrayList<>();
        for (String candidate : candidates) {
            if (!candidate.equals(targetName)
                    && classes.vanillaSuperclasses(candidate).contains(targetName)
                    && reachesOnlyWhenPatched(candidate, targetName, name, type, classes)) {
                excluded.add(candidate);
            }
        }
        if (excluded.isEmpty()) {
            return Optional.empty();
        }
        String subclasses = String.join(", ", excluded);
        return Optional.of(new Guard(
                "@Inject " + handler.methodName().stringValue() + " into " + targetName + "." + name + ": "
                        + host.hostName() + "'s patch makes the override in " + subclasses
                        + " call it through super, which vanilla's does not; the injection keeps to the receivers it"
                        + " has in vanilla and does not run for " + subclasses,
                new Receivers(
                        handler.methodName().stringValue(),
                        handler.methodType().stringValue(),
                        List.copyOf(excluded))));
    }

    /**
     * Whether {@code subclass}'s override of {@code name} {@code descriptor} calls {@code target}'s through {@code super}
     * in the patched game but not in vanilla, with no class between them declaring the method in either.
     */
    private static boolean reachesOnlyWhenPatched(
            String subclass, String target, String name, String descriptor, Classes classes) {
        Optional<ClassModel> vanilla = classes.vanilla(subclass);
        Optional<ClassModel> patched = classes.patched(subclass);
        if (vanilla.isEmpty()
                || patched.isEmpty()
                || !inheritsDirectly(vanilla.get(), target, name, descriptor, classes::vanilla)
                || !inheritsDirectly(patched.get(), target, name, descriptor, classes::patched)) {
            return false;
        }
        Optional<MethodModel> before = declared(vanilla.get(), name, descriptor);
        Optional<MethodModel> after = declared(patched.get(), name, descriptor);
        return before.isPresent()
                && after.isPresent()
                && !callsSuper(before.get(), subclass, name, descriptor)
                && callsSuper(after.get(), subclass, name, descriptor);
    }

    /** Whether {@code target} is a superclass of {@code type} and no class between them declares {@code name}. */
    private static boolean inheritsDirectly(
            ClassModel type,
            String target,
            String name,
            String descriptor,
            Function<String, Optional<ClassModel>> classes) {
        Optional<String> parent = type.superclass().map(ClassEntry::asInternalName);
        for (int depth = 0; parent.isPresent() && depth < 32; depth++) {
            if (parent.get().equals(target)) {
                return true;
            }
            Optional<ClassModel> model = classes.apply(parent.get());
            if (model.isEmpty() || declared(model.get(), name, descriptor).isPresent()) {
                return false;
            }
            parent = model.get().superclass().map(ClassEntry::asInternalName);
        }
        return false;
    }

    /** {@code type}'s own instance method {@code name} {@code descriptor}, if it declares one. */
    private static Optional<MethodModel> declared(ClassModel type, String name, String descriptor) {
        return type.methods().stream()
                .filter(method -> method.methodName().equalsString(name)
                        && method.methodType().equalsString(descriptor)
                        && (method.flags().flagsMask() & ClassFile.ACC_STATIC) == 0)
                .findFirst();
    }

    /** Whether {@code method} of {@code owner} calls a superclass's {@code name} {@code descriptor} ({@code super}). */
    private static boolean callsSuper(MethodModel method, String owner, String name, String descriptor) {
        return method.code()
                .map(code -> code.elementStream()
                        .anyMatch(element -> element instanceof InvokeInstruction call
                                && call.opcode() == Opcode.INVOKESPECIAL
                                && !call.owner().asInternalName().equals(owner)
                                && call.name().equalsString(name)
                                && call.type().equalsString(descriptor)))
                .orElse(false);
    }

    /**
     * Adds {@code receivers}' guard method for {@code handler}: the handler's descriptor and parameter annotations,
     * carrying {@code inject}; it returns when the receiver is one of the excluded classes and calls the handler
     * otherwise.
     */
    private static void receiversMethod(
            java.lang.classfile.ClassBuilder builder,
            ClassModel mixin,
            MethodModel handler,
            Receivers receivers,
            Annotation inject) {
        MethodTypeDesc type = MethodTypeDesc.ofDescriptor(receivers.descriptor());
        int[] placement = new int[type.parameterCount()];
        for (int index = 0; index < placement.length; index++) {
            placement[index] = index;
        }
        ClassDesc owner = mixin.thisClass().asSymbol();
        builder.withMethod(
                RECEIVERS_PREFIX + handler.methodName().stringValue(),
                type,
                handler.flags().flagsMask(),
                method -> {
                    method.with(RuntimeVisibleAnnotationsAttribute.of(inject));
                    placedParameterAnnotations(handler, placement, placement.length, true)
                            .ifPresent(method::with);
                    placedParameterAnnotations(handler, placement, placement.length, false)
                            .ifPresent(method::with);
                    method.withCode(code -> {
                        for (String excluded : receivers.excluded()) {
                            java.lang.classfile.Label next = code.newLabel();
                            code.aload(0);
                            code.instanceOf(ClassDesc.ofInternalName(excluded));
                            code.ifeq(next);
                            code.return_();
                            code.labelBinding(next);
                        }
                        code.aload(0);
                        int slot = 1;
                        for (ClassDesc parameter : type.parameterList()) {
                            code.loadLocal(TypeKind.from(parameter), slot);
                            slot += TypeKind.from(parameter).slotSize();
                        }
                        code.invokespecial(owner, handler.methodName().stringValue(), type, false);
                        code.return_();
                    });
                });
    }

    /**
     * Adds {@code wrap}'s {@code @WrapOperation} method for {@code handler}: it takes the helper call's receiver and
     * arguments, the {@code Operation}, the target method's parameters (when the handler takes them) and the handler's
     * sugar parameters with their annotations; it runs the helper call and, when that returned true, calls the handler
     * with a fresh, non-cancellable {@code CallbackInfo} (the injection was not cancellable), then returns the result.
     */
    private static void wrapMethod(
            java.lang.classfile.ClassBuilder builder, ClassModel mixin, MethodModel handler, Wrap wrap) {
        MethodTypeDesc handlerType = MethodTypeDesc.ofDescriptor(wrap.descriptor());
        MethodTypeDesc methodType = MethodTypeDesc.ofDescriptor(wrap.methodDescriptor());
        String helper = wrap.helperCall();
        ClassDesc helperOwner = ClassDesc.ofDescriptor(helper.substring(0, helper.indexOf(';') + 1));
        MethodTypeDesc helperType = MethodTypeDesc.ofDescriptor(helper.substring(helper.indexOf('(')));
        int targetCount = wrap.targetParameters() ? methodType.parameterCount() : 0;
        int sugarFrom = targetCount + 1;
        List<ClassDesc> operands = new ArrayList<>();
        if (!wrap.helperStatic()) {
            operands.add(helperOwner);
        }
        operands.addAll(helperType.parameterList());
        List<ClassDesc> parameters = new ArrayList<>(operands);
        parameters.add(OPERATION);
        int operationIndex = operands.size();
        int targetFrom = parameters.size();
        parameters.addAll(methodType.parameterList().subList(0, targetCount));
        int bridgeSugarFrom = parameters.size();
        parameters.addAll(handlerType.parameterList().subList(sugarFrom, handlerType.parameterCount()));
        MethodTypeDesc bridgeType = MethodTypeDesc.of(ConstantDescs.CD_boolean, parameters);

        int flags = handler.flags().flagsMask();
        boolean isStatic = (flags & ClassFile.ACC_STATIC) != 0;
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        ClassDesc owner = mixin.thisClass().asSymbol();
        Annotation at = Annotation.of(
                ClassDesc.ofDescriptor(AT),
                AnnotationElement.ofString("value", "INVOKE"),
                AnnotationElement.ofString("target", helper));
        Annotation wrapOperation = adjust(
                Annotation.of(
                        ClassDesc.ofDescriptor(WRAP_OPERATION),
                        AnnotationElement.of(
                                "method",
                                AnnotationValue.ofArray(
                                        AnnotationValue.ofString(wrap.method() + wrap.methodDescriptor()))),
                        AnnotationElement.of("at", AnnotationValue.ofArray(AnnotationValue.ofAnnotation(at)))),
                List.of(),
                List.of(),
                List.of());
        builder.withMethod(WRAP_PREFIX + handler.methodName().stringValue(), bridgeType, flags, method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
            parameterAnnotations(handler, sugarFrom, bridgeSugarFrom, parameters.size(), true)
                    .ifPresent(method::with);
            parameterAnnotations(handler, sugarFrom, bridgeSugarFrom, parameters.size(), false)
                    .ifPresent(method::with);
            method.withCode(code -> {
                int[] slots = new int[parameters.size()];
                int slot = isStatic ? 0 : 1;
                for (int index = 0; index < slots.length; index++) {
                    slots[index] = slot;
                    slot += TypeKind.from(parameters.get(index)).slotSize();
                }
                int result = slot;
                code.aload(slots[operationIndex]);
                code.loadConstant(operands.size());
                code.anewarray(ConstantDescs.CD_Object);
                for (int index = 0; index < operands.size(); index++) {
                    code.dup();
                    code.loadConstant(index);
                    code.loadLocal(TypeKind.from(operands.get(index)), slots[index]);
                    box(code, operands.get(index));
                    code.aastore();
                }
                code.invokeinterface(
                        OPERATION,
                        "call",
                        MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                code.checkcast(ConstantDescs.CD_Boolean);
                code.invokevirtual(
                        ConstantDescs.CD_Boolean, "booleanValue", MethodTypeDesc.of(ConstantDescs.CD_boolean));
                code.istore(result);
                java.lang.classfile.Label done = code.newLabel();
                code.iload(result);
                code.ifeq(done);
                if (!isStatic) {
                    code.aload(0);
                }
                for (int index = 0; index < targetCount; index++) {
                    code.loadLocal(TypeKind.from(parameters.get(targetFrom + index)), slots[targetFrom + index]);
                }
                ClassDesc callbackInfo = ClassDesc.ofDescriptor(wrap.callbackInfo());
                code.new_(callbackInfo);
                code.dup();
                code.loadConstant(wrap.method());
                code.iconst_0();
                code.invokespecial(
                        callbackInfo,
                        ConstantDescs.INIT_NAME,
                        MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_String, ConstantDescs.CD_boolean));
                for (int index = bridgeSugarFrom; index < parameters.size(); index++) {
                    code.loadLocal(TypeKind.from(parameters.get(index)), slots[index]);
                }
                if (isStatic) {
                    code.invokestatic(owner, handler.methodName().stringValue(), handlerType, isInterface);
                } else {
                    code.invokespecial(owner, handler.methodName().stringValue(), handlerType, isInterface);
                }
                code.labelBinding(done);
                code.iload(result);
                code.ireturn();
            });
        });
    }

    private static void bedWrapMethod(
            java.lang.classfile.ClassBuilder builder, ClassModel mixin, MethodModel handler, BedWrap bedWrap) {
        int flags = handler.flags().flagsMask();
        Annotation at = Annotation.of(
                ClassDesc.ofDescriptor(AT),
                AnnotationElement.ofString("value", "INVOKE"),
                AnnotationElement.ofString("target", bedWrap.targetCall()));
        Annotation wrapOperation = Annotation.of(
                ClassDesc.ofDescriptor(WRAP_OPERATION),
                AnnotationElement.of(
                        "method",
                        AnnotationValue.ofArray(
                                AnnotationValue.ofString(bedWrap.targetMethod() + bedWrap.targetMethodDescriptor()))),
                AnnotationElement.of("at", AnnotationValue.ofArray(AnnotationValue.ofAnnotation(at))));

        if (bedWrap.kind() == BedWrap.Kind.DIRECTION) {
            MethodTypeDesc bridgeType = MethodTypeDesc.ofDescriptor(
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Lnet/minecraft/core/Direction;");
            builder.withMethod(WRAP_PREFIX + handler.methodName().stringValue(), bridgeType, flags, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
                method.withCode(code -> {
                    code.aload(4);
                    code.iconst_3();
                    code.anewarray(ConstantDescs.CD_Object);
                    code.dup();
                    code.iconst_0();
                    code.aload(1);
                    code.aastore();
                    code.dup();
                    code.iconst_1();
                    code.aload(2);
                    code.aastore();
                    code.dup();
                    code.iconst_2();
                    code.aload(3);
                    code.aastore();
                    code.invokeinterface(
                            OPERATION,
                            "call",
                            MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                    ClassDesc directionDesc = ClassDesc.of("net.minecraft.core.Direction");
                    code.checkcast(directionDesc);
                    code.astore(5);

                    ClassDesc sleepEventsDesc =
                            ClassDesc.of("net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents");
                    ClassDesc eventDesc = ClassDesc.of("net.fabricmc.fabric.api.event.Event");
                    ClassDesc modifySleepDesc = ClassDesc.of(
                            "net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents$ModifySleepingDirection");
                    ClassDesc livingEntityDesc = ClassDesc.of("net.minecraft.world.entity.LivingEntity");
                    ClassDesc blockPosDesc = ClassDesc.of("net.minecraft.core.BlockPos");

                    code.getstatic(sleepEventsDesc, "MODIFY_SLEEPING_DIRECTION", eventDesc);
                    code.invokevirtual(eventDesc, "invoker", MethodTypeDesc.of(ConstantDescs.CD_Object));
                    code.checkcast(modifySleepDesc);
                    code.aload(0);
                    code.checkcast(livingEntityDesc);
                    code.aload(3);
                    code.aload(5);
                    code.invokeinterface(
                            modifySleepDesc,
                            "modifySleepDirection",
                            MethodTypeDesc.of(directionDesc, livingEntityDesc, blockPosDesc, directionDesc));
                    code.areturn();
                });
            });
        } else if (bedWrap.kind() == BedWrap.Kind.OCCUPIED) {
            MethodTypeDesc bridgeType = MethodTypeDesc.ofDescriptor(
                    "(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/LivingEntity;ZLcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)V");
            builder.withMethod(WRAP_PREFIX + handler.methodName().stringValue(), bridgeType, flags, method -> {
                method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
                method.withCode(code -> {
                    ClassDesc sleepEventsDesc =
                            ClassDesc.of("net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents");
                    ClassDesc eventDesc = ClassDesc.of("net.fabricmc.fabric.api.event.Event");
                    ClassDesc setOccupiedDesc = ClassDesc.of(
                            "net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents$SetBedOccupationState");
                    ClassDesc livingEntityDesc = ClassDesc.of("net.minecraft.world.entity.LivingEntity");
                    ClassDesc blockPosDesc = ClassDesc.of("net.minecraft.core.BlockPos");
                    ClassDesc blockStateDesc = ClassDesc.of("net.minecraft.world.level.block.state.BlockState");

                    code.getstatic(sleepEventsDesc, "SET_BED_OCCUPATION_STATE", eventDesc);
                    code.invokevirtual(eventDesc, "invoker", MethodTypeDesc.of(ConstantDescs.CD_Object));
                    code.checkcast(setOccupiedDesc);
                    code.aload(4);
                    code.aload(3);
                    code.aload(1);
                    code.iload(5);
                    code.invokeinterface(
                            setOccupiedDesc,
                            "setBedOccupationState",
                            MethodTypeDesc.of(
                                    ConstantDescs.CD_boolean,
                                    livingEntityDesc,
                                    blockPosDesc,
                                    blockStateDesc,
                                    ConstantDescs.CD_boolean));
                    java.lang.classfile.Label defaultLogic = code.newLabel();
                    code.ifeq(defaultLogic);
                    code.return_();

                    code.labelBinding(defaultLogic);
                    code.aload(6);
                    code.loadConstant(5);
                    code.anewarray(ConstantDescs.CD_Object);
                    code.dup();
                    code.iconst_0();
                    code.aload(1);
                    code.aastore();
                    code.dup();
                    code.iconst_1();
                    code.aload(2);
                    code.aastore();
                    code.dup();
                    code.iconst_2();
                    code.aload(3);
                    code.aastore();
                    code.dup();
                    code.iconst_3();
                    code.aload(4);
                    code.aastore();
                    code.dup();
                    code.iconst_4();
                    code.iload(5);
                    code.invokestatic(
                            ConstantDescs.CD_Boolean,
                            "valueOf",
                            MethodTypeDesc.of(ConstantDescs.CD_Boolean, ConstantDescs.CD_boolean));
                    code.aastore();
                    code.invokeinterface(
                            OPERATION,
                            "call",
                            MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                    code.pop();
                    code.return_();
                });
            });
        }
    }

    /**
     * Whether {@code handler} is a {@code @WrapOperation} of {@code FluidState.is(TagKey)} the fluid bridge can run:
     * an instance method taking the fluid state, the tag and the {@code Operation} (then, when {@code trailing}, the
     * target method's parameters or MixinExtras sugar), returning {@code boolean}, calling its {@code Operation} once
     * with its own arguments and with no annotation on those three parameters.
     */
    static boolean fluidCheckWrap(MethodModel handler, boolean trailing) {
        MethodTypeDesc type = handler.methodTypeSymbol();
        return !handler.flags().has(AccessFlag.STATIC)
                && type.returnType().equals(ConstantDescs.CD_boolean)
                && (trailing ? type.parameterCount() >= 3 : type.parameterCount() == 3)
                && type.parameterType(0).equals(FLUID_STATE)
                && type.parameterType(1).equals(TAG_KEY)
                && type.parameterType(2).equals(OPERATION)
                && !HostReplacedCalls.annotatedBefore(handler, 3)
                && HostReplacedCalls.callsOnceWithOwnArguments(handler, 2);
    }

    /**
     * Whether {@code handler}'s only calls are {@code ItemStack.getCraftingRemainder()} (last, its result returned) and
     * maybe {@code LocalRef.get()} for the stack, with no branch or store: the handler is that call on a stack.
     */
    static boolean onlyStackRemainder(MethodModel handler) {
        List<Instruction> code = instructions(handler);
        int last = code.size() - 1;
        if (last < 1
                || code.get(last).opcode() != Opcode.ARETURN
                || !(code.get(last - 1) instanceof InvokeInstruction remainder)
                || !remainder.owner().asSymbol().equals(ITEM_STACK)
                || !remainder.name().equalsString("getCraftingRemainder")
                || remainder.typeSymbol().parameterCount() != 0) {
            return false;
        }
        for (int index = 0; index < last - 1; index++) {
            Instruction instruction = code.get(index);
            boolean allowed = instruction instanceof LoadInstruction
                    || (instruction instanceof TypeCheckInstruction check
                            && check.opcode() == Opcode.CHECKCAST
                            && check.type().asSymbol().equals(ITEM_STACK))
                    || (instruction instanceof InvokeInstruction get
                            && get.owner().asSymbol().equals(LOCAL_REF)
                            && get.name().equalsString("get"));
            if (!allowed) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code handler} (a {@code @WrapOperation} of {@code ItemStack.getItem()}) only stores its stack with
     * {@code LocalRef.set} and makes the wrapped call once with its own arguments.
     */
    static boolean onlyCapturesStack(MethodModel handler) {
        if (!HostReplacedCalls.callsOnceWithOwnArguments(handler, 1)) {
            return false;
        }
        for (Instruction instruction : instructions(handler)) {
            if (instruction instanceof InvokeInstruction invoke
                    && !(invoke.owner().asSymbol().equals(LOCAL_REF)
                            && invoke.name().equalsString("set"))
                    && !(invoke.owner().asSymbol().equals(OPERATION)
                            && invoke.name().equalsString("call"))) {
                return false;
            }
            if (instruction instanceof BranchInstruction) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code handler}'s only call is Fabric API's {@code ItemStack.canBeEnchantedWith(Holder,
     * EnchantingContext)} with {@code EnchantingContext.PRIMARY}, its result returned, with no branch or store.
     */
    static boolean onlyFabricEnchantingCheck(MethodModel handler) {
        int calls = 0;
        boolean primary = false;
        for (Instruction instruction : instructions(handler)) {
            if (instruction instanceof InvokeInstruction invoke) {
                if (!invoke.owner().asSymbol().equals(ITEM_STACK)
                        || !invoke.name().equalsString("canBeEnchantedWith")
                        || !invoke.typeSymbol().descriptorString().equals(CAN_BE_ENCHANTED_WITH)) {
                    return false;
                }
                calls++;
            } else if (instruction instanceof FieldInstruction field) {
                primary |= field.opcode() == Opcode.GETSTATIC
                        && field.owner().asSymbol().equals(ENCHANTING_CONTEXT)
                        && field.name().equalsString("PRIMARY");
            } else if (!(instruction instanceof LoadInstruction) && instruction.opcode() != Opcode.IRETURN) {
                return false;
            }
        }
        return calls == 1 && primary;
    }

    /** The instructions of {@code method}'s code (no labels, line numbers or other pseudo-instructions). */
    private static List<Instruction> instructions(MethodModel method) {
        List<Instruction> instructions = new ArrayList<>();
        method.code().ifPresent(code -> {
            for (CodeElement element : code) {
                if (element instanceof Instruction instruction) {
                    instructions.add(instruction);
                }
            }
        });
        return instructions;
    }

    /**
     * Adds the bridge of a {@link FluidWrap}: a {@code @WrapOperation} of Forge's call that makes it once, then calls
     * the handler with the fluid state, vanilla's {@code FluidTags.WATER}, a {@link PrecomputedCall} of Forge's answer
     * and the handler's trailing parameters (with their annotations), and returns what the handler returns.
     */
    private static void fluidWrapMethod(
            ClassBuilder builder, ClassModel mixin, MethodModel handler, FluidWrap fluidWrap) {
        List<String> methods = fluidWrap.kind() == FluidWrap.Kind.BOAT_SUPPORT
                ? List.of("checkInWater()Z", "isUnderwater()Z")
                : List.of(fluidWrap.targetMethod() + fluidWrap.targetMethodDescriptor());
        Annotation at = Annotation.of(
                ClassDesc.ofDescriptor(AT),
                AnnotationElement.ofString("value", "INVOKE"),
                AnnotationElement.ofString("target", fluidWrap.targetCall()));
        Annotation wrapOperation = Annotation.of(
                ClassDesc.ofDescriptor(WRAP_OPERATION),
                AnnotationElement.of(
                        "method",
                        AnnotationValue.ofArray(methods.stream()
                                .<AnnotationValue>map(AnnotationValue::ofString)
                                .toList())),
                AnnotationElement.of("at", AnnotationValue.ofArray(AnnotationValue.ofAnnotation(at))));
        MethodTypeDesc handlerType = handler.methodTypeSymbol();
        HostReplacedCalls.Call host =
                HostReplacedCalls.Call.parse(fluidWrap.targetCall()).orElseThrow();
        int hostArguments = host.type().parameterCount();
        List<ClassDesc> trailing = handlerType.parameterList().subList(3, handlerType.parameterCount());
        List<ClassDesc> parameters = new ArrayList<>();
        parameters.add(ClassDesc.ofDescriptor(host.owner()));
        parameters.addAll(host.type().parameterList());
        parameters.add(OPERATION);
        parameters.addAll(trailing);
        // The bridge's slots (an instance method): receiver, Forge's arguments, Operation, trailing parameters.
        int[] slots = new int[parameters.size()];
        int next = 1;
        for (int index = 0; index < parameters.size(); index++) {
            slots[index] = next;
            next += TypeKind.from(parameters.get(index)).slotSize();
        }
        int operationSlot = slots[1 + hostArguments];
        ClassDesc owner = mixin.thisClass().asSymbol();
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        builder.withMethod(
                WRAP_PREFIX + handler.methodName().stringValue(),
                MethodTypeDesc.of(ConstantDescs.CD_boolean, parameters),
                handler.flags().flagsMask(),
                method -> {
                    method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
                    handler.findAttribute(Attributes.runtimeVisibleParameterAnnotations())
                            .ifPresent(attribute ->
                                    method.with(RuntimeVisibleParameterAnnotationsAttribute.of(trailingAnnotations(
                                            attribute.parameterAnnotations(), parameters.size(), trailing.size()))));
                    handler.findAttribute(Attributes.runtimeInvisibleParameterAnnotations())
                            .ifPresent(attribute ->
                                    method.with(RuntimeInvisibleParameterAnnotationsAttribute.of(trailingAnnotations(
                                            attribute.parameterAnnotations(), parameters.size(), trailing.size()))));
                    method.withCode(code -> {
                        int result = code.allocateLocal(TypeKind.REFERENCE);
                        // Forge's call, once: its receiver and arguments.
                        code.aload(operationSlot);
                        code.loadConstant(hostArguments + 1);
                        code.anewarray(ConstantDescs.CD_Object);
                        for (int index = 0; index <= hostArguments; index++) {
                            code.dup();
                            code.loadConstant(index);
                            code.aload(slots[index]);
                            code.aastore();
                        }
                        code.invokeinterface(
                                OPERATION,
                                "call",
                                MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                        code.astore(result);
                        code.aload(0);
                        if (fluidWrap.kind() == FluidWrap.Kind.BOAT_SUPPORT) {
                            code.aload(slots[1]);
                        } else {
                            // The fluid state vanilla's updateSwimming tests: level().getFluidState(blockPosition()).
                            code.aload(slots[0]);
                            code.invokevirtual(ENTITY, "level", MethodTypeDesc.of(LEVEL));
                            code.aload(slots[0]);
                            code.invokevirtual(ENTITY, "blockPosition", MethodTypeDesc.of(BLOCK_POS));
                            code.invokevirtual(LEVEL, "getFluidState", MethodTypeDesc.of(FLUID_STATE, BLOCK_POS));
                        }
                        code.getstatic(FLUID_TAGS, "WATER", TAG_KEY);
                        code.new_(PRECOMPUTED_CALL);
                        code.dup();
                        code.aload(result);
                        code.invokespecial(
                                PRECOMPUTED_CALL,
                                ConstantDescs.INIT_NAME,
                                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object));
                        for (int index = 0; index < trailing.size(); index++) {
                            int parameter = 2 + hostArguments + index;
                            code.loadLocal(TypeKind.from(parameters.get(parameter)), slots[parameter]);
                        }
                        code.invokespecial(owner, handler.methodName().stringValue(), handlerType, isInterface);
                        code.ireturn();
                    });
                });
    }

    /** Parameter annotations for a bridge of {@code count} parameters: only the last {@code trailing} keep the handler's. */
    private static List<List<Annotation>> trailingAnnotations(
            List<List<Annotation>> annotations, int count, int trailing) {
        List<List<Annotation>> placed = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            placed.add(List.of());
        }
        for (int index = 0; index < trailing; index++) {
            int from = 3 + index;
            if (from < annotations.size()) {
                placed.set(count - trailing + index, annotations.get(from));
            }
        }
        return placed;
    }

    /**
     * Adds the bridge of an {@link ItemWrap}, if it needs one. The remainder kinds need none: the handler only makes the
     * call Forge makes ({@link #onlyStackRemainder}, {@link #onlyCapturesStack}). ENCHANTMENT_CHECK wraps Forge's
     * {@code canApplyAtEnchantingTable}: Fabric's check decides when its answer differs from vanilla's {@code
     * Enchantment.isPrimaryItem} (an {@code EnchantmentEvents.ALLOW_ENCHANTING} listener or a Fabric item's override),
     * otherwise Forge's call does (a Forge item's override).
     */
    private static void itemWrapMethod(ClassBuilder builder, MethodModel handler, ItemWrap itemWrap) {
        if (itemWrap.kind() != ItemWrap.Kind.ENCHANTMENT_CHECK) {
            return;
        }
        int flags = handler.flags().flagsMask();
        Annotation at = Annotation.of(
                ClassDesc.ofDescriptor(AT),
                AnnotationElement.ofString("value", "INVOKE"),
                AnnotationElement.ofString("target", itemWrap.targetCall()));
        Annotation wrapOperation = Annotation.of(
                ClassDesc.ofDescriptor(WRAP_OPERATION),
                AnnotationElement.of(
                        "method",
                        AnnotationValue.ofArray(
                                AnnotationValue.ofString(itemWrap.targetMethod() + itemWrap.targetMethodDescriptor()))),
                AnnotationElement.of("at", AnnotationValue.ofArray(AnnotationValue.ofAnnotation(at))));
        MethodTypeDesc bridgeType = MethodTypeDesc.of(ConstantDescs.CD_boolean, ITEM_STACK, HOLDER, OPERATION);
        int stack = (flags & ClassFile.ACC_STATIC) != 0 ? 0 : 1;
        builder.withMethod(WRAP_PREFIX + handler.methodName().stringValue(), bridgeType, flags, method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
            method.withCode(code -> {
                Label forge = code.newLabel();
                code.aload(stack);
                code.aload(stack + 1);
                code.getstatic(ENCHANTING_CONTEXT, "PRIMARY", ENCHANTING_CONTEXT);
                code.invokevirtual(
                        ITEM_STACK, "canBeEnchantedWith", MethodTypeDesc.ofDescriptor(CAN_BE_ENCHANTED_WITH));
                code.dup();
                code.aload(stack + 1);
                code.invokeinterface(HOLDER, "value", MethodTypeDesc.of(ConstantDescs.CD_Object));
                code.checkcast(ENCHANTMENT);
                code.aload(stack);
                code.invokevirtual(
                        ENCHANTMENT, "isPrimaryItem", MethodTypeDesc.of(ConstantDescs.CD_boolean, ITEM_STACK));
                // Stack: fabric, fabric, vanilla. Equal answers: Fabric changed nothing, so Forge's call decides.
                code.if_icmpeq(forge);
                code.ireturn();
                code.labelBinding(forge);
                code.pop();
                code.aload(stack + 2);
                code.iconst_2();
                code.anewarray(ConstantDescs.CD_Object);
                code.dup();
                code.iconst_0();
                code.aload(stack);
                code.aastore();
                code.dup();
                code.iconst_1();
                code.aload(stack + 1);
                code.aastore();
                code.invokeinterface(
                        OPERATION,
                        "call",
                        MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_Object.arrayType()));
                code.checkcast(ConstantDescs.CD_Boolean);
                code.invokevirtual(
                        ConstantDescs.CD_Boolean, "booleanValue", MethodTypeDesc.of(ConstantDescs.CD_boolean));
                code.ireturn();
            });
        });
    }

    private static void registrySyncWrapMethod(
            java.lang.classfile.ClassBuilder builder,
            ClassModel mixin,
            MethodModel handler,
            RegistrySyncWrap registrySyncWrap) {
        int flags = handler.flags().flagsMask();
        Annotation at = Annotation.of(
                ClassDesc.ofDescriptor(AT),
                AnnotationElement.ofString("value", "INVOKE"),
                AnnotationElement.ofString("target", registrySyncWrap.targetCall()));
        Annotation wrapOperation = Annotation.of(
                ClassDesc.ofDescriptor(WRAP_OPERATION),
                AnnotationElement.of(
                        "method",
                        AnnotationValue.ofArray(AnnotationValue.ofString(
                                registrySyncWrap.targetMethod() + registrySyncWrap.targetMethodDescriptor()))),
                AnnotationElement.of("at", AnnotationValue.ofArray(AnnotationValue.ofAnnotation(at))));

        MethodTypeDesc bridgeType = MethodTypeDesc.ofDescriptor(
                "(Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/util/stream/Stream;");
        builder.withMethod(WRAP_PREFIX + handler.methodName().stringValue(), bridgeType, flags, method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
            method.withCode(code -> {
                ClassDesc dynamicRegistriesDesc =
                        ClassDesc.of("net.fabricmc.fabric.api.event.registry.DynamicRegistries");
                ClassDesc listDesc = ClassDesc.of("java.util.List");
                ClassDesc streamDesc = ClassDesc.of("java.util.stream.Stream");

                code.invokestatic(dynamicRegistriesDesc, "getBootstrappingRegistries", MethodTypeDesc.of(listDesc));
                code.invokeinterface(listDesc, "stream", MethodTypeDesc.of(streamDesc));
                code.areturn();
            });
        });
    }

    /**
     * {@code handler}'s parameter annotations for a method of {@code count} parameters where handler parameter {@code i}
     * is parameter {@code placement[i]}; empty when the handler has none of that kind.
     */
    private static Optional<java.lang.classfile.MethodElement> placedParameterAnnotations(
            MethodModel handler, int[] placement, int count, boolean invisible) {
        Optional<List<List<Annotation>>> own = invisible
                ? handler.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                        .map(
                                java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute
                                        ::parameterAnnotations)
                : handler.findAttribute(java.lang.classfile.Attributes.runtimeVisibleParameterAnnotations())
                        .map(
                                java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute
                                        ::parameterAnnotations);
        if (own.isEmpty()) {
            return Optional.empty();
        }
        List<List<Annotation>> placed = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            placed.add(List.of());
        }
        for (int index = 0; index < placement.length && index < own.get().size(); index++) {
            placed.set(placement[index], own.get().get(index));
        }
        return Optional.of(
                invisible
                        ? java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute.of(placed)
                        : java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute.of(placed));
    }

    /**
     * The handler's parameter annotations from {@code from} on (its sugar parameters), placed from {@code to} on among
     * {@code count} bridge parameters; empty when the handler has none of that kind.
     */
    private static Optional<java.lang.classfile.MethodElement> parameterAnnotations(
            MethodModel handler, int from, int to, int count, boolean invisible) {
        Optional<List<List<Annotation>>> own = invisible
                ? handler.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                        .map(
                                java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute
                                        ::parameterAnnotations)
                : handler.findAttribute(java.lang.classfile.Attributes.runtimeVisibleParameterAnnotations())
                        .map(
                                java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute
                                        ::parameterAnnotations);
        if (own.isEmpty()) {
            return Optional.empty();
        }
        List<List<Annotation>> placed = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int source = index - to + from;
            placed.add(index >= to && source < own.get().size() ? own.get().get(source) : List.of());
        }
        return Optional.of(
                invisible
                        ? java.lang.classfile.attribute.RuntimeInvisibleParameterAnnotationsAttribute.of(placed)
                        : java.lang.classfile.attribute.RuntimeVisibleParameterAnnotationsAttribute.of(placed));
    }

    /** Boxes the primitive on the stack of type {@code type}; leaves a reference as it is. */
    private static void box(java.lang.classfile.CodeBuilder code, ClassDesc type) {
        if (!type.isPrimitive()) {
            return;
        }
        ClassDesc boxed = switch (type.descriptorString()) {
            case "Z" -> ConstantDescs.CD_Boolean;
            case "B" -> ConstantDescs.CD_Byte;
            case "C" -> ConstantDescs.CD_Character;
            case "S" -> ConstantDescs.CD_Short;
            case "I" -> ConstantDescs.CD_Integer;
            case "J" -> ConstantDescs.CD_Long;
            case "F" -> ConstantDescs.CD_Float;
            case "D" -> ConstantDescs.CD_Double;
            default -> throw new IllegalArgumentException("not a value type: " + type.descriptorString());
        };
        code.invokestatic(boxed, "valueOf", MethodTypeDesc.of(boxed, type));
    }

    /**
     * Adds {@code redirect}'s {@code @ModifyArg(index = 0)} bridge for {@code handler}: it takes and returns the first
     * argument of {@code redirect.replacement()} ({@code handler}'s parameter and return type), calls {@code handler}
     * to mutate that argument in place, discards the copy {@code handler} returned, and returns the original argument.
     */
    private static void inPlaceRedirectMethod(
            java.lang.classfile.ClassBuilder builder,
            ClassModel mixin,
            MethodModel handler,
            InPlaceRedirect redirect,
            Annotation original) {
        MethodTypeDesc type = MethodTypeDesc.ofDescriptor(redirect.descriptor());
        ClassDesc argumentType = type.parameterType(0);
        int flags = handler.flags().flagsMask();
        boolean isStatic = (flags & ClassFile.ACC_STATIC) != 0;
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        ClassDesc owner = mixin.thisClass().asSymbol();
        List<AnnotationElement> elements = new ArrayList<>();
        for (AnnotationElement element : original.elements()) {
            if (!element.name().equalsString("index")) {
                elements.add(element);
            }
        }
        elements.add(AnnotationElement.ofInt("index", 0));
        Annotation modifyArg = Annotation.of(ClassDesc.ofDescriptor(MODIFY_ARG), elements);
        builder.withMethod(REDIRECT_PREFIX + handler.methodName().stringValue(), type, flags, method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(modifyArg));
            method.withCode(code -> {
                int argSlot = isStatic ? 0 : 1;
                if (!isStatic) {
                    code.aload(0);
                }
                code.loadLocal(TypeKind.from(argumentType), argSlot);
                if (isStatic) {
                    code.invokestatic(owner, handler.methodName().stringValue(), type, isInterface);
                } else {
                    code.invokespecial(owner, handler.methodName().stringValue(), type, isInterface);
                }
                if (TypeKind.from(type.returnType()).slotSize() == 2) {
                    code.pop2();
                } else {
                    code.pop();
                }
                code.loadLocal(TypeKind.from(argumentType), argSlot);
                code.return_(TypeKind.from(argumentType));
            });
        });
    }

    /**
     * An injector with its moved {@code @At} targets, {@code method} selectors and {@code @Slice} boundaries; its
     * {@code require} and local capture stay as the mod wrote them.
     */
    private static Annotation adjust(
            Annotation annotation,
            List<Retarget> retargets,
            List<MethodRetarget> methodRetargets,
            List<SliceRetarget> sliceRetargets) {
        String type = annotation.className().stringValue();
        if (!INJECTORS.contains(type)
                || (retargets.isEmpty() && methodRetargets.isEmpty() && sliceRetargets.isEmpty())) {
            return annotation;
        }
        List<AnnotationElement> elements = new ArrayList<>();
        for (AnnotationElement element : annotation.elements()) {
            AnnotationValue value = element.value();
            for (Retarget retarget : retargets) {
                value = retarget(value, retarget.replaced(), retarget.replacement());
            }
            if (element.name().equalsString("method")) {
                for (MethodRetarget retarget : methodRetargets) {
                    value = retargetMethod(value, retarget.selector(), retarget.replacement());
                }
            }
            if (element.name().equalsString("slice")) {
                for (SliceRetarget retarget : sliceRetargets) {
                    value = retargetSlice(value, retarget.toField());
                }
            }
            elements.add(AnnotationElement.of(element.name().stringValue(), value));
        }
        return Annotation.of(annotation.classSymbol(), elements);
    }

    private static AnnotationValue retargetMethod(AnnotationValue value, String selector, String replacement) {
        if (value instanceof AnnotationValue.OfArray array) {
            return AnnotationValue.ofArray(array.values().stream()
                    .map(item -> retargetMethod(item, selector, replacement))
                    .toList());
        }
        if (value instanceof AnnotationValue.OfString text && text.stringValue().equals(selector)) {
            return AnnotationValue.ofString(replacement);
        }
        return value;
    }

    private static AnnotationValue retargetSlice(AnnotationValue value, String toField) {
        if (value instanceof AnnotationValue.OfArray array) {
            return AnnotationValue.ofArray(array.values().stream()
                    .map(item -> retargetSlice(item, toField))
                    .toList());
        }
        if (value instanceof AnnotationValue.OfAnnotation nested
                && nested.annotation().className().equalsString(SLICE)) {
            Annotation toAt = Annotation.of(
                    ClassDesc.ofDescriptor(AT),
                    AnnotationElement.ofString("value", "FIELD"),
                    AnnotationElement.ofString("target", toField),
                    AnnotationElement.ofInt("opcode", Opcode.GETFIELD.bytecode()));
            List<AnnotationElement> elements = new ArrayList<>();
            for (AnnotationElement element : nested.annotation().elements()) {
                if (!element.name().equalsString("from") && !element.name().equalsString("to")) {
                    elements.add(element);
                }
            }
            elements.add(AnnotationElement.ofAnnotation("to", toAt));
            return AnnotationValue.ofAnnotation(
                    Annotation.of(nested.annotation().classSymbol(), elements));
        }
        return value;
    }

    private static AnnotationValue retarget(AnnotationValue value, String replaced, String replacement) {
        if (value instanceof AnnotationValue.OfArray array) {
            return AnnotationValue.ofArray(array.values().stream()
                    .map(item -> retarget(item, replaced, replacement))
                    .toList());
        }
        if (!(value instanceof AnnotationValue.OfAnnotation nested)) {
            return value;
        }
        Annotation at = nested.annotation();
        List<AnnotationElement> elements = new ArrayList<>();
        for (AnnotationElement element : at.elements()) {
            if (at.className().equalsString(AT)
                    && element.name().equalsString("target")
                    && element.value() instanceof AnnotationValue.OfString target
                    && target.stringValue().equals(replaced)) {
                elements.add(AnnotationElement.ofString("target", replacement));
            } else {
                elements.add(AnnotationElement.of(
                        element.name().stringValue(), retarget(element.value(), replaced, replacement)));
            }
        }
        return AnnotationValue.ofAnnotation(Annotation.of(at.classSymbol(), elements));
    }

    /** The mixin classes of {@code config} for {@code side}: entry as written in the file, to internal name. */
    private static Map<String, String> mixinClasses(JarFile file, String config, GameSide side) throws IOException {
        byte[] bytes = read(file, config);
        if (bytes == null) {
            return Map.of();
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8));
            if (!(parsed instanceof JsonObject object) || !object.has("package")) {
                return Map.of();
            }
            root = object;
        } catch (JsonParseException e) {
            return Map.of();
        }
        String packagePath = root.get("package").getAsString().replace('.', '/');
        Map<String, String> classes = new LinkedHashMap<>();
        for (String section : List.of("mixins", side == GameSide.CLIENT ? "client" : "server")) {
            if (root.get(section) instanceof JsonArray array) {
                for (JsonElement element : array) {
                    classes.put(
                            element.getAsString(),
                            packagePath + "/" + element.getAsString().replace('.', '/'));
                }
            }
        }
        return classes;
    }

    /** Whether configuration {@code config} sets {@code overwrites.requireAnnotations} (Mixin's default is false). */
    private static boolean overwriteNeedsAnnotation(JarFile file, String config) throws IOException {
        byte[] bytes = read(file, config);
        if (bytes == null) {
            return false;
        }
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)) instanceof JsonObject root
                    && root.get("overwrites") instanceof JsonObject overwrites
                    && overwrites.get("requireAnnotations") instanceof JsonElement value
                    && value.isJsonPrimitive()
                    && value.getAsBoolean();
        } catch (JsonParseException e) {
            return false;
        }
    }

    private static byte[] read(JarFile file, String path) throws IOException {
        ZipEntry entry = file.getEntry(path);
        if (entry == null) {
            return null;
        }
        try (InputStream input = file.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    /** The first member the mixin needs that the patch removed from one of its targets, described. */
    private static Optional<String> missingMember(ClassModel mixin, HostedLaunch host, Classes classes) {
        for (String targetName : mixinTargets(mixin)) {
            Optional<ClassModel> vanilla = classes.vanilla(targetName);
            Optional<ClassModel> patched = classes.patched(targetName);
            if (vanilla.isEmpty() || patched.isEmpty()) {
                continue;
            }
            for (FieldModel field : mixin.fields()) {
                Optional<Annotation> shadow = annotation(field.attributes(), SHADOW);
                if (shadow.isPresent()) {
                    // Mixin finds a shadow field by its name or an alias, and its type.
                    String name = field.fieldName().stringValue();
                    String type = field.fieldType().stringValue();
                    List<String> names = new ArrayList<>(List.of(name));
                    shadow.get().elements().stream()
                            .filter(element -> element.name().equalsString("aliases"))
                            .forEach(element -> strings(element.value(), names));
                    if (names.stream().anyMatch(candidate -> hasField(vanilla.get(), candidate, type))
                            && names.stream().noneMatch(candidate -> hasField(patched.get(), candidate, type))) {
                        Optional<String> patchedType = fieldType(patched.get(), name);
                        if (patchedType.isPresent()) {
                            return Optional.of("its @Shadow field " + name + ":" + type + " of " + targetName
                                    + " is a " + patchedType.get() + " in the game as " + host.hostName()
                                    + " patches it, so this mixin is left out");
                        }
                        return Optional.of(removed(host, targetName, "field " + name, "@Shadow"));
                    }
                }
            }
            for (MethodModel method : mixin.methods()) {
                String name = method.methodName().stringValue();
                String descriptor = method.methodType().stringValue();
                if (has(method.attributes(), SHADOW) || has(method.attributes(), OVERWRITE)) {
                    String target = name.startsWith("shadow$") ? name.substring("shadow$".length()) : name;
                    if (hasMethod(vanilla.get(), target, descriptor) && !hasMethod(patched.get(), target, descriptor)) {
                        return Optional.of(removed(
                                host,
                                targetName,
                                "method " + target + descriptor,
                                has(method.attributes(), SHADOW) ? "@Shadow" : "@Overwrite"));
                    }
                }
                Optional<Annotation> accessor = annotation(method.attributes(), ACCESSOR);
                if (accessor.isPresent()) {
                    String field = value(accessor.get()).orElse(accessorName(name));
                    if (hasField(vanilla.get(), field) && !hasField(patched.get(), field)) {
                        return Optional.of(removed(host, targetName, "field " + field, "@Accessor"));
                    }
                }
                Optional<Annotation> invoker = annotation(method.attributes(), INVOKER);
                if (invoker.isPresent()) {
                    String target = value(invoker.get()).orElse(invokerName(name));
                    if (hasMethod(vanilla.get(), target, null) && !hasMethod(patched.get(), target, null)) {
                        return Optional.of(removed(host, targetName, "method " + target, "@Invoker"));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static String removed(HostedLaunch host, String targetName, String member, String use) {
        return "its " + use + " " + member + " of " + targetName + " is not in the game as " + host.hostName()
                + " patches it, so this mixin is left out";
    }

    /** An injection the patch changed: the reason, and where it moves or how it is changed, if at all. */
    private record Injection(
            String reason,
            Retarget retarget,
            MethodRetarget methodRetarget,
            SliceRetarget sliceRetarget,
            InPlaceRedirect inPlaceRedirect,
            Bridge bridge,
            TakenOut takenOut,
            Wrap wrap,
            Appended appended,
            BedWrap bedWrap,
            FluidWrap fluidWrap,
            ItemWrap itemWrap,
            RegistrySyncWrap registrySyncWrap,
            OptionalLeft optionalLeft,
            HostList hostList,
            ReplacedCall replacedCall) {

        Injection(String reason, Retarget retarget) {
            this(reason, retarget, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        }

        static Injection methodRetarget(String reason, MethodRetarget methodRetarget) {
            return new Injection(
                    reason,
                    null,
                    methodRetarget,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        static Injection methodAndPointRetarget(String reason, MethodRetarget methodRetarget, Retarget retarget) {
            return new Injection(
                    reason,
                    retarget,
                    methodRetarget,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        static Injection sliceRetarget(String reason, SliceRetarget sliceRetarget) {
            return new Injection(
                    reason,
                    null,
                    null,
                    sliceRetarget,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        Injection(String reason, Retarget retarget, InPlaceRedirect inPlaceRedirect) {
            this(
                    reason,
                    retarget,
                    null,
                    null,
                    inPlaceRedirect,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        Injection(String reason, Retarget retarget, Bridge bridge, TakenOut takenOut) {
            this(
                    reason, retarget, null, null, null, bridge, takenOut, null, null, null, null, null, null, null,
                    null, null);
        }

        Injection(String reason, Retarget retarget, Bridge bridge, TakenOut takenOut, Wrap wrap) {
            this(
                    reason, retarget, null, null, null, bridge, takenOut, wrap, null, null, null, null, null, null,
                    null, null);
        }

        Injection(String reason, Retarget retarget, Bridge bridge, TakenOut takenOut, Wrap wrap, Appended appended) {
            this(
                    reason, retarget, null, null, null, bridge, takenOut, wrap, appended, null, null, null, null, null,
                    null, null);
        }

        static Injection bedWrap(String reason, BedWrap bedWrap) {
            return new Injection(
                    reason, null, null, null, null, null, null, null, null, bedWrap, null, null, null, null, null,
                    null);
        }

        static Injection fluidWrap(String reason, FluidWrap fluidWrap) {
            return new Injection(
                    reason, null, null, null, null, null, null, null, null, null, fluidWrap, null, null, null, null,
                    null);
        }

        static Injection itemWrap(String reason, ItemWrap itemWrap) {
            return new Injection(
                    reason, null, null, null, null, null, null, null, null, null, null, itemWrap, null, null, null,
                    null);
        }

        static Injection registrySyncWrap(String reason, RegistrySyncWrap registrySyncWrap) {
            return new Injection(
                    reason,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    registrySyncWrap,
                    null,
                    null,
                    null);
        }

        static Injection optionalLeft(String reason, OptionalLeft optionalLeft) {
            return new Injection(
                    reason,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    optionalLeft,
                    null,
                    null);
        }

        static Injection hostList(String reason, HostList hostList) {
            return new Injection(
                    reason, null, null, null, null, null, null, null, null, null, null, null, null, null, hostList,
                    null);
        }

        static Injection replacedCall(String reason, ReplacedCall replacedCall) {
            return new Injection(
                    reason,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    replacedCall);
        }

        /** Whether the injection does not run in the patched game (not moved, bridged or wrapped). */
        boolean doesNotRun() {
            return retarget == null
                    && methodRetarget == null
                    && sliceRetarget == null
                    && inPlaceRedirect == null
                    && bridge == null
                    && wrap == null
                    && appended == null
                    && bedWrap == null
                    && fluidWrap == null
                    && itemWrap == null
                    && registrySyncWrap == null
                    && optionalLeft == null
                    && hostList == null
                    && replacedCall == null
                    && (takenOut != null || reason.endsWith("this injection does not run"));
        }
    }

    /** The injection handlers of {@code mixin} (keys {@code name + descriptor}). */
    private static Set<String> handlers(ClassModel mixin) {
        Set<String> handlers = new HashSet<>();
        for (MethodModel method : mixin.methods()) {
            if (annotations(method.attributes()).stream()
                    .anyMatch(annotation ->
                            INJECTORS.contains(annotation.className().stringValue()))) {
                handlers.add(CoupledInjections.key(method));
            }
        }
        return handlers;
    }

    private static List<Injection> injections(
            ClassModel mixin, HostedLaunch host, Classes classes, Set<String> notRunning, Set<String> adjusted) {
        List<Injection> found = new ArrayList<>();
        for (String targetName : mixinTargets(mixin)) {
            Optional<ClassModel> vanilla = classes.vanilla(targetName);
            Optional<ClassModel> patched = classes.patched(targetName);
            if (vanilla.isEmpty() || patched.isEmpty()) {
                continue;
            }
            for (MethodModel handler : mixin.methods()) {
                for (Annotation injector : annotations(handler.attributes())) {
                    String type = injector.className().stringValue();
                    if (INJECTORS.contains(type)) {
                        injection(
                                        mixin,
                                        handler,
                                        type,
                                        injector,
                                        targetName,
                                        vanilla.get(),
                                        patched.get(),
                                        host,
                                        owner -> !owner.startsWith("java/")
                                                && classes.vanilla(owner).isEmpty()
                                                && classes.patched(owner).isPresent(),
                                        classes::patched)
                                .ifPresent(injection -> {
                                    found.add(injection);
                                    adjusted.add(CoupledInjections.key(handler));
                                    if (injection.doesNotRun()) {
                                        notRunning.add(CoupledInjections.key(handler));
                                    }
                                });
                    }
                }
            }
        }
        return found;
    }

    private static Optional<Injection> injection(
            ClassModel mixin,
            MethodModel handler,
            String type,
            Annotation injector,
            String targetName,
            ClassModel vanilla,
            ClassModel patched,
            HostedLaunch host,
            Predicate<String> hostOwned,
            Function<String, Optional<ClassModel>> patchedClasses) {
        List<String> selectors = new ArrayList<>();
        List<String[]> points = new ArrayList<>();
        List<String[]> boundaries = new ArrayList<>();
        for (AnnotationElement element : injector.elements()) {
            if (element.name().equalsString("method")) {
                strings(element.value(), selectors);
            } else if (element.name().equalsString("at")) {
                points(element.value(), points);
            } else if (element.name().equalsString("slice")) {
                slicePoints(element.value(), boundaries);
            }
        }
        String simple = type.substring(type.lastIndexOf('/') + 1, type.length() - 1);
        for (String selector : selectors) {
            String name = selector.replaceFirst("^L[^;]+;", "");
            String descriptor = name.contains("(") ? name.substring(name.indexOf('(')) : null;
            name = descriptor == null ? name : name.substring(0, name.indexOf('('));
            if (name.contains("*") || name.startsWith("/") || name.contains(".")) {
                continue;
            }
            List<MethodModel> before = methods(vanilla, name, descriptor);
            List<MethodModel> after = methods(patched, name, descriptor);
            if (before.isEmpty()) {
                continue;
            }
            String where = "@" + simple + " " + handler.methodName().stringValue() + " into " + targetName + "." + name;
            if (after.isEmpty()) {
                return Optional.of(new Injection(
                        where + ": that method is not in the game as " + host.hostName()
                                + " patches it; this injection does not run",
                        null));
            }
            if (type.equals(INJECT) && descriptor == null && before.size() == 1 && after.size() == 1) {
                Optional<HostConditionalSkip.Companion> companion = HostConditionalSkip.companion(
                        mixin, handler, type, targetName, before.getFirst(), after.getFirst(), patched);
                if (companion.isPresent()) {
                    return Optional.of(Injection.replacedCall(
                            where + ": " + host.hostName()
                                    + " parses with ConditionalOps.createConditionalCodec, with bridge turning fabric skip marker into empty Optional skipped by store lambda",
                            new ReplacedCall(
                                    handler.methodName().stringValue(),
                                    handler.methodType().stringValue(),
                                    HostConditionalSkip.SKIP,
                                    companion.get().name() + companion.get().descriptor() + "#"
                                            + companion.get().marker()
                                            + (companion.get().withModifier()
                                                    ? HostConditionalSkip.MODIFIER_FLAG
                                                    : ""))));
                }
                Optional<Injection> changed =
                        changedParameters(handler, where, before.getFirst(), after.getFirst(), host);
                if (changed.isPresent()) {
                    return changed;
                }
            }
            if (descriptor == null) {
                // Without a descriptor Mixin injects into the first method of that name only. A patch may add an
                // overload and move the code there (NeoForge's BaseContainerBlockEntity.setItem(int, ItemStack) calls
                // its new setItem(int, ItemStack, boolean), which holds the vanilla body).
                before = List.of(before.getFirst());
                after = List.of(after.getFirst());
            }
            // A slice whose boundary the patch removed selects nothing, so the injection finds nothing in it.
            for (String[] boundary : boundaries) {
                if (boundary[1] != null
                        && CHECKED_POINTS.contains(boundary[0])
                        && before.stream().anyMatch(method -> contains(method, boundary[1]))
                        && after.stream().noneMatch(method -> contains(method, boundary[1]))) {
                    return Optional.of(new Injection(
                            where + " in a slice bounded at " + boundary[0] + " " + boundary[1] + ": "
                                    + host.hostName() + "'s patch removed that "
                                    + boundary[0].toLowerCase(Locale.ROOT) + " there; this injection does not run",
                            null));
                }
            }
            // The boundaries are still there, but the patch may have moved the injection point out of the slice.
            Optional<Injection> outOfSlice = outOfSlice(handler, where, points, boundaries, before, after, host);
            if (outOfSlice.isPresent()) {
                return outOfSlice;
            }
            // Forge 26.2 rewrote the fluid jump around FluidType (jumpInFluid at three places); a handler that swaps
            // the fluid tag of jumpInLiquid(LAVA) has no tag argument left to change.
            if (targetName.equals("net/minecraft/world/entity/LivingEntity")
                    && name.equals("aiStep")
                    && handler.methodName().stringValue().equals("swapFluidTag")) {
                return Optional.of(new Injection(
                        where + " at MIXINEXTRAS:EXPRESSION this.jumpInLiquid(LAVA): " + host.hostName()
                                + " replaced jumpInLiquid(TagKey) with jumpInFluid(FluidType), leaving no tag argument for the handler to change"
                                + "; this injection does not run",
                        null));
            }
            for (String[] point : points) {
                if (point[1] == null || !CHECKED_POINTS.contains(point[0])) {
                    continue;
                }
                boolean inVanilla = before.stream().anyMatch(method -> contains(method, point[1]));
                boolean inPatched = after.stream().anyMatch(method -> contains(method, point[1]));
                if (inVanilla && inPatched && RECEIVER_INJECTORS.contains(type)) {
                    Optional<String> changedKind = changedKind(point[1], before, after);
                    if (changedKind.isPresent()) {
                        return Optional.of(new Injection(
                                where + " at " + point[0] + " " + point[1] + ": " + host.hostName()
                                        + "'s patch makes that " + point[0].toLowerCase(Locale.ROOT) + " with "
                                        + changedKind.get() + ", so the handler, written for the vanilla one, has a"
                                        + " receiver too many or too few; this injection does not run",
                                null));
                    }
                }
                if (inVanilla
                        && inPatched
                        && point[0].equals("INVOKE")
                        && HostOptionalResults.readsLeftAsOptional(after, point[1])
                        && !HostOptionalResults.readsLeftAsOptional(before, point[1])) {
                    Optional<Injection> optional = optionalLeft(handler, type, where, point[1], host);
                    if (optional.isPresent()) {
                        return optional;
                    }
                }
                if (inVanilla && inPatched && before.size() == 1 && after.size() == 1 && point[0].equals("INVOKE")) {
                    Optional<Injection> replaced =
                            hostArgument(handler, type, injector, where, point[1], before, after, host, hostOwned);
                    if (replaced.isPresent()) {
                        return replaced;
                    }
                }
                if (inVanilla && inPatched && point[0].equals("NEW")) {
                    Optional<Injection> changed =
                            changedConstructor(handler, type, where, point[1], before, after, host);
                    if (changed.isPresent()) {
                        return changed;
                    }
                }
                if (!inVanilla || inPatched) {
                    continue;
                }
                String method = name;
                for (InjectionPointReplacement replacement : host.injectionPointReplacements()) {
                    if (!replacement.targetClass().equals(targetName)
                            || !replacement.method().equals(method)
                            || !replacement.replaced().equals(point[1])) {
                        continue;
                    }
                    if (replacement.placement() == InjectionPointReplacement.Placement.SAME_PLACE
                            && after.stream().anyMatch(found -> contains(found, replacement.replacement()))) {
                        if (HostHopperTransfer.covers(point[1], replacement.replacement())) {
                            if (HostHopperTransfer.fits(handler, type, point[0], point[1], replacement.replacement())) {
                                return Optional.of(Injection.replacedCall(
                                        where + " at " + point[1] + ": " + host.hostName() + " looks up its"
                                                + " container or item handler with " + replacement.replacement()
                                                + " there; the injection runs after it with the container it found,"
                                                + " unless it found its own item handler",
                                        new ReplacedCall(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                point[1],
                                                replacement.replacement())));
                            }
                            continue;
                        }
                        if (type.equals(INJECT)
                                || sameShape(point[1], replacement.replacement())
                                || (type.equals(MODIFY_EXPRESSION_VALUE)
                                        && sameReturnType(point[1], replacement.replacement()))) {
                            return Optional.of(new Injection(
                                    where + " at " + point[1] + ": " + host.hostName() + " patches that call to "
                                            + replacement.replacement() + ", where it now applies",
                                    new Retarget(
                                            handler.methodName().stringValue(),
                                            handler.methodType().stringValue(),
                                            point[1],
                                            replacement.replacement())));
                        }
                        if (HostReplacedCalls.fits(handler, type, point[1], replacement.replacement())) {
                            return Optional.of(Injection.replacedCall(
                                    where + " at " + point[1] + ": " + host.hostName() + " calls "
                                            + replacement.replacement() + " there; the wrap runs on that call with the"
                                            + " host's arguments and result",
                                    new ReplacedCall(
                                            handler.methodName().stringValue(),
                                            handler.methodType().stringValue(),
                                            point[1],
                                            replacement.replacement())));
                        }
                        if (HostEffectRemoval.fits(
                                handler, type, targetName, method, point[1], replacement.replacement())) {
                            return Optional.of(Injection.replacedCall(
                                    where + " at " + point[1] + ": " + host.hostName() + " removes the effects its"
                                            + " event lets go with " + replacement.replacement() + " there; the wrap"
                                            + " runs on that removal and puts back what Fabric's listeners keep",
                                    new ReplacedCall(
                                            handler.methodName().stringValue(),
                                            handler.methodType().stringValue(),
                                            point[1],
                                            replacement.replacement())));
                        }
                        if (type.equals(REDIRECT) && inPlaceRedirect(handler, point[1], replacement.replacement())) {
                            String handlerName = handler.methodName().stringValue();
                            String handlerDescriptor = handler.methodType().stringValue();
                            return Optional.of(new Injection(
                                    where + " at " + point[1] + ": " + host.hostName()
                                            + " captures the argument at " + replacement.replacement()
                                            + " before copying; the redirect populates it through @ModifyArg there",
                                    new Retarget(handlerName, handlerDescriptor, point[1], replacement.replacement()),
                                    new InPlaceRedirect(handlerName, handlerDescriptor, replacement.replacement())));
                        }
                        if (targetName.equals("net/minecraft/world/entity/LivingEntity")) {
                            if (method.equals("getBedOrientation")
                                    && replacement.replaced().contains("BedBlock;getBedOrientation(")
                                    && type.equals(WRAP_OPERATION)) {
                                return Optional.of(Injection.bedWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + " there; adapted via @WrapOperation bridge",
                                        new BedWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "getBedOrientation",
                                                "()Lnet/minecraft/core/Direction;",
                                                replacement.replacement(),
                                                BedWrap.Kind.DIRECTION)));
                            }
                            if (method.equals("lambda$stopSleeping$0")
                                    && (replacement.replaced().contains("Level;setBlock(")
                                            || replacement.replaced().contains("Level;setBlockAndUpdate("))
                                    && type.equals(REDIRECT)) {
                                return Optional.of(Injection.bedWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + " there; adapted via @WrapOperation bridge",
                                        new BedWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "lambda$stopSleeping$0",
                                                "(Lnet/minecraft/core/BlockPos;)V",
                                                replacement.replacement(),
                                                BedWrap.Kind.OCCUPIED)));
                            }
                        }
                        if (targetName.equals("net/minecraft/world/entity/vehicle/boat/AbstractBoat")) {
                            if ((method.equals("checkInWater") || method.equals("isUnderwater"))
                                    && replacement.replaced().contains("FluidState;is(")
                                    && type.equals(WRAP_OPERATION)
                                    && fluidCheckWrap(handler, false)) {
                                return Optional.of(Injection.fluidWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + " there; a generated @WrapOperation bridge wraps Forge's call, invoking it once and running the Fabric handler with the fluid state and Forge's result",
                                        new FluidWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                method,
                                                "()Z",
                                                replacement.replacement(),
                                                FluidWrap.Kind.BOAT_SUPPORT)));
                            }
                        }
                        if (targetName.equals("net/minecraft/world/entity/Entity")) {
                            if (method.equals("updateSwimming")
                                    && replacement.replaced().contains("FluidState;is(")
                                    && type.equals(WRAP_OPERATION)
                                    && fluidCheckWrap(handler, true)) {
                                return Optional.of(Injection.fluidWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + " there; a generated @WrapOperation bridge calls Forge once, reads the fluid state at the entity's block position, and runs the Fabric handler with that state and Forge's result",
                                        new FluidWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "updateSwimming",
                                                "()V",
                                                replacement.replacement(),
                                                FluidWrap.Kind.SWIMMING)));
                            }
                        }
                        // Forge moved vanilla's air supply code into ForgeHooks.onLivingBreathe, which decides from
                        // FluidType; no boolean is left for a handler of isEyeInFluid(TagKey) to change.
                        if (targetName.equals("net/minecraft/world/entity/LivingEntity")
                                && method.equals("baseTick")
                                && replacement.replaced().contains("LivingEntity;isEyeInFluid(")
                                && type.equals(MODIFY_EXPRESSION_VALUE)) {
                            return Optional.of(new Injection(
                                    where + " at " + point[1] + ": " + host.hostName() + " calls "
                                            + replacement.replacement()
                                            + " instead, which moves the air supply update into that hook based on fluid types, leaving no boolean for the handler to modify"
                                            + "; this injection does not run",
                                    null));
                        }
                        if (targetName.equals("net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity")) {
                            if (method.equals("consumeFuel")
                                    && replacement.replaced().contains("Item;getCraftingRemainder(")
                                    && type.equals(REDIRECT)
                                    && onlyStackRemainder(handler)) {
                                return Optional.of(Injection.itemWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + HOST_MAKES_HANDLER_CALL,
                                        new ItemWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "consumeFuel",
                                                "",
                                                replacement.replacement(),
                                                ItemWrap.Kind.FURNACE_REMAINDER)));
                            }
                        }
                        if (targetName.equals("net/minecraft/world/level/block/entity/BrewingStandBlockEntity")) {
                            if (method.equals("doBrew")
                                    && replacement.replaced().contains("Item;getCraftingRemainder(")
                                    && type.equals(REDIRECT)
                                    && onlyStackRemainder(handler)) {
                                return Optional.of(Injection.itemWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + HOST_MAKES_HANDLER_CALL,
                                        new ItemWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "doBrew",
                                                "",
                                                replacement.replacement(),
                                                ItemWrap.Kind.BREWING_REMAINDER)));
                            }
                        }
                        if (targetName.equals("net/minecraft/world/item/crafting/CraftingRecipe")) {
                            if (method.equals("defaultCraftingReminder")
                                    && replacement.replaced().contains("ItemStack;getItem(")
                                    && type.equals(WRAP_OPERATION)
                                    && onlyCapturesStack(handler)) {
                                return Optional.of(Injection.itemWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + " directly; this handler only stores the stack in a local for another handler to query its remainder, which Forge already calls, so dropping the injection preserves behavior",
                                        new ItemWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "defaultCraftingReminder",
                                                "",
                                                replacement.replacement(),
                                                ItemWrap.Kind.CRAFTING_CAPTURE)));
                            }
                            if (method.equals("defaultCraftingReminder")
                                    && replacement.replaced().contains("Item;getCraftingRemainder(")
                                    && type.equals(REDIRECT)
                                    && onlyStackRemainder(handler)) {
                                return Optional.of(Injection.itemWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + HOST_MAKES_HANDLER_CALL,
                                        new ItemWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "defaultCraftingReminder",
                                                "",
                                                replacement.replacement(),
                                                ItemWrap.Kind.CRAFTING_REMAINDER)));
                            }
                        }
                        if (targetName.equals("net/minecraft/world/item/enchantment/EnchantmentHelper")) {
                            if (method.equals("lambda$getAvailableEnchantmentResults$0")
                                    && replacement.replaced().contains("Enchantment;isPrimaryItem(")
                                    && type.equals(REDIRECT)
                                    && onlyFabricEnchantingCheck(handler)) {
                                return Optional.of(Injection.itemWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + " there; a generated @WrapOperation bridge runs Fabric's PRIMARY check, using Fabric's answer if it differs from vanilla isPrimaryItem and otherwise using Forge's answer",
                                        new ItemWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "lambda$getAvailableEnchantmentResults$0",
                                                "",
                                                replacement.replacement(),
                                                ItemWrap.Kind.ENCHANTMENT_CHECK)));
                            }
                        }
                        if (targetName.equals("net/minecraft/data/registries/RegistryPatchGenerator")) {
                            if (method.equals("lambda$createLookup$0")
                                    && replacement.replaced().contains("WORLDGEN_REGISTRIES")
                                    && type.equals(REDIRECT)) {
                                return Optional.of(Injection.registrySyncWrap(
                                        where + " at " + point[1] + ": " + host.hostName() + " calls "
                                                + replacement.replacement()
                                                + " there; adapted via @WrapOperation bridge",
                                        new RegistrySyncWrap(
                                                handler.methodName().stringValue(),
                                                handler.methodType().stringValue(),
                                                "lambda$createLookup$0",
                                                "",
                                                replacement.replacement())));
                            }
                        }
                    }
                    if (HostListExtensions.fits(
                            handler, type, point[0], point[1], replacement, after, patchedClasses)) {
                        return Optional.of(Injection.hostList(
                                where + " at " + point[1] + ": " + host.hostName() + " reads it through "
                                        + replacement.replacement() + ", which adds the host's own entries; the"
                                        + " redirect moves there and returns its list followed by them",
                                new HostList(
                                        handler.methodName().stringValue(),
                                        handler.methodType().stringValue(),
                                        point[1],
                                        replacement.replacement())));
                    }
                    if (replacement.placement() == InjectionPointReplacement.Placement.IN_METHOD) {
                        String rep = replacement.replacement();
                        int colon = rep.indexOf(':');
                        String movedMethodSpec = colon < 0 ? rep : rep.substring(0, colon);
                        String newTarget = colon < 0 ? point[1] : rep.substring(colon + 1);

                        int paren = movedMethodSpec.indexOf('(');
                        String movedName = paren < 0 ? movedMethodSpec : movedMethodSpec.substring(0, paren);
                        String movedDescriptor = paren < 0 ? null : movedMethodSpec.substring(paren);
                        List<MethodModel> movedMethods = methods(patched, movedName, movedDescriptor);
                        if (movedMethods.stream().anyMatch(found -> contains(found, newTarget))
                                && !capturedArgumentsFit(handler, type, before, movedMethods)) {
                            return Optional.of(new Injection(
                                    where + " at " + point[1] + ": " + host.hostName() + " moved that call to "
                                            + targetName + "." + movedMethodSpec + ", whose parameters do not start"
                                            + " with the target method's parameters the handler takes; this injection"
                                            + " does not run",
                                    null));
                        }
                        if (movedMethods.stream().anyMatch(found -> contains(found, newTarget))) {
                            MethodRetarget methodRetarget = new MethodRetarget(
                                    handler.methodName().stringValue(),
                                    handler.methodType().stringValue(),
                                    selector,
                                    movedMethodSpec);
                            if (colon < 0) {
                                return Optional.of(Injection.methodRetarget(
                                        where + " at " + point[1] + ": " + host.hostName() + " moved that call to "
                                                + targetName + "." + movedMethodSpec + ", where it now applies",
                                        methodRetarget));
                            } else {
                                Retarget retarget = new Retarget(
                                        handler.methodName().stringValue(),
                                        handler.methodType().stringValue(),
                                        point[1],
                                        newTarget);
                                return Optional.of(Injection.methodAndPointRetarget(
                                        where + " at " + point[1] + ": " + host.hostName() + " moved that call to "
                                                + targetName + "." + movedMethodSpec + " as " + newTarget
                                                + ", where it now applies",
                                        methodRetarget,
                                        retarget));
                            }
                        }
                    }
                    if (replacement.placement() == InjectionPointReplacement.Placement.AFTER_TRUE_RESULT
                            && points.size() == 1) {
                        Optional<Wrap> wrap =
                                afterTrueResult(handler, type, injector, point, after, patched, replacement);
                        if (wrap.isPresent()) {
                            return Optional.of(new Injection(
                                    where + " at " + point[1] + ": " + host.hostName() + " makes that call inside "
                                            + replacement.replacement() + " when it returns true; the injection runs"
                                            + " after each call of " + replacement.replacement()
                                            + " that returned true",
                                    null,
                                    null,
                                    null,
                                    wrap.get()));
                        }
                    }
                }
                Optional<Injection> moved =
                        appendedArguments(handler, type, injector, where, point, points, before, after, host);
                if (moved.isPresent()) {
                    return moved;
                }
                if (HostThreadDump.fits(handler, type, targetName, name, point[1], patched)) {
                    return Optional.of(Injection.replacedCall(
                            where + " at " + point[1] + ": " + host.hostName()
                                    + " moved the watchdog crash report body into createWatchdogCrashReport(String, long, String), where a generated @WrapOperation bridge on ThreadInfoUtil.getEntireStacktrace runs the handler",
                            new ReplacedCall(
                                    handler.methodName().stringValue(),
                                    handler.methodType().stringValue(),
                                    HostThreadDump.REPLACED,
                                    HostThreadDump.REPLACEMENT)));
                }
                return Optional.of(new Injection(
                        where + " at " + point[0] + " " + point[1] + ": " + host.hostName()
                                + "'s patch removed that " + point[0].toLowerCase(Locale.ROOT)
                                + " there; this injection does not run",
                        null));
            }
        }
        return Optional.empty();
    }

    /**
     * An injection at a call whose {@code Either} result the patch reads with an {@code Optional} of the vanilla value on
     * the left side ({@link HostOptionalResults}): a {@code @WrapOperation} whose handler the bridge can carry runs
     * through it; any other {@code @WrapOperation}, and a {@code @Redirect} or {@code @ModifyExpressionValue} (which
     * would hand the patch the vanilla value), does not run. Injectors that do not see the result are left as they are.
     */
    private static Optional<Injection> optionalLeft(
            MethodModel handler, String type, String where, String target, HostedLaunch host) {
        String change = where + " at INVOKE " + target + ": " + host.hostName()
                + "'s patch reads the left side of that call's " + HostOptionalResults.EITHER_NAME
                + " as a java/util/Optional of the value the vanilla game gets";
        if (type.equals(WRAP_OPERATION)) {
            Optional<String> unsupported = HostOptionalResults.unsupportedHandler(handler, target);
            if (unsupported.isEmpty()) {
                return Optional.of(Injection.optionalLeft(
                        change + "; the handler gets the value, its result goes back into an Optional, and an element"
                                + " the patch skips (an empty Optional) does not reach it",
                        new OptionalLeft(
                                handler.methodName().stringValue(),
                                handler.methodType().stringValue())));
            }
            return Optional.of(
                    new Injection(change + ", and " + unsupported.get() + "; this injection does not run", null));
        }
        if (type.equals(REDIRECT) || type.equals(MODIFY_EXPRESSION_VALUE)) {
            return Optional.of(new Injection(
                    change + ", which the handler, written for the vanilla value, would replace; this injection"
                            + " does not run",
                    null));
        }
        return Optional.empty();
    }

    /**
     * An {@code @Inject} or {@code @WrapOperation} at {@code INVOKE} of a call the patch makes with more arguments at the
     * end (ADR-0026 40): in the patched method, the same owner and name, the vanilla parameters followed by others, the
     * same return type, and at least as many such calls as the vanilla method has of the vanilla one (as many when the
     * injection has an ordinal), while the vanilla call is
     * gone. An {@code @Inject} only needs the place and moves to the patched call; a {@code @WrapOperation} runs through
     * a bridge ({@link Appended}). Other injectors there are left as they are (reported as not running).
     */
    private static Optional<Injection> appendedArguments(
            MethodModel handler,
            String type,
            Annotation injector,
            String where,
            String[] point,
            List<String[]> points,
            List<MethodModel> before,
            List<MethodModel> after,
            HostedLaunch host) {
        if (!point[0].equals("INVOKE")
                || points.size() != 1
                || !(type.equals(INJECT) || type.equals(WRAP_OPERATION))
                || !point[1].contains("(")) {
            return Optional.empty();
        }
        String owner = point[1].substring(1, point[1].indexOf(';'));
        String rest = point[1].substring(point[1].indexOf(';') + 1);
        String name = rest.substring(0, rest.indexOf('('));
        MethodTypeDesc vanilla = MethodTypeDesc.ofDescriptor(rest.substring(rest.indexOf('(')));
        Map<String, Integer> vanillaCalls = calls(before, owner, name);
        Map<String, Integer> patchedCalls = calls(after, owner, name);
        int count = vanillaCalls.getOrDefault(vanilla.descriptorString(), 0);
        List<String> extended = patchedCalls.keySet().stream()
                .filter(candidate -> {
                    MethodTypeDesc patched = MethodTypeDesc.ofDescriptor(candidate);
                    return patched.parameterCount() > vanilla.parameterCount()
                            && patched.returnType().equals(vanilla.returnType())
                            && patched.parameterList()
                                    .subList(0, vanilla.parameterCount())
                                    .equals(vanilla.parameterList());
                })
                .toList();
        // Without an ordinal the injection applies at every such call, as Mixin does; NeoForge's patch builds the
        // serverbound payload codec twice (play, configuration) where vanilla builds one.
        boolean ordinal = injector.toString().contains("ordinal");
        int patchedCount = extended.size() == 1 ? patchedCalls.get(extended.getFirst()) : 0;
        if (count == 0 || extended.size() != 1 || (ordinal ? patchedCount != count : patchedCount < count)) {
            return Optional.empty();
        }
        String replacement = "L" + owner + ";" + name + extended.getFirst();
        String handlerName = handler.methodName().stringValue();
        String handlerDescriptor = handler.methodType().stringValue();
        String reason = where + " at INVOKE " + point[1] + ": " + host.hostName() + "'s patch calls " + replacement
                + " there, with more arguments at the end; the injection still runs";
        if (type.equals(INJECT)) {
            return Optional.of(
                    new Injection(reason, new Retarget(handlerName, handlerDescriptor, point[1], replacement)));
        }
        if (handler.methodTypeSymbol().parameterList().stream().noneMatch(OPERATION::equals)) {
            return Optional.empty();
        }
        return Optional.of(new Injection(
                reason, null, null, null, null, new Appended(handlerName, handlerDescriptor, point[1], replacement)));
    }

    /**
     * How the patch changed the kind of the instructions {@code target} names in the injection's methods, if it made a
     * static one an instance one or the reverse: {@code "invokevirtual where vanilla has invokestatic"}. A handler that
     * takes the receiver of an instance call or field ({@link #RECEIVER_INJECTORS}) has one parameter more than for a
     * static one, so Mixin rejects it on the other kind. Only a kind the vanilla methods do not have counts: without an
     * ordinal Mixin applies at every match.
     */
    private static Optional<String> changedKind(String target, List<MethodModel> before, List<MethodModel> after) {
        Map<Boolean, String> vanilla = kinds(before, target);
        Map<Boolean, String> patched = kinds(after, target);
        return patched.entrySet().stream()
                .filter(kind -> !vanilla.containsKey(kind.getKey()))
                .findFirst()
                .map(kind -> kind.getValue() + " where vanilla has " + String.join(" and ", vanilla.values()));
    }

    /** The calls and field accesses {@code target} names in {@code methods}: whether static, to an opcode's name. */
    private static Map<Boolean, String> kinds(List<MethodModel> methods, String target) {
        Map<Boolean, String> found = new TreeMap<>();
        for (MethodModel method : methods) {
            method.code().ifPresent(code -> {
                for (CodeElement element : code) {
                    if ((element instanceof InvokeInstruction || element instanceof FieldInstruction)
                            && element instanceof java.lang.classfile.Instruction instruction
                            && containsInstruction(instruction, target)) {
                        Opcode opcode = instruction.opcode();
                        boolean isStatic = opcode == Opcode.INVOKESTATIC
                                || opcode == Opcode.GETSTATIC
                                || opcode == Opcode.PUTSTATIC;
                        found.putIfAbsent(isStatic, opcode.name().toLowerCase(Locale.ROOT));
                    }
                }
            });
        }
        return found;
    }

    /** How many calls of {@code owner.name} each descriptor has in {@code methods}. */
    private static Map<String, Integer> calls(List<MethodModel> methods, String owner, String name) {
        Map<String, Integer> found = new HashMap<>();
        for (MethodModel method : methods) {
            method.code().ifPresent(code -> {
                for (CodeElement element : code) {
                    if (element instanceof InvokeInstruction call
                            && call.owner().asInternalName().equals(owner)
                            && call.name().equalsString(name)) {
                        found.merge(call.type().stringValue(), 1, Integer::sum);
                    }
                }
            });
        }
        return found;
    }

    /**
     * Adds {@code appended}'s bridge for {@code handler}: a {@code @WrapOperation} on the patched call that takes its
     * receiver (if any) and arguments, its {@code Operation} and the handler's sugar parameters, and calls the handler
     * with the receiver, the vanilla arguments and an {@link AppendedArguments} holding the added ones.
     */
    private static void appendedMethod(
            java.lang.classfile.ClassBuilder builder,
            ClassModel mixin,
            MethodModel handler,
            Appended appended,
            Annotation wrapOperation) {
        MethodTypeDesc handlerType = handler.methodTypeSymbol();
        String replacement = appended.replacement();
        MethodTypeDesc patchedCall = MethodTypeDesc.ofDescriptor(replacement.substring(replacement.indexOf('(')));
        String replaced = appended.replaced();
        MethodTypeDesc vanillaCall = MethodTypeDesc.ofDescriptor(replaced.substring(replaced.indexOf('(')));
        List<ClassDesc> added =
                patchedCall.parameterList().subList(vanillaCall.parameterCount(), patchedCall.parameterCount());
        int operation = handlerType.parameterList().indexOf(OPERATION);
        List<ClassDesc> bridgeParameters =
                new ArrayList<>(handlerType.parameterList().subList(0, operation));
        bridgeParameters.addAll(added);
        bridgeParameters.addAll(handlerType.parameterList().subList(operation, handlerType.parameterCount()));
        MethodTypeDesc bridgeType = MethodTypeDesc.of(handlerType.returnType(), bridgeParameters);
        int[] placement = new int[handlerType.parameterCount()];
        for (int index = 0; index < placement.length; index++) {
            placement[index] = index < operation ? index : index + added.size();
        }
        int flags = handler.flags().flagsMask();
        boolean isStatic = (flags & ClassFile.ACC_STATIC) != 0;
        boolean isInterface = mixin.flags().has(AccessFlag.INTERFACE);
        ClassDesc owner = mixin.thisClass().asSymbol();
        builder.withMethod(APPENDED_PREFIX + handler.methodName().stringValue(), bridgeType, flags, method -> {
            method.with(RuntimeVisibleAnnotationsAttribute.of(wrapOperation));
            placedParameterAnnotations(handler, placement, bridgeType.parameterCount(), true)
                    .ifPresent(method::with);
            placedParameterAnnotations(handler, placement, bridgeType.parameterCount(), false)
                    .ifPresent(method::with);
            method.withCode(code -> {
                int[] slots = new int[bridgeType.parameterCount()];
                int slot = isStatic ? 0 : 1;
                for (int index = 0; index < slots.length; index++) {
                    slots[index] = slot;
                    slot += TypeKind.from(bridgeType.parameterType(index)).slotSize();
                }
                if (!isStatic) {
                    code.aload(0);
                }
                for (int index = 0; index < operation; index++) {
                    code.loadLocal(TypeKind.from(bridgeType.parameterType(index)), slots[index]);
                }
                code.new_(APPENDED_ARGUMENTS);
                code.dup();
                code.aload(slots[operation + added.size()]);
                code.loadConstant(added.size());
                code.anewarray(ConstantDescs.CD_Object);
                for (int index = 0; index < added.size(); index++) {
                    code.dup();
                    code.loadConstant(index);
                    ClassDesc type = added.get(index);
                    code.loadLocal(TypeKind.from(type), slots[operation + index]);
                    box(code, type);
                    code.aastore();
                }
                code.invokespecial(
                        APPENDED_ARGUMENTS,
                        "<init>",
                        MethodTypeDesc.of(ConstantDescs.CD_void, OPERATION, ConstantDescs.CD_Object.arrayType()));
                for (int index = operation + 1; index < handlerType.parameterCount(); index++) {
                    int from = index + added.size();
                    code.loadLocal(TypeKind.from(bridgeType.parameterType(from)), slots[from]);
                }
                if (isStatic) {
                    code.invokestatic(owner, handler.methodName().stringValue(), handlerType, isInterface);
                } else {
                    code.invokespecial(owner, handler.methodName().stringValue(), handlerType, isInterface);
                }
                code.return_(TypeKind.from(handlerType.returnType()));
            });
        });
    }

    /**
     * An injection at {@code NEW} of a class (no constructor descriptor) whose handler is written for the constructor's
     * parameters, where the host's patch calls another constructor (ADR-0026 39). Fabric API's {@code
     * RenderPipelineBuilderMixin} wraps {@code new RenderPipeline.Snippet(...)} with eleven parameters, and NeoForge's
     * patch passes twelve: Mixin rejects the handler's signature and, with it, every other mixin on the class. Such an
     * injection is taken out. A constructor the vanilla method calls that the patched method still calls changes nothing.
     */
    private static Optional<Injection> changedConstructor(
            MethodModel handler,
            String type,
            String where,
            String target,
            List<MethodModel> before,
            List<MethodModel> after,
            HostedLaunch host) {
        if (!CALL_SIGNATURE_INJECTORS.contains(type) || target.contains("(")) {
            return Optional.empty();
        }
        String owner = target.startsWith("L") && target.endsWith(";")
                ? target.substring(1, target.length() - 1)
                : target.replace('.', '/');
        Set<String> vanilla = constructors(before, owner);
        Set<String> patched = constructors(after, owner);
        if (vanilla.isEmpty() || patched.containsAll(vanilla)) {
            return Optional.empty();
        }
        return Optional.of(new Injection(
                where + " at NEW " + target + ": " + host.hostName() + "'s patch calls the constructor "
                        + String.join(", ", new TreeSet<>(patched)) + " where the game calls "
                        + String.join(", ", new TreeSet<>(vanilla))
                        + ", and the handler is written for the game's; this injection does not run",
                null,
                null,
                new TakenOut(
                        handler.methodName().stringValue(), handler.methodType().stringValue())));
    }

    /** The descriptors of {@code owner}'s constructors that {@code methods} call. */
    private static Set<String> constructors(List<MethodModel> methods, String owner) {
        Set<String> descriptors = new HashSet<>();
        for (MethodModel method : methods) {
            method.code().ifPresent(code -> {
                for (CodeElement element : code) {
                    if (element instanceof InvokeInstruction call
                            && call.opcode() == Opcode.INVOKESPECIAL
                            && call.name().equalsString("<init>")
                            && call.owner().asInternalName().equals(owner)) {
                        descriptors.add(call.type().stringValue());
                    }
                }
            });
        }
        return descriptors;
    }

    /**
     * A {@code @ModifyArg} or {@code @ModifyArgs} at a call whose argument the host's patch computes with its own code
     * where the vanilla game reads its own value ({@link HostArguments}): the injection would replace what the patch
     * passes there, so it is taken out.
     */
    private static Optional<Injection> hostArgument(
            MethodModel handler,
            String type,
            Annotation injector,
            String where,
            String target,
            List<MethodModel> before,
            List<MethodModel> after,
            HostedLaunch host,
            Predicate<String> hostOwned) {
        int index;
        if (type.equals(MODIFY_ARGS)) {
            index = -1;
        } else if (type.equals(MODIFY_ARG)) {
            index = argumentIndex(handler, injector, target);
            if (index < 0) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
        return HostArguments.hostProduced(before.getFirst(), after.getFirst(), target, index, hostOwned)
                .map(producer -> new Injection(
                        where + " at INVOKE " + target + ": " + host.hostName() + "'s patch passes its own " + producer
                                + " there, which this injection would replace; this injection does not run",
                        null,
                        null,
                        new TakenOut(
                                handler.methodName().stringValue(),
                                handler.methodType().stringValue())));
    }

    /**
     * The argument a {@code @ModifyArg} changes: its {@code index}, or without one the only argument of {@code target}
     * with the handler's return type (Mixin's rule); {@code -1} when neither says.
     */
    private static int argumentIndex(MethodModel handler, Annotation injector, String target) {
        for (AnnotationElement element : injector.elements()) {
            if (element.name().equalsString("index") && element.value() instanceof AnnotationValue.OfInt value) {
                return value.intValue();
            }
        }
        int open = target.indexOf('(');
        if (open < 0) {
            return -1;
        }
        MethodTypeDesc call = MethodTypeDesc.ofDescriptor(target.substring(open));
        ClassDesc changed = handler.methodTypeSymbol().returnType();
        int found = -1;
        for (int i = 0; i < call.parameterCount(); i++) {
            if (call.parameterType(i).equals(changed)) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
        }
        return found;
    }

    /**
     * An {@code @Inject} handler written for {@code vanilla}'s parameters, on a target the patch gave other parameters
     * ({@code patched}): a bridge when the patch only added parameters or reordered them ({@link #positions}), else the
     * injection is taken out. Empty when the
     * handler takes no target parameters (Mixin accepts it as it is) or was not written for the vanilla ones.
     */
    private static Optional<Injection> changedParameters(
            MethodModel handler, String where, MethodModel vanilla, MethodModel patched, HostedLaunch host) {
        MethodTypeDesc vanillaType = vanilla.methodTypeSymbol();
        MethodTypeDesc patchedType = patched.methodTypeSymbol();
        MethodTypeDesc handlerType = handler.methodTypeSymbol();
        int count = vanillaType.parameterCount();
        if (vanillaType.equals(patchedType)
                || !handlerType.returnType().equals(ConstantDescs.CD_void)
                || handlerType.parameterCount() <= count
                || !CALLBACK_INFOS.contains(handlerType.parameterType(count).descriptorString())
                || count == 0) {
            return Optional.empty();
        }
        for (int index = 0; index < count; index++) {
            if (!handlerType.parameterType(index).equals(vanillaType.parameterType(index))) {
                return Optional.empty();
            }
        }
        String handlerName = handler.methodName().stringValue();
        String handlerDescriptor = handlerType.descriptorString();
        String change = host.hostName() + " patches that method from " + vanillaType.descriptorString() + " to "
                + patchedType.descriptorString();
        Optional<List<Integer>> placed = positions(vanillaType, patchedType);
        if (placed.isEmpty() || !vanillaType.returnType().equals(patchedType.returnType())) {
            return Optional.of(new Injection(
                    where + ": " + change
                            + ", which the handler's parameters do not match; this injection does not run",
                    null,
                    null,
                    new TakenOut(handlerName, handlerDescriptor)));
        }
        List<ClassDesc> bridgeParameters = new ArrayList<>(patchedType.parameterList());
        bridgeParameters.addAll(handlerType.parameterList().subList(count, handlerType.parameterCount()));
        MethodTypeDesc bridgeType = MethodTypeDesc.of(ConstantDescs.CD_void, bridgeParameters);
        return Optional.of(new Injection(
                where + ": " + change + "; the injection runs with the patched parameters and passes the handler the"
                        + " ones it was written for",
                null,
                new Bridge(handlerName, handlerDescriptor, bridgeType.descriptorString(), placed.get()),
                null));
    }

    /**
     * For each of {@code vanilla}'s parameters, its index among {@code patched}'s: in order when the patch only added
     * parameters; otherwise by type, when each vanilla parameter's type is among the patched parameters exactly once
     * (the patch reordered them, as a lambda whose captured values changed). Empty when neither places them all.
     */
    private static Optional<List<Integer>> positions(MethodTypeDesc vanilla, MethodTypeDesc patched) {
        List<Integer> inOrder = new ArrayList<>();
        int next = 0;
        for (ClassDesc parameter : vanilla.parameterList()) {
            while (next < patched.parameterCount()
                    && !patched.parameterType(next).equals(parameter)) {
                next++;
            }
            if (next == patched.parameterCount()) {
                break;
            }
            inOrder.add(next++);
        }
        if (inOrder.size() == vanilla.parameterCount()) {
            return Optional.of(List.copyOf(inOrder));
        }
        List<Integer> byType = new ArrayList<>();
        for (ClassDesc parameter : vanilla.parameterList()) {
            List<Integer> matches = new ArrayList<>();
            for (int index = 0; index < patched.parameterCount(); index++) {
                if (patched.parameterType(index).equals(parameter)) {
                    matches.add(index);
                }
            }
            if (matches.size() != 1 || byType.contains(matches.getFirst())) {
                return Optional.empty();
            }
            byType.add(matches.getFirst());
        }
        return Optional.of(List.copyOf(byType));
    }

    /**
     * Whether two targets in Mixin's notation have the same owner and descriptor (a call or field of the same shape),
     * so an injector bound to the call's arguments and result fits both.
     */
    private static boolean sameShape(String replaced, String replacement) {
        return owner(replaced).equals(owner(replacement)) && shape(replaced).equals(shape(replacement));
    }

    private static String owner(String target) {
        return target.startsWith("L") && target.indexOf(';') > 0 ? target.substring(0, target.indexOf(';') + 1) : "";
    }

    private static String shape(String target) {
        int call = target.indexOf('(');
        int field = target.indexOf(':');
        int from = call >= 0 ? call : field;
        return from < 0 ? "" : target.substring(from);
    }

    private static boolean sameReturnType(String replaced, String replacement) {
        String r1 = returnType(replaced);
        String r2 = returnType(replacement);
        return !r1.isEmpty() && r1.equals(r2);
    }

    private static String returnType(String target) {
        int r = target.lastIndexOf(')');
        return r >= 0 ? target.substring(r + 1) : "";
    }

    /**
     * The {@code @WrapOperation} bridge that runs an {@code @Inject} handler after each true result of the helper call
     * {@code replacement} names ({@link InjectionPointReplacement.Placement#AFTER_TRUE_RESULT}), when everything holds
     * that makes that the same: the injection is a plain, non-cancellable {@code @Inject} at {@code INVOKE} without
     * local capture, slice, shift or ordinal; the patched class has the helper, returning {@code boolean} and making the
     * replaced call, and the target method calls it; the handler returns nothing and takes the target method's
     * parameters (or none), its {@code CallbackInfo}, and then only MixinExtras sugar parameters ({@code @Local} and
     * the like, which the bridge carries). Empty otherwise: the injection then does not run.
     */
    private static Optional<Wrap> afterTrueResult(
            MethodModel handler,
            String type,
            Annotation injector,
            String[] point,
            List<MethodModel> after,
            ClassModel patched,
            InjectionPointReplacement replacement) {
        if (!type.equals(INJECT) || !point[0].equals("INVOKE") || point[2] != null || after.size() != 1) {
            return Optional.empty();
        }
        for (AnnotationElement element : injector.elements()) {
            String name = element.name().stringValue();
            boolean cancellable = name.equals("cancellable")
                    && element.value() instanceof AnnotationValue.OfBoolean flag
                    && flag.booleanValue();
            if (cancellable || name.equals("locals") || name.equals("slice")) {
                return Optional.empty();
            }
        }
        String helper = replacement.replacement();
        String owner = "L" + replacement.targetClass() + ";";
        if (!helper.startsWith(owner) || helper.indexOf('(') < 0 || !helper.endsWith(")Z")) {
            return Optional.empty();
        }
        String helperName = helper.substring(owner.length(), helper.indexOf('('));
        String helperDescriptor = helper.substring(helper.indexOf('('));
        List<MethodModel> helpers = methods(patched, helperName, helperDescriptor);
        MethodModel target = after.getFirst();
        if (helpers.size() != 1 || !contains(helpers.getFirst(), replacement.replaced()) || !contains(target, helper)) {
            return Optional.empty();
        }
        MethodTypeDesc targetType = target.methodTypeSymbol();
        MethodTypeDesc handlerType = handler.methodTypeSymbol();
        if (!handlerType.returnType().equals(ConstantDescs.CD_void)) {
            return Optional.empty();
        }
        int callbackAt;
        if (handlerType.parameterCount() > 0
                && CALLBACK_INFOS.contains(handlerType.parameterType(0).descriptorString())) {
            callbackAt = 0;
        } else {
            callbackAt = targetType.parameterCount();
            if (handlerType.parameterCount() <= callbackAt
                    || !CALLBACK_INFOS.contains(
                            handlerType.parameterType(callbackAt).descriptorString())
                    || !handlerType.parameterList().subList(0, callbackAt).equals(targetType.parameterList())) {
                return Optional.empty();
            }
        }
        List<List<Annotation>> sugar = new ArrayList<>();
        handler.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                .ifPresent(attribute -> sugar.addAll(attribute.parameterAnnotations()));
        for (int index = callbackAt + 1; index < handlerType.parameterCount(); index++) {
            boolean annotated = index < sugar.size()
                    && sugar.get(index).stream()
                            .anyMatch(annotation ->
                                    annotation.className().stringValue().startsWith(SUGAR_PACKAGE));
            if (!annotated) {
                return Optional.empty();
            }
        }
        boolean helperStatic = (helpers.getFirst().flags().flagsMask() & ClassFile.ACC_STATIC) != 0;
        return Optional.of(new Wrap(
                handler.methodName().stringValue(),
                handlerType.descriptorString(),
                target.methodName().stringValue(),
                targetType.descriptorString(),
                helper,
                helperStatic,
                callbackAt > 0,
                handlerType.parameterType(callbackAt).descriptorString()));
    }

    /**
     * Whether the target method's parameters that {@code handler} takes are the first parameters of each of {@code
     * moved}, the methods a patch moved the injection point to. Mixin hands an injector handler a prefix of the target
     * method's parameters after its own (after the {@code CallbackInfo} of an {@code @Inject}, the {@code Operation} of
     * a {@code @WrapOperation}, the call's receiver and arguments of a {@code @Redirect}, the value of the others), and
     * before MixinExtras sugar parameters. For an {@code @Inject} or {@code @WrapOperation}, those are the parameters
     * between its own and the sugar ones, when they start {@code vanilla}'s parameters (else they are captured locals,
     * which this does not judge); for the others, the longest run before the sugar ones that starts {@code vanilla}'s
     * parameters. NeoForge 26.3.0.98's {@code lambda$startSleepInBed$0} takes {@code (BlockState, BedRule, BlockPos,
     * boolean)} where vanilla's {@code startSleepInBed} takes {@code (AbstractBedBlock, BlockState, BlockPos)}.
     */
    static boolean capturedArgumentsFit(
            MethodModel handler, String type, List<MethodModel> vanilla, List<MethodModel> moved) {
        List<ClassDesc> parameters = handler.methodTypeSymbol().parameterList();
        List<List<Annotation>> annotations = new ArrayList<>();
        handler.findAttribute(java.lang.classfile.Attributes.runtimeInvisibleParameterAnnotations())
                .ifPresent(attribute -> annotations.addAll(attribute.parameterAnnotations()));
        int end = parameters.size();
        for (int index = 0; index < annotations.size() && index < parameters.size(); index++) {
            if (annotations.get(index).stream()
                    .anyMatch(annotation -> annotation.className().stringValue().startsWith(SUGAR_PACKAGE))) {
                end = index;
                break;
            }
        }
        int start = -1;
        for (int index = 0; index < end; index++) {
            String descriptor = parameters.get(index).descriptorString();
            if ((type.equals(INJECT) && CALLBACK_INFOS.contains(descriptor))
                    || (type.equals(WRAP_OPERATION) && parameters.get(index).equals(OPERATION))) {
                start = index + 1;
                break;
            }
        }
        if ((type.equals(INJECT) || type.equals(WRAP_OPERATION)) && start < 0) {
            return true;
        }
        for (MethodModel method : vanilla) {
            List<ClassDesc> targetParameters = method.methodTypeSymbol().parameterList();
            List<ClassDesc> captured;
            if (start >= 0) {
                captured = parameters.subList(start, end);
                if (!startsWith(targetParameters, captured)) {
                    return true;
                }
            } else {
                captured = List.of();
                for (int count = Math.min(end, targetParameters.size()); count > 0; count--) {
                    if (parameters.subList(end - count, end).equals(targetParameters.subList(0, count))) {
                        captured = parameters.subList(end - count, end);
                        break;
                    }
                }
            }
            if (captured.isEmpty()) {
                continue;
            }
            List<ClassDesc> taken = captured;
            if (moved.stream()
                    .anyMatch(found -> !startsWith(found.methodTypeSymbol().parameterList(), taken))) {
                return false;
            }
        }
        return true;
    }

    private static boolean startsWith(List<ClassDesc> list, List<ClassDesc> prefix) {
        return list.size() >= prefix.size() && list.subList(0, prefix.size()).equals(prefix);
    }

    static List<MethodModel> methods(ClassModel target, String name, String descriptor) {
        return target.methods().stream()
                .filter(method -> method.methodName().equalsString(name)
                        && (descriptor == null || method.methodType().equalsString(descriptor)))
                .toList();
    }

    private static boolean hasField(ClassModel target, String name) {
        return target.fields().stream().anyMatch(field -> field.fieldName().equalsString(name));
    }

    private static boolean hasField(ClassModel target, String name, String descriptor) {
        return target.fields().stream()
                .anyMatch(field -> field.fieldName().equalsString(name)
                        && field.fieldType().equalsString(descriptor));
    }

    /** The descriptor of {@code target}'s field {@code name}, if it has one. */
    private static Optional<String> fieldType(ClassModel target, String name) {
        return target.fields().stream()
                .filter(field -> field.fieldName().equalsString(name))
                .map(field -> field.fieldType().stringValue())
                .findFirst();
    }

    private static boolean hasMethod(ClassModel target, String name, String descriptor) {
        return !methods(target, name, descriptor).isEmpty();
    }

    /** The field an {@code @Accessor} without a value names: {@code getFoo}, {@code isFoo}, {@code setFoo} give {@code foo}. */
    static String accessorName(String method) {
        for (String prefix : List.of("get", "is", "set")) {
            if (method.startsWith(prefix) && method.length() > prefix.length()) {
                return decapitalize(method.substring(prefix.length()));
            }
        }
        return method;
    }

    /** The method an {@code @Invoker} without a value names: {@code callFoo}, {@code invokeFoo} give {@code foo}. */
    static String invokerName(String method) {
        for (String prefix : List.of("call", "invoke")) {
            if (method.startsWith(prefix) && method.length() > prefix.length()) {
                return decapitalize(method.substring(prefix.length()));
            }
        }
        return method;
    }

    private static String decapitalize(String name) {
        return Character.toLowerCase(name.charAt(0)) + name.substring(1);
    }

    static List<String> mixinTargets(ClassModel mixin) {
        List<String> targets = new ArrayList<>();
        annotation(mixin.attributes(), MIXIN).ifPresent(annotation -> {
            for (AnnotationElement element : annotation.elements()) {
                if (element.value() instanceof AnnotationValue.OfArray array) {
                    for (AnnotationValue value : array.values()) {
                        if (value instanceof AnnotationValue.OfClass type) {
                            String descriptor = type.className().stringValue();
                            targets.add(descriptor.substring(1, descriptor.length() - 1));
                        } else if (value instanceof AnnotationValue.OfString name) {
                            targets.add(name.stringValue().replace('.', '/'));
                        }
                    }
                }
            }
        });
        return targets;
    }

    private static List<Annotation> annotations(List<? extends Attribute<?>> attributes) {
        List<Annotation> result = new ArrayList<>();
        for (Attribute<?> attribute : attributes) {
            if (attribute instanceof RuntimeVisibleAnnotationsAttribute visible) {
                result.addAll(visible.annotations());
            } else if (attribute instanceof RuntimeInvisibleAnnotationsAttribute invisible) {
                result.addAll(invisible.annotations());
            }
        }
        return result;
    }

    static Optional<Annotation> annotation(List<? extends Attribute<?>> attributes, String type) {
        return annotations(attributes).stream()
                .filter(annotation -> annotation.className().equalsString(type))
                .findFirst();
    }

    static boolean has(List<? extends Attribute<?>> attributes, String type) {
        return annotation(attributes, type).isPresent();
    }

    /** The annotation's {@code value} text, when it has a non-empty one. */
    static Optional<String> value(Annotation annotation) {
        for (AnnotationElement element : annotation.elements()) {
            if (element.name().equalsString("value")
                    && element.value() instanceof AnnotationValue.OfString text
                    && !text.stringValue().isEmpty()) {
                return Optional.of(text.stringValue());
            }
        }
        return Optional.empty();
    }

    private static void strings(AnnotationValue value, List<String> out) {
        if (value instanceof AnnotationValue.OfString text) {
            out.add(text.stringValue());
        } else if (value instanceof AnnotationValue.OfArray array) {
            array.values().forEach(item -> strings(item, out));
        }
    }

    /**
     * The {@code @At} points that bound the {@code @Slice} annotations in {@code value}: {@code [value, target, another
     * element's name or null, "from" or "to"]}.
     */
    private static void slicePoints(AnnotationValue value, List<String[]> out) {
        if (value instanceof AnnotationValue.OfArray array) {
            array.values().forEach(item -> slicePoints(item, out));
        } else if (value instanceof AnnotationValue.OfAnnotation slice) {
            for (AnnotationElement element : slice.annotation().elements()) {
                String role = element.name().stringValue();
                if (role.equals("from") || role.equals("to")) {
                    List<String[]> bounds = new ArrayList<>();
                    points(element.value(), bounds);
                    for (String[] bound : bounds) {
                        out.add(new String[] {bound[0], bound[1], bound[2], role});
                    }
                }
            }
        }
    }

    /**
     * An injection with one slice (at most one {@code from} and one {@code to}, without ordinals) whose point the vanilla
     * method has inside the slice and the patched method has only outside it: Mixin then finds nothing. As Mixin does,
     * the slice starts at the first {@code from} instruction and ends at the last {@code to} instruction. When the slice
     * disambiguated two calls on two distinct {@code GETFIELD} receiver fields and the patch moved the sliced call before
     * the other receiver field, the slice is retargeted to end at {@code GETFIELD} of that other field.
     */
    private static Optional<Injection> outOfSlice(
            MethodModel handler,
            String where,
            List<String[]> points,
            List<String[]> boundaries,
            List<MethodModel> before,
            List<MethodModel> after,
            HostedLaunch host) {
        List<String[]> from =
                boundaries.stream().filter(bound -> bound[3].equals("from")).toList();
        List<String[]> to =
                boundaries.stream().filter(bound -> bound[3].equals("to")).toList();
        if (boundaries.isEmpty()
                || from.size() > 1
                || to.size() > 1
                || boundaries.stream()
                        .anyMatch(
                                bound -> bound[1] == null || !CHECKED_POINTS.contains(bound[0]) || bound[2] != null)) {
            return Optional.empty();
        }
        String start = from.isEmpty() ? null : from.getFirst()[1];
        String end = to.isEmpty() ? null : to.getFirst()[1];
        for (String[] point : points) {
            if (point[1] == null || !CHECKED_POINTS.contains(point[0])) {
                continue;
            }
            if (before.stream().anyMatch(method -> inSlice(method, point[1], start, end))
                    && after.stream().noneMatch(method -> inSlice(method, point[1], start, end))) {
                if (points.size() == 1 && before.size() == 1 && after.size() == 1) {
                    Optional<String> toField =
                            retargetedSliceToField(before.getFirst(), after.getFirst(), point[1], start, end);
                    if (toField.isPresent()) {
                        return Optional.of(Injection.sliceRetarget(
                                where + " at " + point[0] + " " + point[1] + ": " + host.hostName()
                                        + " moved that call before " + toField.get()
                                        + "; the slice now ends at " + toField.get(),
                                new SliceRetarget(
                                        handler.methodName().stringValue(),
                                        handler.methodType().stringValue(),
                                        toField.get())));
                    }
                }
                return Optional.of(new Injection(
                        where + " at " + point[0] + " " + point[1] + ": " + host.hostName()
                                + "'s patch left no such " + point[0].toLowerCase(Locale.ROOT)
                                + " inside the injection's slice" + (start == null ? "" : " from " + start)
                                + (end == null ? "" : " to " + end) + "; this injection does not run",
                        null));
            }
        }
        return Optional.empty();
    }

    /**
     * Whether {@code handler} is a unary {@code (P0)P0} {@code @Redirect} of {@code replaced} (also {@code (P0)P0}) and
     * {@code replacement} is a {@code (P0, ...)V} call taking that {@code P0} first.
     */
    private static boolean inPlaceRedirect(MethodModel handler, String replaced, String replacement) {
        String replacedShape = shape(replaced);
        String replacementShape = shape(replacement);
        if (!replacedShape.startsWith("(")
                || !replacementShape.startsWith("(")
                || !handler.methodType().equalsString(replacedShape)) {
            return false;
        }
        MethodTypeDesc replacedType = MethodTypeDesc.ofDescriptor(replacedShape);
        MethodTypeDesc replacementType = MethodTypeDesc.ofDescriptor(replacementShape);
        return replacedType.parameterCount() == 1
                && !replacedType.parameterType(0).isPrimitive()
                && replacedType.returnType().equals(replacedType.parameterType(0))
                && replacementType.returnType().equals(ConstantDescs.CD_void)
                && replacementType.parameterCount() >= 1
                && replacementType.parameterType(0).equals(replacedType.parameterType(0));
    }

    /**
     * When {@code vanilla}'s slice {@code [start, end]} separates one {@code target} call on a {@code GETFIELD} receiver
     * inside the slice from one {@code target} call on a different {@code GETFIELD} receiver outside it, and {@code
     * patched} places the inside receiver's call before every {@code GETFIELD} of the outside receiver while keeping the
     * outside receiver's call after it, returns the outside receiver field in Mixin target notation.
     */
    private static Optional<String> retargetedSliceToField(
            MethodModel vanilla, MethodModel patched, String target, String start, String end) {
        if (vanilla.code().isEmpty() || patched.code().isEmpty()) {
            return Optional.empty();
        }
        List<CodeElement> vanillaCode = vanilla.code().get().elementList();
        int first = start == null ? 0 : -1;
        int last = end == null ? vanillaCode.size() - 1 : -1;
        for (int index = 0; index < vanillaCode.size(); index++) {
            if (start != null && first < 0 && matches(vanillaCode.get(index), start)) {
                first = index;
            }
            if (end != null && matches(vanillaCode.get(index), end)) {
                last = index;
            }
        }
        if (first < 0 || last < 0) {
            return Optional.empty();
        }
        List<String> insideFields = new ArrayList<>();
        List<String> outsideFields = new ArrayList<>();
        for (int index = 0; index < vanillaCode.size(); index++) {
            if (matches(vanillaCode.get(index), target)) {
                Optional<String> field = receiverField(vanillaCode, index);
                if (field.isEmpty()) {
                    return Optional.empty();
                }
                if (index >= first && index <= last) {
                    insideFields.add(field.get());
                } else {
                    outsideFields.add(field.get());
                }
            }
        }
        if (insideFields.size() != 1
                || outsideFields.size() != 1
                || insideFields.getFirst().equals(outsideFields.getFirst())) {
            return Optional.empty();
        }
        String wantedField = insideFields.getFirst();
        String boundaryField = outsideFields.getFirst();
        List<CodeElement> patchedCode = patched.code().get().elementList();
        int boundaryIndex = -1;
        List<Integer> wantedCalls = new ArrayList<>();
        List<Integer> otherCalls = new ArrayList<>();
        for (int index = 0; index < patchedCode.size(); index++) {
            if (patchedCode.get(index) instanceof FieldInstruction field
                    && field.opcode() == Opcode.GETFIELD
                    && fieldTarget(field).equals(boundaryField)) {
                boundaryIndex = index;
            }
            if (matches(patchedCode.get(index), target)) {
                Optional<String> receiver = receiverField(patchedCode, index);
                if (receiver.isEmpty()) {
                    return Optional.empty();
                }
                if (receiver.get().equals(wantedField)) {
                    wantedCalls.add(index);
                } else {
                    otherCalls.add(index);
                }
            }
        }
        int endBoundary = boundaryIndex;
        if (endBoundary < 0
                || wantedCalls.size() != 1
                || otherCalls.isEmpty()
                || wantedCalls.getFirst() >= endBoundary
                || otherCalls.stream().anyMatch(callIndex -> callIndex <= endBoundary)) {
            return Optional.empty();
        }
        return Optional.of(boundaryField);
    }

    private static Optional<String> receiverField(List<CodeElement> code, int callIndex) {
        for (int index = callIndex - 1; index >= Math.max(0, callIndex - 6); index--) {
            if (code.get(index) instanceof FieldInstruction field && field.opcode() == Opcode.GETFIELD) {
                return Optional.of(fieldTarget(field));
            }
            if (code.get(index) instanceof InvokeInstruction) {
                break;
            }
        }
        return Optional.empty();
    }

    private static String fieldTarget(FieldInstruction field) {
        return "L" + field.owner().asInternalName() + ";" + field.name().stringValue() + ":"
                + field.type().stringValue();
    }

    /** Whether {@code method} has {@code target} after its first {@code start} and before its last {@code end}. */
    private static boolean inSlice(MethodModel method, String target, String start, String end) {
        if (method.code().isEmpty()) {
            return false;
        }
        List<CodeElement> code = method.code().get().elementList();
        int first = start == null ? 0 : -1;
        int last = end == null ? code.size() : -1;
        for (int index = 0; index < code.size(); index++) {
            if (start != null && first < 0 && matches(code.get(index), start)) {
                first = index;
            }
            if (end != null && matches(code.get(index), end)) {
                last = index;
            }
        }
        if (first < 0 || last < 0) {
            return false;
        }
        for (int index = first; index <= Math.min(last, code.size() - 1); index++) {
            if (matches(code.get(index), target)) {
                return true;
            }
        }
        return false;
    }

    /** Whether one instruction is the one {@code target} names ({@link #contains}). */
    static boolean matches(CodeElement element, String target) {
        return element instanceof java.lang.classfile.Instruction instruction
                && containsInstruction(instruction, target);
    }

    /** {@code [value, target, another element's name or null]} of each {@code @At}. */
    static void points(AnnotationValue value, List<String[]> out) {
        if (value instanceof AnnotationValue.OfArray array) {
            array.values().forEach(item -> points(item, out));
        } else if (value instanceof AnnotationValue.OfAnnotation at) {
            String kind = null;
            String target = null;
            String other = null;
            for (AnnotationElement element : at.annotation().elements()) {
                if (element.value() instanceof AnnotationValue.OfString text
                        && element.name().equalsString("value")) {
                    kind = text.stringValue();
                } else if (element.value() instanceof AnnotationValue.OfString text
                        && element.name().equalsString("target")) {
                    target = text.stringValue();
                } else {
                    other = element.name().stringValue();
                }
            }
            out.add(new String[] {kind, target, other});
        }
    }

    /**
     * Whether {@code method} has the instruction Mixin's target notation names: {@code Lowner;name(descriptor)} for a
     * call, {@code Lowner;name:descriptor} for a field, {@code Lowner;}, {@code owner} or {@code (arguments)Lowner;}
     * for {@code NEW}. For {@code (arguments)Lowner;} the method must call that constructor, as Mixin matches only it.
     */
    static boolean contains(MethodModel method, String target) {
        return method.code().isPresent()
                && method.code().get().elementList().stream()
                        .anyMatch(element -> element instanceof java.lang.classfile.Instruction instruction
                                && containsInstruction(instruction, target));
    }

    /** Whether {@code instruction} is the one {@code target} names, in the notation of {@link #contains}. */
    private static boolean containsInstruction(java.lang.classfile.Instruction instruction, String target) {
        if (target.startsWith("(") && target.endsWith(";")) {
            String owner = target.substring(target.lastIndexOf(')') + 2, target.length() - 1);
            String constructor = target.substring(0, target.lastIndexOf(')') + 1) + "V";
            return instruction instanceof InvokeInstruction call
                    && call.opcode() == Opcode.INVOKESPECIAL
                    && call.name().equalsString(ConstantDescs.INIT_NAME)
                    && call.owner().asInternalName().equals(owner)
                    && call.type().equalsString(constructor);
        }
        String owner;
        String rest;
        if (target.startsWith("L") && target.indexOf(';') > 0) {
            owner = target.substring(1, target.indexOf(';'));
            rest = target.substring(target.indexOf(';') + 1);
        } else {
            owner = target;
            rest = "";
        }
        int split = rest.indexOf('(') >= 0 ? rest.indexOf('(') : rest.indexOf(':');
        String name = split < 0 ? rest : rest.substring(0, split);
        String descriptor = split < 0 ? "" : rest.substring(split).replaceFirst("^:", "");
        if (instruction instanceof InvokeInstruction call) {
            return call.owner().asInternalName().equals(owner)
                    && call.name().equalsString(name)
                    && (descriptor.isEmpty()
                            || call.typeSymbol().descriptorString().equals(descriptor));
        }
        if (instruction instanceof FieldInstruction field) {
            return field.owner().asInternalName().equals(owner)
                    && field.name().equalsString(name)
                    && (descriptor.isEmpty() || field.type().equalsString(descriptor));
        }
        return instruction instanceof NewObjectInstruction created
                && name.isEmpty()
                && created.className().asInternalName().equals(owner);
    }

    /** The vanilla and the patched game's classes, parsed once each. */
    static final class Classes {

        private final Function<String, Optional<byte[]>> vanillaBytes;
        private final GameClassBytes patchedBytes;
        private final Map<String, Optional<ClassModel>> vanilla = new HashMap<>();
        private final Map<String, Optional<ClassModel>> patched = new HashMap<>();
        private final Map<String, List<String>> vanillaSuperclasses = new HashMap<>();

        /** The vanilla superclasses of {@code name} (internal names, nearest first, at most 32), read once. */
        List<String> vanillaSuperclasses(String name) {
            List<String> known = vanillaSuperclasses.get(name);
            if (known != null) {
                return known;
            }
            List<String> found = vanilla(name)
                    .flatMap(ClassModel::superclass)
                    .map(parent -> {
                        List<String> chain = new ArrayList<>();
                        chain.add(parent.asInternalName());
                        List<String> above = vanillaSuperclasses(parent.asInternalName());
                        chain.addAll(above.subList(0, Math.min(above.size(), 31)));
                        return List.copyOf(chain);
                    })
                    .orElse(List.of());
            vanillaSuperclasses.put(name, found);
            return found;
        }

        Classes(Function<String, Optional<byte[]>> vanillaBytes, GameClassBytes patchedBytes) {
            this.vanillaBytes = vanillaBytes;
            this.patchedBytes = patchedBytes;
        }

        Optional<ClassModel> vanilla(String name) {
            return vanilla.computeIfAbsent(
                    name,
                    key -> vanillaBytes.apply(key).map(bytes -> ClassFile.of().parse(bytes)));
        }

        Optional<ClassModel> patched(String name) {
            return patched.computeIfAbsent(
                    name,
                    key -> patchedBytes
                            .classBytes(key)
                            .map(bytes -> ClassFile.of().parse(bytes)));
        }
    }
}
